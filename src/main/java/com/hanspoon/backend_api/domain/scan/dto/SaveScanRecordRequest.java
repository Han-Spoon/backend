package com.hanspoon.backend_api.domain.scan.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hanspoon.backend_api.domain.scan.entity.ScanFeedbackAnswer;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 스캔 기록 저장 요청.
 *
 * @param storeId 가게 없이 시작한 스캔에 사후 연결할 가게 ID
 * @param storeMatchMethod 사후 연결 가게를 찾은 경로. storeId와 함께 입력하거나 함께 생략
 * @param feedback 프로필 항목 ID별 식단 의사소통 경험. 응답하지 않은 항목은 생략
 */
@Schema(description = "스캔 기록 저장 요청")
public record SaveScanRecordRequest(
        @Schema(description = "사후 연결할 가게 ID(선택)", example = "10342") @Positive Long storeId,
        @Schema(description = "가게 후보 검색 경로(선택)", example = "name_search") StoreMatchMethod storeMatchMethod,
        @Schema(description = "프로필 항목별 의사소통 피드백") @Size(max = 32) Map<@NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9:_-]+") String, @NotNull ScanFeedbackAnswer>
                        feedback) {

    @AssertTrue(message = "storeId and storeMatchMethod must be provided together") @JsonIgnore
    public boolean isStoreContextComplete() {
        return (storeId == null) == (storeMatchMethod == null);
    }
}
