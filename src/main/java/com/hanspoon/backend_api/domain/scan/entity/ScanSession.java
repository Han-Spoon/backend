package com.hanspoon.backend_api.domain.scan.entity;

import com.hanspoon.backend_api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 스캔 세션. users 와 N:1 (FK ON DELETE CASCADE). OCR 결과의 scan_session 매핑.
 * riskyMenuCount 는 룰엔진 단계에서 채워진다.
 */
@Entity
@Table(name = "scan_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScanSession extends BaseEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    /** 업로드 객체 하나당 스캔 세션 하나만 생성하기 위한 멱등 키. 기존 데이터는 null일 수 있다. */
    @Column(name = "storage_key", length = 512)
    private String storageKey;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "menu_count")
    private Integer menuCount;

    @Column(name = "risky_menu_count")
    private Integer riskyMenuCount;

    @Column(name = "scan_status", length = 20, nullable = false)
    private ScanStatus scanStatus;

    /** 비동기 처리 실패 원인. 사용자에게 내부 예외 메시지를 노출하지 않고 재시도 판단에 사용한다. */
    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @Column(name = "scanned_at")
    private Instant scannedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retake_reasons", columnDefinition = "jsonb")
    private List<String> retakeReasons;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retake_suggestions", columnDefinition = "jsonb")
    private List<String> retakeSuggestions;

    private ScanSession(
            UUID userId,
            String storageKey,
            String title,
            Integer menuCount,
            Integer riskyMenuCount,
            ScanStatus scanStatus,
            Instant scannedAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.storageKey = storageKey;
        this.title = title;
        this.menuCount = menuCount;
        this.riskyMenuCount = riskyMenuCount;
        this.scanStatus = scanStatus;
        this.scannedAt = scannedAt;
    }

    public static ScanSession create(
            UUID userId,
            String title,
            Integer menuCount,
            Integer riskyMenuCount,
            ScanStatus scanStatus,
            Instant scannedAt) {
        return new ScanSession(userId, null, title, menuCount, riskyMenuCount, scanStatus, scannedAt);
    }

    public static ScanSession start(UUID userId, String storageKey) {
        return new ScanSession(userId, storageKey, null, null, null, ScanStatus.PROCESSING, null);
    }

    /** OCR 완료 후 메뉴 수/스캔 시각 반영. */
    public void applyOcrResult(Integer menuCount, Instant scannedAt) {
        ensureProcessing();
        this.menuCount = menuCount;
        this.scannedAt = scannedAt;
        this.failureCode = null;
    }

    /** 룰엔진 판정 후 위험 메뉴 수/상태 갱신. */
    public void applyRuleEngineResult(Integer riskyMenuCount, ScanStatus scanStatus) {
        ensureProcessing();
        this.riskyMenuCount = riskyMenuCount;
        this.scanStatus = scanStatus;
        this.failureCode = null;
    }

    public void markFailed(String failureCode) {
        if (this.scanStatus != ScanStatus.PROCESSING) {
            return;
        }
        this.scanStatus = ScanStatus.FAILED;
        this.failureCode = failureCode;
    }

    /** 유저가 이력 제목을 수정. */
    public void changeTitle(String title) {
        this.title = title;
    }

    /** 재촬영 필요 시 상태와 OCR 이 제공한 사유·개선 안내를 반영. */
    public void applyNeedsRetake(List<String> retakeReasons, List<String> retakeSuggestions) {
        ensureProcessing();
        this.scanStatus = ScanStatus.NEEDS_RETAKE;
        this.retakeReasons = retakeReasons;
        this.retakeSuggestions = retakeSuggestions;
        this.failureCode = null;
    }

    private void ensureProcessing() {
        if (scanStatus != ScanStatus.PROCESSING) {
            throw new IllegalStateException("Scan session is already terminal: " + scanStatus);
        }
    }
}
