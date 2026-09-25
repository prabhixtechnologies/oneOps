package com.prabhix.platform.mail.web;

import com.prabhix.platform.mail.dto.TagDtos;
import com.prabhix.platform.mail.helpdesk.TagService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/oneops/mail/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    @PreAuthorize(Authorize.MAIL_READ)
    public List<TagDtos.TagResponse> list(@CurrentUser PrabhixPrincipal principal) {
        return tagService.list(principal.requireOrganizationId());
    }

    @PostMapping
    @PreAuthorize(Authorize.MAIL_THREAD_UPDATE)
    public TagDtos.TagResponse create(@CurrentUser PrabhixPrincipal principal,
                                      @Valid @RequestBody TagDtos.CreateTagRequest request) {
        return tagService.create(principal.requireOrganizationId(), request);
    }

    @PatchMapping
    @PreAuthorize(Authorize.MAIL_THREAD_UPDATE)
    public TagDtos.TagResponse update(@CurrentUser PrabhixPrincipal principal,
                                      @RequestParam UUID id,
                                      @Valid @RequestBody TagDtos.UpdateTagRequest request) {
        return tagService.update(principal.requireOrganizationId(), id, request);
    }

    @DeleteMapping
    @PreAuthorize(Authorize.MAIL_THREAD_UPDATE)
    public ResponseEntity<Void> delete(@CurrentUser PrabhixPrincipal principal, @RequestParam UUID id) {
        tagService.delete(principal.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }
}
