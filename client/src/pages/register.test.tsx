import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, beforeEach } from "vitest";
import { queryClient } from "../api/queryClient";
import { fakeBackend } from "../test/fakeFetch";
import { RegisterPage } from "./RegisterPage";
import { SlipPage } from "./SlipPage";

const STAFF = "00000000-0000-0000-0000-0000000000a1";
const saved = (over = {}) => ({
  id: "x", preferredName: "Rosa", otherNames: [], approxAge: 80, ageIsEstimate: true, isMinor: null, treatedAsMinor: false,
  unaccompanied: false, homeArea: null, description: null, phone: null, visibility: "ask", source: "app", paperRef: null,
  recordedAt: "2026-10-20T12:00:00Z", hasSlip: false, ...over,
});

function app() {
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/register"]}>
        <Routes>
          <Route path="/register" element={<RegisterPage staffId={STAFF} />} />
          <Route path="/persons/:id/slip" element={<SlipPage />} />
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

test("only the name is required; Ask me first is the default; a retry after a network error re-sends the same id", async () => {
  let calls = 0;
  const sent = fakeBackend({
    "POST /api/persons": () => (++calls === 1 ? { status: 503, body: {} } : { status: 201, body: saved({ id: "p1" }) }),
    "GET /api/persons/p1": { status: 200, body: saved({ id: "p1" }) },
  });
  const user = app();
  await user.click(screen.getByRole("button", { name: "No, the person is here with me" }));
  await user.click(screen.getByRole("button", { name: "Next" }));
  expect(await screen.findByText("Please write a name or nickname.")).toBeInTheDocument();
  await user.type(screen.getByLabelText("What should we call you?"), "Rosa");
  for (let i = 0; i < 4; i++) await user.click(screen.getByRole("button", { name: "Next" }));
  await user.click(screen.getByRole("button", { name: "Save" }));
  expect(await screen.findByText("Rosa is saved.", {}, { timeout: 5000 })).toBeInTheDocument();
  const posts = sent.filter((s) => s.path === "/api/persons");
  expect(posts).toHaveLength(2);
  expect((posts[0].body as { id: string }).id).toBe((posts[1].body as { id: string }).id);
  expect(posts[1].body).toMatchObject({ preferredName: "Rosa", visibility: "ask", source: "app" });
}, 10000);

test("paper mode needs the sheet/line and the date before going on", async () => {
  fakeBackend({});
  const user = app();
  await user.click(screen.getByRole("button", { name: "Yes, I'm copying paper forms" }));
  await user.click(screen.getByRole("button", { name: "Next" }));
  expect(screen.getByText('Write where it is on the paper, e.g. "Sheet 4, line 12".')).toBeInTheDocument();
  expect(screen.getByText(/Write the date on the paper/)).toBeInTheDocument();
  expect(screen.getByText("Step 1 of 6")).toBeInTheDocument();
});

test("a reload keeps what was typed (draft), with the same record id", async () => {
  fakeBackend({});
  let user = app();
  await user.click(screen.getByRole("button", { name: "No, the person is here with me" }));
  await user.type(screen.getByLabelText("What should we call you?"), "Mia");
  await waitFor(() => expect(sessionStorage.getItem(`homeward-draft:${STAFF}:register`)).toContain("Mia"));
  const id = JSON.parse(sessionStorage.getItem(`homeward-draft:${STAFF}:register`)!).data.recordId;
  document.body.innerHTML = "";
  user = app();
  expect(await screen.findByDisplayValue("Mia")).toBeInTheDocument();
  expect(JSON.parse(sessionStorage.getItem(`homeward-draft:${STAFF}:register`)!).data.recordId).toBe(id);
  void user;
});

test("the slip shows the code and PIN once, after pressing Print a slip", async () => {
  fakeBackend({
    "GET /api/persons/p1": { status: 200, body: saved({ id: "p1" }) },
    "POST /api/persons/p1/slip": { status: 201, body: { code: "HW-7K3P", pin: "4821", shelterName: "Lincoln High Shelter", deskPhone: "555-0101" } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/persons/p1/slip"]}>
        <Routes><Route path="/persons/:id/slip" element={<SlipPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "Print a slip" }));
  expect(await screen.findByText("HW-7K3P")).toBeInTheDocument();
  expect(screen.getByText("4821")).toBeInTheDocument();
  expect(screen.getByText(/The PIN is shown only now/)).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Print a slip" })).not.toBeInTheDocument();
});
