"use client";

import { ArrowLeft, MessageCircle } from "lucide-react";
import { useEffect, useState } from "react";
import { chatTitle, lastSeenLabel, peerOf, userDisplayName } from "@/lib/format";
import { t } from "@/lib/i18n";
import { useChatStore } from "@/lib/store";
import type { Chat, ID, User } from "@/types";
import { Avatar } from "./Avatar";
import { Composer } from "./Composer";
import { MessageList } from "./MessageList";

const EMPTY: ID[] = [];

function TypingDots() {
  return (
    <span className="ml-0.5 inline-flex items-end gap-0.5 pb-0.5" aria-hidden>
      <span className="typing-dot h-1 w-1 rounded-full bg-current" />
      <span className="typing-dot h-1 w-1 rounded-full bg-current" />
      <span className="typing-dot h-1 w-1 rounded-full bg-current" />
    </span>
  );
}

/** Re-render every 30 s so "last seen" labels stay fresh. */
function useMinuteTick() {
  const [, setN] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setN((n) => n + 1), 30_000);
    return () => clearInterval(id);
  }, []);
}

function Subtitle({ chat, me, typingIds }: { chat: Chat; me: User; typingIds: ID[] }) {
  useMinuteTick();
  const isGroup = chat.type === "group";

  if (typingIds.length) {
    let label = t.typing;
    if (isGroup) {
      if (typingIds.length === 1) {
        const u = chat.members.find((m) => m.id === typingIds[0]);
        label = t.typingUser(userDisplayName(u).split(" ")[0]);
      } else label = t.typingMany(typingIds.length);
    }
    return (
      <span className="flex items-center gap-1 text-brand-600 dark:text-sky-400">
        {label.replace(/…$/, "")}
        <TypingDots />
      </span>
    );
  }

  if (isGroup) {
    const online = chat.members.filter((m) => m.online && m.id !== me.id).length;
    return (
      <span className="text-slate-500 dark:text-slate-400">
        {online > 0 ? t.membersOnline(chat.members.length, online) : t.members(chat.members.length)}
      </span>
    );
  }

  const peer = peerOf(chat, me.id);
  if (!peer) return null;
  if (peer.online) return <span className="text-brand-600 dark:text-sky-400">{t.online}</span>;
  return <span className="text-slate-500 dark:text-slate-400">{lastSeenLabel(peer.last_seen)}</span>;
}

interface Props {
  chatId: ID | null;
  onBack: () => void;
}

export function Conversation({ chatId, onBack }: Props) {
  const me = useChatStore((s) => s.me);
  const chat = useChatStore((s) => (chatId ? s.chats.find((c) => c.id === chatId) : undefined));
  const typingIds = useChatStore((s) => (chatId ? (s.typing[chatId] ?? EMPTY) : EMPTY));

  if (!chatId || !chat || !me) {
    return (
      <div className="chat-wallpaper flex h-full flex-col items-center justify-center p-8 text-center">
        <div className="mb-5 flex h-24 w-24 items-center justify-center rounded-[2rem] bg-white/80 text-brand-600 shadow-xl shadow-brand-900/5 backdrop-blur dark:bg-slate-800/70 dark:text-sky-400">
          <MessageCircle size={44} />
        </div>
        <p className="text-lg font-semibold text-slate-700 dark:text-slate-200">{t.selectChat}</p>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{t.selectChatHint}</p>
      </div>
    );
  }

  const title = chatTitle(chat, me.id);
  const peer = peerOf(chat, me.id);
  const isGroup = chat.type === "group";

  return (
    <div className="chat-wallpaper flex h-full min-w-0 flex-col">
      <header className="z-10 flex items-center gap-3 border-b border-slate-200/70 bg-white/90 px-2 py-2 backdrop-blur-md sm:px-4 dark:border-white/5 dark:bg-app-dark-surface/90">
        <button
          onClick={onBack}
          className="rounded-full p-2 text-slate-500 transition hover:bg-slate-100 md:hidden dark:hover:bg-white/5"
          aria-label={t.back}
        >
          <ArrowLeft size={22} />
        </button>
        <Avatar
          name={title}
          seed={isGroup ? chat.id : (peer?.id ?? chat.id)}
          url={isGroup ? chat.avatar_url : peer?.avatar_url}
          online={!isGroup && !!peer?.online && peer.id !== me.id}
          group={isGroup}
          size={42}
        />
        <div className="min-w-0 flex-1">
          <div className="truncate font-semibold leading-tight text-slate-900 dark:text-white">{title}</div>
          <div className="truncate text-[13px] leading-tight">
            <Subtitle chat={chat} me={me} typingIds={typingIds} />
          </div>
        </div>
      </header>

      <MessageList key={`list-${chat.id}`} chat={chat} meId={me.id} />
      <Composer key={`composer-${chat.id}`} chatId={chat.id} />
    </div>
  );
}
