package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.scan.dto.MenuResult;
import com.hanspoon.backend_api.domain.scan.dto.ScanCreatedResponse;
import com.hanspoon.backend_api.domain.scan.dto.ScanResultResponse;
import com.hanspoon.backend_api.domain.scan.dto.StartScanRequest;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.upload.service.BlobStorageService;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스캔 시작/조회. 시작은 세션을 PROCESSING 으로 저장(즉시 커밋)한 뒤 {@link ScanProcessor} 비동기 처리를 트리거한다.
 *
 * <p>startScan 은 의도적으로 비트랜잭션 — 세션 저장이 즉시 커밋돼야 별도 스레드의 비동기 작업이 그 행을 조회할 수 있다.
 */
@Service
public class ScanService {

    private final BlobStorageService blobStorageService;
    private final ScanSessionRepository scanSessionRepository;
    private final MenuAnalysisRepository menuAnalysisRepository;
    private final ScanProcessor scanProcessor;

    public ScanService(
            BlobStorageService blobStorageService,
            ScanSessionRepository scanSessionRepository,
            MenuAnalysisRepository menuAnalysisRepository,
            ScanProcessor scanProcessor) {
        this.blobStorageService = blobStorageService;
        this.scanSessionRepository = scanSessionRepository;
        this.menuAnalysisRepository = menuAnalysisRepository;
        this.scanProcessor = scanProcessor;
    }

    public ScanCreatedResponse startScan(UUID userId, StartScanRequest request) {
        String storageKey = blobStorageService.extractStorageKey(request.storageKey());
        // title 은 생성 시 null — 조회 때 기본값(스캔 시각)으로 보이고, 수정은 마이페이지 API 담당
        ScanSession session =
                scanSessionRepository.save(ScanSession.create(userId, null, null, null, ScanStatus.PROCESSING, null));
        scanProcessor.process(session.getId(), userId, storageKey, request.source());
        return new ScanCreatedResponse(session.getId(), session.getScanStatus());
    }

    @Transactional(readOnly = true)
    public ScanResultResponse getScan(UUID userId, UUID scanId) {
        ScanSession session = scanSessionRepository
                .findByIdAndUserId(scanId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_NOT_FOUND));
        List<MenuResult> menus = menuAnalysisRepository.findByScanSessionIdOrderByDisplayOrder(scanId).stream()
                .map(ScanService::toMenuResult)
                .toList();
        // title 은 유저 수정본(없으면 null). 기본 제목(스캔 시각) 현지화는 scannedAt 으로 FE 가 로케일 포맷.
        return new ScanResultResponse(
                session.getId(),
                session.getScanStatus(),
                session.getTitle(),
                session.getMenuCount(),
                session.getRiskyMenuCount(),
                session.getScannedAt(),
                menus);
    }

    private static MenuResult toMenuResult(MenuAnalysis m) {
        return new MenuResult(
                m.getDisplayOrder(),
                m.getMenuNameKo(),
                m.getMenuNameEn(),
                m.getPriceText(),
                m.getIsSpicy(),
                m.getRiskLevel(),
                m.getNeedGpt(),
                m.getHitTags(),
                m.getTriggeredFlags(),
                m.getRiskReasons());
    }
}
