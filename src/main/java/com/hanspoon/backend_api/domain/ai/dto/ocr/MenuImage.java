package com.hanspoon.backend_api.domain.ai.dto.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * menu_images 테이블 매핑.
 *
 * @param source 이미지 소스 ("camera" | "upload")
 * @param storageKey 스토리지 키
 * @param imageUrl 이미지 접근 URL
 * @param mimeType MIME 타입
 * @param fileSize 파일 크기(byte)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record MenuImage(String source, String storageKey, String imageUrl, String mimeType, Long fileSize) {}
