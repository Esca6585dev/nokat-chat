import { Check } from "lucide-react";
import { lastSeenLabel, userDisplayName } from "@/lib/format";
import { t } from "@/lib/i18n";
import type { User } from "@/types";
import { Avatar } from "./Avatar";

interface Props {
  user: User;
  onClick: () => void;
  selected?: boolean;
  selectable?: boolean;
  disabled?: boolean;
}

export function UserRow({ user, onClick, selected, selectable, disabled }: Props) {
  const name = userDisplayName(user);
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className="flex w-full items-center gap-3 rounded-2xl px-3 py-2 text-left transition hover:bg-brand-50/80 disabled:opacity-60 dark:hover:bg-white/5"
    >
      <Avatar name={name} seed={user.id} url={user.avatar_url} online={user.online} size={44} />
      <div className="min-w-0 flex-1">
        <div className="truncate font-medium text-slate-900 dark:text-slate-100">{name}</div>
        <div className="truncate text-sm text-slate-500 dark:text-slate-400">
          {user.username ? `@${user.username}` : user.online ? <span className="text-brand-600 dark:text-sky-400">{t.online}</span> : lastSeenLabel(user.last_seen)}
        </div>
      </div>
      {selectable && (
        <span
          className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 transition ${
            selected ? "border-brand-600 bg-brand-600 text-white" : "border-slate-300 dark:border-slate-600"
          }`}
        >
          {selected && <Check size={14} strokeWidth={3} />}
        </span>
      )}
    </button>
  );
}
