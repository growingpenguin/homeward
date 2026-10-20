// components/SearcherView.tsx
// The "turn the screen around" view, safe to show the person who is searching.
// It replaces the WHOLE screen (no staff menus, badges or other people behind it).
// SearcherStatus has no shelter or address field; only a "Connected" answer carries
// a contact plan, and the backend sends one only after the other person said yes.
import { useEffect } from "react";
import { createPortal } from "react-dom";
import type { SearcherStatus } from "../api/types";
import { t } from "../strings";

function message(status: SearcherStatus) {
  switch (status.type) {
    case "Active":     // no match, hidden, pending, declined, blocked: all look the same
      return <p>{t.searcher.active}</p>;
    case "Safe":
      return <p>{t.searcher.safe}</p>;
    case "Connected":  // the only case with details, and only because they said yes
      return (
        <>
          <p>{t.searcher.connected}</p>
          <p className="contact-plan">{status.contactPlan}</p>
        </>
      );
    default: {
      // A new status from the backend stops the build until someone decides what the searcher sees.
      const unhandled: never = status;
      void unhandled;
      return <p>{t.searcher.active}</p>;   // and if it ever slips through at runtime: the safe answer
    }
  }
}

// `status` comes through freshOnly(): undefined means a fresh answer isn't here yet.
// `failed`: the check itself failed, so say so (never "Checking…" forever) and offer to try again.
type Props = { status: SearcherStatus | undefined; failed?: boolean; onRetry?: () => void; onDone: () => void };

export function SearcherView({ status, failed, onRetry, onDone }: Props) {
  // Drawn over the WHOLE page (outside the app), and the app behind it is made inert:
  // the searcher can't see or reach the volunteer's name, menus or "Log out", not even with Tab.
  useEffect(() => {
    const app = document.getElementById("root");
    if (app) app.inert = true;
    return () => {
      if (app) app.inert = false;
    };
  }, []);
  return createPortal(
    <section className="searcher-view" aria-live="polite">
      {status ? message(status) : failed ? (
        <p role="alert">
          {t.searcher.failed} <button type="button" onClick={onRetry}>{t.problem.tryAgain}</button>
        </p>
      ) : <p role="status">{t.searcher.checking}</p>}
      <button type="button" className="big-button" onClick={onDone}>
        {t.searcher.giveBack}
      </button>
    </section>,
    document.body,
  );
}
