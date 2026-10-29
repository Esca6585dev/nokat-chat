import type { User } from "@/types";

const TOKEN_KEY = "nokat.token";
const USER_KEY = "nokat.user";

function safe<T>(fn: () => T, fallback: T): T {
  try {
    return fn();
  } catch {
    return fallback;
  }
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return safe(() => window.localStorage.getItem(TOKEN_KEY), null);
}

export function setSession(token: string, user: User) {
  safe(() => {
    window.localStorage.setItem(TOKEN_KEY, token);
    window.localStorage.setItem(USER_KEY, JSON.stringify(user));
  }, undefined);
}

export function getCachedUser(): User | null {
  if (typeof window === "undefined") return null;
  return safe(() => {
    const raw = window.localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as User) : null;
  }, null);
}

export function cacheUser(user: User) {
  safe(() => window.localStorage.setItem(USER_KEY, JSON.stringify(user)), undefined);
}

export function clearSession() {
  safe(() => {
    window.localStorage.removeItem(TOKEN_KEY);
    window.localStorage.removeItem(USER_KEY);
  }, undefined);
}
