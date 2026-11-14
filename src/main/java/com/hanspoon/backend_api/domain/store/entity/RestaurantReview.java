package com.hanspoon.backend_api.domain.store.entity;

import com.hanspoon.backend_api.domain.store.dto.ReviewMenu;
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

@Entity
@Table(name = "restaurant_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantReview extends BaseEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "scan_session_id")
    private UUID scanSessionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "menus", columnDefinition = "jsonb", nullable = false)
    private List<ReviewMenu> menus;

    private RestaurantReview(UUID userId, Long storeId, UUID scanSessionId, List<ReviewMenu> menus) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.storeId = storeId;
        this.scanSessionId = scanSessionId;
        this.menus = List.copyOf(menus);
    }

    public static RestaurantReview create(UUID userId, Long storeId, UUID scanSessionId, List<ReviewMenu> menus) {
        return new RestaurantReview(userId, storeId, scanSessionId, menus);
    }

    public void updateMenus(List<ReviewMenu> menus) {
        this.menus = List.copyOf(menus);
    }
}
