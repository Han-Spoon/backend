package com.hanspoon.backend_api.domain.scan.repository;

import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuAnalysisRepository extends JpaRepository<MenuAnalysis, UUID> {

    List<MenuAnalysis> findByScanSessionIdOrderByDisplayOrder(UUID scanSessionId);

    /**
     * 메뉴 공개 사진. 가게 메뉴 사진 우선, 없으면 판정 표준 메뉴 이미지.
     * 가게 메뉴는 연결 행, 연결 없으면 스캔 가게 + 정규화 메뉴명 일치.
     */
    @Query(
            value =
                    """
                    SELECT ma.id AS menuAnalysisId,
                           CASE WHEN sia.id IS NOT NULL THEN sia.storage_key ELSE mia.storage_key END AS storageKey,
                           CASE WHEN sia.id IS NOT NULL THEN sia.attribution_text ELSE mia.attribution_text END
                               AS attributionText
                    FROM menu_analyses ma
                    JOIN scan_sessions ss ON ss.id = ma.scan_session_id
                    LEFT JOIN store_menus sm ON sm.store_id = ss.store_id
                        AND (sm.id = ma.store_menu_id
                             OR (ma.store_menu_id IS NULL AND sm.name_normalized = normalize_food_name(ma.display_name)))
                    LEFT JOIN image_assets sia ON sia.id = sm.image_asset_id AND sia.status = 'active'
                    LEFT JOIN menus m ON m.id = ma.menu_id
                    LEFT JOIN image_assets mia ON mia.id = m.image_asset_id AND mia.status = 'active'
                    WHERE ma.scan_session_id = :scanSessionId
                      AND (sia.id IS NOT NULL OR mia.id IS NOT NULL)
                    """,
            nativeQuery = true)
    List<MenuPhotoProjection> findPhotosByScanSessionId(@Param("scanSessionId") UUID scanSessionId);

    /** 정규 메뉴(menus.name_i18n)의 사용자 언어 이름. 없는 언어는 제외. */
    @Query(
            value =
                    """
                    SELECT ma.id AS menuAnalysisId, m.name_i18n ->> :locale AS translatedName
                    FROM menu_analyses ma
                    JOIN menus m ON m.id = ma.menu_id
                    WHERE ma.scan_session_id = :scanSessionId
                      AND m.name_i18n ->> :locale IS NOT NULL
                    """,
            nativeQuery = true)
    List<MenuTranslationProjection> findTranslatedNames(
            @Param("scanSessionId") UUID scanSessionId, @Param("locale") String locale);
}
