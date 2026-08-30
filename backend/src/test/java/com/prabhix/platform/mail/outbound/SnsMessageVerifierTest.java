package com.prabhix.platform.mail.outbound;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SnsMessageVerifierTest {

    private SnsMessageVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new SnsMessageVerifier(new ObjectMapper());
    }

    @Test
    void rejectsMessageWithUntrustedCertUrl() {
        ObjectNode message = new ObjectMapper().createObjectNode();
        message.put("Type", "Notification");
        message.put("MessageId", "msg-1");
        message.put("TopicArn", "arn:aws:sns:us-east-1:123:topic");
        message.put("Message", "{}");
        message.put("Timestamp", "2026-08-28T00:00:00.000Z");
        message.put("Signature", "abc");
        message.put("SigningCertURL", "https://evil.example/cert.pem");
        message.put("SignatureVersion", "1");

        org.junit.jupiter.api.Assertions.assertFalse(verifier.verify(message, "{}".getBytes()));
    }

    @Test
    void buildsCanonicalStringForNotification() {
        ObjectNode message = new ObjectMapper().createObjectNode();
        message.put("Type", "Notification");
        message.put("MessageId", "mid");
        message.put("TopicArn", "arn");
        message.put("Message", "body");
        message.put("Timestamp", "ts");

        String canonical = verifier.buildStringToSign(message, "Notification");

        assertTrue(canonical.contains("Message\nbody\n"));
        assertTrue(canonical.contains("MessageId\nmid\n"));
    }
}
