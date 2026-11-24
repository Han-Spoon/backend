package com.hanspoon.backend_api.domain.store.repository;

/** 가게 대표 사진 S3 키. */
public interface StoreThumbnailProjection {

    Long getStoreId();

    String getStorageKey();
}
