package com.hanspoon.backend_api.domain.scan.dto;

import com.hanspoon.backend_api.domain.scan.entity.ScanFeedbackAnswer;
import com.hanspoon.backend_api.domain.scan.entity.ScanRecord;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 명시적으로 보관한 스캔 기록. storeLocked=true면 스캔 시작 시 확정된 가게라 변경할 수 없다. */
@Schema(description = "저장된 스캔 기록")
public record ScanRecordResponse(
        UUID scanId,
        ScanStoreSummary store,
        boolean storeLocked,
        StoreMatchMethod storeMatchMethod,
        Map<String, ScanFeedbackAnswer> feedback,
        Instant savedAt) {

    public static ScanRecordResponse of(ScanSession session, ScanRecord record) {
        boolean storeLocked = session.getStoreId() != null;
        ScanStoreSummary store = storeLocked
                ? new ScanStoreSummary(session.getStoreId(), session.getStoreNameSnapshot())
                : record.getAttachedStoreId() == null
                        ? null
                        : new ScanStoreSummary(record.getAttachedStoreId(), record.getAttachedStoreNameSnapshot());
        StoreMatchMethod matchMethod =
                storeLocked ? session.getStoreMatchMethod() : record.getAttachedStoreMatchMethod();
        return new ScanRecordResponse(
                session.getId(),
                store,
                storeLocked,
                matchMethod,
                Map.copyOf(record.getFeedback()),
                record.getSavedAt());
    }
}
