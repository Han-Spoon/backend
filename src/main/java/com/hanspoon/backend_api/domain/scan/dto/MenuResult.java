package com.hanspoon.backend_api.domain.scan.dto;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * 스캔 결과의 메뉴 1건. 사용자 표시용.
 * 메뉴 사진은 스캔 가게의 가게 메뉴 사진만.
 *
 * @param scanMenuId 스캔 메뉴 id
 * @param displayOrder 표시 순서
 * @param displayName 메뉴판 표기
 * @param translatedName 정규 메뉴의 사용자 언어 이름, 없거나 한국어 사용자면 null
 * @param description 메뉴판 설명
 * @param translatedDescription 설명 번역, 미제공(null)
 * @param priceText 가격 문자열
 * @param imageUrl 가게 메뉴 사진 공개 주소, null 가능
 * @param imageAttribution 사진 출처 표기, imageUrl 과 함께 null
 * @param isSpicy 매운맛 감지 시 true, 아니면 null
 * @param riskLevel 위험도 (danger | caution | safe)
 * @param status 판정 상태 (completed | partial_failed | failed)
 * @param evidenceBasis 판정 근거 (confirmed | estimated | unknown)
 * @param informationStatus 재료 정보 충분도 (complete | partial | none)
 * @param staffAnswerStatus 직원 응답 상태 (none | partial | complete)
 * @param riskIngredients 위험 재료, Judge 이전 메뉴는 null
 * @param matchedTags 내 식단과 겹친 태그
 * @param message Judge {verdict,reason,notes} 또는 이전 스캔 {ko,en,ar}, null 가능
 */
@Schema(description = "스캔 결과 메뉴 항목")
public record MenuResult(
        UUID scanMenuId,
        Integer displayOrder,
        String displayName,
        String translatedName,
        String description,
        String translatedDescription,
        String priceText,
        String imageUrl,
        String imageAttribution,
        Boolean isSpicy,
        RiskLevel riskLevel,
        String status,
        String evidenceBasis,
        String informationStatus,
        String staffAnswerStatus,
        List<RiskIngredientResult> riskIngredients,
        List<String> matchedTags,
        @Schema(description = "Judge {verdict,reason,notes} 또는 이전 스캔 {ko,en,ar}") JsonNode message) {}
