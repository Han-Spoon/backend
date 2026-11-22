package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.ai.client.AiAnalyzeClient;
import com.hanspoon.backend_api.domain.ai.dto.analyze.PrepareRequest;
import com.hanspoon.backend_api.domain.ai.dto.analyze.PrepareResponse;
import com.hanspoon.backend_api.domain.ai.dto.analyze.PreparedItem;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanAiResult;
import com.hanspoon.backend_api.domain.scan.entity.ScanImage;
import com.hanspoon.backend_api.domain.upload.dto.VerifiedUpload;
import com.hanspoon.backend_api.domain.upload.service.S3StorageService;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prepare/Judge 경로의 AI 호출·응답 검증. @Transactional 추가 금지, 저장은 {@link AnalyzeStateWriter}.
 */
@Component
public class AnalyzeScanFlow {

    private static final String DEFAULT_SOURCE = "upload";

    private final AiAnalyzeClient aiAnalyzeClient;
    private final AnalyzeStateWriter analyzeStateWriter;
    private final S3StorageService s3StorageService;
    private final JsonMapper jsonMapper;

    public AnalyzeScanFlow(
            AiAnalyzeClient aiAnalyzeClient,
            AnalyzeStateWriter analyzeStateWriter,
            S3StorageService s3StorageService,
            JsonMapper jsonMapper) {
        this.aiAnalyzeClient = aiAnalyzeClient;
        this.analyzeStateWriter = analyzeStateWriter;
        this.s3StorageService = s3StorageService;
        this.jsonMapper = jsonMapper;
    }

    /** Prepare 호출·저장. 재촬영이면 empty. */
    public Optional<PreparedScan> prepare(UUID scanId, VerifiedUpload upload, String source, String locale) {
        String resolvedSource = source != null ? source : DEFAULT_SOURCE;
        PrepareResponse response = aiAnalyzeClient.prepare(PrepareRequest.of(
                UUID.randomUUID(),
                scanId.toString(),
                new PrepareRequest.Image(resolvedSource, upload.storageKey(), upload.versionId(), upload.eTag()),
                locale));
        validate(scanId, response);
        ScanImage scanImage = toScanImage(scanId, resolvedSource, upload);

        if (!response.usable()) {
            analyzeStateWriter.applyPrepareRetake(
                    scanImage,
                    scanId,
                    response.scanQuality().reasons(),
                    response.scanQuality().retakeSuggestions());
            return Optional.empty();
        }

        List<PreparedItem> items = readItems(scanId, response.preparedItems());
        ScanAiResult aiResult = ScanAiResult.prepared(
                scanId,
                response.prepareId(),
                response.schemaVersion(),
                response.preparedItemsHash(),
                jsonMapper.writeValueAsString(response.preparedItems()),
                jsonMapper.writeValueAsString(response.prepareSignals()),
                Instant.now());
        List<MenuAnalysis> analyses =
                analyzeStateWriter.applyPrepareResult(scanImage, aiResult, items, locale, Instant.now());
        return Optional.of(new PreparedScan(response, items, analyses));
    }

    private void validate(UUID scanId, PrepareResponse response) {
        if (response == null) {
            throw new BusinessException(ErrorCode.PREPARE_SERVICE_ERROR, "Prepare returned an empty response.");
        }
        if (!PrepareRequest.SCHEMA_VERSION.equals(response.schemaVersion())) {
            throw mismatch("schema_version " + response.schemaVersion());
        }
        if (!scanId.toString().equals(response.scanSessionId())) {
            throw mismatch("scan_session_id " + response.scanSessionId());
        }
        if (response.scanQuality() == null) {
            throw mismatch("scan_quality missing");
        }
        if (response.usable()
                && (response.prepareId() == null
                        || response.preparedItemsHash() == null
                        || response.prepareSignals() == null
                        || !response.prepareSignals().isObject())) {
            throw mismatch("prepare metadata missing");
        }
    }

    /** item_id = {scan_session_id}:{source_index}, source_index 중복 없음, 1개 이상. */
    private List<PreparedItem> readItems(UUID scanId, JsonNode preparedItems) {
        if (preparedItems == null || !preparedItems.isArray() || preparedItems.isEmpty()) {
            throw mismatch("usable prepare must contain at least one item");
        }
        List<PreparedItem> items = new ArrayList<>(preparedItems.size());
        Set<Integer> sourceIndexes = new HashSet<>();
        for (JsonNode node : preparedItems) {
            PreparedItem item = jsonMapper.treeToValue(node, PreparedItem.class);
            if (!(scanId + ":" + item.sourceIndex()).equals(item.itemId()) || !sourceIndexes.add(item.sourceIndex())) {
                throw mismatch("item_id " + item.itemId());
            }
            items.add(item);
        }
        return items;
    }

    private ScanImage toScanImage(UUID scanId, String source, VerifiedUpload upload) {
        return ScanImage.create(
                scanId,
                source,
                upload.storageKey(),
                s3StorageService.objectUri(upload.storageKey()),
                upload.contentType(),
                upload.contentLength(),
                upload.versionId(),
                upload.eTag());
    }

    private BusinessException mismatch(String detail) {
        return new BusinessException(ErrorCode.AI_RESULT_MISMATCH, "Prepare result mismatch: " + detail + ".");
    }

    /** Judge 입력. items·analyses 는 같은 순서. */
    public record PreparedScan(PrepareResponse response, List<PreparedItem> items, List<MenuAnalysis> analyses) {}
}
