package com.hanspoon.backend_api.domain.store.repository;

/** 반경·상호명 검색에 필요한 값만 조회하는 읽기 전용 후보 Projection. */
public interface StoreCandidateProjection {

    Long getStoreId();

    String getName();

    String getBranchName();

    String getRoadAddress();

    Double getLatitude();

    Double getLongitude();

    Integer getDistanceMeters();

    Double getNameSimilarity();

    String getCategoryCode();

    String getCategoryName();

    Boolean getVerified();
}
