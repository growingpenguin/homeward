// pages/HomePage.tsx
// Three big buttons, one for each thing that happens at the desk, and the answers waiting here.
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import { api } from "../api/client";
import type { WaitingRequest } from "../api/types";
import { BigButton } from "../components/BigButton";
import { t } from "../strings";

export function HomePage() {
  const navigate = useNavigate();
  const waiting = useQuery({ queryKey: ["answers"], queryFn: () => api<WaitingRequest[]>("/api/answers") });
  const count = waiting.data?.length ?? 0;
  return (
    <div className="stack">
      <BigButton label={t.home.here} onClick={() => navigate("/register")} />
      <BigButton label={t.home.looking} onClick={() => navigate("/search")} />
      <BigButton label={t.home.code} onClick={() => navigate("/code")} />
      <button type="button" onClick={() => navigate("/answers")}>
        {t.answers.title}{count > 0 ? ` (${count})` : ""}
      </button>
    </div>
  );
}
