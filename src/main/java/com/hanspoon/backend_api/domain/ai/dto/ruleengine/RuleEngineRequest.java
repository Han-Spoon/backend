package com.hanspoon.backend_api.domain.ai.dto.ruleengine;

import com.hanspoon.backend_api.domain.ai.dto.ocr.OcrResponse;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 룰엔진 요청 (백엔드 → AI 서비스). {@code engine.analyze_all(ocr_result, profile)} 가 OCR 결과
 * 전체를 입력으로 받으므로 OCR 응답을 그대로 실어 전달한다.
 *
 * @param profile 사용자 프로필
 * @param ocrResult OCR 응답 전체
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RuleEngineRequest(RuleProfile profile, OcrResponse ocrResult) {}
