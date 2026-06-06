package com.hanspoon.backend_api.domain.scan.entity;

import com.hanspoon.backend_api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "menu_count")
    private Integer menuCount;

    @Column(name = "risky_menu_count")
    private Integer riskyMenuCount;

    @Column(name = "scan_status", length = 20, nullable = false)
    private ScanStatus scanStatus;

    @Column(name = "scanned_at")
    private Instant scannedAt;

    private ScanSession(
            UUID userId,
            String title,
            Integer menuCount,
            Integer riskyMenuCount,
            ScanStatus scanStatus,
            Instant scannedAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
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
        return new ScanSession(userId, title, menuCount, riskyMenuCount, scanStatus, scannedAt);
    }

    /** OCR 완료 후 메뉴 수/스캔 시각 반영. */
    public void applyOcrResult(Integer menuCount, Instant scannedAt) {
        this.menuCount = menuCount;
        this.scannedAt = scannedAt;
    }

    /** 룰엔진 판정 후 위험 메뉴 수/상태 갱신. */
    public void applyRuleEngineResult(Integer riskyMenuCount, ScanStatus scanStatus) {
        this.riskyMenuCount = riskyMenuCount;
        this.scanStatus = scanStatus;
    }

    public void changeStatus(ScanStatus scanStatus) {
        this.scanStatus = scanStatus;
    }
}
