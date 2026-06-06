package com.hanspoon.backend_api.domain.scan.entity;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.GptContext;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RiskReason;
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
 * OCR(menu_name/price/...)과 RuleEngine(risk_level/hit_tags/...) 결과를 index 순서로 머지해 저장한다.
 */
@Entity
@Table(name = "menu_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuAnalysis extends BaseEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "scan_session_id", columnDefinition = "uuid", nullable = false)
    private UUID scanSessionId;

    @Column(name = "display_order")
    private Integer displayOrder;

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

    @Column(name = "is_spicy")
    private Boolean isSpicy;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    @Column(name = "need_gpt")
    private Boolean needGpt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hit_tags", columnDefinition = "jsonb")
    private List<String> hitTags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "triggered_flags", columnDefinition = "jsonb")
    private List<String> triggeredFlags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "forbidden_tags", columnDefinition = "jsonb")
    private List<String> forbiddenTags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "escalation_case", columnDefinition = "jsonb")
    private List<String> escalationCase;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gpt_context", columnDefinition = "jsonb")
    private GptContext gptContext;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_reasons", columnDefinition = "jsonb")
    private List<RiskReason> riskReasons;

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
            Boolean needGpt,
            List<String> hitTags,
            List<String> triggeredFlags,
            List<String> forbiddenTags,
            List<String> escalationCase,
            GptContext gptContext,
            List<RiskReason> riskReasons) {
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
        this.needGpt = needGpt;
        this.hitTags = hitTags;
        this.triggeredFlags = triggeredFlags;
        this.forbiddenTags = forbiddenTags;
        this.escalationCase = escalationCase;
        this.gptContext = gptContext;
        this.riskReasons = riskReasons;
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
            Boolean needGpt,
            List<String> hitTags,
            List<String> triggeredFlags,
            List<String> forbiddenTags,
            List<String> escalationCase,
            GptContext gptContext,
            List<RiskReason> riskReasons) {
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
                needGpt,
                hitTags,
                triggeredFlags,
                forbiddenTags,
                escalationCase,
                gptContext,
                riskReasons);
    }
}
