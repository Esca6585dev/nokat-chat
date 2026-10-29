"use client";

import { X } from "lucide-react";
import { useEffect } from "react";
import { t } from "@/lib/i18n";

interface Props {
  open: boolean;
  onClose: () => void;
  title: React.ReactNode;
  children: React.ReactNode;
  footer?: React.ReactNode;
  leading?: React.ReactNode;
}

export function Modal({ open, onClose, title, children, footer, leading }: Props) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center sm:p-4" role="dialog" aria-modal>
      <div className="animate-fade-in absolute inset-0 bg-slate-900/40 backdrop-blur-[2px]" onClick={onClose} />
      <div className="animate-pop-in relative flex max-h-[92dvh] w-full flex-col overflow-hidden rounded-t-3xl bg-white shadow-2xl shadow-brand-900/20 sm:max-w-md sm:rounded-3xl dark:bg-app-dark-surface">
        <div className="flex items-center gap-2 px-5 pb-3 pt-4">
          {leading}
          <h2 className="flex-1 text-lg font-semibold text-slate-900 dark:text-slate-100">{title}</h2>
          <button
            onClick={onClose}
            className="rounded-full p-2 text-slate-500 transition hover:bg-slate-100 dark:hover:bg-white/5"
            aria-label={t.close}
          >
            <X size={20} />
          </button>
        </div>
        <div className="thin-scroll min-h-0 flex-1 overflow-y-auto">{children}</div>
        {footer && <div className="border-t border-slate-100 px-5 py-3 dark:border-white/5">{footer}</div>}
      </div>
    </div>
  );
}
