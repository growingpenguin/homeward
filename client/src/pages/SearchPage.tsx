// pages/SearchPage.tsx
// "Someone is looking for family": who is looking -> who they're looking for -> possible matches.
// The person looking is registered too (a name is enough) and always leaves with a printed slip.
// Match cards never say where anyone is; the volunteer asks, the found person decides.
import { useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import { z } from "zod";
import { api } from "../api/client";
import { HttpError } from "../api/http";
import { worthRetrying } from "../api/queryClient";
import type { MatchCard, PersonView, SearchResult } from "../api/types";
import { BigButton } from "../components/BigButton";
import { SayThis } from "../components/SayThis";
import { newRecordId } from "../forms/toRequest";
import { useDraft } from "../hooks/useDraft";
import { t } from "../strings";

const searchDraftSchema = z
  .object({
    searcherId: z.string().uuid(),
    searchId: z.string().uuid(),
    name: z.string(),
    adult: z.boolean(),
    names: z.string(),
    ageFrom: z.string(),
    ageTo: z.string(),
    relationship: z.string(),
    lastPlace: z.string(),
    registered: z.boolean(),   // the person looking is saved; a later fix is an edit, not a new registration
  })
  .partial();
type Draft = z.infer<typeof searchDraftSchema>;

const age = (v?: string) => (v && v.trim() !== "" && Number.isInteger(Number(v)) ? Number(v) : undefined);
const problemOf = (e: Error) => (e instanceof HttpError && e.status < 500 ? e.message : t.problem.noConnection);

export function SearchPage({ staffId }: { staffId: string }) {
  const navigate = useNavigate();
  const [initial] = useState<Draft>(() => ({ searcherId: newRecordId(), searchId: newRecordId() }));
  const draft = useDraft(staffId, "search", searchDraftSchema, initial);
  const d = draft.value;
  const set = (patch: Partial<Draft>) => draft.setValue({ ...d, ...patch });
  const [step, setStep] = useState<"who" | "whom" | "results">("who");
  const [problem, setProblem] = useState<string | null>(null);
  const [result, setResult] = useState<SearchResult | null>(null);

  // The person looking: registered like anyone else (same id on every retry, "Ask me first").
  // Saved once; a fix made after "Back" is sent as an edit (the same id would otherwise be ignored).
  const registerSearcher = useMutation({
    mutationFn: () =>
      d.registered
        ? api<PersonView>(`/api/persons/${d.searcherId}`, {
            method: "PUT",
            body: { preferredName: d.name?.trim(), isMinor: !d.adult, unaccompanied: false, visibility: "ask", otherNames: [] },
            userAction: true,
          })
        : api<PersonView>("/api/persons", {
            method: "POST",
            body: { id: d.searcherId, preferredName: d.name?.trim(), isMinor: !d.adult },
            userAction: true,
          }),
    retry: worthRetrying,
  });
  const runSearch = useMutation({
    mutationFn: (searchId: string) =>
      api<SearchResult>("/api/searches", {
        method: "POST",
        body: {
          id: searchId,
          searcherPersonId: d.searcherId,
          soughtNames: (d.names ?? "").split(",").map((n) => n.trim()).filter((n) => n !== ""),
          ageMin: age(d.ageFrom),
          ageMax: age(d.ageTo),
          relationship: d.relationship,
          lastKnownPlace: d.lastPlace,
        },
        userAction: true,
      }),
    retry: worthRetrying,
  });

  const next = (e: FormEvent) => {
    e.preventDefault();
    setProblem(null);
    if (step === "who") {
      if (!d.name?.trim()) return setProblem(t.search.missingName);
      if (d.adult === undefined) return setProblem(t.search.missingAdult);
      registerSearcher.mutate(undefined, {
        onSuccess: () => {
          set({ registered: true });
          setStep("whom");
        },
        onError: (err) => setProblem(problemOf(err)),
      });
    } else {
      const search = (searchId: string, firstTry: boolean) =>
        runSearch.mutate(searchId, {
          onSuccess: (r) => {
            setResult(r);
            setStep("results");
          },
          onError: (err) => {
            // Saved earlier with other names (e.g. the reply was lost, then a name was fixed): a new search.
            if (firstTry && err instanceof HttpError && err.status === 409) {
              const fresh = newRecordId();
              set({ searchId: fresh });
              return search(fresh, false);
            }
            setProblem(problemOf(err));
          },
        });
      search(d.searchId!, true); // !: made when the form opened
    }
  };

  // Always ends with the searcher's own slip, match or not.
  const finish = () => {
    const searcherId = d.searcherId;
    draft.clear();
    navigate(`/persons/${searcherId}/slip`, { state: { saved: true } });
  };

  // The same person looks for another relative: a new search, but the SAME person and slip,
  // so one slip shows the answers to all of their searches.
  const another = () => {
    set({ searchId: newRecordId(), names: "", ageFrom: "", ageTo: "", relationship: "", lastPlace: "" });
    setResult(null);
    setProblem(null);
    setStep("whom");
  };

  if (step === "results" && result) {
    return (
      <div className="stack">
        <h2>{t.search.results}</h2>
        <p><strong>{t.search.dontSay}</strong></p>
        <SayThis>{t.search.sayInstead}</SayThis>
        {result.matches.length === 0 && <p role="status">{t.search.none}</p>}
        {result.matches.map((m) => (
          <MatchCardView key={m.personId} searchId={result.searchId} card={m} onSent={setResult} />
        ))}
        <BigButton label={t.search.another} hint={t.search.another_hint} onClick={another} />
        <BigButton label={t.search.done} onClick={finish} />
      </div>
    );
  }

  return (
    <form className="stack" onSubmit={next}>
      {step === "who" ? (
        <>
          <h2>{t.search.who}</h2>
          <label>{t.search.whoName}<input value={d.name ?? ""} onChange={(e) => set({ name: e.target.value })} autoComplete="off" /></label>
          <fieldset className="choice-cards">
            <legend>{t.search.whoAdult}</legend>
            {([[true, t.register.yes], [false, t.register.no]] as const).map(([v, label]) => (
              <label key={label} className={d.adult === v ? "choice-card is-selected" : "choice-card"}>
                <input type="radio" name="adult" checked={d.adult === v} onChange={() => set({ adult: v })} />
                {label}
              </label>
            ))}
            <span>{t.search.whoAdultHint}</span>
          </fieldset>
        </>
      ) : (
        <>
          <h2>{t.search.whom}</h2>
          <label>{t.search.whomNames}<input value={d.names ?? ""} onChange={(e) => set({ names: e.target.value })} autoComplete="off" /></label>
          <label>{t.search.ageFrom}<input inputMode="numeric" value={d.ageFrom ?? ""} onChange={(e) => set({ ageFrom: e.target.value })} /></label>
          <label>{t.search.ageTo}<input inputMode="numeric" value={d.ageTo ?? ""} onChange={(e) => set({ ageTo: e.target.value })} /></label>
          <label>{t.search.relationship}<input value={d.relationship ?? ""} onChange={(e) => set({ relationship: e.target.value })} /></label>
          <label>{t.search.lastPlace}<input value={d.lastPlace ?? ""} onChange={(e) => set({ lastPlace: e.target.value })} /></label>
        </>
      )}
      {problem && <p role="alert" className="problem">{problem}</p>}
      <BigButton
        type="submit"
        label={runSearch.isPending ? t.search.searching : step === "who" ? t.register.next : t.search.find}
        disabled={registerSearcher.isPending || runSearch.isPending}
      />
      {step === "whom" && <button type="button" onClick={() => setStep("who")}>{t.register.back}</button>}
    </form>
  );
}

// One possible match: name, age and why, never where. "Ask this person" sends a contact request.
export function MatchCardView({ searchId, card, onSent }: { searchId: string; card: MatchCard; onSent: (r: SearchResult) => void }) {
  const [open, setOpen] = useState(false);
  const [message, setMessage] = useState("");
  const send = useMutation({
    mutationFn: () =>
      api<unknown>(`/api/searches/${searchId}/requests`, { method: "POST", body: { personId: card.personId, message }, userAction: true }),
    retry: worthRetrying,   // safe to repeat: one request per search and person
    // The request is saved: refresh the cards in the background. If only the refresh fails,
    // the card still shows "Asked" (it never looks like the request failed).
    onSuccess: () => void api<SearchResult>(`/api/searches/${searchId}`).then(onSent).catch(() => {}),
  });
  return (
    <article className="match-card">
      <h3>{card.preferredName}{card.approxAge !== null ? ` (${t.search.about(card.approxAge)})` : ""}</h3>
      <p>{t.search.reasons}</p>
      <ul>{card.reasons.map((r) => <li key={r}>{r}</li>)}</ul>
      {card.requested || send.isSuccess ? (
        <p role="status">{t.search.requested}</p>
      ) : open ? (
        <div className="stack">
          <label>{t.search.message}<textarea rows={3} value={message} onChange={(e) => setMessage(e.target.value)} /></label>
          <p>{t.messageHint}</p>
          {send.isError && <p role="alert" className="problem">{problemOf(send.error)}</p>}
          <BigButton label={t.search.send} onClick={() => send.mutate()} disabled={send.isPending} />
        </div>
      ) : (
        <button type="button" onClick={() => setOpen(true)}>{t.search.choose}</button>
      )}
    </article>
  );
}
