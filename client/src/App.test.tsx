import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach } from "vitest";
import App from "./App";
import { api, stillHere } from "./api/client";
import { queryClient } from "./api/queryClient";
import { fakeBackend } from "./test/fakeFetch";

const shelter = { id: "00000000-0000-0000-0000-0000000000d1", name: "Lincoln High Shelter" };
const alex = { staffId: "00000000-0000-0000-0000-0000000000a1", shelterId: shelter.id, displayName: "Volunteer Alex", role: "volunteer" };

beforeEach(() => {
  queryClient.clear();
  document.cookie = "XSRF-TOKEN=test-token";
});
afterEach(() => vi.unstubAllGlobals());

test("login: missing details and a wrong PIN get plain sentences; the PIN box is emptied", async () => {
  const sent = fakeBackend({
    "GET /api/me": { status: 401 },
    "GET /api/health": { status: 200, body: { status: "ok" } },
    "GET /api/shelters": { status: 200, body: [shelter] },
    "POST /api/login": { status: 401, body: { message: "That didn't work. Check the shelter, your name and your PIN." } },
  });
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "Log in" }));
  expect(screen.getByRole("alert")).toHaveTextContent("Please choose your shelter, and write your name and PIN.");

  await user.selectOptions(await screen.findByLabelText("Your shelter"), shelter.name);
  await user.type(screen.getByLabelText("Your name"), "Volunteer Alex");
  await user.type(screen.getByLabelText("Your PIN"), "1111");
  await user.click(screen.getByRole("button", { name: "Log in" }));
  expect(await screen.findByText("That didn't work. Check the shelter, your name and your PIN.")).toBeInTheDocument();
  expect(screen.getByLabelText("Your PIN")).toHaveValue("");
  const loginCall = sent.find((s) => s.path === "/api/login")!;
  expect(loginCall.headers["X-XSRF-TOKEN"]).toBe("test-token");
  expect(loginCall.body).toEqual({ shelterId: shelter.id, name: "Volunteer Alex", pin: "1111" });
});

test("logged in: home shows the three actions; the server's lock replaces the whole app", async () => {
  fakeBackend({
    "GET /api/me": { status: 200, body: alex },
    "GET /api/persons/x": { status: 401, body: { locked: true, message: "Screen locked. Enter your PIN to carry on." } },
  });
  render(<App />);
  expect(await screen.findByText("Logged in as Volunteer Alex")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: /Someone is here/ })).toBeInTheDocument();

  await act(() => api("/api/persons/x").catch(() => {}));
  expect(await screen.findByRole("heading", { name: "Screen locked" })).toBeInTheDocument();
  expect(screen.queryByText("Logged in as Volunteer Alex")).not.toBeInTheDocument();
});

test("a session the server ended goes back to the login", async () => {
  fakeBackend({
    "GET /api/me": { status: 200, body: alex },
    "GET /api/shelters": { status: 200, body: [shelter] },
    "GET /api/health": { status: 200, body: {} },
    "GET /api/persons/x": { status: 401, body: { expired: true, message: "Your shift session has ended. Please log in again." } },
  });
  render(<App />);
  await screen.findByText("Logged in as Volunteer Alex");
  await act(() => api("/api/persons/x").catch(() => {}));
  expect(await screen.findByRole("heading", { name: "Log in" })).toBeInTheDocument();
});

test("only a person's actions carry the activity header", async () => {
  const sent = fakeBackend({ "GET /api/me": { status: 200, body: alex } });
  await api("/api/me");
  await stillHere();
  expect(sent[0].headers["X-Homeward-Activity"]).toBeUndefined();
  expect(sent[1].headers["X-Homeward-Activity"]).toBe("1");
});
