/* eslint-disable @next/next/no-img-element */
"use client";

import { Paperclip, SendHorizontal, Smile, X } from "lucide-react";
import { useCallback, useEffect, useImperativeHandle, useLayoutEffect, useRef, useState } from "react";
import { MAX_UPLOAD_BYTES } from "@/lib/api";
import { humanSize } from "@/lib/format";
import { t } from "@/lib/i18n";
import { isImageFile, useChatStore } from "@/lib/store";
import type { ID } from "@/types";
import { EmojiPicker } from "./EmojiPicker";
import { FileIcon } from "./FileIcon";

const drafts = new Map<ID, string>();
const TYPING_RESEND_MS = 3_000;
const TYPING_STOP_MS = 3_500;
const MAX_HEIGHT = 168;

export interface ComposerHandle {
  addFiles: (files: FileList | File[]) => void;
}

interface TrayItem {
  key: string;
  file: File;
  previewUrl?: string;
  width?: number;
  height?: number;
}

let trayKey = 0;

export function Composer({ chatId, ref }: { chatId: ID; ref?: React.Ref<ComposerHandle> }) {
  const send = useChatStore((s) => s.send);
  const sendAttachment = useChatStore((s) => s.sendAttachment);
  const sendTyping = useChatStore((s) => s.sendTyping);
  const showToast = useChatStore((s) => s.showToast);
  const [text, setText] = useState(() => drafts.get(chatId) ?? "");
  const [tray, setTray] = useState<TrayItem[]>([]);
  const [emojiOpen, setEmojiOpen] = useState(false);
  const ref_ = useRef<HTMLTextAreaElement>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const typingSentAt = useRef(0);
  const stopTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Object URLs still owned by the tray (revoked on remove/unmount, handed to the store on send).
  const ownedUrls = useRef(new Set<string>());

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
    const owned = ownedUrls.current;
    return () => {
      owned.forEach((u) => URL.revokeObjectURL(u));
      owned.clear();
    };
  }, []);

  useEffect(() => {
    if (window.matchMedia("(pointer: fine)").matches) ref_.current?.focus();
  }, [chatId]);

  // Auto-grow textarea
  useLayoutEffect(() => {
    const el = ref_.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, MAX_HEIGHT)}px`;
    el.style.overflowY = el.scrollHeight > MAX_HEIGHT ? "auto" : "hidden";
  }, [text]);

  const addFiles = useCallback(
    (list: FileList | File[]) => {
      const files = Array.from(list);
      if (!files.length) return;
      let rejected = false;
      const items: TrayItem[] = [];
      for (const file of files) {
        if (file.size > MAX_UPLOAD_BYTES) {
          rejected = true;
          continue;
        }
        const item: TrayItem = { key: `t${++trayKey}`, file };
        if (isImageFile(file)) {
          item.previewUrl = URL.createObjectURL(file);
          ownedUrls.current.add(item.previewUrl);
          const img = new Image();
          img.onload = () =>
            setTray((tr) =>
              tr.map((x) => (x.key === item.key ? { ...x, width: img.naturalWidth, height: img.naturalHeight } : x)),
            );
          img.src = item.previewUrl;
        }
        items.push(item);
      }
      if (rejected) showToast(t.fileTooLarge);
      if (items.length) {
        setTray((tr) => tr.concat(items));
        requestAnimationFrame(() => ref_.current?.focus());
      }
    },
    [showToast],
  );

  useImperativeHandle(ref, () => ({ addFiles }), [addFiles]);

  const removeItem = (key: string) =>
    setTray((tr) => {
      const it = tr.find((x) => x.key === key);
      if (it?.previewUrl) {
        URL.revokeObjectURL(it.previewUrl);
        ownedUrls.current.delete(it.previewUrl);
      }
      return tr.filter((x) => x.key !== key);
    });

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
    if (!body && tray.length === 0) return;
    if (stopTimer.current) clearTimeout(stopTimer.current);
    stopTimer.current = null;
    if (typingSentAt.current) {
      typingSentAt.current = 0;
      sendTyping(chatId, false);
    }
    setText("");
    drafts.delete(chatId);
    if (tray.length) {
      // Caption goes with the first attachment; the rest are sent as separate messages.
      tray.forEach((it, i) => {
        if (it.previewUrl) ownedUrls.current.delete(it.previewUrl); // ownership → store
        sendAttachment(chatId, it.file, i === 0 ? body : "", {
          previewUrl: it.previewUrl,
          width: it.width,
          height: it.height,
        });
      });
      setTray([]);
    } else {
      void send(chatId, body);
    }
    ref_.current?.focus();
  };

  const insertEmoji = (emoji: string) => {
    const el = ref_.current;
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

  const canSend = text.trim().length > 0 || tray.length > 0;

  return (
    <div className="px-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-2 sm:px-4">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
        className="relative mx-auto flex max-w-3xl items-end gap-2"
      >
        <div className="relative flex min-w-0 flex-1 flex-col rounded-[1.6rem] bg-white shadow-sm ring-1 ring-slate-200/70 transition focus-within:ring-2 focus-within:ring-brand-400/60 dark:bg-bubble-in-dark dark:ring-white/5">
          {tray.length > 0 && (
            <div className="thin-scroll flex gap-2 overflow-x-auto px-3 pb-1 pt-3">
              {tray.map((it) =>
                it.previewUrl ? (
                  <div
                    key={it.key}
                    className="animate-pop-in relative h-20 w-20 shrink-0 overflow-hidden rounded-xl bg-slate-100 ring-1 ring-slate-200 dark:bg-slate-700 dark:ring-white/10"
                    title={`${it.file.name} · ${humanSize(it.file.size)}`}
                  >
                    <img src={it.previewUrl} alt={it.file.name} className="h-full w-full object-cover" draggable={false} />
                    <RemoveButton onClick={() => removeItem(it.key)} />
                  </div>
                ) : (
                  <div
                    key={it.key}
                    className="animate-pop-in relative flex h-20 w-52 shrink-0 items-center gap-2.5 rounded-xl bg-brand-50 py-2 pl-2.5 pr-7 ring-1 ring-brand-100 dark:bg-sky-400/10 dark:ring-sky-400/20"
                    title={it.file.name}
                  >
                    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-sky-accent to-brand-600 text-white">
                      <FileIcon mime={it.file.type} name={it.file.name} size={20} />
                    </span>
                    <span className="min-w-0">
                      <span className="line-clamp-2 break-all text-[13px] font-medium leading-tight text-slate-800 dark:text-slate-100">
                        {it.file.name}
                      </span>
                      <span className="mt-0.5 block text-xs tabular-nums text-slate-500 dark:text-slate-400">
                        {humanSize(it.file.size)}
                      </span>
                    </span>
                    <RemoveButton onClick={() => removeItem(it.key)} />
                  </div>
                ),
              )}
            </div>
          )}

          <div className="flex items-end">
            <button
              type="button"
              onClick={() => setEmojiOpen((o) => !o)}
              className={`m-1 mr-0 shrink-0 rounded-full p-2.5 transition hover:bg-slate-100 dark:hover:bg-white/5 ${
                emojiOpen ? "text-brand-600 dark:text-sky-400" : "text-slate-400"
              }`}
              aria-label="Emoji"
            >
              <Smile size={22} />
            </button>
            {emojiOpen && <EmojiPicker onPick={insertEmoji} onClose={() => setEmojiOpen(false)} />}
            <textarea
              ref={ref_}
              value={text}
              rows={1}
              onChange={(e) => onChange(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
                  e.preventDefault();
                  submit();
                }
              }}
              onPaste={(e) => {
                const files = Array.from(e.clipboardData.files ?? []);
                if (files.length) {
                  e.preventDefault();
                  addFiles(files);
                }
              }}
              placeholder={tray.length ? t.captionPlaceholder : t.messagePlaceholder}
              className="thin-scroll max-h-42 min-w-0 flex-1 resize-none bg-transparent px-1 py-3 text-[15px] leading-6 text-slate-900 outline-none placeholder:text-slate-400 dark:text-slate-100"
            />
            <button
              type="button"
              onClick={() => fileInput.current?.click()}
              className="m-1 ml-0 shrink-0 rounded-full p-2.5 text-slate-400 transition hover:bg-slate-100 hover:text-brand-600 dark:hover:bg-white/5 dark:hover:text-sky-400"
              aria-label={t.attach}
              title={t.attach}
            >
              <Paperclip size={22} />
            </button>
            <input
              ref={fileInput}
              type="file"
              multiple
              className="hidden"
              onChange={(e) => {
                if (e.target.files) addFiles(e.target.files);
                e.target.value = "";
              }}
            />
          </div>
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

function RemoveButton({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={t.remove}
      title={t.remove}
      className="absolute right-1 top-1 flex h-6 w-6 items-center justify-center rounded-full bg-black/55 text-white backdrop-blur-sm transition hover:bg-black/75"
    >
      <X size={14} strokeWidth={2.5} />
    </button>
  );
}
