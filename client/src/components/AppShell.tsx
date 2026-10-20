// components/AppShell.tsx
// When locked, the lock screen REPLACES the app: every page, form and person on screen is unmounted.
import { useState, type ReactNode } from "react";
import { HttpError } from "../api/http";
import { useIdleLock } from "../hooks/useIdleLock";
import { t } from "../strings";

function LockScreen({ onUnlock }: { onUnlock: (pin: string) => Promise<boolean> }) {
  const [pin, setPin] = useState("");
  const [problem, setProblem] = useState<string | null>(null);
  return (
    <main className="lock-screen">
      <h1>{t.lock.title}</h1>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          try {
            setProblem((await onUnlock(pin)) ? null : t.lock.wrong);
          } catch (err) {
            // "Too many tries" comes back as a plain sentence; anything else: the backend couldn't be reached.
            setProblem(err instanceof HttpError && err.status < 500 ? err.message : t.lock.noConnection);
          } finally {
            setPin("");                        // never leave a PIN sitting in the box
          }
        }}
      >
        <label>
          {t.lock.pin}
          <input type="password" inputMode="numeric" autoComplete="off" value={pin} onChange={(e) => setPin(e.target.value)} />
        </label>
        {problem && <p role="alert">{problem}</p>}
        <button type="submit" className="big-button">{t.lock.unlock}</button>
      </form>
    </main>
  );
}

type ShellProps = {
  verifyPin: (pin: string) => Promise<boolean>;
  stillHere: () => Promise<unknown>;
  startLocked?: boolean;
  children: ReactNode;
};

export function AppShell({ verifyPin, stillHere, startLocked, children }: ShellProps) {
  const { locked, unlock } = useIdleLock(verifyPin, stillHere, startLocked);
  return locked ? <LockScreen onUnlock={unlock} /> : <>{children}</>;
}
