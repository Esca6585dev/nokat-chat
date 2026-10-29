package models

import "time"

type User struct {
	ID        string    `gorm:"primaryKey;size:36"`
	Phone     string    `gorm:"uniqueIndex;size:20;not null"`
	Name      string    `gorm:"size:64"`
	Username  *string   `gorm:"uniqueIndex;size:32"`
	Bio       string    `gorm:"size:200"`
	AvatarURL string    `gorm:"size:512"`
	LastSeen  time.Time `gorm:"not null"`
	CreatedAt time.Time `gorm:"not null"`
}

type OTPCode struct {
	Phone     string    `gorm:"primaryKey;size:20"`
	CodeHash  string    `gorm:"size:64;not null"`
	ExpiresAt time.Time `gorm:"not null"`
	SentAt    time.Time `gorm:"not null"`
	Attempts  int       `gorm:"not null;default:0"`
}

const (
	ChatDirect = "direct"
	ChatGroup  = "group"
)

type Chat struct {
	ID        string  `gorm:"primaryKey;size:36"`
	Type      string  `gorm:"size:10;not null"`
	Title     string  `gorm:"size:64"`
	AvatarURL string  `gorm:"size:512"`
	DirectKey *string `gorm:"uniqueIndex;size:80"` // "<minUserID>:<maxUserID>" for direct chats
	CreatedAt time.Time
	UpdatedAt time.Time `gorm:"index"`
}

type ChatMember struct {
	ChatID     string    `gorm:"primaryKey;size:36"`
	UserID     string    `gorm:"primaryKey;size:36;index"`
	LastReadAt time.Time `gorm:"not null"`
	JoinedAt   time.Time `gorm:"not null"`
}

type Message struct {
	ID        string    `gorm:"primaryKey;size:36"`
	ChatID    string    `gorm:"size:36;not null;index:idx_chat_created,priority:1"`
	SenderID  string    `gorm:"size:36;not null"`
	Text      string    `gorm:"type:text;not null"`
	ClientID  string    `gorm:"size:64"`
	CreatedAt time.Time `gorm:"not null;index:idx_chat_created,priority:2"`
}
