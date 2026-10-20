// hooks/useIdleLock.ts
// Laptops get left on desks. After 10 minutes with no clicks or typing, the app locks.
// Only a person counts as activity; background refreshes don't. The backend has the same
// 10-minute rule and is the real lock: unlocking always goes through it (verifyPin).
import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

const IDLE_MS = 10 * 60 * 1000;

export function useIdleLock(verifyPin: (pin: string) => Promise<boolean>) {
  const [locked, setLocked] = useState(false);
  const queryClient = useQueryClient();
  const lastActivity = useRef(Date.now());

  useEffect(() => {
    const expired = () => Date.now() - lastActivity.current >= IDLE_MS;
    const lock = () => {
      void queryClient.cancelQueries();   // stop anything still loading
      queryClient.clear();                // forget every person loaded so far
      setLocked(true);
    };
    // Activity after the deadline doesn't buy more time: it locks.
    const onActivity = () => (expired() ? lock() : (lastActivity.current = Date.now()));
    // Timers pause when a laptop sleeps, so also check the clock on wake-up and every 15 s.
    const onVisible = () => document.visibilityState === "visible" && expired() && lock();
    const tick = window.setInterval(() => expired() && lock(), 15_000);

    const events = ["pointerdown", "keydown"] as const;
    events.forEach((e) => window.addEventListener(e, onActivity));
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      window.clearInterval(tick);
      events.forEach((e) => window.removeEventListener(e, onActivity));
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [queryClient]);

  // Unlocks only if the backend accepts the PIN, and starts a fresh 10 minutes.
  const unlock = async (pin: string) => {
    if (!(await verifyPin(pin))) return false;
    lastActivity.current = Date.now();
    setLocked(false);
    return true;
  };

  return { locked, unlock };
}
