package db

import (
	"fmt"
	"time"

	"github.com/glebarez/sqlite"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"

	"github.com/esca6585dev/nokat-chat/backend/internal/models"
)

func Open(driver, dsn string) (*gorm.DB, error) {
	var dialector gorm.Dialector
	switch driver {
	case "sqlite":
		// WAL + busy timeout keeps concurrent websocket writers from tripping "database is locked".
		dialector = sqlite.Open(dsn + "?_pragma=journal_mode(WAL)&_pragma=busy_timeout(5000)&_pragma=foreign_keys(1)")
	case "postgres":
		dialector = postgres.Open(dsn)
	default:
		return nil, fmt.Errorf("unknown DB_DRIVER %q", driver)
	}
	db, err := gorm.Open(dialector, &gorm.Config{
		Logger:  logger.Default.LogMode(logger.Warn),
		NowFunc: func() time.Time { return time.Now().UTC() },
	})
	if err != nil {
		return nil, err
	}
	if driver == "sqlite" {
		sqlDB, err := db.DB()
		if err != nil {
			return nil, err
		}
		sqlDB.SetMaxOpenConns(1)
	}
	err = db.AutoMigrate(&models.User{}, &models.OTPCode{}, &models.Chat{}, &models.ChatMember{}, &models.Message{})
	return db, err
}
