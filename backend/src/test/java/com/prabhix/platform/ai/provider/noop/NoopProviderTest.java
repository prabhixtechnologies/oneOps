package com.prabhix.platform.ai.provider.noop;

import com.prabhix.platform.ai.provider.model.AiCompletionRequest;
import com.prabhix.platform.ai.provider.model.AiMessage;
import com.prabhix.platform.ai.provider.model.AiRole;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoopProviderTest {

    private final NoopProvider provider = new NoopProvider();

    @Test
    void notConfigured() {
        assertFalse(provider.configured());
    }

    @Test
    void completeThrowsNotConfigured() {
        ApiException ex = assertThrows(ApiException.class, () -> provider.complete(
                AiCompletionRequest.of("none", List.of(new AiMessage(AiRole.USER, "hi")))));
        assertEquals(ErrorCode.AI_NOT_CONFIGURED, ex.getCode());
    }
}
