// hooks/useIdleLock.ts
// Laptops get left on desks. After 10 minutes with no clicks or typing, the app locks.
// Only a person counts as activity; background refreshes don't. The backend has the same
// 10-minute rule and is the real lock: unlocking always goes through it (verifyPin).
// While a person types, the server is told about it once a minute (stillHere), because a long
// form makes no other requests and the server would otherwise lock on its own.
import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { authEvents } from "../api/client";

const IDLE_MS = 10 * 60 * 1000;
const TELL_SERVER_MS = 60 * 1000;

export function useIdleLock(
  verifyPin: (pin: string) => Promise<boolean>,
  stillHere: () => Promise<unknown>,
  startLocked = false,   // the server already said "locked" (e.g. after a reload)
) {
  const [locked, setLocked] = useState(startLocked);
  const lockedNow = useRef(startLocked);
  const queryClient = useQueryClient();
  const lastActivity = useRef(0);
  const lastTold = useRef(0);

  useEffect(() => {
    lastActivity.current = Date.now();
    const expired = () => Date.now() - lastActivity.current >= IDLE_MS;
    const lock = () => {
      void queryClient.cancelQueries();   // stop anything still loading
      // Forget every person loaded so far; keep who is logged in, and saves waiting for the internet.
      queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== "me" });
      lockedNow.current = true;
      setLocked(true);
    };
    // Activity after the deadline doesn't buy more time: it locks.
    const onActivity = () => {
      if (lockedNow.current) return;     // typing the PIN on the lock screen isn't activity
      if (expired()) return lock();
      const now = Date.now();
      lastActivity.current = now;
      if (now - lastTold.current >= TELL_SERVER_MS) {
        lastTold.current = now;
        stillHere().catch(() => {});      // a "locked" answer arrives as an event; network trouble is ignored
      }
    };
    // Timers pause when a laptop sleeps, so also check the clock on wake-up and every 15 s.
    const onVisible = () => !lockedNow.current && document.visibilityState === "visible" && expired() && lock();
    const tick = window.setInterval(() => !lockedNow.current && expired() && lock(), 15_000);

    const events = ["pointerdown", "keydown"] as const;
    events.forEach((e) => window.addEventListener(e, onActivity));
    document.addEventListener("visibilitychange", onVisible);
    authEvents.addEventListener("locked", lock);   // the server is the real lock: when it says so, lock here too
    return () => {
      authEvents.removeEventListener("locked", lock);
      window.clearInterval(tick);
      events.forEach((e) => window.removeEventListener(e, onActivity));
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [queryClient, stillHere]);

  // Unlocks only if the backend accepts the PIN, and starts a fresh 10 minutes.
  const unlock = async (pin: string) => {
    if (!(await verifyPin(pin))) return false;
    lastActivity.current = Date.now();
    lockedNow.current = false;
    setLocked(false);
    return true;
  };

  return { locked, unlock };
}
