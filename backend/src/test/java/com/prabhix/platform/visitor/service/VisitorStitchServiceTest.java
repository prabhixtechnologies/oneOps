package com.prabhix.platform.visitor.service;

import com.prabhix.platform.visitor.domain.Visitor;
import com.prabhix.platform.visitor.domain.VisitorEnums;
import com.prabhix.platform.visitor.repository.VisitorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitorStitchServiceTest {

    @Mock private VisitorRepository visitorRepository;

    private VisitorStitchService stitchService;

    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        stitchService = new VisitorStitchService(visitorRepository);
    }

    @Test
    void identifyStitchesEmailOntoExistingVisitor() {
        Visitor visitor = new Visitor();
        visitor.setId(UUID.randomUUID());
        visitor.setOrganizationId(orgId);
        visitor.setExternalKey("abc");
        visitor.setConsentStatus(VisitorEnums.ConsentStatus.FULL);

        when(visitorRepository.findByOrganizationIdAndExternalKeyAndDeletedAtIsNull(orgId, "abc"))
                .thenReturn(Optional.of(visitor));
        when(visitorRepository.findFirstByOrganizationIdAndEmailIgnoreCaseAndDeletedAtIsNullAndMergedIntoIdIsNull(
                orgId, "jane@example.com")).thenReturn(Optional.empty());
        when(visitorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Visitor result = stitchService.identify(orgId, "abc", "jane@example.com", "Jane", null);

        assertEquals("jane@example.com", result.getEmail());
        assertNotNull(result.getIdentifiedAt());
    }

    @Test
    void identifyMergesDuplicateEmailVisitors() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        Visitor source = new Visitor();
        source.setId(sourceId);
        source.setOrganizationId(orgId);
        source.setExternalKey("new-key");

        Visitor target = new Visitor();
        target.setId(targetId);
        target.setOrganizationId(orgId);
        target.setExternalKey("old-key");
        target.setEmail("jane@example.com");

        when(visitorRepository.findByOrganizationIdAndExternalKeyAndDeletedAtIsNull(orgId, "new-key"))
                .thenReturn(Optional.of(source));
        when(visitorRepository.findFirstByOrganizationIdAndEmailIgnoreCaseAndDeletedAtIsNullAndMergedIntoIdIsNull(
                orgId, "jane@example.com")).thenReturn(Optional.of(target));
        when(visitorRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(visitorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        stitchService.identify(orgId, "new-key", "jane@example.com", "Jane", null);

        verify(visitorRepository).reassignSessions(orgId, sourceId, targetId);
        verify(visitorRepository).reassignPageViews(orgId, sourceId, targetId);
        verify(visitorRepository).reassignEvents(orgId, sourceId, targetId);
        verify(visitorRepository).mergeVisitor(orgId, sourceId, targetId);
    }

    /**
     * Callers persist the returned id against a conversation. Returning the merged-away row
     * would attach the conversation to a tombstone whose sessions, page views, and events had
     * just been reassigned, so the agent would see an empty browsing history.
     */
    @Test
    void identifyReturnsTheSurvivingVisitorAfterAMerge() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        Visitor source = new Visitor();
        source.setId(sourceId);
        source.setOrganizationId(orgId);
        source.setExternalKey("new-key");

        Visitor target = new Visitor();
        target.setId(targetId);
        target.setOrganizationId(orgId);
        target.setExternalKey("old-key");
        target.setEmail("jane@example.com");

        when(visitorRepository.findByOrganizationIdAndExternalKeyAndDeletedAtIsNull(orgId, "new-key"))
                .thenReturn(Optional.of(source));
        when(visitorRepository.findFirstByOrganizationIdAndEmailIgnoreCaseAndDeletedAtIsNullAndMergedIntoIdIsNull(
                orgId, "jane@example.com")).thenReturn(Optional.of(target));
        when(visitorRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(visitorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Visitor result = stitchService.identify(orgId, "new-key", "jane@example.com", "Jane", null);

        assertEquals(targetId, result.getId(), "must return the surviving visitor, not the tombstone");
        assertEquals("Jane", result.getDisplayName());
        assertNotNull(result.getIdentifiedAt());

        ArgumentCaptor<Visitor> saved = ArgumentCaptor.forClass(Visitor.class);
        verify(visitorRepository).save(saved.capture());
        assertEquals(targetId, saved.getValue().getId(), "the tombstone must not be written back");
    }

    /** A name arriving as blank must not erase a name the visitor already gave us. */
    @Test
    void identifyDoesNotOverwriteAKnownNameWithABlankOne() {
        Visitor visitor = new Visitor();
        visitor.setId(UUID.randomUUID());
        visitor.setOrganizationId(orgId);
        visitor.setExternalKey("abc");
        visitor.setDisplayName("Ramesh Kumar");

        when(visitorRepository.findByOrganizationIdAndExternalKeyAndDeletedAtIsNull(orgId, "abc"))
                .thenReturn(Optional.of(visitor));
        when(visitorRepository.findFirstByOrganizationIdAndEmailIgnoreCaseAndDeletedAtIsNullAndMergedIntoIdIsNull(
                orgId, "ramesh@example.com")).thenReturn(Optional.empty());
        when(visitorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Visitor result = stitchService.identify(orgId, "abc", "ramesh@example.com", "  ", null);

        assertEquals("Ramesh Kumar", result.getDisplayName());
    }

    /** Email is stored canonically so the duplicate lookup can rely on it. */
    @Test
    void identifyNormalizesEmailCase() {
        Visitor visitor = new Visitor();
        visitor.setId(UUID.randomUUID());
        visitor.setOrganizationId(orgId);
        visitor.setExternalKey("abc");

        when(visitorRepository.findByOrganizationIdAndExternalKeyAndDeletedAtIsNull(orgId, "abc"))
                .thenReturn(Optional.of(visitor));
        when(visitorRepository.findFirstByOrganizationIdAndEmailIgnoreCaseAndDeletedAtIsNullAndMergedIntoIdIsNull(
                eq(orgId), eq("jane@example.com"))).thenReturn(Optional.empty());
        when(visitorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Visitor result = stitchService.identify(orgId, "abc", "Jane@Example.COM", "Jane", null);

        assertEquals("jane@example.com", result.getEmail());
    }
}
