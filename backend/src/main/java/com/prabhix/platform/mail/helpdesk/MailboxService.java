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
import com.prabhix.platform.org.domain.Team;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.repository.TeamRepository;
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
    private final TeamRepository teamRepository;
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
        // Zero clears the target rather than promising a reply in no time at all. Null still means
        // "leave alone", which is what every other field here means and what a PATCH should.
        if (request.slaFirstResponseMins() != null) {
            mailbox.setSlaFirstResponseMins(
                    request.slaFirstResponseMins() == 0 ? null : request.slaFirstResponseMins());
        }
        if (request.slaResolutionMins() != null) {
            mailbox.setSlaResolutionMins(
                    request.slaResolutionMins() == 0 ? null : request.slaResolutionMins());
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

    /**
     * Grants a mailbox to a person or to a team.
     *
     * <p>The team half is new to the API and not to the schema: {@code team_id} has always been on the
     * row, and mailbox access resolution reads team grants, so the feature was fully built and had no
     * door. Either subject is validated against the organization before the grant is written, so a
     * team from another tenant cannot be named.
     */
    @Transactional
    public MailboxDtos.MailboxMemberResponse addMember(UUID organizationId, UUID mailboxId,
                                                       MailboxDtos.AddMailboxMemberRequest request) {
        requireMailbox(organizationId, mailboxId);

        MailboxMember member = new MailboxMember();
        member.setOrganizationId(organizationId);
        member.setMailboxId(mailboxId);
        member.setAccessLevel(request.accessLevel() != null
                ? request.accessLevel() : MailEnums.MemberAccessLevel.MEMBER);

        if (request.userId() != null) {
            membershipRepository.findByOrganizationIdAndUserId(organizationId, request.userId())
                    .orElseThrow(() -> ApiException.notFound("Member"));
            if (memberRepository.existsByMailboxIdAndUserId(mailboxId, request.userId())) {
                throw ApiException.of(com.prabhix.platform.common.error.ErrorCode.ALREADY_EXISTS,
                        "That user is already a mailbox member");
            }
            member.setUserId(request.userId());
        } else {
            Team team = teamRepository.findById(request.teamId())
                    .filter(t -> organizationId.equals(t.getOrganizationId()))
                    .orElseThrow(() -> ApiException.notFound("Team"));
            if (memberRepository.existsByMailboxIdAndTeamId(mailboxId, team.getId())) {
                throw ApiException.of(com.prabhix.platform.common.error.ErrorCode.ALREADY_EXISTS,
                        "That team already has access to this mailbox");
            }
            member.setTeamId(team.getId());
        }

        return toMemberView(memberRepository.save(member), organizationId);
    }

    @Transactional
    public MailboxDtos.MailboxMemberResponse updateMember(UUID organizationId, UUID mailboxId, UUID memberId,
                                                          MailboxDtos.UpdateMailboxMemberRequest request) {
        requireMailbox(organizationId, mailboxId);
        MailboxMember member = requireMember(organizationId, mailboxId, memberId);
        member.setAccessLevel(request.accessLevel());
        return toMemberView(memberRepository.save(member), organizationId);
    }

    /**
     * Revokes one grant, named by its row id.
     *
     * <p>Was keyed on the user id, which could not name a team grant at all. Callers that hold a user
     * id take the {@code byUser} overload, which is what the older route still does.
     */
    @Transactional
    public void removeMember(UUID organizationId, UUID mailboxId, UUID memberId) {
        requireMailbox(organizationId, mailboxId);
        memberRepository.delete(requireMember(organizationId, mailboxId, memberId));
    }

    @Transactional
    public void removeMemberByUser(UUID organizationId, UUID mailboxId, UUID userId) {
        requireMailbox(organizationId, mailboxId);
        MailboxMember member = memberRepository.findByMailboxIdAndUserId(mailboxId, userId)
                .orElseThrow(() -> ApiException.notFound("Mailbox member"));
        memberRepository.delete(member);
    }

    private MailboxMember requireMember(UUID organizationId, UUID mailboxId, UUID memberId) {
        return memberRepository.findByIdAndMailboxIdAndOrganizationId(memberId, mailboxId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Mailbox member"));
    }

    // ---------------------------------------------------------------------------------------------
    // Routing rules
    // ---------------------------------------------------------------------------------------------

    /**
     * Creates a routing rule on a mailbox.
     *
     * <p>The engine that evaluates these has always been there and so has the table; only the way to
     * write one was missing, so every inbound mail landed unassigned, untagged and without an SLA
     * unless somebody edited the database by hand.
     *
     * <p>Conditions and actions are validated against the shapes the engine understands before the
     * row is written. A rule with a misspelled field name is not a rule — it is a rule that silently
     * never matches, which is the hardest kind of configuration to debug.
     */
    @Transactional
    public MailboxDtos.RoutingRuleResponse createRoutingRule(UUID organizationId, UUID mailboxId,
                                                              MailboxDtos.SaveRoutingRuleRequest request) {
        requireMailbox(organizationId, mailboxId);
        RoutingRuleValidator.validate(request);

        MailRoutingRule rule = new MailRoutingRule();
        rule.setOrganizationId(organizationId);
        rule.setMailboxId(mailboxId);
        applyRoutingRule(rule, request);
        return toRoutingRule(routingRuleRepository.save(rule));
    }

    @Transactional
    public MailboxDtos.RoutingRuleResponse updateRoutingRule(UUID organizationId, UUID mailboxId, UUID ruleId,
                                                             MailboxDtos.SaveRoutingRuleRequest request) {
        requireMailbox(organizationId, mailboxId);
        RoutingRuleValidator.validate(request);

        MailRoutingRule rule = requireRoutingRule(organizationId, mailboxId, ruleId);
        applyRoutingRule(rule, request);
        return toRoutingRule(routingRuleRepository.save(rule));
    }

    @Transactional
    public void deleteRoutingRule(UUID organizationId, UUID mailboxId, UUID ruleId) {
        requireMailbox(organizationId, mailboxId);
        routingRuleRepository.delete(requireRoutingRule(organizationId, mailboxId, ruleId));
    }

    private MailRoutingRule requireRoutingRule(UUID organizationId, UUID mailboxId, UUID ruleId) {
        return routingRuleRepository.findById(ruleId)
                .filter(r -> organizationId.equals(r.getOrganizationId()))
                .filter(r -> mailboxId.equals(r.getMailboxId()))
                .orElseThrow(() -> ApiException.notFound("Routing rule"));
    }

    private void applyRoutingRule(MailRoutingRule rule, MailboxDtos.SaveRoutingRuleRequest request) {
        rule.setName(request.name().trim());
        rule.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        if (request.enabled() != null) {
            rule.setEnabled(request.enabled());
        }
        if (request.priority() != null) {
            rule.setPriority(request.priority());
        }
        if (request.match() != null) {
            rule.setMatchMode(request.match());
        }
        if (request.continueAfterMatch() != null) {
            rule.setContinueAfterMatch(request.continueAfterMatch());
        }
        rule.setConditions(MailJson.toJson(request.conditions()));
        rule.setActions(MailJson.toJson(request.actions()));
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

        List<MailRoutingRule> rules = routingRuleRepository.findByOrganizationIdAndMailboxIdOrderByPriorityAsc(
                mailbox.getOrganizationId(), mailbox.getId());

        return new MailboxDtos.MailboxDetailResponse(
                mailbox.getId(),
                mailbox.getName(),
                mailbox.getAddress(),
                mailbox.getDescription(),
                members.size(),
                mailbox.getOpenThreadCount(),
                mailbox.getSlaFirstResponseMins(),
                mailbox.getSlaResolutionMins(),
                mailbox.getSignatureHtml(),
                mailbox.getCreatedAt(),
                mailbox.getPasswordUpdatedAt(),
                toMemberViews(members, mailbox.getOrganizationId()),
                rules.stream().map(this::toRoutingRule).toList(),
                parseBusinessHours(mailbox));
    }

    /**
     * Names every grant on a mailbox, resolving user and team labels in two queries rather than per row.
     *
     * <p>Team grants used to be dropped here — the mapper filtered to rows with a user id — so a
     * mailbox shared with a team showed no members at all and looked misconfigured.
     */
    private List<MailboxDtos.MailboxMemberResponse> toMemberViews(List<MailboxMember> members, UUID organizationId) {
        Set<UUID> userIds = members.stream()
                .map(MailboxMember::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> teamIds = members.stream()
                .map(MailboxMember::getTeamId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, OrganizationMembership> memberships = userIds.isEmpty() ? Map.of()
                : membershipRepository.findByOrganizationIdAndUserIdIn(organizationId, userIds).stream()
                .collect(Collectors.toMap(OrganizationMembership::getUserId, m -> m, (a, b) -> a));
        Map<UUID, Team> teams = teamIds.isEmpty() ? Map.of()
                : teamRepository.findAllById(teamIds).stream()
                .filter(t -> organizationId.equals(t.getOrganizationId()))
                .collect(Collectors.toMap(Team::getId, t -> t, (a, b) -> a));

        return members.stream().map(m -> toMemberView(m, memberships, teams)).toList();
    }

    private MailboxDtos.MailboxMemberResponse toMemberView(MailboxMember member, UUID organizationId) {
        return toMemberViews(List.of(member), organizationId).getFirst();
    }

    private MailboxDtos.MailboxMemberResponse toMemberView(MailboxMember member,
                                                            Map<UUID, OrganizationMembership> memberships,
                                                            Map<UUID, Team> teams) {
        if (member.getTeamId() != null) {
            Team team = teams.get(member.getTeamId());
            return new MailboxDtos.MailboxMemberResponse(
                    member.getId(), null, member.getTeamId(),
                    team != null ? team.getName() : "Unknown team", null, member.getAccessLevel());
        }
        OrganizationMembership orgMember = memberships.get(member.getUserId());
        return new MailboxDtos.MailboxMemberResponse(
                member.getId(), member.getUserId(), null,
                orgMember != null ? orgMember.getDisplayName() : "Unknown",
                orgMember != null ? orgMember.getEmail() : null,
                member.getAccessLevel());
    }

    private MailboxDtos.RoutingRuleResponse toRoutingRule(MailRoutingRule rule) {
        return new MailboxDtos.RoutingRuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getDescription(),
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
