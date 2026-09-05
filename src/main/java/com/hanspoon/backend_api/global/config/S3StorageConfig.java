package com.hanspoon.backend_api.global.config;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3StorageConfig {

    @Bean
    S3Client s3Client(S3Properties properties) {
        software.amazon.awssdk.services.s3.S3ClientBuilder builder =
                S3Client.builder().region(Region.of(properties.region()));
        applyEndpointOverride(properties, builder::endpointOverride, builder::serviceConfiguration);
        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner(S3Properties properties) {
        S3Presigner.Builder builder = S3Presigner.builder().region(Region.of(properties.region()));
        applyEndpointOverride(properties, builder::endpointOverride, builder::serviceConfiguration);
        return builder.build();
    }

    private void applyEndpointOverride(
            S3Properties properties,
            java.util.function.Consumer<URI> endpointSetter,
            java.util.function.Consumer<S3Configuration> configSetter) {
        if (properties.endpoint() == null || properties.endpoint().isBlank()) {
            return;
        }
        endpointSetter.accept(URI.create(properties.endpoint()));
        configSetter.accept(
                S3Configuration.builder().pathStyleAccessEnabled(true).build());
    }
}
