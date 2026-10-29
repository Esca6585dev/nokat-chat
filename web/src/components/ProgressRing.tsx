"use client";

import { RotateCw, X } from "lucide-react";
import { t } from "@/lib/i18n";

interface Props {
  /** 0..1, or undefined for an indeterminate spinner */
  progress?: number;
  failed?: boolean;
  size?: number;
  onCancel?: () => void;
  onRetry?: () => void;
  /** "dark" = translucent black disc (over images), "brand" = for file cards */
  tone?: "dark" | "brand" | "light";
}

/** Circular upload progress with a cancel (X) button, or a retry button when failed. */
export function ProgressRing({ progress, failed, size = 48, onCancel, onRetry, tone = "dark" }: Props) {
  const stroke = 3;
  const r = (size - stroke) / 2 - 2;
  const c = 2 * Math.PI * r;
  const p = Math.max(0.03, Math.min(1, progress ?? 0.25));
  const bg =
    tone === "dark"
      ? "bg-black/45 text-white backdrop-blur-sm"
      : tone === "light"
        ? "bg-white/25 text-white"
        : "bg-gradient-to-br from-sky-accent to-brand-600 text-white";

  const action = failed ? onRetry : onCancel;
  return (
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation();
        action?.();
      }}
      disabled={!action}
      aria-label={failed ? t.retry : t.cancelUpload}
      title={failed ? t.retry : t.cancelUpload}
      className={`relative flex shrink-0 items-center justify-center rounded-full transition ${bg} ${action ? "hover:scale-105" : "cursor-default"}`}
      style={{ width: size, height: size }}
    >
      {!failed && (
        <svg
          width={size}
          height={size}
          className={`absolute inset-0 -rotate-90 ${progress === undefined ? "animate-spin [animation-duration:1.2s]" : ""}`}
          aria-hidden
        >
          <circle
            cx={size / 2}
            cy={size / 2}
            r={r}
            fill="none"
            stroke="currentColor"
            strokeWidth={stroke}
            strokeLinecap="round"
            strokeDasharray={c}
            strokeDashoffset={c * (1 - p)}
            className="transition-[stroke-dashoffset] duration-200"
          />
        </svg>
      )}
      {failed ? <RotateCw size={size * 0.42} /> : action ? <X size={size * 0.4} strokeWidth={2.5} /> : null}
    </button>
  );
}
