"use client";

import { ChevronDown } from "lucide-react";
import { Fragment, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { dayLabel, sameDay } from "@/lib/format";
import { t } from "@/lib/i18n";
import { useChatStore } from "@/lib/store";
import type { Chat, ID, UIMessage, User } from "@/types";
import { MessageBubble } from "./MessageBubble";
import { Spinner } from "./Spinner";

const GROUP_GAP_MS = 5 * 60_000;
const NEAR_BOTTOM = 120;
const LOAD_OLDER_AT = 240;

interface Props {
  chat: Chat;
  meId: ID;
}

const EMPTY: UIMessage[] = [];

export function MessageList({ chat, meId }: Props) {
  const state = useChatStore((s) => s.messages[chat.id]);
  const loadOlder = useChatStore((s) => s.loadOlder);
  const retry = useChatStore((s) => s.retry);
  const cancelSend = useChatStore((s) => s.cancelSend);
  const items = state?.items ?? EMPTY;

  const scroller = useRef<HTMLDivElement>(null);
  const atBottom = useRef(true);
  const anchor = useRef<{ height: number; top: number } | null>(null);
  const prevFirstId = useRef<ID | undefined>(undefined);
  const prevLastId = useRef<ID | undefined>(undefined);
  const initialised = useRef(false);
  const [showJump, setShowJump] = useState(false);
  const [newBelow, setNewBelow] = useState(0);

  const members = useMemo(() => {
    const m = new Map<ID, User>();
    chat.members.forEach((u) => m.set(u.id, u));
    return m;
  }, [chat.members]);

  const isGroup = chat.type === "group";

  const scrollToBottom = useCallback((smooth: boolean) => {
    const el = scroller.current;
    if (!el) return;
    el.scrollTo({ top: el.scrollHeight, behavior: smooth ? "smooth" : "auto" });
    atBottom.current = true;
    setShowJump(false);
    setNewBelow(0);
  }, []);

  const requestOlder = useCallback(() => {
    const el = scroller.current;
    if (!el || !state || !state.loaded || state.loadingOlder || !state.hasMore) return;
    anchor.current = { height: el.scrollHeight, top: el.scrollTop };
    void loadOlder(chat.id);
  }, [state, loadOlder, chat.id]);

  const onScroll = () => {
    const el = scroller.current;
    if (!el) return;
    const dist = el.scrollHeight - el.scrollTop - el.clientHeight;
    atBottom.current = dist < NEAR_BOTTOM;
    setShowJump(dist > 400);
    if (atBottom.current) setNewBelow(0);
    if (el.scrollTop < LOAD_OLDER_AT) requestOlder();
  };

  // React to item changes: keep position on prepend, stick to bottom on append.
  useLayoutEffect(() => {
    const el = scroller.current;
    if (!el) return;
    const firstId = items[0]?.id;
    const last = items[items.length - 1];
    const lastId = last?.client_id || last?.id;

    if (!initialised.current) {
      if (state?.loaded) {
        el.scrollTop = el.scrollHeight;
        initialised.current = true;
        atBottom.current = true;
      }
    } else if (anchor.current && firstId !== prevFirstId.current) {
      // Older messages were prepended — keep the viewport anchored.
      el.scrollTop = el.scrollHeight - anchor.current.height + anchor.current.top;
      anchor.current = null;
    } else if (lastId !== prevLastId.current && last) {
      const mine = last.sender_id === meId;
      if (mine || atBottom.current) {
        el.scrollTo({ top: el.scrollHeight, behavior: "smooth" });
        atBottom.current = true;
      } else {
        setNewBelow((n) => n + 1);
      }
    }
    if (anchor.current && !state?.loadingOlder) anchor.current = null;
    prevFirstId.current = firstId;
    prevLastId.current = lastId;
  }, [items, state?.loaded, state?.loadingOlder, meId]);

  // If the first page doesn't fill the viewport, keep loading older pages.
  useEffect(() => {
    const el = scroller.current;
    if (el && state?.loaded && state.hasMore && !state.loadingOlder && el.scrollHeight <= el.clientHeight + 40) {
      requestOlder();
    }
  }, [state?.loaded, state?.hasMore, state?.loadingOlder, items.length, requestOlder]);

  // Keep pinned to bottom when the container resizes (e.g. composer grows, keyboard opens).
  useEffect(() => {
    const el = scroller.current;
    if (!el || typeof ResizeObserver === "undefined") return;
    const ro = new ResizeObserver(() => {
      if (atBottom.current) el.scrollTop = el.scrollHeight;
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  return (
    <div className="relative min-h-0 flex-1">
      <div ref={scroller} onScroll={onScroll} className="thin-scroll h-full overflow-y-auto overscroll-contain">
        <div className="mx-auto flex min-h-full max-w-3xl flex-col justify-end px-3 pb-2 pt-4 sm:px-6">
          {state?.loadingOlder && (
            <div className="flex justify-center py-2 text-brand-500">
              <Spinner size={18} />
            </div>
          )}

          {!state?.loaded && (
            <div className="flex flex-1 items-center justify-center text-brand-500">
              <Spinner size={28} />
            </div>
          )}

          {state?.loaded && items.length === 0 && (
            <div className="flex flex-1 items-center justify-center">
              <div className="rounded-2xl bg-white/70 px-5 py-3 text-center text-sm text-slate-600 shadow-sm backdrop-blur dark:bg-slate-800/70 dark:text-slate-300">
                {t.startConversation}
              </div>
            </div>
          )}

          {items.map((m, i) => {
            const prev = items[i - 1];
            const next = items[i + 1];
            const newDay = !prev || !sameDay(prev.created_at, m.created_at);
            const first =
              newDay ||
              prev.sender_id !== m.sender_id ||
              Date.parse(m.created_at) - Date.parse(prev.created_at) > GROUP_GAP_MS;
            const last =
              !next ||
              next.sender_id !== m.sender_id ||
              !sameDay(m.created_at, next.created_at) ||
              Date.parse(next.created_at) - Date.parse(m.created_at) > GROUP_GAP_MS;
            return (
              <Fragment key={m.client_id || m.id}>
                {newDay && (
                  <div className="sticky top-2 z-10 my-3 flex justify-center">
                    <span className="rounded-full bg-white/85 px-3 py-1 text-xs font-medium text-slate-600 shadow-sm backdrop-blur dark:bg-slate-800/85 dark:text-slate-300">
                      {dayLabel(m.created_at)}
                    </span>
                  </div>
                )}
                <MessageBubble
                  message={m}
                  mine={m.sender_id === meId}
                  first={first}
                  last={last}
                  sender={members.get(m.sender_id)}
                  showSender={isGroup}
                  onRetry={m.local === "failed" && m.client_id ? () => retry(chat.id, m.client_id!) : undefined}
                  onCancel={
                    m.client_id && (m.local === "failed" || (m.local === "sending" && m.upload))
                      ? () => cancelSend(chat.id, m.client_id!)
                      : undefined
                  }
                />
              </Fragment>
            );
          })}
        </div>
      </div>

      {(showJump || newBelow > 0) && (
        <button
          onClick={() => scrollToBottom(true)}
          className="animate-pop-in absolute bottom-3 right-4 flex h-11 w-11 items-center justify-center rounded-full bg-white text-slate-600 shadow-lg ring-1 ring-slate-200 transition hover:text-brand-600 dark:bg-slate-800 dark:text-slate-300 dark:ring-white/10"
          aria-label={t.scrollDown}
        >
          <ChevronDown size={22} />
          {newBelow > 0 && (
            <span className="absolute -top-1.5 left-1/2 flex h-5 min-w-5 -translate-x-1/2 items-center justify-center rounded-full bg-gradient-to-r from-sky-accent to-brand-500 px-1.5 text-[11px] font-semibold text-white">
              {newBelow}
            </span>
          )}
        </button>
      )}
    </div>
  );
}
