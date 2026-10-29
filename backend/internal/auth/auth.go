package auth

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"math/big"
	"net/http"
	"regexp"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

var phoneRe = regexp.MustCompile(`^\+[1-9][0-9]{7,14}$`)

// NormalizePhone strips formatting characters and validates E.164.
func NormalizePhone(raw string) (string, error) {
	r := strings.NewReplacer(" ", "", "-", "", "(", "", ")", "")
	p := r.Replace(strings.TrimSpace(raw))
	if strings.HasPrefix(p, "00") {
		p = "+" + p[2:]
	}
	if !phoneRe.MatchString(p) {
		return "", errors.New("telefon belgisi nädogry (meselem +99365123456)")
	}
	return p, nil
}

// GenerateCode returns a uniformly random 6-digit code.
func GenerateCode() (string, error) {
	n, err := rand.Int(rand.Reader, big.NewInt(1_000_000))
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%06d", n.Int64()), nil
}

// HashCode binds the code to the phone so a leaked hash can't be replayed elsewhere.
func HashCode(secret, phone, code string) string {
	m := hmac.New(sha256.New, []byte(secret))
	m.Write([]byte(phone + ":" + code))
	return hex.EncodeToString(m.Sum(nil))
}

func CodeMatches(secret, phone, code, hash string) bool {
	return hmac.Equal([]byte(HashCode(secret, phone, code)), []byte(hash))
}

type Tokens struct {
	Secret []byte
	TTL    time.Duration
}

func (t Tokens) Issue(userID string) (string, error) {
	now := time.Now()
	claims := jwt.RegisteredClaims{
		Subject:   userID,
		IssuedAt:  jwt.NewNumericDate(now),
		ExpiresAt: jwt.NewNumericDate(now.Add(t.TTL)),
		Issuer:    "nokat-chat",
	}
	return jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString(t.Secret)
}

func (t Tokens) Parse(token string) (string, error) {
	claims := &jwt.RegisteredClaims{}
	_, err := jwt.ParseWithClaims(token, claims, func(*jwt.Token) (any, error) { return t.Secret, nil },
		jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}), jwt.WithIssuer("nokat-chat"))
	if err != nil {
		return "", err
	}
	return claims.Subject, nil
}

// SMSSender delivers OTP codes. Plug in a real gateway via SMS_WEBHOOK_URL.
type SMSSender interface {
	Send(ctx context.Context, phone, text string) error
}

type LogSender struct{}

func (LogSender) Send(_ context.Context, phone, text string) error {
	log.Printf("[sms] to=%s text=%q", phone, text)
	return nil
}

type WebhookSender struct {
	URL    string
	Client *http.Client
}

func (w WebhookSender) Send(ctx context.Context, phone, text string) error {
	body, _ := json.Marshal(map[string]string{"phone": phone, "text": text})
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, w.URL, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := w.Client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return fmt.Errorf("sms gateway status %d", resp.StatusCode)
	}
	return nil
}
