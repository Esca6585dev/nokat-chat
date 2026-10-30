"use client";

import { ArrowLeft, KeyRound, Lock, MessageCircle, Trash2, UploadCloud } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { chatTitle, lastSeenLabel, peerOf, userDisplayName } from "@/lib/format";
import { t } from "@/lib/i18n";
import { useChatStore } from "@/lib/store";
import type { Chat, ID, User } from "@/types";
import { Avatar } from "./Avatar";
import { Composer, type ComposerHandle } from "./Composer";
import { MessageList } from "./MessageList";
import {
  FingerprintDialog,
  HeaderMenu,
  OtherDeviceBanner,
  SecretInviteCard,
  SecretWaitingCard,
  secretViewOf,
  type MenuItem,
} from "./SecretChatPanels";
import { Spinner } from "./Spinner";

const EMPTY: ID[] = [];

const hasFiles = (e: React.DragEvent) => Array.from(e.dataTransfer?.types ?? []).includes("Files");

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
  onOpenChat: (id: ID) => void;
}

export function Conversation({ chatId, onBack, onOpenChat }: Props) {
  const me = useChatStore((s) => s.me);
  const chat = useChatStore((s) => (chatId ? s.chats.find((c) => c.id === chatId) : undefined));
  const typingIds = useChatStore((s) => (chatId ? (s.typing[chatId] ?? EMPTY) : EMPTY));
  const hasKey = useChatStore((s) => (chatId ? s.secretKeys[chatId] : undefined));
  const startSecretChat = useChatStore((s) => s.startSecretChat);
  const deleteChat = useChatStore((s) => s.deleteChat);
  const [fpOpen, setFpOpen] = useState(false);
  const composer = useRef<ComposerHandle>(null);
  const [dragging, setDragging] = useState(false);
  const dragDepth = useRef(0);

  useEffect(() => {
    dragDepth.current = 0;
    setDragging(false);
    setFpOpen(false);
  }, [chatId]);

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
  const isSecret = chat.type === "secret";
  const view = secretViewOf(chat, me.id, hasKey);
  const canCompose = view === "none" || view === "active";
  const peerName = userDisplayName(peer) || title;

  const menu: MenuItem[] = [];
  if (chat.type === "direct" && peer && peer.id !== me.id) {
    menu.push({
      label: t.startSecretChat,
      icon: <Lock size={18} className="text-teal-600 dark:text-teal-400" />,
      onClick: async () => {
        const c = await startSecretChat(peer.id);
        if (c) onOpenChat(c.id);
      },
    });
  }
  if (isSecret) {
    if (view === "active") {
      menu.push({ label: t.encryptionKey, icon: <KeyRound size={18} />, onClick: () => setFpOpen(true) });
    }
    menu.push({
      label: t.deleteSecretChat,
      icon: <Trash2 size={18} />,
      danger: true,
      onClick: async () => {
        if (window.confirm(t.deleteSecretConfirm)) await deleteChat(chat.id);
      },
    });
  }

  return (
    <div
      className="chat-wallpaper relative flex h-full min-w-0 flex-col"
      onDragEnter={(e) => {
        if (!hasFiles(e) || !canCompose) return;
        e.preventDefault();
        dragDepth.current++;
        setDragging(true);
      }}
      onDragOver={(e) => {
        if (!hasFiles(e) || !canCompose) return;
        e.preventDefault();
        e.dataTransfer.dropEffect = "copy";
      }}
      onDragLeave={(e) => {
        if (!hasFiles(e)) return;
        dragDepth.current = Math.max(0, dragDepth.current - 1);
        if (dragDepth.current === 0) setDragging(false);
      }}
      onDrop={(e) => {
        if (!hasFiles(e)) return;
        e.preventDefault();
        dragDepth.current = 0;
        setDragging(false);
        if (e.dataTransfer.files.length) composer.current?.addFiles(e.dataTransfer.files);
      }}
    >
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
          <div
            className={`flex items-center gap-1.5 font-semibold leading-tight ${
              isSecret ? "text-teal-700 dark:text-teal-300" : "text-slate-900 dark:text-white"
            }`}
          >
            {isSecret && (
              <button
                onClick={() => view === "active" && setFpOpen(true)}
                className={`-m-1 shrink-0 rounded-full p-1 ${view === "active" ? "hover:bg-teal-50 dark:hover:bg-teal-400/10" : "cursor-default"}`}
                aria-label={t.encryptionKey}
                title={view === "active" ? t.encryptionKey : t.secretChat}
              >
                <Lock size={15} strokeWidth={2.6} />
              </button>
            )}
            <span className="truncate">{title}</span>
          </div>
          <div className="truncate text-[13px] leading-tight">
            <Subtitle chat={chat} me={me} typingIds={typingIds} />
          </div>
        </div>
        <HeaderMenu items={menu} />
      </header>
      {isSecret && <div className="h-0.5 bg-gradient-to-r from-teal-400 via-emerald-500 to-teal-400" aria-hidden />}

      {view === "checking" ? (
        <div className="flex flex-1 items-center justify-center text-teal-500">
          <Spinner size={28} />
        </div>
      ) : view === "waiting" ? (
        <SecretWaitingCard peerName={peerName} />
      ) : view === "invite" ? (
        <SecretInviteCard chat={chat} peerName={peerName} />
      ) : (
        <MessageList key={`list-${chat.id}`} chat={chat} meId={me.id} />
      )}
      {canCompose ? (
        <Composer key={`composer-${chat.id}`} chatId={chat.id} ref={composer} />
      ) : view === "otherDevice" ? (
        <OtherDeviceBanner />
      ) : null}
      {isSecret && (
        <FingerprintDialog open={fpOpen} onClose={() => setFpOpen(false)} chat={chat} peerName={peerName} />
      )}

      {dragging && (
        <div className="animate-fade-in pointer-events-none absolute inset-0 z-30 flex items-center justify-center bg-sky-50/80 p-4 backdrop-blur-sm dark:bg-app-dark/80">
          <div className="flex h-full max-h-80 w-full max-w-xl flex-col items-center justify-center rounded-3xl border-2 border-dashed border-brand-400 bg-white/70 text-center dark:border-sky-400/60 dark:bg-slate-800/60">
            <div className="mb-3 flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-sky-accent to-brand-600 text-white shadow-lg shadow-brand-600/30">
              <UploadCloud size={32} />
            </div>
            <p className="text-lg font-semibold text-slate-800 dark:text-slate-100">{t.dropFiles}</p>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{t.dropFilesHint}</p>
          </div>
        </div>
      )}
    </div>
  );
}
