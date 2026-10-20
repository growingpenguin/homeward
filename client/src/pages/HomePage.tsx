// pages/HomePage.tsx
// Three big buttons, one for each thing that happens at the desk.
import { BigButton } from "../components/BigButton";
import { t } from "../strings";

export function HomePage() {
  // Register arrives in F2, Search and Check a code in F3.
  return (
    <div className="stack">
      <BigButton label={t.home.here} hint={t.home.comingNext} disabled />
      <BigButton label={t.home.looking} hint={t.home.comingNext} disabled />
      <BigButton label={t.home.code} hint={t.home.comingNext} disabled />
    </div>
  );
}
