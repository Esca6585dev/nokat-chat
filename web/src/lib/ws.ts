import { wsUrl } from "./config";
import type { ClientEvent, ServerEvent } from "@/types";

export type WsStatus = "idle" | "connecting" | "open" | "closed";

interface Handlers {
  onEvent: (ev: ServerEvent) => void;
  onStatus?: (s: WsStatus) => void;
  /** Fired on every successful (re)connect; `reconnect` is false on the first one. */
  onOpen?: (reconnect: boolean) => void;
}

const PING_MS = 25_000;
const MIN_BACKOFF = 1_000;
const MAX_BACKOFF = 30_000;

/**
 * Thin WebSocket wrapper: JSON frames, ping every 25 s, exponential backoff
 * reconnect (1 s → 30 s, with jitter).
 */
export class RealtimeClient {
  private ws: WebSocket | null = null;
  private token: string;
  private handlers: Handlers;
  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private backoff = MIN_BACKOFF;
  private everOpened = false;
  private stopped = false;

  constructor(token: string, handlers: Handlers) {
    this.token = token;
    this.handlers = handlers;
  }

  start() {
    this.stopped = false;
    this.connect();
    if (typeof window !== "undefined") {
      window.addEventListener("online", this.handleOnline);
    }
  }

  stop() {
    this.stopped = true;
    if (typeof window !== "undefined") {
      window.removeEventListener("online", this.handleOnline);
    }
    this.clearTimers();
    if (this.ws) {
      this.ws.onclose = null;
      this.ws.onerror = null;
      this.ws.onmessage = null;
      this.ws.onopen = null;
      this.ws.close();
      this.ws = null;
    }
    this.handlers.onStatus?.("closed");
  }

  get isOpen() {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  send(ev: ClientEvent): boolean {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(ev));
      return true;
    }
    return false;
  }

  private handleOnline = () => {
    // Network came back: retry immediately instead of waiting for the backoff.
    if (!this.stopped && !this.isOpen) {
      if (this.retryTimer) clearTimeout(this.retryTimer);
      this.retryTimer = null;
      this.backoff = MIN_BACKOFF;
      this.connect();
    }
  };

  private clearTimers() {
    if (this.pingTimer) clearInterval(this.pingTimer);
    if (this.retryTimer) clearTimeout(this.retryTimer);
    this.pingTimer = null;
    this.retryTimer = null;
  }

  private connect() {
    if (this.stopped) return;
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }
    this.handlers.onStatus?.("connecting");
    let ws: WebSocket;
    try {
      ws = new WebSocket(wsUrl(this.token));
    } catch {
      this.scheduleReconnect();
      return;
    }
    this.ws = ws;

    ws.onopen = () => {
      const reconnect = this.everOpened;
      this.everOpened = true;
      this.backoff = MIN_BACKOFF;
      this.handlers.onStatus?.("open");
      if (this.pingTimer) clearInterval(this.pingTimer);
      this.pingTimer = setInterval(() => this.send({ type: "ping", data: {} }), PING_MS);
      this.handlers.onOpen?.(reconnect);
    };

    ws.onmessage = (e) => {
      if (typeof e.data !== "string") return;
      try {
        const ev = JSON.parse(e.data) as ServerEvent;
        if (ev && typeof ev.type === "string") this.handlers.onEvent(ev);
      } catch {
        /* ignore malformed frame */
      }
    };

    ws.onerror = () => {
      // onclose follows; reconnect handled there.
    };

    ws.onclose = () => {
      if (this.pingTimer) clearInterval(this.pingTimer);
      this.pingTimer = null;
      this.ws = null;
      this.handlers.onStatus?.("closed");
      this.scheduleReconnect();
    };
  }

  private scheduleReconnect() {
    if (this.stopped) return;
    const delay = Math.min(this.backoff, MAX_BACKOFF);
    const jitter = Math.random() * 0.3 * delay;
    this.backoff = Math.min(this.backoff * 2, MAX_BACKOFF);
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      this.connect();
    }, delay + jitter);
  }
}
