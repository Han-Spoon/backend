-- ai_result 통합 후 정리: 룰 중간 컬럼 제거(표시 출처는 FinalOutput) + 재촬영 사유 추가
ALTER TABLE menu_analyses
    DROP COLUMN need_gpt,
    DROP COLUMN triggered_flags,
    DROP COLUMN forbidden_tags,
    DROP COLUMN escalation_case,
    DROP COLUMN gpt_context,
    DROP COLUMN risk_reasons;

ALTER TABLE scan_sessions
    ADD COLUMN retake_reasons JSONB NULL;
