package com.hanspoon.backend_api.domain.scan.entity;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.ai.dto.result.FinalMessage;
import com.hanspoon.backend_api.domain.ai.dto.result.OwnerCard;
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
 * OCR(menu_name/price/...)과 ai_result FinalOutput(risk_level/hit_tags/message/owner_card)을 index 순서로 머지해 저장한다.
 *
 * <p>Prepare/Judge 경로는 Prepare 저장 때 source_index·가게 메뉴 연결로 생성, 판정은 Judge 저장 때 기록.
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

    // ai_result(최종) 표시용
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "message", columnDefinition = "jsonb")
    private FinalMessage message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "owner_card", columnDefinition = "jsonb")
    private OwnerCard ownerCard;

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
        this.message = message;
        this.ownerCard = ownerCard;
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
}
