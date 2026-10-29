import { ApiError } from "./api";
import { t } from "./i18n";

export function errorMessage(e: unknown, overrides: Partial<Record<number, string>> = {}): string {
  if (e instanceof ApiError) {
    if (overrides[e.status]) return overrides[e.status]!;
    if (e.status === 0) return t.networkError;
    if (e.status === 429) return t.tooManyRequests;
    return e.message || t.somethingWrong;
  }
  return t.somethingWrong;
}
