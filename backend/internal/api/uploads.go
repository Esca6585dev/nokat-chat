package api

import (
	"fmt"
	"image"
	_ "image/gif"
	_ "image/jpeg"
	_ "image/png"
	"io"
	"mime"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
	_ "golang.org/x/image/webp"

	"github.com/esca6585dev/nokat-chat/backend/internal/models"
)

// Only these are rendered inline; anything else (incl. SVG/HTML) is download-only.
var imageExt = map[string]string{
	"image/jpeg": ".jpg",
	"image/png":  ".png",
	"image/gif":  ".gif",
	"image/webp": ".webp",
}

var (
	extRe        = regexp.MustCompile(`^\.[a-z0-9]{1,10}$`)
	storedNameRe = regexp.MustCompile(`^[0-9a-f-]{36}(\.[a-z0-9]{1,10})?$`)
)

type AttachmentDTO struct {
	ID     string `json:"id"`
	URL    string `json:"url"`
	Name   string `json:"name"`
	Size   int64  `json:"size"`
	Mime   string `json:"mime"`
	Kind   string `json:"kind"`
	Width  int    `json:"width"`
	Height int    `json:"height"`
}

func uploadKind(u models.Upload) string {
	if _, ok := imageExt[u.Mime]; ok {
		return "image"
	}
	return "file"
}

func attachmentDTO(u models.Upload) AttachmentDTO {
	return AttachmentDTO{
		ID: u.ID, URL: "/api/v1/files/" + u.StoredName, Name: u.OrigName, Size: u.Size,
		Mime: u.Mime, Kind: uploadKind(u), Width: u.Width, Height: u.Height,
	}
}

func (s *Server) upload(c *fiber.Ctx) error {
	fh, err := c.FormFile("file")
	if err != nil {
		return bad("faýl gerek (meýdan: file)")
	}
	if fh.Size > s.cfg.MaxUpload {
		return fiber.NewError(fiber.StatusRequestEntityTooLarge, "faýl gaty uly (iň köp 20 MB)")
	}
	if fh.Size == 0 {
		return bad("faýl boş")
	}
	f, err := fh.Open()
	if err != nil {
		return err
	}
	defer f.Close()

	head := make([]byte, 512)
	n, _ := io.ReadFull(f, head)
	head = head[:n]
	mimeType := strings.Split(http.DetectContentType(head), ";")[0]

	origName := sanitizeName(fh.Filename)
	ext, isImage := imageExt[mimeType]
	if !isImage {
		ext = strings.ToLower(filepath.Ext(origName))
		if !extRe.MatchString(ext) {
			ext = ""
		}
		if byExt := mime.TypeByExtension(ext); ext != "" && byExt != "" {
			mimeType = strings.Split(byExt, ";")[0]
		}
		if _, ok := imageExt[mimeType]; ok {
			// Extension claims an image the sniffer didn't confirm: keep it download-only.
			mimeType = "application/octet-stream"
		}
	}

	up := models.Upload{
		ID: uuid.NewString(), OwnerID: uid(c), OrigName: origName, Mime: mimeType,
		Size: fh.Size, CreatedAt: time.Now().UTC(),
	}
	up.StoredName = uuid.NewString() + ext

	if isImage {
		if _, err := f.Seek(0, io.SeekStart); err == nil {
			if cfg, _, err := image.DecodeConfig(f); err == nil {
				up.Width, up.Height = cfg.Width, cfg.Height
			}
		}
	}

	if err := os.MkdirAll(s.cfg.UploadDir, 0o755); err != nil {
		return err
	}
	if err := c.SaveFile(fh, filepath.Join(s.cfg.UploadDir, up.StoredName)); err != nil {
		return err
	}
	if err := s.db.Create(&up).Error; err != nil {
		_ = os.Remove(filepath.Join(s.cfg.UploadDir, up.StoredName))
		return err
	}
	return c.Status(fiber.StatusCreated).JSON(attachmentDTO(up))
}

func (s *Server) serveFile(c *fiber.Ctx) error {
	name := c.Params("name")
	if !storedNameRe.MatchString(name) {
		return errNotFound
	}
	var up models.Upload
	if err := s.db.First(&up, "stored_name = ?", name).Error; err != nil {
		return err
	}
	if err := c.SendFile(filepath.Join(s.cfg.UploadDir, up.StoredName)); err != nil {
		return err
	}
	if c.Response().StatusCode() != fiber.StatusOK {
		return nil
	}
	c.Set(fiber.HeaderContentType, up.Mime)
	c.Set("X-Content-Type-Options", "nosniff")
	c.Set("Content-Security-Policy", "default-src 'none'; sandbox")
	c.Set(fiber.HeaderCacheControl, "public, max-age=31536000, immutable")
	c.Set("Cross-Origin-Resource-Policy", "cross-origin")
	disposition := "inline"
	if uploadKind(up) != "image" {
		disposition = "attachment"
	}
	c.Set(fiber.HeaderContentDisposition, fmt.Sprintf(`%s; filename="%s"; filename*=UTF-8''%s`,
		disposition, asciiName(up.OrigName), url.PathEscape(up.OrigName)))
	return nil
}

func sanitizeName(name string) string {
	name = filepath.Base(strings.ReplaceAll(name, `\`, "/"))
	name = strings.Map(func(r rune) rune {
		if r < 32 || r == 127 || r == '"' || r == '/' {
			return -1
		}
		return r
	}, name)
	name = strings.TrimSpace(name)
	if name == "" || name == "." {
		name = "file"
	}
	if r := []rune(name); len(r) > 200 {
		name = string(r[len(r)-200:])
	}
	return name
}

func asciiName(name string) string {
	return strings.Map(func(r rune) rune {
		if r > 126 || r == '"' || r == '\\' {
			return '_'
		}
		return r
	}, name)
}
