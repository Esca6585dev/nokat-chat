/* eslint-disable @next/next/no-img-element */
"use client";

import { Download, X } from "lucide-react";
import { useEffect, useState } from "react";
import { t } from "@/lib/i18n";
import { useChatStore } from "@/lib/store";
import { Spinner } from "./Spinner";

export function saveBlob(blob: Blob, name: string) {
  const href = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = href;
  a.download = name || "file";
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(href), 1000);
}

async function download(url: string, name: string, blob?: Blob) {
  if (blob) {
    saveBlob(blob, name);
    return;
  }
  try {
    // Cross-origin <a download> is ignored by browsers, so fetch as a blob first.
    const res = await fetch(url);
    if (!res.ok) throw new Error(String(res.status));
    saveBlob(await res.blob(), name || "image");
  } catch {
    window.open(url, "_blank", "noopener");
  }
}

export function Lightbox() {
  const box = useChatStore((s) => s.lightbox);
  const close = useChatStore((s) => s.closeLightbox);
  const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [blobUrl, setBlobUrl] = useState<string | null>(null);

  // Decrypted secret media: own object URL, revoked when the lightbox closes.
  useEffect(() => {
    if (!box?.blob) {
      setBlobUrl(null);
      return;
    }
    const u = URL.createObjectURL(box.blob);
    setBlobUrl(u);
    return () => URL.revokeObjectURL(u);
  }, [box?.blob]);

  useEffect(() => {
    setLoaded(false);
    if (!box) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.stopImmediatePropagation();
        close();
      }
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [box, close]);

  if (!box) return null;

  return (
    <div className="animate-fade-in fixed inset-0 z-[70] flex flex-col bg-slate-950/95" role="dialog" aria-modal onClick={close}>
      <div className="flex items-center gap-2 px-3 py-3 text-white sm:px-5" onClick={(e) => e.stopPropagation()}>
        <span className="min-w-0 flex-1 truncate text-sm text-white/80">{box.name}</span>
        <button
          onClick={async () => {
            setBusy(true);
            await download(box.url, box.name, box.blob);
            setBusy(false);
          }}
          className="flex items-center gap-2 rounded-full px-3 py-2 text-sm font-medium transition hover:bg-white/10"
          aria-label={t.download}
        >
          {busy ? <Spinner size={18} /> : <Download size={20} />}
          <span className="hidden sm:inline">{t.download}</span>
        </button>
        <button onClick={close} className="rounded-full p-2 transition hover:bg-white/10" aria-label={t.close}>
          <X size={24} />
        </button>
      </div>
      <div className="relative flex min-h-0 flex-1 items-center justify-center p-3 sm:p-8">
        {!loaded && <Spinner size={32} className="absolute text-white/70" />}
        <img
          src={box.blob ? (blobUrl ?? undefined) : box.url}
          alt={box.name}
          onLoad={() => setLoaded(true)}
          onClick={(e) => e.stopPropagation()}
          className={`max-h-full max-w-full select-none rounded-lg object-contain shadow-2xl transition-opacity duration-200 ${loaded ? "opacity-100" : "opacity-0"}`}
          draggable={false}
        />
      </div>
    </div>
  );
}
