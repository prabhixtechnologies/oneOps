package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.mail.inbound.MimeParser;
import org.jsoup.Jsoup;

/**
 * Outbound HTML from mail clients: strip anything dangerous before it is stored or queued.
 *
 * <p>Uses the same Jsoup safelist as inbound {@link MimeParser}, so what users compose cannot introduce
 * tags the renderer would not already allow on received mail.
 */
public final class MailOutboundHtml {

    private MailOutboundHtml() {
    }

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return MimeParser.sanitizeHtml(html);
    }

    /**
     * Appends a mailbox signature once. Skips when the body already contains the signature text, so a
     * client that inserted it while composing is not doubled on send.
     */
    public static String appendSignature(String bodyHtml, String signatureHtml) {
        if (signatureHtml == null || signatureHtml.isBlank()) {
            return bodyHtml != null ? bodyHtml : "";
        }
        String body = bodyHtml != null ? bodyHtml : "";
        if (bodyAlreadyContainsSignature(body, signatureHtml)) {
            return body;
        }
        if (body.isBlank()) {
            return signatureHtml;
        }
        return body + "<br><br>" + signatureHtml;
    }

    static boolean bodyAlreadyContainsSignature(String bodyHtml, String signatureHtml) {
        String signatureText = Jsoup.parse(signatureHtml).text().trim();
        if (signatureText.isEmpty()) {
            return false;
        }
        String bodyText = Jsoup.parse(bodyHtml).text();
        return bodyText.contains(signatureText);
    }
}
