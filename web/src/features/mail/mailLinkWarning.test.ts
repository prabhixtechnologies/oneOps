import { describe, expect, it } from "vitest";
import { parseMailLinkTarget } from "@/features/mail/mailLinkWarning";

describe("parseMailLinkTarget", () => {
  it("accepts https URLs and exposes host only", () => {
    const encoded = encodeURIComponent("https://Example.com/path?q=1");
    const parsed = parseMailLinkTarget(encoded);
    expect(parsed).toEqual({
      kind: "valid",
      href: "https://example.com/path?q=1",
      host: "example.com",
    });
  });

  it("rejects http and javascript schemes", () => {
    expect(parseMailLinkTarget("http://evil.test")).toEqual({ kind: "invalid" });
    expect(parseMailLinkTarget("javascript:alert(1)")).toEqual({ kind: "invalid" });
  });

  it("rejects malformed input", () => {
    expect(parseMailLinkTarget(null)).toEqual({ kind: "invalid" });
    expect(parseMailLinkTarget("%")).toEqual({ kind: "invalid" });
  });
});
