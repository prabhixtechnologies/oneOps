package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.mail.domain.MailTemplate;
import com.prabhix.platform.mail.dto.TemplateDtos;
import com.prabhix.platform.mail.outbound.TemplateRenderer;
import com.prabhix.platform.mail.repository.MailTemplateRepository;
import com.prabhix.platform.common.util.Json;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TemplateManagementService {

    private final MailTemplateRepository templateRepository;
    private final TemplateRenderer templateRenderer;

    @Transactional(readOnly = true)
    public TemplateDtos.TemplateDetailResponse get(String key, UUID organizationId) {
        MailTemplate template = resolveForRead(key, organizationId);
        return toDetail(template);
    }

    @Transactional
    public TemplateDtos.TemplateDetailResponse update(String key, UUID organizationId,
                                                      TemplateDtos.UpdateTemplateRequest request) {
        MailTemplate template = resolveForWrite(key, organizationId);
        if (request.name() != null && !request.name().isBlank()) {
            template.setName(request.name().trim());
        }
        if (request.subject() != null) {
            template.setSubject(request.subject());
        }
        if (request.htmlBody() != null) {
            template.setBodyHtml(request.htmlBody());
        }
        if (request.textBody() != null) {
            template.setBodyText(request.textBody());
        }
        assertStillRenders(template);
        return toDetail(templateRepository.save(template));
    }

    /**
     * A malformed expression in a saved template would not surface until the next password
     * reset or receipt failed to send, so the edit is rendered against the declared example
     * values before it is allowed through.
     */
    private void assertStillRenders(MailTemplate template) {
        Map<String, Object> sample = new LinkedHashMap<>();
        for (Map<String, Object> declared : Json.parseObjectList(template.getVariables())) {
            Object name = declared.get("name");
            if (name != null) {
                Object example = declared.get("example");
                sample.put(String.valueOf(name), example != null ? example : "");
            }
        }
        try {
            templateRenderer.preview(template, sample);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApiException.of(ErrorCode.MAIL_TEMPLATE_RENDER_FAILED,
                    "That template no longer renders: " + e.getMessage());
        }
    }

    private MailTemplate resolveForRead(String key, UUID organizationId) {
        var resolved = templateRepository.resolveTemplate(key, "en", organizationId);
        if (resolved.isEmpty()) {
            throw ApiException.of(ErrorCode.MAIL_TEMPLATE_NOT_FOUND, "Template not found");
        }
        return resolved.get(0);
    }

    private MailTemplate resolveForWrite(String key, UUID organizationId) {
        var orgOverride = templateRepository.findByOrganizationIdAndTemplateKeyAndLocale(
                organizationId, key, "en");
        if (orgOverride.isPresent()) {
            return orgOverride.get();
        }
        var system = templateRepository.findByOrganizationIdIsNullAndTemplateKeyAndLocale(key, "en");
        if (system.isEmpty()) {
            throw ApiException.of(ErrorCode.MAIL_TEMPLATE_NOT_FOUND, "Template not found");
        }
        MailTemplate copy = cloneForOrganization(system.get(), organizationId);
        return templateRepository.save(copy);
    }

    private MailTemplate cloneForOrganization(MailTemplate source, UUID organizationId) {
        MailTemplate copy = new MailTemplate();
        copy.setOrganizationId(organizationId);
        copy.setTemplateKey(source.getTemplateKey());
        copy.setLocale(source.getLocale());
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setSubject(source.getSubject());
        copy.setBodyHtml(source.getBodyHtml());
        copy.setBodyText(source.getBodyText());
        copy.setVariables(source.getVariables());
        copy.setCategory(source.getCategory());
        copy.setTrackingEnabled(source.isTrackingEnabled());
        copy.setEnabled(source.isEnabled());
        return copy;
    }

    private TemplateDtos.TemplateDetailResponse toDetail(MailTemplate t) {
        List<TemplateDtos.TemplateVariable> variables = Json.parseObjectList(t.getVariables())
                .stream()
                .map(v -> new TemplateDtos.TemplateVariable(
                        String.valueOf(v.get("name")),
                        v.get("example") == null ? null : String.valueOf(v.get("example")),
                        Boolean.TRUE.equals(v.get("required"))))
                .toList();
        return new TemplateDtos.TemplateDetailResponse(
                t.getTemplateKey(),
                t.getName(),
                t.getLocale(),
                t.getSubject(),
                t.getBodyHtml(),
                t.getBodyText() != null ? t.getBodyText() : "",
                variables,
                t.getUpdatedAt());
    }
}
