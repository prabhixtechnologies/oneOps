package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.mail.domain.MailThreadNote;
import com.prabhix.platform.mail.dto.ThreadDtos;
import com.prabhix.platform.mail.inbound.MimeParser;
import com.prabhix.platform.mail.repository.MailThreadNoteRepository;
import com.prabhix.platform.mail.util.MailJson;
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

    @Transactional
    public ThreadDtos.NoteSummary add(UUID organizationId, UUID threadId, UUID authorUserId,
                                      ThreadDtos.CreateNoteRequest request) {
        MailThreadNote note = new MailThreadNote();
        note.setOrganizationId(organizationId);
        note.setThreadId(threadId);
        note.setAuthorUserId(authorUserId);
        note.setBodyHtml(request.bodyHtml());
        note.setBodyText(MimeParser.htmlToText(request.bodyHtml()));
        note.setMentionedUsers(MailJson.toJson(extractMentions(request.bodyHtml())));
        note = noteRepository.save(note);
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
