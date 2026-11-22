package com.hanspoon.backend_api.domain.ai.dto.analyze;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Judge 응답 (AI → 백엔드). items 는 sourceIndex 순. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record JudgeResponse(
        String schemaVersion,
        UUID traceId,
        UUID prepareId,
        String scanSessionId,
        Long storeId,
        String preparedItemsHash,
        String bundleHash,
        String knowledgeRevision,
        List<ItemResult> items,
        List<EvidenceRef> evidence,
        List<PersistenceCommand> persistenceCommands,
        List<NodeWarning> warnings,
        List<NodeError> errors) {

    /** 메뉴별 판정. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ItemResult(
            String itemId,
            int sourceIndex,
            Long menuId,
            Long menuCategoryId,
            String normalizationStatus,
            String matchType,
            String selectionMethod,
            String status,
            String route,
            RiskLevel riskLevel,
            String evidenceBasis,
            String informationStatus,
            String ownerAnswerStatus,
            List<RiskIngredient> riskIngredients,
            List<String> matchedTags,
            List<String> evidenceRefs,
            DecisionMessage message,
            List<OwnerCard> ownerCards,
            List<NodeWarning> warnings,
            List<NodeError> errors) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RiskIngredient(
            LocalizedIngredient ingredient,
            List<String> constraintTags,
            List<String> matchedTags,
            String role,
            String status,
            Double posteriorMean,
            Double confidence,
            String priorSource,
            List<String> evidenceRefs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record LocalizedIngredient(Long ingredientId, String canonicalName, String localizedName) {}

    /** 판정 한 줄·이유·안내. 요청 locale 한 언어. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DecisionMessage(String verdict, String reason, List<MessageNote> notes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MessageNote(String code, String text) {}

    /** 직원 질문 카드. question 은 ko + 요청 locale. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OwnerCard(
            String itemId,
            String cardId,
            Long menuId,
            IngredientRef ingredient,
            String ambiguityFlag,
            List<String> constraintTags,
            Map<String, String> question,
            List<String> reasonCodes,
            List<String> evidenceRefs) {}

    /** 저장 명령. 실제 저장 성공 아님. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PersistenceCommand(
            String commandId,
            String operationId,
            String itemId,
            String cardId,
            String action,
            String idempotencyKey,
            JsonNode payload) {}
}
