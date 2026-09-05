package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.scan.dto.MenuResult;
import com.hanspoon.backend_api.domain.scan.dto.ScanCreatedResponse;
import com.hanspoon.backend_api.domain.scan.dto.ScanHistoryItem;
import com.hanspoon.backend_api.domain.scan.dto.ScanResultResponse;
import com.hanspoon.backend_api.domain.scan.dto.StartScanRequest;
import com.hanspoon.backend_api.domain.scan.dto.UpdateScanTitleRequest;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.upload.service.S3StorageService;
import com.hanspoon.backend_api.global.common.PageResponse;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScanService {

    private final S3StorageService s3StorageService;
    private final ScanSessionRepository scanSessionRepository;
    private final MenuAnalysisRepository menuAnalysisRepository;
    private final ScanProcessor scanProcessor;

    public ScanService(
            S3StorageService s3StorageService,
            ScanSessionRepository scanSessionRepository,
            MenuAnalysisRepository menuAnalysisRepository,
            ScanProcessor scanProcessor) {
        this.s3StorageService = s3StorageService;
        this.scanSessionRepository = scanSessionRepository;
        this.menuAnalysisRepository = menuAnalysisRepository;
        this.scanProcessor = scanProcessor;
    }

    public ScanCreatedResponse startScan(UUID userId, StartScanRequest request) {
        // 형식 · 소유권 검증 (외부 입력을 받는 유일한 지점)
        String storageKey = s3StorageService.resolveKey(userId, request.storageKey());

        // presigned PUT 은 서버가 내용을 모르므로 실제 업로드 여부·크기·타입을 여기서 확인한다.
        // 비동기로 넘긴 뒤 실패하면 사용자는 폴링만 하다 FAILED 를 받게 된다.
        s3StorageService.verifyUploadObject(storageKey);

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
                menus,
                session.getRetakeReasons());
    }

    /** 본인 스캔 이력 목록(최신순). 분석이 끝난 completed 만 노출, menus 는 미포함. */
    @Transactional(readOnly = true)
    public PageResponse<ScanHistoryItem> getScans(UUID userId, Pageable pageable) {
        return PageResponse.of(
                scanSessionRepository.findByUserIdAndScanStatus(userId, ScanStatus.COMPLETED, pageable),
                ScanHistoryItem::from);
    }

    /** 본인 스캔 이력 제목 수정. 없거나 타인 소유면 SCAN_NOT_FOUND. */
    @Transactional
    public ScanHistoryItem updateTitle(UUID userId, UUID scanId, UpdateScanTitleRequest request) {
        ScanSession session = scanSessionRepository
                .findByIdAndUserId(scanId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_NOT_FOUND));
        session.changeTitle(request.title());
        return ScanHistoryItem.from(session);
    }

    /** 본인 스캔 이력 삭제. 연관 menu_images/menu_analyses 는 FK CASCADE 로 함께 삭제. */
    @Transactional
    public void deleteScan(UUID userId, UUID scanId) {
        ScanSession session = scanSessionRepository
                .findByIdAndUserId(scanId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_NOT_FOUND));
        scanSessionRepository.delete(session);
    }

    private static MenuResult toMenuResult(MenuAnalysis m) {
        return new MenuResult(
                m.getDisplayOrder(),
                m.getMenuNameKo(),
                m.getMenuNameEn(),
                m.getPriceText(),
                m.getIsSpicy(),
                m.getRiskLevel(),
                m.getHitTags(),
                m.getMessage(),
                m.getOwnerCard());
    }
}
