// components/SearcherView.tsx
// The "turn the screen around" view, safe to show the person who is searching.
// It replaces the WHOLE screen (no staff menus, badges or other people behind it).
// SearcherStatus has no shelter or address field; only a "Connected" answer carries
// a contact plan, and the backend sends one only after the other person said yes.
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
export function SearcherView({ status, onDone }: { status: SearcherStatus | undefined; onDone: () => void }) {
  return (
    <main className="searcher-view">
      {status ? message(status) : <p role="status">{t.searcher.checking}</p>}
      <button type="button" className="big-button" onClick={onDone}>
        {t.searcher.giveBack}
      </button>
    </main>
  );
}
