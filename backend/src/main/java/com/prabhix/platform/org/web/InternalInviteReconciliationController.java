package com.prabhix.platform.org.web;

import com.prabhix.identity.client.ServiceTokenGuard;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.org.service.InviteUserReconciliationService;
import com.prabhix.platform.org.service.InviteUserReconciliationService.ApplyResult;
import com.prabhix.platform.org.service.InviteUserReconciliationService.DryRunReport;
import com.prabhix.platform.org.service.InviteUserReconciliationService.EmailDuplicateGroup;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/oneops/users/reconcile")
@RequiredArgsConstructor
public class InternalInviteReconciliationController {

    private final ServiceTokenGuard serviceToken;
    private final InviteUserReconciliationService reconciliation;

    @GetMapping("/dry-run")
    public DryRunReport dryRunAll(HttpServletRequest http) {
        requireServiceToken(http);
        return reconciliation.dryRun();
    }

    @PostMapping("/dry-run")
    public EmailDuplicateGroup dryRunOne(HttpServletRequest http, @RequestBody DryRunEmailRequest request) {
        requireServiceToken(http);
        return reconciliation.dryRunEmail(request.email());
    }

    @PostMapping("/apply")
    public ApplyResult apply(HttpServletRequest http, @RequestBody ApplyEmailRequest request) {
        requireServiceToken(http);
        return reconciliation.apply(request.email());
    }

    private void requireServiceToken(HttpServletRequest http) {
        if (!serviceToken.configured()) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "Invite reconciliation is not configured");
        }
        if (!serviceToken.permits(http)) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "Invalid service token");
        }
    }

    public record DryRunEmailRequest(@NotBlank @Email String email) {
    }

    public record ApplyEmailRequest(@NotBlank @Email String email) {
    }
}
