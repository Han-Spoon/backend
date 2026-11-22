package com.hanspoon.backend_api.domain.scan.dto;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.ai.dto.result.OwnerCard;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * 스캔 결과의 메뉴 1건. 사용자 표시용.
 * 메뉴 사진은 스캔 가게의 가게 메뉴 사진만. AI 반환 이미지 URL 미사용.
 *
 * <p>V14 전까지 레거시(룰엔진)·Judge 결과 공존. Judge 전용 필드는 레거시 메뉴에서 null.
 *
 * @param scanMenuId 스캔 메뉴 id
 * @param displayOrder 표시 순서
 * @param menuNameKo 한국어 메뉴명
 * @param menuNameEn 영어 메뉴명
 * @param descriptionKo 한국어 메뉴 설명
 * @param descriptionEn 영어 메뉴 설명
 * @param priceText 가격 문자열
 * @param imageUrl 가게 메뉴 사진 공개 주소, null 가능
 * @param imageAttribution 사진 출처 표기, imageUrl 과 함께 null
 * @param isSpicy 매움 여부
 * @param riskLevel 위험도 (danger | caution | safe)
 * @param status 판정 상태 (completed | partial_failed | failed), Judge 전용
 * @param evidenceBasis 판정 근거 (confirmed | estimated | unknown), Judge 전용
 * @param informationStatus 재료 정보 충분도 (complete | partial | none), Judge 전용
 * @param staffAnswerStatus 직원 응답 상태 (none | partial | complete), Judge 전용
 * @param riskIngredients 위험 재료, Judge 전용
 * @param hits 위험 태그
 * @param message 레거시 {ko,en,ar} 또는 Judge {verdict,reason,notes}, null 가능
 * @param ownerCard 레거시 사장님 소통 카드, Judge 메뉴는 null
 */
@Schema(description = "스캔 결과 메뉴 항목")
public record MenuResult(
        UUID scanMenuId,
        Integer displayOrder,
        String menuNameKo,
        String menuNameEn,
        String descriptionKo,
        String descriptionEn,
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
        List<String> hits,
        @Schema(description = "레거시 {ko,en,ar} 또는 Judge {verdict,reason,notes}") JsonNode message,
        OwnerCard ownerCard) {}
