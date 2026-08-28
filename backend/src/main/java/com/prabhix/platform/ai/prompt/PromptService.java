package com.prabhix.platform.ai.prompt;

import com.prabhix.platform.ai.domain.AiPrompt;
import com.prabhix.platform.ai.repository.AiPromptRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromptService {

    private final AiPromptRepository promptRepository;
    private final PromptRenderer renderer;

    @Transactional(readOnly = true)
    public ResolvedPrompt resolve(UUID organizationId, String taskKey, Map<String, Object> variables) {
        List<AiPrompt> resolved = promptRepository.resolvePrompt(taskKey, organizationId);
        if (resolved.isEmpty()) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "AI prompt " + taskKey + " was not found");
        }
        AiPrompt prompt = resolved.get(0);
        String rendered = renderer.render(prompt.getTemplate(), variables);
        return new ResolvedPrompt(
                taskKey,
                rendered,
                prompt.getProvider(),
                prompt.getModel(),
                prompt.getTemperature(),
                prompt.getPromptVersion());
    }

    @Transactional(readOnly = true)
    public List<AiPrompt> listForOrg(UUID organizationId) {
        return promptRepository.findByOrganizationIdOrOrganizationIdIsNullOrderByTaskKey(organizationId);
    }

    @Transactional
    public AiPrompt upsertOrgPrompt(UUID organizationId, String taskKey, String template,
                                    String provider, String model, Double temperature, UUID userId) {
        AiPrompt prompt = promptRepository.findByOrganizationIdAndTaskKey(organizationId, taskKey)
                .orElseGet(() -> {
                    AiPrompt created = new AiPrompt();
                    created.setOrganizationId(organizationId);
                    created.setTaskKey(taskKey);
                    created.setName(taskKey);
                    return created;
                });
        prompt.setTemplate(template);
        if (provider != null) {
            prompt.setProvider(provider);
        }
        if (model != null) {
            prompt.setModel(model);
        }
        if (temperature != null) {
            prompt.setTemperature(temperature);
        }
        prompt.setPromptVersion(prompt.getPromptVersion() + 1);
        return promptRepository.save(prompt);
    }

    public record ResolvedPrompt(
            String taskKey,
            String renderedText,
            String providerOverride,
            String modelOverride,
            double temperature,
            int version) {
    }
}
