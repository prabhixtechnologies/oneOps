import { describe, expect, it } from "vitest";
import { hardenLinks, sanitizeEmailHtml } from "./sanitize";

describe("sanitizeEmailHtml", () => {
  it("keeps the formatting a real message uses", () => {
    const html = sanitizeEmailHtml(
      '<p>Hello <b>there</b></p><blockquote>quoted</blockquote><a href="https://example.com">link</a>',
    );
    expect(html).toContain("<b>there</b>");
    expect(html).toContain("<blockquote>");
    expect(html).toContain('href="https://example.com"');
  });

  it("removes script tags", () => {
    const html = sanitizeEmailHtml('<p>hi</p><script>fetch("/steal")</script>');
    expect(html).not.toContain("script");
    expect(html).toContain("<p>hi</p>");
  });

  it("removes event handlers, which are the version of this that looks harmless", () => {
    const html = sanitizeEmailHtml('<img src="x" onerror="alert(1)">');
    expect(html).not.toContain("onerror");
  });

  it("rejects javascript: and data: URLs", () => {
    expect(sanitizeEmailHtml('<a href="javascript:alert(1)">x</a>')).not.toContain("javascript:");
    expect(sanitizeEmailHtml('<a href="data:text/html,<script>1</script>">x</a>')).not.toContain(
      "data:text/html",
    );
  });

  it("strips inline styles, which can cover the page with a clickable overlay", () => {
    const html = sanitizeEmailHtml('<div style="position:fixed;inset:0">x</div>');
    expect(html).not.toContain("style=");
  });

  it("drops forms, so a message cannot render a fake sign-in box", () => {
    const html = sanitizeEmailHtml('<form action="https://evil.test"><input name="password"></form>');
    expect(html).not.toContain("<form");
    expect(html).not.toContain("<input");
  });

  it("drops iframes", () => {
    expect(sanitizeEmailHtml('<iframe src="https://evil.test"></iframe>')).not.toContain("iframe");
  });

  it("keeps remote images, which is a deliberate trade rather than an oversight", () => {
    const html = sanitizeEmailHtml('<img src="https://tracker.test/pixel.gif">');
    expect(html).toContain("https://tracker.test/pixel.gif");
  });
});

describe("hardenLinks", () => {
  it("adds noopener, without which the opened page can navigate this one", () => {
    const container = document.createElement("div");
    container.innerHTML = '<a href="https://example.com">x</a>';
    hardenLinks(container);

    const anchor = container.querySelector("a");
    expect(anchor?.getAttribute("target")).toBe("_blank");
    expect(anchor?.getAttribute("rel")).toContain("noopener");
    expect(anchor?.getAttribute("rel")).toContain("noreferrer");
  });
});
