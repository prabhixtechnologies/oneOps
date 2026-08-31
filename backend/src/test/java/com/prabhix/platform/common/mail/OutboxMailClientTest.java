package com.prabhix.platform.common.mail;

import com.prabhix.platform.common.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxMailClientTest {

    @Mock MailRequestRepository requests;
    @InjectMocks OutboxMailClient client;

    @Test
    void withoutADedupeKeyItSavesARow() {
        when(requests.save(any())).thenAnswer(invocation -> {
            MailRequestRecord saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        UUID organizationId = UUID.randomUUID();
        client.send(MailRequest.forOrganization(organizationId, "a@example.com",
                "org.invite", Map.of("link", "https://example.com/i/1"), null));

        ArgumentCaptor<MailRequestRecord> captor = ArgumentCaptor.forClass(MailRequestRecord.class);
        verify(requests).save(captor.capture());
        MailRequestRecord row = captor.getValue();

        assertEquals(organizationId, row.getOrganizationId());
        assertEquals("org.invite", row.getTemplateKey());
        assertEquals(MailRequestRecord.Status.PENDING, row.getStatus());
        assertEquals(List.of("a@example.com"), Json.parseStringList(row.getToAddresses()));
        assertEquals(Map.of("link", "https://example.com/i/1"), Json.parseMap(row.getVariables()));
    }

    /**
     * The insert has to be the conditional one, not a save.
     *
     * <p>A save would race: two instances doing the same work both find nothing, both insert, and
     * the loser hits the unique index — inside the caller's transaction, so the business work it was
     * attached to fails too. An invitation would be lost because the invitation email was sent
     * twice, which is the wrong way round.
     */
    @Test
    void aDedupeKeyGoesThroughTheConditionalInsert() {
        UUID inserted = UUID.randomUUID();
        when(requests.insertWithDedupe(any(), eq("auth.magic-link"), eq("en"), any(), any(),
                eq("magic:a@example.com:1"), anyInt(), anyInt())).thenReturn(inserted);

        UUID id = client.send(MailRequest.interactive("a@example.com", "auth.magic-link",
                Map.of(), "magic:a@example.com:1"));

        assertEquals(inserted, id);
        verify(requests, never()).save(any());
    }

    /**
     * Losing the race is a success, not an error. The caller asked for one email and there is one;
     * handing back the winner's id is what makes "asking twice is one email" true for both callers.
     */
    @Test
    void losingTheDedupeRaceReturnsTheExistingRequest() {
        MailRequestRecord existing = new MailRequestRecord();
        existing.setId(UUID.randomUUID());

        when(requests.insertWithDedupe(any(), any(), any(), any(), any(), eq("once"), anyInt(), anyInt()))
                .thenReturn(null);
        when(requests.findByDedupeKey("once")).thenReturn(Optional.of(existing));

        UUID id = client.send(MailRequest.interactive("a@example.com", "auth.otp", Map.of(), "once"));

        assertEquals(existing.getId(), id);
    }

    /**
     * A conflict with nothing to conflict against means the unique index and this code disagree
     * about what a duplicate is. Returning null instead would hand the caller an id for a request
     * that does not exist.
     */
    @Test
    void aConflictWithNoRowIsAnError() {
        when(requests.insertWithDedupe(any(), any(), any(), any(), any(), eq("ghost"), anyInt(), anyInt()))
                .thenReturn(null);
        when(requests.findByDedupeKey("ghost")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () ->
                client.send(MailRequest.interactive("a@example.com", "auth.otp", Map.of(), "ghost")));
    }
}
