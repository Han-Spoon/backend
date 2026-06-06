package com.hanspoon.backend_api.domain.scan.controller;

import com.hanspoon.backend_api.domain.scan.dto.ScanCreatedResponse;
import com.hanspoon.backend_api.domain.scan.dto.ScanResultResponse;
import com.hanspoon.backend_api.domain.scan.dto.StartScanRequest;
import com.hanspoon.backend_api.domain.scan.service.ScanService;
import com.hanspoon.backend_api.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Scan", description = "메뉴판 스캔 시작/조회 API")
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

    @Operation(summary = "스캔 결과 조회(폴링). 본인 스캔만 조회 가능")
    @GetMapping("/{scanId}")
    public ScanResultResponse getScan(@CurrentUser String userId, @PathVariable UUID scanId) {
        return scanService.getScan(UUID.fromString(userId), scanId);
    }
}
