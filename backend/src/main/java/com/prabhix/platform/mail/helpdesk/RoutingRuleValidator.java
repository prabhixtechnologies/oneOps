package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.dto.MailboxDtos;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Checks a routing rule against the shapes {@code RoutingRuleEngine} actually understands.
 *
 * <p>The engine is deliberately permissive at evaluation time: an unknown condition field resolves to
 * null and an unknown action type is ignored, because a bad rule must not stop a customer's mail from
 * being delivered. That is the right behaviour at runtime and the wrong behaviour at configuration
 * time — it means a rule with {@code SUBJET} instead of {@code SUBJECT} saves cleanly, appears in the
 * list, and never fires. Nobody debugs that quickly, because there is nothing to see.
 *
 * <p>So the strictness lives here, at the boundary where a human can be told what is wrong. The
 * vocabulary is duplicated from the engine on purpose; the alternative is exporting the engine's
 * switch statements as data, which couples the writer of rules to the evaluator of them for the sake
 * of removing two lists.
 */
final class RoutingRuleValidator {

    private static final Set<String> FIELDS = Set.of(
            "FROM", "FROM_DOMAIN", "TO", "CC", "SUBJECT", "BODY", "HAS_ATTACHMENT", "SPAM_SCORE");

    private static final Set<String> OPS = Set.of("EQUALS", "CONTAINS", "MATCHES", "IN", "GT", "LT");

    /** Ops that compare numbers, and so are only meaningful against a numeric field. */
    private static final Set<String> NUMERIC_OPS = Set.of("GT", "LT");

    private static final Set<String> NUMERIC_FIELDS = Set.of("SPAM_SCORE");

    private static final Set<String> ACTIONS = Set.of(
            "ASSIGN_USER", "ASSIGN_TEAM", "SET_PRIORITY", "SET_STATUS", "ADD_TAG",
            "APPLY_SLA", "MOVE_MAILBOX", "AUTO_REPLY", "MARK_SPAM");

    /** Actions whose value is a UUID the engine will parse, silently to null if it is not one. */
    private static final Set<String> UUID_ACTIONS = Set.of("ASSIGN_USER", "ASSIGN_TEAM", "MOVE_MAILBOX");

    private static final int MAX_CONDITIONS = 20;
    private static final int MAX_ACTIONS = 10;

    private RoutingRuleValidator() {
    }

    static void validate(MailboxDtos.SaveRoutingRuleRequest request) {
        if (request.conditions().size() > MAX_CONDITIONS) {
            throw ApiException.invalidState("A rule may have at most " + MAX_CONDITIONS + " conditions");
        }
        if (request.actions().size() > MAX_ACTIONS) {
            throw ApiException.invalidState("A rule may have at most " + MAX_ACTIONS + " actions");
        }
        for (Map<String, Object> condition : request.conditions()) {
            validateCondition(condition);
        }
        for (Map<String, Object> action : request.actions()) {
            validateAction(action);
        }
    }

    private static void validateCondition(Map<String, Object> condition) {
        String field = string(condition.get("field"));
        String op = string(condition.get("op"));
        Object value = condition.get("value");

        // HEADER:X is open-ended by design — a rule can match any header the sender set — so only the
        // prefix is checked, plus that a header was actually named.
        boolean header = field != null && field.startsWith("HEADER:");
        if (header && field.length() == "HEADER:".length()) {
            throw ApiException.invalidState("HEADER: needs a header name after the colon");
        }
        if (!header && (field == null || !FIELDS.contains(field))) {
            throw ApiException.invalidState("Unknown condition field: " + field);
        }
        if (op == null || !OPS.contains(op)) {
            throw ApiException.invalidState("Unknown condition operator: " + op);
        }
        if (value == null) {
            throw ApiException.invalidState("Condition on " + field + " needs a value");
        }

        if ("IN".equals(op) && !(value instanceof List<?>)) {
            throw ApiException.invalidState("IN takes a list of values");
        }
        if (!"IN".equals(op) && value instanceof List<?>) {
            throw ApiException.invalidState(op + " takes a single value, not a list");
        }

        // The engine returns 0 from its numeric comparison when either side will not parse, and 0
        // means "not greater and not less", so GT and LT on a text field are conditions that can
        // never be true.
        if (NUMERIC_OPS.contains(op) && !header && !NUMERIC_FIELDS.contains(field)) {
            throw ApiException.invalidState(op + " compares numbers and " + field + " is not numeric");
        }

        if ("MATCHES".equals(op)) {
            try {
                Pattern.compile(String.valueOf(value));
            } catch (PatternSyntaxException ex) {
                throw ApiException.invalidState("That regular expression does not compile: "
                        + ex.getDescription());
            }
        }
    }

    private static void validateAction(Map<String, Object> action) {
        String type = string(action.get("type"));
        Object value = action.get("value");

        if (type == null || !ACTIONS.contains(type)) {
            throw ApiException.invalidState("Unknown action: " + type);
        }
        // MARK_SPAM is the one action with no argument; the engine ignores any value given.
        if ("MARK_SPAM".equals(type)) {
            return;
        }
        if (value == null) {
            throw ApiException.invalidState(type + " needs a value");
        }

        if (UUID_ACTIONS.contains(type)) {
            try {
                UUID.fromString(String.valueOf(value));
            } catch (IllegalArgumentException ex) {
                throw ApiException.invalidState(type + " needs an id");
            }
            return;
        }
        switch (type) {
            case "SET_PRIORITY" -> requireEnum(MailEnums.Priority.class, value, "priority");
            case "SET_STATUS" -> requireEnum(MailEnums.ThreadStatus.class, value, "status");
            case "APPLY_SLA" -> {
                int minutes = requireInt(value);
                if (minutes <= 0) {
                    throw ApiException.invalidState("An SLA target must be a positive number of minutes");
                }
            }
            case "ADD_TAG", "AUTO_REPLY" -> {
                if (String.valueOf(value).isBlank()) {
                    throw ApiException.invalidState(type + " needs a value");
                }
            }
            default -> throw new IllegalStateException("Unreachable: " + type);
        }
    }

    private static <E extends Enum<E>> void requireEnum(Class<E> type, Object value, String what) {
        try {
            Enum.valueOf(type, String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            throw ApiException.invalidState("Not a known " + what + ": " + value);
        }
    }

    private static int requireInt(Object value) {
        // Numbers arrive as Integer from Jackson and as String from a client that quoted them; both are
        // accepted because the engine accepts both, and rejecting one here would fail a rule that works.
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw ApiException.invalidState("Expected a number, got: " + value);
        }
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
