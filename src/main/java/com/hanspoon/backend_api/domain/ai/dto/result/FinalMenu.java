package com.hanspoon.backend_api.domain.ai.dto.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * ai_result FinalOutput — 사용자에게 보여줄 최종 메뉴 1건. judged 의 menu_analyses 를 교체한 형태.
 *
 * @param menuName 메뉴명 (한국어)
 * @param riskLevel 위험도 (danger | caution | safe)
 * @param hits 위험 태그
 * @param message 사용자 안내문 (다국어), null 가능
 * @param ownerCard 사장님 소통 카드, null 가능
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinalMenu(
        String menuName, RiskLevel riskLevel, List<String> hits, FinalMessage message, OwnerCard ownerCard) {}
