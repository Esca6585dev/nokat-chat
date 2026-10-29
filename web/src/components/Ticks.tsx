import { AlertCircle, Check, CheckCheck, Clock3 } from "lucide-react";
import { t } from "@/lib/i18n";
import type { UIMessage } from "@/types";

interface Props {
  message: Pick<UIMessage, "status" | "local">;
  /** "bubble" = on blue outgoing bubble, "list" = on neutral background */
  variant?: "bubble" | "list";
  size?: number;
}

export function Ticks({ message, variant = "bubble", size = 16 }: Props) {
  const base = variant === "bubble" ? "text-white/75" : "text-slate-400 dark:text-slate-500";
  const read = variant === "bubble" ? "text-sky-300" : "text-sky-500 dark:text-sky-400";
  if (message.local === "sending") return <Clock3 size={size - 3} className={base} aria-label={t.sending} />;
  if (message.local === "failed") return <AlertCircle size={size - 2} className="text-rose-400" aria-label={t.failed} />;
  if (message.status === "read") return <CheckCheck size={size} className={read} strokeWidth={2.4} />;
  if (message.status === "delivered") return <CheckCheck size={size} className={base} strokeWidth={2.2} />;
  return <Check size={size} className={base} strokeWidth={2.4} />;
}
