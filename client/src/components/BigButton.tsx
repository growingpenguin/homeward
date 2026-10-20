// components/BigButton.tsx
// One look for every main action: big, full width, words not icons.
import type { ButtonHTMLAttributes } from "react";

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  label: string;   // what happens, in plain words: "Someone is here"
  hint?: string;   // one short line under it, optional
};

export function BigButton({ label, hint, className, type = "button", ...rest }: Props) {
  // type="button" by default: inside a form, a "Back" button must never save the form by accident.
  return (
    <button type={type} className={className ? `big-button ${className}` : "big-button"} {...rest}>
      <span className="big-button__label">{label}</span>
      {hint && <span className="big-button__hint">{hint}</span>}
    </button>
  );
}
