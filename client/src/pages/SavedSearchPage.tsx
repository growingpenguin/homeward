// pages/SavedSearchPage.tsx
// A search made earlier at this desk: its possible matches, and "Check again" for people registered since
// (e.g. paper forms typed in tonight). Opened from Check a code, after the person's own slip was checked.
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router";
import { api } from "../api/client";
import { HttpError } from "../api/http";
import type { SearchResult } from "../api/types";
import { BigButton } from "../components/BigButton";
import { SayThis } from "../components/SayThis";
import { t } from "../strings";
import { MatchCardView } from "./SearchPage";

export function SavedSearchPage() {
  const { id } = useParams();
  const qc = useQueryClient();
  const search = useQuery({ queryKey: ["search", id], queryFn: () => api<SearchResult>(`/api/searches/${id}`), retry: false });
  const check = useMutation({
    mutationFn: () => api<SearchResult>(`/api/searches/${id}/check`, { method: "POST", userAction: true }),
    onSuccess: (r) => qc.setQueryData(["search", id], r),
  });
  const update = (r: SearchResult) => qc.setQueryData(["search", id], r);

  if (search.isError) {
    const otherDesk = search.error instanceof HttpError && search.error.status === 404;
    return <p role="alert">{otherDesk ? t.search.otherDesk : t.problem.noConnection}</p>;
  }
  if (!search.data) return null;
  const s = search.data;
  return (
    <div className="stack">
      <h2>{t.search.open(s.soughtNames.join(", "))}</h2>
      <p><strong>{t.search.dontSay}</strong></p>
      <SayThis>{t.search.sayInstead}</SayThis>
      <BigButton label={check.isPending ? t.search.searching : t.search.checkAgain_btn} onClick={() => check.mutate()} disabled={check.isPending} />
      {s.matches.length === 0 && <p role="status">{t.search.none}</p>}
      {s.matches.map((m) => <MatchCardView key={m.personId} searchId={s.searchId} card={m} onSent={update} />)}
      <nav className="stack"><Link to="/">{t.slip.home}</Link></nav>
    </div>
  );
}
