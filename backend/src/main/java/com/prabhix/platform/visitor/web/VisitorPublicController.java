package com.prabhix.platform.visitor.web;

import com.prabhix.platform.visitor.dto.VisitorDtos;
import com.prabhix.platform.visitor.service.VisitorIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Visitor tracking (public)", description = "Unauthenticated marketing-site ingest")
@RestController
@RequestMapping("/api/v1/oneops/visitor/public")
@RequiredArgsConstructor
public class VisitorPublicController {

    private final VisitorIngestService ingestService;

    @PostMapping("/ingest")
    @Operation(summary = "Batch ingest page views and custom events")
    public VisitorDtos.IngestAck ingest(@RequestParam String orgSlug,
                                        @Valid @RequestBody VisitorDtos.BatchIngestRequest request,
                                        HttpServletRequest http) {
        return ingestService.ingest(orgSlug, request, clientIp(http), http.getHeader("User-Agent"));
    }

    @PostMapping("/identify")
    @Operation(summary = "Stitch anonymous visitor history to an identity")
    public void identify(@RequestParam String orgSlug,
                         @Valid @RequestBody VisitorDtos.IdentifyRequest request) {
        ingestService.identify(orgSlug, request);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
