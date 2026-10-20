// components/AppShell.tsx
// When locked, the lock screen REPLACES the app: every page, form and person on screen is unmounted.
import { useState, type ReactNode } from "react";
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
          } catch {
            setProblem(t.lock.noConnection);   // the backend couldn't be reached: say so, stay locked
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

export function AppShell({ verifyPin, children }: { verifyPin: (pin: string) => Promise<boolean>; children: ReactNode }) {
  const { locked, unlock } = useIdleLock(verifyPin);
  return locked ? <LockScreen onUnlock={unlock} /> : <>{children}</>;
}
