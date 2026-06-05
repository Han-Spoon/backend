package com.hanspoon.backend_api.domain.ai.dto.ocr;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * OCR 요청 (백엔드 → AI 서비스). 이미지는 미리 스토리지에 저장된 뒤 URL 로 전달한다.
 *
 * @param source 이미지 소스 ("camera" | "upload")
 * @param storageKey 스토리지 키 (예: "scans/menu_003.jpg")
 * @param imageUrl 이미지 접근 URL
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OcrRequest(String source, String storageKey, String imageUrl) {}
