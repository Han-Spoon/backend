package com.hanspoon.backend_api.domain.ai.dto.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * scan_sessions 테이블 매핑. riskyMenuCount 는 OCR 단계에선 null, 룰엔진이 채운다.
 *
 * @param title 원본 이미지 파일명
 * @param menuCount 추출된 메뉴 수
 * @param riskyMenuCount 위험/주의 메뉴 수 (OCR 단계 null)
 * @param scanStatus 스캔 상태 (예: "completed")
 * @param scannedAt 스캔 시각 (ISO-8601 문자열)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScanSession(
        String title, Integer menuCount, Integer riskyMenuCount, String scanStatus, String scannedAt) {}
