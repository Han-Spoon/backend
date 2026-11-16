-- V12 스캔 AI 원본 · 스캔 메뉴 연결(확장) · 메뉴 찜 · 이미지 · 큐레이션 · 약관 · 번역 캐시 · 식사 후기·신고 · 동네
-- ─────────────────────────────────────────────────────────────
-- E. 가게 메뉴 · 스캔 연결
-- ─────────────────────────────────────────────────────────────

-- Prepare 원본(Judge 재전송·해시 검증)과 Judge 메타(재현 정보). 스캔 1:1.
-- 이력 목록 테이블(scan_sessions)에서 큰 JSON 과 판정 메타를 떼어 낸다.
CREATE TABLE scan_ai_results (
    scan_session_id      UUID         NOT NULL,
    prepare_id           UUID         NOT NULL,
    schema_version       VARCHAR(10)  NOT NULL,
    prepared_items_hash  VARCHAR(71)  NOT NULL,
    prepared_items       JSONB        NOT NULL,
    prepare_signals      JSONB        NOT NULL,
    prepared_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    knowledge_revision   VARCHAR(64)  NULL,
    bundle_hash          VARCHAR(71)  NULL,
    judge_evidence       JSONB        NULL,
    judge_warnings       JSONB        NULL,
    judge_raw            JSONB        NULL,
    judged_at            TIMESTAMPTZ  NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_scan_ai_results PRIMARY KEY (scan_session_id),
    CONSTRAINT uq_scan_ai_results_prepare UNIQUE (prepare_id),
    CONSTRAINT fk_scan_ai_results_session FOREIGN KEY (scan_session_id) REFERENCES scan_sessions (id) ON DELETE CASCADE,
    CONSTRAINT ck_scan_ai_results_prepared_hash CHECK (prepared_items_hash ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT ck_scan_ai_results_bundle_hash CHECK (bundle_hash IS NULL OR bundle_hash ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT ck_scan_ai_results_items CHECK (jsonb_typeof(prepared_items) = 'array'),
    CONSTRAINT ck_scan_ai_results_signals CHECK (jsonb_typeof(prepare_signals) = 'object'),
    CONSTRAINT ck_scan_ai_results_judge CHECK (
        (judged_at IS NULL) = (bundle_hash IS NULL)
        AND (judged_at IS NULL) = (knowledge_revision IS NULL)
        AND (judged_at IS NULL) = (judge_evidence IS NULL)
        AND (judged_at IS NULL) = (judge_warnings IS NULL)),
    CONSTRAINT ck_scan_ai_results_judge_json CHECK (
        judged_at IS NULL OR (jsonb_typeof(judge_evidence) = 'array' AND jsonb_typeof(judge_warnings) = 'array')),
    -- Judge 응답 원본은 판정 후에만, 객체로. 90일 뒤 NULL 로 비운다(아래 인덱스). 근거·경고는 장기 보관 컬럼에 따로 있다
    CONSTRAINT ck_scan_ai_results_judge_raw CHECK (
        judge_raw IS NULL OR (judged_at IS NOT NULL AND jsonb_typeof(judge_raw) = 'object'))
);
-- Judge 원본 90일 보존 정리: UPDATE scan_ai_results SET judge_raw = NULL, updated_at = now()
--                           WHERE judge_raw IS NOT NULL AND judged_at < now() - interval '90 days'
CREATE INDEX idx_scan_ai_results_judge_raw_purge ON scan_ai_results (judged_at) WHERE judge_raw IS NOT NULL;

-- menu_analyses (확장): 새 컬럼과 그 제약만 추가한다. 기존 컬럼의 이름 변경·삭제, 단수 카드 → 배열 전환,
-- 기존 값에 걸리는 CHECK 는 현재 파이프라인과 충돌하므로 V14(Prepare/Judge 연동 배포)에서 적용한다.
ALTER TABLE menu_analyses
    ADD COLUMN source_index          INTEGER      NULL,
    ADD COLUMN price_options         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN normalization_status  VARCHAR(20)  NULL,
    ADD COLUMN status                VARCHAR(20)  NULL,
    ADD COLUMN evidence_basis        VARCHAR(20)  NULL,
    ADD COLUMN information_status    VARCHAR(20)  NULL,
    ADD COLUMN risk_ingredients      JSONB        NULL,
    ADD COLUMN recommendation        JSONB        NULL,
    ADD COLUMN store_id              BIGINT       NULL,
    ADD COLUMN store_menu_id         BIGINT       NULL,
    ADD COLUMN menu_id               BIGINT       NULL,
    ADD COLUMN menu_match_type       VARCHAR(20)  NULL,
    ADD COLUMN staff_answer_status   VARCHAR(10)  NOT NULL DEFAULT 'none',
    ADD COLUMN ai_risk_level         VARCHAR(10)  NULL;

ALTER TABLE menu_analyses
    ADD CONSTRAINT uq_menu_analyses_item UNIQUE (scan_session_id, source_index),
    -- 식사 후기 줄이 같은 스캔의 메뉴만 가리키도록 하는 복합 FK 대상
    ADD CONSTRAINT uq_menu_analyses_session_link UNIQUE (id, scan_session_id),
    -- 가게 메뉴는 스캔의 가게와 같아야 한다: (store_id, scan) 복합 FK + (store_menu_id, store_id) 복합 FK
    ADD CONSTRAINT fk_menu_analyses_scan_store FOREIGN KEY (scan_session_id, store_id)
        REFERENCES scan_sessions (id, store_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_menu_analyses_store_menu FOREIGN KEY (store_menu_id, store_id)
        REFERENCES store_menus (id, store_id) ON DELETE SET NULL (store_menu_id),
    ADD CONSTRAINT fk_menu_analyses_menu FOREIGN KEY (menu_id) REFERENCES menus (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_menu_analyses_store_menu_scope CHECK (store_menu_id IS NULL OR store_id IS NOT NULL),
    ADD CONSTRAINT ck_menu_analyses_source_index CHECK (source_index IS NULL OR source_index >= 0),
    ADD CONSTRAINT ck_menu_analyses_price_options CHECK (jsonb_typeof(price_options) = 'array'),
    ADD CONSTRAINT ck_menu_analyses_risk_ingredients CHECK (risk_ingredients IS NULL OR jsonb_typeof(risk_ingredients) = 'array'),
    ADD CONSTRAINT ck_menu_analyses_recommendation CHECK (recommendation IS NULL OR jsonb_typeof(recommendation) = 'object'),
    ADD CONSTRAINT ck_menu_analyses_ai_risk_level CHECK (ai_risk_level IS NULL OR ai_risk_level IN ('danger', 'caution', 'safe')),
    ADD CONSTRAINT ck_menu_analyses_normalization CHECK (
        normalization_status IS NULL OR normalization_status IN ('normalized', 'ambiguous', 'unmatched', 'invalid')),
    ADD CONSTRAINT ck_menu_analyses_status CHECK (status IS NULL OR status IN ('completed', 'partial_failed', 'failed')),
    ADD CONSTRAINT ck_menu_analyses_evidence_basis CHECK (
        evidence_basis IS NULL OR evidence_basis IN ('confirmed', 'estimated', 'unknown')),
    ADD CONSTRAINT ck_menu_analyses_information_status CHECK (
        information_status IS NULL OR information_status IN ('complete', 'partial', 'none')),
    -- #24: 직원 응답 상태(Judge items[].owner_answer_status). 가게 없는 스캔은 직원 질문이 없어 항상 none
    ADD CONSTRAINT ck_menu_analyses_staff_answer_status CHECK (staff_answer_status IN ('none', 'partial', 'complete')),
    ADD CONSTRAINT ck_menu_analyses_staff_answer_scope CHECK (store_id IS NOT NULL OR staff_answer_status = 'none'),
    -- FN-safe: 정보가 완전하고 직원 응답도 complete 여야 SAFE (⑧ §1, ⓪ owner_answer_status).
    -- information_status IS NULL 은 Judge 이전 파이프라인(룰엔진) 행이라 통과시킨다. Judge 행은 아래 ck_menu_analyses_judged_information 이 NULL 을 막는다.
    ADD CONSTRAINT ck_menu_analyses_safe_requires_complete CHECK (
        risk_level IS DISTINCT FROM 'safe' OR information_status IS NULL
        OR (information_status = 'complete' AND staff_answer_status = 'complete')),
    -- Judge 가 판정한 행(status 있음)은 information_status 가 반드시 있다 → 위 SAFE 조건이 NULL 로 새지 않는다.
    -- 룰엔진 행은 status 가 NULL 이라 그대로 통과한다.
    ADD CONSTRAINT ck_menu_analyses_judged_information CHECK (status IS NULL OR information_status IS NOT NULL),
    ADD CONSTRAINT ck_menu_analyses_match_type CHECK (
        menu_match_type IS NULL OR menu_match_type IN ('exact', 'alias', 'fuzzy', 'runtime_variant', 'unmatched')),
    -- CASE 로 써서 NULL 이 "통과"로 새지 않게 한다. runtime_variant 의 menu_id 는 AI 응답 그대로(있을 수도 없을 수도)
    ADD CONSTRAINT ck_menu_analyses_match CHECK (CASE
        WHEN menu_match_type IS NULL THEN menu_id IS NULL
        WHEN menu_match_type IN ('exact', 'alias', 'fuzzy') THEN menu_id IS NOT NULL
        WHEN menu_match_type = 'unmatched' THEN menu_id IS NULL
        ELSE true END);

-- 기존 idx_menu_analyses_session(scan_session_id) 은 uq_menu_analyses_item 이 선두 컬럼으로 대신한다
DROP INDEX idx_menu_analyses_session;
CREATE INDEX idx_menu_analyses_store_menu ON menu_analyses (store_menu_id) WHERE store_menu_id IS NOT NULL;
CREATE INDEX idx_menu_analyses_menu ON menu_analyses (menu_id) WHERE menu_id IS NOT NULL;

-- 기존 FK(scan_session_id 단독)는 복합 FK 와 중복이지만, 가게 없는 스캔(store_id NULL)은 복합 FK 검사가 생략되므로 유지한다.


-- saved_cards: 언어를 바꿔 다시 볼 때 표준 메뉴 번역명을 쓰기 위한 참조
ALTER TABLE saved_cards
    ADD COLUMN store_menu_id BIGINT NULL,
    ADD COLUMN menu_id       BIGINT NULL,
    ADD CONSTRAINT fk_saved_cards_store_menu FOREIGN KEY (store_menu_id) REFERENCES store_menus (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_saved_cards_menu FOREIGN KEY (menu_id) REFERENCES menus (id) ON DELETE SET NULL;
CREATE INDEX idx_saved_cards_store_menu ON saved_cards (store_menu_id) WHERE store_menu_id IS NOT NULL;
CREATE INDEX idx_saved_cards_menu ON saved_cards (menu_id) WHERE menu_id IS NOT NULL;
CREATE INDEX idx_saved_cards_scan ON saved_cards (scan_session_id) WHERE scan_session_id IS NOT NULL;

-- 메뉴 찜 = 결과 화면 하트 (2026-11-03). 토글, 마이페이지 "저장한 메뉴"에서 메뉴명·판정·식당을 본다. 스캔 기록과 별개 화면.
-- 내 스캔의 메뉴만 찜할 수 있다: (scan_session_id, menu_analysis_id) → 그 스캔의 메뉴, (scan_session_id, user_id) → 내 스캔.
-- 스캔 기록을 지우면(메뉴 분석 CASCADE) 찜도 함께 지운다 (2026-11-09). 탈퇴는 scan_sessions CASCADE 로 함께 삭제.
CREATE TABLE favorite_menus (
    user_id           UUID         NOT NULL,
    menu_analysis_id  UUID         NOT NULL,
    scan_session_id   UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_favorite_menus PRIMARY KEY (user_id, menu_analysis_id),
    -- 컬럼 순서를 (scan_session_id, …)로 맞춰 아래 인덱스 하나가 두 FK 의 연쇄 탐색을 모두 맡는다
    CONSTRAINT fk_favorite_menus_menu FOREIGN KEY (scan_session_id, menu_analysis_id)
        REFERENCES menu_analyses (scan_session_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_menus_scan FOREIGN KEY (scan_session_id, user_id)
        REFERENCES scan_sessions (id, user_id) ON DELETE CASCADE
);
-- 저장한 메뉴 목록 (찜한 최신순 키셋)
CREATE INDEX idx_favorite_menus_user_recent ON favorite_menus (user_id, created_at DESC, menu_analysis_id DESC);
-- 스캔·메뉴 분석 삭제 연쇄 (두 FK 모두 scan_session_id 로 시작)
CREATE INDEX idx_favorite_menus_scan ON favorite_menus (scan_session_id, menu_analysis_id);

-- ─────────────────────────────────────────────────────────────
-- F. 이미지
-- ─────────────────────────────────────────────────────────────
CREATE TABLE image_assets (
    id                BIGINT         GENERATED ALWAYS AS IDENTITY,
    storage_key       VARCHAR(512)   NOT NULL,
    content_sha256    CHAR(64)       NOT NULL,
    mime_type         VARCHAR(100)   NOT NULL,
    width             INTEGER        NULL,
    height            INTEGER        NULL,
    file_size         BIGINT         NULL,
    source_type       VARCHAR(20)    NOT NULL,
    author            VARCHAR(200)   NULL,
    license_code      VARCHAR(50)    NULL,
    source_url        VARCHAR(1024)  NULL,
    attribution_text  VARCHAR(500)   NULL,
    status            VARCHAR(20)    NOT NULL DEFAULT 'active',
    created_by        UUID           NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_image_assets PRIMARY KEY (id),
    CONSTRAINT uq_image_assets_storage_key UNIQUE (storage_key),
    CONSTRAINT uq_image_assets_sha256 UNIQUE (content_sha256),
    CONSTRAINT fk_image_assets_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_image_assets_sha256 CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_image_assets_mime CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_image_assets_size CHECK ((width IS NULL OR width > 0) AND (height IS NULL OR height > 0)
        AND (file_size IS NULL OR file_size > 0)),
    CONSTRAINT ck_image_assets_source CHECK (source_type IN ('team_original', 'external_licensed', 'public_domain')),
    CONSTRAINT ck_image_assets_license CHECK (
        source_type <> 'external_licensed'
        OR (author IS NOT NULL AND license_code IS NOT NULL AND source_url IS NOT NULL)),
    CONSTRAINT ck_image_assets_status CHECK (status IN ('active', 'hidden'))
);

-- 대표 이미지는 부모마다 1개뿐이라 연결 테이블 대신 FK 컬럼으로 둔다 (여러 장이 필요해지면 연결 테이블 추가).
-- stores 는 적재 스크립트가 컬럼을 명시해 upsert 하므로 이 컬럼을 덮어쓰지 않는다(scripts/load_stores.py).
ALTER TABLE stores
    ADD COLUMN image_asset_id BIGINT NULL,
    ADD CONSTRAINT fk_stores_image FOREIGN KEY (image_asset_id) REFERENCES image_assets (id) ON DELETE RESTRICT;
ALTER TABLE store_menus
    ADD COLUMN image_asset_id BIGINT NULL,
    ADD CONSTRAINT fk_store_menus_image FOREIGN KEY (image_asset_id) REFERENCES image_assets (id) ON DELETE RESTRICT;
ALTER TABLE menus
    ADD COLUMN image_asset_id BIGINT NULL,
    ADD CONSTRAINT fk_menus_image FOREIGN KEY (image_asset_id) REFERENCES image_assets (id) ON DELETE RESTRICT;
-- 사용 중인 이미지 삭제 검사(RESTRICT)가 큰 테이블을 전체 스캔하지 않도록
CREATE INDEX idx_stores_image ON stores (image_asset_id) WHERE image_asset_id IS NOT NULL;
CREATE INDEX idx_store_menus_image ON store_menus (image_asset_id) WHERE image_asset_id IS NOT NULL;
CREATE INDEX idx_menus_image ON menus (image_asset_id) WHERE image_asset_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────
-- G. 큐레이션 (백엔드 전담 — AI Judge 계약에 큐레이션 필드 없음, AI #150 2026-10-18)
--    운영자 기능은 작성·게시·공개/비공개·삭제뿐이다 (2026-10-27 결정). 한국어 원문 하나를 쓰면
--    나머지 6개 언어 번역을 한 번에 만들어 저장하고, 언어별 검수는 하지 않는다.
--    태그·메뉴 연결은 추천 계산에만 쓰고 열람은 제한하지 않는다. 삭제는 행 삭제(번역·메뉴 연결 CASCADE)
--    읽는 시간은 저장하지 않고 언어별 본문 길이(char_length(body))로 계산한다 — 원문을 고치면 번역을 모두 다시 만들기 때문
-- ─────────────────────────────────────────────────────────────
CREATE TABLE curation_articles (
    id                    BIGINT        GENERATED ALWAYS AS IDENTITY,
    slug                  VARCHAR(100)  NOT NULL,
    category_code         VARCHAR(20)   NOT NULL,
    cover_image_asset_id  BIGINT        NULL,
    constraint_tags       VARCHAR(20)[] NOT NULL DEFAULT '{}',
    audience_tags         VARCHAR(30)[] NOT NULL DEFAULT '{}',
    source_title          VARCHAR(300)  NULL,
    source_publisher      VARCHAR(200)  NULL,
    source_url            VARCHAR(1024) NULL,
    source_license        VARCHAR(50)   NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'draft',
    published_at          TIMESTAMPTZ   NULL,
    issue_no              INTEGER       NULL,
    created_by            UUID          NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_curation_articles PRIMARY KEY (id),
    CONSTRAINT uq_curation_articles_slug UNIQUE (slug),
    -- 글 번호("HAN SPOON · 01"): 첫 게시 때 부여하고 숨김·삭제가 있어도 다른 글 번호를 바꾸지 않는다 (2026-11-10)
    CONSTRAINT uq_curation_articles_issue UNIQUE (issue_no),
    CONSTRAINT fk_curation_articles_cover FOREIGN KEY (cover_image_asset_id) REFERENCES image_assets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_curation_articles_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_curation_articles_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    -- 분류 4종은 고정 코드. 화면 이름(7개 언어)은 앱 다국어 리소스 (기존 curation_categories 테이블 대체)
    CONSTRAINT ck_curation_articles_category CHECK (category_code IN ('culture', 'restaurant', 'food', 'tips')),
    -- 참고 출처는 글당 하나 (기존 curation_sources 테이블 대체). 제목 없이 다른 출처 정보만 두지 않는다
    CONSTRAINT ck_curation_articles_source CHECK (
        source_title IS NOT NULL OR (source_publisher IS NULL AND source_url IS NULL AND source_license IS NULL)),
    -- draft = 작성 후 첫 게시 전, published = 공개, hidden = 게시 후 비공개
    CONSTRAINT ck_curation_articles_status CHECK (status IN ('draft', 'published', 'hidden')),
    -- 첫 게시 때 published_at 을 채우고 이후 공개/비공개 전환에도 유지한다
    CONSTRAINT ck_curation_articles_published CHECK ((status = 'draft') = (published_at IS NULL)),
    CONSTRAINT ck_curation_articles_issue CHECK ((status = 'draft') = (issue_no IS NULL) AND (issue_no IS NULL OR issue_no > 0)),
    CONSTRAINT ck_curation_articles_constraint_tags CHECK (constraint_tags <@ ARRAY['is_egg', 'is_milk', 'is_buckwheat',
        'is_peanut', 'is_soybean', 'is_wheat', 'is_mackerel', 'is_crab', 'is_shrimp', 'is_pork', 'is_peach', 'is_tomato',
        'is_sulfite', 'is_walnut', 'is_chicken', 'is_beef', 'is_squid', 'is_shellfish', 'is_pinenut', 'is_fish',
        'is_duck', 'is_alcohol', 'is_spicy']::varchar[]),
    CONSTRAINT ck_curation_articles_audience_tags CHECK (audience_tags <@ ARRAY['first_timer', 'vegetarian', 'vegan',
        'halal', 'kosher', 'hindu', 'no_spicy', 'no_alcohol', 'allergy_care']::varchar[])
);
CREATE INDEX idx_curation_articles_published ON curation_articles (published_at DESC, id DESC) WHERE status = 'published';
CREATE INDEX idx_curation_articles_category
    ON curation_articles (category_code, published_at DESC, id DESC) WHERE status = 'published';
CREATE INDEX idx_curation_articles_cover ON curation_articles (cover_image_asset_id) WHERE cover_image_asset_id IS NOT NULL;

CREATE TABLE curation_article_translations (
    article_id     BIGINT        NOT NULL,
    locale         VARCHAR(5)    NOT NULL,
    title          VARCHAR(200)  NOT NULL,
    subtitle       VARCHAR(300)  NULL,
    summary        VARCHAR(500)  NULL,
    body           TEXT          NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_curation_article_translations PRIMARY KEY (article_id, locale),
    CONSTRAINT fk_curation_article_translations_article FOREIGN KEY (article_id)
        REFERENCES curation_articles (id) ON DELETE CASCADE,
    -- ko 행이 운영자가 쓴 원문, 나머지는 원문에서 한 번에 만든 번역. 원문을 고치면 번역을 모두 다시 만든다
    CONSTRAINT ck_curation_article_translations_locale CHECK (locale IN ('ko', 'en', 'ar', 'zh-CN', 'zh-TW', 'ja', 'es'))
);

CREATE TABLE curation_article_menus (
    article_id  BIGINT       NOT NULL,
    menu_id     BIGINT       NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_curation_article_menus PRIMARY KEY (article_id, menu_id),
    CONSTRAINT fk_curation_article_menus_article FOREIGN KEY (article_id) REFERENCES curation_articles (id) ON DELETE CASCADE,
    CONSTRAINT fk_curation_article_menus_menu FOREIGN KEY (menu_id) REFERENCES menus (id) ON DELETE RESTRICT
);
-- 스캔 결과·큐레이션 탭 추천은 메뉴에서 출발한다
CREATE INDEX idx_curation_article_menus_menu ON curation_article_menus (menu_id, article_id);

-- ─────────────────────────────────────────────────────────────
-- H. 약관 동의 — 스키마 v2 에서 만들지 않는다 (2026-11-15)
--    운영에 배포된 Flyway V7__consent_decisions.sql 의 user_consent_decisions 를 그대로 쓴다.
--    약관 원문·버전·필수 여부는 src/main/resources/legal/{버전}.json, 동의·철회는 행 추가(이력).
--    이전 설계의 terms_versions · user_terms_agreements 는 폐기했다.
-- ─────────────────────────────────────────────────────────────

-- ─────────────────────────────────────────────────────────────
-- I. 기계번역 캐시 (스캔 기록을 다른 언어로 열 때 백엔드가 번역, ⑧ §4 2026-10-17 · 식사 후기 원문도 같은 캐시 사용)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE text_translations (
    source_hash      CHAR(64)     NOT NULL,
    target_locale    VARCHAR(5)   NOT NULL,
    source_locale    VARCHAR(5)   NOT NULL,
    source_text      TEXT         NOT NULL,
    translated_text  TEXT         NOT NULL,
    provider         VARCHAR(30)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_text_translations PRIMARY KEY (source_hash, target_locale),
    CONSTRAINT ck_text_translations_hash CHECK (source_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_text_translations_locales CHECK (
        source_locale IN ('ko', 'en', 'ar', 'zh-CN', 'zh-TW', 'ja', 'es')
        AND target_locale IN ('ko', 'en', 'ar', 'zh-CN', 'zh-TW', 'ja', 'es')
        AND source_locale <> target_locale)
);
CREATE INDEX idx_text_translations_created ON text_translations (created_at);

-- ─────────────────────────────────────────────────────────────
-- J. 식사 후기 (2026-11-15 화면 기준으로 다시 설계)
-- ─────────────────────────────────────────────────────────────
-- 단위: 후기 1건(meal_reviews) = 한 사람이 한 식당에 남긴 글 → 먹은 메뉴마다 1줄(meal_review_items) → 내 식단 항목마다 응답 1행(meal_review_answers).
--      화면의 "후기 N개"와 가게 카드의 후기 수는 후기 건수(meal_reviews)다. 후기 카드·수정·삭제·신고 모두 후기 단위다.
-- 식당 필수. 스캔은 선택: 결과 화면에서 쓰면 그 스캔에 연결되고(스캔당 1건), 지도의 가게 상세에서 쓰면 스캔 없이 남는다.
-- 메뉴: 스캔 결과의 메뉴를 고르거나 직접 입력한다(직접 입력은 menu_analysis_id NULL). 같은 스캔의 메뉴인지는 앱이 확인한다.
-- 공개: hidden_at IS NULL 인 후기만 가게 후기에 나온다. 작성자 이름은 응답에 넣지 않는다.
-- 삭제: 연결된 스캔 삭제 → 후기 CASCADE (화면: 이 기록으로 남긴 식사 후기도 함께 지워져요). 탈퇴 → CASCADE.
CREATE TABLE meal_reviews (
    id               UUID         NOT NULL,
    user_id          UUID         NOT NULL,
    store_id         BIGINT       NOT NULL,
    scan_session_id  UUID         NULL,
    hidden_at        TIMESTAMPTZ  NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_meal_reviews PRIMARY KEY (id),
    -- 스캔당 후기 1건 (스캔 없는 후기는 여러 건 가능)
    CONSTRAINT uq_meal_reviews_scan UNIQUE (scan_session_id),
    CONSTRAINT fk_meal_reviews_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_meal_reviews_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE RESTRICT,
    -- 내 스캔에만 연결할 수 있다
    CONSTRAINT fk_meal_reviews_scan FOREIGN KEY (scan_session_id, user_id)
        REFERENCES scan_sessions (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_meal_reviews_hidden CHECK (hidden_at IS NULL OR hidden_at >= created_at)
);
-- 가게 후기 목록·후기 수 (최신순)
CREATE INDEX idx_meal_reviews_store_recent ON meal_reviews (store_id, created_at DESC, id DESC);
-- 내가 작성한 후기 · 탈퇴 연쇄
CREATE INDEX idx_meal_reviews_user_recent ON meal_reviews (user_id, created_at DESC, id DESC);

CREATE TABLE meal_review_items (
    id                    UUID          NOT NULL,
    review_id             UUID          NOT NULL,
    menu_analysis_id      UUID          NULL,
    menu_name             VARCHAR(255)  NOT NULL,
    menu_name_normalized  VARCHAR(255)  GENERATED ALWAYS AS (normalize_food_name(menu_name)) STORED,
    comment               TEXT          NULL,
    comment_locale        VARCHAR(5)    NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_meal_review_items PRIMARY KEY (id),
    -- 후기 하나에 같은 메뉴는 한 줄
    CONSTRAINT uq_meal_review_items_menu UNIQUE (review_id, menu_name_normalized),
    CONSTRAINT fk_meal_review_items_review FOREIGN KEY (review_id) REFERENCES meal_reviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_meal_review_items_menu FOREIGN KEY (menu_analysis_id) REFERENCES menu_analyses (id) ON DELETE SET NULL,
    CONSTRAINT ck_meal_review_items_menu_name CHECK (menu_name_normalized <> ''),
    -- 더 남기고 싶은 말(선택)은 500자 이내. 빈 문자열 대신 NULL. 원문 언어가 있어야 번역 캐시 키(sha256(locale:원문))를 만든다
    CONSTRAINT ck_meal_review_items_comment CHECK (
        comment IS NULL OR (btrim(comment) <> '' AND char_length(comment) <= 500)),
    CONSTRAINT ck_meal_review_items_comment_locale CHECK (
        (comment IS NULL) = (comment_locale IS NULL)
        AND (comment_locale IS NULL OR comment_locale IN ('ko', 'en', 'ar', 'zh-CN', 'zh-TW', 'ja', 'es')))
);
-- 메뉴 분석 행 삭제 시 SET NULL 탐색
CREATE INDEX idx_meal_review_items_menu ON meal_review_items (menu_analysis_id) WHERE menu_analysis_id IS NOT NULL;

-- 내 식단 항목 코드: 화면(프론트)의 식단 항목 ID 와 같은 값을 쓴다.
--   vegan:{vegan|lacto|ovo|lacto_ovo|pesco} · religion:{halal|kosher|hindu} · allergy:{식약처 19종} · preference:no-spicy · preference:no-alcohol
-- 응답하지 않은 항목은 행을 두지 않는다(FN-safe: "없었어요"로 채우지 않음).
CREATE TABLE meal_review_answers (
    review_item_id  UUID         NOT NULL,
    diet_code       VARCHAR(30)  NOT NULL,
    answer          VARCHAR(10)  NOT NULL,
    CONSTRAINT pk_meal_review_answers PRIMARY KEY (review_item_id, diet_code),
    CONSTRAINT fk_meal_review_answers_item FOREIGN KEY (review_item_id) REFERENCES meal_review_items (id) ON DELETE CASCADE,
    CONSTRAINT ck_meal_review_answers_diet_code CHECK (diet_code IN (
        'vegan:vegan', 'vegan:lacto', 'vegan:ovo', 'vegan:lacto_ovo', 'vegan:pesco',
        'religion:halal', 'religion:kosher', 'religion:hindu',
        'allergy:egg', 'allergy:milk', 'allergy:buckwheat', 'allergy:peanut', 'allergy:soybean', 'allergy:wheat',
        'allergy:mackerel', 'allergy:crab', 'allergy:shrimp', 'allergy:pork', 'allergy:peach', 'allergy:tomato',
        'allergy:sulfite', 'allergy:walnut', 'allergy:chicken', 'allergy:beef', 'allergy:squid', 'allergy:shellfish',
        'allergy:pinenut',
        'preference:no-spicy', 'preference:no-alcohol')),
    -- 있었어요 / 없었어요 / 잘 모르겠어요
    CONSTRAINT ck_meal_review_answers_answer CHECK (answer IN ('present', 'absent', 'unknown'))
);

-- 후기 신고: 남의 후기를 신고한다. 저장만 하고 바로 숨기지 않는다. 검토·숨김은 관리자 화면(3순위)에서 meal_reviews.hidden_at 으로 한다.
-- 내 후기 신고 금지(400)는 앱이 검사한다. 같은 사람이 같은 후기 재신고는 UNIQUE(409).
CREATE TABLE meal_review_reports (
    id                UUID          NOT NULL,
    review_id         UUID          NOT NULL,
    reporter_user_id  UUID          NOT NULL,
    reason            VARCHAR(20)   NOT NULL,
    detail            TEXT          NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_meal_review_reports PRIMARY KEY (id),
    CONSTRAINT uq_meal_review_reports_reporter UNIQUE (review_id, reporter_user_id),
    CONSTRAINT fk_meal_review_reports_review FOREIGN KEY (review_id) REFERENCES meal_reviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_meal_review_reports_reporter FOREIGN KEY (reporter_user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- 부적절 / 허위 정보 / 광고·스팸 / 기타
    CONSTRAINT ck_meal_review_reports_reason CHECK (reason IN ('inappropriate', 'false_info', 'spam', 'other')),
    -- 상세는 선택(화면에는 사유 선택만 있다), 500자 이내, 빈 문자열 대신 NULL
    CONSTRAINT ck_meal_review_reports_detail CHECK (
        detail IS NULL OR (btrim(detail) <> '' AND char_length(detail) <= 500))
);
-- 탈퇴 연쇄용 (uq 는 review_id 선두라 대신하지 못함)
CREATE INDEX idx_meal_review_reports_reporter ON meal_review_reports (reporter_user_id);

-- ─────────────────────────────────────────────────────────────
-- K. 동네 (2026-11-01 반경 자동 배정, 2026-11-03 정렬)
-- ─────────────────────────────────────────────────────────────
-- 홈 동네 칩. 관리자가 중심 좌표·반경으로 등록하면 그 안의 가게가 자동으로 그 동네다(수동 배정 없음, 연결 테이블 없음).
-- 가게 검색은 stores 의 idx_stores_geo_active(earthdistance GiST)를 쓴다. 정렬: 위치가 있으면 사용자와의 거리 → 비슷하면 후기 수,
-- 없으면 후기 많은 순. 동네가 겹치면 한 가게가 여러 동네에 나올 수 있다. 이름은 관리자가 등록하므로 앱 리소스가 아니라 DB 다국어.
CREATE TABLE areas (
    id             BIGINT            GENERATED ALWAYS AS IDENTITY,
    name_i18n      JSONB             NOT NULL,
    center_lat     DOUBLE PRECISION  NOT NULL,
    center_lng     DOUBLE PRECISION  NOT NULL,
    radius_m       INTEGER           NOT NULL,
    display_order  SMALLINT          NOT NULL DEFAULT 0,
    status         VARCHAR(20)       NOT NULL DEFAULT 'active',
    created_at     TIMESTAMPTZ       NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ       NOT NULL DEFAULT now(),
    CONSTRAINT pk_areas PRIMARY KEY (id),
    -- ko 필수 + 7개 언어 안. 없는 언어는 ko 로 보여 준다. ko 가 없을 때 NULL 로 "통과"하지 않게 ? 로 먼저 확인한다
    CONSTRAINT ck_areas_name CHECK (
        jsonb_typeof(name_i18n) = 'object' AND name_i18n ? 'ko' AND jsonb_typeof(name_i18n -> 'ko') = 'string'
        AND btrim(name_i18n ->> 'ko') <> ''
        AND name_i18n - ARRAY['ko', 'en', 'ar', 'zh-CN', 'zh-TW', 'ja', 'es'] = '{}'::jsonb),
    -- stores 좌표 범위와 같다 (ck_stores_lat·ck_stores_lng)
    CONSTRAINT ck_areas_center CHECK (center_lat BETWEEN 33 AND 39 AND center_lng BETWEEN 124 AND 132),
    CONSTRAINT ck_areas_radius CHECK (radius_m > 0),
    CONSTRAINT ck_areas_status CHECK (status IN ('active', 'hidden'))
);

