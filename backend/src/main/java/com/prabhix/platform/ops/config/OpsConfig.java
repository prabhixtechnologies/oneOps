package com.prabhix.platform.ops.config;

import com.prabhix.identity.client.IdentityClientProperties;
import com.prabhix.platform.ops.aws.AwsOpsProperties;
import com.prabhix.platform.ops.client.MobiStackAdminClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
        AwsOpsProperties.class,
        OpsHealthProperties.class,
        OpsGithubProperties.class,
        MobiStackAdminProperties.class
})
public class OpsConfig {

    @Bean
    MobiStackAdminClient mobiStackAdminClient(MobiStackAdminProperties mobistack,
                                              IdentityClientProperties identity,
                                              RestClient.Builder builders) {
        return new MobiStackAdminClient(mobistack, identity, builders);
    }
}
