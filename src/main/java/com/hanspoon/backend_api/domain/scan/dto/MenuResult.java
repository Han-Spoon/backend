package com.hanspoon.backend_api.domain.scan.dto;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RiskReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 스캔 결과의 메뉴 1건 (OCR + RuleEngine 머지).
 *
 * @param displayOrder 표시 순서
 * @param menuNameKo 한국어 메뉴명
 * @param menuNameEn 영어 메뉴명
 * @param priceText 가격 문자열
 * @param isSpicy 매움 여부
 * @param riskLevel 위험도 (danger | caution | safe)
 * @param needGpt GPT 추가 판단 필요 여부
 * @param hitTags 1차 히트 태그
 * @param triggeredFlags 발동된 애매함 플래그
 * @param riskReasons 사용자 안내 사유 (needGpt=true 면 아직 null — 후속 GPT 단계가 채움)
 */
@Schema(description = "스캔 결과 메뉴 항목")
public record MenuResult(
        Integer displayOrder,
        String menuNameKo,
        String menuNameEn,
        String priceText,
        Boolean isSpicy,
        RiskLevel riskLevel,
        Boolean needGpt,
        List<String> hitTags,
        List<String> triggeredFlags,
        List<RiskReason> riskReasons) {}
