"use client";

import { Check, Clock3, Lock, MoreVertical, ShieldCheck, Smartphone, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { chatFingerprint } from "@/lib/e2e";
import { t } from "@/lib/i18n";
import { useChatStore } from "@/lib/store";
import type { Chat } from "@/types";
import { Modal } from "./Modal";
import { Spinner } from "./Spinner";

export type SecretView = "none" | "checking" | "waiting" | "invite" | "active" | "otherDevice";

export function secretViewOf(chat: Chat, meId: string, hasKey: boolean | undefined): SecretView {
  if (chat.type !== "secret" || !chat.secret) return "none";
  const iAmInitiator = chat.secret.initiator_id === meId;
  if (chat.secret.state === "pending") {
    if (!iAmInitiator) return "invite";
    return hasKey === undefined ? "checking" : hasKey ? "waiting" : "otherDevice";
  }
  return hasKey === undefined ? "checking" : hasKey ? "active" : "otherDevice";
}

// ---- Fingerprint ---------------------------------------------------------------

const FP_COLORS = ["#0EA5E9", "#14B8A6", "#2563EB", "#10B981", "#6366F1", "#22D3EE", "#1E40AF", "#5EEAD4"];

export function FingerprintDialog({
  open,
  onClose,
  chat,
  peerName,
}: {
  open: boolean;
  onClose: () => void;
  chat: Chat;
  peerName: string;
}) {
  const [fp, setFp] = useState<string | null>(null);
  const keys = `${chat.secret?.initiator_key}|${chat.secret?.peer_key}`;

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setFp(null);
    chatFingerprint(chat).then((f) => !cancelled && setFp(f));
    return () => {
      cancelled = true;
    };
  }, [open, keys]); // eslint-disable-line react-hooks/exhaustive-deps

  const groups = fp ? fp.split(" ") : [];
  const nibbles = fp ? fp.replace(/ /g, "").split("") : [];

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={
        <span className="flex items-center gap-2">
          <Lock size={18} className="text-teal-600 dark:text-teal-400" /> {t.encryptionKey}
        </span>
      }
    >
      <div className="px-5 pb-6">
        {!fp ? (
          <div className="flex justify-center py-12 text-teal-500">
            <Spinner size={28} />
          </div>
        ) : (
          <>
            <div className="mx-auto mb-5 grid w-44 grid-cols-8 gap-1 rounded-2xl bg-slate-50 p-3 dark:bg-white/5" aria-hidden>
              {nibbles.map((n, i) => (
                <span
                  key={i}
                  className="aspect-square rounded-[3px]"
                  style={{ background: FP_COLORS[parseInt(n, 16) % FP_COLORS.length], opacity: 0.45 + (parseInt(n, 16) / 15) * 0.55 }}
                />
              ))}
            </div>
            <div className="mx-auto grid max-w-xs grid-cols-4 gap-x-3 gap-y-2 rounded-2xl border border-teal-200 bg-teal-50/60 px-4 py-3 text-center font-mono text-lg font-semibold tracking-wider text-slate-800 dark:border-teal-400/20 dark:bg-teal-400/10 dark:text-teal-100">
              {groups.map((g, i) => (
                <span key={i}>{g}</span>
              ))}
            </div>
            <p className="mt-5 flex gap-2.5 text-sm leading-relaxed text-slate-600 dark:text-slate-300">
              <ShieldCheck size={20} className="mt-0.5 shrink-0 text-teal-600 dark:text-teal-400" />
              {t.fingerprintExplain(peerName)}
            </p>
          </>
        )}
      </div>
    </Modal>
  );
}

// ---- Pending states ---------------------------------------------------------------

function LockBadge({ children }: { children?: React.ReactNode }) {
  return (
    <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-teal-400 to-emerald-600 text-white shadow-lg shadow-teal-600/30">
      {children ?? <Lock size={30} />}
    </div>
  );
}

export function SecretWaitingCard({ peerName }: { peerName: string }) {
  return (
    <div className="flex flex-1 items-center justify-center p-6">
      <div className="animate-pop-in w-full max-w-sm rounded-3xl bg-white/90 p-6 text-center shadow-xl shadow-teal-900/5 backdrop-blur dark:bg-slate-800/80">
        <LockBadge>
          <Clock3 size={30} />
        </LockBadge>
        <p className="font-semibold text-slate-800 dark:text-slate-100">{t.secretWaiting(peerName)}</p>
        <p className="mt-2 text-sm leading-relaxed text-slate-500 dark:text-slate-400">{t.secretWaitingBody}</p>
      </div>
    </div>
  );
}

export function SecretInviteCard({ chat, peerName }: { chat: Chat; peerName: string }) {
  const acceptSecret = useChatStore((s) => s.acceptSecret);
  const deleteChat = useChatStore((s) => s.deleteChat);
  const [busy, setBusy] = useState<"accept" | "decline" | null>(null);

  return (
    <div className="flex flex-1 items-center justify-center p-6">
      <div className="animate-pop-in w-full max-w-sm rounded-3xl bg-white/90 p-6 text-center shadow-xl shadow-teal-900/5 backdrop-blur dark:bg-slate-800/80">
        <LockBadge />
        <p className="font-semibold text-slate-800 dark:text-slate-100">{t.secretInviteTitle(peerName)}</p>
        <p className="mt-2 text-sm leading-relaxed text-slate-500 dark:text-slate-400">{t.secretInviteBody}</p>
        <div className="mt-5 flex gap-2">
          <button
            disabled={!!busy}
            onClick={async () => {
              if (!window.confirm(t.declineSecretConfirm)) return;
              setBusy("decline");
              await deleteChat(chat.id);
              setBusy(null);
            }}
            className="flex flex-1 items-center justify-center gap-1.5 rounded-2xl border border-slate-200 py-3 font-semibold text-slate-600 transition hover:bg-slate-50 disabled:opacity-60 dark:border-white/10 dark:text-slate-300 dark:hover:bg-white/5"
          >
            {busy === "decline" ? <Spinner size={18} /> : <X size={18} />} {t.decline}
          </button>
          <button
            disabled={!!busy}
            onClick={async () => {
              setBusy("accept");
              await acceptSecret(chat.id);
              setBusy(null);
            }}
            className="flex flex-1 items-center justify-center gap-1.5 rounded-2xl bg-gradient-to-r from-teal-500 to-emerald-600 py-3 font-semibold text-white shadow-lg shadow-teal-600/25 transition hover:shadow-teal-600/40 disabled:opacity-60"
          >
            {busy === "accept" ? <Spinner size={18} /> : <Check size={18} />} {t.accept}
          </button>
        </div>
      </div>
    </div>
  );
}

export function OtherDeviceBanner() {
  return (
    <div className="px-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-2 sm:px-4">
      <div className="mx-auto flex max-w-3xl items-center gap-3 rounded-2xl bg-white/90 px-4 py-3 shadow-sm ring-1 ring-teal-200 backdrop-blur dark:bg-slate-800/80 dark:ring-teal-400/20">
        <Smartphone size={22} className="shrink-0 text-teal-600 dark:text-teal-400" />
        <div className="min-w-0">
          <p className="font-medium text-slate-800 dark:text-slate-100">{t.otherDeviceBanner}</p>
          <p className="text-sm text-slate-500 dark:text-slate-400">{t.otherDeviceHint}</p>
        </div>
      </div>
    </div>
  );
}

// ---- Header menu ---------------------------------------------------------------------

export interface MenuItem {
  label: string;
  icon: React.ReactNode;
  onClick: () => void;
  danger?: boolean;
}

export function HeaderMenu({ items }: { items: MenuItem[] }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.stopImmediatePropagation();
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", onDown);
    window.addEventListener("keydown", onKey, true);
    return () => {
      document.removeEventListener("mousedown", onDown);
      window.removeEventListener("keydown", onKey, true);
    };
  }, [open]);

  if (!items.length) return null;
  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        className="rounded-full p-2 text-slate-500 transition hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-white/5"
        aria-label={t.moreActions}
        aria-expanded={open}
      >
        <MoreVertical size={20} />
      </button>
      {open && (
        <div className="animate-pop-in absolute right-0 top-full z-30 mt-1 min-w-56 overflow-hidden rounded-2xl border border-slate-100 bg-white py-1.5 shadow-2xl shadow-brand-900/10 dark:border-white/5 dark:bg-slate-800">
          {items.map((it) => (
            <button
              key={it.label}
              onClick={() => {
                setOpen(false);
                it.onClick();
              }}
              className={`flex w-full items-center gap-3 px-4 py-2.5 text-left text-[15px] transition hover:bg-slate-50 dark:hover:bg-white/5 ${
                it.danger ? "text-rose-600 dark:text-rose-400" : "text-slate-700 dark:text-slate-200"
              }`}
            >
              {it.icon}
              {it.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
