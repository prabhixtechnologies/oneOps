package com.prabhix.platform.config;

import com.prabhix.platform.files.service.FileStorageService;
import com.prabhix.platform.files.storage.S3ObjectStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnExpression("'${prabhix.storage.access-key:}' != '' && '${prabhix.storage.secret-key:}' != ''")
    FileStorageService.ObjectStore objectStore(PrabhixProperties properties) {
        return new S3ObjectStore(properties);
    }
}
