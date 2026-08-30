package com.prabhix.platform.mail.helpdesk;

import tools.jackson.databind.JsonNode;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.spi.EntitlementGate;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailRoutingRule;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.domain.MailboxMember;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.dto.MailboxDtos;
import com.prabhix.platform.mail.provisioning.MailboxCredentialsCipher;
import com.prabhix.platform.mail.repository.MailRoutingRuleRepository;
import com.prabhix.platform.mail.repository.MailboxMemberRepository;
import com.prabhix.platform.mail.repository.MailboxRepository;
import com.prabhix.platform.mail.util.MailJson;
import com.prabhix.platform.org.domain.OrganizationMembership;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MailboxService {

    private final MailboxRepository mailboxRepository;
    private final MailboxMemberRepository memberRepository;
    private final MailRoutingRuleRepository routingRuleRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final PrabhixProperties properties;
    private final EntitlementGate entitlements;
    private final MailboxCredentialsCipher credentialsCipher;

    @Transactional(readOnly = true)
    public List<MailboxDtos.MailboxResponse> list(UUID organizationId) {
        return mailboxRepository.findByOrganizationIdAndDeletedAtIsNullOrderByName(organizationId)
                .stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public MailboxDtos.MailboxDetailResponse get(UUID organizationId, UUID mailboxId) {
        Mailbox mailbox = requireMailbox(organizationId, mailboxId);
        return toDetail(mailbox);
    }

    @Transactional
    public MailboxDtos.MailboxResponse create(UUID organizationId, MailboxDtos.CreateMailboxRequest request) {
        long count = mailboxRepository.countByOrganizationIdAndDeletedAtIsNull(organizationId);
        if (count >= properties.limits().maxMailboxesPerOrganization()) {
            throw ApiException.of(com.prabhix.platform.common.error.ErrorCode.LIMIT_EXCEEDED,
                    "Mailbox limit reached");
        }
        // The check above is the platform-wide ceiling; this one is what the plan actually sold.
        entitlements.requireQuota(organizationId, "mailboxes", count);
        if (mailboxRepository.findByAddressIgnoreCaseAndDeletedAtIsNull(request.address()).isPresent()) {
            throw ApiException.conflict("That address is already in use");
        }
        Mailbox mailbox = new Mailbox();
        mailbox.setOrganizationId(organizationId);
        mailbox.setAddress(request.address().toLowerCase());
        mailbox.setName(request.name());
        mailbox.setKind(request.kind() != null ? request.kind() : MailEnums.MailboxKind.SHARED);
        mailbox.setDescription(request.description());
        applyCredentialUpdates(mailbox, request.imapPassword(), request.smtpPassword());
        return toSummary(mailboxRepository.save(mailbox));
    }

    @Transactional
    public MailboxDtos.MailboxDetailResponse update(UUID organizationId, UUID mailboxId,
                                                    MailboxDtos.UpdateMailboxRequest request) {
        Mailbox mailbox = requireMailbox(organizationId, mailboxId);
        if (request.name() != null && !request.name().isBlank()) {
            mailbox.setName(request.name().trim());
        }
        if (request.description() != null) {
            mailbox.setDescription(request.description().isBlank() ? null : request.description().trim());
        }
        if (request.signature() != null) {
            mailbox.setSignatureHtml(request.signature().isBlank() ? null : request.signature());
        }
        if (request.businessHours() != null) {
            mailbox.setBusinessHours(MailJson.toJson(toBusinessHoursMap(request.businessHours())));
            mailbox.setTimezone(request.businessHours().timezone());
        }
        applyCredentialUpdates(mailbox, request.imapPassword(), request.smtpPassword());
        return toDetail(mailboxRepository.save(mailbox));
    }

    @Transactional
    public void delete(UUID organizationId, UUID mailboxId) {
        Mailbox mailbox = requireMailbox(organizationId, mailboxId);
        mailbox.setDeletedAt(Instant.now());
        mailbox.setStatus(MailEnums.MailboxStatus.ARCHIVED);
        mailboxRepository.save(mailbox);
    }

    @Transactional
    public MailboxDtos.MailboxMemberResponse addMember(UUID organizationId, UUID mailboxId,
                                                       MailboxDtos.AddMailboxMemberRequest request) {
        requireMailbox(organizationId, mailboxId);
        membershipRepository.findByOrganizationIdAndUserId(organizationId, request.userId())
                .orElseThrow(() -> ApiException.notFound("Member"));

        if (memberRepository.existsByMailboxIdAndUserId(mailboxId, request.userId())) {
            throw ApiException.of(com.prabhix.platform.common.error.ErrorCode.ALREADY_EXISTS,
                    "That user is already a mailbox member");
        }

        MailboxMember member = new MailboxMember();
        member.setOrganizationId(organizationId);
        member.setMailboxId(mailboxId);
        member.setUserId(request.userId());
        member.setAccessLevel(request.accessLevel() != null
                ? request.accessLevel() : MailEnums.MemberAccessLevel.MEMBER);
        memberRepository.save(member);

        OrganizationMembership orgMember = membershipRepository
                .findByOrganizationIdAndUserId(organizationId, request.userId())
                .orElseThrow();
        return new MailboxDtos.MailboxMemberResponse(
                request.userId(), orgMember.getDisplayName(), orgMember.getEmail());
    }

    @Transactional
    public void removeMember(UUID organizationId, UUID mailboxId, UUID userId) {
        requireMailbox(organizationId, mailboxId);
        MailboxMember member = memberRepository.findByMailboxIdAndUserId(mailboxId, userId)
                .orElseThrow(() -> ApiException.notFound("Mailbox member"));
        memberRepository.delete(member);
    }

    private void applyCredentialUpdates(Mailbox mailbox, String imapPassword, String smtpPassword) {
        if (imapPassword != null && !imapPassword.isBlank()) {
            credentialsCipher.storeImapPassword(mailbox, imapPassword);
        }
        if (smtpPassword != null && !smtpPassword.isBlank()) {
            credentialsCipher.storeSmtpPassword(mailbox, smtpPassword);
        }
    }

    private Mailbox requireMailbox(UUID organizationId, UUID mailboxId) {
        return mailboxRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(mailboxId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Mailbox"));
    }

    private MailboxDtos.MailboxDetailResponse toDetail(Mailbox mailbox) {
        List<MailboxMember> members = memberRepository.findByMailboxId(mailbox.getId());
        Set<UUID> userIds = members.stream()
                .map(MailboxMember::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Map<UUID, OrganizationMembership> memberships = membershipRepository
                .findByOrganizationIdAndUserIdIn(mailbox.getOrganizationId(), userIds).stream()
                .collect(Collectors.toMap(OrganizationMembership::getUserId, m -> m, (a, b) -> a));

        List<MailboxDtos.MailboxMemberResponse> memberViews = members.stream()
                .filter(m -> m.getUserId() != null)
                .map(m -> {
                    OrganizationMembership orgMember = memberships.get(m.getUserId());
                    return new MailboxDtos.MailboxMemberResponse(
                            m.getUserId(),
                            orgMember != null ? orgMember.getDisplayName() : "Unknown",
                            orgMember != null ? orgMember.getEmail() : "");
                })
                .toList();

        List<MailRoutingRule> rules = routingRuleRepository.findByOrganizationIdAndMailboxIdOrderByPriorityAsc(
                mailbox.getOrganizationId(), mailbox.getId());

        return new MailboxDtos.MailboxDetailResponse(
                mailbox.getId(),
                mailbox.getName(),
                mailbox.getAddress(),
                mailbox.getDescription(),
                members.size(),
                mailbox.getOpenThreadCount(),
                mailbox.getSlaFirstResponseMins() != null
                        ? String.valueOf(mailbox.getSlaFirstResponseMins()) : null,
                mailbox.getSignatureHtml(),
                mailbox.getCreatedAt(),
                mailbox.getPasswordUpdatedAt(),
                memberViews,
                rules.stream().map(this::toRoutingRule).toList(),
                parseBusinessHours(mailbox));
    }

    private MailboxDtos.RoutingRuleResponse toRoutingRule(MailRoutingRule rule) {
        return new MailboxDtos.RoutingRuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getPriority(),
                MailJson.parseObjectList(rule.getConditions()),
                rule.getMatchMode().name(),
                MailJson.parseObjectList(rule.getActions()),
                rule.isContinueAfterMatch(),
                rule.isEnabled());
    }

    private MailboxDtos.BusinessHoursResponse parseBusinessHours(Mailbox mailbox) {
        JsonNode hours = MailJson.mapper().valueToTree(MailJson.parseMap(mailbox.getBusinessHours()));
        if (hours.isEmpty()) {
            return null;
        }
        List<Integer> workingDays = new ArrayList<>();
        String startTime = "09:00";
        String endTime = "18:00";
        List<String> holidays = new ArrayList<>();

        // propertyNames(), not fieldNames(): renamed in Jackson 3, and it hands back a Collection
        // rather than an Iterator.
        for (String day : hours.propertyNames()) {
            if ("holidays".equals(day) && hours.get(day).isArray()) {
                hours.get(day).forEach(n -> holidays.add(n.asText()));
                continue;
            }
            JsonNode dayConfig = hours.get(day);
            if (dayConfig != null && dayConfig.has("start") && dayConfig.has("end")) {
                workingDays.add(dayOfWeekNumber(day));
                startTime = dayConfig.get("start").asText();
                endTime = dayConfig.get("end").asText();
            }
        }

        if (workingDays.isEmpty()) {
            return new MailboxDtos.BusinessHoursResponse(
                    mailbox.getTimezone(), List.of(1, 2, 3, 4, 5),
                    "09:00", "18:00", holidays);
        }
        return new MailboxDtos.BusinessHoursResponse(
                mailbox.getTimezone(), workingDays, startTime, endTime, holidays);
    }

    private int dayOfWeekNumber(String day) {
        return switch (day.toLowerCase()) {
            case "monday" -> 1;
            case "tuesday" -> 2;
            case "wednesday" -> 3;
            case "thursday" -> 4;
            case "friday" -> 5;
            case "saturday" -> 6;
            case "sunday" -> 7;
            default -> 1;
        };
    }

    private Map<String, Object> toBusinessHoursMap(MailboxDtos.BusinessHoursResponse hours) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (Integer day : hours.workingDays()) {
            String key = dayName(day);
            map.put(key, Map.of("start", hours.startTime(), "end", hours.endTime()));
        }
        map.put("holidays", hours.holidays());
        return map;
    }

    private String dayName(int day) {
        return switch (day) {
            case 1 -> "monday";
            case 2 -> "tuesday";
            case 3 -> "wednesday";
            case 4 -> "thursday";
            case 5 -> "friday";
            case 6 -> "saturday";
            case 7 -> "sunday";
            default -> "monday";
        };
    }

    private MailboxDtos.MailboxResponse toSummary(Mailbox m) {
        return new MailboxDtos.MailboxResponse(
                m.getId(), m.getAddress(), m.getName(), m.getKind(), m.getStatus(),
                m.getOpenThreadCount(), m.getUnassignedCount());
    }
}
