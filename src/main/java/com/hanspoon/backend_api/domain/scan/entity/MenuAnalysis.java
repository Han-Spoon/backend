package com.hanspoon.backend_api.domain.scan.entity;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.ai.dto.result.FinalMessage;
import com.hanspoon.backend_api.domain.ai.dto.result.OwnerCard;
import com.hanspoon.backend_api.domain.ai.dto.result.OwnerQuestion;
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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 메뉴 분석 결과. scan_sessions 와 N:1 (FK ON DELETE CASCADE).
 * OCR(menu_name/price/...)과 ai_result FinalOutput(risk_level/hit_tags/message/owner_card)을 index 순서로 머지해 저장한다.
 * message·owner_card 는 레거시·Judge 두 형식이 섞여 원본 JSON 문자열로 보관.
 *
 * <p>Prepare/Judge 경로는 Prepare 저장 때 source_index·가게 메뉴 연결로 생성, 판정은 Judge 저장 때 기록.
 */
@Entity
@Table(name = "menu_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuAnalysis extends BaseEntity {

    private static final String EMPTY_ARRAY = "[]";
    private static final JsonMapper LEGACY_JSON = JsonMapper.shared();

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

    @Column(name = "menu_name_ko", length = 255)
    private String menuNameKo;

    @Column(name = "menu_name_en", length = 255)
    private String menuNameEn;

    @Column(name = "description_ko", columnDefinition = "text")
    private String descriptionKo;

    @Column(name = "description_en", columnDefinition = "text")
    private String descriptionEn;

    @Column(name = "price_text", length = 100)
    private String priceText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "price_options", columnDefinition = "jsonb", nullable = false)
    private String priceOptions = EMPTY_ARRAY;

    @Column(name = "is_spicy")
    private Boolean isSpicy;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hit_tags", columnDefinition = "jsonb")
    private List<String> hitTags;

    /** 레거시 {ko,en,ar} 또는 Judge DecisionMessage 원본 JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "message", columnDefinition = "jsonb")
    private String message;

    /** 레거시 단일 카드 또는 Judge owner_cards 배열 원본 JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "owner_card", columnDefinition = "jsonb")
    private String ownerCard;

    // Judge 저장(JDBC) 전용 컬럼. 엔티티에서는 읽기만.
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

    private MenuAnalysis(
            UUID scanSessionId,
            Integer displayOrder,
            String menuNameKo,
            String menuNameEn,
            String descriptionKo,
            String descriptionEn,
            String priceText,
            Boolean isSpicy,
            String imageUrl,
            RiskLevel riskLevel,
            List<String> hitTags,
            FinalMessage message,
            OwnerCard ownerCard) {
        this.id = UUID.randomUUID();
        this.scanSessionId = scanSessionId;
        this.displayOrder = displayOrder;
        this.menuNameKo = menuNameKo;
        this.menuNameEn = menuNameEn;
        this.descriptionKo = descriptionKo;
        this.descriptionEn = descriptionEn;
        this.priceText = priceText;
        this.isSpicy = isSpicy;
        this.imageUrl = imageUrl;
        this.riskLevel = riskLevel;
        this.hitTags = hitTags;
        this.message = legacyJson(message);
        this.ownerCard = legacyJson(ownerCard);
    }

    public static MenuAnalysis create(
            UUID scanSessionId,
            Integer displayOrder,
            String menuNameKo,
            String menuNameEn,
            String descriptionKo,
            String descriptionEn,
            String priceText,
            Boolean isSpicy,
            String imageUrl,
            RiskLevel riskLevel,
            List<String> hitTags,
            FinalMessage message,
            OwnerCard ownerCard) {
        return new MenuAnalysis(
                scanSessionId,
                displayOrder,
                menuNameKo,
                menuNameEn,
                descriptionKo,
                descriptionEn,
                priceText,
                isSpicy,
                imageUrl,
                riskLevel,
                hitTags,
                message,
                ownerCard);
    }

    /** Prepare 저장용. 판정 컬럼은 비워 둠. isSpicy 는 감지 시 true, 아니면 null. */
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
        MenuAnalysis analysis = new MenuAnalysis(
                scanSessionId,
                null,
                displayName,
                null,
                description,
                null,
                priceText,
                isSpicy,
                null,
                null,
                null,
                null,
                null);
        analysis.sourceIndex = sourceIndex;
        analysis.priceOptions = priceOptions != null ? priceOptions : EMPTY_ARRAY;
        analysis.storeId = storeId;
        analysis.storeMenuId = storeMenuId;
        return analysis;
    }

    /** 기존 Hibernate(Jackson 2) 저장 형식과 같은 camelCase 키. */
    private static String legacyJson(FinalMessage message) {
        if (message == null) {
            return null;
        }
        return LEGACY_JSON
                .createObjectNode()
                .put("ko", message.ko())
                .put("en", message.en())
                .put("ar", message.ar())
                .toString();
    }

    private static String legacyJson(OwnerCard card) {
        if (card == null) {
            return null;
        }
        ObjectNode node =
                LEGACY_JSON.createObjectNode().put("menuName", card.menuName()).put("flag", card.flag());
        OwnerQuestion question = card.question();
        if (question == null) {
            node.putNull("question");
        } else {
            node.putObject("question")
                    .put("ko", question.ko())
                    .put("en", question.en())
                    .put("ar", question.ar());
        }
        return node.toString();
    }
}
