package com.hanspoon.backend_api.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.s3")
public record S3Properties(
        String bucket, String region, String endpoint, Duration uploadUrlTtl, Duration readUrlTtl, long maxFileSize) {}
