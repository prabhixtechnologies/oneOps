package com.prabhix.platform.visitor.web;

import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import com.prabhix.platform.visitor.dto.VisitorDtos;
import com.prabhix.platform.visitor.service.VisitorQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Visitors", description = "Authenticated visitor analytics and live presence")
@RestController
@RequestMapping("/api/v1/visitors")
@RequiredArgsConstructor
public class VisitorController {

    private final VisitorQueryService queryService;

    @GetMapping("/live")
    @PreAuthorize(Authorize.VISITOR_READ)
    @Operation(summary = "List visitors currently on the site")
    public List<VisitorDtos.LiveVisitor> live(@CurrentUser PrabhixPrincipal principal) {
        return queryService.live(principal);
    }

    @GetMapping
    @PreAuthorize(Authorize.VISITOR_READ)
    public CursorPage<VisitorDtos.VisitorSummary> list(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return queryService.list(principal, cursor, limit);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Authorize.VISITOR_READ)
    public VisitorDtos.VisitorDetail get(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        return queryService.get(principal, id);
    }

    @GetMapping("/{id}/page-views")
    @PreAuthorize(Authorize.VISITOR_READ)
    public CursorPage<VisitorDtos.PageViewView> pageViews(
            @CurrentUser PrabhixPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return queryService.pageViews(principal, id, cursor, limit);
    }

    @GetMapping("/{id}/events")
    @PreAuthorize(Authorize.VISITOR_READ)
    public CursorPage<VisitorDtos.EventView> events(
            @CurrentUser PrabhixPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return queryService.events(principal, id, cursor, limit);
    }

    @GetMapping("/analytics/summary")
    @PreAuthorize(Authorize.VISITOR_ANALYTICS)
    public VisitorDtos.AnalyticsSummary analytics(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam(defaultValue = "30") int days) {
        return queryService.analytics(principal, days);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Authorize.VISITOR_MANAGE)
    public void delete(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        queryService.deleteVisitorData(principal, id);
    }
}
