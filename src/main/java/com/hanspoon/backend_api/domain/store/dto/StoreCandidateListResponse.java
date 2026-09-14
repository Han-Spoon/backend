package com.hanspoon.backend_api.domain.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 자체 DB 가게 후보 목록. 빈 목록이면 클라이언트가 Kakao fallback을 요청할 수 있다. */
@Schema(description = "가게 후보 목록")
public record StoreCandidateListResponse(List<StoreCandidateResponse> items) {

    public StoreCandidateListResponse {
        items = List.copyOf(items);
    }
}
