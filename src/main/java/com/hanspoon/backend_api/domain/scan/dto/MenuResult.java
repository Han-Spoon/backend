package com.hanspoon.backend_api.domain.scan.dto;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.ai.dto.result.FinalMessage;
import com.hanspoon.backend_api.domain.ai.dto.result.OwnerCard;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 스캔 결과의 메뉴 1건 (OCR + ai_result 최종). 사용자 표시용.
 *
 * @param displayOrder 표시 순서
 * @param menuNameKo 한국어 메뉴명
 * @param menuNameEn 영어 메뉴명
 * @param priceText 가격 문자열
 * @param isSpicy 매움 여부
 * @param riskLevel 위험도 (danger | caution | safe)
 * @param hits 위험 태그
 * @param message 사용자 안내문 (다국어), null 가능
 * @param ownerCard 사장님 소통 카드, null 가능
 */
@Schema(description = "스캔 결과 메뉴 항목")
public record MenuResult(
        Integer displayOrder,
        String menuNameKo,
        String menuNameEn,
        String priceText,
        Boolean isSpicy,
        RiskLevel riskLevel,
        List<String> hits,
        FinalMessage message,
        OwnerCard ownerCard) {}
