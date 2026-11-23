package com.hanspoon.backend_api.domain.review.repository;

import com.hanspoon.backend_api.domain.review.dto.ReviewResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 식사 후기(meal_reviews·items·answers) JDBC. */
@Repository
public class ReviewRepository {

    private static final String SELECT_REVIEWS =
            """
            SELECT r.id, r.user_id, r.store_id, st.name AS store_name, r.scan_session_id,
                   r.hidden_at IS NOT NULL AS hidden, r.created_at, r.updated_at
            FROM meal_reviews r
            JOIN stores st ON st.id = r.store_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ReviewRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** normalize_food_name 결과, 입력 순. */
    public List<String> normalizeNames(List<String> names) {
        return jdbc.queryForList(
                """
                SELECT normalize_food_name(t.name)
                FROM unnest(CAST(:names AS text[])) WITH ORDINALITY AS t(name, ord)
                ORDER BY t.ord
                """,
                Map.of("names", names.toArray(String[]::new)),
                String.class);
    }

    /** 주어진 id 중 그 스캔의 메뉴. */
    public Set<UUID> findScanMenuIds(UUID scanSessionId, Collection<UUID> menuIds) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT id FROM menu_analyses WHERE scan_session_id = :scanSessionId AND id IN (:ids)",
                Map.of("scanSessionId", scanSessionId, "ids", menuIds),
                UUID.class));
    }

    public boolean existsByScan(UUID scanSessionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM meal_reviews WHERE scan_session_id = :scanSessionId)",
                Map.of("scanSessionId", scanSessionId),
                Boolean.class));
    }

    public void insertReview(UUID reviewId, UUID userId, long storeId, UUID scanSessionId) {
        jdbc.update(
                """
                INSERT INTO meal_reviews (id, user_id, store_id, scan_session_id)
                VALUES (:id, :userId, :storeId, :scanSessionId)
                """,
                new MapSqlParameterSource()
                        .addValue("id", reviewId)
                        .addValue("userId", userId)
                        .addValue("storeId", storeId)
                        .addValue("scanSessionId", scanSessionId));
    }

    /** 메뉴 줄과 응답. created_at 은 clock_timestamp 로 작성 순 보존. */
    public void insertItems(UUID reviewId, List<ItemRow> items) {
        for (ItemRow item : items) {
            jdbc.update(
                    """
                    INSERT INTO meal_review_items (id, review_id, menu_analysis_id, menu_name, comment, comment_locale,
                                                   created_at, updated_at)
                    VALUES (:id, :reviewId, :menuAnalysisId, :name, :comment, :commentLocale,
                            clock_timestamp(), clock_timestamp())
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", item.id())
                            .addValue("reviewId", reviewId)
                            .addValue("menuAnalysisId", item.menuAnalysisId())
                            .addValue("name", item.name())
                            .addValue("comment", item.comment())
                            .addValue("commentLocale", item.commentLocale()));
            insertAnswers(item.id(), item.answers());
        }
    }

    /** viewer 기준 mine, 남의 후기는 scanId·scanMenuId 가림. */
    public Optional<ReviewResponse> findOne(UUID reviewId, UUID viewerId) {
        return find("WHERE r.id = :reviewId", Map.of("reviewId", reviewId), viewerId).stream()
                .findFirst();
    }

    private void insertAnswers(UUID itemId, Map<String, String> answers) {
        for (Map.Entry<String, String> answer : answers.entrySet()) {
            jdbc.update(
                    """
                    INSERT INTO meal_review_answers (review_item_id, diet_code, answer)
                    VALUES (:itemId, :dietCode, :answer)
                    """,
                    Map.of("itemId", itemId, "dietCode", answer.getKey(), "answer", answer.getValue()));
        }
    }

    private List<ReviewResponse> find(String where, Map<String, ?> params, UUID viewerId) {
        List<ReviewRow> reviews = jdbc.query(
                SELECT_REVIEWS + where + " ORDER BY r.created_at DESC, r.id DESC",
                params,
                (rs, rowNum) -> new ReviewRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getLong("store_id"),
                        rs.getString("store_name"),
                        rs.getObject("scan_session_id", UUID.class),
                        rs.getBoolean("hidden"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)));
        if (reviews.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ItemView>> items =
                findItems(reviews.stream().map(ReviewRow::id).toList());
        return reviews.stream()
                .map(review -> {
                    boolean mine = review.userId().equals(viewerId);
                    List<ReviewResponse.Menu> menus = items.getOrDefault(review.id(), List.of()).stream()
                            .map(item -> new ReviewResponse.Menu(
                                    item.name(),
                                    mine ? item.menuAnalysisId() : null,
                                    item.answers(),
                                    item.comment(),
                                    item.commentLocale()))
                            .toList();
                    return new ReviewResponse(
                            review.id(),
                            review.storeId(),
                            review.storeName(),
                            mine ? review.scanSessionId() : null,
                            menus,
                            mine,
                            review.hidden(),
                            review.createdAt().toInstant(),
                            review.updatedAt().toInstant());
                })
                .toList();
    }

    /** 후기별 메뉴 줄(작성 순)과 응답(식단 코드 순). */
    private Map<UUID, List<ItemView>> findItems(List<UUID> reviewIds) {
        Map<UUID, ItemView> byId = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT i.review_id, i.id, i.menu_analysis_id, i.menu_name, i.comment, i.comment_locale,
                       a.diet_code, a.answer
                FROM meal_review_items i
                LEFT JOIN meal_review_answers a ON a.review_item_id = i.id
                WHERE i.review_id IN (:reviewIds)
                ORDER BY i.created_at, i.id, a.diet_code
                """,
                Map.of("reviewIds", reviewIds),
                rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    ItemView item = byId.get(id);
                    if (item == null) {
                        item = new ItemView(
                                rs.getObject("review_id", UUID.class),
                                rs.getString("menu_name"),
                                rs.getObject("menu_analysis_id", UUID.class),
                                new LinkedHashMap<>(),
                                rs.getString("comment"),
                                rs.getString("comment_locale"));
                        byId.put(id, item);
                    }
                    String dietCode = rs.getString("diet_code");
                    if (dietCode != null) {
                        item.answers().put(dietCode, rs.getString("answer"));
                    }
                });
        Map<UUID, List<ItemView>> byReview = new HashMap<>();
        byId.values().forEach(item -> byReview.computeIfAbsent(item.reviewId(), id -> new ArrayList<>())
                .add(item));
        return byReview;
    }

    /** 저장할 메뉴 줄. */
    public record ItemRow(
            UUID id,
            String name,
            UUID menuAnalysisId,
            String comment,
            String commentLocale,
            Map<String, String> answers) {}

    private record ReviewRow(
            UUID id,
            UUID userId,
            long storeId,
            String storeName,
            UUID scanSessionId,
            boolean hidden,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    private record ItemView(
            UUID reviewId,
            String name,
            UUID menuAnalysisId,
            Map<String, String> answers,
            String comment,
            String commentLocale) {}
}
