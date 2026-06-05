package com.hanspoon.backend_api.domain.ai.dto.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * OCR 응답 (AI 서비스 → 백엔드). {@code ai_ocr/result_builder.build_final_result} 출력 스키마.
 * 이 단계의 menu_analyses[].risk_level 은 항상 null (룰엔진이 채움).
 *
 * @param scanSession scan_sessions 테이블 매핑 정보
 * @param menuImage menu_images 테이블 매핑 정보
 * @param scanQuality 품질/재촬영 판단 메타데이터
 * @param menuAnalyses 추출된 메뉴 분석 목록
 * @param gptQualityJudgment GPT 품질 평가 (옵션, 느슨한 구조라 JsonNode 로 보존)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record OcrResponse(
        ScanSession scanSession,
        MenuImage menuImage,
        ScanQuality scanQuality,
        List<MenuAnalysis> menuAnalyses,
        JsonNode gptQualityJudgment) {}
