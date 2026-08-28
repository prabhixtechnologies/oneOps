package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.mail.domain.MailTag;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailThreadTag;
import com.prabhix.platform.mail.dto.TagDtos;
import com.prabhix.platform.mail.repository.MailTagRepository;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.mail.repository.MailThreadTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TagService {

    private final MailTagRepository tagRepository;
    private final MailThreadTagRepository threadTagRepository;
    private final MailThreadRepository threadRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<TagDtos.TagResponse> list(UUID organizationId) {
        return tagRepository.findByOrganizationIdOrderByName(organizationId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public TagDtos.TagResponse create(UUID organizationId, TagDtos.CreateTagRequest request) {
        MailTag tag = new MailTag();
        tag.setOrganizationId(organizationId);
        tag.setSlug(request.slug() != null ? request.slug() : Ids.slug(request.name()));
        tag.setName(request.name());
        tag.setColour(request.colour() != null ? request.colour() : "#7C3AED");
        tag.setDescription(request.description());
        tag = tagRepository.save(tag);
        events.publishEvent(AuditRequested.labelled(organizationId, null,
                "mail.tag.created", "mail_tag", tag.getId(), tag.getName()));
        return toDto(tag);
    }

    @Transactional
    public TagDtos.TagResponse update(UUID organizationId, UUID tagId, TagDtos.UpdateTagRequest request) {
        MailTag tag = requireTag(organizationId, tagId);
        tag.setName(request.name());
        if (request.colour() != null) {
            tag.setColour(request.colour());
        }
        if (request.description() != null) {
            tag.setDescription(request.description());
        }
        tag = tagRepository.save(tag);
        events.publishEvent(AuditRequested.changed(organizationId, null,
                "mail.tag.updated", "mail_tag", tagId, Map.of("name", request.name())));
        return toDto(tag);
    }

    @Transactional
    public void delete(UUID organizationId, UUID tagId) {
        MailTag tag = requireTag(organizationId, tagId);
        threadTagRepository.deleteByIdTagId(tagId);
        tagRepository.delete(tag);
        events.publishEvent(AuditRequested.labelled(organizationId, null,
                "mail.tag.deleted", "mail_tag", tagId, tag.getName()));
    }

    @Transactional
    public void addToThread(UUID organizationId, UUID threadId, UUID tagId, UUID appliedBy) {
        requireThread(organizationId, threadId);
        requireTag(organizationId, tagId);
        if (!threadTagRepository.existsByIdThreadIdAndIdTagId(threadId, tagId)) {
            MailThreadTag tt = new MailThreadTag();
            tt.setId(new MailThreadTag.Id(threadId, tagId));
            tt.setOrganizationId(organizationId);
            tt.setAppliedBy(appliedBy);
            threadTagRepository.save(tt);
            refreshUsageCount(tagId);
            events.publishEvent(AuditRequested.of(organizationId, appliedBy,
                    "mail.thread.tag_added", "mail_thread", threadId));
        }
    }

    @Transactional
    public void removeFromThread(UUID organizationId, UUID threadId, UUID tagId, UUID actorId) {
        requireThread(organizationId, threadId);
        requireTag(organizationId, tagId);
        threadTagRepository.deleteByIdThreadIdAndIdTagId(threadId, tagId);
        refreshUsageCount(tagId);
        events.publishEvent(AuditRequested.of(organizationId, actorId,
                "mail.thread.tag_removed", "mail_thread", threadId));
    }

    @Transactional
    public int bulkAddToThreads(UUID organizationId, List<UUID> threadIds, UUID tagId, UUID appliedBy) {
        requireTag(organizationId, tagId);
        int applied = 0;
        for (UUID threadId : threadIds) {
            requireThread(organizationId, threadId);
            if (!threadTagRepository.existsByIdThreadIdAndIdTagId(threadId, tagId)) {
                MailThreadTag tt = new MailThreadTag();
                tt.setId(new MailThreadTag.Id(threadId, tagId));
                tt.setOrganizationId(organizationId);
                tt.setAppliedBy(appliedBy);
                threadTagRepository.save(tt);
                applied++;
            }
        }
        refreshUsageCount(tagId);
        if (applied > 0) {
            events.publishEvent(AuditRequested.of(organizationId, appliedBy,
                    "mail.thread.bulk_tagged", "mail_tag", tagId));
        }
        return applied;
    }

    private void refreshUsageCount(UUID tagId) {
        tagRepository.findById(tagId).ifPresent(tag -> {
            tag.setUsageCount((int) threadTagRepository.countByIdTagId(tagId));
            tagRepository.save(tag);
        });
    }

    private MailTag requireTag(UUID organizationId, UUID tagId) {
        return tagRepository.findByIdAndOrganizationId(tagId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Tag"));
    }

    private MailThread requireThread(UUID organizationId, UUID threadId) {
        return threadRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(threadId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Thread"));
    }

    private TagDtos.TagResponse toDto(MailTag tag) {
        return new TagDtos.TagResponse(tag.getId(), tag.getSlug(), tag.getName(),
                tag.getColour(), tag.getUsageCount());
    }
}
