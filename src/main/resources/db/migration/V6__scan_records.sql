-- 사용자가 명시적으로 보관한 스캔 기록.
-- scan_sessions.store_id 는 분석 시작 시점의 불변 컨텍스트이므로 사후 가게 연결로 수정하지 않는다.
-- 가게 없이 시작한 스캔에 사용자가 나중에 연결한 가게만 attached_store_* 에 저장한다.
CREATE TABLE scan_records (
    scan_session_id             UUID         NOT NULL,
    attached_store_id           BIGINT       NULL,
    attached_store_name_snapshot VARCHAR(200) NULL,
    attached_store_match_method VARCHAR(20)  NULL,
    feedback                    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    saved_at                    TIMESTAMPTZ  NOT NULL,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_scan_records PRIMARY KEY (scan_session_id),
    CONSTRAINT fk_scan_records_session FOREIGN KEY (scan_session_id)
        REFERENCES scan_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_scan_records_attached_store FOREIGN KEY (attached_store_id)
        REFERENCES stores (id) ON DELETE RESTRICT,
    CONSTRAINT ck_scan_records_match_method CHECK (
        attached_store_match_method IS NULL
        OR attached_store_match_method IN ('gps_candidate', 'name_search', 'kakao_fallback')
    ),
    CONSTRAINT ck_scan_records_store_context CHECK (
        (attached_store_id IS NULL
            AND attached_store_name_snapshot IS NULL
            AND attached_store_match_method IS NULL)
        OR
        (attached_store_id IS NOT NULL
            AND attached_store_name_snapshot IS NOT NULL
            AND btrim(attached_store_name_snapshot) <> ''
            AND attached_store_match_method IS NOT NULL)
    ),
    CONSTRAINT ck_scan_records_feedback_object CHECK (jsonb_typeof(feedback) = 'object')
);

CREATE INDEX idx_scan_records_attached_store
    ON scan_records (attached_store_id) WHERE attached_store_id IS NOT NULL;

COMMENT ON TABLE scan_records IS
    '사용자가 Keep this scan으로 보관한 1:1 기록. 분석 컨텍스트와 사후 가게 연결을 분리한다.';
COMMENT ON COLUMN scan_records.attached_store_id IS
    '가게 없이 시작한 스캔의 사후 연결만 저장. scan_sessions.store_id가 있으면 반드시 NULL.';
COMMENT ON COLUMN scan_records.attached_store_name_snapshot IS
    '사후 연결 시 서버가 stores.name에서 복사한 표시용 스냅샷.';
COMMENT ON COLUMN scan_records.feedback IS
    '프로필 항목 ID별 식단 의사소통 경험(yes/no/unknown). 가게가 있는 기록에서만 허용.';
