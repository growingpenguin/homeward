// forms/PersonFields.tsx
// The questions of one step, shared by Register (one step per screen) and Edit (all on one page).
import { Controller, type UseFormReturn } from "react-hook-form";
import { SayThis } from "../components/SayThis";
import { VisibilityChoice } from "../components/VisibilityChoice";
import { t } from "../strings";
import type { RegisterForm, RegisterInput } from "./registerSchema";

export type PersonForm = UseFormReturn<RegisterInput, unknown, RegisterForm>;

function Problem({ message }: { message?: string }) {
  return message ? <p role="alert" className="problem">{message}</p> : null;
}

// "Ana, Annie" in the box, ["Ana", "Annie"] in the form; empty pieces are dropped.
const toNames = (v: unknown) =>
  typeof v === "string" ? v.split(",").map((s) => s.trim()).filter((s) => s !== "") : v;

export function PersonFields({ step, form }: { step: string; form: PersonForm }) {
  const { register, control, watch, formState: { errors } } = form;
  const msg = (f: keyof RegisterInput) => errors[f]?.message as string | undefined;

  switch (step) {
    case "paper":
      return (
        <div className="stack">
          <label>{t.register.paperRef}<input {...register("paperRef")} /></label>
          <Problem message={msg("paperRef")} />
          <label>{t.register.paperDate}<input type="date" {...register("paperDate")} /></label>
          <Problem message={msg("paperDate")} />
          <label>{t.paper.slipCode}<input {...register("slipCode")} autoComplete="off" /></label>
          <Problem message={msg("slipCode")} />
        </div>
      );
    case "name":
      return (
        <div className="stack">
          <SayThis>{t.name.hint}</SayThis>
          <label>{t.steps.name}<input {...register("preferredName")} autoComplete="off" /></label>
          <Problem message={msg("preferredName")} />
          <label>{t.register.otherNames}<input {...register("otherNames", { setValueAs: toNames })} autoComplete="off" /></label>
          <Problem message={errors.otherNames?.message ?? errors.otherNames?.find?.((e) => e)?.message} />
        </div>
      );
    case "age": {
      const age = Number(watch("approxAge"));
      const ageKnown = `${watch("approxAge") ?? ""}`.trim() !== "" && Number.isFinite(age);
      // Asked only when they may be a child: an adult with them changes how a child is protected.
      const mayBeChild = watch("isMinor") === true || (watch("isMinor") === undefined && (!ageKnown || age < 18));
      return (
        <div className="stack">
          <label>{t.register.age}<input inputMode="numeric" {...register("approxAge")} autoComplete="off" /></label>
          <Problem message={msg("approxAge")} />
          <Controller
            control={control}
            name="isMinor"
            render={({ field }) => (
              <fieldset className="choice-cards">
                <legend>{t.register.minor.question}</legend>
                {([[true, t.register.minor.yes], [false, t.register.minor.no], [undefined, t.register.minor.unsure]] as const).map(([v, label]) => (
                  <label key={label} className={field.value === v ? "choice-card is-selected" : "choice-card"}>
                    <input type="radio" name="isMinor" checked={field.value === v} onChange={() => field.onChange(v)} />
                    {label}
                  </label>
                ))}
              </fieldset>
            )}
          />
          <Problem message={msg("isMinor")} />
          {mayBeChild && (
            <Controller
              control={control}
              name="unaccompanied"
              render={({ field }) => (
                <fieldset className="choice-cards">
                  <legend>{t.age.adultWithThem}</legend>
                  {([[false, t.register.yes], [true, t.register.no]] as const).map(([v, label]) => (
                    <label key={label} className={field.value === v ? "choice-card is-selected" : "choice-card"}>
                      <input type="radio" name="unaccompanied" checked={field.value === v} onChange={() => field.onChange(v)} />
                      {label}
                    </label>
                  ))}
                </fieldset>
              )}
            />
          )}
        </div>
      );
    }
    case "privacy":
      return (
        <div className="stack">
          <SayThis>{t.steps.privacy}</SayThis>
          <Controller
            control={control}
            name="visibility"
            render={({ field }) => <VisibilityChoice value={field.value ?? "ask"} onChange={field.onChange} />}
          />
        </div>
      );
    case "extra":
      return (
        <div className="stack">
          <label>{t.register.homeArea}<input {...register("homeArea")} autoComplete="off" /></label>
          <Problem message={msg("homeArea")} />
          <label>{t.recognise}<textarea {...register("description")} rows={3} /></label>
          <Problem message={msg("description")} />
          <label>{t.register.phone}<input type="tel" {...register("phone")} autoComplete="off" /></label>
          <Problem message={msg("phone")} />
        </div>
      );
    default:
      return null;
  }
}

// The "Check and save" step: everything at a glance, in plain words.
export function Summary({ values }: { values: RegisterInput }) {
  const none = t.register.notGiven;
  const text = (v: unknown) => (v === undefined || v === null || `${v}`.trim() === "" ? none : `${v}`);
  const minor = values.isMinor === true ? t.register.minor.yes : values.isMinor === false ? t.register.minor.no : t.register.minor.unsure;
  return (
    <dl className="summary">
      <dt>{t.steps.name}</dt><dd>{text(values.preferredName)}</dd>
      <dt>{t.register.otherNames}</dt><dd>{values.otherNames?.length ? values.otherNames.join(", ") : none}</dd>
      <dt>{t.register.age}</dt><dd>{text(values.approxAge)} ({minor})</dd>
      <dt>{t.steps.privacy}</dt><dd>{t.visibility[values.visibility ?? "ask"].label}</dd>
      <dt>{t.register.homeArea}</dt><dd>{text(values.homeArea)}</dd>
      <dt>{t.recognise}</dt><dd>{text(values.description)}</dd>
      <dt>{t.register.phone}</dt><dd>{text(values.phone)}</dd>
    </dl>
  );
}
