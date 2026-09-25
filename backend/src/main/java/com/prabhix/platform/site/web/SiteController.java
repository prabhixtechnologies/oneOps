package com.prabhix.platform.site.web;

import com.prabhix.platform.site.dto.SiteDtos.ApplicationAck;
import com.prabhix.platform.site.dto.SiteDtos.GenericAck;
import com.prabhix.platform.site.dto.SiteDtos.JobApplicationRequest;
import com.prabhix.platform.site.dto.SiteDtos.JobRoleDetail;
import com.prabhix.platform.site.dto.SiteDtos.JobRoleSummary;
import com.prabhix.platform.site.dto.SiteDtos.LeadRequest;
import com.prabhix.platform.site.dto.SiteDtos.SubscribeRequest;
import com.prabhix.platform.site.service.SiteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/oneops/site")
@RequiredArgsConstructor
public class SiteController {

    private final SiteService siteService;

    @PostMapping("/leads")
    public GenericAck submitLead(@Valid @RequestBody LeadRequest request, HttpServletRequest http) {
        return siteService.submitLead(request, clientIp(http), http.getHeader("User-Agent"));
    }

    @PostMapping("/subscribers")
    public GenericAck subscribe(@Valid @RequestBody SubscribeRequest request, HttpServletRequest http) {
        return siteService.subscribe(request, clientIp(http));
    }

    @GetMapping("/subscribers/confirm")
    public GenericAck confirm(@RequestParam String token) {
        return siteService.confirmSubscription(token);
    }

    @GetMapping("/subscribers/unsubscribe")
    public GenericAck unsubscribe(@RequestParam String token) {
        return siteService.unsubscribe(token);
    }

    @GetMapping("/careers")
    public List<JobRoleSummary> careers() {
        return siteService.listOpenRoles();
    }

    @GetMapping(value = "/careers", params = "slug")
    public JobRoleDetail career(@RequestParam String slug) {
        return siteService.getRole(slug);
    }

    @PostMapping(value = "/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApplicationAck apply(@Valid @RequestPart("application") JobApplicationRequest request,
                                @RequestPart(value = "resume", required = false) MultipartFile resume,
                                HttpServletRequest http) {
        return siteService.submitApplication(request, resume, clientIp(http));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
