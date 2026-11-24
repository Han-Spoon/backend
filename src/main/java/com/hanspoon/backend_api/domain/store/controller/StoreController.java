package com.hanspoon.backend_api.domain.store.controller;

import com.hanspoon.backend_api.domain.store.dto.StoreCandidateListResponse;
import com.hanspoon.backend_api.domain.store.dto.StoreCandidateSearchRequest;
import com.hanspoon.backend_api.domain.store.dto.StoreDetailResponse;
import com.hanspoon.backend_api.domain.store.service.StoreDetailService;
import com.hanspoon.backend_api.domain.store.service.StoreSearchService;
import com.hanspoon.backend_api.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Store", description = "가게 후보 검색(주변·이름)·가게 상세 API")
@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreSearchService storeSearchService;
    private final StoreDetailService storeDetailService;

    public StoreController(StoreSearchService storeSearchService, StoreDetailService storeDetailService) {
        this.storeSearchService = storeSearchService;
        this.storeDetailService = storeDetailService;
    }

    @Operation(summary = "가게 후보 검색(주변·이름). query 만 있으면 좌표 생략 가능. GPS 로그 노출 방지를 위해 JSON 본문 사용")
    @PostMapping("/candidates")
    public StoreCandidateListResponse findCandidates(@Valid @RequestBody StoreCandidateSearchRequest request) {
        return storeSearchService.findCandidates(request);
    }

    @Operation(summary = "가게 상세. 영업 중 가게 정보와 메뉴(메뉴 사진 포함). 후기는 /stores/{storeId}/reviews")
    @GetMapping("/{storeId}")
    public StoreDetailResponse getDetail(@CurrentUser String userId, @PathVariable long storeId) {
        return storeDetailService.getDetail(UUID.fromString(userId), storeId);
    }
}
