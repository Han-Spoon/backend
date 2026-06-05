package com.hanspoon.backend_api.domain.ai.dto.ruleengine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hanspoon.backend_api.domain.ai.dto.ocr.MenuImage;
import com.hanspoon.backend_api.domain.ai.dto.ocr.ScanQuality;
import com.hanspoon.backend_api.domain.ai.dto.ocr.ScanSession;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 룰엔진 응답 (AI 서비스 → 백엔드). {@code engine.analyze_all} 은 입력 OCR 결과를 복사하되
 * scan_session.risky_menu_count 를 채우고 menu_analyses[] 를 룰엔진 출력으로 교체한다.
 *
 * <p>주의: 룰엔진 출력 menu_analyses 는 OCR 의 price_text/description/display_order 를 보존하지
 * 않는다. 원본 OCR 결과와의 머지는 배열 index 순서로 한다.
 *
 * @param scanSession riskyMenuCount 가 채워진 scan_session
 * @param menuImage menu_image (변경 없음)
 * @param scanQuality scan_quality (변경 없음)
 * @param menuAnalyses 룰엔진 위험도 판정 목록
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleEngineResponse(
        ScanSession scanSession, MenuImage menuImage, ScanQuality scanQuality, List<RuleMenuAnalysis> menuAnalyses) {}
