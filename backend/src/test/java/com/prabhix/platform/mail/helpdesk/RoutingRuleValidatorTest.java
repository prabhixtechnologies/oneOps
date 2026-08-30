package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.dto.MailboxDtos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The engine ignores what it does not understand, so that a bad rule cannot stop mail being
 * delivered. That leaves configuration-time validation as the only place a typo can be reported, and
 * these are the mistakes that otherwise produce a rule which saves cleanly and never fires.
 */
class RoutingRuleValidatorTest {

    @Test
    void acceptsAWellFormedRule() {
        assertDoesNotThrow(() -> RoutingRuleValidator.validate(rule(
                List.of(Map.of("field", "FROM_DOMAIN", "op", "EQUALS", "value", "acme.com")),
                List.of(Map.of("type", "SET_PRIORITY", "value", "HIGH")))));
    }

    @Test
    void rejectsAMisspelledField() {
        assertMessageContains("Unknown condition field: SUBJET", rule(
                List.of(Map.of("field", "SUBJET", "op", "CONTAINS", "value", "invoice")),
                List.of(Map.of("type", "MARK_SPAM"))));
    }

    @Test
    void rejectsAMisspelledOperator() {
        assertMessageContains("Unknown condition operator: STARTS_WITH", rule(
                List.of(Map.of("field", "SUBJECT", "op", "STARTS_WITH", "value", "RE:")),
                List.of(Map.of("type", "MARK_SPAM"))));
    }

    @Test
    void rejectsAnUnknownAction() {
        assertMessageContains("Unknown action: SET_ASSIGNEE", rule(
                List.of(Map.of("field", "SUBJECT", "op", "CONTAINS", "value", "x")),
                List.of(Map.of("type", "SET_ASSIGNEE", "value", "someone"))));
    }

    /** HEADER: is open-ended because a rule may match any header a sender set. */
    @Test
    void acceptsAnyHeaderButNotAnEmptyOne() {
        assertDoesNotThrow(() -> RoutingRuleValidator.validate(rule(
                List.of(Map.of("field", "HEADER:X-Mailer", "op", "CONTAINS", "value", "Outlook")),
                List.of(Map.of("type", "ADD_TAG", "value", "desktop")))));

        assertMessageContains("needs a header name", rule(
                List.of(Map.of("field", "HEADER:", "op", "CONTAINS", "value", "x")),
                List.of(Map.of("type", "MARK_SPAM"))));
    }

    /**
     * The engine's numeric comparison returns 0 when either side will not parse, and 0 is neither
     * greater nor less, so GT on a subject line is a condition that can never be true.
     */
    @Test
    void rejectsNumericComparisonOnTextFields() {
        assertMessageContains("GT compares numbers and SUBJECT is not numeric", rule(
                List.of(Map.of("field", "SUBJECT", "op", "GT", "value", 5)),
                List.of(Map.of("type", "MARK_SPAM"))));

        assertDoesNotThrow(() -> RoutingRuleValidator.validate(rule(
                List.of(Map.of("field", "SPAM_SCORE", "op", "GT", "value", 5)),
                List.of(Map.of("type", "MARK_SPAM")))));
    }

    @Test
    void inNeedsAListAndOthersDoNot() {
        assertMessageContains("IN takes a list", rule(
                List.of(Map.of("field", "FROM", "op", "IN", "value", "a@b.com")),
                List.of(Map.of("type", "MARK_SPAM"))));

        assertMessageContains("EQUALS takes a single value", rule(
                List.of(Map.of("field", "FROM", "op", "EQUALS", "value", List.of("a@b.com"))),
                List.of(Map.of("type", "MARK_SPAM"))));
    }

    /**
     * A pattern that does not compile is swallowed by the engine and treated as no match, so it would
     * otherwise be indistinguishable from a rule nobody has triggered yet.
     */
    @Test
    void rejectsARegexThatDoesNotCompile() {
        assertMessageContains("does not compile", rule(
                List.of(Map.of("field", "SUBJECT", "op", "MATCHES", "value", "invoice(")),
                List.of(Map.of("type", "MARK_SPAM"))));
    }

    @Test
    void assignmentActionsNeedAnId() {
        assertMessageContains("ASSIGN_USER needs an id", rule(
                List.of(Map.of("field", "SUBJECT", "op", "CONTAINS", "value", "x")),
                List.of(Map.of("type", "ASSIGN_USER", "value", "priya"))));

        assertDoesNotThrow(() -> RoutingRuleValidator.validate(rule(
                List.of(Map.of("field", "SUBJECT", "op", "CONTAINS", "value", "x")),
                List.of(Map.of("type", "ASSIGN_USER", "value", UUID.randomUUID().toString())))));
    }

    @Test
    void enumActionsNeedAKnownValue() {
        assertMessageContains("Not a known priority: SEVERE", rule(
                List.of(Map.of("field", "SUBJECT", "op", "CONTAINS", "value", "x")),
                List.of(Map.of("type", "SET_PRIORITY", "value", "SEVERE"))));

        assertMessageContains("Not a known status: DONE", rule(
                List.of(Map.of("field", "SUBJECT", "op", "CONTAINS", "value", "x")),
                List.of(Map.of("type", "SET_STATUS", "value", "DONE"))));
    }

    @Test
    void slaMinutesMustBePositiveAndMayArriveAsAString() {
        assertMessageContains("positive number of minutes", rule(
                List.of(Map.of("field", "SUBJECT", "op", "CONTAINS", "value", "x")),
                List.of(Map.of("type", "APPLY_SLA", "value", 0))));

        assertDoesNotThrow(() -> RoutingRuleValidator.validate(rule(
                List.of(Map.of("field", "SUBJECT", "op", "CONTAINS", "value", "x")),
                List.of(Map.of("type", "APPLY_SLA", "value", "30")))));
    }

    /** The one action with no argument. */
    @Test
    void markSpamNeedsNoValue() {
        assertDoesNotThrow(() -> RoutingRuleValidator.validate(rule(
                List.of(Map.of("field", "SPAM_SCORE", "op", "GT", "value", 8)),
                List.of(Map.of("type", "MARK_SPAM")))));
    }

    private static void assertMessageContains(String fragment, MailboxDtos.SaveRoutingRuleRequest request) {
        ApiException ex = assertThrows(ApiException.class, () -> RoutingRuleValidator.validate(request));
        assertTrue(ex.getMessage().contains(fragment),
                "expected message to contain \"" + fragment + "\" but was \"" + ex.getMessage() + "\"");
    }

    private static MailboxDtos.SaveRoutingRuleRequest rule(List<Map<String, Object>> conditions,
                                                            List<Map<String, Object>> actions) {
        return new MailboxDtos.SaveRoutingRuleRequest(
                "Escalate", null, true, 10, MailEnums.MatchMode.ALL, conditions, actions, false);
    }
}
