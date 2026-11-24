package com.hanspoon.backend_api.global.config;

import org.springframework.stereotype.Component;

/** S3 객체 키 → 공개 이미지 주소(CDN). */
@Component
public class CdnUrls {

    private final String baseUrl;

    public CdnUrls(CdnProperties cdnProperties) {
        this.baseUrl = cdnProperties.baseUrl().replaceAll("/+$", "");
    }

    /** 키가 없으면 null. */
    public String of(String storageKey) {
        return storageKey == null ? null : baseUrl + "/" + storageKey;
    }
}
