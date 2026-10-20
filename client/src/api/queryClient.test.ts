import { HttpError } from "./http";
import { worthRetrying } from "./queryClient";

test("worthRetrying retries network trouble and server errors, never a 4xx, and stops after 3 tries", () => {
  expect(worthRetrying(0, new TypeError("Failed to fetch"))).toBe(true);   // no connection at all
  for (const status of [500, 502, 503, 504]) expect(worthRetrying(0, new HttpError(status, "x"))).toBe(true);
  for (const status of [400, 401, 403, 404, 409, 422, 429]) expect(worthRetrying(0, new HttpError(status, "x"))).toBe(false);
  expect(worthRetrying(2, new HttpError(503, "x"))).toBe(true);
  expect(worthRetrying(3, new HttpError(503, "x"))).toBe(false);
  expect(worthRetrying(3, new TypeError("Failed to fetch"))).toBe(false);
});
