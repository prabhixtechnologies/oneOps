package com.prabhix.platform.org.web;

import com.prabhix.platform.org.dto.OrgDtos.PermissionCategory;
import com.prabhix.platform.org.dto.OrgDtos.PermissionEntry;
import com.prabhix.platform.security.rbac.Authorize;
import com.prabhix.platform.security.rbac.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

    @GetMapping
    @PreAuthorize(Authorize.ORG_ROLE_READ)
    public List<PermissionCategory> catalogue() {
        Map<String, List<PermissionEntry>> grouped = new LinkedHashMap<>();
        for (Permission permission : Permission.values()) {
            if (permission == Permission.PLATFORM_ADMIN) {
                continue;
            }
            String category = categoryOf(permission);
            grouped.computeIfAbsent(category, key -> new ArrayList<>())
                    .add(new PermissionEntry(permission.name(), permission.description()));
        }
        return grouped.entrySet().stream()
                .map(entry -> new PermissionCategory(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    private String categoryOf(Permission permission) {
        return switch (permission) {
            case ORG_READ, ORG_UPDATE, ORG_DELETE, ORG_MEMBER_READ, ORG_MEMBER_INVITE,
                 ORG_MEMBER_UPDATE, ORG_MEMBER_REMOVE, ORG_ROLE_READ, ORG_ROLE_MANAGE,
                 ORG_TEAM_READ, ORG_TEAM_MANAGE, ORG_API_KEY_MANAGE -> "ORGANIZATION";
            case MAIL_READ, MAIL_READ_ALL, MAIL_SEND, MAIL_ASSIGN, MAIL_THREAD_UPDATE,
                 MAIL_THREAD_DELETE, MAIL_NOTE_WRITE, MAIL_MAILBOX_READ, MAIL_MAILBOX_MANAGE,
                 MAIL_DOMAIN_READ, MAIL_DOMAIN_MANAGE, MAIL_TEMPLATE_READ, MAIL_TEMPLATE_MANAGE,
                 MAIL_SUPPRESSION_MANAGE -> "MAIL";
            case BILLING_READ, BILLING_MANAGE, BILLING_INVOICE_DOWNLOAD -> "BILLING";
            case FILE_READ, FILE_UPLOAD, FILE_DELETE -> "FILES";
            case AUDIT_READ -> "AUDIT";
            default -> "OTHER";
        };
    }
}
