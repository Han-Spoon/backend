package com.hanspoon.backend_api.domain.ai.dto.analyze;

import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Judge 판정용 DB 지식 묶음. AI 계약(schema 3.0) closed schema.
 *
 * <p>status 가 ok 가 아니면 배열은 비우고 단일 값은 null.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record KnowledgeBundle(
        String bundleSchemaVersion,
        IngredientGraph ingredientGraph,
        List<ItemKnowledge> items,
        SourceReliability sourceReliability) {

    public static final String SCHEMA_VERSION = "3.0";

    /** 공통 재료 그래프. 한 Bundle 에 한 번. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record IngredientGraph(
            SliceStatus status,
            List<GraphIngredient> ingredients,
            List<IngredientAlias> aliases,
            List<IngredientTag> tags,
            List<IngredientComposition> compositions,
            List<IngredientCompositionTag> compositionTags) {}

    /** localizedName: 요청 locale 이름, 없으면 null. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record GraphIngredient(
            long ingredientId,
            String canonicalName,
            String nameNormalized,
            String localizedName,
            String taxonomyCategory) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record IngredientAlias(long ingredientId, String aliasNormalized) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record IngredientTag(long ingredientId, String tagCode) {}

    /** part_of 관계만. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record IngredientComposition(long parentIngredientId, long childIngredientId, String ambiguityFlag) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record IngredientCompositionTag(long parentIngredientId, long childIngredientId, String tagCode) {}

    /** 메뉴 항목별 지식. 후보마다 candidateKnowledge 정확히 하나. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ItemKnowledge(
            String itemId,
            CandidateLookup candidateLookup,
            List<CandidateKnowledge> candidateKnowledge,
            OntologySlice fallbackOntology,
            WebSearchCache webSearchCache) {}

    /** score 내림차순·menuId 오름차순, 최대 5. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CandidateLookup(SliceStatus status, List<Candidate> candidates) {

        public static CandidateLookup notFound() {
            return new CandidateLookup(SliceStatus.NOT_FOUND, List.of());
        }
    }

    /** matchType: canonical | spacing | synonym | ocr_correction | fuzzy. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Candidate(long menuId, Long menuCategoryId, String canonicalName, double score, String matchType) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CandidateKnowledge(
            long menuId, ExactFeedbackSlice exactFeedback, OntologySlice ontology, PriorSlice priors) {}

    /** 가게 확정값. 가게 없는 스캔은 not_found. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ExactFeedbackSlice(SliceStatus status, ExactFeedback exact) {

        public static ExactFeedbackSlice notFound() {
            return new ExactFeedbackSlice(SliceStatus.NOT_FOUND, null);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ExactFeedback(
            long menuId, List<Confirmation> confirmations, List<FlagConfirmation> flagConfirmations) {}

    /** status: present | absent | unknown. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Confirmation(
            IngredientRef ingredient, List<String> constraintTags, String status, EvidenceRef evidence) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FlagConfirmation(
            String ambiguityFlag, List<String> constraintTags, String status, EvidenceRef evidence) {}

    /** DB 원재료 행. 펼친 결과 아님. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OntologySlice(
            SliceStatus status,
            OntologyMenu menu,
            List<RecipeIngredient> recipeIngredients,
            List<MenuRecipeCorpus> menuRecipeCorpora,
            List<MenuIngredientPrior> menuIngredientPriors,
            MenuIngredientCache cache) {

        public static OntologySlice notFound() {
            return new OntologySlice(SliceStatus.NOT_FOUND, null, List.of(), List.of(), List.of(), null);
        }
    }

    /** source: curated | web_search_generated | variant_generated. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OntologyMenu(
            long menuId,
            Long baseMenuId,
            String remainToken,
            Long menuCategoryId,
            String source,
            List<String> ambiguityFlags,
            long knowledgeRevision) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RecipeIngredient(long ingredientId) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MenuRecipeCorpus(String corpus, int nTotal) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MenuIngredientPrior(String corpus, long ingredientId, int kCount) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MenuIngredientCache(long menuRevision, JsonNode ingredientsSnapshot) {}

    /** 가게 prior. 가게 없는 스캔은 not_found. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PriorSlice(SliceStatus status, List<StorePrior> store, List<EvidenceRef> evidence) {

        public static PriorSlice notFound() {
            return new PriorSlice(SliceStatus.NOT_FOUND, List.of(), List.of());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record StorePrior(long ingredientId, double alpha, double beta, List<String> evidenceRefs) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record WebSearchCache(SliceStatus status, List<JsonNode> cachedSources) {

        public static WebSearchCache notFound() {
            return new WebSearchCache(SliceStatus.NOT_FOUND, List.of());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SourceReliability(SliceStatus status, List<SourceWeight> weights) {

        public static SourceReliability notFound() {
            return new SourceReliability(SliceStatus.NOT_FOUND, List.of());
        }
    }

    /** sourceKey: corpus:{corpus} | curated | web_search. weight 0~1. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SourceWeight(String sourceKey, double weight) {}
}
