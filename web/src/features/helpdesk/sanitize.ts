import DOMPurify from "dompurify";

/**
 * Renders someone else's HTML without letting it run.
 *
 * <p>Mail bodies are the most hostile input a mail client handles: an attacker chooses every byte and
 * only has to get one message delivered. Rendering `bodyHtml` into the page without this is a standing
 * invitation to read the reader's session — which, since OneOps holds an access token in memory, means
 * their whole inbox.
 *
 * <p>Remote images are blocked by default. The original URL is preserved on {@code data-remote-src} so the
 * reader can load it explicitly from the helpdesk UI.
 */
export function sanitizeEmailHtml(html: string): string {
  const sanitized = DOMPurify.sanitize(html, {
    FORBID_TAGS: ["style", "form", "input", "button", "iframe", "object", "embed", "link", "meta"],
    FORBID_ATTR: ["style", "srcset", "formaction", "background"],
    // Anything not on this list is not a link a mail body has any business making. javascript: and
    // data: URLs in particular are how a sanitiser that only filters tags still gets bypassed.
    ALLOWED_URI_REGEXP: /^(?:https?|mailto|tel|cid):/i,
    ADD_ATTR: ["target", "data-remote-src"],
  });
  return sanitized.replace(/<img\b([^>]*?)\ssrc=(["'])(https?:\/\/[^"']+)\2/gi, (_match, attrs, _q, src) => {
    return `<img${attrs} data-remote-src="${src}" alt="Remote image blocked"`;
  });
}

/** Restores a blocked remote image after explicit reader consent. */
export function revealRemoteImages(container: HTMLElement): void {
  for (const img of container.querySelectorAll("img[data-remote-src]:not([src])")) {
    const remote = img.getAttribute("data-remote-src");
    if (remote) {
      img.setAttribute("src", remote);
    }
  }
}

/**
 * Forces every link in a sanitised body to open in a new tab with the referrer stripped.
 *
 * <p>`target="_blank"` without `rel="noopener"` hands the opened page a handle on this one, which it can
 * use to navigate OneOps somewhere else — a phishing page that appears in the tab the reader trusts.
 */
export function hardenLinks(container: HTMLElement): void {
  for (const anchor of container.querySelectorAll("a")) {
    anchor.setAttribute("target", "_blank");
    anchor.setAttribute("rel", "noopener noreferrer nofollow");
  }
}
