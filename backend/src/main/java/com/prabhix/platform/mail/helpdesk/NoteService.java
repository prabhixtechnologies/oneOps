package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.MailThreadEvent;
import com.prabhix.platform.mail.domain.MailThreadNote;
import com.prabhix.platform.mail.dto.ThreadDtos;
import com.prabhix.platform.mail.inbound.MimeParser;
import com.prabhix.platform.mail.repository.MailThreadEventRepository;
import com.prabhix.platform.mail.repository.MailThreadNoteRepository;
import com.prabhix.platform.common.util.Json;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class NoteService {

    private static final Pattern MENTION = Pattern.compile("@\\[([a-f0-9-]{36})]");

    private final MailThreadNoteRepository noteRepository;
    private final MailThreadEventRepository eventRepository;

    @Transactional
    public ThreadDtos.NoteSummary add(UUID organizationId, UUID threadId, UUID authorUserId,
                                      ThreadDtos.CreateNoteRequest request) {
        MailThreadNote note = new MailThreadNote();
        note.setOrganizationId(organizationId);
        note.setThreadId(threadId);
        note.setAuthorUserId(authorUserId);
        note.setBodyHtml(request.bodyHtml());
        note.setBodyText(MimeParser.htmlToText(request.bodyHtml()));
        note.setMentionedUsers(Json.toJson(extractMentions(request.bodyHtml())));
        note = noteRepository.save(note);

        // NOTE_ADDED existed in the event enum and was never emitted, so the activity timeline showed
        // assignments and status changes but silently skipped the notes interleaved between them --
        // which is exactly the context somebody reads a timeline for.
        //
        // The note body is not copied into the event. It is already persisted, notes are soft-deletable
        // and events are not, so duplicating the text here would resurrect deleted notes in the
        // timeline.
        MailThreadEvent event = new MailThreadEvent();
        event.setOrganizationId(organizationId);
        event.setThreadId(threadId);
        event.setEventType(MailEnums.ThreadEventType.NOTE_ADDED);
        event.setActorUserId(authorUserId);
        eventRepository.save(event);

        return new ThreadDtos.NoteSummary(note.getId(), note.getAuthorUserId(),
                note.getBodyHtml(), note.getCreatedAt());
    }

    List<UUID> extractMentions(String html) {
        List<UUID> mentioned = new ArrayList<>();
        Matcher matcher = MENTION.matcher(html);
        while (matcher.find()) {
            try {
                mentioned.add(UUID.fromString(matcher.group(1)));
            } catch (IllegalArgumentException ignored) {
                // skip invalid UUIDs
            }
        }
        return mentioned;
    }
}
