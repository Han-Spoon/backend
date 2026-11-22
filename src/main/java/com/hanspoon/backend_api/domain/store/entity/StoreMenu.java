package com.hanspoon.backend_api.domain.store.entity;

import com.hanspoon.backend_api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가게 메뉴(store_menus). stores 와 N:1, 표준 메뉴(menus) 와 N:1.
 *
 * <p>행 생성·갱신은 {@code StoreMenuRepository#upsertSeen} 전담.
 */
@Entity
@Table(name = "store_menus")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreMenu extends BaseEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "name_normalized", length = 255, nullable = false)
    private String nameNormalized;

    @Column(name = "display_name", length = 255, nullable = false)
    private String displayName;

    @Column(name = "menu_id")
    private Long menuId;

    @Column(name = "mapping_source", length = 20, nullable = false)
    private String mappingSource;
}
