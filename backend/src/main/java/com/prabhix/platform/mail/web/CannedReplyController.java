package com.prabhix.platform.mail.web;

import com.prabhix.platform.mail.dto.CannedReplyDtos;
import com.prabhix.platform.mail.helpdesk.CannedReplyService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mail/canned-replies")
@RequiredArgsConstructor
public class CannedReplyController {

    private final CannedReplyService cannedReplyService;

    @GetMapping
    @PreAuthorize(Authorize.MAIL_READ)
    public List<CannedReplyDtos.CannedReplyResponse> list(@CurrentUser PrabhixPrincipal principal) {
        return cannedReplyService.list(principal.requireOrganizationId());
    }

    @PostMapping
    @PreAuthorize(Authorize.MAIL_THREAD_UPDATE)
    public CannedReplyDtos.CannedReplyResponse create(@CurrentUser PrabhixPrincipal principal,
                                                      @Valid @RequestBody CannedReplyDtos.CreateRequest request) {
        return cannedReplyService.create(principal.requireOrganizationId(), request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Authorize.MAIL_THREAD_UPDATE)
    public CannedReplyDtos.CannedReplyResponse update(@CurrentUser PrabhixPrincipal principal,
                                                      @PathVariable UUID id,
                                                      @Valid @RequestBody CannedReplyDtos.UpdateRequest request) {
        return cannedReplyService.update(principal.requireOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Authorize.MAIL_THREAD_UPDATE)
    public ResponseEntity<Void> delete(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        cannedReplyService.delete(principal.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }
}
