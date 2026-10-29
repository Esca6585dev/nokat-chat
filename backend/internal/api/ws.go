package api

import (
	"encoding/json"
	"log"
	"time"

	"github.com/gofiber/contrib/websocket"
	"github.com/gofiber/fiber/v2"

	"github.com/esca6585dev/nokat-chat/backend/internal/hub"
	"github.com/esca6585dev/nokat-chat/backend/internal/models"
)

const (
	wsReadTimeout = 75 * time.Second // clients ping every ~25 s
	wsWriteWait   = 10 * time.Second
)

func (s *Server) wsAuth(c *fiber.Ctx) error {
	if !websocket.IsWebSocketUpgrade(c) {
		return fiber.ErrUpgradeRequired
	}
	userID, err := s.tokens.Parse(c.Query("token"))
	if err != nil {
		return fiber.NewError(fiber.StatusUnauthorized, "token nädogry")
	}
	c.Locals("uid", userID)
	return c.Next()
}

type inbound struct {
	Type string          `json:"type"`
	Data json.RawMessage `json:"data"`
}

func (s *Server) wsHandler(conn *websocket.Conn) {
	userID := conn.Locals("uid").(string)
	client := &hub.Client{UserID: userID, Send: make(chan []byte, 128)}
	if s.hub.Register(client) {
		s.broadcastPresence(userID, true, time.Now().UTC())
	}

	done := make(chan struct{})
	go func() {
		defer close(done)
		for payload := range client.Send {
			_ = conn.SetWriteDeadline(time.Now().Add(wsWriteWait))
			if err := conn.WriteMessage(websocket.TextMessage, payload); err != nil {
				_ = conn.Close() // unblocks the reader below
				for range client.Send {
				}
				return
			}
		}
	}()

	_ = conn.SetReadDeadline(time.Now().Add(wsReadTimeout))
	conn.SetPongHandler(func(string) error { return conn.SetReadDeadline(time.Now().Add(wsReadTimeout)) })
	for {
		_, raw, err := conn.ReadMessage()
		if err != nil {
			break
		}
		_ = conn.SetReadDeadline(time.Now().Add(wsReadTimeout))
		var in inbound
		if json.Unmarshal(raw, &in) != nil {
			continue
		}
		s.handleInbound(client, in)
	}

	if s.hub.Unregister(client) {
		now := time.Now().UTC()
		s.db.Model(&models.User{}).Where("id = ?", userID).Update("last_seen", now)
		s.broadcastPresence(userID, false, now)
	}
	<-done
}

func (s *Server) handleInbound(c *hub.Client, in inbound) {
	var d struct {
		ChatID   string `json:"chat_id"`
		Text     string `json:"text"`
		ClientID string `json:"client_id"`
		Typing   bool   `json:"typing"`
	}
	_ = json.Unmarshal(in.Data, &d)
	switch in.Type {
	case "ping":
		select {
		case c.Send <- []byte(`{"type":"pong","data":{}}`):
		default:
		}
	case "message.send":
		if _, err := s.sendMessage(c.UserID, d.ChatID, d.Text, d.ClientID); err != nil {
			log.Printf("ws message.send from %s: %v", c.UserID, err)
		}
	case "typing":
		if s.ensureMember(c.UserID, d.ChatID) != nil {
			return
		}
		others := make([]string, 0)
		for _, id := range s.memberIDs(d.ChatID) {
			if id != c.UserID {
				others = append(others, id)
			}
		}
		s.hub.SendTo(others, hub.Event{Type: "typing",
			Data: fiber.Map{"chat_id": d.ChatID, "user_id": c.UserID, "typing": d.Typing}})
	case "read":
		_ = s.markRead(c.UserID, d.ChatID)
	}
}

// broadcastPresence notifies everyone who shares a chat with the user.
func (s *Server) broadcastPresence(userID string, online bool, at time.Time) {
	var ids []string
	s.db.Model(&models.ChatMember{}).Distinct("user_id").
		Where("chat_id IN (?)", s.db.Model(&models.ChatMember{}).Select("chat_id").Where("user_id = ?", userID)).
		Where("user_id <> ?", userID).Pluck("user_id", &ids)
	s.hub.SendTo(ids, hub.Event{Type: "presence",
		Data: fiber.Map{"user_id": userID, "online": online, "last_seen": at}})
}
