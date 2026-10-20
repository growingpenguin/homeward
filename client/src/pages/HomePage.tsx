// pages/HomePage.tsx
// Three big buttons, one for each thing that happens at the desk.
import { useNavigate } from "react-router";
import { BigButton } from "../components/BigButton";
import { t } from "../strings";

export function HomePage() {
  const navigate = useNavigate();
  // Search and Check a code arrive in F3.
  return (
    <div className="stack">
      <BigButton label={t.home.here} onClick={() => navigate("/register")} />
      <BigButton label={t.home.looking} hint={t.home.comingNext} disabled />
      <BigButton label={t.home.code} hint={t.home.comingNext} disabled />
    </div>
  );
}
