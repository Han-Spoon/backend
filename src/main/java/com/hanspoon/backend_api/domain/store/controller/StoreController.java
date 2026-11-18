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

@Tag(name = "Store", description = "가게 후보 검색 API(주변·이름)")
@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreSearchService storeSearchService;

    public StoreController(StoreSearchService storeSearchService) {
        this.storeSearchService = storeSearchService;
    }

    @Operation(summary = "가게 후보 검색(주변·이름). query 만 있으면 좌표 생략 가능. GPS 로그 노출 방지를 위해 JSON 본문 사용")
    @PostMapping("/candidates")
    public StoreCandidateListResponse findCandidates(@Valid @RequestBody StoreCandidateSearchRequest request) {
        return storeSearchService.findCandidates(request);
    }
}
