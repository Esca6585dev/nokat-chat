"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { onUnauthorized } from "@/lib/api";
import { clearSession, getCachedUser, getToken } from "@/lib/auth";
import { useChatStore } from "@/lib/store";
import type { ID } from "@/types";
import { Conversation } from "@/components/Conversation";
import { NewChatDialog, type NewChatMode } from "@/components/NewChatDialog";
import { SettingsDrawer } from "@/components/SettingsDrawer";
import { Sidebar } from "@/components/Sidebar";

const HISTORY_KEY = "nokatChat";

export default function ChatPage() {
  const router = useRouter();
  const [authed, setAuthed] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [dialog, setDialog] = useState<NewChatMode | null>(null);
  const activeChatId = useChatStore((s) => s.activeChatId);
  const setActive = useChatStore((s) => s.setActive);
  const setFocused = useChatStore((s) => s.setFocused);
  const me = useChatStore((s) => s.me);

  // Auth guard + realtime lifecycle
  useEffect(() => {
    if (!getToken()) {
      router.replace("/");
      return;
    }
    const cached = getCachedUser();
    if (cached && !cached.name) {
      router.replace("/setup");
      return;
    }
    setAuthed(true);
    const store = useChatStore.getState();
    store.init();
    onUnauthorized(() => {
      useChatStore.getState().teardown();
      router.replace("/");
    });
    return () => {
      onUnauthorized(null);
      useChatStore.getState().teardown();
    };
  }, [router]);

  // Redirect to profile setup if the server says we have no name yet.
  useEffect(() => {
    if (me && !me.name) router.replace("/setup");
  }, [me, router]);

  // Window focus / visibility → read receipts only while actually looking.
  useEffect(() => {
    const update = () => setFocused(document.visibilityState === "visible" && document.hasFocus());
    update();
    window.addEventListener("focus", update);
    window.addEventListener("blur", update);
    document.addEventListener("visibilitychange", update);
    return () => {
      window.removeEventListener("focus", update);
      window.removeEventListener("blur", update);
      document.removeEventListener("visibilitychange", update);
    };
  }, [setFocused]);

  // Browser back button closes the open chat (important on mobile).
  useEffect(() => {
    const onPop = () => {
      if (!(window.history.state && window.history.state[HISTORY_KEY])) setActive(null);
    };
    window.addEventListener("popstate", onPop);
    return () => window.removeEventListener("popstate", onPop);
  }, [setActive]);

  const openChat = useCallback(
    (id: ID) => {
      const cur = useChatStore.getState().activeChatId;
      if (!cur) window.history.pushState({ ...(window.history.state ?? {}), [HISTORY_KEY]: true }, "");
      setActive(id);
    },
    [setActive],
  );

  const closeChat = useCallback(() => {
    if (window.history.state && window.history.state[HISTORY_KEY]) window.history.back();
    else setActive(null);
  }, [setActive]);

  // Esc closes the conversation (when nothing else is open).
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !dialog && !settingsOpen && useChatStore.getState().activeChatId) closeChat();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [dialog, settingsOpen, closeChat]);

  // Tab title with unread count
  const unread = useChatStore((s) => s.chats.reduce((n, c) => n + (c.unread_count || 0), 0));
  useEffect(() => {
    document.title = unread > 0 ? `(${unread}) Nokat Chat` : "Nokat Chat";
  }, [unread]);

  const logout = () => {
    clearSession();
    useChatStore.getState().teardown();
    router.replace("/");
  };

  if (!authed) return <div className="h-dvh" />;

  return (
    <main className="flex h-dvh overflow-hidden bg-app-bg dark:bg-app-dark">
      <div
        className={`relative h-full w-full shrink-0 border-r border-slate-200/70 md:block md:w-[360px] lg:w-[400px] dark:border-white/5 ${
          activeChatId ? "hidden" : "block"
        }`}
      >
        <Sidebar
          onOpenChat={openChat}
          onOpenSettings={() => setSettingsOpen(true)}
          onNewChat={() => setDialog("direct")}
          onNewGroup={() => setDialog("group")}
        />
        <SettingsDrawer open={settingsOpen} onClose={() => setSettingsOpen(false)} onLogout={logout} />
      </div>

      <section className={`h-full min-w-0 flex-1 ${activeChatId ? "block" : "hidden md:block"}`}>
        <Conversation chatId={activeChatId} onBack={closeChat} />
      </section>

      <NewChatDialog
        open={dialog !== null}
        initialMode={dialog ?? "direct"}
        onClose={() => setDialog(null)}
        onOpenChat={openChat}
      />
    </main>
  );
}
