package com.hanspoon.backend_api.domain.scan.repository;

import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuAnalysisRepository extends JpaRepository<MenuAnalysis, UUID> {

    List<MenuAnalysis> findByScanSessionIdOrderByDisplayOrder(UUID scanSessionId);

    /** 연결된 가게 메뉴의 공개 사진. 연결 없는 행은 스캔 가게 + 정규화 메뉴명 일치. */
    @Query(
            value =
                    """
                    SELECT ma.id AS menuAnalysisId, ia.storage_key AS storageKey,
                           ia.attribution_text AS attributionText
                    FROM menu_analyses ma
                    JOIN scan_sessions ss ON ss.id = ma.scan_session_id
                    JOIN store_menus sm ON sm.store_id = ss.store_id
                        AND (sm.id = ma.store_menu_id
                             OR (ma.store_menu_id IS NULL AND sm.name_normalized = normalize_food_name(ma.display_name)))
                    JOIN image_assets ia ON ia.id = sm.image_asset_id AND ia.status = 'active'
                    WHERE ma.scan_session_id = :scanSessionId
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
