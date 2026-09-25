package com.prabhix.platform.mail.web;

import com.prabhix.platform.mail.dto.TemplateDtos;
import com.prabhix.platform.mail.helpdesk.TemplateManagementService;
import com.prabhix.platform.mail.outbound.TemplateService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/oneops/mail/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final TemplateManagementService templateManagementService;

    @GetMapping
    @PreAuthorize(Authorize.MAIL_TEMPLATE_READ)
    public List<TemplateDtos.TemplateResponse> list(@CurrentUser PrabhixPrincipal principal) {
        return templateService.list(principal.requireOrganizationId());
    }

    @GetMapping(params = "key")
    @PreAuthorize(Authorize.MAIL_TEMPLATE_READ)
    public TemplateDtos.TemplateDetailResponse get(@CurrentUser PrabhixPrincipal principal,
                                                   @RequestParam String key) {
        return templateManagementService.get(key, principal.requireOrganizationId());
    }

    @PatchMapping
    @PreAuthorize(Authorize.MAIL_TEMPLATE_MANAGE)
    public TemplateDtos.TemplateDetailResponse update(@CurrentUser PrabhixPrincipal principal,
                                                      @RequestParam String key,
                                                      @Valid @RequestBody TemplateDtos.UpdateTemplateRequest request) {
        return templateManagementService.update(key, principal.requireOrganizationId(), request);
    }

    @PostMapping("/preview")
    @PreAuthorize(Authorize.MAIL_TEMPLATE_READ)
    public TemplateDtos.PreviewResponse preview(@CurrentUser PrabhixPrincipal principal,
                                                @RequestParam String key,
                                                @Valid @RequestBody TemplateDtos.PreviewRequest request) {
        return templateService.preview(key, principal.requireOrganizationId(), request);
    }
}
