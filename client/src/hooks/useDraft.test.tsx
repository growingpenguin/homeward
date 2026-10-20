import { act, renderHook } from "@testing-library/react";
import { beforeEach } from "vitest";
import { z } from "zod";
import { clearAllDrafts, useDraft } from "./useDraft";

const schema = z.object({ name: z.string() });
const initial = { name: "" };
const KEY = (staff: string, form = "register") => `homeward-draft:${staff}:${form}`;
const draftFor = (staff: string, form = "register") => renderHook(() => useDraft(staff, form, schema, initial));

beforeEach(() => sessionStorage.clear());

test("another volunteer never sees a draft, and the same volunteer gets their own back", () => {
  const ana = draftFor("staff-ana");
  act(() => ana.result.current.setValue({ name: "Rosa Test" }));
  expect(JSON.parse(sessionStorage.getItem(KEY("staff-ana"))!).data).toEqual({ name: "Rosa Test" });

  expect(draftFor("staff-ben").result.current.value).toEqual(initial);        // someone else on the same tablet
  expect(draftFor("staff-ana").result.current.value).toEqual({ name: "Rosa Test" });
  expect(draftFor("staff-ana", "search").result.current.value).toEqual(initial);   // another form
});

test("switching volunteer in the same screen loads that volunteer's draft, never copies the old one", () => {
  const first = draftFor("staff-ana");
  act(() => first.result.current.setValue({ name: "Rosa Test" }));
  const { result, rerender } = renderHook(({ staff }) => useDraft(staff, "register", schema, initial), { initialProps: { staff: "staff-ana" } });
  expect(result.current.value).toEqual({ name: "Rosa Test" });
  rerender({ staff: "staff-ben" });
  expect(result.current.value).toEqual(initial);
  expect(sessionStorage.getItem(KEY("staff-ben"))).toBeNull();
});

test("clearAllDrafts removes every draft of every volunteer and nothing else", () => {
  const [a, b, c] = [draftFor("staff-ana"), draftFor("staff-ben"), draftFor("staff-ben", "search")];
  act(() => a.result.current.setValue({ name: "A" }));
  act(() => b.result.current.setValue({ name: "B" }));
  act(() => c.result.current.setValue({ name: "C" }));
  expect(Object.keys(sessionStorage)).toHaveLength(3);
  sessionStorage.setItem("something-else", "keep");
  clearAllDrafts();
  expect(Object.keys(sessionStorage)).toEqual(["something-else"]);
});

test("a corrupt, expired or wrong-shaped draft is discarded", () => {
  const key = KEY("staff-ana");
  sessionStorage.setItem(key, "{not json");
  expect(draftFor("staff-ana").result.current.value).toEqual(initial);

  sessionStorage.setItem(key, JSON.stringify({ savedAt: Date.now() - 13 * 60 * 60 * 1000, data: { name: "Old" } }));
  expect(draftFor("staff-ana").result.current.value).toEqual(initial);
  expect(sessionStorage.getItem(key)).toBeNull();                                // an expired draft is also deleted

  sessionStorage.setItem(key, JSON.stringify({ data: { name: "No time" } }));     // no savedAt
  expect(draftFor("staff-ana").result.current.value).toEqual(initial);

  sessionStorage.setItem(key, JSON.stringify({ savedAt: Date.now(), data: { name: 42 } }));   // no longer fits the form
  expect(draftFor("staff-ana").result.current.value).toEqual(initial);

  sessionStorage.setItem(key, JSON.stringify({ savedAt: Date.now() - 60_000, data: { name: "Fresh" } }));
  expect(draftFor("staff-ana").result.current.value).toEqual({ name: "Fresh" });  // a good one still loads
});
