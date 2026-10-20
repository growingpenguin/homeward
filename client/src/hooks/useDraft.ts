// hooks/useDraft.ts
// Keeps a half-typed form if the page reloads or the Wi-Fi drops, so an interruption never loses work.
// Safe on shared laptops:
//  - the key includes the logged-in volunteer, so nobody else's draft is ever loaded
//  - drafts expire after a shift (12 h) and are checked against the form's rules before use
//  - sessionStorage: gone when the tab closes; logout clears every draft
import { useEffect, useState } from "react";
import type { z } from "zod";

const PREFIX = "homeward-draft:";
const MAX_AGE_MS = 12 * 60 * 60 * 1000;

type Saved = { savedAt: number; data: unknown };

function load<T>(key: string, schema: z.ZodType<T, z.ZodTypeDef, unknown>, initial: T): T {
  try {
    const raw = sessionStorage.getItem(PREFIX + key);
    if (!raw) return initial;
    const saved = JSON.parse(raw) as Partial<Saved> | null;
    if (!saved || typeof saved.savedAt !== "number" || Date.now() - saved.savedAt > MAX_AGE_MS) {
      sessionStorage.removeItem(PREFIX + key);          // too old or broken: throw it away
      return initial;
    }
    const parsed = schema.safeParse(saved.data);
    return parsed.success ? parsed.data : initial;     // doesn't fit the form any more: start fresh
  } catch {
    return initial;
  }
}

export function useDraft<T>(
  staffId: string,                                     // whose draft this is
  form: string,                                        // which form: "register", "search"
  schema: z.ZodType<T, z.ZodTypeDef, unknown>,
  initial: T,
) {
  const key = `${staffId}:${form}`;
  const [state, setState] = useState(() => ({ key, value: load(key, schema, initial), dirty: false }));
  const [canKeepDraft, setCanKeepDraft] = useState(true);

  // Another volunteer or another form: load THAT draft, never copy this one across.
  if (state.key !== key) setState({ key, value: load(key, schema, initial), dirty: false });

  useEffect(() => {
    if (!state.dirty) return;                          // nothing typed yet: don't write anything
    try {
      sessionStorage.setItem(PREFIX + state.key, JSON.stringify({ savedAt: Date.now(), data: state.value }));
      setCanKeepDraft(true);
    } catch {
      setCanKeepDraft(false);                          // storage blocked or full: the banner says so
    }
  }, [state]);

  const setValue = (value: T) => setState({ key, value, dirty: true });

  const clear = () => {                                // after a successful save: really delete it
    try {
      sessionStorage.removeItem(PREFIX + key);
    } catch {
      /* ignore */
    }
    setState({ key, value: initial, dirty: false });   // dirty: false, so the empty form isn't written back
  };

  // Start the next form now, written straight to storage: the page may close in the same moment
  // (e.g. it navigates away), and then the effect above would never run.
  const reset = (value: T) => {
    try {
      sessionStorage.setItem(PREFIX + key, JSON.stringify({ savedAt: Date.now(), data: value }));
    } catch {
      setCanKeepDraft(false);
    }
    setState({ key, value, dirty: false });
  };

  return { value: state.value, setValue, clear, reset, canKeepDraft };
}

// Called on logout and when a different volunteer logs in: no personal data is left behind.
export function clearAllDrafts() {
  try {
    Object.keys(sessionStorage)
      .filter((k) => k.startsWith(PREFIX))
      .forEach((k) => sessionStorage.removeItem(k));
  } catch {
    /* ignore */
  }
}
