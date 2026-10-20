import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach } from "vitest";
import { queryClient } from "../api/queryClient";
import { t } from "../strings";
import { fakeBackend } from "../test/fakeFetch";
import { LoginPage } from "./LoginPage";

beforeEach(() => {
  queryClient.clear();
  sessionStorage.clear();
  document.cookie = "XSRF-TOKEN=test-token";
  vi.useFakeTimers({ shouldAdvanceTime: true });
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

async function fillAndSubmit() {
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  render(
    <QueryClientProvider client={queryClient}>
      <LoginPage onLoggedIn={() => {}} />
    </QueryClientProvider>,
  );
  await user.selectOptions(await screen.findByLabelText(t.login.shelter), "s1");
  await user.type(screen.getByLabelText(t.login.name), "Vol Test");
  await user.type(screen.getByLabelText(t.login.pin), "1234");
  await user.click(screen.getByRole("button", { name: t.login.submit }));
}

test("a 502 on login (the free server is waking up) is retried and then shows the plain no-connection sentence", async () => {
  const sent = fakeBackend({
    "GET /api/shelters": { status: 200, body: [{ id: "s1", name: "North Test Shelter" }] },
    "POST /api/login": { status: 502, body: {} },
  });
  await fillAndSubmit();
  for (let i = 0; i < 3; i++) await act(() => vi.advanceTimersByTimeAsync(5_000));   // the retries wait 5 s each
  expect(await screen.findByRole("alert")).toHaveTextContent(t.problem.noConnection);
  expect(sent.filter((s) => s.path === "/api/login")).toHaveLength(4);               // the first try and 3 retries
});

test("a wrong PIN (401) is shown as the server's sentence and never retried", async () => {
  const sent = fakeBackend({
    "GET /api/shelters": { status: 200, body: [{ id: "s1", name: "North Test Shelter" }] },
    "POST /api/login": { status: 401, body: { message: "That name and PIN didn't match." } },
  });
  await fillAndSubmit();
  expect(await screen.findByRole("alert")).toHaveTextContent("That name and PIN didn't match.");
  expect(sent.filter((s) => s.path === "/api/login")).toHaveLength(1);
});
