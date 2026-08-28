package com.prabhix.platform.ai.prompt;

import com.prabhix.platform.ai.domain.AiPrompt;
import com.prabhix.platform.ai.repository.AiPromptRepository;
import com.prabhix.platform.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPromptIsolationTest {

    @Mock private AiPromptRepository promptRepository;

    private PromptService promptService;
    private final UUID orgA = UUID.randomUUID();
    private final UUID orgB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        promptService = new PromptService(promptRepository, new PromptRenderer());
    }

    @Test
    void orgCannotResolveOtherOrgOverridePrompt() {
        AiPrompt orgBPrompt = new AiPrompt();
        orgBPrompt.setOrganizationId(orgB);
        orgBPrompt.setTaskKey("mail.reply_suggest");
        orgBPrompt.setTemplate("Org B secret [[${subject}]]");
        orgBPrompt.setTemperature(0.3);

        when(promptRepository.resolvePrompt("mail.reply_suggest", orgA))
                .thenReturn(List.of(platformDefault()));

        promptService.resolve(orgA, "mail.reply_suggest", java.util.Map.of("subject", "Hi"));

        when(promptRepository.resolvePrompt("mail.reply_suggest", orgA)).thenReturn(List.of());
        assertThrows(ApiException.class, () -> promptService.resolve(orgA, "mail.reply_suggest", java.util.Map.of()));
    }

    @Test
    void orgOverrideWinsOverPlatformDefault() {
        AiPrompt override = new AiPrompt();
        override.setOrganizationId(orgA);
        override.setTaskKey("mail.reply_suggest");
        override.setTemplate("Org A template [[${subject}]]");
        override.setTemperature(0.5);
        override.setPromptVersion(2);

        when(promptRepository.resolvePrompt("mail.reply_suggest", orgA))
                .thenReturn(List.of(override));

        var resolved = promptService.resolve(orgA, "mail.reply_suggest", java.util.Map.of("subject", "Test"));
        org.junit.jupiter.api.Assertions.assertTrue(resolved.renderedText().contains("Org A template"));
        org.junit.jupiter.api.Assertions.assertEquals(2, resolved.version());
    }

    private AiPrompt platformDefault() {
        AiPrompt prompt = new AiPrompt();
        prompt.setTaskKey("mail.reply_suggest");
        prompt.setTemplate("Default [[${subject}]]");
        prompt.setTemperature(0.3);
        prompt.setPromptVersion(1);
        return prompt;
    }
}
