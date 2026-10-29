"use client";

import { ChevronDown, ArrowRight } from "lucide-react";
import { useState } from "react";
import { t } from "@/lib/i18n";
import { Spinner } from "../Spinner";
import { COUNTRIES } from "./countries";

interface Props {
  initialIso?: string;
  initialNumber?: string;
  busy: boolean;
  error: string | null;
  onSubmit: (e164: string, iso: string, local: string) => void;
}

export function PhoneStep({ initialIso = "TM", initialNumber = "", busy, error, onSubmit }: Props) {
  const [iso, setIso] = useState(initialIso);
  const [number, setNumber] = useState(initialNumber);
  const [localError, setLocalError] = useState<string | null>(null);
  const country = COUNTRIES.find((c) => c.iso === iso) ?? COUNTRIES[0];

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const digits = (country.code + number).replace(/\D/g, "");
    if (digits.length < 8 || digits.length > 15 || number.replace(/\D/g, "").length < 4) {
      setLocalError(t.invalidPhone);
      return;
    }
    setLocalError(null);
    onSubmit(`+${digits}`, iso, number);
  };

  const shownError = localError || error;

  return (
    <form onSubmit={submit} className="animate-pop-in space-y-5">
      <div className="text-center">
        <h1 className="text-2xl font-semibold text-slate-900 dark:text-white">{t.loginTitle}</h1>
        <p className="mt-1.5 text-sm text-slate-500 dark:text-slate-400">{t.loginSubtitle}</p>
      </div>

      <div
        className={`flex items-stretch overflow-hidden rounded-2xl border bg-white transition focus-within:ring-4 dark:bg-slate-900/60 ${
          shownError
            ? "border-rose-400 focus-within:ring-rose-500/15"
            : "border-slate-200 focus-within:border-brand-500 focus-within:ring-brand-500/15 dark:border-white/10"
        }`}
      >
        <label className="relative flex items-center gap-1.5 border-r border-slate-200 pl-3.5 pr-2 dark:border-white/10">
          <span className="sr-only">{t.countryCode}</span>
          <span className="text-xl leading-none">{country.flag}</span>
          <span className="font-medium text-slate-800 dark:text-slate-100">{country.code}</span>
          <ChevronDown size={16} className="text-slate-400" />
          <select
            value={iso}
            onChange={(e) => setIso(e.target.value)}
            className="absolute inset-0 cursor-pointer opacity-0"
            aria-label={t.countryCode}
          >
            {COUNTRIES.map((c) => (
              <option key={c.iso} value={c.iso}>
                {c.flag} {c.name} ({c.code})
              </option>
            ))}
          </select>
        </label>
        <input
          type="tel"
          inputMode="tel"
          autoComplete="tel-national"
          autoFocus
          value={number}
          onChange={(e) => {
            setNumber(e.target.value.replace(/[^\d\s-]/g, ""));
            setLocalError(null);
          }}
          placeholder={country.placeholder}
          className="min-w-0 flex-1 bg-transparent px-3.5 py-3.5 text-lg tracking-wide text-slate-900 outline-none placeholder:text-slate-300 dark:text-white dark:placeholder:text-slate-600"
        />
      </div>

      {shownError && <p className="-mt-2 text-sm text-rose-500">{shownError}</p>}

      <button
        type="submit"
        disabled={busy || !number.trim()}
        className="group flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-brand-600 to-brand-500 py-3.5 font-semibold text-white shadow-lg shadow-brand-600/25 transition hover:shadow-brand-600/40 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-50 disabled:shadow-none"
      >
        {busy ? <Spinner size={18} /> : t.sendCode}
        {!busy && <ArrowRight size={18} className="transition group-hover:translate-x-0.5" />}
      </button>

      <p className="text-center text-xs text-slate-400 dark:text-slate-500">{t.termsHint}</p>
    </form>
  );
}
