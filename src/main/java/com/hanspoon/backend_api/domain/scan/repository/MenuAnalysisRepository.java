package com.hanspoon.backend_api.domain.scan.repository;

import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuAnalysisRepository extends JpaRepository<MenuAnalysis, UUID> {

    List<MenuAnalysis> findByScanSessionIdOrderByDisplayOrder(UUID scanSessionId);

    /** 스캔 가게 + 정규화 메뉴명 일치 가게 메뉴의 공개 사진. V14 후 store_menu_id 조인으로 전환. */
    @Query(
            value =
                    """
                    SELECT ma.id AS menuAnalysisId, ia.storage_key AS storageKey,
                           ia.attribution_text AS attributionText
                    FROM menu_analyses ma
                    JOIN scan_sessions ss ON ss.id = ma.scan_session_id
                    JOIN store_menus sm ON sm.store_id = ss.store_id
                        AND sm.name_normalized = normalize_food_name(ma.menu_name_ko)
                    JOIN image_assets ia ON ia.id = sm.image_asset_id AND ia.status = 'active'
                    WHERE ma.scan_session_id = :scanSessionId
                    """,
            nativeQuery = true)
    List<MenuPhotoProjection> findPhotosByScanSessionId(@Param("scanSessionId") UUID scanSessionId);
}
