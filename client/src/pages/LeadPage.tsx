// pages/LeadPage.tsx
// Only for leads: children waiting for a sign-off, children without an adult, and safety flags.
// A child is never released by a name match alone: guardian check, a note and how they'll meet.
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router";
import { api } from "../api/client";
import { HttpError } from "../api/http";
import type { LeadOverview, WaitingRequest } from "../api/types";
import { BigButton } from "../components/BigButton";
import { SayThis } from "../components/SayThis";
import { t } from "../strings";

const problemOf = (e: Error) => (e instanceof HttpError && e.status < 500 ? e.message : t.problem.noConnection);

export function LeadPage() {
  const qc = useQueryClient();
  const overview = useQuery({ queryKey: ["lead"], queryFn: () => api<LeadOverview>("/api/lead/overview") });
  const lift = useMutation({
    mutationFn: (flagId: string) => api<void>(`/api/lead/flags/${flagId}/clear`, { method: "POST", userAction: true }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["lead"] }),
  });
  if (overview.isError) return <p role="alert">{problemOf(overview.error)}</p>;
  if (!overview.data) return null;
  const o = overview.data;
  return (
    <div className="stack">
      <h2>{t.lead.title}</h2>
      <section className="stack">
        <h3>{t.lead.children}</h3>
        {o.childrenWaiting.length === 0 && <p>{t.lead.noChildren}</p>}
        {o.childrenWaiting.map((r) => <Release key={r.requestId} request={r} />)}
      </section>
      <section>
        <h3>{t.lead.unaccompanied}</h3>
        {o.unaccompaniedChildren.length === 0 ? <p>{t.lead.noUnaccompanied}</p> : (
          <ul>{o.unaccompaniedChildren.map((c) => (
            <li key={c.personId}><Link to={`/persons/${c.personId}/slip`}>{c.preferredName}</Link> ({t.lead.age(c.approxAge)})</li>
          ))}</ul>
        )}
      </section>
      <section>
        <h3>{t.lead.flags}</h3>
        {o.flags.length === 0 ? <p>{t.lead.noFlags}</p> : (
          <ul>{o.flags.map((f) => (
            <li key={f.flagId}>
              <strong>{t.lead.kinds[f.kind] ?? f.kind}</strong>: {f.about}{f.note ? ` (${f.note})` : ""}{" "}
              <button type="button" onClick={() => lift.mutate(f.flagId)} disabled={lift.isPending}>{t.lead.lift}</button>
            </li>
          ))}</ul>
        )}
        {lift.isError && <p role="alert" className="problem">{problemOf(lift.error)}</p>}
      </section>
    </div>
  );
}

function Release({ request: r }: { request: WaitingRequest }) {
  const qc = useQueryClient();
  const [guardian, setGuardian] = useState(false);
  const [note, setNote] = useState("");
  const [plan, setPlan] = useState("");
  const release = useMutation({
    mutationFn: () =>
      api<void>(`/api/lead/requests/${r.requestId}/release-minor`, {
        method: "POST",
        body: { guardianChecked: guardian, note, contactPlan: plan },
        userAction: true,
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["lead"] }),
  });
  return (
    <article className="answer-card stack">
      <h4>{t.answers.lookingFor(r.searcherName ?? t.answers.unknownSearcher, r.personName)}</h4>
      {r.relationship && <p>{t.answers.relationship(r.relationship)}</p>}
      {r.message && <SayThis>{r.message}</SayThis>}
      <label className="checkbox"><input type="checkbox" checked={guardian} onChange={(e) => setGuardian(e.target.checked)} /> {t.lead.guardian}</label>
      <label>{t.lead.note}<textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} /></label>
      <label>{t.lead.plan}<input value={plan} onChange={(e) => setPlan(e.target.value)} /></label>
      {release.isError && <p role="alert" className="problem">{problemOf(release.error)}</p>}
      <BigButton label={t.lead.release} onClick={() => release.mutate()} disabled={release.isPending || !guardian || !note.trim() || !plan.trim()} />
    </article>
  );
}
