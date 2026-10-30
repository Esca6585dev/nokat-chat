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
	ChatSecret = "secret" // end-to-end encrypted 1:1 chat, see docs/SECRET_CHATS.md

	SecretPending = "pending"
	SecretActive  = "active"
)

type Chat struct {
	ID        string  `gorm:"primaryKey;size:36"`
	Type      string  `gorm:"size:10;not null"`
	Title     string  `gorm:"size:64"`
	AvatarURL string  `gorm:"size:512"`
	DirectKey *string `gorm:"uniqueIndex;size:80"` // "<minUserID>:<maxUserID>" for direct chats
	// Secret chats only. Keys are per-chat P-256 public keys (base64 raw point); private keys never reach the server.
	SecretState  string `gorm:"size:10"`
	InitiatorID  string `gorm:"size:36"`
	InitiatorKey string `gorm:"size:100"`
	PeerKey      string `gorm:"size:100"`
	CreatedAt    time.Time
	UpdatedAt    time.Time `gorm:"index"`
}

type ChatMember struct {
	ChatID     string    `gorm:"primaryKey;size:36"`
	UserID     string    `gorm:"primaryKey;size:36;index"`
	LastReadAt time.Time `gorm:"not null"`
	JoinedAt   time.Time `gorm:"not null"`
}

type Message struct {
	ID           string    `gorm:"primaryKey;size:36"`
	ChatID       string    `gorm:"size:36;not null;index:idx_chat_created,priority:1"`
	SenderID     string    `gorm:"size:36;not null"`
	Text         string    `gorm:"type:text;not null"`
	Ciphertext   string    `gorm:"type:text"` // secret chats: opaque client-encrypted payload
	ClientID     string    `gorm:"size:64"`
	AttachmentID *string   `gorm:"size:36;uniqueIndex"`
	CreatedAt    time.Time `gorm:"not null;index:idx_chat_created,priority:2"`
}

// Upload is a file stored under UPLOAD_DIR, attachable to exactly one message.
type Upload struct {
	ID         string `gorm:"primaryKey;size:36"`
	OwnerID    string `gorm:"size:36;not null;index"`
	StoredName string `gorm:"size:64;not null;uniqueIndex"`
	OrigName   string `gorm:"size:255;not null"`
	Mime       string `gorm:"size:128;not null"`
	Size       int64  `gorm:"not null"`
	Width      int
	Height     int
	CreatedAt  time.Time
}
