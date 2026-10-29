package api

import (
	"context"
	"errors"
	"log"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"github.com/esca6585dev/nokat-chat/backend/internal/auth"
	"github.com/esca6585dev/nokat-chat/backend/internal/models"
)

func (s *Server) requestOTP(c *fiber.Ctx) error {
	var body struct {
		Phone string `json:"phone"`
	}
	if err := c.BodyParser(&body); err != nil {
		return bad("nädogry sorag")
	}
	phone, err := auth.NormalizePhone(body.Phone)
	if err != nil {
		return bad(err.Error())
	}

	now := time.Now().UTC()
	var existing models.OTPCode
	if err := s.db.First(&existing, "phone = ?", phone).Error; err == nil {
		if wait := s.cfg.OTPResend - now.Sub(existing.SentAt); wait > 0 {
			return fiber.NewError(fiber.StatusTooManyRequests, "kody täzeden ibermek üçin garaşyň")
		}
	}

	code, err := auth.GenerateCode()
	if err != nil {
		return err
	}
	otp := models.OTPCode{
		Phone: phone, CodeHash: auth.HashCode(s.cfg.JWTSecret, phone, code),
		ExpiresAt: now.Add(s.cfg.OTPTTL), SentAt: now, Attempts: 0,
	}
	if err := s.db.Clauses(clause.OnConflict{UpdateAll: true}).Create(&otp).Error; err != nil {
		return err
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := s.sms.Send(ctx, phone, "Nokat Chat tassyklama kody: "+code); err != nil {
		log.Printf("sms send failed: %v", err)
		if !s.cfg.OTPDevMode {
			return fiber.NewError(fiber.StatusBadGateway, "SMS iberip bolmady")
		}
	}

	resp := fiber.Map{"ok": true, "expires_in": int(s.cfg.OTPTTL.Seconds())}
	if s.cfg.OTPDevMode {
		resp["dev_code"] = code
	}
	return c.JSON(resp)
}

func (s *Server) verifyOTP(c *fiber.Ctx) error {
	var body struct {
		Phone string `json:"phone"`
		Code  string `json:"code"`
	}
	if err := c.BodyParser(&body); err != nil {
		return bad("nädogry sorag")
	}
	phone, err := auth.NormalizePhone(body.Phone)
	if err != nil {
		return bad(err.Error())
	}

	var otp models.OTPCode
	if err := s.db.First(&otp, "phone = ?", phone).Error; err != nil {
		return bad("ilki kod soraň")
	}
	if time.Now().After(otp.ExpiresAt) {
		s.db.Delete(&otp)
		return bad("kodyň möhleti geçdi, täzesini soraň")
	}
	if otp.Attempts >= s.cfg.OTPMaxTries {
		s.db.Delete(&otp)
		return fiber.NewError(fiber.StatusTooManyRequests, "synanyşyk köp boldy, täze kod soraň")
	}
	if !auth.CodeMatches(s.cfg.JWTSecret, phone, body.Code, otp.CodeHash) {
		s.db.Model(&otp).UpdateColumn("attempts", gorm.Expr("attempts + 1"))
		return bad("kod nädogry")
	}
	s.db.Delete(&otp)

	var user models.User
	err = s.db.First(&user, "phone = ?", phone).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		now := time.Now().UTC()
		user = models.User{ID: uuid.NewString(), Phone: phone, LastSeen: now, CreatedAt: now}
		if err := s.db.Create(&user).Error; err != nil {
			return err
		}
	} else if err != nil {
		return err
	}

	token, err := s.tokens.Issue(user.ID)
	if err != nil {
		return err
	}
	return c.JSON(fiber.Map{"token": token, "user": s.userDTO(user), "is_new": user.Name == ""})
}
