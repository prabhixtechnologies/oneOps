package com.prabhix.platform.site.service;

import com.prabhix.platform.common.mail.MailClient;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.service.FileStorageService;
import com.prabhix.platform.site.domain.SiteEnums;
import com.prabhix.platform.site.domain.SiteJobApplication;
import com.prabhix.platform.site.domain.SiteJobRole;
import com.prabhix.platform.site.dto.SiteDtos.JobApplicationRequest;
import com.prabhix.platform.site.repository.SiteJobApplicationRepository;
import com.prabhix.platform.site.repository.SiteJobRoleRepository;
import com.prabhix.platform.site.repository.SiteLeadRepository;
import com.prabhix.platform.site.repository.SiteSubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteServiceResumeTest {

    @Mock private SiteLeadRepository leadRepository;
    @Mock private SiteSubscriberRepository subscriberRepository;
    @Mock private SiteJobRoleRepository jobRoleRepository;
    @Mock private SiteJobApplicationRepository applicationRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private ApplicationEventPublisher events;
    @Mock private MailClient mail;

    private SiteService siteService;

    private final UUID internalOrg = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @BeforeEach
    void setUp() {
        siteService = new SiteService(
                leadRepository,
                subscriberRepository,
                jobRoleRepository,
                applicationRepository,
                fileStorageService,
                events,
                mail,
                new PrabhixProperties(
                        new PrabhixProperties.Urls("http://localhost:3000", "http://localhost:5173",
                                "http://localhost:8080"),
                        null, null, null, null, null, null, null));
    }

    @Test
    void storesResumeWhenInternalOrgConfigured() throws Exception {
        ReflectionTestUtils.setField(siteService, "internalOrganizationId", internalOrg.toString());

        SiteJobRole role = openRole("engineer");
        when(jobRoleRepository.findBySlugAndStatus("engineer", SiteEnums.JobRoleStatus.OPEN))
                .thenReturn(Optional.of(role));
        when(applicationRepository.findByRoleSlugAndEmail(any(), any())).thenReturn(Optional.empty());

        StoredFile stored = new StoredFile();
        stored.setId(UUID.randomUUID());
        when(fileStorageService.store(any(), any(), any(), any(), any())).thenReturn(stored);

        MockMultipartFile resume = new MockMultipartFile(
                "resume", "cv.pdf", "application/pdf", new byte[] {1, 2, 3});
        JobApplicationRequest request = new JobApplicationRequest(
                "engineer", "Pat Lee", "pat@example.com", null, null, null, null);

        siteService.submitApplication(request, resume, "127.0.0.1");

        ArgumentCaptor<SiteJobApplication> captor = ArgumentCaptor.forClass(SiteJobApplication.class);
        verify(applicationRepository).save(captor.capture());
        assertEquals(stored.getId(), captor.getValue().getResumeFileId());
        assertNull(captor.getValue().getInternalNotes());
    }

    @Test
    void flagsApplicationWhenInternalOrgMissing() throws Exception {
        ReflectionTestUtils.setField(siteService, "internalOrganizationId", "");

        SiteJobRole role = openRole("designer");
        when(jobRoleRepository.findBySlugAndStatus("designer", SiteEnums.JobRoleStatus.OPEN))
                .thenReturn(Optional.of(role));
        when(applicationRepository.findByRoleSlugAndEmail(any(), any())).thenReturn(Optional.empty());

        MockMultipartFile resume = new MockMultipartFile(
                "resume", "cv.pdf", "application/pdf", new byte[] {9});
        JobApplicationRequest request = new JobApplicationRequest(
                "designer", "Sam Kim", "sam@example.com", null, null, null, "Hello");

        siteService.submitApplication(request, resume, "127.0.0.1");

        ArgumentCaptor<SiteJobApplication> captor = ArgumentCaptor.forClass(SiteJobApplication.class);
        verify(applicationRepository).save(captor.capture());
        assertNull(captor.getValue().getResumeFileId());
        assertNotNull(captor.getValue().getInternalNotes());
        assertEquals(SiteEnums.ApplicationStatus.RECEIVED, captor.getValue().getStatus());
    }

    private SiteJobRole openRole(String slug) {
        SiteJobRole role = new SiteJobRole();
        role.setId(UUID.randomUUID());
        role.setSlug(slug);
        role.setStatus(SiteEnums.JobRoleStatus.OPEN);
        return role;
    }
}
