package hub

import (
	"encoding/json"
	"sync"
)

// Event is the envelope for every WebSocket frame.
type Event struct {
	Type string `json:"type"`
	Data any    `json:"data"`
}

type Client struct {
	UserID string
	Send   chan []byte
}

// Hub tracks live connections per user. A user may have several (phone + web).
type Hub struct {
	mu      sync.RWMutex
	clients map[string]map[*Client]struct{}
}

func New() *Hub {
	return &Hub{clients: make(map[string]map[*Client]struct{})}
}

// Register adds a client and reports whether the user just came online.
func (h *Hub) Register(c *Client) (firstConn bool) {
	h.mu.Lock()
	defer h.mu.Unlock()
	set, ok := h.clients[c.UserID]
	if !ok {
		set = make(map[*Client]struct{})
		h.clients[c.UserID] = set
	}
	set[c] = struct{}{}
	return len(set) == 1
}

// Unregister removes a client and reports whether the user went offline.
func (h *Hub) Unregister(c *Client) (lastConn bool) {
	h.mu.Lock()
	defer h.mu.Unlock()
	set, ok := h.clients[c.UserID]
	if !ok {
		return false
	}
	if _, ok := set[c]; !ok {
		return false
	}
	delete(set, c)
	close(c.Send)
	if len(set) == 0 {
		delete(h.clients, c.UserID)
		return true
	}
	return false
}

func (h *Hub) Online(userID string) bool {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.clients[userID]) > 0
}

// SendTo pushes an event to every connection of the given users, skipping slow clients.
func (h *Hub) SendTo(userIDs []string, ev Event) {
	payload, err := json.Marshal(ev)
	if err != nil {
		return
	}
	h.mu.RLock()
	defer h.mu.RUnlock()
	for _, id := range userIDs {
		for c := range h.clients[id] {
			select {
			case c.Send <- payload:
			default: // buffer full: drop rather than block the whole hub
			}
		}
	}
}
