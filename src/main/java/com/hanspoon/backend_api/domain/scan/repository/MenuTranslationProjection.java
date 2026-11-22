package com.hanspoon.backend_api.domain.scan.repository;

import java.util.UUID;

/** 스캔 메뉴 id + 번역 메뉴명. */
public interface MenuTranslationProjection {

    UUID getMenuAnalysisId();

    String getTranslatedName();
}
