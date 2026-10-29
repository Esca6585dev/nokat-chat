"use client";

import { useEffect, useState } from "react";
import { api } from "./api";
import type { User } from "@/types";

/** Debounced GET /users/search. Queries shorter than `min` return nothing. */
export function useUserSearch(q: string, min = 1, delay = 250) {
  const [results, setResults] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const query = q.trim();

  useEffect(() => {
    if (query.length < min) {
      setResults([]);
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    const id = setTimeout(() => {
      api
        .searchUsers(query)
        .then((r) => !cancelled && setResults(r))
        .catch(() => !cancelled && setResults([]))
        .finally(() => !cancelled && setLoading(false));
    }, delay);
    return () => {
      cancelled = true;
      clearTimeout(id);
    };
  }, [query, min, delay]);

  return { results, loading };
}
