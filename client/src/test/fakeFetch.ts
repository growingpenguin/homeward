// A fake backend for tests: answers by "METHOD path" and records every request.
import { vi } from "vitest";

type Answer = { status: number; body?: unknown; delayMs?: number };   // delayMs: a slow answer, for races
export type Sent = { method: string; path: string; headers: Record<string, string>; body?: unknown };

export function fakeBackend(answers: Record<string, Answer | (() => Answer)>) {
  const sent: Sent[] = [];
  vi.stubGlobal("fetch", vi.fn(async (path: string, init: RequestInit = {}) => {
    const method = init.method ?? "GET";
    sent.push({ method, path, headers: (init.headers ?? {}) as Record<string, string>, body: init.body && JSON.parse(String(init.body)) });
    const a = answers[`${method} ${path}`];
    const { status, body, delayMs } = typeof a === "function" ? a() : a ?? { status: 404, body: { message: "not found" } };
    if (delayMs) await new Promise((r) => setTimeout(r, delayMs));
    return new Response(status === 204 ? null : JSON.stringify(body ?? {}), { status, headers: { "Content-Type": "application/json" } });
  }));
  return sent;
}
