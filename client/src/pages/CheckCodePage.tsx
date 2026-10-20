// pages/CheckCodePage.tsx
// For people with no phone: the code and PIN from their slip, at ANY desk. Then the screen is turned
// around and they see the answer to each of their searches, asked fresh from the backend each time.
import { useEffect, useRef, useState, type FormEvent } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import { HttpError } from "../api/http";
import { freshOnly, searcherStatusQuery } from "../api/queryClient";
import type { LookupOut, SearchAnswer, SearcherStatus } from "../api/types";
import { BigButton } from "../components/BigButton";
import { CodeKeypad } from "../components/CodeKeypad";
import { SearcherView } from "../components/SearcherView";
import { AnswersPage } from "./AnswersPage";
import { t } from "../strings";

const fetchStatus = (searchId: string) => api<SearcherStatus>(`/api/searches/${searchId}/status`);
// The server forgets the checked slip (so the next person at this desk can't see or answer for them).
const endCheck = () => void api<void>("/api/lookup/end", { method: "POST", userAction: true }).catch(() => {});

export function CheckCodePage() {
  const [code, setCode] = useState("");
  const [answering, setAnswering] = useState(false);
  const checked = useRef(false);   // a slip is checked on the server right now
  const [pin, setPin] = useState("");
  const [showing, setShowing] = useState<number | null>(null);   // which search the searcher is looking at
  const lookup = useMutation({
    mutationFn: () => api<LookupOut>("/api/lookup", { method: "POST", body: { code, pin }, userAction: true }),
    gcTime: 0,   // nothing about this person stays in memory after the screen closes
    onSuccess: (r) => {
      checked.current = r.found;
    },
  });
  // Leaving this screen in any way (Home, lock, logout) ends the check on the server too.
  useEffect(() => () => {
    if (checked.current) endCheck();
  }, []);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    lookup.mutate();
    setPin("");   // never leave a PIN sitting in the box
  };

  const found = lookup.data?.found ? lookup.data : null;
  // Done with this person: their slip stops opening anything at this desk, and the screen starts fresh.
  const finish = () => {
    endCheck();
    checked.current = false;
    lookup.reset();
    setAnswering(false);
    setShowing(null);
    setCode("");
  };

  if (found && showing !== null) {
    const search = found.searches[showing];
    const last = showing + 1 >= found.searches.length;
    return <FreshAnswer key={search.searchId} search={search} onDone={() => (last ? finish() : setShowing(showing + 1))} />;
  }

  if (found && answering) {
    return (
      <div className="stack">
        <AnswersPage onlyPersonId={found.personId ?? undefined} />
        <button type="button" onClick={finish}>{t.check.finish}</button>
      </div>
    );
  }

  if (found) {
    return (
      <div className="stack">
        <h2>{t.check.hello(found.personName ?? "")}</h2>
        {found.searches.length === 0 ? (
          <p>{t.check.noSearches}</p>
        ) : (
          <BigButton label={t.check.showSearcher} hint={t.check.searches(found.searches.length)} onClick={() => setShowing(0)} />
        )}
        {found.answersWaiting > 0 && (
          <BigButton label={t.check.answerNow} hint={t.check.waiting(found.answersWaiting)} onClick={() => setAnswering(true)} />
        )}
        <button type="button" onClick={finish}>{t.check.finish}</button>
      </div>
    );
  }

  const problem =
    lookup.data && !lookup.data.found ? lookup.data.message
      : lookup.error ? (lookup.error instanceof HttpError && lookup.error.status < 500 ? lookup.error.message : t.problem.noConnection)
      : null;
  return (
    <form className="stack" onSubmit={submit}>
      <h2>{t.check.title}</h2>
      <CodeKeypad value={code} onChange={setCode} />
      <label>{t.check.pin}<input type="password" inputMode="numeric" autoComplete="off" value={pin} onChange={(e) => setPin(e.target.value)} /></label>
      {problem && <p role="alert" className="problem">{problem}</p>}
      <BigButton type="submit" label={t.check.submit} disabled={lookup.isPending || code.length < 4 || pin.trim() === ""} />
    </form>
  );
}

// One search's answer, turned towards the searcher: always fetched fresh, so a flag added a minute ago wins.
function FreshAnswer({ search, onDone }: { search: SearchAnswer; onDone: () => void }) {
  const q = useQuery(searcherStatusQuery(search.searchId, fetchStatus));
  return <SearcherView status={freshOnly(q)} failed={q.isError} onRetry={() => void q.refetch()} onDone={onDone} />;
}
