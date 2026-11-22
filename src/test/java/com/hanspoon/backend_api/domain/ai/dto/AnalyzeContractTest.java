package com.hanspoon.backend_api.domain.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.domain.ai.dto.analyze.EvidenceRef;
import com.hanspoon.backend_api.domain.ai.dto.analyze.JudgeRequest;
import com.hanspoon.backend_api.domain.ai.dto.analyze.JudgeResponse;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.Candidate;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.CandidateKnowledge;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.CandidateLookup;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.ExactFeedback;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.ExactFeedbackSlice;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.GraphIngredient;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.IngredientComposition;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.IngredientGraph;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.IngredientTag;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.ItemKnowledge;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.MenuIngredientPrior;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.MenuRecipeCorpus;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.OntologyMenu;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.OntologySlice;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.PriorSlice;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.RecipeIngredient;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.SourceReliability;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.SourceWeight;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.StorePrior;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.WebSearchCache;
import com.hanspoon.backend_api.domain.ai.dto.analyze.PrepareResponse;
import com.hanspoon.backend_api.domain.ai.dto.analyze.PreparedItem;
import com.hanspoon.backend_api.domain.ai.dto.analyze.SliceStatus;
import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleProfile;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** AI docs/0-agent-supervisor.md §1-3 예시 JSON 과의 계약 일치. */
class AnalyzeContractTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void judgeRequestSerializesToDocumentedShape() throws IOException {
        JsonNode expected = fixture("judge-request.json");

        JudgeRequest request = new JudgeRequest(
                "3.0",
                UUID.fromString("0199a2f0-0000-7000-8000-000000000001"),
                UUID.fromString("0199a2f0-0000-7000-8000-000000000101"),
                "scan-123",
                123456L,
                expected.get("prepared_items_hash").asString(),
                "2026-10-12T03:00:00Z",
                expected.get("prepare_signals"),
                expected.get("prepared_items"),
                documentedBundle(),
                new RuleProfile("halal", false, null, false, List.of(), false),
                "ko");

        JsonNode actual = mapper.readTree(mapper.writeValueAsString(request));
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void notFoundSlicesUseDocumentedEmptyShape() {
        assertThat(mapper.writeValueAsString(OntologySlice.notFound()))
                .isEqualTo("{\"status\":\"not_found\",\"menu\":null,\"recipe_ingredients\":[],"
                        + "\"menu_recipe_corpora\":[],\"menu_ingredient_priors\":[],\"cache\":null}");
        assertThat(mapper.writeValueAsString(ExactFeedbackSlice.notFound()))
                .isEqualTo("{\"status\":\"not_found\",\"exact\":null}");
        assertThat(mapper.writeValueAsString(PriorSlice.notFound()))
                .isEqualTo("{\"status\":\"not_found\",\"store\":[],\"evidence\":[]}");
        assertThat(mapper.writeValueAsString(CandidateLookup.notFound()))
                .isEqualTo("{\"status\":\"not_found\",\"candidates\":[]}");
    }

    @Test
    void prepareResponseKeepsRawItemsAndReadsDisplayFields() throws IOException {
        JsonNode raw = fixture("prepare-response.json");

        PrepareResponse response = mapper.treeToValue(raw, PrepareResponse.class);
        List<PreparedItem> items =
                List.of(mapper.treeToValue(response.preparedItems().get(0), PreparedItem.class));

        assertThat(response.usable()).isTrue();
        assertThat(response.prepareId()).isEqualTo(UUID.fromString("0199a2f0-0000-7000-8000-000000000101"));
        assertThat(response.preparedItems()).isEqualTo(raw.get("prepared_items"));
        assertThat(response.prepareSignals()).isEqualTo(raw.get("prepare_signals"));
        assertThat(items.getFirst().itemId()).isEqualTo("scan-123:0");
        assertThat(items.getFirst().displayName()).isEqualTo("김치 찌개");
        assertThat(items.getFirst().priceText()).isEqualTo("8,000원");
        assertThat(items.getFirst().spicyObserved()).isFalse();
    }

    @Test
    void judgeResponseParsesDocumentedExample() throws IOException {
        JudgeResponse response = mapper.treeToValue(fixture("judge-response.json"), JudgeResponse.class);

        JudgeResponse.ItemResult item = response.items().getFirst();
        assertThat(response.bundleHash()).startsWith("sha256:");
        assertThat(item.riskLevel()).isEqualTo(RiskLevel.DANGER);
        assertThat(item.ownerAnswerStatus()).isEqualTo("complete");
        assertThat(item.riskIngredients().getFirst().ingredient().localizedName())
                .isEqualTo("돼지고기");
        assertThat(item.message().notes().getFirst().code()).isEqualTo("hard_to_remove");
        assertThat(response.evidence().getFirst().observedAt()).isEqualTo(Instant.parse("2026-10-08T03:00:00Z"));
    }

    private KnowledgeBundle documentedBundle() {
        IngredientGraph graph = new IngredientGraph(
                SliceStatus.OK,
                List.of(
                        new GraphIngredient(109, "김치", "김치", "김치", "fermented"),
                        new GraphIngredient(110, "액젓", "액젓", "액젓", "fermented")),
                List.of(),
                List.of(new IngredientTag(110, "is_fish")),
                List.of(new IngredientComposition(109, 110, "has_unclear_jeotgal")),
                List.of());
        CandidateKnowledge knowledge = new CandidateKnowledge(
                1001,
                new ExactFeedbackSlice(SliceStatus.OK, new ExactFeedback(1001, List.of(), List.of())),
                new OntologySlice(
                        SliceStatus.OK,
                        new OntologyMenu(1001, null, null, 11L, "curated", List.of("has_unclear_jeotgal"), 7),
                        List.of(new RecipeIngredient(109)),
                        List.of(new MenuRecipeCorpus("10000recipe", 57)),
                        List.of(new MenuIngredientPrior("10000recipe", 109, 41)),
                        null),
                new PriorSlice(
                        SliceStatus.OK,
                        List.of(new StorePrior(101, 8.0, 2.0, List.of("evidence-prior-001"))),
                        List.of(new EvidenceRef(
                                "evidence-prior-001",
                                "soft",
                                "ocr_variant_tag",
                                "ingredient_evidence_log:9001",
                                "approved",
                                0.5,
                                Instant.parse("2026-10-12T03:00:00Z")))));
        ItemKnowledge item = new ItemKnowledge(
                "scan-123:0",
                new CandidateLookup(SliceStatus.OK, List.of(new Candidate(1001, 11L, "김치찌개", 1.0, "canonical"))),
                List.of(knowledge),
                OntologySlice.notFound(),
                WebSearchCache.notFound());
        return new KnowledgeBundle(
                "3.0",
                graph,
                List.of(item),
                new SourceReliability(
                        SliceStatus.OK,
                        List.of(
                                new SourceWeight("corpus:10000recipe", 0.8),
                                new SourceWeight("web_search", 0.5),
                                new SourceWeight("curated", 1.0))));
    }

    private JsonNode fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/ai/analyze/" + name)) {
            return mapper.readTree(in);
        }
    }
}
