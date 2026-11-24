package com.hanspoon.backend_api.domain.store.repository;

/** 반경·상호명 검색에 필요한 값만 조회하는 읽기 전용 후보 Projection. */
public interface StoreCandidateProjection {

    Long getStoreId();

    String getName();

    String getBranchName();

    String getRoadAddress();

    String getFloorInfo();

    Double getLatitude();

    Double getLongitude();

    Integer getDistanceMeters();

    Double getNameSimilarity();

    String getCategoryCode();

    String getCategoryName();

    Boolean getVerified();

    Integer getReviewCount();

    /** 메뉴 매칭으로 걸린 가게의 메뉴 표시명. 이름 매칭·반경 검색은 null. */
    String getMatchedMenuName();
}
