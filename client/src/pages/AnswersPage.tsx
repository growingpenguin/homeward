// pages/AnswersPage.tsx
// Requests from people looking for someone at this desk (or for the person whose slip was just checked).
// The volunteer reads the message out; the person chooses. Before "Connect", the screen shows exactly
// what will be shared. Children always go to a lead.
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { HttpError } from "../api/http";
import type { WaitingRequest } from "../api/types";
import { BigButton } from "../components/BigButton";
import { SayThis } from "../components/SayThis";
import { t } from "../strings";

type Choice = "CONNECT" | "SAFE" | "NOT_NOW" | "DECLINE";

// onlyPersonId: after a slip check, show only that person's requests (never other people sheltered here).
export function AnswersPage({ onlyPersonId }: { onlyPersonId?: string }) {
  const waiting = useQuery({ queryKey: ["answers"], queryFn: () => api<WaitingRequest[]>("/api/answers") });
  if (waiting.isError) return <p role="alert">{t.problem.noConnection}</p>;
  if (!waiting.data) return null;
  const list = onlyPersonId ? waiting.data.filter((r) => r.personId === onlyPersonId) : waiting.data;
  return (
    <div className="stack">
      <h2>{t.answers.title}</h2>
      {list.length === 0 && <p role="status">{t.answers.none}</p>}
      {list.map((r) => <Answer key={r.requestId} request={r} />)}
    </div>
  );
}

function Answer({ request: r }: { request: WaitingRequest }) {
  const qc = useQueryClient();
  const [connecting, setConnecting] = useState(false);
  const [plan, setPlan] = useState("");
  const searcher = r.searcherName ?? t.answers.unknownSearcher;
  const decide = useMutation({
    mutationFn: (choice: Choice) =>
      api<void>(`/api/requests/${r.requestId}/decision`, {
        method: "POST",
        body: { choice, contactPlan: choice === "CONNECT" ? plan : undefined },
        userAction: true,
      }),
    // A decision is never repeated by accident (no retry); the list is fetched again afterwards.
    onSuccess: () => qc.invalidateQueries({ queryKey: ["answers"] }),
  });
  const problem = decide.error && (decide.error instanceof HttpError && decide.error.status < 500 ? decide.error.message : t.problem.noConnection);

  return (
    <article className="answer-card">
      <h3>{t.answers.lookingFor(searcher, r.personName)}</h3>
      {r.relationship && <p>{t.answers.relationship(r.relationship)}</p>}
      {r.message && <SayThis label={t.answers.readOut}>{r.message}</SayThis>}
      {r.personVisibility === "findable" && <p>{t.answers.findable}</p>}
      {r.childInvolved ? (
        <p role="status"><strong>{t.answers.childNeedsLead}</strong></p>
      ) : connecting ? (
        <div className="stack">
          <label>{t.answers.contactPlan}<input value={plan} onChange={(e) => setPlan(e.target.value)} /></label>
          {plan.trim() !== "" && (
            <>
              <p>{t.answers.willShare}</p>
              <p className="contact-plan">“{plan.trim()}”</p>
              <BigButton label={t.answers.confirmConnect} onClick={() => decide.mutate("CONNECT")} disabled={decide.isPending} />
            </>
          )}
          <button type="button" onClick={() => setConnecting(false)}>{t.register.back}</button>
        </div>
      ) : (
        <BigButton label={t.answers.connect(searcher)} onClick={() => setConnecting(true)} />
      )}
      {!connecting && !r.childInvolved && (
        <div className="stack">
          <BigButton label={t.answers.safe(searcher)} onClick={() => decide.mutate("SAFE")} disabled={decide.isPending} />
          <button type="button" onClick={() => decide.mutate("NOT_NOW")} disabled={decide.isPending}>{t.answers.notNow}</button>
          <button type="button" onClick={() => decide.mutate("DECLINE")} disabled={decide.isPending}>{t.answers.no}</button>
        </div>
      )}
      {problem && <p role="alert" className="problem">{problem}</p>}
    </article>
  );
}
