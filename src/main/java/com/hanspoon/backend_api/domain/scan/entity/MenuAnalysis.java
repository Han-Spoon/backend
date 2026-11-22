package com.hanspoon.backend_api.domain.scan.entity;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 메뉴 분석 결과. scan_sessions 와 N:1 (FK ON DELETE CASCADE).
 * Prepare 저장 때 메뉴판 항목으로 생성, 판정 컬럼은 Judge 저장(JDBC)이 기록.
 *
 * <p>판정 컬럼은 엔티티에서 읽기만. 오래된 엔티티 저장이 Judge 결과를 덮어쓰지 않게 하기 위함.
 */
@Entity
@Table(name = "menu_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuAnalysis extends BaseEntity {

    private static final String EMPTY_ARRAY = "[]";

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "scan_session_id", columnDefinition = "uuid", nullable = false)
    private UUID scanSessionId;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "source_index")
    private Integer sourceIndex;

    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "store_menu_id")
    private Long storeMenuId;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "price_text", length = 100)
    private String priceText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "price_options", columnDefinition = "jsonb", nullable = false)
    private String priceOptions = EMPTY_ARRAY;

    @Column(name = "is_spicy")
    private Boolean isSpicy;

    // Judge 저장(JDBC) 전용 컬럼
    @Column(name = "risk_level", length = 20, insertable = false, updatable = false)
    private RiskLevel riskLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_tags", columnDefinition = "jsonb", insertable = false, updatable = false)
    private List<String> matchedTags;

    /** Judge {verdict,reason,notes} 또는 레거시 {ko,en,ar} 원본 JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "message", columnDefinition = "jsonb", insertable = false, updatable = false)
    private String message;

    /** Judge owner_cards 배열 원본 JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "staff_cards", columnDefinition = "jsonb", insertable = false, updatable = false)
    private String staffCards;

    @Column(name = "menu_id", insertable = false, updatable = false)
    private Long menuId;

    @Column(name = "status", length = 20, insertable = false, updatable = false)
    private String status;

    @Column(name = "evidence_basis", length = 20, insertable = false, updatable = false)
    private String evidenceBasis;

    @Column(name = "information_status", length = 20, insertable = false, updatable = false)
    private String informationStatus;

    @Column(name = "staff_answer_status", length = 10, insertable = false, updatable = false)
    private String staffAnswerStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_ingredients", columnDefinition = "jsonb", insertable = false, updatable = false)
    private String riskIngredients;

    /** Prepare 저장용. isSpicy 는 감지 시 true, 아니면 null. */
    public static MenuAnalysis prepared(
            UUID scanSessionId,
            int sourceIndex,
            String displayName,
            String description,
            String priceText,
            String priceOptions,
            Boolean isSpicy,
            Long storeId,
            Long storeMenuId) {
        MenuAnalysis analysis = new MenuAnalysis();
        analysis.id = UUID.randomUUID();
        analysis.scanSessionId = scanSessionId;
        analysis.sourceIndex = sourceIndex;
        analysis.displayName = displayName;
        analysis.description = description;
        analysis.priceText = priceText;
        analysis.priceOptions = priceOptions != null ? priceOptions : EMPTY_ARRAY;
        analysis.isSpicy = isSpicy;
        analysis.storeId = storeId;
        analysis.storeMenuId = storeMenuId;
        return analysis;
    }
}
