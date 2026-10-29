"use client";

import { Menu, MessageSquarePlus, Search, Users, X } from "lucide-react";
import { useMemo, useState } from "react";
import { chatTitle, peerOf, userDisplayName } from "@/lib/format";
import { t } from "@/lib/i18n";
import { useChatStore } from "@/lib/store";
import { useUserSearch } from "@/lib/useUserSearch";
import type { ID } from "@/types";
import { Avatar } from "./Avatar";
import { ChatListItem } from "./ChatListItem";
import { Logo } from "./Logo";
import { Spinner } from "./Spinner";
import { UserRow } from "./UserRow";

interface Props {
  onOpenChat: (id: ID) => void;
  onOpenSettings: () => void;
  onNewChat: () => void;
  onNewGroup: () => void;
}

const EMPTY: ID[] = [];

export function Sidebar({ onOpenChat, onOpenSettings, onNewChat, onNewGroup }: Props) {
  const me = useChatStore((s) => s.me);
  const chats = useChatStore((s) => s.chats);
  const chatsLoaded = useChatStore((s) => s.chatsLoaded);
  const activeChatId = useChatStore((s) => s.activeChatId);
  const typing = useChatStore((s) => s.typing);
  const wsStatus = useChatStore((s) => s.wsStatus);
  const openDirect = useChatStore((s) => s.openDirect);

  const [query, setQuery] = useState("");
  const q = query.trim().toLowerCase();
  const { results: users, loading: searching } = useUserSearch(query, 2);
  const [opening, setOpening] = useState<ID | null>(null);

  const filtered = useMemo(() => {
    if (!q) return chats;
    return chats.filter((c) => {
      const title = chatTitle(c, me?.id).toLowerCase();
      if (title.includes(q)) return true;
      const peer = peerOf(c, me?.id);
      return !!peer && (peer.username.toLowerCase().includes(q) || peer.phone.includes(q));
    });
  }, [chats, q, me?.id]);

  // Hide users that are already shown as direct chats.
  const directPeerIds = useMemo(
    () => new Set(filtered.filter((c) => c.type === "direct").map((c) => peerOf(c, me?.id)?.id)),
    [filtered, me?.id],
  );
  const extraUsers = users.filter((u) => !directPeerIds.has(u.id));

  const startDirect = async (userId: ID) => {
    setOpening(userId);
    try {
      const chat = await openDirect(userId);
      setQuery("");
      onOpenChat(chat.id);
    } catch {
      /* ignore */
    } finally {
      setOpening(null);
    }
  };

  const connecting = wsStatus !== "open" && wsStatus !== "idle";

  return (
    <aside className="relative flex h-full flex-col bg-white dark:bg-app-dark-surface">
      {/* Header */}
      <div className="flex items-center gap-2 px-3 pb-2 pt-3">
        <button
          onClick={onOpenSettings}
          className="rounded-full p-1 transition hover:bg-brand-50 dark:hover:bg-white/5"
          aria-label={t.settings}
        >
          {me ? (
            <Avatar name={userDisplayName(me) || "?"} seed={me.id} url={me.avatar_url} size={36} />
          ) : (
            <Menu size={22} className="m-1.5 text-slate-500" />
          )}
        </button>
        <div className="flex min-w-0 flex-1 items-center gap-2">
          <Logo size={22} className="hidden rounded-md sm:block" />
          <h1 className="truncate text-lg font-semibold text-slate-900 dark:text-white">
            {connecting ? (
              <span className="flex items-center gap-2 text-base font-medium text-slate-500 dark:text-slate-400">
                <Spinner size={14} className="text-brand-500" />
                {t.connecting}
              </span>
            ) : (
              t.chats
            )}
          </h1>
        </div>
        <button
          onClick={onNewGroup}
          className="rounded-full p-2 text-slate-500 transition hover:bg-brand-50 hover:text-brand-600 dark:text-slate-400 dark:hover:bg-white/5 dark:hover:text-sky-400"
          aria-label={t.newGroup}
          title={t.newGroup}
        >
          <Users size={20} />
        </button>
        <button
          onClick={onNewChat}
          className="rounded-full p-2 text-slate-500 transition hover:bg-brand-50 hover:text-brand-600 dark:text-slate-400 dark:hover:bg-white/5 dark:hover:text-sky-400"
          aria-label={t.newChat}
          title={t.newChat}
        >
          <MessageSquarePlus size={20} />
        </button>
      </div>

      {/* Search */}
      <div className="px-3 pb-2">
        <div className="relative">
          <Search size={17} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => e.key === "Escape" && setQuery("")}
            placeholder={t.search}
            className="w-full rounded-full border border-transparent bg-slate-100 py-2.5 pl-10 pr-9 text-[15px] text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-brand-400 focus:bg-white focus:ring-4 focus:ring-brand-500/10 dark:bg-white/5 dark:text-white dark:focus:bg-transparent"
          />
          {query && (
            <button
              onClick={() => setQuery("")}
              className="absolute right-2 top-1/2 -translate-y-1/2 rounded-full p-1 text-slate-400 hover:bg-slate-200 dark:hover:bg-white/10"
              aria-label={t.close}
            >
              <X size={16} />
            </button>
          )}
        </div>
      </div>

      {/* List */}
      <div className="thin-scroll min-h-0 flex-1 overflow-y-auto px-2 pb-24">
        {!chatsLoaded ? (
          <div className="space-y-1 px-1 pt-1">
            {Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="flex animate-pulse items-center gap-3 rounded-2xl px-2 py-2.5">
                <div className="h-[52px] w-[52px] rounded-full bg-slate-100 dark:bg-white/5" />
                <div className="flex-1 space-y-2">
                  <div className="h-3.5 w-1/2 rounded bg-slate-100 dark:bg-white/5" />
                  <div className="h-3 w-3/4 rounded bg-slate-100 dark:bg-white/5" />
                </div>
              </div>
            ))}
          </div>
        ) : (
          <>
            {q && filtered.length > 0 && (
              <div className="px-3 pb-1 pt-2 text-xs font-semibold uppercase tracking-wide text-slate-400">
                {t.yourChats}
              </div>
            )}
            {filtered.map((c) => (
              <ChatListItem
                key={c.id}
                chat={c}
                meId={me?.id}
                active={c.id === activeChatId}
                typingUsers={typing[c.id] ?? EMPTY}
                onClick={() => onOpenChat(c.id)}
              />
            ))}

            {q.length >= 2 && (extraUsers.length > 0 || searching) && (
              <>
                <div className="flex items-center gap-2 px-3 pb-1 pt-4 text-xs font-semibold uppercase tracking-wide text-slate-400">
                  {t.globalSearch}
                  {searching && <Spinner size={12} />}
                </div>
                {extraUsers.map((u) => (
                  <UserRow key={u.id} user={u} disabled={opening === u.id} onClick={() => startDirect(u.id)} />
                ))}
              </>
            )}

            {q && !searching && filtered.length === 0 && extraUsers.length === 0 && (
              <p className="px-4 py-10 text-center text-sm text-slate-400">{t.noResults}</p>
            )}

            {!q && chats.length === 0 && (
              <div className="flex flex-col items-center px-6 py-16 text-center">
                <div className="mb-4 flex h-20 w-20 items-center justify-center rounded-3xl bg-gradient-to-br from-sky-100 to-brand-100 text-brand-600 dark:from-sky-400/10 dark:to-brand-500/10 dark:text-sky-400">
                  <MessageSquarePlus size={36} />
                </div>
                <p className="font-semibold text-slate-800 dark:text-slate-100">{t.noChats}</p>
                <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{t.noChatsHint}</p>
              </div>
            )}
          </>
        )}
      </div>

      {/* Floating action button */}
      <button
        onClick={onNewChat}
        className="absolute bottom-5 right-5 flex h-14 w-14 items-center justify-center rounded-full bg-gradient-to-br from-sky-accent to-brand-600 text-white shadow-xl shadow-brand-600/30 transition hover:scale-105 active:scale-95"
        aria-label={t.newChat}
      >
        <MessageSquarePlus size={24} />
      </button>
    </aside>
  );
}
