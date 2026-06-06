package com.hanspoon.backend_api.domain.ai.dto.gpt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * GPT 에스컬레이션 최종 출력. 노션 설계 기준이되 safety_level 대신 {@link RiskLevel} 로 통일.
 *
 * <p><b>주의:</b> 본 DTO 는 확정 계약에 맞춰 정의만 해둔 것으로, 현재 AI 레포에 해당 엔드포인트가
 * 미구현이다. 전송 방식(AI 서비스 내부 처리 vs 백엔드 직접 호출) 확정 후 {@code AiClient} 에 호출
 * 메서드를 추가한다.
 *
 * @param menuNameKo 한국어 메뉴명
 * @param riskLevel 위험도 (danger | caution | safe)
 * @param hitTags 위험 태그
 * @param hiddenIngredients 추론된 숨은 재료
 * @param risks 위험 설명 목록
 * @param confidence 신뢰도 (0.0~1.0)
 * @param message 사용자 안내문 (다국어)
 * @param ownerCard 사장님 소통 카드 (옵션)
 * @param recommendation 권장 행동
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record GptResult(
        String menuNameKo,
        RiskLevel riskLevel,
        List<String> hitTags,
        List<HiddenIngredient> hiddenIngredients,
        List<GptRisk> risks,
        Double confidence,
        LocalizedText message,
        OwnerCard ownerCard,
        String recommendation) {}
