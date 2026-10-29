"use client";

import { ArrowLeft, KeyRound } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { t } from "@/lib/i18n";
import { Spinner } from "../Spinner";

const LEN = 6;
const RESEND_SECONDS = 30;

interface Props {
  phone: string;
  devCode?: string;
  busy: boolean;
  error: string | null;
  /** Bumped by the parent after each successful (re)send to restart the countdown. */
  sentAt: number;
  onVerify: (code: string) => void;
  onResend: () => void;
  onBack: () => void;
  onClearError: () => void;
}

export function OtpStep({ phone, devCode, busy, error, sentAt, onVerify, onResend, onBack, onClearError }: Props) {
  const [digits, setDigits] = useState<string[]>(() => Array(LEN).fill(""));
  const [left, setLeft] = useState(RESEND_SECONDS);
  const refs = useRef<(HTMLInputElement | null)[]>([]);
  const lastSubmitted = useRef<string>("");

  // Countdown
  useEffect(() => {
    const tick = () => setLeft(Math.max(0, RESEND_SECONDS - Math.floor((Date.now() - sentAt) / 1000)));
    tick();
    const id = setInterval(tick, 500);
    return () => clearInterval(id);
  }, [sentAt]);

  useEffect(() => {
    refs.current[0]?.focus();
  }, []);

  // When an error arrives, clear boxes and refocus for a quick retry.
  useEffect(() => {
    if (error) {
      setDigits(Array(LEN).fill(""));
      lastSubmitted.current = "";
      refs.current[0]?.focus();
    }
  }, [error]);

  const submitIfComplete = (next: string[]) => {
    const code = next.join("");
    if (code.length === LEN && /^\d{6}$/.test(code) && code !== lastSubmitted.current) {
      lastSubmitted.current = code;
      onVerify(code);
    }
  };

  const fillFrom = (start: number, raw: string) => {
    const chars = raw.replace(/\D/g, "").split("");
    if (!chars.length) return;
    const next = digits.slice();
    let i = start;
    for (const c of chars) {
      if (i >= LEN) break;
      next[i++] = c;
    }
    setDigits(next);
    if (error) onClearError();
    refs.current[Math.min(i, LEN - 1)]?.focus();
    submitIfComplete(next);
  };

  const onChange = (i: number, value: string) => {
    if (value === "") {
      const next = digits.slice();
      next[i] = "";
      setDigits(next);
      return;
    }
    // Take only newly typed chars (input may contain old digit + new one)
    const typed = value.length > 1 && digits[i] && value.startsWith(digits[i]) ? value.slice(1) : value;
    fillFrom(i, typed);
  };

  const onKeyDown = (i: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Backspace" && !digits[i] && i > 0) {
      e.preventDefault();
      const next = digits.slice();
      next[i - 1] = "";
      setDigits(next);
      refs.current[i - 1]?.focus();
    } else if (e.key === "ArrowLeft" && i > 0) {
      e.preventDefault();
      refs.current[i - 1]?.focus();
    } else if (e.key === "ArrowRight" && i < LEN - 1) {
      e.preventDefault();
      refs.current[i + 1]?.focus();
    } else if (e.key === "Enter") {
      submitIfComplete(digits);
    }
  };

  const onPaste = (i: number, e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    fillFrom(i, e.clipboardData.getData("text"));
  };

  const code = digits.join("");

  return (
    <div className="animate-pop-in space-y-5">
      <button
        onClick={onBack}
        className="-ml-2 flex items-center gap-1.5 rounded-full px-2 py-1 text-sm font-medium text-brand-600 transition hover:bg-brand-50 dark:text-sky-400 dark:hover:bg-white/5"
      >
        <ArrowLeft size={16} /> {t.changeNumber}
      </button>

      <div className="text-center">
        <h1 className="text-2xl font-semibold text-slate-900 dark:text-white">{t.otpTitle}</h1>
        <p className="mt-1.5 text-sm text-slate-500 dark:text-slate-400">{t.otpSubtitle(phone)}</p>
      </div>

      {devCode && (
        <button
          type="button"
          onClick={() => fillFrom(0, devCode)}
          className="mx-auto flex items-center gap-2 rounded-full border border-sky-200 bg-sky-50 px-3.5 py-1.5 text-sm font-medium text-sky-700 transition hover:bg-sky-100 dark:border-sky-400/20 dark:bg-sky-400/10 dark:text-sky-300"
        >
          <KeyRound size={14} />
          {t.devCode(devCode)}
        </button>
      )}

      <div className="flex justify-center gap-2 sm:gap-2.5">
        {digits.map((d, i) => (
          <input
            key={i}
            ref={(el) => {
              refs.current[i] = el;
            }}
            value={d}
            onChange={(e) => onChange(i, e.target.value)}
            onKeyDown={(e) => onKeyDown(i, e)}
            onPaste={(e) => onPaste(i, e)}
            onFocus={(e) => e.target.select()}
            inputMode="numeric"
            autoComplete={i === 0 ? "one-time-code" : "off"}
            maxLength={LEN}
            aria-label={`${i + 1}`}
            disabled={busy}
            className={`h-14 w-11 rounded-xl border-2 bg-white text-center text-2xl font-semibold text-slate-900 caret-brand-500 outline-none transition sm:h-16 sm:w-12 dark:bg-slate-900/60 dark:text-white ${
              error
                ? "border-rose-400"
                : d
                  ? "border-brand-500 shadow-md shadow-brand-500/10 dark:border-sky-400"
                  : "border-slate-200 focus:border-brand-500 dark:border-white/10 dark:focus:border-sky-400"
            }`}
          />
        ))}
      </div>

      {error && <p className="text-center text-sm text-rose-500">{error}</p>}

      <button
        onClick={() => submitIfComplete(digits)}
        disabled={busy || code.length !== LEN}
        className="flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-brand-600 to-brand-500 py-3.5 font-semibold text-white shadow-lg shadow-brand-600/25 transition hover:shadow-brand-600/40 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-50 disabled:shadow-none"
      >
        {busy ? <Spinner size={18} /> : t.verify}
      </button>

      <div className="text-center text-sm">
        {left > 0 ? (
          <span className="tabular-nums text-slate-400 dark:text-slate-500">{t.resendIn(left)}</span>
        ) : (
          <button
            onClick={() => {
              lastSubmitted.current = "";
              onResend();
            }}
            disabled={busy}
            className="font-medium text-brand-600 hover:underline dark:text-sky-400"
          >
            {t.resend}
          </button>
        )}
      </div>
    </div>
  );
}
