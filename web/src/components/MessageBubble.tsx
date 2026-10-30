/* eslint-disable @next/next/no-img-element */
"use client";

import { Download, ImageOff, Lock } from "lucide-react";
import { memo, useEffect, useRef, useState } from "react";
import { attachmentUrl } from "@/lib/config";
import { gradientFor, humanSize, timeHM, userDisplayName } from "@/lib/format";
import { t } from "@/lib/i18n";
import { useChatStore } from "@/lib/store";
import type { UIMessage, User } from "@/types";
import { Avatar } from "./Avatar";
import { FileIcon, fileExt } from "./FileIcon";
import { ProgressRing } from "./ProgressRing";
import { Ticks } from "./Ticks";
import { saveBlob } from "./Lightbox";
import { Spinner } from "./Spinner";
import { useSecretMedia } from "./useSecretMedia";

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
  onCancel?: () => void;
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

const IMG_MAX_W = 320;
const IMG_MAX_H = 380;
const IMG_MIN_W = 150;

/** Emoji-only short messages are rendered large, like Telegram. */
function isJumboEmoji(text: string) {
  const s = text.trim();
  if (!s || s.length > 16) return false;
  return /^(?:\p{Extended_Pictographic}|\p{Emoji_Component}|‍|️|\s)+$/u.test(s) && !/^[\d#*\s]+$/.test(s);
}

function imageBox(width: number, height: number) {
  const w = width > 0 ? width : 4;
  const h = height > 0 ? height : 3;
  const scale = Math.min(1, IMG_MAX_W / w, IMG_MAX_H / h);
  const dispW = Math.max(IMG_MIN_W, Math.round(w * scale));
  // Clamp very tall/wide images to a sane frame (object-cover crops the rest).
  const ratio = Math.min(Math.max(w / h, dispW / IMG_MAX_H), dispW / 90);
  return { width: dispW, ratio };
}

function SenderName({ sender }: { sender: User }) {
  return (
    <div className={`truncate text-[13px] font-semibold ${nameColors[gradientFor(sender.id)]}`}>
      {userDisplayName(sender)}
    </div>
  );
}

function MessageBubbleBase({ message, mine, first, last, sender, showSender, onRetry, onCancel }: Props) {
  const openLightbox = useChatStore((s) => s.openLightbox);
  const [imgLoaded, setImgLoaded] = useState(false);
  const failed = message.local === "failed";
  const kind = message.kind || "text";
  const att = message.attachment;
  const isImage = kind === "image" && !!att;
  const isFile = kind === "file" && !!att;
  const caption = message.text?.trim() ?? "";
  const jumbo = kind === "text" && isJumboEmoji(message.text);
  const time = timeHM(message.created_at);
  const uploading = !!message.upload && message.local === "sending";
  const busy = message.local === "sending";
  const showName = showSender && !mine && first && !!sender;
  const secret = !!message.secretFile;
  const media = useSecretMedia(message, secret && isImage);
  // Secret files: download is requested on click, then saved once decrypted.
  const wantSave = useRef(false);
  useEffect(() => {
    if (wantSave.current && media.blob && att) {
      wantSave.current = false;
      saveBlob(media.blob, att.name);
    }
  }, [media.blob, att]);

  const radius = mine
    ? `rounded-2xl ${first ? "" : "rounded-tr-md"} ${last ? "rounded-br-sm" : "rounded-br-md"}`
    : `rounded-2xl ${first ? "" : "rounded-tl-md"} ${last ? "rounded-bl-sm" : "rounded-bl-md"}`;

  const bubbleColor = mine
    ? "bg-gradient-to-br from-brand-600 to-brand-500 text-white shadow-brand-600/20"
    : "bg-white text-slate-900 shadow-slate-900/5 dark:bg-bubble-in-dark dark:text-slate-100";

  const meta = (overlay = false) => (
    <span
      className={`flex select-none items-center gap-1 text-[11px] leading-none tabular-nums ${
        overlay
          ? "rounded-full bg-black/45 px-2 py-1 text-white backdrop-blur-sm"
          : jumbo
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
  const inlineMeta = <span className="float-right ml-2 mt-1.5 translate-y-1">{meta()}</span>;

  let body: React.ReactNode;

  if (isImage) {
    const src = message.localPreview || (secret ? media.url : attachmentUrl(att.url));
    const box = imageBox(att.width, att.height);
    const canOpen = !busy && !failed && !!att.url && (!secret || !!media.blob);
    body = (
      <div className={`${radius} ${bubbleColor} overflow-hidden p-1 shadow-sm`} style={{ width: box.width + 8, maxWidth: "100%" }}>
        {showName && (
          <div className="px-2 pb-1 pt-0.5">
            <SenderName sender={sender!} />
          </div>
        )}
        <div
          className="relative block w-full overflow-hidden rounded-xl bg-slate-200/60 dark:bg-slate-700/60"
          style={{ aspectRatio: String(box.ratio) }}
        >
          <button
            type="button"
            onClick={() =>
              canOpen &&
              (secret ? openLightbox("", att.name, media.blob ?? undefined) : openLightbox(attachmentUrl(att.url), att.name))
            }
            className={`absolute inset-0 block h-full w-full ${canOpen ? "cursor-zoom-in" : "cursor-default"}`}
            aria-label={att.name || t.photo}
          >
            {src && (
              <img
                src={src}
                alt={att.name || t.photo}
                onLoad={() => setImgLoaded(true)}
                className={`h-full w-full object-cover transition-opacity duration-300 ${imgLoaded ? "opacity-100" : "opacity-0"}`}
                draggable={false}
                loading="lazy"
              />
            )}
          </button>
          {secret && !src && !busy && !failed && (
            <span className="pointer-events-none absolute inset-0 flex items-center justify-center text-slate-400">
              {media.error ? <ImageOff size={28} /> : <Spinner size={24} />}
            </span>
          )}
          {(busy || failed) && (
            <span className="absolute inset-0 flex items-center justify-center bg-black/10">
              <ProgressRing
                progress={uploading ? message.upload?.progress : undefined}
                failed={failed}
                onCancel={uploading ? onCancel : undefined}
                onRetry={onRetry}
              />
            </span>
          )}
          {!caption && <span className="pointer-events-none absolute bottom-1.5 right-1.5">{meta(true)}</span>}
        </div>
        {caption && (
          <div className="whitespace-pre-wrap break-words px-2 pb-1 pt-1.5 text-[15px] leading-[1.35rem] [overflow-wrap:anywhere]">
            {caption}
            {inlineMeta}
          </div>
        )}
      </div>
    );
  } else if (isFile) {
    const href = att.url && !secret ? attachmentUrl(att.url) : undefined;
    const saveSecret = () => {
      if (media.blob) saveBlob(media.blob, att.name);
      else {
        wantSave.current = true;
        media.load();
      }
    };
    const sub = uploading
      ? `${humanSize(Math.round(att.size * (message.upload?.progress ?? 0)))} / ${humanSize(att.size)}`
      : [humanSize(att.size), fileExt(att.name)].filter(Boolean).join(" · ");
    body = (
      <div className={`${radius} ${bubbleColor} w-[19rem] max-w-full px-2.5 pb-2 pt-2 shadow-sm`}>
        {showName && (
          <div className="px-0.5 pb-1">
            <SenderName sender={sender!} />
          </div>
        )}
        <div className="flex items-center gap-3">
          {secret && !busy && !failed ? (
            <button
              type="button"
              onClick={saveSecret}
              className={`relative flex h-[46px] w-[46px] shrink-0 items-center justify-center rounded-full transition ${
                mine ? "bg-white/20 text-white hover:bg-white/30" : "bg-gradient-to-br from-teal-400 to-brand-600 text-white"
              }`}
              aria-label={`${t.download}: ${att.name}`}
              title={media.error ? t.decryptFailed : t.download}
            >
              {media.loading ? <Spinner size={20} /> : media.error ? <ImageOff size={20} /> : <Download size={20} />}
            </button>
          ) : busy || failed ? (
            <ProgressRing
              size={46}
              tone={mine ? "light" : "brand"}
              progress={uploading ? message.upload?.progress : undefined}
              failed={failed}
              onCancel={uploading ? onCancel : undefined}
              onRetry={onRetry}
            />
          ) : (
            <a
              href={href}
              download={att.name}
              target="_blank"
              rel="noopener noreferrer"
              className={`group/file relative flex h-[46px] w-[46px] shrink-0 items-center justify-center rounded-full transition ${
                mine ? "bg-white/20 text-white hover:bg-white/30" : "bg-gradient-to-br from-sky-accent to-brand-600 text-white"
              }`}
              aria-label={`${t.download}: ${att.name}`}
              title={t.download}
            >
              <span className="transition group-hover/file:scale-0">
                <FileIcon mime={att.mime} name={att.name} />
              </span>
              <Download size={20} className="absolute scale-0 transition group-hover/file:scale-100" />
            </a>
          )}
          <div className="min-w-0 flex-1">
            {secret && !busy && !failed ? (
              <button
                type="button"
                onClick={saveSecret}
                className="block max-w-full truncate text-left text-[15px] font-medium hover:underline"
                title={att.name}
              >
                {att.name}
              </button>
            ) : href && !busy ? (
              <a
                href={href}
                download={att.name}
                target="_blank"
                rel="noopener noreferrer"
                className="block truncate text-[15px] font-medium hover:underline"
                title={att.name}
              >
                {att.name}
              </a>
            ) : (
              <div className="truncate text-[15px] font-medium" title={att.name}>
                {att.name}
              </div>
            )}
            <div className={`mt-0.5 truncate text-xs tabular-nums ${mine ? "text-white/75" : "text-slate-500 dark:text-slate-400"}`}>
              {uploading ? `${t.uploading} ${sub}` : sub}
            </div>
          </div>
        </div>
        {caption ? (
          <div className="whitespace-pre-wrap break-words px-0.5 pt-1.5 text-[15px] leading-[1.35rem] [overflow-wrap:anywhere]">
            {caption}
            {inlineMeta}
          </div>
        ) : (
          <div className="mt-1 flex justify-end">{meta()}</div>
        )}
      </div>
    );
  } else if (kind === "encrypted") {
    body = (
      <div className={`${radius} ${bubbleColor} px-3 pb-2 pt-1.5 shadow-sm`}>
        {showName && <SenderName sender={sender!} />}
        <div className="flex items-center gap-1.5 text-[15px] italic leading-[1.35rem] opacity-80">
          {message.e2e === "failed" ? t.decryptFailed : (
            <>
              <Lock size={14} className="shrink-0" />
              {t.encryptedMessage.replace(/^🔒\s*/, "")}
            </>
          )}
          <span className="ml-auto pl-2 not-italic">{meta()}</span>
        </div>
      </div>
    );
  } else if (jumbo) {
    body = (
      <div className="px-1">
        {showName && <SenderName sender={sender!} />}
        <div className="text-5xl leading-tight">{message.text.trim()}</div>
        <div className={`mt-1 flex ${mine ? "justify-end" : "justify-start"}`}>{meta()}</div>
      </div>
    );
  } else {
    body = (
      <div
        className={`${radius} ${bubbleColor} px-3 pb-2 pt-1.5 shadow-sm ${failed ? "cursor-pointer opacity-70" : ""} ${
          busy ? "opacity-90" : ""
        }`}
        onClick={failed ? onRetry : undefined}
      >
        {showName && <SenderName sender={sender!} />}
        <div className="whitespace-pre-wrap break-words text-[15px] leading-[1.35rem] [overflow-wrap:anywhere]">
          {message.text}
          {inlineMeta}
        </div>
      </div>
    );
  }

  return (
    <div className={`flex items-end gap-2 ${mine ? "justify-end" : "justify-start"} ${last ? "mb-2" : "mb-0.5"}`}>
      {showSender && !mine && (
        <div className="w-8 shrink-0">
          {last && sender && (
            <Avatar name={userDisplayName(sender)} seed={sender.id} url={sender.avatar_url} size={32} />
          )}
        </div>
      )}
      <div className={`relative flex min-w-0 max-w-[min(80%,34rem)] flex-col ${mine ? "items-end" : "items-start"}`}>
        {body}
        {failed && (
          <div className="mt-1 flex items-center gap-2 text-xs text-rose-500">
            <span className={kind === "text" ? "cursor-pointer" : ""} onClick={kind === "text" ? onRetry : undefined}>
              {t.failed}
            </span>
            {onCancel && (
              <button onClick={onCancel} className="font-medium underline-offset-2 hover:underline">
                {t.remove}
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export const MessageBubble = memo(MessageBubbleBase);
