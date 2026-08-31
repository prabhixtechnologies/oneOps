package com.prabhix.platform.common.mail;

import com.prabhix.platform.common.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailRequestRelayTest {

    @Mock MailRequestRepository requests;
    @Mock MailRequestHandler handler;
    @Mock ObjectProvider<MailRequestHandler> handlerProvider;

    private MailRequestRelay relay() {
        return new MailRequestRelay(requests, handlerProvider);
    }

    private MailRequestRecord pending() {
        MailRequestRecord row = new MailRequestRecord();
        row.setId(UUID.randomUUID());
        row.setTemplateKey("auth.magic-link");
        row.setToAddresses(Json.toJson(List.of("a@example.com")));
        row.setVariables(Json.toJson(Map.of("link", "https://example.com/l/1")));
        row.setMaxAttempts(3);
        return row;
    }

    @Test
    void deliversAndRecordsTheOutboxRowItBecame() {
        UUID outboxId = UUID.randomUUID();
        MailRequestRecord row = pending();

        when(handlerProvider.getIfAvailable()).thenReturn(handler);
        when(requests.claimPending(any(), anyInt())).thenReturn(new ArrayList<>(List.of(row)));
        when(requests.claimRetryable(any(), anyInt())).thenReturn(new ArrayList<>());
        when(handler.accept(any())).thenReturn(outboxId);

        relay().drain();

        ArgumentCaptor<MailRequest> sent = ArgumentCaptor.forClass(MailRequest.class);
        verify(handler).accept(sent.capture());
        assertEquals(List.of("a@example.com"), sent.getValue().to());
        assertEquals("auth.magic-link", sent.getValue().templateKey());

        assertEquals(MailRequestRecord.Status.DELIVERED, row.getStatus());
        assertEquals(outboxId, row.getOutboxId());
        assertNotNull(row.getDeliveredAt());
        assertNull(row.getLastError());
    }

    /**
     * A handler that throws must leave the row retryable, scheduled into the future.
     *
     * <p>This is the whole reason the request is written down. Under the Spring event it replaced,
     * a failure here happened after the caller's transaction had committed and on a thread nobody
     * was watching: the mail was simply never queued and nothing recorded that it should have been.
     */
    @Test
    void aFailedDeliveryIsRetriedWithBackoff() {
        MailRequestRecord row = pending();

        when(handlerProvider.getIfAvailable()).thenReturn(handler);
        when(requests.claimPending(any(), anyInt())).thenReturn(new ArrayList<>(List.of(row)));
        when(requests.claimRetryable(any(), anyInt())).thenReturn(new ArrayList<>());
        when(handler.accept(any())).thenThrow(new IllegalStateException("transport down"));

        relay().drain();

        assertEquals(MailRequestRecord.Status.FAILED, row.getStatus());
        assertEquals(1, row.getAttempts());
        assertEquals("transport down", row.getLastError());
        assertTrue(row.getNextAttemptAt().isAfter(Instant.now()));
    }

    /**
     * DEAD rather than deleted, and DEAD rather than retried forever. The row is the only record
     * that somebody is still waiting for an email that never arrived.
     */
    @Test
    void theLastAttemptIsDeadNotRetried() {
        MailRequestRecord row = pending();
        row.setAttempts(2);

        when(handlerProvider.getIfAvailable()).thenReturn(handler);
        when(requests.claimPending(any(), anyInt())).thenReturn(new ArrayList<>(List.of(row)));
        when(requests.claimRetryable(any(), anyInt())).thenReturn(new ArrayList<>());
        when(handler.accept(any())).thenThrow(new IllegalStateException("still down"));

        relay().drain();

        assertEquals(MailRequestRecord.Status.DEAD, row.getStatus());
        assertEquals(3, row.getAttempts());
    }

    /**
     * With nothing handling mail the rows must keep, untouched. Claiming them would strand them in
     * CLAIMED with no one to deliver them, and the release sweep would only churn.
     */
    @Test
    void withNoHandlerNothingIsClaimed() {
        when(handlerProvider.getIfAvailable()).thenReturn(null);

        relay().drain();

        verifyNoInteractions(requests);
        verify(handler, never()).accept(any());
    }
}
