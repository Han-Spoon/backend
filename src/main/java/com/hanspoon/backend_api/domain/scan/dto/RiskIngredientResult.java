package com.hanspoon.backend_api.domain.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * Judge 위험 재료 1건.
 *
 * @param name 재료명. ko 와 스캔 locale
 * @param status 포함 여부 (present | absent | unknown)
 * @param posteriorMean 포함 확률 0~1, null 가능
 * @param matchedTags 식단과 겹친 태그
 * @param confidence 확률 신뢰도, null 가능
 * @param role 재료 역할, null 가능
 * @param priorSource 확률 출처, null 가능
 */
@Schema(description = "위험 재료")
public record RiskIngredientResult(
        Map<String, String> name,
        String status,
        Double posteriorMean,
        List<String> matchedTags,
        Double confidence,
        String role,
        String priorSource) {}
