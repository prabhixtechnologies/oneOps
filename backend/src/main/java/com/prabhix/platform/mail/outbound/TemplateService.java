package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mail.domain.MailTemplate;
import com.prabhix.platform.mail.dto.TemplateDtos;
import com.prabhix.platform.mail.repository.MailTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final MailTemplateRepository templateRepository;
    private final TemplateRenderer templateRenderer;

    @Transactional(readOnly = true)
    public List<TemplateDtos.TemplateResponse> list(UUID organizationId) {
        return templateRepository.findByOrganizationIdOrOrganizationIdIsNullOrderByTemplateKey(organizationId)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public TemplateDtos.PreviewResponse preview(String key, UUID organizationId,
                                                TemplateDtos.PreviewRequest request) {
        var resolved = templateRepository.resolveTemplate(key, "en", organizationId);
        if (resolved.isEmpty()) {
            throw ApiException.of(com.prabhix.platform.common.error.ErrorCode.MAIL_TEMPLATE_NOT_FOUND,
                    "Template not found");
        }
        var rendered = templateRenderer.preview(resolved.get(0), request.variables());
        return new TemplateDtos.PreviewResponse(rendered.subject(), rendered.bodyHtml(), rendered.bodyText());
    }

    private TemplateDtos.TemplateResponse toDto(MailTemplate t) {
        return new TemplateDtos.TemplateResponse(
                t.getId(), t.getTemplateKey(), t.getLocale(), t.getName(),
                t.getSubject(), t.getCategory(), t.isEnabled());
    }
}
