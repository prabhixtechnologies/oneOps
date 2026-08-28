package com.prabhix.platform.files.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FilesScanProperties.class)
public class FilesScanConfig {
}
