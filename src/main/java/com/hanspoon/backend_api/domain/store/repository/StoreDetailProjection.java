package com.hanspoon.backend_api.domain.store.repository;

/** 가게 상세 상단 정보. 썸네일은 S3 키. */
public interface StoreDetailProjection {

    Long getStoreId();

    String getName();

    String getBranchName();

    String getRoadAddress();

    String getFloorInfo();

    Double getLatitude();

    Double getLongitude();

    String getCategoryCode();

    String getCategoryName();

    Boolean getVerified();

    Integer getReviewCount();

    String getThumbnailStorageKey();
}
