package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.util.MailTrackingToken;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MailTrackingInjector {

    private final PrabhixProperties properties;

    public String inject(String bodyHtml, UUID outboxId, boolean templateTrackingEnabled) {
        if (!properties.mail().tracking().enabled() || !templateTrackingEnabled
                || bodyHtml == null || bodyHtml.isBlank()) {
            return bodyHtml;
        }

        String apiBase = properties.urls().api().replaceAll("/$", "");
        String secret = properties.mail().tracking().secret();
        String openToken = MailTrackingToken.buildToken(secret, "open", outboxId.toString());
        String pixel = "<img src=\"" + apiBase + "/api/v1/mail/t/o/" + openToken
                + "\" width=\"1\" height=\"1\" alt=\"\" style=\"display:none\"/>";

        Document document = Jsoup.parseBodyFragment(bodyHtml);
        document.body().append(pixel);

        for (Element link : document.select("a[href]")) {
            String href = link.attr("href");
            if (href == null || href.isBlank() || href.startsWith("mailto:")) {
                continue;
            }
            String encodedUrl = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(href.getBytes(StandardCharsets.UTF_8));
            String clickPayload = outboxId + ":" + encodedUrl;
            String clickToken = MailTrackingToken.buildToken(secret, "click", clickPayload);
            link.attr("href", apiBase + "/api/v1/mail/t/c/" + clickToken);
        }

        return document.body().html();
    }
}
