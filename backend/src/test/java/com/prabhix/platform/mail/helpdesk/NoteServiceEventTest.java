package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.MailThreadEvent;
import com.prabhix.platform.mail.dto.ThreadDtos;
import com.prabhix.platform.mail.repository.MailThreadEventRepository;
import com.prabhix.platform.mail.repository.MailThreadNoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceEventTest {

    @Mock private MailThreadNoteRepository noteRepository;
    @Mock private MailThreadEventRepository eventRepository;

    @InjectMocks private NoteService noteService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID threadId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();

    /**
     * The timeline showed assignments and status changes and skipped the notes interleaved between
     * them, which is the context somebody reads a timeline for.
     */
    @Test
    void addingANoteEmitsNoteAdded() {
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        noteService.add(orgId, threadId, authorId,
                new ThreadDtos.CreateNoteRequest("<p>Rang the customer, no answer</p>"));

        ArgumentCaptor<MailThreadEvent> captor = ArgumentCaptor.forClass(MailThreadEvent.class);
        verify(eventRepository).save(captor.capture());
        MailThreadEvent event = captor.getValue();
        assertEquals(MailEnums.ThreadEventType.NOTE_ADDED, event.getEventType());
        assertEquals(threadId, event.getThreadId());
        assertEquals(authorId, event.getActorUserId());
    }

    /**
     * The body is deliberately not copied onto the event: notes are soft-deletable and events are not,
     * so duplicating the text would resurrect deleted notes in the timeline.
     */
    @Test
    void theEventDoesNotCarryTheNoteBody() {
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        noteService.add(orgId, threadId, authorId,
                new ThreadDtos.CreateNoteRequest("<p>Card ending 4242</p>"));

        ArgumentCaptor<MailThreadEvent> captor = ArgumentCaptor.forClass(MailThreadEvent.class);
        verify(eventRepository).save(captor.capture());
        assertNull(captor.getValue().getToValue());
        assertNull(captor.getValue().getFromValue());
    }
}
