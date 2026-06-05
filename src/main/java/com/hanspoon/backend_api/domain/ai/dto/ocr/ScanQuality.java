package com.hanspoon.backend_api.domain.ai.dto.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 품질/재촬영 판단 메타데이터.
 *
 * @param status 품질 상태 ("usable" | "low_confidence" | "needs_retake")
 * @param score 종합 품질 점수 (0~100)
 * @param rawLineCount OCR 원본 라인 수
 * @param priceMatchCount 가격 매칭된 메뉴 수
 * @param priceMatchRatio 가격 매칭 비율
 * @param imageWidth 이미지 가로 px
 * @param imageHeight 이미지 세로 px
 * @param imageQuality 이미지 품질 세부 분석
 * @param retakeSuggestions 재촬영 가이드 문구
 * @param reasons 품질 저하 사유
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScanQuality(
        String status,
        Integer score,
        Integer rawLineCount,
        Integer priceMatchCount,
        Double priceMatchRatio,
        Integer imageWidth,
        Integer imageHeight,
        ImageQuality imageQuality,
        List<String> retakeSuggestions,
        List<String> reasons) {}
