"use client";

import { useEffect, useState } from "react";
import { attachmentUrl } from "@/lib/config";
import { fetchDecryptedBlob } from "@/lib/e2e";
import type { UIMessage } from "@/types";

export interface SecretMedia {
  blob: Blob | null;
  /** object URL for the decrypted blob (revoked on unmount) */
  url: string | null;
  error: boolean;
  loading: boolean;
}

/**
 * Download + decrypt a secret-chat attachment in memory. `auto` = start immediately (images);
 * otherwise call `load()` (files, on click).
 */
export function useSecretMedia(message: UIMessage, auto: boolean) {
  const sf = message.secretFile;
  const [state, setState] = useState<SecretMedia>({ blob: null, url: null, error: false, loading: false });
  const [requested, setRequested] = useState(auto);

  useEffect(() => {
    if (!sf || !requested) return;
    let cancelled = false;
    let objectUrl: string | null = null;
    setState((s) => ({ ...s, loading: true, error: false }));
    fetchDecryptedBlob(attachmentUrl(sf.url), sf.key, sf.mime)
      .then((blob) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setState({ blob, url: objectUrl, error: false, loading: false });
      })
      .catch(() => {
        if (!cancelled) setState({ blob: null, url: null, error: true, loading: false });
      });
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [sf?.url, sf?.key, sf?.mime, requested]); // eslint-disable-line react-hooks/exhaustive-deps

  return { ...state, load: () => setRequested(true) };
}
