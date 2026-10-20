// App.tsx: who is logged in decides what is shown. Logged in, everything sits inside AppShell (idle lock).
import { useEffect } from "react";
import { QueryClientProvider, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { authEvents, getMe, logout, stillHere, verifyPin } from "./api/client";
import { queryClient } from "./api/queryClient";
import type { Me } from "./api/types";
import { AppShell } from "./components/AppShell";
import { ConnectionBanner } from "./components/ConnectionBanner";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { t } from "./strings";

function Session() {
  const qc = useQueryClient();
  const me = useQuery({ queryKey: ["me"], queryFn: getMe });

  // Logged out (by the server, or by pressing Log out): forget everything loaded, show the login.
  useEffect(() => {
    const loggedOut = () => {
      void qc.cancelQueries();
      qc.removeQueries({ predicate: (q) => q.queryKey[0] !== "me" });
      qc.setQueryData(["me"], null);
    };
    authEvents.addEventListener("expired", loggedOut);
    return () => authEvents.removeEventListener("expired", loggedOut);
  }, [qc]);

  const signOut = useMutation({
    mutationFn: logout,
    onSettled: () => authEvents.dispatchEvent(new Event("expired")),
  });

  if (me.isPending) return <main><ConnectionBanner connecting unsent={0} canKeepDraft /></main>;
  if (me.isError) {
    return (
      <main>
        <p role="alert">{t.problem.noConnection}</p>
        <button type="button" onClick={() => me.refetch()}>{t.problem.tryAgain}</button>
      </main>
    );
  }
  if (!me.data) return <LoginPage onLoggedIn={(m: Me) => qc.setQueryData(["me"], m)} />;

  return (
    <AppShell verifyPin={verifyPin} stillHere={stillHere}>
      <header className="top-bar no-print">
        <span>{t.home.hello(me.data.displayName)}</span>
        <button type="button" onClick={() => signOut.mutate()}>{t.home.logout}</button>
      </header>
      <main>
        <h1>{t.appName}</h1>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </AppShell>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Session />
      </BrowserRouter>
    </QueryClientProvider>
  );
}
