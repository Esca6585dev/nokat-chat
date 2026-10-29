package api

import (
	"regexp"
	"strings"
	"unicode/utf8"

	"github.com/gofiber/fiber/v2"

	"github.com/esca6585dev/nokat-chat/backend/internal/models"
)

var usernameRe = regexp.MustCompile(`^[a-z0-9_]{3,32}$`)

func (s *Server) getMe(c *fiber.Ctx) error {
	var u models.User
	if err := s.db.First(&u, "id = ?", uid(c)).Error; err != nil {
		return err
	}
	return c.JSON(s.userDTO(u))
}

func (s *Server) updateMe(c *fiber.Ctx) error {
	var body struct {
		Name      *string `json:"name"`
		Username  *string `json:"username"`
		Bio       *string `json:"bio"`
		AvatarURL *string `json:"avatar_url"`
	}
	if err := c.BodyParser(&body); err != nil {
		return bad("nädogry sorag")
	}
	var u models.User
	if err := s.db.First(&u, "id = ?", uid(c)).Error; err != nil {
		return err
	}
	if body.Name != nil {
		name := strings.TrimSpace(*body.Name)
		if name == "" || utf8.RuneCountInString(name) > 64 {
			return bad("at 1-64 harp bolmaly")
		}
		u.Name = name
	}
	if body.Username != nil {
		un := strings.ToLower(strings.TrimPrefix(strings.TrimSpace(*body.Username), "@"))
		if un == "" {
			u.Username = nil
		} else {
			if !usernameRe.MatchString(un) {
				return bad("ulanyjy ady 3-32 harp: a-z, 0-9, _")
			}
			var n int64
			s.db.Model(&models.User{}).Where("username = ? AND id <> ?", un, u.ID).Count(&n)
			if n > 0 {
				return fiber.NewError(fiber.StatusConflict, "bu ulanyjy ady eýýäm alnan")
			}
			u.Username = &un
		}
	}
	if body.Bio != nil {
		bio := strings.TrimSpace(*body.Bio)
		if utf8.RuneCountInString(bio) > 200 {
			return bad("bio iň köp 200 harp")
		}
		u.Bio = bio
	}
	if body.AvatarURL != nil {
		u.AvatarURL = strings.TrimSpace(*body.AvatarURL)
	}
	if err := s.db.Save(&u).Error; err != nil {
		return err
	}
	return c.JSON(s.userDTO(u))
}

func (s *Server) searchUsers(c *fiber.Ctx) error {
	q := strings.ToLower(strings.TrimSpace(c.Query("q")))
	users := []models.User{}
	if q == "" {
		return c.JSON([]UserDTO{})
	}
	like := "%" + escapeLike(q) + "%"
	prefix := escapeLike(strings.TrimPrefix(q, "@")) + "%"
	phone := escapeLike(strings.NewReplacer(" ", "", "-", "").Replace(q)) + "%"
	if !strings.HasPrefix(phone, "+") {
		phone = "%" + phone
	}
	err := s.db.Where("id <> ?", uid(c)).
		Where(`LOWER(name) LIKE ? ESCAPE '\' OR username LIKE ? ESCAPE '\' OR phone LIKE ? ESCAPE '\'`, like, prefix, phone).
		Order("name").Limit(20).Find(&users).Error
	if err != nil {
		return err
	}
	out := make([]UserDTO, 0, len(users))
	for _, u := range users {
		out = append(out, s.userDTO(u))
	}
	return c.JSON(out)
}

func (s *Server) getUser(c *fiber.Ctx) error {
	var u models.User
	if err := s.db.First(&u, "id = ?", c.Params("id")).Error; err != nil {
		return err
	}
	return c.JSON(s.userDTO(u))
}

func escapeLike(s string) string {
	return strings.NewReplacer(`\`, `\\`, `%`, `\%`, `_`, `\_`).Replace(s)
}
