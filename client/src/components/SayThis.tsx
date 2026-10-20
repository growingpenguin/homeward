// components/SayThis.tsx
// The exact words to read out, so a volunteer never has to make up how to explain privacy.
import type { ReactNode } from "react";
import { t } from "../strings";

export function SayThis({ children }: { children: ReactNode }) {
  return (
    <aside className="say-this" aria-label={t.say}>
      <strong>{t.say}</strong> “{children}”
    </aside>
  );
}
