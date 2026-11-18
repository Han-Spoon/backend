package com.hanspoon.backend_api.domain.scan.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

/**
 * 스캔 기록 저장 요청.
 *
 * @param storeId 가게 없이 시작한 스캔에 사후 연결할 가게 ID
 * @param storeMatchMethod 사후 연결 가게를 찾은 경로. storeId와 함께 입력하거나 함께 생략
 */
@Schema(description = "스캔 기록 저장 요청")
public record SaveScanRecordRequest(
        @Schema(description = "사후 연결할 가게 ID(선택)", example = "10342") @Positive Long storeId,
        @Schema(description = "가게 후보 검색 경로(선택)", example = "name_search") StoreMatchMethod storeMatchMethod) {

    @AssertTrue(message = "storeId and storeMatchMethod must be provided together") @JsonIgnore
    public boolean isStoreContextComplete() {
        return (storeId == null) == (storeMatchMethod == null);
    }

    /** store_detail 은 스캔 시작 경로라 사후 연결에는 쓰지 않는다(ck_scan_records_match_method 3종). */
    @AssertTrue(message = "storeMatchMethod store_detail cannot be used to attach a store to a record") @JsonIgnore
    public boolean isAttachMethodAllowed() {
        return storeMatchMethod != StoreMatchMethod.STORE_DETAIL;
    }
}
