package com.hanspoon.backend_api.domain.scan.repository;

import com.hanspoon.backend_api.domain.scan.entity.ScanAiResult;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScanAiResultRepository extends JpaRepository<ScanAiResult, UUID> {

    /** 보관 기간이 지난 Judge 원본 비움. 근거·경고 컬럼은 유지. 부분 인덱스 idx_scan_ai_results_judge_raw_purge 사용. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE scan_ai_results
                    SET judge_raw = NULL, updated_at = :now
                    WHERE judge_raw IS NOT NULL AND judged_at < :cutoff
                    """,
            nativeQuery = true)
    int clearJudgeRawBefore(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
