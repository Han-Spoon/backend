package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.scan.dto.SaveScanRecordRequest;
import com.hanspoon.backend_api.domain.scan.dto.ScanRecordResponse;
import com.hanspoon.backend_api.domain.scan.entity.ScanFeedbackAnswer;
import com.hanspoon.backend_api.domain.scan.entity.ScanRecord;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.ScanRecordRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.store.entity.Store;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.domain.store.entity.StoreStatus;
import com.hanspoon.backend_api.domain.store.repository.StoreRepository;
import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.VegetarianType;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScanRecordService {

    private static final Set<String> SUPPORTED_FEEDBACK_ITEM_IDS = supportedFeedbackItemIds();

    private final ScanSessionRepository scanSessionRepository;
    private final ScanRecordRepository scanRecordRepository;
    private final StoreRepository storeRepository;

    public ScanRecordService(
            ScanSessionRepository scanSessionRepository,
            ScanRecordRepository scanRecordRepository,
            StoreRepository storeRepository) {
        this.scanSessionRepository = scanSessionRepository;
        this.scanRecordRepository = scanRecordRepository;
        this.storeRepository = storeRepository;
    }

    /** 같은 scanId에 대한 PUT은 한 행을 갱신한다. 스캔 행 잠금으로 최초 생성 경쟁도 직렬화한다. */
    @Transactional
    public ScanRecordResponse save(UUID userId, UUID scanId, SaveScanRecordRequest request) {
        ScanSession session = scanSessionRepository
                .findByIdAndUserIdForUpdate(scanId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_NOT_FOUND));
        if (session.getScanStatus() != ScanStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.SCAN_NOT_COMPLETED);
        }
        if (session.getStoreId() != null && request.storeId() != null) {
            throw new BusinessException(ErrorCode.SCAN_STORE_CONTEXT_LOCKED);
        }

        ScanRecord record = scanRecordRepository.findById(scanId).orElse(null);
        AttachedStore attachedStore = resolveAttachedStore(session, request, record);
        Map<String, ScanFeedbackAnswer> feedback = request.feedback() == null ? Map.of() : request.feedback();
        if (!SUPPORTED_FEEDBACK_ITEM_IDS.containsAll(feedback.keySet())) {
            throw new BusinessException(ErrorCode.INVALID_SCAN_FEEDBACK);
        }
        if (session.getStoreId() == null && attachedStore == null && !feedback.isEmpty()) {
            throw new BusinessException(ErrorCode.SCAN_FEEDBACK_REQUIRES_STORE);
        }

        Instant savedAt = Instant.now();
        if (record == null) {
            record = ScanRecord.create(
                    scanId,
                    attachedStore == null ? null : attachedStore.id(),
                    attachedStore == null ? null : attachedStore.name(),
                    attachedStore == null ? null : attachedStore.matchMethod(),
                    feedback,
                    savedAt);
        }
        record.update(
                attachedStore == null ? null : attachedStore.id(),
                attachedStore == null ? null : attachedStore.name(),
                attachedStore == null ? null : attachedStore.matchMethod(),
                feedback,
                savedAt);
        scanRecordRepository.save(record);
        return ScanRecordResponse.of(session, record);
    }

    @Transactional(readOnly = true)
    public ScanRecordResponse get(UUID userId, UUID scanId) {
        ScanSession session = scanSessionRepository
                .findByIdAndUserId(scanId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_NOT_FOUND));
        ScanRecord record = scanRecordRepository
                .findById(scanId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_RECORD_NOT_FOUND));
        return ScanRecordResponse.of(session, record);
    }

    @Transactional(readOnly = true)
    public ScanRecordResponse findResponse(ScanSession session) {
        return scanRecordRepository
                .findById(session.getId())
                .map(record -> ScanRecordResponse.of(session, record))
                .orElse(null);
    }

    private AttachedStore resolveAttachedStore(
            ScanSession session, SaveScanRecordRequest request, ScanRecord existingRecord) {
        if (session.getStoreId() != null || request.storeId() == null) {
            return null;
        }
        if (existingRecord != null
                && Objects.equals(existingRecord.getAttachedStoreId(), request.storeId())
                && existingRecord.getAttachedStoreMatchMethod() == request.storeMatchMethod()) {
            // 이미 저장한 가게는 이후 비활성화돼도 과거 기록 갱신이 가능해야 한다.
            return new AttachedStore(
                    existingRecord.getAttachedStoreId(),
                    existingRecord.getAttachedStoreNameSnapshot(),
                    existingRecord.getAttachedStoreMatchMethod());
        }
        Store store = storeRepository
                .findByIdAndStatus(request.storeId(), StoreStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        return new AttachedStore(store.getId(), store.getName(), request.storeMatchMethod());
    }

    private static Set<String> supportedFeedbackItemIds() {
        Set<String> ids = new HashSet<>();
        for (VegetarianType type : VegetarianType.values()) {
            ids.add("vegan:" + type.getCode());
        }
        for (ReligionType type : ReligionType.values()) {
            if (type != ReligionType.NONE) {
                ids.add("religion:" + type.getCode());
            }
        }
        for (AllergyCode code : AllergyCode.values()) {
            ids.add("allergy:" + code.getCode());
        }
        ids.add("preference:no-spicy");
        ids.add("preference:no-alcohol");
        return Set.copyOf(ids);
    }

    private record AttachedStore(Long id, String name, StoreMatchMethod matchMethod) {}
}
