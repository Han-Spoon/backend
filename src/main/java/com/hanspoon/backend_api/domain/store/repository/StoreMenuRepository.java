package com.hanspoon.backend_api.domain.store.repository;

import com.hanspoon.backend_api.domain.store.entity.StoreMenu;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StoreMenuRepository extends JpaRepository<StoreMenu, Long> {

    /**
     * 가게 상세 메뉴. 숨김 제외, id 순.
     * 이미지: 가게 메뉴 사진, 없으면 연결 표준 메뉴 이미지. 번역 이름은 locale 이 null 이면 null.
     */
    @Query(
            value =
                    """
                    SELECT sm.id AS storeMenuId,
                           sm.display_name AS name,
                           m.name_i18n ->> CAST(:locale AS text) AS translatedName,
                           sm.description AS description,
                           sm.price_text AS priceText,
                           CAST(sm.price_options AS text) AS priceOptions,
                           sm.menu_id AS menuId,
                           CASE WHEN sia.id IS NOT NULL THEN sia.storage_key ELSE mia.storage_key END
                               AS imageStorageKey,
                           CASE WHEN sia.id IS NOT NULL THEN sia.attribution_text ELSE mia.attribution_text END
                               AS imageAttribution,
                           CASE WHEN sia.id IS NOT NULL THEN 'store'
                                WHEN mia.id IS NOT NULL THEN 'standard'
                           END AS imageSource
                    FROM store_menus sm
                    LEFT JOIN image_assets sia ON sia.id = sm.image_asset_id AND sia.status = 'active'
                    LEFT JOIN menus m ON m.id = sm.menu_id
                    LEFT JOIN image_assets mia ON mia.id = m.image_asset_id AND mia.status = 'active'
                    WHERE sm.store_id = :storeId
                      AND sm.status = 'active'
                    ORDER BY sm.id
                    """,
            nativeQuery = true)
    List<StoreMenuDetailProjection> findActiveMenusWithImages(
            @Param("storeId") long storeId, @Param("locale") String locale);

    /**
     * 스캔에서 본 가게 메뉴 upsert. 기존 행은 표시값 유지, 마지막 확인 정보만 갱신.
     * 같은 스캔의 중복 이름은 seen_count 1회만 증가. 정규화 이름이 비면 null.
     */
    @Transactional
    @Query(
            value =
                    """
                    INSERT INTO store_menus (store_id, name_normalized, display_name, description, price_text,
                                             price_options, last_scan_session_id)
                    SELECT :storeId, n.name, :displayName, :description, :priceText,
                           CAST(:priceOptions AS jsonb), :scanSessionId
                    FROM (SELECT normalize_food_name(:menuName) AS name) n
                    WHERE n.name <> ''
                    ON CONFLICT (store_id, name_normalized) DO UPDATE
                    SET last_seen_at = now(),
                        seen_count = store_menus.seen_count
                            + CASE WHEN store_menus.last_scan_session_id IS DISTINCT FROM EXCLUDED.last_scan_session_id
                                   THEN 1 ELSE 0 END,
                        last_scan_session_id = EXCLUDED.last_scan_session_id,
                        updated_at = now()
                    RETURNING id
                    """,
            nativeQuery = true)
    Long upsertSeen(
            @Param("storeId") Long storeId,
            @Param("menuName") String menuName,
            @Param("displayName") String displayName,
            @Param("description") String description,
            @Param("priceText") String priceText,
            @Param("priceOptions") String priceOptions,
            @Param("scanSessionId") UUID scanSessionId);
}
