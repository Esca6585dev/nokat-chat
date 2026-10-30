package api

import (
	"errors"
	"strings"
	"time"

	"github.com/gofiber/contrib/websocket"
	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/cors"
	"github.com/gofiber/fiber/v2/middleware/limiter"
	"github.com/gofiber/fiber/v2/middleware/logger"
	"github.com/gofiber/fiber/v2/middleware/recover"
	"gorm.io/gorm"

	"github.com/esca6585dev/nokat-chat/backend/internal/auth"
	"github.com/esca6585dev/nokat-chat/backend/internal/config"
	"github.com/esca6585dev/nokat-chat/backend/internal/hub"
)

var errNotFound = errors.New("not found")

type Server struct {
	cfg    config.Config
	db     *gorm.DB
	hub    *hub.Hub
	tokens auth.Tokens
	sms    auth.SMSSender
}

func NewServer(cfg config.Config, db *gorm.DB, h *hub.Hub, sms auth.SMSSender) *Server {
	return &Server{cfg: cfg, db: db, hub: h, sms: sms,
		tokens: auth.Tokens{Secret: []byte(cfg.JWTSecret), TTL: cfg.JWTTTL}}
}

func (s *Server) App() *fiber.App {
	app := fiber.New(fiber.Config{
		AppName:      "Nokat Chat API",
		BodyLimit:    21 << 20, // 20 MB uploads + multipart overhead
		ErrorHandler: errorHandler,
	})
	app.Use(recover.New(), logger.New(logger.Config{Format: "${time} ${status} ${method} ${path} ${latency}\n"}))
	app.Use(cors.New(cors.Config{
		AllowOrigins: s.cfg.CORSOrigins,
		AllowHeaders: "Origin, Content-Type, Accept, Authorization",
		AllowMethods: "GET,POST,PATCH,DELETE,OPTIONS",
	}))

	v1 := app.Group("/api/v1")
	v1.Get("/health", func(c *fiber.Ctx) error { return c.JSON(fiber.Map{"status": "ok"}) })

	authG := v1.Group("/auth", limiter.New(limiter.Config{Max: 20, Expiration: time.Minute}))
	authG.Post("/otp/request", s.requestOTP)
	authG.Post("/otp/verify", s.verifyOTP)

	v1.Get("/files/:name", s.serveFile)

	v1.Use("/ws", s.wsAuth)
	v1.Get("/ws", websocket.New(s.wsHandler))

	p := v1.Group("", s.requireAuth)
	p.Get("/me", s.getMe)
	p.Patch("/me", s.updateMe)
	p.Post("/uploads", limiter.New(limiter.Config{Max: 60, Expiration: time.Minute,
		KeyGenerator: func(c *fiber.Ctx) string { return uid(c) }}), s.upload)
	p.Get("/users/search", s.searchUsers)
	p.Get("/users/:id", s.getUser)
	p.Get("/chats", s.listChats)
	p.Post("/chats/direct", s.createDirect)
	p.Post("/chats/group", s.createGroup)
	p.Post("/chats/secret", s.createSecret)
	p.Post("/chats/:id/secret/accept", s.acceptSecret)
	p.Delete("/chats/:id", s.deleteChat)
	p.Get("/chats/:id", s.getChat)
	p.Get("/chats/:id/messages", s.listMessages)
	p.Post("/chats/:id/messages", s.postMessage)
	p.Post("/chats/:id/read", s.markReadHandler)
	return app
}

func errorHandler(c *fiber.Ctx, err error) error {
	code := fiber.StatusInternalServerError
	msg := "server ýalňyşlygy"
	var fe *fiber.Error
	switch {
	case errors.As(err, &fe):
		code, msg = fe.Code, fe.Message
	case errors.Is(err, errNotFound), errors.Is(err, gorm.ErrRecordNotFound):
		code, msg = fiber.StatusNotFound, "tapylmady"
	}
	return c.Status(code).JSON(fiber.Map{"error": msg})
}

func (s *Server) requireAuth(c *fiber.Ctx) error {
	h := c.Get(fiber.HeaderAuthorization)
	token, ok := strings.CutPrefix(h, "Bearer ")
	if !ok {
		return fiber.NewError(fiber.StatusUnauthorized, "awtorizasiýa gerek")
	}
	uid, err := s.tokens.Parse(token)
	if err != nil {
		return fiber.NewError(fiber.StatusUnauthorized, "token nädogry")
	}
	c.Locals("uid", uid)
	return c.Next()
}

func uid(c *fiber.Ctx) string { return c.Locals("uid").(string) }

func bad(msg string) error { return fiber.NewError(fiber.StatusBadRequest, msg) }
