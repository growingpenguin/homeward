// api/client.ts
// Every call to the backend goes through here: cookies, the CSRF header, the activity header, and 401s.
import { HttpError } from "./http";
import type { LoginRequest, Me, ShelterOption } from "./types";
import { t } from "../strings";

// The backend's "locked" (10 minutes idle) and "expired" (12-hour shift, or the session is gone) answers.
// AppShell listens for "locked", App for "expired".
export const authEvents = new EventTarget();

function cookie(name: string): string | undefined {
  const found = document.cookie.split("; ").find((c) => c.startsWith(`${name}=`));
  return found ? decodeURIComponent(found.slice(name.length + 1)) : undefined;
}

type Options = {
  method?: "GET" | "POST" | "PUT";
  body?: unknown;
  userAction?: boolean;   // a person did something: counts as activity for the server's idle lock
  checkingLogin?: boolean; // a plain 401 here only means nobody is logged in yet
};

// A wrong PIN is a 401 with a sentence on these two. A 401 without one (anywhere) means the session is gone,
// e.g. the free server restarted and forgot it.
const PIN_CHECKS = ["/api/login", "/api/unlock"];

export async function api<T>(path: string, { method = "GET", body, userAction = false, checkingLogin = false }: Options = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  // Only real clicks and typing; background refreshes must not keep the server unlocked.
  if (userAction) headers["X-Homeward-Activity"] = "1";
  if (method !== "GET") {
    // The backend sets the CSRF cookie on /api/csrf, and clears it at login (a new one per session).
    if (!cookie("XSRF-TOKEN")) {
      const res = await fetch("/api/csrf", { credentials: "same-origin" });
      // No cookie means the write would be refused (403): report it as "no answer", which is retried.
      if (!cookie("XSRF-TOKEN")) throw new HttpError(res.status >= 500 ? res.status : 503, t.problem.noConnection);
    }
    headers["X-XSRF-TOKEN"] = cookie("XSRF-TOKEN")!;   // !: checked just above
  }

  const res = await fetch(path, {
    method,
    headers,
    credentials: "same-origin",
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.ok) return (res.status === 204 ? undefined : await res.json()) as T;

  const problem = (await res.json().catch(() => ({}))) as { message?: string; locked?: boolean; expired?: boolean };
  if (res.status === 401) {
    if (problem.locked) authEvents.dispatchEvent(new Event("locked"));
    else if (problem.expired || (!checkingLogin && (!PIN_CHECKS.includes(path) || !problem.message))) authEvents.dispatchEvent(new Event("expired"));
  }
  const error = new HttpError(res.status, problem.message ?? t.problem.server);
  error.locked = problem.locked === true;
  throw error;
}

export const getShelters = () => api<ShelterOption[]>("/api/shelters");

// Who is logged in: null if nobody is, "locked" if someone is but the screen is locked (e.g. after a reload).
export async function getMe(): Promise<Me | null | "locked"> {
  try {
    return await api<Me>("/api/me", { checkingLogin: true });
  } catch (e) {
    if (e instanceof HttpError && e.status === 401) return e.locked ? "locked" : null;
    throw e;
  }
}

export const login = (body: LoginRequest) => api<Me>("/api/login", { method: "POST", body, userAction: true });
export const logout = () => api<void>("/api/logout", { method: "POST", userAction: true });
// Tells the server a person is still working (typing a long form makes no other requests).
export const stillHere = () => api<Me>("/api/me", { userAction: true });

// For the lock screen: true if the PIN is right, false if not; other problems are thrown.
export async function verifyPin(pin: string): Promise<boolean> {
  try {
    await api<Me>("/api/unlock", { method: "POST", body: { pin }, userAction: true });
    return true;
  } catch (e) {
    if (e instanceof HttpError && e.status === 401) return false;
    throw e;
  }
}
