package com.prabhix.platform;

import com.prabhix.platform.config.PrabhixProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(PrabhixProperties.class)
@EnableJpaAuditing
@EnableScheduling
@EnableAsync
public class PrabhixApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrabhixApplication.class, args);
    }
}
