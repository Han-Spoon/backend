package com.hanspoon.backend_api.domain.favorite.repository;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.favorite.dto.SavedMenuResponse;
import com.hanspoon.backend_api.domain.scan.dto.ScanStoreSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 메뉴 찜(favorite_menus) JDBC. */
@Repository
public class SavedMenuRepository {

    private static final String SELECT_SAVED =
            """
            SELECT fm.menu_analysis_id, fm.scan_session_id, ma.display_name,
                   CASE WHEN ss.locale IS NULL OR ss.locale = 'ko' THEN NULL
                        ELSE m.name_i18n ->> ss.locale END AS translated_name,
                   ma.risk_level,
                   COALESCE(ss.store_id, sr.attached_store_id) AS store_id,
                   CASE WHEN ss.store_id IS NOT NULL THEN ss.store_name_snapshot
                        ELSE sr.attached_store_name_snapshot END AS store_name,
                   fm.created_at
            FROM favorite_menus fm
            JOIN menu_analyses ma ON ma.id = fm.menu_analysis_id
            JOIN scan_sessions ss ON ss.id = fm.scan_session_id
            LEFT JOIN menus m ON m.id = ma.menu_id
            LEFT JOIN scan_records sr ON sr.scan_session_id = fm.scan_session_id
            WHERE fm.user_id = :userId
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SavedMenuRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 내 스캔의 메뉴일 때만 추가. 이미 찜이면 그대로. */
    public void insertIfOwned(UUID userId, UUID scanMenuId) {
        jdbc.update(
                """
                INSERT INTO favorite_menus (user_id, menu_analysis_id, scan_session_id)
                SELECT ss.user_id, ma.id, ma.scan_session_id
                FROM menu_analyses ma
                JOIN scan_sessions ss ON ss.id = ma.scan_session_id
                WHERE ma.id = :scanMenuId AND ss.user_id = :userId
                ON CONFLICT (user_id, menu_analysis_id) DO NOTHING
                """,
                Map.of("userId", userId, "scanMenuId", scanMenuId));
    }

    public Optional<SavedMenuResponse> findOne(UUID userId, UUID scanMenuId) {
        return jdbc
                .query(
                        SELECT_SAVED + "AND fm.menu_analysis_id = :scanMenuId",
                        Map.of("userId", userId, "scanMenuId", scanMenuId),
                        (rs, rowNum) -> toResponse(rs))
                .stream()
                .findFirst();
    }

    /** 찜한 최신순 전체. */
    public List<SavedMenuResponse> findAll(UUID userId) {
        return jdbc.query(
                SELECT_SAVED + "ORDER BY fm.created_at DESC, fm.menu_analysis_id DESC",
                Map.of("userId", userId),
                (rs, rowNum) -> toResponse(rs));
    }

    /** 삭제 행 수. */
    public int delete(UUID userId, UUID scanMenuId) {
        return jdbc.update(
                "DELETE FROM favorite_menus WHERE user_id = :userId AND menu_analysis_id = :scanMenuId",
                Map.of("userId", userId, "scanMenuId", scanMenuId));
    }

    private static SavedMenuResponse toResponse(ResultSet rs) throws SQLException {
        String riskLevel = rs.getString("risk_level");
        Long storeId = rs.getObject("store_id", Long.class);
        return new SavedMenuResponse(
                rs.getObject("menu_analysis_id", UUID.class),
                rs.getObject("scan_session_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("translated_name"),
                riskLevel == null ? null : RiskLevel.fromCode(riskLevel),
                storeId == null ? null : new ScanStoreSummary(storeId, rs.getString("store_name")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
