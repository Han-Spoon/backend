package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.ai.dto.analyze.PreparedItem;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanAiResult;
import com.hanspoon.backend_api.domain.scan.entity.ScanImage;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanAiResultRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanImageRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.store.repository.StoreMenuRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prepare/Judge 경로의 저장 전담 컴포넌트. 외부 I/O 추가 금지.
 *
 * @see ScanStateWriter
 */
@Component
public class AnalyzeStateWriter {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeStateWriter.class);

    private final ScanSessionRepository scanSessionRepository;
    private final ScanImageRepository scanImageRepository;
    private final ScanAiResultRepository scanAiResultRepository;
    private final StoreMenuRepository storeMenuRepository;
    private final MenuAnalysisRepository menuAnalysisRepository;
    private final JsonMapper jsonMapper;

    public AnalyzeStateWriter(
            ScanSessionRepository scanSessionRepository,
            ScanImageRepository scanImageRepository,
            ScanAiResultRepository scanAiResultRepository,
            StoreMenuRepository storeMenuRepository,
            MenuAnalysisRepository menuAnalysisRepository,
            JsonMapper jsonMapper) {
        this.scanSessionRepository = scanSessionRepository;
        this.scanImageRepository = scanImageRepository;
        this.scanAiResultRepository = scanAiResultRepository;
        this.storeMenuRepository = storeMenuRepository;
        this.menuAnalysisRepository = menuAnalysisRepository;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Prepare 저장. 순서: scan_ai_results → 가게 있으면 store_menus upsert → menu_analyses.
     * 세션이 없으면 빈 목록.
     */
    @Transactional
    public List<MenuAnalysis> applyPrepareResult(
            ScanImage scanImage, ScanAiResult aiResult, List<PreparedItem> items, String locale, Instant scannedAt) {
        UUID scanId = aiResult.getScanSessionId();
        ScanSession session = scanSessionRepository.findById(scanId).orElse(null);
        if (session == null) {
            log.warn("Scan session not found while saving prepare result: {}", scanId);
            return List.of();
        }
        scanImageRepository.save(scanImage);
        scanAiResultRepository.save(aiResult);

        Long storeId = session.getStoreId();
        List<MenuAnalysis> analyses = new ArrayList<>(items.size());
        for (PreparedItem item : items) {
            String priceOptions =
                    item.priceOptions() != null ? jsonMapper.writeValueAsString(item.priceOptions()) : null;
            Long storeMenuId = storeId == null
                    ? null
                    : storeMenuRepository.upsertSeen(
                            storeId,
                            item.normalizedMenuName(),
                            item.displayName(),
                            item.description(),
                            item.priceText(),
                            priceOptions != null ? priceOptions : "[]",
                            scanId);
            analyses.add(MenuAnalysis.prepared(
                    scanId,
                    item.sourceIndex(),
                    item.displayName(),
                    item.description(),
                    item.priceText(),
                    priceOptions,
                    item.spicyObserved() ? Boolean.TRUE : null,
                    storeId,
                    storeMenuId));
        }
        menuAnalysisRepository.saveAll(analyses);
        session.applyPrepareResult(items.size(), scannedAt, locale);
        return analyses;
    }

    /** 재촬영 필요. 원본 이미지와 사유만 저장. */
    @Transactional
    public void applyPrepareRetake(
            ScanImage scanImage, UUID scanId, List<String> retakeReasons, List<String> retakeSuggestions) {
        ScanSession session = scanSessionRepository.findById(scanId).orElse(null);
        if (session == null) {
            log.warn("Scan session not found while saving retake: {}", scanId);
            return;
        }
        scanImageRepository.save(scanImage);
        session.applyNeedsRetake(retakeReasons, retakeSuggestions);
    }
}
