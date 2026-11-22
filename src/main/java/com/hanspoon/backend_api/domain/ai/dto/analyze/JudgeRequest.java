package com.hanspoon.backend_api.domain.ai.dto.analyze;

import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleProfile;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Judge 요청 (백엔드 → AI).
 *
 * <p>preparedItems·prepareSignals·preparedItemsHash 는 같은 prepareId 의 Prepare 응답 원본 그대로.
 * storeId null 은 가게 없는 스캔.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record JudgeRequest(
        String schemaVersion,
        UUID traceId,
        UUID prepareId,
        String scanSessionId,
        Long storeId,
        String preparedItemsHash,
        String knowledgeRevision,
        JsonNode prepareSignals,
        JsonNode preparedItems,
        KnowledgeBundle knowledgeBundle,
        RuleProfile userProfile,
        String locale) {}
