// App.tsx: who is logged in decides what is shown. Logged in, everything sits inside AppShell (idle lock).
import { useEffect } from "react";
import { QueryClientProvider, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { authEvents, getMe, logout, stillHere, verifyPin } from "./api/client";
import { queryClient } from "./api/queryClient";
import type { Me } from "./api/types";
import { AppShell } from "./components/AppShell";
import { clearAllDrafts } from "./hooks/useDraft";
import { ConnectionBanner } from "./components/ConnectionBanner";
import { AnswersPage } from "./pages/AnswersPage";
import { CheckCodePage } from "./pages/CheckCodePage";
import { EditPage } from "./pages/EditPage";
import { HomePage } from "./pages/HomePage";
import { LeadPage } from "./pages/LeadPage";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { SearchPage } from "./pages/SearchPage";
import { SlipPage } from "./pages/SlipPage";
import { t } from "./strings";

function Session() {
  const qc = useQueryClient();
  // Changes to the session arrive as events (login, lock, expired), so no refetching on focus.
  const me = useQuery({ queryKey: ["me"], queryFn: getMe, staleTime: Infinity, refetchOnWindowFocus: false });

  // Logged out (by the server, or by pressing Log out): forget everything loaded, show the login.
  useEffect(() => {
    const loggedOut = () => {
      // Already logged out: a late 401 (e.g. the "still here" call sent by the click on Log out itself)
      // must not run this again, or it would cancel the login page's own shelter list while it loads.
      if (qc.getQueryData(["me"]) === null) return;
      // Everything about people goes; the shelter list (public, used by the login page) stays.
      const personal = (q: { queryKey: readonly unknown[] }) => q.queryKey[0] !== "me" && q.queryKey[0] !== "shelters";
      void qc.cancelQueries({ predicate: personal });
      qc.removeQueries({ predicate: personal });
      qc.getMutationCache().clear();   // unsent saves must not go out later under someone else's login
      clearAllDrafts();                // no survivor's details left on a shared laptop
      qc.setQueryData(["me"], null);
    };
    authEvents.addEventListener("expired", loggedOut);
    return () => authEvents.removeEventListener("expired", loggedOut);
  }, [qc]);

  // The login page only comes back once the server has really ended the session; otherwise the next
  // person at the laptop could reload into this volunteer's session.
  const signOut = useMutation({
    mutationFn: logout,
    onSuccess: () => authEvents.dispatchEvent(new Event("expired")),
  });

  // After a reload onto a locked server the volunteer's name isn't known yet: unlocking fetches it.
  const unlockAndReload = async (pin: string) => {
    const ok = await verifyPin(pin);
    if (ok) await qc.refetchQueries({ queryKey: ["me"] });
    return ok;
  };

  if (me.isPending) return <main><ConnectionBanner connecting unsent={0} canKeepDraft /></main>;
  if (me.isError && me.data === undefined) {
    return (
      <main>
        <p role="alert">{t.problem.noConnection}</p>
        <button type="button" onClick={() => me.refetch()}>{t.problem.tryAgain}</button>
      </main>
    );
  }
  if (me.data === null || me.data === undefined) return <LoginPage onLoggedIn={(m: Me) => qc.setQueryData(["me"], m)} />;

  // Lead tools are shown only to leads; the backend refuses them to anyone else anyway (/api/lead/**).
  const isLead = me.data !== "locked" && (me.data.role === "lead" || me.data.role === "coordinator");
  return (
    <AppShell verifyPin={unlockAndReload} stillHere={stillHere} startLocked={me.data === "locked"}>
      <header className="top-bar no-print">
        {me.data !== "locked" && <span>{t.home.hello(me.data.displayName)}</span>}
        <button type="button" onClick={() => signOut.mutate()} disabled={signOut.isPending}>{t.home.logout}</button>
      </header>
      {signOut.isError && <p role="alert" className="no-print">{t.home.logoutFailed}</p>}
      <main>
        <h1>{t.appName}</h1>
        <Routes>
          <Route path="/" element={<HomePage isLead={isLead} />} />
          {me.data !== "locked" && <Route path="/register" element={<RegisterPage key={me.data.staffId} staffId={me.data.staffId} />} />}
          {me.data !== "locked" && <Route path="/search" element={<SearchPage key={me.data.staffId} staffId={me.data.staffId} />} />}
          <Route path="/answers" element={<AnswersPage />} />
          <Route path="/code" element={<CheckCodePage />} />
          <Route path="/persons/:id/slip" element={<SlipPage isLead={isLead} />} />
          {isLead && <Route path="/lead" element={<LeadPage />} />}
          <Route path="/persons/:id/edit" element={<EditPage />} />
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
