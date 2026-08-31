package com.prabhix.platform.mail.inbound;

import com.prabhix.platform.mail.domain.MailRoutingRule;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.repository.MailRoutingRuleRepository;
import com.prabhix.platform.mail.repository.MailTagRepository;
import com.prabhix.platform.common.util.Json;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/** Evaluates routing rules in priority order; guards regex against catastrophic backtracking. */
@Component
@RequiredArgsConstructor
public class RoutingRuleEngine {

    private static final int MAX_REGEX_INPUT = 10_000;
    private static final long REGEX_TIMEOUT_MS = 200;

    private final MailRoutingRuleRepository ruleRepository;
    private final MailTagRepository tagRepository;
    private final ExecutorService regexExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "mail-regex-guard");
        t.setDaemon(true);
        return t;
    });

    public void apply(UUID organizationId, UUID mailboxId, MailThread thread,
                      MimeParser.ParsedMime parsed, RoutingContext context) {
        List<MailRoutingRule> rules = ruleRepository.findActiveForMailbox(organizationId, mailboxId);
        for (MailRoutingRule rule : rules) {
            if (matches(rule, parsed, context)) {
                applyActions(rule, thread, context);
                ruleRepository.incrementMatchCount(rule.getId(), Instant.now());
                if (!rule.isContinueAfterMatch()) {
                    break;
                }
            }
        }
    }

    boolean matches(MailRoutingRule rule, MimeParser.ParsedMime parsed, RoutingContext context) {
        List<Map<String, Object>> conditions = Json.parseObjectList(rule.getConditions());
        if (conditions.isEmpty()) {
            return false;
        }
        boolean any = rule.getMatchMode() == MailEnums.MatchMode.ANY;
        for (Map<String, Object> condition : conditions) {
            boolean result = evaluateCondition(condition, parsed, context);
            if (any && result) {
                return true;
            }
            if (!any && !result) {
                return false;
            }
        }
        return !any;
    }

    private boolean evaluateCondition(Map<String, Object> condition,
                                    MimeParser.ParsedMime parsed,
                                    RoutingContext context) {
        String field = String.valueOf(condition.get("field"));
        String op = String.valueOf(condition.get("op"));
        Object value = condition.get("value");
        String actual = resolveField(field, parsed, context);

        return switch (op) {
            case "EQUALS" -> actual != null && actual.equalsIgnoreCase(String.valueOf(value));
            case "CONTAINS" -> actual != null && actual.toLowerCase(Locale.ROOT)
                    .contains(String.valueOf(value).toLowerCase(Locale.ROOT));
            case "MATCHES" -> actual != null && safeMatches(String.valueOf(value), actual);
            case "IN" -> value instanceof List<?> list && actual != null
                    && list.stream().anyMatch(v -> actual.equalsIgnoreCase(String.valueOf(v)));
            case "GT" -> compareNumeric(actual, value) > 0;
            case "LT" -> compareNumeric(actual, value) < 0;
            default -> false;
        };
    }

    private String resolveField(String field, MimeParser.ParsedMime parsed, RoutingContext context) {
        if (field.startsWith("HEADER:")) {
            String name = field.substring(7);
            return parsed.getHeaders().get(name);
        }
        return switch (field) {
            case "FROM" -> parsed.getFrom();
            case "FROM_DOMAIN" -> domainOf(parsed.getFrom());
            case "TO" -> String.join(",", parsed.getToAddresses());
            case "CC" -> String.join(",", parsed.getCcAddresses());
            case "SUBJECT" -> parsed.getSubject();
            case "BODY" -> parsed.getBodyText() != null ? parsed.getBodyText() : parsed.getBodyHtml();
            case "HAS_ATTACHMENT" -> String.valueOf(parsed.getAttachmentCount() > 0);
            case "SPAM_SCORE" -> context.getSpamScore() != null ? context.getSpamScore().toPlainString() : null;
            default -> null;
        };
    }

    private void applyActions(MailRoutingRule rule, MailThread thread, RoutingContext context) {
        List<Map<String, Object>> actions = Json.parseObjectList(rule.getActions());
        for (Map<String, Object> action : actions) {
            String type = String.valueOf(action.get("type"));
            Object value = action.get("value");
            switch (type) {
                case "ASSIGN_USER" -> thread.setAssigneeUserId(parseUuid(value));
                case "ASSIGN_TEAM" -> thread.setAssigneeTeamId(parseUuid(value));
                case "SET_PRIORITY" -> thread.setPriority(MailEnums.Priority.valueOf(String.valueOf(value)));
                case "SET_STATUS" -> thread.setStatus(MailEnums.ThreadStatus.valueOf(String.valueOf(value)));
                case "ADD_TAG" -> context.addTagSlug(String.valueOf(value));
                case "APPLY_SLA" -> context.setSlaPolicyMinutes(parseInt(value));
                case "MOVE_MAILBOX" -> context.setMoveMailboxId(parseUuid(value));
                case "AUTO_REPLY" -> context.setAutoReplyTemplate(String.valueOf(value));
                case "MARK_SPAM" -> thread.setStatus(MailEnums.ThreadStatus.SPAM);
                default -> { /* unknown action ignored */ }
            }
        }
    }

    boolean safeMatches(String pattern, String input) {
        final String bounded = input.length() > MAX_REGEX_INPUT
                ? input.substring(0, MAX_REGEX_INPUT) : input;
        Pattern compiled;
        try {
            compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        } catch (Exception ex) {
            return false;
        }
        Future<Boolean> future = regexExecutor.submit(() -> compiled.matcher(bounded).find());
        try {
            return future.get(REGEX_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private int compareNumeric(String actual, Object expected) {
        if (actual == null || expected == null) {
            return 0;
        }
        try {
            double a = Double.parseDouble(actual);
            double e = Double.parseDouble(String.valueOf(expected));
            return Double.compare(a, e);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String domainOf(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        return email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
    }

    private UUID parseUuid(Object value) {
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer parseInt(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    @lombok.Getter
    @lombok.Setter
    public static class RoutingContext {
        private BigDecimal spamScore;
        private Integer slaPolicyMinutes;
        private UUID moveMailboxId;
        private String autoReplyTemplate;
        private java.util.List<String> tagSlugs = new java.util.ArrayList<>();

        public void addTagSlug(String slug) {
            tagSlugs.add(slug);
        }
    }
}
