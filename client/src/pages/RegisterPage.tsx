// pages/RegisterPage.tsx
// "Someone is here": one question per screen, a draft kept on every change, and the same id on every retry.
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useMutation, useMutationState } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import type { z } from "zod";
import { api } from "../api/client";
import { HttpError } from "../api/http";
import { worthRetrying } from "../api/queryClient";
import type { PersonView, RegisterPersonRequest } from "../api/types";
import { BigButton } from "../components/BigButton";
import { ConnectionBanner } from "../components/ConnectionBanner";
import { PersonFields, Summary } from "../forms/PersonFields";
import { personResolver, registerDraftSchema, registerSteps, type RegisterForm, type RegisterInput } from "../forms/registerSchema";
import { newRecordId, toRegisterRequest } from "../forms/toRequest";
import { useDraft } from "../hooks/useDraft";
import { t } from "../strings";

type Draft = z.infer<typeof registerDraftSchema>;

// A fresh form; in paper mode the next form keeps paper mode and the date (forms come in stacks).
const blank = (keep: Partial<Draft> = {}): Draft => ({ recordId: newRecordId(), visibility: "ask", otherNames: [], ...keep });

// The backend answers a re-sent id with the person as FIRST stored. If that differs from what was
// sent (the volunteer changed something after a lost reply), the changes weren't saved.
const sameAsSent = (p: PersonView, r: RegisterPersonRequest) =>
  p.preferredName === r.preferredName?.trim() && p.visibility === r.visibility &&
  p.unaccompanied === r.unaccompanied && p.approxAge === (r.approxAge ?? null) && p.isMinor === (r.isMinor ?? null);

export function RegisterPage({ staffId }: { staffId: string }) {
  const navigate = useNavigate();
  const [initial] = useState(() => blank());
  const draft = useDraft(staffId, "register", registerDraftSchema, initial);
  const recordId = draft.value.recordId ?? initial.recordId!;   // !: blank() always sets it
  const form = useForm<RegisterInput, unknown, RegisterForm>({
    resolver: personResolver,
    defaultValues: draft.value as RegisterInput,
  });
  const fromPaper = form.watch("fromPaper");
  const [chosenMode, setChosenMode] = useState(draft.value.fromPaper !== undefined);
  const [index, setIndex] = useState(0);
  const [problem, setProblem] = useState<string | null>(null);
  const steps = registerSteps(fromPaper === true);
  const step = steps[Math.min(index, steps.length - 1)];

  // Every change goes into the draft, with the id made when the form opened.
  useEffect(() => {
    const sub = form.watch((v) => draft.setValue({ ...(v as Draft), recordId }));
    return () => sub.unsubscribe();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- draft.setValue is re-made each render; the key doesn't change
  }, [form, recordId]);

  const unsent = useMutationState({ filters: { status: "pending", predicate: (m) => m.state.isPaused } }).length;
  const save = useMutation({
    mutationFn: (r: RegisterPersonRequest) => api<PersonView>("/api/persons", { method: "POST", body: r, userAction: true }),
    retry: worthRetrying,   // safe to repeat: the same id is stored once
  });

  const choose = (paper: boolean) => {
    form.setValue("fromPaper", paper);
    setChosenMode(true);
    setIndex(0);
  };

  const next = async () => {
    const fields = step.fields as (keyof RegisterInput)[];
    // The paper rules sit on the whole form, so they're checked here too, before leaving the paper step.
    if (step.id === "paper") {
      const v = form.getValues();
      if (!`${v.paperRef ?? ""}`.trim()) form.setError("paperRef", { message: t.paper.sheetLine });
      if (!`${v.paperDate ?? ""}`.trim()) form.setError("paperDate", { message: t.paper.dateMissing });
      if (!`${v.paperRef ?? ""}`.trim() || !`${v.paperDate ?? ""}`.trim()) return;
    }
    if (fields.length === 0 || (await form.trigger(fields))) setIndex(index + 1);
  };

  const submit = form.handleSubmit(
    (data) => {
      setProblem(null);
      // Callbacks here, not on the hook: they only run while this page is open, so a save that
      // finishes later (after being offline) can't suddenly move the volunteer to another screen.
      save.mutate(toRegisterRequest(recordId, data), {
        onSuccess: (p, r) => {
          if (r.source === "paper") draft.reset(blank({ fromPaper: true, paperDate: form.getValues("paperDate") }));
          else draft.clear();
          if (sameAsSent(p, r)) navigate(`/persons/${p.id}/slip`);
          else navigate(`/persons/${p.id}/edit`, { state: { notice: t.register.alreadySaved } });
        },
        onError: (e) => setProblem(e instanceof HttpError && e.status < 500 ? e.message : t.problem.noConnection),
      });
    },
    // Something on an earlier step is wrong: go back to that step, where the message is shown.
    (errors) => {
      const at = steps.findIndex((s) => s.fields.some((f) => f in errors));
      if (at >= 0) setIndex(at);
    },
  );

  if (!chosenMode) {
    return (
      <div className="stack">
        <h2>{t.register.paperQuestion}</h2>
        <BigButton label={t.register.personHere} onClick={() => choose(false)} />
        <BigButton label={t.register.copyingPaper} onClick={() => choose(true)} />
      </div>
    );
  }

  return (
    <form className="stack" onSubmit={(e) => { e.preventDefault(); if (step.id === "check") void submit(); else void next(); }}>
      <ConnectionBanner connecting={save.isPending && unsent === 0} unsent={unsent} canKeepDraft={draft.canKeepDraft} />
      <p>{t.steps.progress(index + 1, steps.length)}</p>
      <h2>{step.title}</h2>
      {step.id === "check" ? <Summary values={form.watch()} /> : <PersonFields step={step.id} form={form} />}
      {problem && <p role="alert" className="problem">{problem}</p>}
      {step.id === "check"
        ? <BigButton type="submit" label={save.isPending ? t.register.saving : t.register.save} disabled={save.isPending} />
        : <BigButton type="submit" label={t.register.next} />}
      <button type="button" onClick={() => (index === 0 ? setChosenMode(false) : setIndex(index - 1))} disabled={save.isPending}>
        {t.register.back}
      </button>
    </form>
  );
}
