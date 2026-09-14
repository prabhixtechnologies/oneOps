import DOMPurify from "dompurify";

/**
 * Renders someone else's HTML without letting it run.
 *
 * <p>Mail bodies are the most hostile input a mail client handles: an attacker chooses every byte and
 * only has to get one message delivered. Rendering `bodyHtml` into the page without this is a standing
 * invitation to read the reader's session — which, since OneOps holds an access token in memory, means
 * their whole inbox.
 *
 * <p>Remote images are left in place deliberately. Blocking them is the privacy-conscious default and it
 * also breaks half of legitimate mail; the trade is a product decision, and the honest place to make it
 * is a setting rather than a silently stripped tag.
 */
export function sanitizeEmailHtml(html: string): string {
  return DOMPurify.sanitize(html, {
    FORBID_TAGS: ["style", "form", "input", "button", "iframe", "object", "embed", "link", "meta"],
    FORBID_ATTR: ["style", "srcset", "formaction", "background"],
    // Anything not on this list is not a link a mail body has any business making. javascript: and
    // data: URLs in particular are how a sanitiser that only filters tags still gets bypassed.
    ALLOWED_URI_REGEXP: /^(?:https?|mailto|tel|cid):/i,
    ADD_ATTR: ["target"],
  });
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
