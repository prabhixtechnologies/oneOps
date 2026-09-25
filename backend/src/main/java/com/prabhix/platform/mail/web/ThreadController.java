package com.prabhix.platform.mail.web;

import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.mail.dto.TagDtos;
import com.prabhix.platform.mail.dto.ThreadDtos;
import com.prabhix.platform.mail.helpdesk.AssignmentService;
import com.prabhix.platform.mail.helpdesk.NoteService;
import com.prabhix.platform.mail.helpdesk.ReplyService;
import com.prabhix.platform.mail.helpdesk.TagService;
import com.prabhix.platform.mail.helpdesk.ThreadService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/oneops/mail/threads")
@RequiredArgsConstructor
public class ThreadController {

    private final ThreadService threadService;
    private final ReplyService replyService;
    private final AssignmentService assignmentService;
    private final NoteService noteService;
    private final TagService tagService;

    @GetMapping
    @PreAuthorize(Authorize.MAIL_READ)
    public CursorPage<ThreadDtos.ThreadSummary> list(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam(required = false) UUID mailboxId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) UUID assigneeUserId,
            @RequestParam(required = false) UUID assigneeTeamId,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false, defaultValue = "false") boolean hasAttachment,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        var query = new ThreadDtos.ThreadListQuery(
                mailboxId,
                status != null ? com.prabhix.platform.mail.domain.MailEnums.ThreadStatus.valueOf(status) : null,
                priority != null ? com.prabhix.platform.mail.domain.MailEnums.Priority.valueOf(priority) : null,
                assigneeUserId, assigneeTeamId, tagId, unreadOnly, hasAttachment, q, cursor, limit);
        return threadService.list(principal, query);
    }

    @GetMapping(params = "id")
    @PreAuthorize(Authorize.MAIL_READ)
    public ThreadDtos.ThreadDetail get(@CurrentUser PrabhixPrincipal principal, @RequestParam UUID id) {
        return threadService.get(principal, id);
    }

    @PatchMapping
    @PreAuthorize(Authorize.MAIL_THREAD_UPDATE)
    public ThreadDtos.ThreadSummary update(@CurrentUser PrabhixPrincipal principal,
                                           @RequestParam UUID id,
                                           @Valid @RequestBody ThreadDtos.UpdateThreadRequest request) {
        return threadService.update(principal, id, request);
    }

    @PostMapping("/bulk")
    @PreAuthorize(Authorize.MAIL_THREAD_UPDATE)
    public int bulkUpdate(@CurrentUser PrabhixPrincipal principal,
                          @Valid @RequestBody ThreadDtos.BulkUpdateRequest request) {
        return threadService.bulkUpdate(principal, request);
    }

    @PostMapping("/reply")
    @PreAuthorize(Authorize.MAIL_SEND)
    public ThreadDtos.MessageSummary reply(@CurrentUser PrabhixPrincipal principal,
                                           @RequestParam UUID id,
                                           @Valid @RequestBody ThreadDtos.ReplyRequest request) {
        return replyService.reply(principal, id, request);
    }

    @PostMapping("/assign")
    @PreAuthorize(Authorize.MAIL_ASSIGN)
    public void assign(@CurrentUser PrabhixPrincipal principal,
                       @RequestParam UUID id,
                       @Valid @RequestBody ThreadDtos.AssignRequest request) {
        UUID orgId = principal.requireOrganizationId();
        if (request.userId() != null) {
            assignmentService.assignToUser(orgId, id, request.userId(), principal.userId());
        } else if (request.teamId() != null) {
            assignmentService.assignToTeam(orgId, id, request.teamId(), principal.userId());
        }
    }

    @PostMapping("/unassign")
    @PreAuthorize(Authorize.MAIL_ASSIGN)
    public void unassign(@CurrentUser PrabhixPrincipal principal, @RequestParam UUID id) {
        assignmentService.unassign(principal.requireOrganizationId(), id);
    }

    @PostMapping("/notes")
    @PreAuthorize(Authorize.MAIL_NOTE_WRITE)
    public ThreadDtos.NoteSummary addNote(@CurrentUser PrabhixPrincipal principal,
                                          @RequestParam UUID id,
                                          @Valid @RequestBody ThreadDtos.CreateNoteRequest request) {
        return noteService.add(principal.requireOrganizationId(), id, principal.userId(), request);
    }

    @PostMapping("/tags")
    @PreAuthorize(Authorize.MAIL_THREAD_UPDATE)
    public void addTag(@CurrentUser PrabhixPrincipal principal,
                       @RequestParam UUID id,
                       @Valid @RequestBody TagDtos.ThreadTagRequest request) {
        tagService.addToThread(principal.requireOrganizationId(), id, request.tagId(), principal.userId());
    }

    @DeleteMapping("/tags")
    @PreAuthorize(Authorize.MAIL_THREAD_UPDATE)
    public void removeTag(@CurrentUser PrabhixPrincipal principal,
                          @RequestParam UUID id,
                          @RequestParam UUID tagId) {
        tagService.removeFromThread(principal.requireOrganizationId(), id, tagId, principal.userId());
    }

    @PostMapping("/tags/bulk")
    @PreAuthorize(Authorize.MAIL_THREAD_UPDATE)
    public int bulkTag(@CurrentUser PrabhixPrincipal principal,
                       @Valid @RequestBody TagDtos.BulkThreadTagRequest request) {
        return tagService.bulkAddToThreads(
                principal.requireOrganizationId(), request.threadIds(), request.tagId(), principal.userId());
    }
}
