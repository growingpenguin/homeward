import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, beforeEach } from "vitest";
import { queryClient } from "../api/queryClient";
import { SearcherView } from "../components/SearcherView";
import { fakeBackend } from "../test/fakeFetch";
import { AnswersPage } from "./AnswersPage";
import { CheckCodePage } from "./CheckCodePage";
import { SearchPage } from "./SearchPage";

const STAFF = "00000000-0000-0000-0000-0000000000a1";

function at(path: string) {
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/search" element={<SearchPage staffId={STAFF} />} />
          <Route path="/answers" element={<AnswersPage />} />
          <Route path="/code" element={<CheckCodePage />} />
          <Route path="/persons/:id/slip" element={<p>slip page</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return userEvent.setup();
}

beforeEach(() => {
  queryClient.clear();
  sessionStorage.clear();
  document.cookie = "XSRF-TOKEN=test-token";
});
afterEach(() => vi.unstubAllGlobals());

test("the searcher sees one sentence for every hidden state, and a failed check says so", () => {
  const { rerender } = render(<SearcherView status={{ type: "Active" }} onDone={() => {}} />);
  const active = screen.getByText(/Your request is saved/).textContent;
  rerender(<SearcherView status={undefined} failed onRetry={() => {}} onDone={() => {}} />);
  expect(screen.getByRole("alert")).toHaveTextContent("Couldn't check right now.");
  expect(screen.queryByText(/Checking for the latest answer/)).not.toBeInTheDocument();
  rerender(<SearcherView status={{ type: "Connected", contactPlan: "Call the Eastside desk" }} onDone={() => {}} />);
  expect(screen.getByText("Call the Eastside desk")).toBeInTheDocument();
  expect(active).toBe("Your request is saved. If they choose to connect, we'll let you know here.");
});

test("search: the person looking is registered, cards show reasons (no place), and a request goes to the chosen person", async () => {
  const card = { personId: "p1", preferredName: "Rosa Diaz", approxAge: 80, ageIsEstimate: true, child: false, score: 0.8,
    reasons: ["name sounds like Rosa Diaz", "age fits (about 80)"], requested: false };
  const sent = fakeBackend({
    "POST /api/persons": { status: 201, body: {} },
    "POST /api/searches": () => ({ status: 201, body: { searchId: "s1", searcherPersonId: "x", soughtNames: ["Rosa Dias"], matches: [card] } }),
    "POST /api/searches/s1/requests": { status: 201, body: { requestId: "r1" } },
    "GET /api/searches/s1": { status: 200, body: { searchId: "s1", searcherPersonId: "x", soughtNames: ["Rosa Dias"], matches: [{ ...card, requested: true }] } },
  });
  const user = at("/search");
  await user.type(screen.getByLabelText("Their name or nickname"), "Luis Diaz");
  await user.click(screen.getByRole("button", { name: "Next" }));
  expect(screen.getByRole("alert")).toHaveTextContent("Please answer: is the person looking 18 or over?");
  await user.click(screen.getByRole("radio", { name: "Yes" }));
  await user.click(screen.getByRole("button", { name: "Next" }));
  await user.type(await screen.findByLabelText(/Name or names/), "Rosa Dias");
  await user.click(screen.getByRole("button", { name: "Search" }));
  expect(await screen.findByText("name sounds like Rosa Diaz")).toBeInTheDocument();
  expect(screen.getByText("Don't say whether anyone is here.")).toBeInTheDocument();
  expect(screen.getByLabelText("Say:")).toHaveTextContent("We'll ask, and let you know.");
  expect(screen.getByLabelText("Say:")).not.toHaveTextContent("Don't say");
  await user.click(screen.getByRole("button", { name: "Ask this person" }));
  await user.type(screen.getByLabelText(/A message for them/), "Your son Luis is looking for you.");
  await user.click(screen.getByRole("button", { name: "Send the request" }));
  expect(await screen.findByText("Asked")).toBeInTheDocument();
  const searcher = sent.find((s) => s.path === "/api/persons")!.body as { isMinor: boolean };
  expect(searcher.isMinor).toBe(false);
  expect(sent.filter((s) => s.path === "/api/searches")).toHaveLength(1);
  expect(sent.find((s) => s.path === "/api/searches/s1/requests")!.body).toEqual({ personId: "p1", message: "Your son Luis is looking for you." });
});

test("search: a child's match card says a lead decides before anything is shared", async () => {
  const reason = "a child: a lead decides before anything is shared";
  const card = { personId: "c1", preferredName: "Mia Diaz", approxAge: 9, ageIsEstimate: true, child: true, score: 0.8,
    reasons: ["name spelled like Mia Diaz", reason], requested: false };
  fakeBackend({
    "POST /api/persons": { status: 201, body: {} },
    "POST /api/searches": () => ({ status: 201, body: { searchId: "s2", searcherPersonId: "x", soughtNames: ["Mia Diaz"], matches: [card] } }),
  });
  const user = at("/search");
  await user.type(screen.getByLabelText("Their name or nickname"), "Luis Diaz");
  await user.click(screen.getByRole("radio", { name: "Yes" }));
  await user.click(screen.getByRole("button", { name: "Next" }));
  await user.type(await screen.findByLabelText(/Name or names/), "Mia Diaz");
  await user.click(screen.getByRole("button", { name: "Search" }));
  const article = (await screen.findByText(reason)).closest("article")!;
  expect(within(article).getByRole("heading", { name: /Mia Diaz/ })).toBeInTheDocument();
});

test("answers: Connect first shows what will be shared; a child goes to a lead", async () => {
  const sent = fakeBackend({
    "GET /api/answers": { status: 200, body: [
      { requestId: "r1", personId: "a", personName: "Rosa", searcherName: "Luis", relationship: "son", message: "Your son is here.", childInvolved: false, personVisibility: "ask" },
      { requestId: "r2", personId: "b", personName: "Mia", searcherName: "Tom", relationship: "dad", message: null, childInvolved: true, personVisibility: "ask" },
    ] },
    "POST /api/requests/r1/decision": { status: 204 },
  });
  const user = at("/answers");
  await user.click(await screen.findByRole("button", { name: /Connect with Luis/ }));
  expect(screen.queryByRole("button", { name: "Yes, share this" })).not.toBeInTheDocument();
  await user.type(screen.getByLabelText(/How will they meet/), "Call the Eastside desk and ask for Rosa");
  expect(screen.getByText(/This will be shared with the person looking/)).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Yes, share this" }));
  expect(sent.find((s) => s.path === "/api/requests/r1/decision")!.body).toEqual({ choice: "CONNECT", contactPlan: "Call the Eastside desk and ask for Rosa" });
  expect(screen.getByText(/A child is involved, so a lead decides/)).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: /Connect with Tom/ })).not.toBeInTheDocument();
  // A child's card has no answer buttons at all: a lead decides everything.
  const childCard = screen.getByText(/A child is involved, so a lead decides/).closest("article")!;
  expect(within(childCard).queryAllByRole("button")).toHaveLength(0);
});

test("check a code: a miss gets the plain sentence; a match turns the screen around, fresh, then ends the check", async () => {
  let found = false;
  const sent = fakeBackend({
    "POST /api/lookup": () => (found
      ? { status: 200, body: { found: true, message: null, personName: "Luis Diaz", answersWaiting: 0, searches: [{ searchId: "s1", soughtNames: ["Rosa"], status: { type: "Active" } }] } }
      : { status: 200, body: { found: false, message: "That code and PIN didn't match. Please check the slip and try again.", personName: null, answersWaiting: 0, searches: [] } }),
    "GET /api/searches/s1/status": { status: 200, body: { type: "Safe" } },
    "POST /api/lookup/end": { status: 204 },
  });
  const user = at("/code");
  await user.type(screen.getByRole("textbox", { name: /Code from the slip/ }), "7k3p");
  await user.type(screen.getByLabelText("PIN from the slip"), "1234");
  await user.click(screen.getByRole("button", { name: "Check" }));
  expect(await screen.findByText("That code and PIN didn't match. Please check the slip and try again.")).toBeInTheDocument();
  expect(screen.getByLabelText("PIN from the slip")).toHaveValue("");
  expect(sent.find((s) => s.path === "/api/lookup")!.body).toEqual({ code: "7K3P", pin: "1234" });
  found = true;
  await user.type(screen.getByLabelText("PIN from the slip"), "4821");
  await user.click(screen.getByRole("button", { name: "Check" }));
  await user.click(await screen.findByRole("button", { name: /Turn the screen around/ }));
  // Asked fresh: the lookup said Active, the status asked just now says Safe, and the fresh one wins.
  expect(await screen.findByText("They asked us to tell you they are safe.")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Give the screen back to the volunteer" }));
  expect(sent.some((s) => s.path === "/api/lookup/end")).toBe(true);
  expect(await screen.findByRole("heading", { name: "Check a code" })).toBeInTheDocument();
});

test("the same person can look for another relative: a new search, the same searcher (one slip covers both)", async () => {
  const sent = fakeBackend({
    "POST /api/persons": { status: 201, body: {} },
    "POST /api/searches": () => ({ status: 201, body: { searchId: "s" + sent.length, searcherPersonId: "x", soughtNames: ["A"], matches: [] } }),
  });
  const user = at("/search");
  await user.type(screen.getByLabelText("Their name or nickname"), "Ilse Varnhagen");
  await user.click(screen.getByRole("radio", { name: "Yes" }));
  await user.click(screen.getByRole("button", { name: "Next" }));
  await user.type(await screen.findByLabelText(/Name or names/), "Otto Varnhagen");
  await user.click(screen.getByRole("button", { name: "Search" }));
  await user.click(await screen.findByRole("button", { name: /Look for someone else too/ }));
  await user.type(await screen.findByLabelText(/Name or names/), "Greta Varnhagen");
  await user.click(screen.getByRole("button", { name: "Search" }));
  await screen.findByRole("button", { name: /Look for someone else too/ });
  const searches = sent.filter((s) => s.path === "/api/searches").map((s) => s.body as { id: string; searcherPersonId: string });
  expect(searches).toHaveLength(2);
  expect(searches[0].searcherPersonId).toBe(searches[1].searcherPersonId);   // one person, one slip
  expect(searches[0].id).not.toBe(searches[1].id);                           // two searches
  expect(sent.filter((s) => s.path === "/api/persons")).toHaveLength(1);    // registered once
});
