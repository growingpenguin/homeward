// pages/LoginPage.tsx
// Shelter, name, PIN. Wrong details and "too many tries" come back from the backend as plain sentences.
import { useState, type FormEvent } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { getShelters, login, wakeUp } from "../api/client";
import { HttpError } from "../api/http";
import type { Me } from "../api/types";
import { BigButton } from "../components/BigButton";
import { ConnectionBanner } from "../components/ConnectionBanner";
import { t } from "../strings";

// The free backend sleeps when unused and can take longer to wake than Netlify waits (about 26 s),
// so a login that got no answer (network trouble, 502–504) is tried again. A wrong PIN is never retried.
const noAnswer = (count: number, e: Error) => count < 3 && (!(e instanceof HttpError) || e.status >= 502);

export function LoginPage({ onLoggedIn }: { onLoggedIn: (me: Me) => void }) {
  // Asking for the shelters also wakes the backend while the volunteer types.
  useQuery({ queryKey: ["wake-up"], queryFn: wakeUp, staleTime: Infinity });
  const shelters = useQuery({ queryKey: ["shelters"], queryFn: getShelters, staleTime: Infinity });
  const [shelterId, setShelterId] = useState("");
  const [name, setName] = useState("");
  const [pin, setPin] = useState("");
  const [problem, setProblem] = useState<string | null>(null);

  const send = useMutation({
    mutationFn: login,
    retry: noAnswer,
    retryDelay: 5_000,
    onSuccess: onLoggedIn,
    onError: (e) => setProblem(e instanceof HttpError && e.status < 500 ? e.message : t.problem.noConnection),
    onSettled: () => setPin(""),   // never leave a PIN sitting in the box
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!shelterId || !name.trim() || !pin.trim()) return setProblem(t.login.missing);
    setProblem(null);
    send.mutate({ shelterId, name: name.trim(), pin: pin.trim() });
  };

  return (
    <main>
      <ConnectionBanner connecting={shelters.isPending || send.isPending} unsent={0} canKeepDraft />
      <h1>{t.appName}</h1>
      <h2>{t.login.title}</h2>
      <form onSubmit={submit} className="stack">
        <label>
          {t.login.shelter}
          <select value={shelterId} onChange={(e) => setShelterId(e.target.value)}>
            <option value="">{t.login.choose}</option>
            {shelters.data?.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
        </label>
        <label>
          {t.login.name}
          <input value={name} onChange={(e) => setName(e.target.value)} autoComplete="username" />
        </label>
        <label>
          {t.login.pin}
          <input type="password" inputMode="numeric" autoComplete="current-password" value={pin} onChange={(e) => setPin(e.target.value)} />
        </label>
        {problem && <p role="alert">{problem}</p>}
        {shelters.isError && (
          <p role="alert">
            {t.problem.noConnection} <button type="button" onClick={() => shelters.refetch()}>{t.problem.tryAgain}</button>
          </p>
        )}
        <BigButton type="submit" label={t.login.submit} disabled={send.isPending} />
      </form>
    </main>
  );
}
