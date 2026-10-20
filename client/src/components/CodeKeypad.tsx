// components/CodeKeypad.tsx
// For people with no phone: the volunteer enters the code from the printed slip.
// Type it, paste it, or tap the big keys. Only characters a code can contain are kept.
import { t } from "../strings";

const ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";   // same as the backend: no 0/O, no 1/I
const CODE_LENGTH = 4;

// For typed or pasted text: "hw-7k3p", "HW 7K3P", "HW7K3P" and "7K3P" all become "7K3P".
// "HW" is only treated as the prefix when it's clearly the prefix, because H and W can be part of a code.
export function normalizeCode(raw: string): string {
  let text = raw.toUpperCase().trim();
  if (/^HW[\s-]/.test(text)) text = text.slice(3);                   // "HW-7K3P", "HW 7K3P"
  let chars = [...text].filter((ch) => ALPHABET.includes(ch));
  if (chars.length === CODE_LENGTH + 2 && chars[0] === "H" && chars[1] === "W") chars = chars.slice(2);  // "HW7K3P"
  return chars.slice(0, CODE_LENGTH).join("");
}

type Props = { value: string; onChange: (v: string) => void };

export function CodeKeypad({ value, onChange }: Props) {
  // A key adds exactly one character; no prefix guessing, so tapping H then W keeps "HW".
  const press = (ch: string) => {
    if (value.length < CODE_LENGTH) onChange(value + ch);
  };

  return (
    <div className="code-keypad">
      <label className="code-input">
        {t.code.label}
        <span className="code-input__row">
          HW-
          <input
            value={value}
            onChange={(e) => onChange(normalizeCode(e.target.value))}
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            inputMode="text"
            maxLength={12}   // room for a pasted "HW-7K3P"; normalizeCode keeps 4
          />
        </span>
      </label>
      <div className="keypad" role="group" aria-label={t.code.keys}>
        {[...ALPHABET].map((ch) => (
          <button type="button" key={ch} onClick={() => press(ch)}>
            {ch}
          </button>
        ))}
        <button type="button" onClick={() => onChange(value.slice(0, -1))} aria-label={t.code.delete}>
          ⌫
        </button>
      </div>
    </div>
  );
}
