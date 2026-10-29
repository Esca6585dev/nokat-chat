package config

import (
	"os"
	"strconv"
	"time"
)

type Config struct {
	Addr          string
	DBDriver      string // "sqlite" | "postgres"
	DBDSN         string
	JWTSecret     string
	JWTTTL        time.Duration
	CORSOrigins   string
	OTPDevMode    bool
	OTPTTL        time.Duration
	OTPResend     time.Duration
	OTPMaxTries   int
	SMSWebhookURL string // optional: POST {"phone","text"} to an SMS gateway
}

func Load() Config {
	return Config{
		Addr:          env("ADDR", ":8080"),
		DBDriver:      env("DB_DRIVER", "sqlite"),
		DBDSN:         env("DB_DSN", "nokat.db"),
		JWTSecret:     env("JWT_SECRET", "change-me-in-production"),
		JWTTTL:        30 * 24 * time.Hour,
		CORSOrigins:   env("CORS_ORIGINS", "*"),
		OTPDevMode:    envBool("OTP_DEV_MODE", true),
		OTPTTL:        2 * time.Minute,
		OTPResend:     30 * time.Second,
		OTPMaxTries:   5,
		SMSWebhookURL: env("SMS_WEBHOOK_URL", ""),
	}
}

func env(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func envBool(key string, def bool) bool {
	v, err := strconv.ParseBool(os.Getenv(key))
	if err != nil {
		return def
	}
	return v
}
