package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.mail.domain.MailTemplate;
import com.prabhix.platform.mail.inbound.MimeParser;
import com.prabhix.platform.mail.repository.MailTemplateRepository;
import com.prabhix.platform.mail.util.MailJson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TemplateRenderer {

    private final MailTemplateRepository templateRepository;
    private final TemplateEngine templateEngine = createEngine();

    public RenderedTemplate render(String templateKey, String locale, UUID organizationId,
                                     Map<String, Object> variables) {
        List<MailTemplate> resolved = templateRepository.resolveTemplate(templateKey, locale, organizationId);
        if (resolved.isEmpty()) {
            throw ApiException.of(ErrorCode.MAIL_TEMPLATE_NOT_FOUND,
                    "Template " + templateKey + " was not found");
        }
        MailTemplate template = resolved.get(0);
        validateVariables(template, variables);

        Context context = new Context();
        context.setVariables(variables);
        String subject = templateEngine.process(template.getSubject(), context);
        String html = templateEngine.process(template.getBodyHtml(), context);
        String text = template.getBodyText();
        if (text == null || text.isBlank()) {
            text = MimeParser.htmlToText(html);
        }
        return new RenderedTemplate(subject, html, text, template.isTrackingEnabled());
    }

    public RenderedTemplate preview(MailTemplate template, Map<String, Object> variables) {
        validateVariables(template, variables);
        Context context = new Context();
        context.setVariables(variables);
        String subject = templateEngine.process(template.getSubject(), context);
        String html = templateEngine.process(template.getBodyHtml(), context);
        String text = template.getBodyText();
        if (text == null || text.isBlank()) {
            text = MimeParser.htmlToText(html);
        }
        return new RenderedTemplate(subject, html, text, template.isTrackingEnabled());
    }

    void validateVariables(MailTemplate template, Map<String, Object> variables) {
        List<Map<String, Object>> declared = MailJson.parseObjectList(template.getVariables());
        for (Map<String, Object> var : declared) {
            boolean required = Boolean.TRUE.equals(var.get("required"));
            String name = String.valueOf(var.get("name"));
            if (required && (variables == null || !variables.containsKey(name) || variables.get(name) == null)) {
                throw ApiException.of(ErrorCode.MAIL_TEMPLATE_RENDER_FAILED,
                        "Missing required template variable: " + name);
            }
        }
    }

    private static TemplateEngine createEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    public record RenderedTemplate(String subject, String bodyHtml, String bodyText, boolean trackingEnabled) {
    }
}
