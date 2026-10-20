// components/VisibilityChoice.tsx
// Three big cards instead of a dropdown: every choice is visible and explained in plain words.
import { t } from "../strings";

export type Visibility = "findable" | "ask" | "hidden";
const options: Visibility[] = ["findable", "ask", "hidden"];

type Props = { value: Visibility; onChange: (v: Visibility) => void };

export function VisibilityChoice({ value, onChange }: Props) {
  return (
    <fieldset className="choice-cards">
      <legend className="visually-hidden">{t.steps.privacy}</legend>
      {options.map((v) => (
        // The whole card is the click target, not just the small radio circle.
        <label key={v} className={value === v ? "choice-card is-selected" : "choice-card"}>
          <input
            type="radio"
            name="visibility"
            value={v}
            checked={value === v}
            onChange={() => onChange(v)}
          />
          <strong>{t.visibility[v].label}</strong>
          <span>{t.visibility[v].hint}</span>
        </label>
      ))}
    </fieldset>
  );
}
