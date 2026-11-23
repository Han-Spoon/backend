package com.hanspoon.backend_api.domain.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 직원 질문 1건. 문구·재료명은 ko 와 스캔 locale.
 *
 * @param questionId 직원 응답 저장 경로 값
 * @param cardId AI 카드 id
 * @param target 질문 대상 (ingredient | flag)
 * @param ingredientName 재료명, flag 질문이면 null
 * @param ambiguityFlag 애매한 부분 플래그, ingredient 질문이면 null
 * @param askedTags 플래그 질문의 태그(정렬), ingredient 질문이면 null
 * @param questionText 직원에게 보여 줄 문구
 * @param status 질문 상태 (pending | answered | expired)
 * @param answer 직원 답 (present | absent | unknown), 답 전 null
 * @param answeredAt 답한 시각, 답 전 null
 */
@Schema(description = "직원 질문")
public record StaffQuestionResult(
        UUID questionId,
        String cardId,
        String target,
        Map<String, String> ingredientName,
        String ambiguityFlag,
        List<String> askedTags,
        Map<String, String> questionText,
        String status,
        String answer,
        Instant answeredAt) {}
