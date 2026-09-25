package com.prabhix.platform.site.web;

import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import com.prabhix.platform.site.dto.SiteAdminDtos;
import com.prabhix.platform.site.service.SiteAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/oneops/admin/site")
@RequiredArgsConstructor
public class SiteAdminController {

    private final SiteAdminService siteAdminService;

    @GetMapping("/leads")
    @PreAuthorize(Authorize.SITE_LEAD_READ)
    public CursorPage<SiteAdminDtos.LeadSummary> listLeads(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return siteAdminService.listLeads(status, cursor, limit);
    }

    @GetMapping(value = "/leads", params = "id")
    @PreAuthorize(Authorize.SITE_LEAD_READ)
    public SiteAdminDtos.LeadDetail getLead(@RequestParam UUID id) {
        return siteAdminService.getLead(id);
    }

    @PatchMapping("/leads")
    @PreAuthorize(Authorize.SITE_LEAD_MANAGE)
    public SiteAdminDtos.LeadDetail updateLead(@CurrentUser PrabhixPrincipal principal,
                                               @RequestParam UUID id,
                                               @Valid @RequestBody SiteAdminDtos.UpdateLeadStatusRequest request) {
        return siteAdminService.updateLeadStatus(id, principal.userId(), request);
    }

    @GetMapping("/subscribers")
    @PreAuthorize(Authorize.SITE_SUBSCRIBER_READ)
    public CursorPage<SiteAdminDtos.SubscriberSummary> listSubscribers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return siteAdminService.listSubscribers(status, cursor, limit);
    }

    @GetMapping(value = "/subscribers", params = "id")
    @PreAuthorize(Authorize.SITE_SUBSCRIBER_READ)
    public SiteAdminDtos.SubscriberSummary getSubscriber(@RequestParam UUID id) {
        return siteAdminService.getSubscriber(id);
    }

    @GetMapping("/applications")
    @PreAuthorize(Authorize.SITE_APPLICATION_READ)
    public CursorPage<SiteAdminDtos.ApplicationSummary> listApplications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return siteAdminService.listApplications(status, cursor, limit);
    }

    @GetMapping(value = "/applications", params = "id")
    @PreAuthorize(Authorize.SITE_APPLICATION_READ)
    public SiteAdminDtos.ApplicationDetail getApplication(@RequestParam UUID id) {
        return siteAdminService.getApplication(id);
    }

    @PatchMapping("/applications")
    @PreAuthorize(Authorize.SITE_APPLICATION_MANAGE)
    public SiteAdminDtos.ApplicationDetail updateApplication(@CurrentUser PrabhixPrincipal principal,
                                                             @RequestParam UUID id,
                                                             @Valid @RequestBody SiteAdminDtos.UpdateApplicationStatusRequest request) {
        return siteAdminService.updateApplicationStatus(id, principal.userId(), request);
    }
}
