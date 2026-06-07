-- 초기 스키마 (통합 베이스라인) — 첫 배포 전 V1~V7 스쿼시.
-- Covers: auth/profile, user_allergies, scan(sessions/images/analyses), saved_cards.
-- UUID는 애플리케이션에서 생성(UUID.randomUUID()). 코드형 컬럼(scan_status/card_type/risk_level 등)은 enum 소문자 code 저장.

-- ─────────────────────────────────────────────────────────────
-- Auth & Profile
-- ─────────────────────────────────────────────────────────────
CREATE TABLE users (
    id              UUID         NOT NULL,
    email           VARCHAR(255) NOT NULL,
    nickname        VARCHAR(100) NOT NULL,
    language_code   VARCHAR(2)   NOT NULL DEFAULT 'en', -- en/ko/ar (MVP), ISO 639-1
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE user_auth_identities (
    id              UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    provider        VARCHAR(10)  NOT NULL DEFAULT 'google',
    provider_id     VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_auth_identities PRIMARY KEY (id),
    CONSTRAINT uq_user_auth_identities_provider UNIQUE (provider, provider_id),
    CONSTRAINT fk_user_auth_identities_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_user_auth_identities_user ON user_auth_identities (user_id);

CREATE TABLE user_sessions (
    id                  UUID         NOT NULL,
    user_id             UUID         NOT NULL,
    refresh_token_hash  VARCHAR(255) NOT NULL, -- SHA-256 해시
    expires_at          TIMESTAMPTZ  NOT NULL,
    revoked_at          TIMESTAMPTZ  NULL,      -- 로그아웃/강제폐기 시점
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_sessions PRIMARY KEY (id),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uq_user_sessions_refresh_token_hash ON user_sessions (refresh_token_hash);
CREATE INDEX idx_user_sessions_user ON user_sessions (user_id);

CREATE TABLE user_profiles (
    id                          UUID        NOT NULL,
    user_id                     UUID        NOT NULL,
    nationality                 VARCHAR(2)  NULL,        -- ISO 3166-1 alpha-2 (예: SA/US/KR)
    is_first_time_korean_food   BOOLEAN     NULL,
    is_vegetarian               BOOLEAN     NULL,
    vegetarian_type             VARCHAR(50) NULL,        -- vegan/lacto/ovo/lacto_ovo/pesco
    religion_type               VARCHAR(50) NULL,        -- halal/kosher/hindu/none
    no_spicy                    BOOLEAN     NULL,
    no_alcohol                  BOOLEAN     NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_profiles PRIMARY KEY (id),
    CONSTRAINT uq_user_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE user_allergies (
    id               UUID        NOT NULL,
    user_profile_id  UUID        NOT NULL,
    allergy_code     VARCHAR(50) NOT NULL,   -- AllergyCode enum 소문자 code (식약처 19종)
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_allergies PRIMARY KEY (id),
    CONSTRAINT uq_user_allergies UNIQUE (user_profile_id, allergy_code),
    CONSTRAINT fk_user_allergies_profile FOREIGN KEY (user_profile_id)
        REFERENCES user_profiles (id) ON DELETE CASCADE
);
CREATE INDEX idx_user_allergies_profile ON user_allergies (user_profile_id);

-- ─────────────────────────────────────────────────────────────
-- Scan (sessions / images / analyses)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE scan_sessions (
    id                UUID         NOT NULL,
    user_id           UUID         NOT NULL,
    title             VARCHAR(255) NULL,       -- 유저 편집 제목(미편집 시 null)
    menu_count        INTEGER      NULL,
    risky_menu_count  INTEGER      NULL,        -- 룰엔진이 채움(danger/caution 수)
    scan_status       VARCHAR(20)  NOT NULL,    -- processing/completed/failed/needs_retake
    scanned_at        TIMESTAMPTZ  NULL,
    retake_reasons    JSONB        NULL,        -- needs_retake 시 OCR 제공 사유(string[])
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

-- OCR(menu_name/price/...) + ai_result FinalOutput(risk_level/hit_tags/message/owner_card) 머지 저장.
CREATE TABLE menu_analyses (
    id                UUID          NOT NULL,
    scan_session_id   UUID          NOT NULL,
    display_order     INTEGER       NULL,
    menu_name_ko      VARCHAR(255)  NULL,
    menu_name_en      VARCHAR(255)  NULL,
    description_ko    TEXT          NULL,
    description_en    TEXT          NULL,
    price_text        VARCHAR(100)  NULL,
    is_spicy          BOOLEAN       NULL,
    image_url         VARCHAR(1024) NULL,
    risk_level        VARCHAR(20)   NULL,   -- danger/caution/safe
    hit_tags          JSONB         NULL,   -- string[]
    message           JSONB         NULL,   -- {ko, en, ar} 사용자 안내문
    owner_card        JSONB         NULL,   -- {menu_name, flag, question:{ko,en,ar}}
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_menu_analyses PRIMARY KEY (id),
    CONSTRAINT fk_menu_analyses_session FOREIGN KEY (scan_session_id)
        REFERENCES scan_sessions (id) ON DELETE CASCADE
);
CREATE INDEX idx_menu_analyses_session ON menu_analyses (scan_session_id);

-- ─────────────────────────────────────────────────────────────
-- Saved cards (자주 쓰는 카드) — 텍스트 스냅샷. 문구 생성은 FE, 서버는 저장만.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE saved_cards (
    id              UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    card_type       VARCHAR(20)  NOT NULL,   -- order | ingredient_check | exclude
    menu_name_ko    VARCHAR(255) NOT NULL,
    text            JSONB        NOT NULL,   -- {ko, en, ar} 화면 표시 문구 스냅샷
    scan_session_id UUID         NULL,       -- 출처 스캔(선택)
    last_saved_at   TIMESTAMPTZ  NOT NULL,   -- 중복 재저장 시 갱신 → 목록 정렬(최신 저장순)
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_saved_cards PRIMARY KEY (id),
    CONSTRAINT fk_saved_cards_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_cards_scan FOREIGN KEY (scan_session_id)
        REFERENCES scan_sessions (id) ON DELETE SET NULL
);
CREATE INDEX idx_saved_cards_user ON saved_cards (user_id, last_saved_at DESC);
