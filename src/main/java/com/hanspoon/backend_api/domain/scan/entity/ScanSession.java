package com.hanspoon.backend_api.domain.scan.entity;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 스캔 세션. users 와 N:1 (FK ON DELETE CASCADE).
 * danger/caution/safe 카운터는 Judge 저장 트랜잭션에서 메뉴별 위험도로 기록.
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

    @Column(name = "store_id")
    private Long storeId;

    /** 가게명이 변경되거나 비활성화돼도 과거 스캔 이력을 보존하는 서버 생성 스냅샷. */
    @Column(name = "store_name_snapshot", length = 200)
    private String storeNameSnapshot;

    @Column(name = "store_match_method", length = 20)
    private StoreMatchMethod storeMatchMethod;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "menu_count")
    private Integer menuCount;

    /** 이력 목록용 비정규화 카운터(C10). 목록 조회가 menu_analyses 를 집계하지 않게 한다. */
    @Column(name = "danger_count")
    private Integer dangerCount;

    @Column(name = "caution_count")
    private Integer cautionCount;

    @Column(name = "safe_count")
    private Integer safeCount;

    @Column(name = "locale", length = 5)
    private String locale;

    /** Judge 요청 user_profile 스냅샷. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diet_snapshot", columnDefinition = "jsonb")
    private String dietSnapshot;

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
            Long storeId,
            String storeNameSnapshot,
            StoreMatchMethod storeMatchMethod,
            String title,
            Integer menuCount,
            ScanStatus scanStatus,
            Instant scannedAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.storageKey = storageKey;
        this.storeId = storeId;
        this.storeNameSnapshot = storeNameSnapshot;
        this.storeMatchMethod = storeMatchMethod;
        this.title = title;
        this.menuCount = menuCount;
        this.scanStatus = scanStatus;
        this.scannedAt = scannedAt;
    }

    public static ScanSession create(
            UUID userId, String title, Integer menuCount, ScanStatus scanStatus, Instant scannedAt) {
        return new ScanSession(userId, null, null, null, null, title, menuCount, scanStatus, scannedAt);
    }

    public static ScanSession start(
            UUID userId, String storageKey, Long storeId, String storeNameSnapshot, StoreMatchMethod storeMatchMethod) {
        return new ScanSession(
                userId,
                storageKey,
                storeId,
                storeNameSnapshot,
                storeMatchMethod,
                null,
                null,
                ScanStatus.PROCESSING,
                null);
    }

    /** 사용자가 가게 연결을 건너뛴 신규 스캔. 세 가게 컨텍스트 컬럼은 모두 null로 유지한다. */
    public static ScanSession startWithoutStore(UUID userId, String storageKey) {
        return new ScanSession(userId, storageKey, null, null, null, null, null, ScanStatus.PROCESSING, null);
    }

    /** 가게 도입 전 세션을 재현하는 테스트·마이그레이션 전용 팩토리. 신규 스캔에서 사용하지 않는다. */
    public static ScanSession startLegacy(UUID userId, String storageKey) {
        return new ScanSession(userId, storageKey, null, null, null, null, null, ScanStatus.PROCESSING, null);
    }

    public boolean hasSameStoreContext(Long storeId, StoreMatchMethod storeMatchMethod) {
        return Objects.equals(this.storeId, storeId) && this.storeMatchMethod == storeMatchMethod;
    }

    /** Prepare 저장 후 메뉴 수/스캔 시각/판정 언어 반영. */
    public void applyPrepareResult(Integer menuCount, Instant scannedAt, String locale) {
        ensureProcessing();
        this.menuCount = menuCount;
        this.scannedAt = scannedAt;
        this.locale = locale;
        this.failureCode = null;
    }

    /** Judge 저장 후 위험도별 메뉴 수·식단 스냅샷·완료. 판정 없는 메뉴(null)는 미집계. */
    public void applyJudgeResult(List<RiskLevel> riskLevels, String dietSnapshot) {
        ensureProcessing();
        this.dangerCount = count(riskLevels, RiskLevel.DANGER);
        this.cautionCount = count(riskLevels, RiskLevel.CAUTION);
        this.safeCount = count(riskLevels, RiskLevel.SAFE);
        this.dietSnapshot = dietSnapshot;
        this.scanStatus = ScanStatus.COMPLETED;
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

    /** 재촬영 필요 시 상태와 Prepare 가 제공한 사유·개선 안내를 반영. */
    public void applyNeedsRetake(List<String> retakeReasons, List<String> retakeSuggestions) {
        ensureProcessing();
        this.scanStatus = ScanStatus.NEEDS_RETAKE;
        this.retakeReasons = retakeReasons;
        this.retakeSuggestions = retakeSuggestions;
        this.failureCode = null;
    }

    private static int count(List<RiskLevel> riskLevels, RiskLevel level) {
        return (int) riskLevels.stream().filter(level::equals).count();
    }

    private void ensureProcessing() {
        if (scanStatus != ScanStatus.PROCESSING) {
            throw new IllegalStateException("Scan session is already terminal: " + scanStatus);
        }
    }
}
