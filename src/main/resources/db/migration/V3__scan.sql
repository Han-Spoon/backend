-- Scan schema (V3)
-- Covers: scan_sessions, menu_images, menu_analyses
-- AI OCR/RuleEngine 응답 영속화. scan_quality 상세는 일회성 판단값이라 미저장(성공/실패/재촬영은 scan_sessions.scan_status).

CREATE TABLE scan_sessions (
    id                UUID         NOT NULL,
    user_id           UUID         NOT NULL,
    title             VARCHAR(255) NULL,       -- 원본 이미지 파일명
    menu_count        INTEGER      NULL,
    risky_menu_count  INTEGER      NULL,        -- 룰엔진이 채움(danger/caution 수)
    scan_status       VARCHAR(20)  NOT NULL,    -- processing/completed/failed/needs_retake
    scanned_at        TIMESTAMPTZ  NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_scan_sessions PRIMARY KEY (id),
    CONSTRAINT fk_scan_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_scan_sessions_user ON scan_sessions (user_id);

CREATE TABLE menu_images (
    id                  UUID             NOT NULL,
    scan_session_id     UUID             NOT NULL,
    source              VARCHAR(20)      NULL,   -- camera/upload
    storage_key         VARCHAR(512)     NULL,
    image_url           VARCHAR(1024)    NULL,
    mime_type           VARCHAR(100)     NULL,
    file_size           BIGINT           NULL,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT pk_menu_images PRIMARY KEY (id),
    CONSTRAINT uq_menu_images_session UNIQUE (scan_session_id),
    CONSTRAINT fk_menu_images_session FOREIGN KEY (scan_session_id)
        REFERENCES scan_sessions (id) ON DELETE CASCADE
);

CREATE TABLE menu_analyses (
    id                UUID          NOT NULL,
    scan_session_id   UUID          NOT NULL,
    display_order     INTEGER       NULL,
    -- OCR
    menu_name_ko      VARCHAR(255)  NULL,
    menu_name_en      VARCHAR(255)  NULL,
    description_ko    TEXT          NULL,
    description_en    TEXT          NULL,
    price_text        VARCHAR(100)  NULL,
    is_spicy          BOOLEAN       NULL,
    image_url         VARCHAR(1024) NULL,
    -- RuleEngine (scalar)
    risk_level        VARCHAR(20)   NULL,   -- danger/caution/safe
    need_gpt          BOOLEAN       NULL,
    -- RuleEngine (jsonb)
    hit_tags          JSONB         NULL,   -- string[]
    triggered_flags   JSONB         NULL,   -- string[]
    forbidden_tags    JSONB         NULL,   -- string[]
    escalation_case   JSONB         NULL,   -- string[] (unknown_remain/unknown_menu/ambiguity)
    gpt_context       JSONB         NULL,   -- {base_menu, ingredients_explicit, ...}
    risk_reasons      JSONB         NULL,   -- [{reason_type, reason_ko}]
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_menu_analyses PRIMARY KEY (id),
    CONSTRAINT fk_menu_analyses_session FOREIGN KEY (scan_session_id)
        REFERENCES scan_sessions (id) ON DELETE CASCADE
);

CREATE INDEX idx_menu_analyses_session ON menu_analyses (scan_session_id);
