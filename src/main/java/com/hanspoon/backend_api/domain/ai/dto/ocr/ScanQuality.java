package com.hanspoon.backend_api.domain.ai.dto.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 품질/재촬영 판단 메타데이터.
 *
 * @param status 품질 상태 ("usable" | "low_confidence" | "needs_retake")
 * @param score 종합 품질 점수 (0~100)
 * @param rawLineCount OCR 원본 라인 수
 * @param priceMatchCount 가격 매칭된 메뉴 수
 * @param priceMatchRatio 가격 매칭 비율
 * @param imageWidth 이미지 가로 px
 * @param imageHeight 이미지 세로 px
 * @param imageQuality 이미지 품질 세부 분석
 * @param retakeSuggestions 재촬영 가이드 문구
 * @param reasons 품질 저하 사유
 * @param preprocessingAttempted 전처리 이미지 생성 시도 여부
 * @param preprocessingApplied 최종 결과에 전처리 이미지가 사용됐는지 여부
 * @param selectedOcrAttempt 선택된 OCR 시도(original | preprocessed)
 * @param ocrAttemptCount CLOVA OCR 호출 횟수
 * @param retrySkippedReason 재시도를 생략한 이유
 * @param ocrProcessingTimeMs AI OCR 처리 시간(ms)
 * @param ocrBudgetMs AI OCR 시간 예산(ms)
 * @param imageFetchSource 이미지 조회 경로(s3_iam | presigned_url)
 * @param queueWaitMs AI 내부 처리 슬롯 대기 시간(ms)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScanQuality(
        String status,
        Integer score,
        Integer rawLineCount,
        Integer priceMatchCount,
        Double priceMatchRatio,
        Integer imageWidth,
        Integer imageHeight,
        ImageQuality imageQuality,
        List<String> retakeSuggestions,
        List<String> reasons,
        Boolean preprocessingAttempted,
        Boolean preprocessingApplied,
        String selectedOcrAttempt,
        Integer ocrAttemptCount,
        String retrySkippedReason,
        Long ocrProcessingTimeMs,
        Long ocrBudgetMs,
        String imageFetchSource,
        Long queueWaitMs) {}
