package com.hanspoon.backend_api.domain.scan.entity;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "saved_menus")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedMenu extends BaseEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(name = "scan_session_id", columnDefinition = "uuid", nullable = false)
    private UUID scanSessionId;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "menu_name", nullable = false, length = 255)
    private String menuName;

    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "store_name", length = 200)
    private String storeName;

    private SavedMenu(
            UUID userId,
            UUID scanSessionId,
            Integer displayOrder,
            String menuName,
            RiskLevel riskLevel,
            Long storeId,
            String storeName) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.scanSessionId = scanSessionId;
        this.displayOrder = displayOrder;
        this.menuName = menuName;
        this.riskLevel = riskLevel;
        this.storeId = storeId;
        this.storeName = storeName;
    }

    public static SavedMenu create(
            UUID userId,
            UUID scanSessionId,
            Integer displayOrder,
            String menuName,
            RiskLevel riskLevel,
            Long storeId,
            String storeName) {
        return new SavedMenu(userId, scanSessionId, displayOrder, menuName, riskLevel, storeId, storeName);
    }
}
