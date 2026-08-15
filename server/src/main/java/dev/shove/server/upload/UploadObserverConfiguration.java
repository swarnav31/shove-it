package dev.shove.server.upload;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.shove.core.observability.UploadObserver;
import dev.shove.core.observability.UploadObservers;

@Configuration(proxyBeanMethods = false)
class UploadObserverConfiguration {

    @Bean
    @ConditionalOnMissingBean(UploadObserver.class)
    UploadObserver uploadObserver() {
        return UploadObservers.noOp();
    }
}
