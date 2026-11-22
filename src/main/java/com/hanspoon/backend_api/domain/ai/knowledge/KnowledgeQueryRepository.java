package com.hanspoon.backend_api.domain.ai.knowledge;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** KnowledgeBundle 원본 행 조회. */
@Repository
public class KnowledgeQueryRepository {

    private static final double SYNONYM_SCORE = 0.98;
    private static final double FUZZY_MAX_SCORE = 0.94;
    private static final int FUZZY_SCAN_LIMIT = 10;

    private final NamedParameterJdbcTemplate jdbc;

    public KnowledgeQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 트랜잭션 시작 시각. 같은 스냅샷 식별값. */
    public Instant snapshotAt() {
        return jdbc.queryForObject("SELECT now()", Map.of(), Timestamp.class).toInstant();
    }

    public List<IngredientRow> findIngredients(String locale) {
        return jdbc.query(
                """
                SELECT id, name_ko, name_normalized, taxonomy_category, tag_codes,
                       COALESCE(name_i18n ->> :locale, CASE WHEN :locale = 'ko' THEN name_ko END) AS localized_name
                FROM ingredients
                ORDER BY id
                """,
                Map.of("locale", locale),
                (rs, i) -> new IngredientRow(
                        rs.getLong("id"),
                        rs.getString("name_ko"),
                        rs.getString("name_normalized"),
                        rs.getString("localized_name"),
                        rs.getString("taxonomy_category"),
                        strings(rs, "tag_codes")));
    }

    public List<IngredientAliasRow> findIngredientAliases() {
        return jdbc.query(
                "SELECT ingredient_id, alias_normalized FROM ingredient_aliases ORDER BY ingredient_id, alias_normalized",
                Map.of(),
                (rs, i) -> new IngredientAliasRow(rs.getLong("ingredient_id"), rs.getString("alias_normalized")));
    }

    public List<CompositionRow> findCompositions() {
        return jdbc.query(
                """
                SELECT parent_ingredient_id, child_ingredient_id, ambiguity_flag, tag_codes
                FROM ingredient_compositions
                WHERE relation_type = 'part_of'
                ORDER BY parent_ingredient_id, child_ingredient_id
                """,
                Map.of(),
                (rs, i) -> new CompositionRow(
                        rs.getLong("parent_ingredient_id"),
                        rs.getLong("child_ingredient_id"),
                        rs.getString("ambiguity_flag"),
                        strings(rs, "tag_codes")));
    }

    /** 표준 메뉴명·별칭 정확 일치. */
    public List<CandidateRow> findExactCandidates(String menuName) {
        return jdbc.query(
                """
                SELECT m.id, m.category_id, m.name_ko, 'canonical' AS match_type, 1.0 AS score
                FROM menus m
                WHERE m.name_normalized = normalize_food_name(:name)
                UNION ALL
                SELECT m.id, m.category_id, m.name_ko, 'synonym', :synonymScore
                FROM menu_aliases a
                JOIN menus m ON m.id = a.menu_id
                WHERE a.alias_normalized = normalize_food_name(:name)
                """,
                Map.of("name", menuName, "synonymScore", SYNONYM_SCORE),
                this::candidate);
    }

    /** 메뉴명·별칭 trigram 거리 상위 행. score = similarity, 최대 0.94. */
    public List<CandidateRow> findFuzzyCandidates(String menuName) {
        return jdbc.query(
                """
                SELECT m.id, m.category_id, m.name_ko, 'fuzzy' AS match_type,
                       LEAST(round(similarity(f.name, n.q)::numeric, 4), :maxScore) AS score
                FROM (SELECT normalize_food_name(:name) AS q) n
                CROSS JOIN LATERAL (
                    (SELECT id AS menu_id, name_normalized AS name FROM menus
                     ORDER BY name_normalized <-> n.q LIMIT :limit)
                    UNION ALL
                    (SELECT menu_id, alias_normalized FROM menu_aliases
                     ORDER BY alias_normalized <-> n.q LIMIT :limit)
                ) f
                JOIN menus m ON m.id = f.menu_id
                WHERE similarity(f.name, n.q) > 0
                """,
                Map.of("name", menuName, "maxScore", FUZZY_MAX_SCORE, "limit", FUZZY_SCAN_LIMIT),
                this::candidate);
    }

    public List<MenuRow> findMenus(Collection<Long> menuIds) {
        return jdbc.query(
                """
                SELECT id, base_menu_id, remain_token, category_id, source, ambiguity_flags, knowledge_revision
                FROM menus WHERE id IN (:ids)
                """,
                Map.of("ids", menuIds),
                (rs, i) -> new MenuRow(
                        rs.getLong("id"),
                        nullableLong(rs, "base_menu_id"),
                        rs.getString("remain_token"),
                        nullableLong(rs, "category_id"),
                        rs.getString("source"),
                        strings(rs, "ambiguity_flags"),
                        rs.getLong("knowledge_revision")));
    }

    public List<RecipeRow> findRecipeIngredients(Collection<Long> menuIds) {
        return jdbc.query(
                "SELECT menu_id, ingredient_id FROM recipe_ingredients WHERE menu_id IN (:ids) ORDER BY menu_id, ingredient_id",
                Map.of("ids", menuIds),
                (rs, i) -> new RecipeRow(rs.getLong("menu_id"), rs.getLong("ingredient_id")));
    }

    public List<CorpusRow> findCorpora(Collection<Long> menuIds) {
        return jdbc.query(
                "SELECT menu_id, corpus, n_total FROM menu_recipe_corpora WHERE menu_id IN (:ids) ORDER BY menu_id, corpus",
                Map.of("ids", menuIds),
                (rs, i) -> new CorpusRow(rs.getLong("menu_id"), rs.getString("corpus"), rs.getInt("n_total")));
    }

    public List<PriorRow> findPriors(Collection<Long> menuIds) {
        return jdbc.query(
                """
                SELECT menu_id, corpus, ingredient_id, k_count FROM menu_ingredient_priors
                WHERE menu_id IN (:ids) ORDER BY menu_id, corpus, ingredient_id
                """,
                Map.of("ids", menuIds),
                (rs, i) -> new PriorRow(
                        rs.getLong("menu_id"),
                        rs.getString("corpus"),
                        rs.getLong("ingredient_id"),
                        rs.getInt("k_count")));
    }

    /** 무효화되지 않은 재료 확정값. */
    public List<ConfirmationRow> findConfirmations(Collection<Long> storeMenuIds) {
        return jdbc.query(
                """
                SELECT c.id, c.store_menu_id, c.ingredient_id, i.name_ko, c.present, c.confirmed_at
                FROM ingredient_confirmations c
                JOIN ingredients i ON i.id = c.ingredient_id
                WHERE c.store_menu_id IN (:ids) AND c.invalidated_at IS NULL
                ORDER BY c.store_menu_id, c.ingredient_id
                """,
                Map.of("ids", storeMenuIds),
                (rs, i) -> new ConfirmationRow(
                        rs.getLong("id"),
                        rs.getLong("store_menu_id"),
                        rs.getLong("ingredient_id"),
                        rs.getString("name_ko"),
                        rs.getBoolean("present"),
                        rs.getTimestamp("confirmed_at").toInstant()));
    }

    /** 무효화되지 않은 애매함 플래그 확정값. */
    public List<FlagConfirmationRow> findFlagConfirmations(Collection<Long> storeMenuIds) {
        return jdbc.query(
                """
                SELECT id, store_menu_id, ambiguity_flag, asked_tags, present, confirmed_at
                FROM flag_confirmations
                WHERE store_menu_id IN (:ids) AND invalidated_at IS NULL
                ORDER BY store_menu_id, ambiguity_flag, id
                """,
                Map.of("ids", storeMenuIds),
                (rs, i) -> new FlagConfirmationRow(
                        rs.getLong("id"),
                        rs.getLong("store_menu_id"),
                        rs.getString("ambiguity_flag"),
                        strings(rs, "asked_tags"),
                        rs.getBoolean("present"),
                        rs.getTimestamp("confirmed_at").toInstant()));
    }

    public List<SourceWeightRow> findSourceWeights() {
        return jdbc.query(
                "SELECT source_type, reliability FROM source_reliability ORDER BY source_type",
                Map.of(),
                (rs, i) -> new SourceWeightRow(rs.getString("source_type"), rs.getDouble("reliability")));
    }

    private CandidateRow candidate(ResultSet rs, int rowNum) throws SQLException {
        return new CandidateRow(
                rs.getLong("id"),
                nullableLong(rs, "category_id"),
                rs.getString("name_ko"),
                rs.getDouble("score"),
                rs.getString("match_type"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static List<String> strings(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList();
    }

    public record IngredientRow(
            long id,
            String nameKo,
            String nameNormalized,
            String localizedName,
            String taxonomyCategory,
            List<String> tagCodes) {}

    public record IngredientAliasRow(long ingredientId, String aliasNormalized) {}

    public record CompositionRow(long parentId, long childId, String ambiguityFlag, List<String> tagCodes) {}

    public record CandidateRow(long menuId, Long categoryId, String nameKo, double score, String matchType) {}

    public record MenuRow(
            long id,
            Long baseMenuId,
            String remainToken,
            Long categoryId,
            String source,
            List<String> ambiguityFlags,
            long knowledgeRevision) {}

    public record RecipeRow(long menuId, long ingredientId) {}

    public record CorpusRow(long menuId, String corpus, int nTotal) {}

    public record PriorRow(long menuId, String corpus, long ingredientId, int kCount) {}

    public record ConfirmationRow(
            long id,
            long storeMenuId,
            long ingredientId,
            String ingredientName,
            boolean present,
            Instant confirmedAt) {}

    public record FlagConfirmationRow(
            long id,
            long storeMenuId,
            String ambiguityFlag,
            List<String> askedTags,
            boolean present,
            Instant confirmedAt) {}

    public record SourceWeightRow(String sourceType, double weight) {}
}
