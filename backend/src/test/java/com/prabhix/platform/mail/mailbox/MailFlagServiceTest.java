package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailThreadFlag;
import com.prabhix.platform.mail.repository.MailThreadFlagRepository;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailFlagServiceTest {

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID threadId = UUID.randomUUID();

    private MailThreadFlagRepository flagRepository;
    private MailThreadRepository threadRepository;
    private MailFolderService folders;
    private MailboxAccess access;
    private MailFlagService service;
    private PrabhixPrincipal principal;

    @BeforeEach
    void setUp() {
        flagRepository = mock(MailThreadFlagRepository.class);
        threadRepository = mock(MailThreadRepository.class);
        folders = mock(MailFolderService.class);
        access = mock(MailboxAccess.class);
        service = new MailFlagService(flagRepository, threadRepository, folders, access);

        principal = mock(PrabhixPrincipal.class);
        when(principal.requireOrganizationId()).thenReturn(orgId);
        when(principal.userId()).thenReturn(userId);
    }

    @Test
    @DisplayName("snoozed lists only active snoozes for the signed-in reader")
    void snoozedListsActive() {
        MailThreadFlag flag = MailThreadFlag.of(threadId, userId, orgId);
        flag.setSnoozedUntil(Instant.now().plus(1, ChronoUnit.HOURS));
        when(flagRepository.findActiveSnoozes(eq(orgId), eq(userId), any())).thenReturn(List.of(flag));
        when(folders.foldersFor(List.of(threadId))).thenReturn(Map.of(threadId, UUID.randomUUID()));

        MailThread thread = new MailThread();
        thread.setId(threadId);
        thread.setOrganizationId(orgId);
        thread.setSubject("Later");
        thread.setMessageCount(1);
        thread.setLastMessageAt(Instant.now());
        when(threadRepository.findAllById(List.of(threadId))).thenReturn(List.of(thread));

        var views = service.snoozed(principal);
        assertThat(views).hasSize(1);
        assertThat(views.getFirst().id()).isEqualTo(threadId);
        assertThat(views.getFirst().snoozedUntil()).isNotNull();
    }

    @Test
    @DisplayName("clearSnooze removes an active snooze without picking a new time")
    void clearSnooze() {
        MailThread thread = new MailThread();
        thread.setId(threadId);
        thread.setOrganizationId(orgId);
        when(access.requireThread(principal, threadId)).thenReturn(thread);

        MailThreadFlag flag = MailThreadFlag.of(threadId, userId, orgId);
        flag.setSnoozedUntil(Instant.now().plus(2, ChronoUnit.HOURS));
        when(flagRepository.findByIdThreadIdAndIdUserId(threadId, userId)).thenReturn(Optional.of(flag));
        when(flagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(folders.foldersFor(List.of(threadId))).thenReturn(Map.of(threadId, UUID.randomUUID()));

        var view = service.apply(principal, threadId, new MailboxDtos.FlagRequest(null, null, null, true));
        assertThat(view.snoozedUntil()).isNull();
        verify(flagRepository).save(flag);
        assertThat(flag.getSnoozedUntil()).isNull();
    }

    @Test
    @DisplayName("snoozeUntil must be in the future")
    void snoozeMustBeFuture() {
        MailThread thread = new MailThread();
        thread.setId(threadId);
        when(access.requireThread(principal, threadId)).thenReturn(thread);
        when(flagRepository.findByIdThreadIdAndIdUserId(threadId, userId)).thenReturn(Optional.empty());
        when(flagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant past = Instant.now().minus(1, ChronoUnit.MINUTES);
        assertThatThrownBy(() -> service.apply(
                principal, threadId, new MailboxDtos.FlagRequest(null, null, past, null)))
                .isInstanceOf(ApiException.class);
    }
}
