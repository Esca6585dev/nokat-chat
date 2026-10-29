"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { MessageCircle, ShieldCheck, Zap } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { getCachedUser, getToken, setSession } from "@/lib/auth";
import { errorMessage } from "@/lib/errors";
import { t } from "@/lib/i18n";
import { Logo, Wordmark } from "@/components/Logo";
import { PhoneStep } from "@/components/auth/PhoneStep";
import { OtpStep } from "@/components/auth/OtpStep";

type Step = "phone" | "otp";

export default function LoginPage() {
  const router = useRouter();
  const [ready, setReady] = useState(false);
  const [step, setStep] = useState<Step>("phone");
  const [phone, setPhone] = useState("");
  const [iso, setIso] = useState("TM");
  const [local, setLocal] = useState("");
  const [devCode, setDevCode] = useState<string | undefined>();
  const [sentAt, setSentAt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (getToken()) {
      const u = getCachedUser();
      router.replace(u && !u.name ? "/setup" : "/chat");
    } else {
      setReady(true);
    }
  }, [router]);

  const requestCode = async (e164: string) => {
    setBusy(true);
    setError(null);
    try {
      const res = await api.requestOtp(e164);
      setPhone(e164);
      setDevCode(res.dev_code);
      setSentAt(Date.now());
      setStep("otp");
    } catch (e) {
      // Already requested within 30 s: move on to the code screen anyway.
      if (step === "phone" && e instanceof ApiError && e.status === 429 && phone === e164) {
        setStep("otp");
      } else {
        setError(errorMessage(e));
      }
    } finally {
      setBusy(false);
    }
  };

  const verify = async (code: string) => {
    setBusy(true);
    setError(null);
    try {
      const res = await api.verifyOtp(phone, code);
      setSession(res.token, res.user);
      router.replace(res.is_new || !res.user.name ? "/setup" : "/chat");
    } catch (e) {
      setError(errorMessage(e, { 400: t.invalidCode, 401: t.invalidCode }));
      setBusy(false);
    }
  };

  if (!ready) return <div className="min-h-dvh" />;

  return (
    <main className="relative flex min-h-dvh flex-col overflow-hidden lg:flex-row">
      {/* Brand panel */}
      <section className="relative hidden flex-1 flex-col justify-between overflow-hidden bg-gradient-to-br from-sky-accent via-brand-600 to-brand-800 p-12 text-white lg:flex">
        <div className="pointer-events-none absolute -right-24 -top-24 h-96 w-96 rounded-full bg-white/10 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-32 -left-16 h-[28rem] w-[28rem] rounded-full bg-sky-300/20 blur-3xl" />
        <div className="relative flex items-center gap-3">
          <Logo size={44} className="rounded-xl shadow-lg ring-1 ring-white/30" />
          <span className="text-2xl font-bold tracking-tight">{t.appName}</span>
        </div>
        <div className="relative max-w-md">
          <h2 className="text-4xl font-bold leading-tight">{t.tagline}</h2>
          <ul className="mt-8 space-y-4 text-white/90">
            <li className="flex items-center gap-3">
              <span className="rounded-xl bg-white/15 p-2">
                <Zap size={18} />
              </span>
              {t.featureRealtime}
            </li>
            <li className="flex items-center gap-3">
              <span className="rounded-xl bg-white/15 p-2">
                <MessageCircle size={18} />
              </span>
              {t.featureChats}
            </li>
            <li className="flex items-center gap-3">
              <span className="rounded-xl bg-white/15 p-2">
                <ShieldCheck size={18} />
              </span>
              {t.featureSecure}
            </li>
          </ul>
        </div>
        <p className="relative text-sm text-white/60">© {new Date().getFullYear()} Nokat Chat</p>
      </section>

      {/* Form panel */}
      <section className="relative flex flex-1 items-center justify-center px-5 py-10 lg:max-w-xl">
        <div className="pointer-events-none absolute inset-x-0 top-0 h-72 bg-gradient-to-b from-sky-200/50 to-transparent lg:hidden dark:from-brand-800/30" />
        <div className="relative w-full max-w-sm">
          <div className="mb-8 flex flex-col items-center gap-3">
            <Logo size={84} className="rounded-[1.6rem] shadow-xl shadow-brand-600/25" />
            <Wordmark className="text-3xl" />
          </div>
          <div className="rounded-3xl border border-white/60 bg-white/80 p-6 shadow-xl shadow-brand-900/5 backdrop-blur sm:p-7 dark:border-white/5 dark:bg-app-dark-surface/80">
            {step === "phone" ? (
              <PhoneStep
                initialIso={iso}
                initialNumber={local}
                busy={busy}
                error={error}
                onSubmit={(e164, i, l) => {
                  setIso(i);
                  setLocal(l);
                  void requestCode(e164);
                }}
              />
            ) : (
              <OtpStep
                phone={phone}
                devCode={devCode}
                busy={busy}
                error={error}
                sentAt={sentAt}
                onVerify={verify}
                onResend={() => void requestCode(phone)}
                onBack={() => {
                  setStep("phone");
                  setError(null);
                }}
                onClearError={() => setError(null)}
              />
            )}
          </div>
        </div>
      </section>
    </main>
  );
}
