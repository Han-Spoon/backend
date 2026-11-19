package com.hanspoon.backend_api.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 공개 이미지(CloudFront /menus/*) 기본 주소. */
@ConfigurationProperties("app.cdn")
public record CdnProperties(String baseUrl) {}
