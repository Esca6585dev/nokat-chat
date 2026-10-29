"use client";

import { memo } from "react";
import { gradientFor, timeHM, userDisplayName } from "@/lib/format";
import { t } from "@/lib/i18n";
import type { UIMessage, User } from "@/types";
import { Avatar } from "./Avatar";
import { Ticks } from "./Ticks";

interface Props {
  message: UIMessage;
  mine: boolean;
  /** first message of a run from the same sender */
  first: boolean;
  /** last message of a run (gets the tail) */
  last: boolean;
  /** group chat: show sender name/avatar for incoming */
  sender?: User;
  showSender: boolean;
  onRetry?: () => void;
}

const nameColors: Record<string, string> = {
  "from-sky-400 to-blue-600": "text-sky-600 dark:text-sky-400",
  "from-cyan-400 to-sky-600": "text-cyan-600 dark:text-cyan-400",
  "from-indigo-400 to-blue-700": "text-indigo-600 dark:text-indigo-400",
  "from-blue-400 to-indigo-600": "text-blue-600 dark:text-blue-400",
  "from-teal-400 to-cyan-600": "text-teal-600 dark:text-teal-400",
  "from-violet-400 to-indigo-600": "text-violet-600 dark:text-violet-400",
  "from-sky-300 to-blue-500": "text-sky-600 dark:text-sky-300",
  "from-emerald-400 to-teal-600": "text-emerald-600 dark:text-emerald-400",
};

/** Emoji-only short messages are rendered large, like Telegram. */
function isJumboEmoji(text: string) {
  const s = text.trim();
  if (!s || s.length > 16) return false;
  return /^(?:\p{Extended_Pictographic}|\p{Emoji_Component}|‍|️|\s)+$/u.test(s) && !/^[\d#*\s]+$/.test(s);
}

function MessageBubbleBase({ message, mine, first, last, sender, showSender, onRetry }: Props) {
  const failed = message.local === "failed";
  const jumbo = isJumboEmoji(message.text);
  const time = timeHM(message.created_at);

  const radius = mine
    ? `rounded-2xl ${first ? "" : "rounded-tr-md"} ${last ? "rounded-br-sm" : "rounded-br-md"}`
    : `rounded-2xl ${first ? "" : "rounded-tl-md"} ${last ? "rounded-bl-sm" : "rounded-bl-md"}`;

  const meta = (
    <span
      className={`float-right ml-2 mt-1.5 flex translate-y-1 select-none items-center gap-1 text-[11px] leading-none tabular-nums ${
        jumbo
          ? "rounded-full bg-black/25 px-1.5 py-1 text-white"
          : mine
            ? "text-white/75"
            : "text-slate-400 dark:text-slate-500"
      }`}
    >
      {time}
      {mine && <Ticks message={message} size={15} />}
    </span>
  );

  return (
    <div className={`flex items-end gap-2 ${mine ? "justify-end" : "justify-start"} ${last ? "mb-2" : "mb-0.5"}`}>
      {showSender && !mine && (
        <div className="w-8 shrink-0">
          {last && sender && (
            <Avatar name={userDisplayName(sender)} seed={sender.id} url={sender.avatar_url} size={32} />
          )}
        </div>
      )}
      <div
        className={`group relative max-w-[min(80%,34rem)] ${failed ? "cursor-pointer" : ""}`}
        onClick={failed ? onRetry : undefined}
        title={failed ? t.failed : undefined}
      >
        {jumbo ? (
          <div className="px-1">
            {showSender && !mine && first && sender && (
              <div className={`mb-0.5 text-[13px] font-semibold ${nameColors[gradientFor(sender.id)]}`}>
                {userDisplayName(sender)}
              </div>
            )}
            <div className="text-5xl leading-tight">{message.text.trim()}</div>
            <div className={`mt-0.5 flex ${mine ? "justify-end" : "justify-start"}`}>{meta}</div>
          </div>
        ) : (
          <div
            className={`${radius} px-3 pb-2 pt-1.5 shadow-sm ${
              mine
                ? "bg-gradient-to-br from-brand-600 to-brand-500 text-white shadow-brand-600/20"
                : "bg-white text-slate-900 shadow-slate-900/5 dark:bg-bubble-in-dark dark:text-slate-100"
            } ${failed ? "opacity-70" : ""} ${message.local === "sending" ? "opacity-90" : ""}`}
          >
            {showSender && !mine && first && sender && (
              <div className={`text-[13px] font-semibold ${nameColors[gradientFor(sender.id)]}`}>
                {userDisplayName(sender)}
              </div>
            )}
            <div className="whitespace-pre-wrap break-words text-[15px] leading-[1.35rem] [overflow-wrap:anywhere]">
              {message.text}
              {meta}
            </div>
          </div>
        )}
        {failed && <div className="mt-1 text-right text-xs text-rose-500">{t.failed}</div>}
      </div>
    </div>
  );
}

export const MessageBubble = memo(MessageBubbleBase);
