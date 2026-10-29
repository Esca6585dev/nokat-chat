"use client";

import { AlertCircle } from "lucide-react";
import { useChatStore } from "@/lib/store";

export function Toast() {
  const toast = useChatStore((s) => s.toast);
  if (!toast) return null;
  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-24 z-[60] flex justify-center px-4" role="status" aria-live="polite">
      <div
        key={toast.id}
        className="animate-pop-in flex max-w-md items-center gap-2 rounded-2xl bg-slate-900/90 px-4 py-3 text-sm font-medium text-white shadow-2xl backdrop-blur dark:bg-slate-700/95"
      >
        <AlertCircle size={18} className="shrink-0 text-rose-400" />
        {toast.text}
      </div>
    </div>
  );
}
