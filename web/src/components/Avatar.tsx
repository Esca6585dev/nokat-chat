/* eslint-disable @next/next/no-img-element */
import { gradientFor, initials } from "@/lib/format";
import { Users } from "lucide-react";

interface Props {
  name: string;
  seed: string;
  url?: string;
  size?: number;
  online?: boolean;
  group?: boolean;
  className?: string;
}

export function Avatar({ name, seed, url, size = 48, online, group, className = "" }: Props) {
  const fontSize = Math.max(11, Math.round(size * 0.38));
  const dot = Math.max(10, Math.round(size * 0.26));
  return (
    <div className={`relative shrink-0 ${className}`} style={{ width: size, height: size }}>
      {url ? (
        <img src={url} alt={name} className="h-full w-full rounded-full object-cover" draggable={false} />
      ) : (
        <div
          className={`flex h-full w-full select-none items-center justify-center rounded-full bg-gradient-to-br ${gradientFor(seed)} font-semibold text-white shadow-inner`}
          style={{ fontSize }}
          aria-hidden
        >
          {group && !name ? <Users size={size * 0.45} /> : initials(name)}
        </div>
      )}
      {online && (
        <span
          className="absolute bottom-0 right-0 rounded-full border-2 border-white bg-emerald-500 dark:border-app-dark-surface"
          style={{ width: dot, height: dot }}
        />
      )}
    </div>
  );
}
