"use client";

import { Lock } from "lucide-react";
import { memo } from "react";
import { chatTitle, listTime, messagePreview, peerOf, userDisplayName } from "@/lib/format";
import { t } from "@/lib/i18n";
import type { Chat, ID } from "@/types";
import { Avatar } from "./Avatar";
import { Ticks } from "./Ticks";

interface Props {
  chat: Chat;
  meId: ID | undefined;
  active: boolean;
  typingUsers: ID[];
  onClick: () => void;
}

function ChatListItemBase({ chat, meId, active, typingUsers, onClick }: Props) {
  const peer = peerOf(chat, meId);
  const title = chatTitle(chat, meId);
  const last = chat.last_message;
  const mine = !!last && last.sender_id === meId;
  const isGroup = chat.type === "group";
  const isSecret = chat.type === "secret";
  const pendingLabel =
    isSecret && chat.secret?.state === "pending"
      ? chat.secret.initiator_id === meId
        ? t.secretWaitingShort
        : t.secretInvitePreview
      : "";

  let typingLabel = "";
  if (typingUsers.length) {
    if (!isGroup) typingLabel = t.typing;
    else if (typingUsers.length === 1) {
      const u = chat.members.find((m) => m.id === typingUsers[0]);
      typingLabel = t.typingUser(userDisplayName(u).split(" ")[0]);
    } else typingLabel = t.typingMany(typingUsers.length);
  }

  let prefix = "";
  if (last && isGroup) {
    if (mine) prefix = `${t.you}: `;
    else {
      const sender = chat.members.find((m) => m.id === last.sender_id);
      if (sender) prefix = `${userDisplayName(sender).split(" ")[0]}: `;
    }
  }

  return (
    <button
      onClick={onClick}
      className={`group flex w-full items-center gap-3 rounded-2xl px-3 py-2.5 text-left transition ${
        active
          ? "bg-gradient-to-r from-brand-600 to-brand-500 text-white shadow-md shadow-brand-600/20"
          : "hover:bg-brand-50/80 dark:hover:bg-white/5"
      }`}
    >
      <Avatar
        name={title}
        seed={isGroup ? chat.id : (peer?.id ?? chat.id)}
        url={isGroup ? chat.avatar_url : peer?.avatar_url || chat.avatar_url}
        online={!isGroup && !!peer?.online && peer.id !== meId}
        group={isGroup}
        size={52}
        className={active ? "[&>span]:border-brand-500" : ""}
      />
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline gap-2">
          <span
            className={`flex min-w-0 items-center gap-1 font-semibold ${
              active ? "text-white" : isSecret ? "text-teal-600 dark:text-teal-400" : "text-slate-900 dark:text-slate-100"
            }`}
          >
            {isSecret && <Lock size={14} strokeWidth={2.6} className="shrink-0" aria-label={t.secretChat} />}
            <span className="truncate">{title}</span>
          </span>
          <span
            className={`ml-auto shrink-0 text-xs tabular-nums ${
              active ? "text-white/80" : chat.unread_count ? "font-medium text-brand-600 dark:text-sky-400" : "text-slate-400"
            }`}
          >
            {listTime(last?.created_at ?? chat.updated_at)}
          </span>
        </div>
        <div className="mt-0.5 flex items-center gap-1.5">
          {!typingLabel && !pendingLabel && mine && last && (
            <span className="shrink-0">
              <Ticks message={last} variant={active ? "bubble" : "list"} size={16} />
            </span>
          )}
          <span
            className={`min-w-0 flex-1 truncate text-sm ${
              typingLabel
                ? active
                  ? "text-white"
                  : "text-brand-600 dark:text-sky-400"
                : active
                  ? "text-white/85"
                  : "text-slate-500 dark:text-slate-400"
            }`}
          >
            {typingLabel ? (
              typingLabel
            ) : pendingLabel ? (
              <span className={active ? "text-white" : "text-teal-600 dark:text-teal-400"}>{pendingLabel}</span>
            ) : last ? (
              <>
                {prefix && (
                  <span className={active ? "text-white" : "text-slate-700 dark:text-slate-300"}>{prefix}</span>
                )}
                {messagePreview(last)}
              </>
            ) : (
              <span className="italic opacity-70">{isSecret ? t.e2eNotice : t.draftEmpty}</span>
            )}
          </span>
          {chat.unread_count > 0 && (
            <span
              className={`ml-1 flex h-5 min-w-5 shrink-0 items-center justify-center rounded-full px-1.5 text-xs font-semibold tabular-nums ${
                active ? "bg-white text-brand-600" : "bg-gradient-to-r from-sky-accent to-brand-500 text-white"
              }`}
            >
              {chat.unread_count > 999 ? "999+" : chat.unread_count}
            </span>
          )}
        </div>
      </div>
    </button>
  );
}

export const ChatListItem = memo(ChatListItemBase);
