import { months, t, weekdaysShort } from "./i18n";
import type { Chat, ID, Message, User } from "@/types";

const pad = (n: number) => String(n).padStart(2, "0");

export function parseDate(s: string | undefined | null): Date | null {
  if (!s) return null;
  const d = new Date(s);
  return isNaN(d.getTime()) ? null : d;
}

export function timeHM(s: string): string {
  const d = parseDate(s);
  return d ? `${pad(d.getHours())}:${pad(d.getMinutes())}` : "";
}

function startOfDay(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

export function sameDay(a: string, b: string): boolean {
  const da = parseDate(a);
  const db = parseDate(b);
  return !!da && !!db && startOfDay(da) === startOfDay(db);
}

/** Time label used in the chat list. */
export function listTime(s: string | undefined): string {
  const d = parseDate(s);
  if (!d) return "";
  const now = new Date();
  const diffDays = Math.round((startOfDay(now) - startOfDay(d)) / 86_400_000);
  if (diffDays <= 0) return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  if (diffDays === 1) return t.yesterday;
  if (diffDays < 7) return weekdaysShort[d.getDay()];
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${String(d.getFullYear()).slice(2)}`;
}

/** Label for the sticky date separator inside a conversation. */
export function dayLabel(s: string): string {
  const d = parseDate(s);
  if (!d) return "";
  const now = new Date();
  const diffDays = Math.round((startOfDay(now) - startOfDay(d)) / 86_400_000);
  if (diffDays === 0) return t.today;
  if (diffDays === 1) return t.yesterday;
  const base = `${d.getDate()} ${months[d.getMonth()]}`;
  return d.getFullYear() === now.getFullYear() ? base : `${base} ${d.getFullYear()}`;
}

export function lastSeenLabel(s: string | undefined): string {
  const d = parseDate(s);
  if (!d || d.getFullYear() < 2000) return t.offline;
  const diffMs = Date.now() - d.getTime();
  if (diffMs < 60_000) return t.lastSeenJustNow;
  const now = new Date();
  const diffDays = Math.round((startOfDay(now) - startOfDay(d)) / 86_400_000);
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  if (diffDays === 0) return t.lastSeen(`${t.today.toLowerCase()} ${hm}`);
  if (diffDays === 1) return t.lastSeen(`${t.yesterday.toLowerCase()} ${hm}`);
  return t.lastSeen(`${dayLabel(s!)} ${hm}`);
}

export function userDisplayName(u: User | undefined | null): string {
  if (!u) return "";
  return u.name || (u.username ? `@${u.username}` : u.phone);
}

export function peerOf(chat: Chat, meId: ID | undefined): User | undefined {
  if (chat.type !== "direct") return undefined;
  return chat.members.find((m) => m.id !== meId) ?? chat.members[0];
}

export function chatTitle(chat: Chat, meId: ID | undefined): string {
  if (chat.type === "group") return chat.title || t.newGroup;
  return userDisplayName(peerOf(chat, meId)) || chat.title || t.appName;
}

export function initials(name: string): string {
  const clean = name.replace(/^[@+]/, "").trim();
  if (!clean) return "?";
  const parts = clean.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) return (Array.from(parts[0])[0] + Array.from(parts[1])[0]).toUpperCase();
  const chars = Array.from(parts[0]);
  if (/^\d+$/.test(parts[0])) return chars.slice(-2).join("");
  return chars.slice(0, 2).join("").toUpperCase();
}

const GRADIENTS = [
  "from-sky-400 to-blue-600",
  "from-cyan-400 to-sky-600",
  "from-indigo-400 to-blue-700",
  "from-blue-400 to-indigo-600",
  "from-teal-400 to-cyan-600",
  "from-violet-400 to-indigo-600",
  "from-sky-300 to-blue-500",
  "from-emerald-400 to-teal-600",
];

export function gradientFor(seed: string): string {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) | 0;
  return GRADIENTS[Math.abs(h) % GRADIENTS.length];
}

export function newClientId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    try {
      return crypto.randomUUID();
    } catch {
      /* insecure context */
    }
  }
  return `c-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

export function humanSize(bytes: number): string {
  if (!isFinite(bytes) || bytes < 0) return "";
  let i = 0;
  let n = bytes;
  while (n >= 1024 && i < t.sizeUnits.length - 1) {
    n /= 1024;
    i++;
  }
  const v = i === 0 ? String(n) : n < 10 ? n.toFixed(1) : String(Math.round(n));
  return `${v.replace(/\.0$/, "")} ${t.sizeUnits[i]}`;
}

/** One-line preview of a message for chat lists etc. */
export function messagePreview(m: Pick<Message, "kind" | "text" | "attachment">): string {
  const text = (m.text ?? "").replace(/\s+/g, " ").trim();
  const kind = m.kind || (m.attachment ? m.attachment.kind : "text");
  if (kind === "image") return `📷 ${text || t.photo}`;
  if (kind === "file") return `📎 ${m.attachment?.name || text || t.file}`;
  return text;
}
