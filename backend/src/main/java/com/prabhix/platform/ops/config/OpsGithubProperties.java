package com.prabhix.platform.ops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Optional GitHub Actions checks for the ops hub. Auth is {@code GITHUB_TOKEN} from the
 * environment only — never a property file secret.
 */
@Validated
@ConfigurationProperties(prefix = "prabhix.ops.github")
public record OpsGithubProperties(
        @DefaultValue("https://api.github.com") String apiUrl,
        @DefaultValue({
                "prabhixtechnologies/Mobile",
                "prabhixtechnologies/oneOps",
                "prabhixtechnologies/MobiStack",
                "prabhixtechnologies/Identity",
                "prabhixtechnologies/Mailroom",
                "prabhixtechnologies/Infra"
        }) List<String> repos,
        @DefaultValue("prabhixtechnologies/Infra") String deployRepo,
        @DefaultValue("deploy.yml") String deployWorkflow,
        @DefaultValue("main") String deployRef) {
}
