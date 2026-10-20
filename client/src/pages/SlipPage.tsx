// pages/SlipPage.tsx
// After saving: print the slip with the code and PIN. The PIN is shown once and kept nowhere else.
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router";
import { api } from "../api/client";
import { HttpError } from "../api/http";
import type { PersonView, SlipResponse } from "../api/types";
import { BigButton } from "../components/BigButton";
import { t } from "../strings";

export function SlipPage({ isLead = false }: { isLead?: boolean }) {
  const { id } = useParams();
  const qc = useQueryClient();
  const person = useQuery({ queryKey: ["person", id], queryFn: () => api<PersonView>(`/api/persons/${id}`) });
  const slip = useMutation({
    mutationFn: () => api<SlipResponse>(`/api/persons/${id}/slip`, { method: "POST", userAction: true }),
    gcTime: 0,   // the PIN is dropped from memory as soon as this screen closes
    // Coming back to this screen must show "already has a slip", not the Print button again.
    onSuccess: () => qc.setQueryData<PersonView>(["person", id], (p) => p && { ...p, hasSlip: true }),
  });
  // Lead only: a lost slip is voided and a new one printed (the new PIN is also shown only once).
  const reprint = useMutation({
    mutationFn: () => api<SlipResponse>(`/api/lead/persons/${id}/reissue-slip`, { method: "POST", userAction: true }),
    gcTime: 0,
  });
  const protect = useMutation({
    mutationFn: () => api<unknown>("/api/lead/flags", { method: "POST", body: { personId: id, kind: "do_not_disclose" }, userAction: true }),
  });
  const printed = slip.data ?? reprint.data;

  if (!person.data) return person.isError ? <p role="alert">{person.error.message}</p> : null;
  const failed = slip.error ?? reprint.error ?? protect.error;
  const problem = failed && (failed instanceof HttpError && failed.status < 500 ? failed.message : t.problem.noConnection);

  return (
    <div className="stack">
      <h2 className="no-print">{t.slip.saved(person.data.preferredName)}</h2>
      {printed ? (
        <>
          <div className="slip">
            <p><strong>{printed.shelterName}</strong></p>
            <p>{t.slip.code}: <span className="slip-code">{printed.code}</span></p>
            {printed.pin && <p>{t.slip.pin}: <span className="slip-code">{printed.pin}</span></p>}
            {printed.deskPhone && <p>{t.slip.desk}: {printed.deskPhone}</p>}
            <p>{t.slip.keep}</p>
          </div>
          <p className="no-print" role="status">{t.slip.pinOnce}</p>
          <BigButton className="no-print" label={t.slip.printNow} onClick={() => window.print()} />
        </>
      ) : person.data.hasSlip ? (
        <p role="status">{t.slip.alreadyHas}</p>
      ) : (
        <BigButton label={t.slip.print} onClick={() => slip.mutate()} disabled={slip.isPending} />
      )}
      {problem && <p role="alert" className="problem">{problem}</p>}
      {isLead && !printed && (
        <div className="stack no-print">
          {person.data.hasSlip && <button type="button" onClick={() => reprint.mutate()} disabled={reprint.isPending}>{t.lead.reprint}</button>}
          {protect.isSuccess ? <p role="status">{t.lead.protected}</p> : (
            <button type="button" onClick={() => protect.mutate()} disabled={protect.isPending}>{t.lead.protect}</button>
          )}
        </div>
      )}
      <nav className="stack no-print">
        <Link to={`/persons/${id}/edit`}>{t.slip.edit}</Link>
        <Link to="/register">{t.slip.next}</Link>
        <Link to="/">{t.slip.home}</Link>
      </nav>
    </div>
  );
}
