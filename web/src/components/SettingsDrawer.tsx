"use client";

import { ArrowLeft, LogOut, Moon, Phone } from "lucide-react";
import { useEffect } from "react";
import { userDisplayName } from "@/lib/format";
import { t } from "@/lib/i18n";
import { useChatStore } from "@/lib/store";
import { Avatar } from "./Avatar";
import { Logo } from "./Logo";
import { ProfileForm } from "./ProfileForm";

interface Props {
  open: boolean;
  onClose: () => void;
  onLogout: () => void;
}

export function SettingsDrawer({ open, onClose, onLogout }: Props) {
  const me = useChatStore((s) => s.me);
  const setMe = useChatStore((s) => s.setMe);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open || !me) return null;
  const name = userDisplayName(me);

  return (
    <div className="fixed inset-0 z-40 md:absolute">
      <div className="animate-fade-in absolute inset-0 bg-slate-900/30 md:hidden" onClick={onClose} />
      <div className="animate-slide-in-left absolute inset-y-0 left-0 flex w-full flex-col bg-white shadow-2xl sm:max-w-sm md:max-w-none dark:bg-app-dark-surface">
        <div className="flex items-center gap-2 px-3 py-3">
          <button
            onClick={onClose}
            className="rounded-full p-2 text-slate-500 transition hover:bg-slate-100 dark:hover:bg-white/5"
            aria-label={t.back}
          >
            <ArrowLeft size={22} />
          </button>
          <h2 className="text-lg font-semibold text-slate-900 dark:text-white">{t.settings}</h2>
        </div>

        <div className="thin-scroll min-h-0 flex-1 overflow-y-auto">
          <div className="relative mx-3 overflow-hidden rounded-3xl bg-gradient-to-br from-sky-accent via-brand-600 to-brand-800 px-5 pb-5 pt-6 text-white">
            <div className="pointer-events-none absolute -right-10 -top-10 h-40 w-40 rounded-full bg-white/10 blur-2xl" />
            <div className="relative flex items-center gap-4">
              <Avatar name={name} seed={me.id} url={me.avatar_url} size={72} className="ring-4 ring-white/25 rounded-full" />
              <div className="min-w-0">
                <div className="truncate text-xl font-semibold">{name}</div>
                {me.username && <div className="truncate text-white/80">@{me.username}</div>}
                <div className="mt-1 flex items-center gap-1.5 text-sm text-white/80">
                  <Phone size={13} /> {me.phone}
                </div>
              </div>
            </div>
            {me.bio && <p className="relative mt-4 text-sm text-white/90">{me.bio}</p>}
          </div>

          <div className="px-5 pt-6">
            <h3 className="mb-3 text-xs font-semibold uppercase tracking-wide text-slate-400">{t.profile}</h3>
            <ProfileForm key={me.id} user={me} submitLabel={t.save} onSaved={setMe} showAvatar={false} />
          </div>

          <div className="mx-5 mt-6 flex items-start gap-3 rounded-2xl bg-slate-50 p-3.5 text-sm text-slate-500 dark:bg-white/5 dark:text-slate-400">
            <Moon size={18} className="mt-0.5 shrink-0" />
            {t.darkModeHint}
          </div>

          <div className="px-5 py-6">
            <button
              onClick={() => {
                if (window.confirm(t.logoutConfirm)) onLogout();
              }}
              className="flex w-full items-center justify-center gap-2 rounded-2xl border border-rose-200 py-3 font-semibold text-rose-600 transition hover:bg-rose-50 dark:border-rose-500/30 dark:text-rose-400 dark:hover:bg-rose-500/10"
            >
              <LogOut size={18} /> {t.logout}
            </button>
            <div className="mt-6 flex items-center justify-center gap-2 text-xs text-slate-400">
              <Logo size={16} className="rounded" /> {t.appName} · {t.version} 0.1.0
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
