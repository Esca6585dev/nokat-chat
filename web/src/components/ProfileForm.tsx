"use client";

import { AtSign, Check } from "lucide-react";
import { useState } from "react";
import { api } from "@/lib/api";
import { errorMessage } from "@/lib/errors";
import { t } from "@/lib/i18n";
import type { ProfilePatch, User } from "@/types";
import { Avatar } from "./Avatar";
import { Spinner } from "./Spinner";

const USERNAME_RE = /^[a-z0-9_]{3,32}$/;

interface Props {
  user: User;
  submitLabel: string;
  onSaved: (u: User) => void;
  showAvatar?: boolean;
}

export function ProfileForm({ user, submitLabel, onSaved, showAvatar = true }: Props) {
  const [name, setName] = useState(user.name);
  const [username, setUsername] = useState(user.username);
  const [bio, setBio] = useState(user.bio);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<{ name?: string; username?: string }>({});
  const [saved, setSaved] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const fe: typeof fieldError = {};
    if (!name.trim()) fe.name = t.nameRequired;
    if (username && !USERNAME_RE.test(username)) fe.username = t.usernameInvalid;
    setFieldError(fe);
    if (fe.name || fe.username) return;

    const patch: ProfilePatch = {};
    if (name.trim() !== user.name) patch.name = name.trim();
    if (username !== user.username) patch.username = username;
    if (bio.trim() !== user.bio) patch.bio = bio.trim();

    setBusy(true);
    setError(null);
    try {
      const updated = Object.keys(patch).length ? await api.updateMe(patch) : user;
      setSaved(true);
      setTimeout(() => setSaved(false), 1800);
      onSaved(updated);
    } catch (err) {
      const msg = errorMessage(err, { 409: t.usernameTaken });
      if (msg === t.usernameTaken) setFieldError({ username: msg });
      else setError(msg);
    } finally {
      setBusy(false);
    }
  };

  const input =
    "w-full rounded-xl border bg-white px-3.5 py-3 text-slate-900 outline-none transition placeholder:text-slate-300 focus:ring-4 dark:bg-slate-900/60 dark:text-white dark:placeholder:text-slate-600";
  const ok = "border-slate-200 focus:border-brand-500 focus:ring-brand-500/15 dark:border-white/10";
  const bad = "border-rose-400 focus:ring-rose-500/15";

  return (
    <form onSubmit={submit} className="space-y-4">
      {showAvatar && (
        <div className="flex flex-col items-center gap-2 pb-2">
          <Avatar name={name || user.phone} seed={user.id} size={88} />
          <span className="text-sm text-slate-500 dark:text-slate-400">{user.phone}</span>
        </div>
      )}

      <label className="block">
        <span className="mb-1.5 block text-sm font-medium text-slate-600 dark:text-slate-300">{t.name}</span>
        <input
          value={name}
          onChange={(e) => {
            setName(e.target.value);
            setFieldError((f) => ({ ...f, name: undefined }));
          }}
          placeholder={t.namePlaceholder}
          maxLength={64}
          autoFocus={!user.name}
          className={`${input} ${fieldError.name ? bad : ok}`}
        />
        {fieldError.name && <span className="mt-1 block text-sm text-rose-500">{fieldError.name}</span>}
      </label>

      <label className="block">
        <span className="mb-1.5 block text-sm font-medium text-slate-600 dark:text-slate-300">{t.username}</span>
        <div className="relative">
          <AtSign size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={username}
            onChange={(e) => {
              setUsername(e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, ""));
              setFieldError((f) => ({ ...f, username: undefined }));
            }}
            placeholder={t.usernamePlaceholder}
            maxLength={32}
            autoCapitalize="off"
            autoCorrect="off"
            spellCheck={false}
            className={`${input} pl-9 ${fieldError.username ? bad : ok}`}
          />
        </div>
        <span className={`mt-1 block text-xs ${fieldError.username ? "text-rose-500" : "text-slate-400"}`}>
          {fieldError.username || t.usernameHint}
        </span>
      </label>

      <label className="block">
        <span className="mb-1.5 block text-sm font-medium text-slate-600 dark:text-slate-300">{t.bio}</span>
        <textarea
          value={bio}
          onChange={(e) => setBio(e.target.value)}
          placeholder={t.bioPlaceholder}
          maxLength={160}
          rows={3}
          className={`${input} ${ok} resize-none`}
        />
        <span className="mt-1 block text-right text-xs tabular-nums text-slate-400">{bio.length}/160</span>
      </label>

      {error && <p className="text-sm text-rose-500">{error}</p>}

      <button
        type="submit"
        disabled={busy}
        className="flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-brand-600 to-brand-500 py-3.5 font-semibold text-white shadow-lg shadow-brand-600/25 transition hover:shadow-brand-600/40 active:scale-[0.99] disabled:opacity-60"
      >
        {busy ? (
          <Spinner size={18} />
        ) : saved ? (
          <>
            <Check size={18} /> {t.saved}
          </>
        ) : (
          submitLabel
        )}
      </button>
    </form>
  );
}
