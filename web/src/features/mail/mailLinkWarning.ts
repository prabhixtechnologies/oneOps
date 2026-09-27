export type ParsedMailLinkTarget =
  | { kind: "invalid" }
  | { kind: "valid"; href: string; host: string };

/** Parses the tracked-link interstitial target query param; only https URLs are continuable. */
export function parseMailLinkTarget(raw: string | null): ParsedMailLinkTarget {
  if (raw == null || raw.trim() === "") {
    return { kind: "invalid" };
  }
  let decoded: string;
  try {
    decoded = decodeURIComponent(raw.trim());
  } catch {
    return { kind: "invalid" };
  }
  try {
    const url = new URL(decoded);
    if (url.protocol !== "https:") {
      return { kind: "invalid" };
    }
    const host = url.hostname.toLowerCase();
    if (!host) {
      return { kind: "invalid" };
    }
    return { kind: "valid", href: url.toString(), host };
  } catch {
    return { kind: "invalid" };
  }
}
