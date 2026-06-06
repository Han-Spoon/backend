package com.hanspoon.backend_api.domain.ai.dto.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * ai_result(`POST /v1/result`) 응답. AI 는 judged_result 전체(scan_session/menu_image/scan_quality 포함)를 돌려주지만,
 * 백엔드는 menu_analyses(=FinalMenu) 만 소비하므로 그 외 키는 {@code @JsonIgnoreProperties} 로 무시한다.
 *
 * @param menuAnalyses 최종 표시용 메뉴 목록
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinalResultResponse(List<FinalMenu> menuAnalyses) {}
