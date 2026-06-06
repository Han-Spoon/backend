package com.hanspoon.backend_api.domain.ai.dto.ruleengine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hanspoon.backend_api.domain.ai.dto.common.EscalationCase;
import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 룰엔진 단일 메뉴 판정 결과 ({@code engine.analyze} 반환 dict).
 *
 * @param menuNameKo 한국어 메뉴명
 * @param isSpicy 매움 여부 (effective)
 * @param riskLevel 위험도 판정
 * @param hitTags 1차 명시/변형 재료 히트 태그
 * @param triggeredFlags 발동된 애매함 플래그
 * @param forbiddenTags 프로필에서 도출된 금지 태그
 * @param needGpt GPT 호출 필요 여부
 * @param escalationCase GPT 호출 사유 (중복 가능)
 * @param gptContext GPT 컨텍스트 (needGpt=false 면 null)
 * @param riskReasons 사용자 안내 사유 (needGpt=true 면 null, GPT 가 채움)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleMenuAnalysis(
        String menuNameKo,
        Boolean isSpicy,
        RiskLevel riskLevel,
        List<String> hitTags,
        List<String> triggeredFlags,
        List<String> forbiddenTags,
        Boolean needGpt,
        List<EscalationCase> escalationCase,
        GptContext gptContext,
        List<RiskReason> riskReasons) {}
