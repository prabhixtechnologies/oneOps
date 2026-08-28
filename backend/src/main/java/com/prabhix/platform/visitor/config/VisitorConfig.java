package com.prabhix.platform.visitor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VisitorProperties.class)
public class VisitorConfig {
}
