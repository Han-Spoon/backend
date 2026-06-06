package com.hanspoon.backend_api.domain.ai.dto.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * menu_analyses 테이블 매핑 (OCR 단계). riskLevel 은 OCR 단계에선 null, 룰엔진이 채운다.
 *
 * @param menuNameKo 한국어 메뉴명
 * @param menuNameEn 영어 메뉴명
 * @param descriptionKo 한국어 설명
 * @param descriptionEn 영어 설명
 * @param priceText 가격 문자열 (보정된 원 단위)
 * @param riskLevel 위험도 (OCR 단계 null)
 * @param isSpicy 매움 여부
 * @param imageUrl 개별 메뉴 이미지 URL
 * @param displayOrder 화면 표시 순서
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record MenuAnalysis(
        String menuNameKo,
        String menuNameEn,
        String descriptionKo,
        String descriptionEn,
        String priceText,
        RiskLevel riskLevel,
        Boolean isSpicy,
        String imageUrl,
        Integer displayOrder) {}
