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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 스캔 AI 원본(scan_ai_results). scan_sessions 와 1:1 (FK ON DELETE CASCADE).
 *
 * <p>prepared_items·prepare_signals 는 AI 응답 JSON 문자열 그대로 저장.
 */
@Entity
@Table(name = "scan_ai_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScanAiResult extends BaseEntity {

    @Id
    @Column(name = "scan_session_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID scanSessionId;

    @Column(name = "prepare_id", columnDefinition = "uuid", nullable = false, unique = true)
    private UUID prepareId;

    @Column(name = "schema_version", length = 10, nullable = false)
    private String schemaVersion;

    @Column(name = "prepared_items_hash", length = 71, nullable = false)
    private String preparedItemsHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prepared_items", columnDefinition = "jsonb", nullable = false)
    private String preparedItems;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prepare_signals", columnDefinition = "jsonb", nullable = false)
    private String prepareSignals;

    @Column(name = "prepared_at", nullable = false)
    private Instant preparedAt;

    @Column(name = "knowledge_revision", length = 64)
    private String knowledgeRevision;

    @Column(name = "bundle_hash", length = 71)
    private String bundleHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "judge_evidence", columnDefinition = "jsonb")
    private String judgeEvidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "judge_warnings", columnDefinition = "jsonb")
    private String judgeWarnings;

    /** Judge 응답 원본. 90일 보관. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "judge_raw", columnDefinition = "jsonb")
    private String judgeRaw;

    @Column(name = "judged_at")
    private Instant judgedAt;

    private ScanAiResult(
            UUID scanSessionId,
            UUID prepareId,
            String schemaVersion,
            String preparedItemsHash,
            String preparedItems,
            String prepareSignals,
            Instant preparedAt) {
        this.scanSessionId = scanSessionId;
        this.prepareId = prepareId;
        this.schemaVersion = schemaVersion;
        this.preparedItemsHash = preparedItemsHash;
        this.preparedItems = preparedItems;
        this.prepareSignals = prepareSignals;
        this.preparedAt = preparedAt;
    }

    public static ScanAiResult prepared(
            UUID scanSessionId,
            UUID prepareId,
            String schemaVersion,
            String preparedItemsHash,
            String preparedItems,
            String prepareSignals,
            Instant preparedAt) {
        return new ScanAiResult(
                scanSessionId, prepareId, schemaVersion, preparedItemsHash, preparedItems, prepareSignals, preparedAt);
    }

    /** Judge 메타 4개와 원본을 함께 기록. */
    public void applyJudge(
            String knowledgeRevision,
            String bundleHash,
            String judgeEvidence,
            String judgeWarnings,
            String judgeRaw,
            Instant judgedAt) {
        this.knowledgeRevision = knowledgeRevision;
        this.bundleHash = bundleHash;
        this.judgeEvidence = judgeEvidence;
        this.judgeWarnings = judgeWarnings;
        this.judgeRaw = judgeRaw;
        this.judgedAt = judgedAt;
    }
}
