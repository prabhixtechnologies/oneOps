package com.prabhix.platform.ops.config;

import com.prabhix.platform.ops.aws.AwsOpsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AwsOpsProperties.class,
        OpsHealthProperties.class,
        OpsGithubProperties.class
})
public class OpsConfig {
}
