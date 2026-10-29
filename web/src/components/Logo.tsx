/* eslint-disable @next/next/no-img-element */
import { t } from "@/lib/i18n";

export function Logo({ size = 40, className = "" }: { size?: number; className?: string }) {
  return <img src="/logo.svg" width={size} height={size} alt={t.appName} className={className} draggable={false} />;
}

export function Wordmark({ className = "" }: { className?: string }) {
  return (
    <span className={`font-bold tracking-tight ${className}`}>
      <span className="bg-gradient-to-r from-sky-accent via-brand-600 to-brand-800 bg-clip-text text-transparent dark:from-sky-300 dark:via-sky-400 dark:to-brand-400">
        Nokat
      </span>{" "}
      <span className="text-slate-800 dark:text-slate-100">Chat</span>
    </span>
  );
}
