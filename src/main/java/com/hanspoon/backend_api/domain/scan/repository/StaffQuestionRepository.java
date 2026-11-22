package com.hanspoon.backend_api.domain.scan.repository;

import com.hanspoon.backend_api.domain.scan.dto.StaffQuestionResult;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 직원 질문 JDBC 조회. 질문과 메뉴는 같은 스캔·같은 가게 메뉴로 연결. */
@Repository
public class StaffQuestionRepository {

    private static final String KO = "ko";

    private final NamedParameterJdbcTemplate jdbc;

    public StaffQuestionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 스캔의 직원 질문. 메뉴 id 와 함께, 생성 순. */
    public List<MenuStaffQuestion> findByScan(UUID scanSessionId, String locale) {
        return jdbc.query(
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
                ORDER BY q.created_at, q.card_id
                """,
                new MapSqlParameterSource()
                        .addValue("scanSessionId", scanSessionId)
                        .addValue("locale", locale == null ? KO : locale),
                (rs, rowNum) ->
                        new MenuStaffQuestion(rs.getObject("menu_analysis_id", UUID.class), toResult(rs, locale)));
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
}
