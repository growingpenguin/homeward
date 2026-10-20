// pages/SlipPage.tsx
// After saving: print the slip with the code and PIN. The PIN is shown once and kept nowhere else.
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router";
import { api } from "../api/client";
import { HttpError } from "../api/http";
import type { PersonView, SlipResponse } from "../api/types";
import { BigButton } from "../components/BigButton";
import { t } from "../strings";

export function SlipPage() {
  const { id } = useParams();
  const person = useQuery({ queryKey: ["person", id], queryFn: () => api<PersonView>(`/api/persons/${id}`) });
  const slip = useMutation({
    mutationFn: () => api<SlipResponse>(`/api/persons/${id}/slip`, { method: "POST", userAction: true }),
    gcTime: 0,   // the PIN is dropped from memory as soon as this screen closes
  });

  if (!person.data) return person.isError ? <p role="alert">{person.error.message}</p> : null;
  const problem = slip.error && (slip.error instanceof HttpError && slip.error.status < 500 ? slip.error.message : t.problem.noConnection);

  return (
    <div className="stack">
      <h2 className="no-print">{t.slip.saved(person.data.preferredName)}</h2>
      {slip.data ? (
        <>
          <div className="slip">
            <p><strong>{slip.data.shelterName}</strong></p>
            <p>{t.slip.code}: <span className="slip-code">{slip.data.code}</span></p>
            {slip.data.pin && <p>{t.slip.pin}: <span className="slip-code">{slip.data.pin}</span></p>}
            {slip.data.deskPhone && <p>{t.slip.desk}: {slip.data.deskPhone}</p>}
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
      <nav className="stack no-print">
        <Link to={`/persons/${id}/edit`}>{t.slip.edit}</Link>
        <Link to="/register">{t.slip.next}</Link>
        <Link to="/">{t.slip.home}</Link>
      </nav>
    </div>
  );
}
