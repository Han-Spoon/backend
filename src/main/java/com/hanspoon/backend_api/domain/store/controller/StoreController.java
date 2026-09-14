package com.hanspoon.backend_api.domain.store.controller;

import com.hanspoon.backend_api.domain.store.dto.StoreCandidateListResponse;
import com.hanspoon.backend_api.domain.store.dto.StoreCandidateSearchRequest;
import com.hanspoon.backend_api.domain.store.service.StoreSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Store", description = "현재 위치 기반 가게 후보 검색 API")
@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreSearchService storeSearchService;

    public StoreController(StoreSearchService storeSearchService) {
        this.storeSearchService = storeSearchService;
    }

    @Operation(summary = "자체 DB에서 현재 위치 주변의 가게 후보 조회. GPS 로그 노출 방지를 위해 JSON 본문 사용")
    @PostMapping("/candidates")
    public StoreCandidateListResponse findCandidates(@Valid @RequestBody StoreCandidateSearchRequest request) {
        return storeSearchService.findCandidates(request);
    }
}
