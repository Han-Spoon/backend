-- Auth & profile schema (V1)
-- Covers: users, user_auth_identities, user_sessions, user_profiles
-- UUID는 애플리케이션에서 생성(UUID.randomUUID())
-- 나머지 ERD(메뉴/스캔/pgvector 등)는 후속 마이그레이션(V2+)에서 추가.

CREATE TABLE users (
    id              UUID         NOT NULL,
    email           VARCHAR(255) NOT NULL,
    nickname        VARCHAR(100) NOT NULL,
    language_code   CHAR(2)      NOT NULL DEFAULT 'en', -- en/ko/ar (MVP), ISO 639-1
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
    nationality                 CHAR(2)     NULL,        -- ISO 3166-1 alpha-2 (예: SA/US/KR)
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
