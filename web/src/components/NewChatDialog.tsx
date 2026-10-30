"use client";

import { ArrowLeft, Lock, Search, Users, X } from "lucide-react";
import { useEffect, useState } from "react";
import { errorMessage } from "@/lib/errors";
import { userDisplayName } from "@/lib/format";
import { t } from "@/lib/i18n";
import { useChatStore } from "@/lib/store";
import { useUserSearch } from "@/lib/useUserSearch";
import type { ID, User } from "@/types";
import { Avatar } from "./Avatar";
import { Modal } from "./Modal";
import { Spinner } from "./Spinner";
import { UserRow } from "./UserRow";

export type NewChatMode = "direct" | "group" | "secret";

interface Props {
  open: boolean;
  initialMode: NewChatMode;
  onClose: () => void;
  onOpenChat: (id: ID) => void;
}

export function NewChatDialog({ open, initialMode, onClose, onOpenChat }: Props) {
  const [mode, setMode] = useState<NewChatMode>(initialMode);
  const [query, setQuery] = useState("");
  const [title, setTitle] = useState("");
  const [selected, setSelected] = useState<User[]>([]);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const openDirect = useChatStore((s) => s.openDirect);
  const createGroup = useChatStore((s) => s.createGroup);
  const startSecretChat = useChatStore((s) => s.startSecretChat);
  const { results, loading } = useUserSearch(query, 1);

  useEffect(() => {
    if (open) {
      setMode(initialMode);
      setQuery("");
      setTitle("");
      setSelected([]);
      setError(null);
      setBusy(null);
    }
  }, [open, initialMode]);

  const startDirect = async (u: User) => {
    setBusy(u.id);
    setError(null);
    try {
      const chat = await openDirect(u.id);
      onClose();
      onOpenChat(chat.id);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  };

  const startSecret = async (u: User) => {
    setBusy(u.id);
    setError(null);
    const chat = await startSecretChat(u.id);
    setBusy(null);
    if (chat) {
      onClose();
      onOpenChat(chat.id);
    }
  };

  const toggle = (u: User) =>
    setSelected((s) => (s.some((x) => x.id === u.id) ? s.filter((x) => x.id !== u.id) : s.concat(u)));

  const submitGroup = async () => {
    if (!title.trim() || selected.length === 0) return;
    setBusy("group");
    setError(null);
    try {
      const chat = await createGroup(
        title.trim(),
        selected.map((u) => u.id),
      );
      onClose();
      onOpenChat(chat.id);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  };

  const isGroup = mode === "group";
  const isSecret = mode === "secret";

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={
        isSecret ? (
          <span className="flex items-center gap-2 text-teal-700 dark:text-teal-300">
            <Lock size={18} /> {t.startSecretChat}
          </span>
        ) : isGroup ? (
          t.newGroup
        ) : (
          t.newChat
        )
      }
      leading={
        mode !== "direct" && initialMode === "direct" ? (
          <button
            onClick={() => setMode("direct")}
            className="-ml-2 rounded-full p-2 text-slate-500 hover:bg-slate-100 dark:hover:bg-white/5"
            aria-label={t.back}
          >
            <ArrowLeft size={20} />
          </button>
        ) : undefined
      }
      footer={
        isGroup ? (
          <div className="flex items-center justify-between gap-3">
            <span className="text-sm text-slate-500 dark:text-slate-400">{t.selected(selected.length)}</span>
            <div className="flex gap-2">
              <button
                onClick={onClose}
                className="rounded-xl px-4 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-white/5"
              >
                {t.cancel}
              </button>
              <button
                onClick={submitGroup}
                disabled={!title.trim() || selected.length === 0 || busy === "group"}
                className="flex min-w-24 items-center justify-center rounded-xl bg-gradient-to-r from-brand-600 to-brand-500 px-5 py-2.5 text-sm font-semibold text-white shadow-md shadow-brand-600/20 transition disabled:opacity-50 disabled:shadow-none"
              >
                {busy === "group" ? <Spinner size={16} /> : t.create}
              </button>
            </div>
          </div>
        ) : undefined
      }
    >
      <div className="space-y-3 px-5 pb-3">
        {isGroup && (
          <div className="flex items-center gap-3">
            <Avatar name={title} seed={title || "group"} size={52} group />
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder={t.groupTitlePlaceholder}
              aria-label={t.groupTitle}
              maxLength={64}
              autoFocus
              className="flex-1 border-b-2 border-slate-200 bg-transparent py-2 text-lg text-slate-900 outline-none transition placeholder:text-slate-300 focus:border-brand-500 dark:border-white/10 dark:text-white dark:placeholder:text-slate-600"
            />
          </div>
        )}

        {isGroup && selected.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {selected.map((u) => (
              <button
                key={u.id}
                onClick={() => toggle(u)}
                className="animate-pop-in flex items-center gap-1.5 rounded-full bg-brand-50 py-1 pl-1 pr-2 text-sm text-brand-700 transition hover:bg-brand-100 dark:bg-sky-400/10 dark:text-sky-300"
              >
                <Avatar name={userDisplayName(u)} seed={u.id} url={u.avatar_url} size={22} />
                <span className="max-w-32 truncate">{userDisplayName(u)}</span>
                <X size={14} />
              </button>
            ))}
          </div>
        )}

        <div className="relative">
          <Search size={17} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={isGroup ? t.addMembers : t.searchUsers}
            autoFocus={!isGroup}
            key={mode}
            className="w-full rounded-full bg-slate-100 py-2.5 pl-10 pr-4 text-[15px] text-slate-900 outline-none transition placeholder:text-slate-400 focus:bg-white focus:ring-2 focus:ring-brand-500/30 dark:bg-white/5 dark:text-white"
          />
        </div>
        {error && <p className="text-sm text-rose-500">{error}</p>}
        {isSecret && (
          <p className="flex items-start gap-2 rounded-2xl bg-teal-50 p-3 text-sm text-teal-800 dark:bg-teal-400/10 dark:text-teal-200">
            <Lock size={16} className="mt-0.5 shrink-0" />
            {t.startSecretChatHint}
          </p>
        )}
      </div>

      <div className="min-h-64 px-2 pb-3">
        {mode === "direct" && (
          <button
            onClick={() => setMode("secret")}
            className="flex w-full items-center gap-3 rounded-2xl px-3 py-2.5 text-left font-medium text-teal-600 transition hover:bg-teal-50/80 dark:text-teal-400 dark:hover:bg-white/5"
          >
            <span className="flex h-11 w-11 items-center justify-center rounded-full bg-gradient-to-br from-teal-400 to-emerald-600 text-white">
              <Lock size={20} />
            </span>
            {t.startSecretChat}
          </button>
        )}
        {mode === "direct" && (
          <button
            onClick={() => setMode("group")}
            className="flex w-full items-center gap-3 rounded-2xl px-3 py-2.5 text-left font-medium text-brand-600 transition hover:bg-brand-50/80 dark:text-sky-400 dark:hover:bg-white/5"
          >
            <span className="flex h-11 w-11 items-center justify-center rounded-full bg-gradient-to-br from-sky-accent to-brand-600 text-white">
              <Users size={20} />
            </span>
            {t.newGroup}
          </button>
        )}

        {loading && (
          <div className="flex justify-center py-6 text-brand-500">
            <Spinner />
          </div>
        )}
        {!loading && query.trim() && results.length === 0 && (
          <p className="py-8 text-center text-sm text-slate-400">{t.noResults}</p>
        )}
        {!loading && !query.trim() && (
          <p className="py-8 text-center text-sm text-slate-400">{t.searchHint}</p>
        )}
        {!loading &&
          results.map((u) =>
            isGroup ? (
              <UserRow
                key={u.id}
                user={u}
                selectable
                selected={selected.some((x) => x.id === u.id)}
                onClick={() => toggle(u)}
              />
            ) : (
              <UserRow
                key={u.id}
                user={u}
                disabled={busy === u.id}
                onClick={() => (isSecret ? startSecret(u) : startDirect(u))}
              />
            ),
          )}
      </div>
    </Modal>
  );
}
