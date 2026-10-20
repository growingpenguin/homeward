// components/SayThis.tsx
// The exact words to read out, so a volunteer never has to make up how to explain privacy.
import type { ReactNode } from "react";
import { t } from "../strings";

// label: "Say:" for words the volunteer says; "Read this to them:" for a message someone else wrote.
export function SayThis({ children, label = t.say }: { children: ReactNode; label?: string }) {
  return (
    <aside className="say-this" aria-label={label}>
      <strong>{label}</strong> “{children}”
    </aside>
  );
}
