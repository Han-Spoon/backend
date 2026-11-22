package com.hanspoon.backend_api.domain.ai.dto.analyze;

import tools.jackson.databind.JsonNode;

/** Judge 응답과 원본 JSON(judge_raw 보관용). */
public record JudgeResult(JudgeResponse response, JsonNode raw) {}
