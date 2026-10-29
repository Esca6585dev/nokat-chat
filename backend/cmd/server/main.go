package main

import (
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/esca6585dev/nokat-chat/backend/internal/api"
	"github.com/esca6585dev/nokat-chat/backend/internal/auth"
	"github.com/esca6585dev/nokat-chat/backend/internal/config"
	"github.com/esca6585dev/nokat-chat/backend/internal/db"
	"github.com/esca6585dev/nokat-chat/backend/internal/hub"
)

func main() {
	cfg := config.Load()
	if cfg.JWTSecret == "change-me-in-production" && !cfg.OTPDevMode {
		log.Fatal("JWT_SECRET must be set when OTP_DEV_MODE=false")
	}

	database, err := db.Open(cfg.DBDriver, cfg.DBDSN)
	if err != nil {
		log.Fatalf("db: %v", err)
	}

	var sms auth.SMSSender = auth.LogSender{}
	if cfg.SMSWebhookURL != "" {
		sms = auth.WebhookSender{URL: cfg.SMSWebhookURL, Client: &http.Client{Timeout: 10 * time.Second}}
	}

	app := api.NewServer(cfg, database, hub.New(), sms).App()

	go func() {
		quit := make(chan os.Signal, 1)
		signal.Notify(quit, os.Interrupt, syscall.SIGTERM)
		<-quit
		_ = app.ShutdownWithTimeout(10 * time.Second)
	}()

	log.Printf("Nokat Chat API listening on %s (db=%s, otp_dev_mode=%v)", cfg.Addr, cfg.DBDriver, cfg.OTPDevMode)
	if err := app.Listen(cfg.Addr); err != nil {
		log.Fatal(err)
	}
}
