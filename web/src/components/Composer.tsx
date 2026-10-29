"use client";

import { SendHorizontal, Smile } from "lucide-react";
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { t } from "@/lib/i18n";
import { useChatStore } from "@/lib/store";
import type { ID } from "@/types";
import { EmojiPicker } from "./EmojiPicker";

const drafts = new Map<ID, string>();
const TYPING_RESEND_MS = 3_000;
const TYPING_STOP_MS = 3_500;
const MAX_HEIGHT = 168;

export function Composer({ chatId }: { chatId: ID }) {
  const send = useChatStore((s) => s.send);
  const sendTyping = useChatStore((s) => s.sendTyping);
  const [text, setText] = useState(() => drafts.get(chatId) ?? "");
  const [emojiOpen, setEmojiOpen] = useState(false);
  const ref = useRef<HTMLTextAreaElement>(null);
  const typingSentAt = useRef(0);
  const stopTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const stopTyping = useCallback(() => {
    if (stopTimer.current) clearTimeout(stopTimer.current);
    stopTimer.current = null;
    if (typingSentAt.current) {
      typingSentAt.current = 0;
      sendTyping(chatId, false);
    }
  }, [chatId, sendTyping]);

  // Stop typing when switching chats/unmounting.
  useEffect(() => stopTyping, [stopTyping]);

  useEffect(() => {
    if (window.matchMedia("(pointer: fine)").matches) ref.current?.focus();
  }, [chatId]);

  // Auto-grow textarea
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, MAX_HEIGHT)}px`;
    el.style.overflowY = el.scrollHeight > MAX_HEIGHT ? "auto" : "hidden";
  }, [text]);

  const onChange = (value: string) => {
    setText(value);
    drafts.set(chatId, value);
    if (!value.trim()) {
      stopTyping();
      return;
    }
    const now = Date.now();
    if (now - typingSentAt.current > TYPING_RESEND_MS) {
      typingSentAt.current = now;
      sendTyping(chatId, true);
    }
    if (stopTimer.current) clearTimeout(stopTimer.current);
    stopTimer.current = setTimeout(stopTyping, TYPING_STOP_MS);
  };

  const submit = () => {
    const body = text.trim();
    if (!body) return;
    if (stopTimer.current) clearTimeout(stopTimer.current);
    stopTimer.current = null;
    typingSentAt.current = 0; // store.send() already emits typing:false
    setText("");
    drafts.delete(chatId);
    void send(chatId, body);
    ref.current?.focus();
  };

  const insertEmoji = (emoji: string) => {
    const el = ref.current;
    const start = el?.selectionStart ?? text.length;
    const end = el?.selectionEnd ?? text.length;
    const next = text.slice(0, start) + emoji + text.slice(end);
    onChange(next);
    requestAnimationFrame(() => {
      if (!el) return;
      el.focus();
      const pos = start + emoji.length;
      el.setSelectionRange(pos, pos);
    });
  };

  const canSend = text.trim().length > 0;

  return (
    <div className="px-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-2 sm:px-4">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
        className="relative mx-auto flex max-w-3xl items-end gap-2"
      >
        <div className="relative flex min-w-0 flex-1 items-end rounded-[1.6rem] bg-white shadow-sm ring-1 ring-slate-200/70 transition focus-within:ring-2 focus-within:ring-brand-400/60 dark:bg-bubble-in-dark dark:ring-white/5">
          <button
            type="button"
            onClick={() => setEmojiOpen((o) => !o)}
            className={`m-1 shrink-0 rounded-full p-2.5 transition hover:bg-slate-100 dark:hover:bg-white/5 ${
              emojiOpen ? "text-brand-600 dark:text-sky-400" : "text-slate-400"
            }`}
            aria-label="Emoji"
          >
            <Smile size={22} />
          </button>
          {emojiOpen && <EmojiPicker onPick={insertEmoji} onClose={() => setEmojiOpen(false)} />}
          <textarea
            ref={ref}
            value={text}
            rows={1}
            onChange={(e) => onChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
                e.preventDefault();
                submit();
              }
            }}
            placeholder={t.messagePlaceholder}
            className="thin-scroll max-h-42 min-w-0 flex-1 resize-none bg-transparent py-3 pr-4 text-[15px] leading-6 text-slate-900 outline-none placeholder:text-slate-400 dark:text-slate-100"
          />
        </div>
        <button
          type="submit"
          disabled={!canSend}
          aria-label={t.send}
          title={t.send}
          className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-full text-white shadow-lg transition ${
            canSend
              ? "scale-100 bg-gradient-to-br from-sky-accent to-brand-600 shadow-brand-600/30 hover:scale-105 active:scale-95"
              : "scale-95 bg-slate-300 shadow-none dark:bg-slate-700"
          }`}
        >
          <SendHorizontal size={21} className={canSend ? "translate-x-px" : ""} />
        </button>
      </form>
    </div>
  );
}
