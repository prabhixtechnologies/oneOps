package com.prabhix.platform.mail.web;

import com.prabhix.platform.mail.dto.SuppressionDtos;
import com.prabhix.platform.mail.outbound.SuppressionListService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mail/suppressions")
@RequiredArgsConstructor
public class SuppressionController {

    private final SuppressionListService suppressionListService;

    @GetMapping
    @PreAuthorize(Authorize.MAIL_SUPPRESSION_MANAGE)
    public List<SuppressionDtos.SuppressionResponse> list(@CurrentUser PrabhixPrincipal principal) {
        return suppressionListService.list(principal.requireOrganizationId());
    }

    @PostMapping
    @PreAuthorize(Authorize.MAIL_SUPPRESSION_MANAGE)
    public SuppressionDtos.SuppressionResponse add(@CurrentUser PrabhixPrincipal principal,
                                                   @Valid @RequestBody SuppressionDtos.CreateRequest request) {
        return suppressionListService.add(principal.requireOrganizationId(), request);
    }
}
