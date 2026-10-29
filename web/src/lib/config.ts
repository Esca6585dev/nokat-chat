const DEFAULT_API_URL = "http://localhost:8080/api/v1";

export const API_URL = (process.env.NEXT_PUBLIC_API_URL || DEFAULT_API_URL).replace(/\/+$/, "");

/** WebSocket endpoint derived from the REST base URL (http→ws, https→wss). */
export function wsUrl(token: string): string {
  let base = API_URL;
  // Support a relative API URL such as "/api/v1" (same origin as the web app).
  if (base.startsWith("/") && typeof window !== "undefined") {
    base = window.location.origin + base;
  }
  base = base.replace(/^http(s?):\/\//i, (_m, s: string) => `ws${s}://`);
  return `${base}/ws?token=${encodeURIComponent(token)}`;
}
