// components/ConnectionBanner.tsx
// Always say what's going on, honestly: a calm banner instead of a frozen screen.
import { useEffect, useState } from "react";
import { t } from "../strings";

type Props = {
  connecting: boolean;    // a request has been waiting a while (e.g. the free server is starting)
  unsent: number;         // saves waiting for the internet
  canKeepDraft: boolean;  // from useDraft: false if this device won't store a draft
};

export function ConnectionBanner({ connecting, unsent, canKeepDraft }: Props) {
  // navigator.onLine is only a hint (it can say "online" when the server is unreachable),
  // so "connecting" and "unsent" come from the real requests.
  const [online, setOnline] = useState(navigator.onLine);

  useEffect(() => {
    const on = () => setOnline(true);
    const off = () => setOnline(false);
    window.addEventListener("online", on);
    window.addEventListener("offline", off);
    return () => {
      window.removeEventListener("online", on);
      window.removeEventListener("offline", off);
    };
  }, []);

  if (!canKeepDraft) return <div role="alert" className="banner banner--warn">{t.banner.noDraft}</div>;
  if (!online) return <div role="status" className="banner banner--offline">{t.banner.offline}</div>;
  if (unsent > 0) return <div role="status" className="banner">{t.banner.notSent(unsent)}</div>;
  if (connecting) return <div role="status" className="banner">{t.banner.connecting}</div>;
  return null;
}
