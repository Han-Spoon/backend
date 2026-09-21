package com.hanspoon.backend_api.domain.scan.entity;

import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사용자가 명시적으로 보관한 스캔 기록. scan_sessions 와 1:1이다.
 *
 * <p>스캔 시작 시 선택한 가게는 {@link ScanSession}에 고정한다. attachedStore*는 가게 없이 시작한 스캔에 나중에
 * 연결한 가게만 표현해 분석 출처와 기록 분류를 섞지 않는다.
 */
@Entity
@Table(name = "scan_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScanRecord extends BaseEntity {

    @Id
    @Column(name = "scan_session_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID scanSessionId;

    @Column(name = "attached_store_id")
    private Long attachedStoreId;

    @Column(name = "attached_store_name_snapshot", length = 200)
    private String attachedStoreNameSnapshot;

    @Column(name = "attached_store_match_method", length = 20)
    private StoreMatchMethod attachedStoreMatchMethod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feedback", columnDefinition = "jsonb", nullable = false)
    private Map<String, ScanFeedbackAnswer> feedback;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    private ScanRecord(
            UUID scanSessionId,
            Long attachedStoreId,
            String attachedStoreNameSnapshot,
            StoreMatchMethod attachedStoreMatchMethod,
            Map<String, ScanFeedbackAnswer> feedback,
            Instant savedAt) {
        this.scanSessionId = scanSessionId;
        update(attachedStoreId, attachedStoreNameSnapshot, attachedStoreMatchMethod, feedback, savedAt);
    }

    public static ScanRecord create(
            UUID scanSessionId,
            Long attachedStoreId,
            String attachedStoreNameSnapshot,
            StoreMatchMethod attachedStoreMatchMethod,
            Map<String, ScanFeedbackAnswer> feedback,
            Instant savedAt) {
        return new ScanRecord(
                scanSessionId, attachedStoreId, attachedStoreNameSnapshot, attachedStoreMatchMethod, feedback, savedAt);
    }

    public void update(
            Long attachedStoreId,
            String attachedStoreNameSnapshot,
            StoreMatchMethod attachedStoreMatchMethod,
            Map<String, ScanFeedbackAnswer> feedback,
            Instant savedAt) {
        this.attachedStoreId = attachedStoreId;
        this.attachedStoreNameSnapshot = attachedStoreNameSnapshot;
        this.attachedStoreMatchMethod = attachedStoreMatchMethod;
        this.feedback = Map.copyOf(feedback);
        this.savedAt = savedAt;
    }
}
