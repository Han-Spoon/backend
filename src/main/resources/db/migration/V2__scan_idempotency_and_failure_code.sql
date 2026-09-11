ALTER TABLE scan_sessions
    ADD COLUMN storage_key VARCHAR(512) NULL,
    ADD COLUMN failure_code VARCHAR(64) NULL,
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;

-- Presigned PUT으로 생성한 불변 S3 객체는 한 번만 분석.
-- 기존 데이터는 storage_key가 null이므로 마이그레이션 시 충돌하지 않음.
CREATE UNIQUE INDEX uq_scan_sessions_user_storage_key
    ON scan_sessions (user_id, storage_key)
    WHERE storage_key IS NOT NULL;
