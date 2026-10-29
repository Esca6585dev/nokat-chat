"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { cacheUser, getCachedUser, getToken } from "@/lib/auth";
import { t } from "@/lib/i18n";
import type { User } from "@/types";
import { Logo } from "@/components/Logo";
import { ProfileForm } from "@/components/ProfileForm";
import { Spinner } from "@/components/Spinner";

export default function SetupPage() {
  const router = useRouter();
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    if (!getToken()) {
      router.replace("/");
      return;
    }
    const cached = getCachedUser();
    if (cached) setUser(cached);
    api
      .me()
      .then((u) => {
        cacheUser(u);
        setUser(u);
      })
      .catch(() => {
        if (!getToken()) router.replace("/");
      });
  }, [router]);

  return (
    <main className="relative flex min-h-dvh items-center justify-center px-5 py-10">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-80 bg-gradient-to-b from-sky-200/60 to-transparent dark:from-brand-800/30" />
      <div className="relative w-full max-w-sm">
        <div className="mb-6 flex flex-col items-center gap-3 text-center">
          <Logo size={56} className="rounded-2xl shadow-lg shadow-brand-600/25" />
          <h1 className="text-2xl font-semibold text-slate-900 dark:text-white">{t.setupTitle}</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400">{t.setupSubtitle}</p>
        </div>
        <div className="animate-pop-in rounded-3xl border border-white/60 bg-white/80 p-6 shadow-xl shadow-brand-900/5 backdrop-blur sm:p-7 dark:border-white/5 dark:bg-app-dark-surface/80">
          {user ? (
            <ProfileForm
              user={user}
              submitLabel={t.continue}
              onSaved={(u) => {
                cacheUser(u);
                router.replace("/chat");
              }}
            />
          ) : (
            <div className="flex justify-center py-10 text-brand-500">
              <Spinner size={28} />
            </div>
          )}
        </div>
      </div>
    </main>
  );
}
