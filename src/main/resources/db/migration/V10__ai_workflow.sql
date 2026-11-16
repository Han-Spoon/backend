-- V10 운영 워크플로우: 관리자 역할 · 웹 원본 · 검토 · 웹 등록 · 명령 수신함 · 감사 로그
-- ─────────────────────────────────────────────────────────────
-- C. 운영 워크플로우 (관리자 · 웹 원본 · 검토 · 명령 수신함 · 신뢰도 · 감사)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE user_roles (
    user_id     UUID         NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_roles_role CHECK (role IN ('admin'))
);

-- 이름은 "캐시"가 아니라 원본 누적 로그. AI 계약 이름(web_search_cache slice, save_web_search_cache)은 그대로 둔다.
CREATE TABLE web_search_sources (
    id                    BIGINT        GENERATED ALWAYS AS IDENTITY,
    normalized_menu_name  VARCHAR(100)  NOT NULL,
    original_menu_name    VARCHAR(255)  NULL,
    search_status         VARCHAR(20)   NOT NULL,
    payload               JSONB         NOT NULL DEFAULT '[]'::jsonb,
    searched_at          TIMESTAMPTZ   NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_web_search_sources PRIMARY KEY (id),
    CONSTRAINT uq_web_search_sources_name UNIQUE (normalized_menu_name),
    CONSTRAINT ck_web_search_sources_name CHECK (
        normalized_menu_name <> '' AND normalized_menu_name = normalize_food_name(normalized_menu_name)),
    CONSTRAINT ck_web_search_sources_status CHECK (search_status IN ('searched', 'unresolved')),
    -- 검색 순위(next_rank)는 저장하지 않는다: ⑥이 매번 1위부터 읽고 payload 에 있는 URL 만 건너뛴다 (AI 10-27 회의, #14)
    CONSTRAINT ck_web_search_sources_payload CHECK (jsonb_typeof(payload) = 'array')
);

CREATE TABLE knowledge_review_items (
    id                        BIGINT        GENERATED ALWAYS AS IDENTITY,
    store_id                  BIGINT        NOT NULL,
    item_type                 VARCHAR(30)   NOT NULL,
    status                    VARCHAR(20)   NOT NULL DEFAULT 'pending',
    proposed_name_ko          VARCHAR(100)  NOT NULL,
    proposed_name_normalized  VARCHAR(100)  GENERATED ALWAYS AS (normalize_food_name(proposed_name_ko)) STORED,
    base_menu_id              BIGINT        NULL,
    remain_token              VARCHAR(30)   NULL,
    payload                   JSONB         NOT NULL,
    review_notes              JSONB         NOT NULL DEFAULT '[]'::jsonb,
    web_search_source_id      BIGINT        NULL,
    source_scan_session_id    UUID          NULL,
    resolved_menu_id          BIGINT        NULL,
    reviewed_by               UUID          NULL,
    reviewed_at               TIMESTAMPTZ   NULL,
    review_note               VARCHAR(500)  NULL,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_knowledge_review_items PRIMARY KEY (id),
    CONSTRAINT fk_knowledge_review_items_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE RESTRICT,
    CONSTRAINT fk_knowledge_review_items_base_menu FOREIGN KEY (base_menu_id) REFERENCES menus (id) ON DELETE RESTRICT,
    CONSTRAINT fk_knowledge_review_items_web_source FOREIGN KEY (web_search_source_id)
        REFERENCES web_search_sources (id) ON DELETE SET NULL,
    CONSTRAINT fk_knowledge_review_items_scan FOREIGN KEY (source_scan_session_id)
        REFERENCES scan_sessions (id) ON DELETE SET NULL,
    CONSTRAINT fk_knowledge_review_items_resolved_menu FOREIGN KEY (resolved_menu_id) REFERENCES menus (id) ON DELETE RESTRICT,
    CONSTRAINT fk_knowledge_review_items_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_knowledge_review_items_type CHECK (item_type IN ('web_search_menu', 'runtime_variant')),
    CONSTRAINT ck_knowledge_review_items_status CHECK (status IN ('pending', 'auto_approved', 'approved', 'rejected')),
    CONSTRAINT ck_knowledge_review_items_name CHECK (btrim(proposed_name_ko) <> '' AND proposed_name_ko = btrim(proposed_name_ko)),
    CONSTRAINT ck_knowledge_review_items_variant CHECK (
        (item_type = 'runtime_variant') = (base_menu_id IS NOT NULL)
        AND (base_menu_id IS NULL) = (remain_token IS NULL)),
    CONSTRAINT ck_knowledge_review_items_web_source_type CHECK (item_type = 'web_search_menu' OR web_search_source_id IS NULL),
    CONSTRAINT ck_knowledge_review_items_payload CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_knowledge_review_items_review_notes CHECK (jsonb_typeof(review_notes) = 'array'),
    -- 웹 레시피 묶음(검색 1회의 새 레시피)은 이상 신호(⑥ review_notes)가 없으면 자동 승인, 있으면 관리자 대기.
    -- 관리자는 이상 신호를 보고 묶음을 예(approved)/아니오(rejected)로만 고른다 (2026-10-26 회의). 변형은 항상 관리자 검토.
    CONSTRAINT ck_knowledge_review_items_routing CHECK (CASE
        WHEN item_type = 'web_search_menu' THEN (status = 'auto_approved') = (review_notes = '[]'::jsonb)
        ELSE status <> 'auto_approved' END),
    -- resolved_menu_id: 변형은 승인 시 새 변형 메뉴(필수), 웹 묶음은 승인 레시피 30개로 등록된 메뉴(등록 전 NULL)
    CONSTRAINT ck_knowledge_review_items_resolution CHECK (
        (status = 'pending' AND reviewed_at IS NULL AND reviewed_by IS NULL AND resolved_menu_id IS NULL)
        OR (status = 'auto_approved' AND reviewed_at IS NOT NULL AND reviewed_by IS NULL)
        OR (status = 'approved' AND reviewed_at IS NOT NULL
            AND (item_type = 'web_search_menu' OR resolved_menu_id IS NOT NULL))
        OR (status = 'rejected' AND reviewed_at IS NOT NULL AND resolved_menu_id IS NULL))
);
-- 같은 이름의 변형 대기 항목은 하나만. AI 명령은 가게마다 오므로 백엔드가 기존 대기 항목에 근거를 합친다.
-- 웹 묶음은 이상 신호가 묶음마다 계산되므로(⑥ §2-6) 합치지 않고 검색 1회 = 항목 1개로 둔다.
CREATE UNIQUE INDEX uq_knowledge_review_items_pending
    ON knowledge_review_items (item_type, proposed_name_normalized) WHERE status = 'pending' AND item_type = 'runtime_variant';
CREATE INDEX idx_knowledge_review_items_name ON knowledge_review_items (item_type, proposed_name_normalized, status);
CREATE INDEX idx_knowledge_review_items_pending_recent
    ON knowledge_review_items (created_at DESC, id DESC) WHERE status = 'pending';
CREATE INDEX idx_knowledge_review_items_scan
    ON knowledge_review_items (source_scan_session_id) WHERE source_scan_session_id IS NOT NULL;
CREATE INDEX idx_knowledge_review_items_web_source
    ON knowledge_review_items (web_search_source_id) WHERE web_search_source_id IS NOT NULL;
-- 사용자 탈퇴 시 SET NULL 탐색 (검토 이력은 계속 쌓이는 테이블)
CREATE INDEX idx_knowledge_review_items_reviewer ON knowledge_review_items (reviewed_by) WHERE reviewed_by IS NOT NULL;

-- 웹 검토 묶음의 레시피 목록. 승인·반려는 묶음(knowledge_review_items.status) 단위다 (2026-10-26 회의).
-- 30개 판정 = 같은 메뉴명의 승인 묶음(auto_approved·approved, 아직 등록 전) 레시피 URL 중복 제거 개수
CREATE TABLE knowledge_review_sources (
    id                     BIGINT         GENERATED ALWAYS AS IDENTITY,
    review_item_id         BIGINT         NOT NULL,
    source_url             VARCHAR(1024)  NOT NULL,
    title                  VARCHAR(500)   NULL,
    fetched_at             TIMESTAMPTZ    NULL,
    extracted_ingredients  JSONB          NOT NULL,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_knowledge_review_sources PRIMARY KEY (id),
    CONSTRAINT uq_knowledge_review_sources_url UNIQUE (review_item_id, source_url),
    CONSTRAINT fk_knowledge_review_sources_item FOREIGN KEY (review_item_id)
        REFERENCES knowledge_review_items (id) ON DELETE CASCADE,
    CONSTRAINT ck_knowledge_review_sources_url CHECK (source_url ~ '^https?://'),
    CONSTRAINT ck_knowledge_review_sources_ingredients CHECK (jsonb_typeof(extracted_ingredients) = 'array')
);
-- 30개 판정: 같은 이름의 등록 전 승인 웹 묶음
CREATE INDEX idx_knowledge_review_items_web_unregistered
    ON knowledge_review_items (proposed_name_normalized)
    WHERE item_type = 'web_search_menu' AND status IN ('auto_approved', 'approved') AND resolved_menu_id IS NULL;
-- 웹 묶음 → 등록 메뉴 역참조 (메뉴 삭제 RESTRICT 검사)
CREATE INDEX idx_knowledge_review_items_resolved_menu
    ON knowledge_review_items (resolved_menu_id) WHERE resolved_menu_id IS NOT NULL;

-- 영수증(멱등) + 실패 명령 재처리를 한 테이블로 (inbox 패턴)
CREATE TABLE ai_command_inbox (
    action           VARCHAR(40)    NOT NULL,
    idempotency_key  VARCHAR(128)   NOT NULL,
    store_id         BIGINT         NULL,
    scan_session_id  UUID           NULL,
    request_hash     CHAR(64)       NOT NULL,
    payload          JSONB          NOT NULL,
    status           VARCHAR(20)    NOT NULL DEFAULT 'received',
    attempt_count    INTEGER        NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ    NULL,
    last_error       VARCHAR(1000)  NULL,
    result           JSONB          NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_ai_command_inbox PRIMARY KEY (action, idempotency_key),
    -- store_id·scan_session_id 는 FK 없이 값만 보관한다. 명령을 실제 테이블에 반영할 때 그 테이블의 FK 가 검증하고,
    -- 수신함은 재처리용 기술 로그라 스캔·가게 삭제가 이 테이블(대용량)로 연쇄되지 않게 한다.
    CONSTRAINT ck_ai_command_inbox_action CHECK (action IN (
        'create_review_item', 'create_owner_verification_request', 'record_owner_answer',
        'update_source_reliability', 'save_web_search_cache', 'save_menu_ingredient_cache')),
    CONSTRAINT ck_ai_command_inbox_key CHECK (idempotency_key ~ '^[A-Za-z0-9:_-]{16,128}$'),
    CONSTRAINT ck_ai_command_inbox_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    -- 전역 명령 3종만 가게가 없다
    CONSTRAINT ck_ai_command_inbox_store_scope CHECK (
        (action IN ('update_source_reliability', 'save_web_search_cache', 'save_menu_ingredient_cache')) = (store_id IS NULL)),
    CONSTRAINT ck_ai_command_inbox_payload CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_ai_command_inbox_status CHECK (status IN ('received', 'applied', 'failed', 'dead')),
    CONSTRAINT ck_ai_command_inbox_result CHECK (status <> 'applied' OR result IS NOT NULL),
    CONSTRAINT ck_ai_command_inbox_attempts CHECK (attempt_count >= 0)
);
CREATE INDEX idx_ai_command_inbox_retry ON ai_command_inbox (next_attempt_at) WHERE status IN ('received', 'failed');
CREATE INDEX idx_ai_command_inbox_purge ON ai_command_inbox (created_at) WHERE status = 'applied';

CREATE TABLE admin_audit_logs (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY,
    admin_user_id  UUID         NULL,
    action         VARCHAR(60)  NOT NULL,
    target_type    VARCHAR(40)  NOT NULL,
    target_id      VARCHAR(64)  NOT NULL,
    detail         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    ip_address     INET         NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_admin_audit_logs PRIMARY KEY (id),
    -- FK 를 두지 않는다: 관리자 계정이 삭제돼도 감사 로그 행이 SET NULL 로 바뀌면 안 된다(감사 기록 불변)
    CONSTRAINT ck_admin_audit_logs_action CHECK (action ~ '^[a-z_]+\.[a-z_]+$'),
    CONSTRAINT ck_admin_audit_logs_detail CHECK (jsonb_typeof(detail) = 'object')
);
CREATE INDEX idx_admin_audit_logs_target ON admin_audit_logs (target_type, target_id, created_at DESC);
CREATE INDEX idx_admin_audit_logs_admin ON admin_audit_logs (admin_user_id, created_at DESC);

