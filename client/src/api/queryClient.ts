// api/queryClient.ts
// Bad Wi-Fi is normal in a shelter: retry quietly, but only when retrying can help.
import { QueryClient, queryOptions } from "@tanstack/react-query";
import type { SearcherStatus } from "./types";
import { HttpError } from "./http";

// Retry network trouble and server hiccups (5xx), never "not allowed" or "check your input" (4xx).
export const worthRetrying = (count: number, error: Error) =>
  count < 3 && !(error instanceof HttpError && error.status < 500);

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: worthRetrying,
      retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 15_000),  // 1 s, 2 s, 4 s … max 15 s
    },
    mutations: {
      retry: false,   // off by default: a decision or a PIN check must not be repeated by accident.
                      // Registration turns it on, because it re-sends the same id (see toRequest.ts).
    },
  },
});

// What a searcher sees is asked fresh every time and never kept in memory,
// so a safety flag added a minute ago always wins over an old "Connected".
export const searcherStatusQuery = (searchId: string, fetchStatus: (id: string) => Promise<SearcherStatus>) =>
  queryOptions({
    queryKey: ["searcher-status", searchId],
    queryFn: () => fetchStatus(searchId),
    staleTime: 0,
    gcTime: 0,
    refetchOnMount: "always",
  });

// Show a searcher an answer only if it came from a request made for THIS screen, the latest
// request succeeded, and nothing newer is loading or waiting for the internet ("paused").
// Otherwise the screen says "Checking…".
export function freshOnly<T>(q: {
  data: T | undefined;
  status: "pending" | "error" | "success";
  fetchStatus: "fetching" | "paused" | "idle";
  isFetchedAfterMount: boolean;
}): T | undefined {
  return q.status === "success" && q.fetchStatus === "idle" && q.isFetchedAfterMount ? q.data : undefined;
}
