package com.hanspoon.backend_api.domain.scan.repository;

import java.util.UUID;

/** 스캔 메뉴별 가게 메뉴 사진. */
public interface MenuPhotoProjection {

    UUID getMenuAnalysisId();

    String getStorageKey();

    String getAttributionText();
}
