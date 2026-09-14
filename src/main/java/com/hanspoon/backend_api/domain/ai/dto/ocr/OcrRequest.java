package com.hanspoon.backend_api.domain.ai.dto.ocr;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * OCR 요청 (백엔드 → AI 서비스). 검증된 S3 객체 식별 정보를 전달한다.
 *
 * @param storeId 스캔 세션에 고정된 가게 ID
 * @param source 이미지 소스 ("camera" | "upload")
 * @param storageKey 스토리지 키 (예: "scans/menu_003.jpg")
 * @param imageUrl 레거시 호환 필드. 운영 S3 IAM 경로에서는 null
 * @param versionId S3 버전 관리가 활성화된 버킷에서 객체가 생성될 때 S3가 부여하는 버전 식별자
 * @param expectedEtag 검증 시점의 S3 객체 ETag
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OcrRequest(
        Long storeId, String source, String storageKey, String imageUrl, String versionId, String expectedEtag) {
    public static OcrRequest forS3(
            Long storeId,
            String source,
            String storageKey,
            String fallbackImageUrl,
            String versionId,
            String expectedEtag) {
        return new OcrRequest(storeId, source, storageKey, fallbackImageUrl, versionId, expectedEtag);
    }
}
