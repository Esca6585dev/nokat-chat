package api

import (
	"errors"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
	"gorm.io/gorm"

	"github.com/esca6585dev/nokat-chat/backend/internal/hub"
	"github.com/esca6585dev/nokat-chat/backend/internal/models"
)

const maxMessageLen = 4000

func (s *Server) listChats(c *fiber.Ctx) error {
	me := uid(c)
	var ids []string
	if err := s.db.Model(&models.ChatMember{}).Where("user_id = ?", me).Pluck("chat_id", &ids).Error; err != nil {
		return err
	}
	chats, err := s.buildChats(me, ids)
	if err != nil {
		return err
	}
	// Empty direct chats stay hidden until the first message, like most messengers.
	out := make([]ChatDTO, 0, len(chats))
	for _, ch := range chats {
		if ch.Type == models.ChatDirect && ch.LastMessage == nil {
			continue
		}
		out = append(out, ch)
	}
	return c.JSON(out)
}

func (s *Server) getChat(c *fiber.Ctx) error {
	if err := s.ensureMember(uid(c), c.Params("id")); err != nil {
		return err
	}
	ch, err := s.buildChat(uid(c), c.Params("id"))
	if err != nil {
		return err
	}
	return c.JSON(ch)
}

func (s *Server) createDirect(c *fiber.Ctx) error {
	me := uid(c)
	var body struct {
		UserID string `json:"user_id"`
	}
	if err := c.BodyParser(&body); err != nil || body.UserID == "" {
		return bad("user_id gerek")
	}
	if body.UserID == me {
		return bad("özüňiz bilen söhbet açyp bolmaýar")
	}
	var peer models.User
	if err := s.db.First(&peer, "id = ?", body.UserID).Error; err != nil {
		return err
	}
	a, b := me, peer.ID
	if a > b {
		a, b = b, a
	}
	key := a + ":" + b

	var chat models.Chat
	err := s.db.First(&chat, "direct_key = ?", key).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		now := time.Now().UTC()
		chat = models.Chat{ID: uuid.NewString(), Type: models.ChatDirect, DirectKey: &key, CreatedAt: now, UpdatedAt: now}
		err = s.db.Transaction(func(tx *gorm.DB) error {
			if err := tx.Create(&chat).Error; err != nil {
				return err
			}
			return tx.Create([]models.ChatMember{
				{ChatID: chat.ID, UserID: me, LastReadAt: now, JoinedAt: now},
				{ChatID: chat.ID, UserID: peer.ID, LastReadAt: now, JoinedAt: now},
			}).Error
		})
		if err != nil {
			// Lost a race with the peer creating the same chat: use theirs.
			if err2 := s.db.First(&chat, "direct_key = ?", key).Error; err2 != nil {
				return err
			}
		}
	} else if err != nil {
		return err
	}
	ch, err := s.buildChat(me, chat.ID)
	if err != nil {
		return err
	}
	return c.JSON(ch)
}

func (s *Server) createGroup(c *fiber.Ctx) error {
	me := uid(c)
	var body struct {
		Title     string   `json:"title"`
		MemberIDs []string `json:"member_ids"`
	}
	if err := c.BodyParser(&body); err != nil {
		return bad("nädogry sorag")
	}
	title := strings.TrimSpace(body.Title)
	if title == "" || utf8.RuneCountInString(title) > 64 {
		return bad("topar ady 1-64 harp bolmaly")
	}
	set := map[string]bool{me: true}
	for _, id := range body.MemberIDs {
		set[id] = true
	}
	ids := make([]string, 0, len(set))
	for id := range set {
		ids = append(ids, id)
	}
	var n int64
	s.db.Model(&models.User{}).Where("id IN ?", ids).Count(&n)
	if int(n) != len(ids) {
		return bad("käbir agzalar tapylmady")
	}
	if len(ids) < 2 {
		return bad("iň az bir agza saýlaň")
	}

	now := time.Now().UTC()
	chat := models.Chat{ID: uuid.NewString(), Type: models.ChatGroup, Title: title, CreatedAt: now, UpdatedAt: now}
	err := s.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Create(&chat).Error; err != nil {
			return err
		}
		members := make([]models.ChatMember, 0, len(ids))
		for _, id := range ids {
			members = append(members, models.ChatMember{ChatID: chat.ID, UserID: id, LastReadAt: now, JoinedAt: now})
		}
		return tx.Create(&members).Error
	})
	if err != nil {
		return err
	}
	for _, id := range ids {
		if id == me {
			continue
		}
		if dto, err := s.buildChat(id, chat.ID); err == nil {
			s.hub.SendTo([]string{id}, hub.Event{Type: "chat.new", Data: dto})
		}
	}
	ch, err := s.buildChat(me, chat.ID)
	if err != nil {
		return err
	}
	return c.Status(fiber.StatusCreated).JSON(ch)
}

func (s *Server) listMessages(c *fiber.Ctx) error {
	chatID := c.Params("id")
	if err := s.ensureMember(uid(c), chatID); err != nil {
		return err
	}
	limit := c.QueryInt("limit", 50)
	if limit < 1 || limit > 100 {
		limit = 50
	}
	q := s.db.Where("chat_id = ?", chatID)
	if before := c.Query("before"); before != "" {
		var pivot models.Message
		if err := s.db.First(&pivot, "id = ? AND chat_id = ?", before, chatID).Error; err != nil {
			return bad("before nädogry")
		}
		q = q.Where("created_at < ? OR (created_at = ? AND id < ?)", pivot.CreatedAt, pivot.CreatedAt, pivot.ID)
	}
	var msgs []models.Message
	if err := q.Order("created_at DESC, id DESC").Limit(limit).Find(&msgs).Error; err != nil {
		return err
	}
	var members []models.ChatMember
	s.db.Where("chat_id = ?", chatID).Find(&members)
	uploads := s.uploadsFor(msgs)
	out := make([]MessageDTO, len(msgs))
	for i, m := range msgs {
		out[len(msgs)-1-i] = messageDTO(m, members, uploads) // oldest → newest
	}
	return c.JSON(out)
}

func (s *Server) postMessage(c *fiber.Ctx) error {
	var body struct {
		Text         string `json:"text"`
		ClientID     string `json:"client_id"`
		AttachmentID string `json:"attachment_id"`
	}
	if err := c.BodyParser(&body); err != nil {
		return bad("nädogry sorag")
	}
	msg, err := s.sendMessage(uid(c), c.Params("id"), body.Text, body.ClientID, body.AttachmentID)
	if err != nil {
		return err
	}
	return c.Status(fiber.StatusCreated).JSON(msg)
}

func (s *Server) markReadHandler(c *fiber.Ctx) error {
	if err := s.markRead(uid(c), c.Params("id")); err != nil {
		return err
	}
	return c.JSON(fiber.Map{"ok": true})
}

func (s *Server) ensureMember(userID, chatID string) error {
	var n int64
	s.db.Model(&models.ChatMember{}).Where("chat_id = ? AND user_id = ?", chatID, userID).Count(&n)
	if n == 0 {
		return errNotFound
	}
	return nil
}

func (s *Server) memberIDs(chatID string) []string {
	var ids []string
	s.db.Model(&models.ChatMember{}).Where("chat_id = ?", chatID).Pluck("user_id", &ids)
	return ids
}

// sendMessage is shared by REST and WebSocket so both paths behave identically.
func (s *Server) sendMessage(senderID, chatID, text, clientID, attachmentID string) (MessageDTO, error) {
	text = strings.TrimSpace(text)
	if text == "" && attachmentID == "" {
		return MessageDTO{}, bad("hat boş")
	}
	if utf8.RuneCountInString(text) > maxMessageLen {
		return MessageDTO{}, bad("hat gaty uzyn")
	}
	if len(clientID) > 64 {
		clientID = clientID[:64]
	}
	if err := s.ensureMember(senderID, chatID); err != nil {
		return MessageDTO{}, err
	}
	var members []models.ChatMember
	if err := s.db.Where("chat_id = ?", chatID).Find(&members).Error; err != nil {
		return MessageDTO{}, err
	}

	if clientID != "" {
		var dup models.Message
		if err := s.db.First(&dup, "chat_id = ? AND sender_id = ? AND client_id = ?", chatID, senderID, clientID).Error; err == nil {
			return messageDTO(dup, members, s.uploadsFor([]models.Message{dup})), nil
		}
	}

	uploads := map[string]models.Upload{}
	var attachment *string
	if attachmentID != "" {
		var up models.Upload
		if err := s.db.First(&up, "id = ? AND owner_id = ?", attachmentID, senderID).Error; err != nil {
			return MessageDTO{}, bad("faýl tapylmady")
		}
		var used int64
		s.db.Model(&models.Message{}).Where("attachment_id = ?", up.ID).Count(&used)
		if used > 0 {
			return MessageDTO{}, bad("bu faýl eýýäm iberildi")
		}
		uploads[up.ID] = up
		attachment = &up.ID
	}

	var prior int64
	s.db.Model(&models.Message{}).Where("chat_id = ?", chatID).Count(&prior)

	now := time.Now().UTC()
	msg := models.Message{ID: uuid.NewString(), ChatID: chatID, SenderID: senderID, Text: text, ClientID: clientID, AttachmentID: attachment, CreatedAt: now}
	err := s.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Create(&msg).Error; err != nil {
			return err
		}
		if err := tx.Model(&models.Chat{}).Where("id = ?", chatID).Update("updated_at", now).Error; err != nil {
			return err
		}
		return tx.Model(&models.ChatMember{}).Where("chat_id = ? AND user_id = ?", chatID, senderID).
			Update("last_read_at", now).Error
	})
	if err != nil {
		return MessageDTO{}, err
	}

	ids := make([]string, 0, len(members))
	for _, m := range members {
		ids = append(ids, m.UserID)
	}
	// First message of a direct chat: the peer hasn't seen this chat yet.
	if prior == 0 {
		for _, id := range ids {
			if id == senderID {
				continue
			}
			if dto, err := s.buildChat(id, chatID); err == nil {
				dto.LastMessage, dto.UnreadCount = nil, 0
				s.hub.SendTo([]string{id}, hub.Event{Type: "chat.new", Data: dto})
			}
		}
	}
	dto := messageDTO(msg, members, uploads)
	s.hub.SendTo(ids, hub.Event{Type: "message.new", Data: dto})
	return dto, nil
}

func (s *Server) markRead(userID, chatID string) error {
	if err := s.ensureMember(userID, chatID); err != nil {
		return err
	}
	now := time.Now().UTC()
	if err := s.db.Model(&models.ChatMember{}).Where("chat_id = ? AND user_id = ?", chatID, userID).
		Update("last_read_at", now).Error; err != nil {
		return err
	}
	s.hub.SendTo(s.memberIDs(chatID), hub.Event{Type: "message.read",
		Data: fiber.Map{"chat_id": chatID, "user_id": userID, "read_at": now}})
	return nil
}
