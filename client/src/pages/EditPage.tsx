// pages/EditPage.tsx
// Fixing a typo after saving: the same questions on one page. The privacy choice is always sent,
// so an edit can never quietly reset it (the backend refuses an edit without it).
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useLocation, useNavigate, useParams } from "react-router";
import { api } from "../api/client";
import { HttpError } from "../api/http";
import type { EditPersonRequest, PersonView } from "../api/types";
import { BigButton } from "../components/BigButton";
import { PersonFields } from "../forms/PersonFields";
import { personResolver, unaccompaniedToSend, type RegisterForm, type RegisterInput } from "../forms/registerSchema";
import { t } from "../strings";

export function EditPage() {
  const { id } = useParams();
  const person = useQuery({ queryKey: ["person", id], queryFn: () => api<PersonView>(`/api/persons/${id}`) });
  if (!person.data) return person.isError ? <p role="alert">{person.error.message}</p> : null;
  return <EditForm person={person.data} />;
}

function EditForm({ person }: { person: PersonView }) {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [problem, setProblem] = useState<string | null>(null);
  const notice = (useLocation().state as { notice?: string } | null)?.notice;
  const form = useForm<RegisterInput, unknown, RegisterForm>({
    resolver: personResolver,
    defaultValues: {
      preferredName: person.preferredName,
      otherNames: person.otherNames,
      approxAge: person.approxAge,
      isMinor: person.isMinor,
      unaccompanied: person.unaccompanied,
      homeArea: person.homeArea ?? "",
      description: person.description ?? "",
      phone: person.phone ?? "",
      visibility: person.visibility as RegisterInput["visibility"],
    },
  });
  const save = useMutation({
    mutationFn: (body: EditPersonRequest) => api<PersonView>(`/api/persons/${person.id}`, { method: "PUT", body, userAction: true }),
    onSuccess: (p) => {
      qc.setQueryData(["person", p.id], p);
      navigate(`/persons/${p.id}/slip`);
    },
    onError: (e) => setProblem(e instanceof HttpError && e.status < 500 ? e.message : t.problem.noConnection),
  });

  const submit = form.handleSubmit((f) =>
    save.mutate({
      preferredName: f.preferredName,
      otherNames: f.otherNames,
      approxAge: f.approxAge ?? null,
      ageIsEstimate: person.ageIsEstimate,
      isMinor: f.isMinor ?? null,
      unaccompanied: unaccompaniedToSend(f),
      homeArea: f.homeArea ?? null,
      description: f.description ?? null,
      phone: f.phone ?? null,
      visibility: f.visibility,
    }),
  );

  return (
    <form className="stack" onSubmit={(e) => { e.preventDefault(); void submit(); }}>
      <h2>{t.edit.title(person.preferredName)}</h2>
      {notice && <p role="status" className="problem">{notice}</p>}
      {["name", "age", "privacy", "extra"].map((s) => <PersonFields key={s} step={s} form={form} />)}
      {problem && <p role="alert" className="problem">{problem}</p>}
      <BigButton type="submit" label={t.edit.save} disabled={save.isPending} />
    </form>
  );
}
