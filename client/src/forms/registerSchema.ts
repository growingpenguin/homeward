// forms/registerSchema.ts
// What the Register form accepts. Only the name is required; "I don't know" is always a valid answer.
import { z } from "zod";
import { t } from "../strings";

// The age box: empty means "unknown" (never 0); "80" typed as text is fine; anything else gets a plain message.
const optionalAge = z
  .union([z.string(), z.number(), z.null(), z.undefined()])
  .transform((v, ctx) => {
    if (v === undefined || v === null || (typeof v === "string" && v.trim() === "")) return undefined;
    const n = typeof v === "number" ? v : Number(v.trim());
    if (!Number.isInteger(n)) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: t.age.notNumber });
      return z.NEVER;
    }
    if (n < 0 || n > 120) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: t.age.outOfRange });
      return z.NEVER;
    }
    return n;
  });

// "HW-7K3P" or just "7K3P" (what CodeKeypad gives); always sent as "HW-7K3P", the database's format.
const slipCode = z
  .string()
  .trim()
  .toUpperCase()
  .regex(/^(HW-?)?[2-9A-HJ-NP-Z]{4}$/, t.paper.badSlip)
  .transform((c) => `HW-${c.slice(-4)}`)
  .optional()
  .or(z.literal("").transform(() => undefined));

// A real calendar date in yyyy-mm-dd. (JavaScript would quietly turn "2026-02-30" into March 2,
// so the date is built and read back to make sure nothing rolled over.)
function isCalendarDate(d: string): boolean {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(d);
  if (!m) return false;
  const [y, mo, day] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const date = new Date(Date.UTC(y, mo - 1, day));
  return date.getUTCFullYear() === y && date.getUTCMonth() === mo - 1 && date.getUTCDate() === day;
}

// The date written on a paper form: empty is fine, a real calendar date is fine, anything else gets a message.
const paperDate = z
  .string()
  .trim()
  .refine((d) => d === "" || isCalendarDate(d), t.paper.badDate)
  .transform((d) => (d === "" ? undefined : d))
  .optional();

export const registerFields = z.object({
  preferredName: z.string().trim().min(1, t.name.missing).max(100, t.name.tooLong),
  otherNames: z.array(z.string().trim().min(1)).default([]),
  approxAge: optionalAge,
  isMinor: z.boolean().optional(),                           // undefined = not sure
  unaccompanied: z.boolean().default(false),                 // asked only when they may be a child
  homeArea: z.string().trim().optional(),
  description: z.string().trim().max(500).optional(),     // anything that helps recognise them
  phone: z.string().trim().optional(),
  visibility: z.enum(["findable", "ask", "hidden"]).default("ask"),   // safe default
  // Paper mode is chosen once, before the first form, and stays on for the whole stack.
  fromPaper: z.boolean().default(false),
  paperRef: z.string().trim().optional(),
  paperDate,                                                 // the date written on the paper (yyyy-mm-dd)
  slipCode,
});

// Same rules as the database, checked here first so the volunteer gets a friendly sentence.
export const registerSchema = registerFields
  .refine((f) => !f.fromPaper || !!f.paperRef, { path: ["paperRef"], message: t.paper.sheetLine })
  // The database would store "now" if the date were missing, which isn't when it was written down.
  .refine((f) => !f.fromPaper || !!f.paperDate, { path: ["paperDate"], message: t.paper.dateMissing })
  .refine((f) => !(f.isMinor === false && f.approxAge !== undefined && f.approxAge < 18), {
    path: ["isMinor"],
    message: t.age.childMarkedAdult,
  })
  .refine((f) => !(f.isMinor === true && f.approxAge !== undefined && f.approxAge >= 18), {
    path: ["isMinor"],
    message: t.age.adultMarkedChild,
  });

export type RegisterForm = z.infer<typeof registerSchema>;

// For drafts: whatever was typed so far, unfinished or not. Only the shape is checked here;
// the full rules run when the volunteer presses Save.
// recordId is the id made when the form opened (newRecordId): keeping it in the draft means a
// reload re-sends the SAME id, so the database ignores the repeat instead of making a second person.
export const registerDraftSchema = z
  .object({
    recordId: z.string().uuid(),
    preferredName: z.string(),
    otherNames: z.array(z.string()),
    approxAge: z.union([z.string(), z.number(), z.null()]),
    isMinor: z.boolean(),
    unaccompanied: z.boolean(),
    homeArea: z.string(),
    description: z.string(),
    phone: z.string(),
    visibility: z.enum(["findable", "ask", "hidden"]),
    fromPaper: z.boolean(),
    paperRef: z.string(),
    paperDate: z.string(),
    slipCode: z.string(),
  })
  .partial();

// One question per screen; the volunteer always sees "Step 2 of 5".
type Step = { id: string; title: string; fields: ReadonlyArray<keyof RegisterForm>; optional?: boolean };

export function registerSteps(fromPaper: boolean): Step[] {
  return [
    ...(fromPaper ? [{ id: "paper", title: t.steps.paper, fields: ["paperRef", "paperDate", "slipCode"] } as Step] : []),
    { id: "name", title: t.steps.name, fields: ["preferredName", "otherNames"] },
    { id: "age", title: t.steps.age, fields: ["approxAge", "isMinor", "unaccompanied"] },
    { id: "privacy", title: t.steps.privacy, fields: ["visibility"] },
    { id: "extra", title: t.steps.extra, fields: ["homeArea", "description", "phone"], optional: true },
    { id: "check", title: t.steps.check, fields: [] },
  ];
}
