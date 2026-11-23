package com.hanspoon.backend_api.domain.scan.dto;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 직원 응답 저장 결과. 화면은 메뉴·카운터를 이 값으로 갱신.
 *
 * @param question 답한 질문
 * @param menu 질문 메뉴의 현재 판정
 * @param counts 스캔 위험도 카운터
 */
@Schema(description = "직원 응답 저장 결과")
public record StaffAnswerResponse(StaffQuestionResult question, Menu menu, Counts counts) {

    /**
     * @param scanMenuId 스캔 메뉴 id
     * @param riskLevel 위험도 (danger | caution | safe)
     * @param staffAnswerStatus 직원 응답 상태 (partial | complete)
     */
    public record Menu(UUID scanMenuId, RiskLevel riskLevel, String staffAnswerStatus) {}

    public record Counts(int dangerCount, int cautionCount, int safeCount) {}
}
