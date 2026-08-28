package com.prabhix.platform.mail.inbound;

import com.prabhix.platform.mail.domain.MailRoutingRule;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.repository.MailRoutingRuleRepository;
import com.prabhix.platform.mail.repository.MailTagRepository;
import com.prabhix.platform.mail.util.MailJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeout;

@ExtendWith(MockitoExtension.class)
class RoutingRuleEngineTest {

    @Mock
    MailRoutingRuleRepository ruleRepository;
    @Mock
    MailTagRepository tagRepository;

    RoutingRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RoutingRuleEngine(ruleRepository, tagRepository);
    }

    @Test
    void allConditionsMustMatch() {
        MailRoutingRule rule = rule();
        rule.setMatchMode(MailEnums.MatchMode.ALL);
        rule.setConditions(MailJson.toJson(List.of(
                Map.of("field", "FROM_DOMAIN", "op", "EQUALS", "value", "acme.com"),
                Map.of("field", "SUBJECT", "op", "CONTAINS", "value", "urgent"))));

        MimeParser.ParsedMime parsed = parsed("user@acme.com", "urgent help", null);
        assertTrue(engine.matches(rule, parsed, ctx()));

        parsed.setSubject("routine");
        assertFalse(engine.matches(rule, parsed, ctx()));
    }

    @Test
    void anyConditionMatches() {
        MailRoutingRule rule = rule();
        rule.setMatchMode(MailEnums.MatchMode.ANY);
        rule.setConditions(MailJson.toJson(List.of(
                Map.of("field", "SUBJECT", "op", "CONTAINS", "value", "billing"),
                Map.of("field", "HAS_ATTACHMENT", "op", "EQUALS", "value", "true"))));

        MimeParser.ParsedMime parsed = parsed("a@b.com", "billing issue", null);
        assertTrue(engine.matches(rule, parsed, ctx()));
    }

    @Test
    void operatorsWork() {
        MailRoutingRule rule = rule();
        rule.setMatchMode(MailEnums.MatchMode.ALL);

        rule.setConditions(MailJson.toJson(List.of(
                Map.of("field", "FROM", "op", "IN", "value", List.of("a@x.com", "b@x.com")))));
        assertTrue(engine.matches(rule, parsed("a@x.com", "s", null), ctx()));

        rule.setConditions(MailJson.toJson(List.of(
                Map.of("field", "SUBJECT", "op", "MATCHES", "value", "outage.*"))));
        assertTrue(engine.matches(rule, parsed("a@b.com", "outage now", null), ctx()));

        rule.setConditions(MailJson.toJson(List.of(
                Map.of("field", "SPAM_SCORE", "op", "GT", "value", "5"))));
        RoutingRuleEngine.RoutingContext ctx = ctx();
        ctx.setSpamScore(new java.math.BigDecimal("7"));
        assertTrue(engine.matches(rule, parsed("a@b.com", "s", null), ctx));
    }

    @Test
    void pathologicalRegexDoesNotHang() {
        assertTimeout(java.time.Duration.ofSeconds(2), () -> {
            boolean result = engine.safeMatches("(a+)+$", "aaaaaaaaaaaaaaaaaaaaaaaaaaaa!");
            assertFalse(result);
        });
    }

    private MailRoutingRule rule() {
        MailRoutingRule rule = new MailRoutingRule();
        rule.setMatchMode(MailEnums.MatchMode.ALL);
        return rule;
    }

    private MimeParser.ParsedMime parsed(String from, String subject, String body) {
        MimeParser.ParsedMime p = new MimeParser.ParsedMime();
        p.setFrom(from);
        p.setSubject(subject);
        p.setBodyText(body);
        return p;
    }

    private RoutingRuleEngine.RoutingContext ctx() {
        return new RoutingRuleEngine.RoutingContext();
    }
}
