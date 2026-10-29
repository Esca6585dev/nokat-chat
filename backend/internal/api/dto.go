package api

import (
	"sort"
	"time"

	"github.com/esca6585dev/nokat-chat/backend/internal/models"
)

type UserDTO struct {
	ID        string    `json:"id"`
	Phone     string    `json:"phone"`
	Name      string    `json:"name"`
	Username  string    `json:"username"`
	Bio       string    `json:"bio"`
	AvatarURL string    `json:"avatar_url"`
	Online    bool      `json:"online"`
	LastSeen  time.Time `json:"last_seen"`
	CreatedAt time.Time `json:"created_at"`
}

type MessageDTO struct {
	ID        string    `json:"id"`
	ChatID    string    `json:"chat_id"`
	SenderID  string    `json:"sender_id"`
	Text      string    `json:"text"`
	ClientID  string    `json:"client_id,omitempty"`
	Status    string    `json:"status"`
	CreatedAt time.Time `json:"created_at"`
}

type ChatDTO struct {
	ID          string      `json:"id"`
	Type        string      `json:"type"`
	Title       string      `json:"title"`
	AvatarURL   string      `json:"avatar_url"`
	Members     []UserDTO   `json:"members"`
	LastMessage *MessageDTO `json:"last_message"`
	UnreadCount int64       `json:"unread_count"`
	UpdatedAt   time.Time   `json:"updated_at"`
}

func (s *Server) userDTO(u models.User) UserDTO {
	username := ""
	if u.Username != nil {
		username = *u.Username
	}
	return UserDTO{
		ID: u.ID, Phone: u.Phone, Name: u.Name, Username: username, Bio: u.Bio,
		AvatarURL: u.AvatarURL, Online: s.hub.Online(u.ID), LastSeen: u.LastSeen.UTC(), CreatedAt: u.CreatedAt.UTC(),
	}
}

// messageDTO derives the delivery status from the other members' read cursors.
func messageDTO(m models.Message, members []models.ChatMember) MessageDTO {
	status := "sent"
	for _, cm := range members {
		if cm.UserID != m.SenderID && !cm.LastReadAt.Before(m.CreatedAt) {
			status = "read"
			break
		}
	}
	return MessageDTO{
		ID: m.ID, ChatID: m.ChatID, SenderID: m.SenderID, Text: m.Text,
		ClientID: m.ClientID, Status: status, CreatedAt: m.CreatedAt.UTC(),
	}
}

// buildChats assembles full chat views for uid with a fixed number of queries.
func (s *Server) buildChats(uid string, chatIDs []string) ([]ChatDTO, error) {
	if len(chatIDs) == 0 {
		return []ChatDTO{}, nil
	}
	var chats []models.Chat
	if err := s.db.Where("id IN ?", chatIDs).Find(&chats).Error; err != nil {
		return nil, err
	}
	var members []models.ChatMember
	if err := s.db.Where("chat_id IN ?", chatIDs).Order("joined_at").Find(&members).Error; err != nil {
		return nil, err
	}
	userIDs := make([]string, 0, len(members))
	membersByChat := map[string][]models.ChatMember{}
	for _, m := range members {
		membersByChat[m.ChatID] = append(membersByChat[m.ChatID], m)
		userIDs = append(userIDs, m.UserID)
	}
	var users []models.User
	if err := s.db.Where("id IN ?", userIDs).Find(&users).Error; err != nil {
		return nil, err
	}
	userByID := map[string]models.User{}
	for _, u := range users {
		userByID[u.ID] = u
	}

	var last []models.Message
	if err := s.db.Raw(`SELECT m.* FROM messages m
		WHERE m.chat_id IN ? AND m.created_at = (SELECT MAX(created_at) FROM messages WHERE chat_id = m.chat_id)`, chatIDs).
		Scan(&last).Error; err != nil {
		return nil, err
	}
	lastByChat := map[string]models.Message{}
	for _, m := range last {
		lastByChat[m.ChatID] = m
	}

	type unreadRow struct {
		ChatID string
		N      int64
	}
	var unread []unreadRow
	if err := s.db.Raw(`SELECT m.chat_id AS chat_id, COUNT(*) AS n FROM messages m
		JOIN chat_members cm ON cm.chat_id = m.chat_id AND cm.user_id = ?
		WHERE m.chat_id IN ? AND m.sender_id <> ? AND m.created_at > cm.last_read_at
		GROUP BY m.chat_id`, uid, chatIDs, uid).Scan(&unread).Error; err != nil {
		return nil, err
	}
	unreadByChat := map[string]int64{}
	for _, r := range unread {
		unreadByChat[r.ChatID] = r.N
	}

	out := make([]ChatDTO, 0, len(chats))
	for _, c := range chats {
		cms := membersByChat[c.ID]
		dto := ChatDTO{
			ID: c.ID, Type: c.Type, Title: c.Title, AvatarURL: c.AvatarURL,
			Members: make([]UserDTO, 0, len(cms)), UnreadCount: unreadByChat[c.ID], UpdatedAt: c.UpdatedAt.UTC(),
		}
		for _, cm := range cms {
			if u, ok := userByID[cm.UserID]; ok {
				dto.Members = append(dto.Members, s.userDTO(u))
			}
		}
		if m, ok := lastByChat[c.ID]; ok {
			md := messageDTO(m, cms)
			dto.LastMessage = &md
		}
		out = append(out, dto)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].UpdatedAt.After(out[j].UpdatedAt) })
	return out, nil
}

func (s *Server) buildChat(uid, chatID string) (ChatDTO, error) {
	chats, err := s.buildChats(uid, []string{chatID})
	if err != nil {
		return ChatDTO{}, err
	}
	if len(chats) == 0 {
		return ChatDTO{}, errNotFound
	}
	return chats[0], nil
}
