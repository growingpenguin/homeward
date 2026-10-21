import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router";
import { render as rtlRender } from "@testing-library/react";
import { afterEach, beforeEach } from "vitest";
import { queryClient } from "../api/queryClient";
import { fakeBackend } from "../test/fakeFetch";
import { HomePage } from "./HomePage";
import { LeadPage } from "./LeadPage";
import { SlipPage } from "./SlipPage";
import { Route, Routes } from "react-router";

const wrap = (ui: React.ReactNode) =>
  render(<QueryClientProvider client={queryClient}><MemoryRouter>{ui}</MemoryRouter></QueryClientProvider>);

beforeEach(() => {
  queryClient.clear();
  document.cookie = "XSRF-TOKEN=test-token";
});
afterEach(() => vi.unstubAllGlobals());

test("only leads see the lead page link", async () => {
  fakeBackend({ "GET /api/answers": { status: 200, body: [] } });
  const { unmount } = wrap(<HomePage />);
  expect(screen.queryByRole("button", { name: /Lead page/ })).not.toBeInTheDocument();
  unmount();
  wrap(<HomePage isLead />);
  expect(screen.getByRole("button", { name: /Lead page/ })).toBeInTheDocument();
});

test("a child is released only after the guardian check, a note and how they'll meet; flags can be lifted", async () => {
  const sent = fakeBackend({
    "GET /api/lead/overview": { status: 200, body: {
      childrenWaiting: [{ requestId: "r1", personId: "c1", personName: "Mia", searcherName: "Tom", relationship: "dad", message: null, childInvolved: true, personVisibility: "ask" }],
      unaccompaniedChildren: [{ personId: "c1", preferredName: "Mia", approxAge: 9 }],
      flags: [{ flagId: "f1", kind: "custody_hold", about: "Leo", note: "court order" }],
    } },
    "POST /api/lead/requests/r1/release-minor": { status: 204 },
    "POST /api/lead/flags/f1/clear": { status: 204 },
  });
  wrap(<LeadPage />);
  const user = userEvent.setup();
  const review = await screen.findByRole("button", { name: "Check what will be shared" });
  expect(review).toBeDisabled();
  await user.click(screen.getByLabelText(/I checked the guardian/));
  await user.type(screen.getByLabelText(/How you checked/), "Mia recognised her dad");
  await user.type(screen.getByLabelText(/How they will meet/), "Dad comes to the Eastside desk at 3 p.m.");
  expect(review).toBeDisabled();   // still needs "the person here agreed"
  await user.click(screen.getByLabelText(/The person at this desk agreed/));
  await user.click(review);
  expect(screen.getByText("“Dad comes to the Eastside desk at 3 p.m.”")).toBeInTheDocument();
  expect(sent.some((s) => s.path.includes("release-minor"))).toBe(false);   // nothing sent before "Yes"
  await user.click(screen.getByRole("button", { name: "Yes, release: connect them" }));
  expect(sent.find((s) => s.path === "/api/lead/requests/r1/release-minor")!.body).toEqual({
    guardianChecked: true, note: "Mia recognised her dad", contactPlan: "Dad comes to the Eastside desk at 3 p.m.",
  });
  expect(screen.getByText(/about 9/)).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Lift this flag" }));
  expect(sent.some((s) => s.path === "/api/lead/flags/f1/clear")).toBe(false);   // asks first
  await user.click(screen.getByRole("button", { name: "Yes, lift it" }));
  expect(sent.some((s) => s.path === "/api/lead/flags/f1/clear")).toBe(true);
});

const person = { id: "p1", preferredName: "Rosa", otherNames: [], approxAge: 80, ageIsEstimate: true, isMinor: null, treatedAsMinor: false,
  unaccompanied: false, homeArea: null, description: null, phone: null, visibility: "ask", source: "app", paperRef: null,
  recordedAt: "2026-10-20T12:00:00Z", hasSlip: true };
const slipAt = (isLead: boolean) =>
  rtlRender(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/persons/p1/slip"]}>
        <Routes><Route path="/persons/:id/slip" element={<SlipPage isLead={isLead} />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );

test("a volunteer never sees the lead actions on a person's page", async () => {
  fakeBackend({ "GET /api/persons/p1": { status: 200, body: person } });
  slipAt(false);
  expect(await screen.findByRole("heading", { name: "Rosa" })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: /Reprint a lost slip/ })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: /Protect this person/ })).not.toBeInTheDocument();
});

test("a lead's reprint says the old slip stops working and asks first; the new PIN is shown once", async () => {
  const sent = fakeBackend({
    "GET /api/persons/p1": { status: 200, body: person },
    "POST /api/lead/persons/p1/reissue-slip": { status: 201, body: { code: "HW-9Q2R", pin: "5173", shelterName: "Eastside Community Center", deskPhone: "555-0102" } },
  });
  slipAt(true);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: /Reprint a lost slip/ }));
  expect(screen.getByRole("alert")).toHaveTextContent("Their old slip and PIN will stop working");
  expect(sent.some((s) => s.path.includes("reissue-slip"))).toBe(false);
  await user.click(screen.getByRole("button", { name: "Yes, make a new slip" }));
  expect(await screen.findByText("5173")).toBeInTheDocument();
  expect(screen.getByText(/The PIN is shown only now/)).toBeInTheDocument();
});
