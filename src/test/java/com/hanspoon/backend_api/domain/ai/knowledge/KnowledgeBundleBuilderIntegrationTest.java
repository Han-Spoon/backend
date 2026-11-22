package com.hanspoon.backend_api.domain.ai.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.Candidate;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.CandidateKnowledge;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.ItemKnowledge;
import com.hanspoon.backend_api.domain.ai.dto.analyze.SliceStatus;
import com.hanspoon.backend_api.domain.ai.knowledge.KnowledgeBundleBuilder.BuiltBundle;
import com.hanspoon.backend_api.domain.ai.knowledge.KnowledgeBundleBuilder.BundleItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class KnowledgeBundleBuilderIntegrationTest {

    @Autowired
    private KnowledgeBundleBuilder builder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tag;
    private long porkId;
    private long sauceId;
    private long stewId;
    private long storeId;
    private long storeMenuId;
    private long confirmationId;

    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        porkId = id(
                """
                insert into ingredients (name_ko, name_i18n, taxonomy_category, tag_codes)
                values (?, '{"en":"pork"}'::jsonb, 'base', '{is_pork}') returning id
                """,
                "돼지고기" + tag);
        sauceId =
                id("insert into ingredients (name_ko, taxonomy_category) values (?, 'sauce') returning id", "양념" + tag);
        jdbcTemplate.update(
                "insert into ingredient_aliases (ingredient_id, alias_ko) values (?, ?)", porkId, "돈육" + tag);
        jdbcTemplate.update(
                """
                insert into ingredient_compositions (parent_ingredient_id, child_ingredient_id, ambiguity_flag, tag_codes)
                values (?, ?, 'has_hidden_animal', '{is_pork}')
                """,
                sauceId,
                porkId);
        long categoryId = id("insert into menu_categories (name_ko) values (?) returning id", "찌개" + tag);
        stewId = id(
                """
                insert into menus (name_ko, category_id, ambiguity_flags, source, needs_review)
                values (?, ?, '{has_unclear_jeotgal}', 'curated', false) returning id
                """,
                "김치찌개" + tag,
                categoryId);
        jdbcTemplate.update("insert into menu_aliases (menu_id, alias_ko) values (?, ?)", stewId, "김치찌게" + tag);
        jdbcTemplate.update(
                "insert into recipe_ingredients (menu_id, ingredient_id, evidence_type) values (?, ?, 'explicit')",
                stewId,
                porkId);
        long batchId = id(
                """
                insert into knowledge_import_batches (source, source_version, status, finished_at)
                values ('10000recipe_prior', ?, 'completed', now()) returning id
                """,
                tag);
        jdbcTemplate.update(
                "insert into menu_recipe_corpora (menu_id, corpus, n_total, import_batch_id) values (?, '10000recipe', 57, ?)",
                stewId,
                batchId);
        jdbcTemplate.update(
                """
                insert into menu_ingredient_priors (menu_id, corpus, ingredient_id, n_total, k_count)
                values (?, '10000recipe', ?, 57, 41)
                """,
                stewId,
                porkId);
        jdbcTemplate.update(
                """
                insert into source_reliability (source_type, agree_count, compared_count)
                values ('corpus:10000recipe', 3, 4) on conflict do nothing
                """);

        storeId = insertStore();
        storeMenuId = id(
                "insert into store_menus (store_id, name_normalized, display_name) values (?, ?, '김치찌개') returning id",
                storeId,
                "김치찌개" + tag);
        confirmationId = id(
                """
                insert into ingredient_confirmations (store_id, store_menu_id, ingredient_id, present, confirmed_at)
                values (?, ?, ?, true, '2026-11-20T03:00:00Z') returning id
                """,
                storeId,
                storeMenuId,
                porkId);
        jdbcTemplate.update(
                """
                insert into ingredient_confirmations
                    (store_id, store_menu_id, ingredient_id, present, confirmed_at, invalidated_at)
                values (?, ?, ?, false, '2026-11-01T00:00:00Z', '2026-11-02T00:00:00Z')
                """,
                storeId,
                storeMenuId,
                sauceId);
        jdbcTemplate.update(
                """
                insert into flag_confirmations (store_id, store_menu_id, ambiguity_flag, asked_tags, present, confirmed_at)
                values (?, ?, 'has_unclear_jeotgal', '{is_fish,is_shrimp}', false, '2026-11-20T03:00:00Z')
                """,
                storeId,
                storeMenuId);
    }

    @Test
    void storeScanBuildsCandidatesOntologyAndConfirmations() {
        BuiltBundle built = builder.build(
                storeId,
                "en",
                List.of(
                        new BundleItem("s:0", "김치찌개" + tag, storeMenuId),
                        new BundleItem("s:1", "김치찌게" + tag, null),
                        new BundleItem("s:2", "김치찌개" + tag + "곱빼기", null),
                        new BundleItem("s:3", "■", null)));
        KnowledgeBundle bundle = built.bundle();

        assertThat(Instant.parse(built.knowledgeRevision())).isNotNull();
        assertThat(bundle.bundleSchemaVersion()).isEqualTo("3.0");
        assertThat(bundle.items()).extracting(ItemKnowledge::itemId).containsExactly("s:0", "s:1", "s:2", "s:3");

        ItemKnowledge exact = bundle.items().get(0);
        assertThat(exact.candidateLookup().candidates())
                .containsExactly(new Candidate(
                        stewId,
                        exact.candidateLookup().candidates().getFirst().menuCategoryId(),
                        "김치찌개" + tag,
                        1.0,
                        "canonical"));
        CandidateKnowledge knowledge = exact.candidateKnowledge().getFirst();
        assertThat(knowledge.menuId()).isEqualTo(stewId);
        assertThat(knowledge.exactFeedback().status()).isEqualTo(SliceStatus.OK);
        assertThat(knowledge.exactFeedback().exact().menuId()).isEqualTo(stewId);
        // 무효화된 확정값 제외
        assertThat(knowledge.exactFeedback().exact().confirmations())
                .singleElement()
                .satisfies(confirmation -> {
                    assertThat(confirmation.ingredient().ingredientId()).isEqualTo(porkId);
                    assertThat(confirmation.status()).isEqualTo("present");
                    assertThat(confirmation.constraintTags()).isEmpty();
                    assertThat(confirmation.evidence().sourceRef())
                            .isEqualTo("ingredient_confirmations:" + confirmationId);
                    assertThat(confirmation.evidence().evidenceClass()).isEqualTo("hard");
                    assertThat(confirmation.evidence().verificationStatus()).isEqualTo("owner_confirmed");
                });
        assertThat(knowledge.exactFeedback().exact().flagConfirmations())
                .singleElement()
                .satisfies(flag -> {
                    assertThat(flag.constraintTags()).containsExactly("is_fish", "is_shrimp");
                    assertThat(flag.status()).isEqualTo("absent");
                });
        assertThat(knowledge.ontology().status()).isEqualTo(SliceStatus.OK);
        assertThat(knowledge.ontology().menu().ambiguityFlags()).containsExactly("has_unclear_jeotgal");
        assertThat(knowledge.ontology().menu().knowledgeRevision()).isEqualTo(1);
        assertThat(knowledge.ontology().recipeIngredients())
                .extracting(r -> r.ingredientId())
                .containsExactly(porkId);
        assertThat(knowledge.ontology().menuRecipeCorpora()).singleElement().satisfies(c -> {
            assertThat(c.corpus()).isEqualTo("10000recipe");
            assertThat(c.nTotal()).isEqualTo(57);
        });
        assertThat(knowledge.ontology().menuIngredientPriors()).singleElement().satisfies(p -> assertThat(p.kCount())
                .isEqualTo(41));
        assertThat(knowledge.ontology().cache()).isNull();
        assertThat(knowledge.priors().status()).isEqualTo(SliceStatus.NOT_FOUND);
        assertThat(exact.fallbackOntology().status()).isEqualTo(SliceStatus.NOT_FOUND);
        assertThat(exact.webSearchCache().status()).isEqualTo(SliceStatus.NOT_FOUND);

        ItemKnowledge alias = bundle.items().get(1);
        assertThat(alias.candidateLookup().candidates().getFirst().matchType()).isEqualTo("synonym");
        assertThat(alias.candidateLookup().candidates().getFirst().score()).isEqualTo(0.98);
        // 가게 메뉴 연결이 없으면 확정값 not_found
        assertThat(alias.candidateKnowledge().getFirst().exactFeedback().status())
                .isEqualTo(SliceStatus.NOT_FOUND);

        ItemKnowledge fuzzy = bundle.items().get(2);
        assertThat(fuzzy.candidateLookup().candidates())
                .hasSizeLessThanOrEqualTo(5)
                .anySatisfy(candidate -> {
                    assertThat(candidate.menuId()).isEqualTo(stewId);
                    assertThat(candidate.matchType()).isEqualTo("fuzzy");
                    assertThat(candidate.score()).isPositive().isLessThanOrEqualTo(0.94);
                });
        assertThat(fuzzy.candidateKnowledge())
                .extracting(CandidateKnowledge::menuId)
                .containsExactlyInAnyOrderElementsOf(fuzzy.candidateLookup().candidates().stream()
                        .map(Candidate::menuId)
                        .toList());

        ItemKnowledge empty = bundle.items().get(3);
        assertThat(empty.candidateLookup().status()).isEqualTo(SliceStatus.NOT_FOUND);
        assertThat(empty.candidateKnowledge()).isEmpty();

        var graph = bundle.ingredientGraph();
        assertThat(graph.status()).isEqualTo(SliceStatus.OK);
        assertThat(graph.ingredients()).anySatisfy(ingredient -> {
            assertThat(ingredient.ingredientId()).isEqualTo(porkId);
            assertThat(ingredient.localizedName()).isEqualTo("pork");
            assertThat(ingredient.taxonomyCategory()).isEqualTo("base");
        });
        assertThat(graph.ingredients()).anySatisfy(ingredient -> {
            assertThat(ingredient.ingredientId()).isEqualTo(sauceId);
            assertThat(ingredient.localizedName()).isNull();
        });
        assertThat(graph.tags()).contains(new KnowledgeBundle.IngredientTag(porkId, "is_pork"));
        assertThat(graph.aliases()).contains(new KnowledgeBundle.IngredientAlias(porkId, "돈육" + tag));
        assertThat(graph.compositions())
                .contains(new KnowledgeBundle.IngredientComposition(sauceId, porkId, "has_hidden_animal"));
        assertThat(graph.compositionTags())
                .contains(new KnowledgeBundle.IngredientCompositionTag(sauceId, porkId, "is_pork"));
        assertThat(bundle.sourceReliability().status()).isEqualTo(SliceStatus.OK);
        assertThat(bundle.sourceReliability().weights())
                .anySatisfy(weight -> assertThat(weight.sourceKey()).isEqualTo("corpus:10000recipe"));
    }

    @Test
    void storelessScanSendsNoStoreScopedKnowledge() {
        BuiltBundle built = builder.build(null, "ko", List.of(new BundleItem("s:0", "김치찌개" + tag, null)));

        CandidateKnowledge knowledge =
                built.bundle().items().getFirst().candidateKnowledge().getFirst();
        assertThat(knowledge.exactFeedback()).isEqualTo(KnowledgeBundle.ExactFeedbackSlice.notFound());
        assertThat(knowledge.priors()).isEqualTo(KnowledgeBundle.PriorSlice.notFound());
        assertThat(knowledge.ontology().cache()).isNull();
        assertThat(built.bundle().ingredientGraph().ingredients()).anySatisfy(ingredient -> {
            assertThat(ingredient.ingredientId()).isEqualTo(sauceId);
            assertThat(ingredient.localizedName()).isEqualTo("양념" + tag);
        });
    }

    private long id(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    /** 반경 검색 테스트와 겹치지 않는 좌표. */
    private long insertStore() {
        long categoryId = id(
                "insert into store_categories(code, name, level) values (?, '음식', 1) returning id",
                "I" + UUID.randomUUID().toString().substring(0, 5));
        long batchId = id(
                "insert into store_import_batches(source, source_version) values ('sbiz', ?) returning id",
                String.valueOf(100000 + Math.abs(UUID.randomUUID().hashCode() % 899999)));
        return id(
                """
                insert into stores(sbiz_store_no, name, category_id, lat, lng, origin, last_batch_id)
                values (?, '한스푼', ?, 33.4, 126.5, 'sbiz', ?) returning id
                """,
                "S" + UUID.randomUUID().toString().substring(0, 8),
                categoryId,
                batchId);
    }
}
