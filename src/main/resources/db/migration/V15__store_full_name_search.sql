-- V15 가게 이름+지점명 검색. "본죽 용산점"처럼 지점명까지 친 검색어의 포함·접두 매칭
-- 생성 컬럼 대신 식 인덱스: 40만 행 테이블 재작성 없음

CREATE INDEX idx_stores_full_name_trgm ON stores
    USING gin (normalize_store_name(name || branch_name) gin_trgm_ops)
    WHERE status = 'active';

COMMENT ON INDEX idx_stores_full_name_trgm IS '이름+지점명 검색 | 포함·접두 매칭; 유사도 매칭은 idx_stores_name_trgm';
