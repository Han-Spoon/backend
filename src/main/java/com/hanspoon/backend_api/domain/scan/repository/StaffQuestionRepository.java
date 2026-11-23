package com.hanspoon.backend_api.domain.scan.repository;

import com.hanspoon.backend_api.domain.scan.dto.StaffQuestionResult;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 직원 질문 JDBC 조회·응답 저장. 질문과 메뉴는 같은 스캔·같은 가게 메뉴로 연결. */
@Repository
public class StaffQuestionRepository {

    private static final String KO = "ko";

    private static final String SELECT_QUESTIONS =
            """
            SELECT ma.id AS menu_analysis_id, q.id, q.card_id, q.ingredient_id, q.ambiguity_flag, q.asked_tags,
                   q.question_text ->> 'ko' AS text_ko, q.question_text ->> :locale AS text_locale,
                   i.name_ko AS ingredient_ko, i.name_i18n ->> :locale AS ingredient_locale,
                   q.status, q.answer, q.answered_at
            FROM staff_questions q
            JOIN menu_analyses ma
              ON ma.scan_session_id = q.scan_session_id AND ma.store_menu_id = q.store_menu_id
            LEFT JOIN ingredients i ON i.id = q.ingredient_id
            WHERE q.scan_session_id = :scanSessionId
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public StaffQuestionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 스캔의 직원 질문. 메뉴 id 와 함께, 생성 순. */
    public List<MenuStaffQuestion> findByScan(UUID scanSessionId, String locale) {
        return jdbc.query(
                SELECT_QUESTIONS + "ORDER BY q.created_at, q.card_id",
                params(scanSessionId, locale),
                (rs, rowNum) ->
                        new MenuStaffQuestion(rs.getObject("menu_analysis_id", UUID.class), toResult(rs, locale)));
    }

    /** 질문 1건 (응답 결과용). */
    public StaffQuestionResult findOne(UUID scanSessionId, UUID questionId, String locale) {
        return jdbc.queryForObject(
                SELECT_QUESTIONS + "AND q.id = :questionId",
                params(scanSessionId, locale).addValue("questionId", questionId),
                (rs, rowNum) -> toResult(rs, locale));
    }

    /** 응답 대상 질문. 질문·스캔 메뉴·스캔 행 잠금. */
    public Optional<AnswerTarget> lockForAnswer(UUID scanSessionId, UUID questionId) {
        return jdbc
                .query(
                        """
                        SELECT q.id, q.store_menu_id, q.ingredient_id IS NOT NULL AS ingredient, q.status,
                               q.resolved_confirmation_id, q.resolved_flag_confirmation_id,
                               ma.id AS menu_analysis_id, ma.ai_risk_level
                        FROM staff_questions q
                        JOIN menu_analyses ma
                          ON ma.scan_session_id = q.scan_session_id AND ma.store_menu_id = q.store_menu_id
                        JOIN scan_sessions s ON s.id = q.scan_session_id
                        WHERE q.id = :questionId AND q.scan_session_id = :scanSessionId
                        FOR UPDATE
                        """,
                        Map.of("questionId", questionId, "scanSessionId", scanSessionId),
                        (rs, rowNum) -> new AnswerTarget(
                                rs.getObject("id", UUID.class),
                                rs.getLong("store_menu_id"),
                                rs.getBoolean("ingredient"),
                                rs.getString("status"),
                                rs.getObject("resolved_confirmation_id", Long.class),
                                rs.getObject("resolved_flag_confirmation_id", Long.class),
                                rs.getObject("menu_analysis_id", UUID.class),
                                rs.getString("ai_risk_level")))
                .stream()
                .findFirst();
    }

    /** 재료 확정값 upsert. 다시 확정하면 사용 중지 해제. */
    public long upsertIngredientConfirmation(UUID questionId, boolean present) {
        return jdbc.queryForObject(
                """
                INSERT INTO ingredient_confirmations (store_id, store_menu_id, ingredient_id, present, confirmed_at)
                SELECT store_id, store_menu_id, ingredient_id, :present, now() FROM staff_questions WHERE id = :questionId
                ON CONFLICT (store_menu_id, ingredient_id) DO UPDATE
                SET present = EXCLUDED.present, confirmed_at = EXCLUDED.confirmed_at, invalidated_at = NULL,
                    updated_at = now()
                RETURNING id
                """,
                Map.of("questionId", questionId, "present", present),
                Long.class);
    }

    /** 플래그 확정값 upsert. 다시 확정하면 사용 중지 해제. */
    public long upsertFlagConfirmation(UUID questionId, boolean present) {
        return jdbc.queryForObject(
                """
                INSERT INTO flag_confirmations (store_id, store_menu_id, ambiguity_flag, asked_tags, present, confirmed_at)
                SELECT store_id, store_menu_id, ambiguity_flag, asked_tags, :present, now()
                FROM staff_questions WHERE id = :questionId
                ON CONFLICT (store_menu_id, ambiguity_flag, asked_tags) DO UPDATE
                SET present = EXCLUDED.present, confirmed_at = EXCLUDED.confirmed_at, invalidated_at = NULL,
                    updated_at = now()
                RETURNING id
                """,
                Map.of("questionId", questionId, "present", present),
                Long.class);
    }

    /** 확정값 사용 중지. 다른 트랜잭션이 더 늦게 확정했으면 그 시각. */
    public void invalidateConfirmations(Long confirmationId, Long flagConfirmationId) {
        if (confirmationId != null) {
            jdbc.update(
                    """
                    UPDATE ingredient_confirmations
                    SET invalidated_at = GREATEST(now(), confirmed_at), updated_at = now()
                    WHERE id = :id AND invalidated_at IS NULL
                    """,
                    Map.of("id", confirmationId));
        }
        if (flagConfirmationId != null) {
            jdbc.update(
                    """
                    UPDATE flag_confirmations
                    SET invalidated_at = GREATEST(now(), confirmed_at), updated_at = now()
                    WHERE id = :id AND invalidated_at IS NULL
                    """,
                    Map.of("id", flagConfirmationId));
        }
    }

    /** 답 저장. 링크는 질문 종류에 맞는 한쪽만, unknown 은 둘 다 null. */
    public void saveAnswer(UUID questionId, String answer, Long confirmationId, Long flagConfirmationId) {
        Map<String, Object> params = new HashMap<>();
        params.put("questionId", questionId);
        params.put("answer", answer);
        params.put("confirmationId", confirmationId);
        params.put("flagConfirmationId", flagConfirmationId);
        jdbc.update(
                """
                UPDATE staff_questions
                SET status = 'answered', answer = :answer, answered_at = now(),
                    resolved_confirmation_id = :confirmationId, resolved_flag_confirmation_id = :flagConfirmationId,
                    updated_at = now()
                WHERE id = :questionId
                """,
                params);
    }

    /** 같은 스캔·가게 메뉴 질문의 답과 대상 태그 (재료 = tag_codes, 플래그 = asked_tags). */
    public List<MenuAnswer> findMenuAnswers(UUID scanSessionId, long storeMenuId) {
        return jdbc.query(
                """
                SELECT q.answer, CASE WHEN q.ingredient_id IS NOT NULL THEN i.tag_codes ELSE q.asked_tags END AS tags
                FROM staff_questions q
                LEFT JOIN ingredients i ON i.id = q.ingredient_id
                WHERE q.scan_session_id = :scanSessionId AND q.store_menu_id = :storeMenuId
                """,
                Map.of("scanSessionId", scanSessionId, "storeMenuId", storeMenuId),
                (rs, rowNum) -> new MenuAnswer(rs.getString("answer"), strings(rs.getArray("tags"))));
    }

    public void updateMenu(UUID menuAnalysisId, String riskLevel, String staffAnswerStatus) {
        jdbc.update(
                """
                UPDATE menu_analyses
                SET risk_level = :riskLevel, staff_answer_status = :staffAnswerStatus, updated_at = now()
                WHERE id = :id
                """,
                Map.of("id", menuAnalysisId, "riskLevel", riskLevel, "staffAnswerStatus", staffAnswerStatus));
    }

    /** 스캔 카운터 재계산. JPA 쓰기와 겹치지 않게 lock_version 증가. */
    public RiskCounts recountScan(UUID scanSessionId) {
        return jdbc.queryForObject(
                """
                UPDATE scan_sessions s
                SET danger_count = c.danger, caution_count = c.caution, safe_count = c.safe,
                    lock_version = s.lock_version + 1, updated_at = now()
                FROM (SELECT count(*) FILTER (WHERE risk_level = 'danger') AS danger,
                             count(*) FILTER (WHERE risk_level = 'caution') AS caution,
                             count(*) FILTER (WHERE risk_level = 'safe') AS safe
                      FROM menu_analyses WHERE scan_session_id = :scanSessionId) c
                WHERE s.id = :scanSessionId
                RETURNING s.danger_count, s.caution_count, s.safe_count
                """,
                Map.of("scanSessionId", scanSessionId),
                (rs, rowNum) ->
                        new RiskCounts(rs.getInt("danger_count"), rs.getInt("caution_count"), rs.getInt("safe_count")));
    }

    private static MapSqlParameterSource params(UUID scanSessionId, String locale) {
        return new MapSqlParameterSource()
                .addValue("scanSessionId", scanSessionId)
                .addValue("locale", locale == null ? KO : locale);
    }

    private static StaffQuestionResult toResult(ResultSet rs, String locale) throws SQLException {
        boolean ingredient = rs.getObject("ingredient_id") != null;
        OffsetDateTime answeredAt = rs.getObject("answered_at", OffsetDateTime.class);
        return new StaffQuestionResult(
                rs.getObject("id", UUID.class),
                rs.getString("card_id"),
                ingredient ? "ingredient" : "flag",
                ingredient ? localized(rs.getString("ingredient_ko"), rs.getString("ingredient_locale"), locale) : null,
                rs.getString("ambiguity_flag"),
                strings(rs.getArray("asked_tags")),
                localized(rs.getString("text_ko"), rs.getString("text_locale"), locale),
                rs.getString("status"),
                rs.getString("answer"),
                answeredAt == null ? null : answeredAt.toInstant());
    }

    private static Map<String, String> localized(String ko, String translated, String locale) {
        Map<String, String> text = new LinkedHashMap<>();
        text.put(KO, ko);
        if (locale != null && !KO.equals(locale) && translated != null) {
            text.put(locale, translated);
        }
        return text;
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? null : Arrays.asList((String[]) array.getArray());
    }

    /** 질문 + 연결된 스캔 메뉴 id. */
    public record MenuStaffQuestion(UUID menuAnalysisId, StaffQuestionResult question) {}

    /** 잠근 응답 대상. 기존 링크는 unknown 으로 고칠 때 사용 중지 대상. */
    public record AnswerTarget(
            UUID questionId,
            long storeMenuId,
            boolean ingredient,
            String status,
            Long confirmationId,
            Long flagConfirmationId,
            UUID menuAnalysisId,
            String aiRiskLevel) {}

    /** 메뉴 질문의 답(미답 null)과 대상 태그. */
    public record MenuAnswer(String answer, List<String> tags) {}

    public record RiskCounts(int dangerCount, int cautionCount, int safeCount) {}
}
