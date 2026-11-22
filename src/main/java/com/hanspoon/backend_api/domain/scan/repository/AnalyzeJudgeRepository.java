package com.hanspoon.backend_api.domain.scan.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Judge 저장용 JDBC 쓰기. V14 전까지 menu_analyses 레거시 컬럼(message·owner_card)에 새 형식 JSON 저장.
 */
@Repository
public class AnalyzeJudgeRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyzeJudgeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 첫 판정이라 risk_level·ai_risk_level 동일. JSON 인자는 문자열. */
    public void updateMenuJudgment(MenuJudgment judgment) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", judgment.menuAnalysisId());
        params.put("riskLevel", judgment.riskLevel());
        params.put("displayOrder", judgment.displayOrder());
        params.put("normalizationStatus", judgment.normalizationStatus());
        params.put("status", judgment.status());
        params.put("evidenceBasis", judgment.evidenceBasis());
        params.put("informationStatus", judgment.informationStatus());
        params.put("riskIngredients", judgment.riskIngredients());
        params.put("matchedTags", judgment.matchedTags());
        params.put("message", judgment.message());
        params.put("staffCards", judgment.staffCards());
        params.put("menuId", judgment.menuId());
        params.put("menuMatchType", judgment.menuMatchType());
        params.put("staffAnswerStatus", judgment.staffAnswerStatus());
        jdbc.update(
                """
                UPDATE menu_analyses
                SET risk_level = :riskLevel,
                    ai_risk_level = :riskLevel,
                    display_order = :displayOrder,
                    normalization_status = :normalizationStatus,
                    status = :status,
                    evidence_basis = :evidenceBasis,
                    information_status = :informationStatus,
                    risk_ingredients = CAST(:riskIngredients AS jsonb),
                    hit_tags = CAST(:matchedTags AS jsonb),
                    message = CAST(:message AS jsonb),
                    owner_card = CAST(:staffCards AS jsonb),
                    menu_id = :menuId,
                    menu_match_type = :menuMatchType,
                    staff_answer_status = :staffAnswerStatus,
                    updated_at = now()
                WHERE id = :id
                """,
                params);
    }

    /** 자동 매핑(none·fuzzy)만 교체. 고정 매핑(name_match·admin)과 다르면 충돌 횟수 증가. */
    public void updateStoreMenuMapping(long storeMenuId, long menuId) {
        jdbc.update(
                """
                UPDATE store_menus
                SET mapping_conflict_count = mapping_conflict_count
                        + CASE WHEN mapping_source IN ('name_match', 'admin') AND menu_id <> :menuId THEN 1 ELSE 0 END,
                    menu_id = CASE WHEN mapping_source IN ('none', 'fuzzy') THEN :menuId ELSE menu_id END,
                    mapping_source = CASE WHEN mapping_source IN ('none', 'fuzzy') THEN 'fuzzy' ELSE mapping_source END,
                    updated_at = now()
                WHERE id = :storeMenuId
                """,
                Map.of("storeMenuId", storeMenuId, "menuId", menuId));
    }

    /** 수신함 적재. 이미 받은 명령이면 false. */
    public boolean insertCommand(
            String action,
            String idempotencyKey,
            Long storeId,
            UUID scanSessionId,
            String requestHash,
            String payload) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("key", idempotencyKey);
        params.put("storeId", storeId);
        params.put("scanSessionId", scanSessionId);
        params.put("hash", requestHash);
        params.put("payload", payload);
        return jdbc.update(
                        """
                        INSERT INTO ai_command_inbox (action, idempotency_key, store_id, scan_session_id, request_hash, payload)
                        VALUES (:action, :key, :storeId, :scanSessionId, :hash, CAST(:payload AS jsonb))
                        ON CONFLICT (action, idempotency_key) DO NOTHING
                        """,
                        params)
                == 1;
    }

    public void markCommandApplied(String action, String idempotencyKey, String result) {
        jdbc.update(
                """
                UPDATE ai_command_inbox
                SET status = 'applied', attempt_count = attempt_count + 1, result = CAST(:result AS jsonb),
                    updated_at = now()
                WHERE action = :action AND idempotency_key = :key
                """,
                Map.of("action", action, "key", idempotencyKey, "result", result));
    }

    /** 같은 스캔·가게 메뉴·대상 질문이 있으면 false. */
    public boolean insertStaffQuestion(StaffQuestionRow row) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", row.id());
        params.put("storeId", row.storeId());
        params.put("scanSessionId", row.scanSessionId());
        params.put("storeMenuId", row.storeMenuId());
        params.put("menuId", row.menuId());
        params.put("ingredientId", row.ingredientId());
        params.put("ambiguityFlag", row.ambiguityFlag());
        params.put("askedTags", row.askedTags() == null ? null : String.join(",", row.askedTags()));
        params.put("question", row.questionText());
        params.put("cardId", row.cardId());
        return jdbc.update(
                        """
                        INSERT INTO staff_questions (id, store_id, scan_session_id, store_menu_id, menu_id, ingredient_id,
                                                     ambiguity_flag, asked_tags, question_text, card_id)
                        VALUES (:id, :storeId, :scanSessionId, :storeMenuId, :menuId, :ingredientId, :ambiguityFlag,
                                string_to_array(:askedTags, ',')::varchar[], CAST(:question AS jsonb), :cardId)
                        ON CONFLICT DO NOTHING
                        """,
                        params)
                == 1;
    }

    public record MenuJudgment(
            UUID menuAnalysisId,
            String riskLevel,
            int displayOrder,
            String normalizationStatus,
            String status,
            String evidenceBasis,
            String informationStatus,
            String riskIngredients,
            String matchedTags,
            String message,
            String staffCards,
            Long menuId,
            String menuMatchType,
            String staffAnswerStatus) {}

    public record StaffQuestionRow(
            UUID id,
            long storeId,
            UUID scanSessionId,
            long storeMenuId,
            Long menuId,
            Long ingredientId,
            String ambiguityFlag,
            List<String> askedTags,
            String questionText,
            String cardId) {}
}
