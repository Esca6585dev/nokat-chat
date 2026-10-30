package api

import (
	"crypto/ecdh"
	"encoding/base64"
	"os"
	"path/filepath"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
	"gorm.io/gorm"

	"github.com/esca6585dev/nokat-chat/backend/internal/hub"
	"github.com/esca6585dev/nokat-chat/backend/internal/models"
)

// Secret chats are end-to-end encrypted (docs/SECRET_CHATS.md). The server only
// validates shapes and relays opaque data; it can never derive the chat key.

const (
	minCiphertext = 12 + 16  // IV + GCM tag
	maxCiphertext = 64 << 10 // decoded bytes
)

// validPublicKey accepts a base64 uncompressed P-256 point that lies on the curve.
func validPublicKey(b64 string) error {
	raw, err := base64.StdEncoding.DecodeString(b64)
	if err != nil || len(raw) != 65 || raw[0] != 4 {
		return bad("public_key nädogry (P-256, 65 baýt, base64)")
	}
	if _, err := ecdh.P256().NewPublicKey(raw); err != nil {
		return bad("public_key egrilikde ýatmaýar")
	}
	return nil
}

func validCiphertext(b64 string) error {
	if b64 == "" {
		return bad("ciphertext gerek")
	}
	if base64.StdEncoding.DecodedLen(len(b64)) > maxCiphertext+3 {
		return bad("ciphertext gaty uly")
	}
	raw, err := base64.StdEncoding.DecodeString(b64)
	if err != nil || len(raw) < minCiphertext || len(raw) > maxCiphertext {
		return bad("ciphertext nädogry")
	}
	return nil
}

func (s *Server) createSecret(c *fiber.Ctx) error {
	me := uid(c)
	var body struct {
		UserID    string `json:"user_id"`
		PublicKey string `json:"public_key"`
	}
	if err := c.BodyParser(&body); err != nil || body.UserID == "" {
		return bad("user_id gerek")
	}
	if body.UserID == me {
		return bad("özüňiz bilen söhbet açyp bolmaýar")
	}
	if err := validPublicKey(body.PublicKey); err != nil {
		return err
	}
	var peer models.User
	if err := s.db.First(&peer, "id = ?", body.UserID).Error; err != nil {
		return err
	}

	now := time.Now().UTC()
	chat := models.Chat{
		ID: uuid.NewString(), Type: models.ChatSecret, SecretState: models.SecretPending,
		InitiatorID: me, InitiatorKey: body.PublicKey, CreatedAt: now, UpdatedAt: now,
	}
	err := s.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Create(&chat).Error; err != nil {
			return err
		}
		return tx.Create([]models.ChatMember{
			{ChatID: chat.ID, UserID: me, LastReadAt: now, JoinedAt: now},
			{ChatID: chat.ID, UserID: peer.ID, LastReadAt: now, JoinedAt: now},
		}).Error
	})
	if err != nil {
		return err
	}
	if dto, err := s.buildChat(peer.ID, chat.ID); err == nil {
		s.hub.SendTo([]string{peer.ID}, hub.Event{Type: "chat.new", Data: dto})
	}
	dto, err := s.buildChat(me, chat.ID)
	if err != nil {
		return err
	}
	return c.Status(fiber.StatusCreated).JSON(dto)
}

func (s *Server) acceptSecret(c *fiber.Ctx) error {
	me, chatID := uid(c), c.Params("id")
	var body struct {
		PublicKey string `json:"public_key"`
	}
	if err := c.BodyParser(&body); err != nil {
		return bad("nädogry sorag")
	}
	if err := validPublicKey(body.PublicKey); err != nil {
		return err
	}
	if err := s.ensureMember(me, chatID); err != nil {
		return err
	}
	var chat models.Chat
	if err := s.db.First(&chat, "id = ?", chatID).Error; err != nil {
		return err
	}
	if chat.Type != models.ChatSecret {
		return bad("bu gizlin söhbet däl")
	}
	if chat.InitiatorID == me {
		return fiber.NewError(fiber.StatusForbidden, "çakylygy beýleki tarap kabul etmeli")
	}
	// Conditional update: the first of the peer's devices to accept wins.
	res := s.db.Model(&models.Chat{}).Where("id = ? AND secret_state = ?", chatID, models.SecretPending).
		Updates(map[string]any{"secret_state": models.SecretActive, "peer_key": body.PublicKey, "updated_at": time.Now().UTC()})
	if res.Error != nil {
		return res.Error
	}
	if res.RowsAffected == 0 {
		return fiber.NewError(fiber.StatusConflict, "gizlin söhbet eýýäm kabul edildi")
	}
	for _, id := range s.memberIDs(chatID) {
		if id == me {
			continue
		}
		if dto, err := s.buildChat(id, chatID); err == nil {
			s.hub.SendTo([]string{id}, hub.Event{Type: "chat.updated", Data: dto})
		}
	}
	dto, err := s.buildChat(me, chatID)
	if err != nil {
		return err
	}
	// The accepting user's other devices learn the chat is now bound elsewhere.
	s.hub.SendTo([]string{me}, hub.Event{Type: "chat.updated", Data: dto})
	return c.JSON(dto)
}

// deleteChat removes a secret chat with all messages and encrypted blobs for both sides.
func (s *Server) deleteChat(c *fiber.Ctx) error {
	me, chatID := uid(c), c.Params("id")
	if err := s.ensureMember(me, chatID); err != nil {
		return err
	}
	var chat models.Chat
	if err := s.db.First(&chat, "id = ?", chatID).Error; err != nil {
		return err
	}
	if chat.Type != models.ChatSecret {
		return bad("diňe gizlin söhbeti pozup bolýar")
	}
	members := s.memberIDs(chatID)

	var uploadIDs []string
	s.db.Model(&models.Message{}).Where("chat_id = ? AND attachment_id IS NOT NULL", chatID).Pluck("attachment_id", &uploadIDs)
	var uploads []models.Upload
	if len(uploadIDs) > 0 {
		s.db.Where("id IN ?", uploadIDs).Find(&uploads)
	}
	err := s.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Where("chat_id = ?", chatID).Delete(&models.Message{}).Error; err != nil {
			return err
		}
		if len(uploadIDs) > 0 {
			if err := tx.Where("id IN ?", uploadIDs).Delete(&models.Upload{}).Error; err != nil {
				return err
			}
		}
		if err := tx.Where("chat_id = ?", chatID).Delete(&models.ChatMember{}).Error; err != nil {
			return err
		}
		return tx.Delete(&models.Chat{}, "id = ?", chatID).Error
	})
	if err != nil {
		return err
	}
	for _, u := range uploads {
		_ = os.Remove(filepath.Join(s.cfg.UploadDir, u.StoredName))
	}
	s.hub.SendTo(members, hub.Event{Type: "chat.deleted", Data: fiber.Map{"chat_id": chatID}})
	return c.JSON(fiber.Map{"ok": true})
}
