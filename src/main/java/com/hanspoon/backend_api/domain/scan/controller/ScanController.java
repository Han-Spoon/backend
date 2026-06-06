package com.hanspoon.backend_api.domain.scan.controller;

import com.hanspoon.backend_api.domain.scan.dto.ScanCreatedResponse;
import com.hanspoon.backend_api.domain.scan.dto.ScanHistoryItem;
import com.hanspoon.backend_api.domain.scan.dto.ScanResultResponse;
import com.hanspoon.backend_api.domain.scan.dto.StartScanRequest;
import com.hanspoon.backend_api.domain.scan.dto.UpdateScanTitleRequest;
import com.hanspoon.backend_api.domain.scan.service.ScanService;
import com.hanspoon.backend_api.global.common.PageResponse;
import com.hanspoon.backend_api.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Scan", description = "메뉴판 스캔 시작/조회/이력 API")
@RestController
@RequestMapping("/api/v1/scans")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @Operation(summary = "스캔 시작(비동기). 202 로 scanId 반환 후 OCR/판정은 백그라운드 처리")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ScanCreatedResponse startScan(@CurrentUser String userId, @Valid @RequestBody StartScanRequest request) {
        return scanService.startScan(UUID.fromString(userId), request);
    }

    @Operation(summary = "스캔 이력 목록 조회(최신순). 마이페이지/메인 공용. 본인 스캔만")
    @GetMapping
    public PageResponse<ScanHistoryItem> getScans(
            @CurrentUser String userId, @PageableDefault(size = 20) Pageable pageable) {
        // 정렬은 createdAt 내림차순 고정 — scannedAt 은 processing/failed 시 null 이라 정렬 키로 부적합
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        return scanService.getScans(UUID.fromString(userId), sorted);
    }

    @Operation(summary = "스캔 결과 조회(폴링). 본인 스캔만 조회 가능")
    @GetMapping("/{scanId}")
    public ScanResultResponse getScan(@CurrentUser String userId, @PathVariable UUID scanId) {
        return scanService.getScan(UUID.fromString(userId), scanId);
    }

    @Operation(summary = "스캔 이력 제목 수정. 본인 스캔만. 없거나 타인 소유면 404")
    @PatchMapping("/{scanId}")
    public ScanHistoryItem updateTitle(
            @CurrentUser String userId, @PathVariable UUID scanId, @Valid @RequestBody UpdateScanTitleRequest request) {
        return scanService.updateTitle(UUID.fromString(userId), scanId, request);
    }

    @Operation(summary = "스캔 이력 삭제(연관 이미지/분석 CASCADE). 본인 스캔만. 없거나 타인 소유면 404")
    @DeleteMapping("/{scanId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScan(@CurrentUser String userId, @PathVariable UUID scanId) {
        scanService.deleteScan(UUID.fromString(userId), scanId);
    }
}
