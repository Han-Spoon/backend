package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.scan.dto.SaveScanRecordRequest;
import com.hanspoon.backend_api.domain.scan.dto.ScanRecordResponse;
import com.hanspoon.backend_api.domain.scan.entity.ScanRecord;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.ScanRecordRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.store.entity.Store;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.domain.store.entity.StoreStatus;
import com.hanspoon.backend_api.domain.store.repository.StoreRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScanRecordService {

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
        Long storeId = attachedStore == null ? null : attachedStore.id();
        String storeName = attachedStore == null ? null : attachedStore.name();
        StoreMatchMethod matchMethod = attachedStore == null ? null : attachedStore.matchMethod();

        Instant savedAt = Instant.now();
        if (record == null) {
            record = ScanRecord.create(scanId, storeId, storeName, matchMethod, savedAt);
        } else {
            record.update(storeId, storeName, matchMethod, savedAt);
        }
        scanRecordRepository.save(record);
        return ScanRecordResponse.of(session, record);
    }

    /** 목록용 일괄 조회. 기록 없는 스캔은 키 없음. */
    @Transactional(readOnly = true)
    public Map<UUID, ScanRecord> findRecords(Collection<UUID> scanIds) {
        return scanRecordRepository.findAllById(scanIds).stream()
                .collect(Collectors.toMap(ScanRecord::getScanSessionId, Function.identity()));
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

    private record AttachedStore(Long id, String name, StoreMatchMethod matchMethod) {}
}
