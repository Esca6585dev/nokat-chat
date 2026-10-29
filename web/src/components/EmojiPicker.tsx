"use client";

import { useEffect, useRef } from "react";

const EMOJIS = [
  "😀", "😁", "😂", "🤣", "😊", "😇", "🙂", "😉", "😍", "🥰", "😘", "😋",
  "😎", "🤩", "🥳", "🤔", "🤗", "🤭", "😐", "🙄", "😏", "😴", "😢", "😭",
  "😡", "😱", "🥺", "😅", "😬", "🤝", "👍", "👎", "👏", "🙌", "🙏", "💪",
  "👋", "✌️", "👌", "🤞", "❤️", "🧡", "💛", "💚", "💙", "💜", "💔", "💯",
  "🔥", "✨", "⭐", "🎉", "🎁", "🌹", "🌸", "☀️", "🌙", "⚡", "☕", "🍉",
  "🍎", "🍞", "🍰", "⚽", "🏆", "🎵", "📌", "📷", "💡", "✅", "❌", "❓",
];

interface Props {
  onPick: (emoji: string) => void;
  onClose: () => void;
}

export function EmojiPicker({ onPick, onClose }: Props) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose();
    };
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    // Delay so the opening click doesn't immediately close it.
    const id = setTimeout(() => document.addEventListener("mousedown", onDown), 0);
    window.addEventListener("keydown", onKey);
    return () => {
      clearTimeout(id);
      document.removeEventListener("mousedown", onDown);
      window.removeEventListener("keydown", onKey);
    };
  }, [onClose]);

  return (
    <div
      ref={ref}
      className="animate-pop-in absolute bottom-full left-0 z-20 mb-2 w-[19.5rem] max-w-[calc(100vw-1.5rem)] rounded-2xl border border-slate-100 bg-white p-2 shadow-2xl shadow-brand-900/10 dark:border-white/5 dark:bg-slate-800"
    >
      <div className="grid grid-cols-8 gap-0.5">
        {EMOJIS.map((e) => (
          <button
            key={e}
            type="button"
            onMouseDown={(ev) => ev.preventDefault()}
            onClick={() => onPick(e)}
            className="flex h-9 w-9 items-center justify-center rounded-lg text-[22px] transition hover:scale-110 hover:bg-brand-50 dark:hover:bg-white/10"
          >
            {e}
          </button>
        ))}
      </div>
    </div>
  );
}
