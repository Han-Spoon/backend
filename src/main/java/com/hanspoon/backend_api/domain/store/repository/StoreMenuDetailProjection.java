package com.hanspoon.backend_api.domain.store.repository;

/** 가게 상세 메뉴 1건. 이미지는 S3 키, 출처 구분(store | standard). */
public interface StoreMenuDetailProjection {

    Long getStoreMenuId();

    String getName();

    String getTranslatedName();

    String getDescription();

    String getPriceText();

    String getPriceOptions();

    Long getMenuId();

    String getImageStorageKey();

    String getImageAttribution();

    String getImageSource();
}
