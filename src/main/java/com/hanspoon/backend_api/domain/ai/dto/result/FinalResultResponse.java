package com.hanspoon.backend_api.domain.ai.dto.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hanspoon.backend_api.domain.ai.dto.ocr.ScanSession;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * ai_result(`POST /v1/result`) 응답. AI 는 judged_result 전체(scan_session/menu_image/scan_quality 포함)를 돌려주지만,
 * 백엔드는 가게 컨텍스트 동일성을 확인한 뒤 menu_analyses(=FinalMenu)를 소비한다.
 *
 * @param scanSession 전체 파이프라인에서 보존된 가게 컨텍스트
 * @param menuAnalyses 최종 표시용 메뉴 목록
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinalResultResponse(ScanSession scanSession, List<FinalMenu> menuAnalyses) {}
