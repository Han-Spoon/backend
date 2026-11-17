-- V11 가게 단위 확정·증거: 가게 메뉴 · 확정값 · 직원 질문 · 증거 로그 · 가게 prior VIEW · 캐시 · 출처 신뢰도
-- ─────────────────────────────────────────────────────────────
-- D. 가게 단위 학습·확정 (store_id NOT NULL)
-- ─────────────────────────────────────────────────────────────
-- 가게 메뉴 = "이 가게 메뉴판에 이렇게 적혀 있다". 재료·레시피는 표준 메뉴(menus)에 있고,
--     직원 응답(질문·확정값)은 표준 메뉴가 아니라 이 가게 메뉴에 붙는다 (2026-10-26 회의).
--     표준 메뉴 매핑이 바뀌거나 아직 없어도(웹 경로 메뉴) 답변이 그 가게의 그 메뉴에 그대로 남는다.
CREATE TABLE store_menus (
    id                      BIGINT        GENERATED ALWAYS AS IDENTITY,
    store_id                BIGINT        NOT NULL,
    name_normalized         VARCHAR(255)  NOT NULL,
    display_name            VARCHAR(255)  NOT NULL,
    description             TEXT          NULL,
    price_text              VARCHAR(100)  NULL,
    price_options           JSONB         NOT NULL DEFAULT '[]'::jsonb,
    menu_id                 BIGINT        NULL,
    mapping_source          VARCHAR(20)   NOT NULL DEFAULT 'none',
    mapping_conflict_count  INTEGER       NOT NULL DEFAULT 0,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'active',
    first_seen_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_seen_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    seen_count              INTEGER       NOT NULL DEFAULT 1,
    last_scan_session_id    UUID          NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_store_menus PRIMARY KEY (id),
    CONSTRAINT uq_store_menus_name UNIQUE (store_id, name_normalized),
    -- menu_analyses·직원 질문·확정값이 같은 가게의 가게 메뉴만 가리키도록 하는 복합 FK 대상
    CONSTRAINT uq_store_menus_link UNIQUE (id, store_id),
    CONSTRAINT fk_store_menus_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE RESTRICT,
    CONSTRAINT fk_store_menus_menu FOREIGN KEY (menu_id) REFERENCES menus (id) ON DELETE RESTRICT,
    CONSTRAINT fk_store_menus_last_scan FOREIGN KEY (last_scan_session_id) REFERENCES scan_sessions (id) ON DELETE SET NULL,
    CONSTRAINT ck_store_menus_name CHECK (name_normalized <> '' AND name_normalized = normalize_food_name(name_normalized)),
    CONSTRAINT ck_store_menus_display_name CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_store_menus_price_options CHECK (jsonb_typeof(price_options) = 'array'),
    CONSTRAINT ck_store_menus_mapping CHECK (mapping_source IN ('none', 'fuzzy', 'name_match', 'admin')),
    CONSTRAINT ck_store_menus_mapping_pair CHECK ((mapping_source = 'none') = (menu_id IS NULL)),
    CONSTRAINT ck_store_menus_status CHECK (status IN ('active', 'hidden')),
    CONSTRAINT ck_store_menus_counts CHECK (seen_count >= 1 AND mapping_conflict_count >= 0),
    CONSTRAINT ck_store_menus_seen CHECK (last_seen_at >= first_seen_at)
);
CREATE INDEX idx_store_menus_menu ON store_menus (menu_id) WHERE menu_id IS NOT NULL;
CREATE INDEX idx_store_menus_last_scan ON store_menus (last_scan_session_id) WHERE last_scan_session_id IS NOT NULL;
CREATE INDEX idx_store_menus_conflicts ON store_menus (updated_at DESC) WHERE mapping_conflict_count >= 2;

-- 직원 응답 = 손님 질문 카드에 가게 직원이 버튼으로 답한 값 (2026-11-09). 사장님 계정·관리자 승인은 없다.
--     답(present/absent)은 누르는 즉시 가게 공용 확정값에 upsert 한다. AI Judge 는 다시 부르지 않는다 (2026-11-12).
--     present 이고 그 대상이 내 식단과 겹치면 서버가 그 메뉴 risk_level 을 danger 로만 올린다(AI 첫 판정은 ai_risk_level). 안전 판정은 다음 스캔의 Judge.
--     잘못 누르면 같은 질문에서 다시 답한다: 확정값은 최신 답으로 갱신, unknown(모름)으로 고치면 invalidated_at 으로 사용 중지.
--     기간 만료 없음. 관리자 무효화(invalidated_at)도 같은 컬럼을 쓴다. 답 이력은 staff_questions 행으로 본다(AI #25).
--     이상 답변 잠금(flagged_anomaly·override_eligible)은 폐기: 모든 확정값을 그대로 override 에 쓴다 (AI 10-27 회의, #26).
--     AI 계약 이름은 그대로다: Judge owner_answer_status → staff_answer_status, 명령 create_owner_verification_request → staff_questions.
CREATE TABLE ingredient_confirmations (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY,
    store_id           BIGINT       NOT NULL,
    store_menu_id      BIGINT       NOT NULL,
    ingredient_id      BIGINT       NOT NULL,
    present            BOOLEAN      NOT NULL,
    source             VARCHAR(20)  NOT NULL DEFAULT 'staff_confirmed',
    confirmed_at       TIMESTAMPTZ  NOT NULL,
    invalidated_at     TIMESTAMPTZ  NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_ingredient_confirmations PRIMARY KEY (id),
    CONSTRAINT uq_ingredient_confirmations UNIQUE (store_menu_id, ingredient_id),
    CONSTRAINT uq_ingredient_confirmations_link UNIQUE (id, store_menu_id, ingredient_id),
    -- 가게 메뉴가 같은 가게 것인지 복합 FK 로 강제한다 (store_id 단독 FK 는 이 FK 가 대신한다)
    CONSTRAINT fk_ingredient_confirmations_store_menu FOREIGN KEY (store_menu_id, store_id)
        REFERENCES store_menus (id, store_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ingredient_confirmations_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ingredient_confirmations_source CHECK (source IN ('staff_confirmed')),
    CONSTRAINT ck_ingredient_confirmations_invalidated CHECK (invalidated_at IS NULL OR invalidated_at >= confirmed_at)
);

CREATE TABLE flag_confirmations (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY,
    store_id        BIGINT        NOT NULL,
    store_menu_id   BIGINT        NOT NULL,
    ambiguity_flag  VARCHAR(30)   NOT NULL,
    asked_tags      VARCHAR(20)[] NOT NULL,
    present         BOOLEAN       NOT NULL,
    confirmed_at    TIMESTAMPTZ   NOT NULL,
    invalidated_at  TIMESTAMPTZ   NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_flag_confirmations PRIMARY KEY (id),
    CONSTRAINT uq_flag_confirmations UNIQUE (store_menu_id, ambiguity_flag, asked_tags),
    -- 질문 링크 FK 대상. asked_tags(varchar[])는 FK 에 넣으면 PG RI 검사가 배열 비교 연산자를 찾지 못해 실행 시 실패하므로 뺀다
    CONSTRAINT uq_flag_confirmations_link UNIQUE (id, store_menu_id, ambiguity_flag),
    CONSTRAINT fk_flag_confirmations_store_menu FOREIGN KEY (store_menu_id, store_id)
        REFERENCES store_menus (id, store_id) ON DELETE RESTRICT,
    CONSTRAINT ck_flag_confirmations_invalidated CHECK (invalidated_at IS NULL OR invalidated_at >= confirmed_at),
    CONSTRAINT ck_flag_confirmations_flag CHECK (ambiguity_flag IN (
        'has_unclear_broth', 'has_unclear_jeotgal', 'has_unclear_seasoning', 'has_hidden_animal', 'has_variant')),
    CONSTRAINT ck_flag_confirmations_tags CHECK (
        cardinality(asked_tags) > 0 AND is_sorted_distinct(asked_tags)
        AND asked_tags <@ ARRAY['is_egg', 'is_milk', 'is_buckwheat', 'is_peanut', 'is_soybean', 'is_wheat',
            'is_mackerel', 'is_crab', 'is_shrimp', 'is_pork', 'is_peach', 'is_tomato', 'is_sulfite', 'is_walnut',
            'is_chicken', 'is_beef', 'is_squid', 'is_shellfish', 'is_pinenut', 'is_fish', 'is_duck', 'is_alcohol']::varchar[])
);

-- 직원 질문: 대상(재료 또는 플래그+태그)마다 스캔당 한 번 만든다 (2026-10-26 회의, AI #151). 차수(round)·직전 질문은 두지 않는다.
-- 답은 같은 행에서 고칠 수 있다(answer·answered_at 덮어쓰기). status = 'expired' 는 답하지 않은 질문의 처리 기한 종료만 뜻한다.
-- 빼기·덜 맵게·주문 카드의 직원 응답(가능/어려움)은 그 주문 한 번의 요청이라 가게 공용 사실이 아니므로 저장하지 않는다 (2026-11-10).
CREATE TABLE staff_questions (
    id                             UUID          NOT NULL,
    store_id                       BIGINT        NOT NULL,
    scan_session_id                UUID          NULL,
    store_menu_id                  BIGINT        NOT NULL,
    menu_id                        BIGINT        NULL,
    ingredient_id                  BIGINT        NULL,
    ambiguity_flag                 VARCHAR(30)   NULL,
    asked_tags                     VARCHAR(20)[] NULL,
    question_text                  JSONB         NOT NULL,
    card_id                        VARCHAR(400)  NULL,
    status                         VARCHAR(20)   NOT NULL DEFAULT 'pending',
    answer                         VARCHAR(10)   NULL,
    answered_at                    TIMESTAMPTZ   NULL,
    resolved_confirmation_id       BIGINT        NULL,
    resolved_flag_confirmation_id  BIGINT        NULL,
    created_at                     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_staff_questions PRIMARY KEY (id),
    CONSTRAINT uq_staff_questions_card UNIQUE (scan_session_id, card_id),
    CONSTRAINT fk_staff_questions_scan FOREIGN KEY (scan_session_id, store_id)
        REFERENCES scan_sessions (id, store_id) ON DELETE SET NULL (scan_session_id),
    CONSTRAINT fk_staff_questions_store_menu FOREIGN KEY (store_menu_id, store_id)
        REFERENCES store_menus (id, store_id) ON DELETE RESTRICT,
    CONSTRAINT fk_staff_questions_menu FOREIGN KEY (menu_id) REFERENCES menus (id) ON DELETE RESTRICT,
    CONSTRAINT fk_staff_questions_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_staff_questions_confirmation
        FOREIGN KEY (resolved_confirmation_id, store_menu_id, ingredient_id)
        REFERENCES ingredient_confirmations (id, store_menu_id, ingredient_id) ON DELETE RESTRICT,
    CONSTRAINT fk_staff_questions_flag_confirmation
        FOREIGN KEY (resolved_flag_confirmation_id, store_menu_id, ambiguity_flag)
        REFERENCES flag_confirmations (id, store_menu_id, ambiguity_flag) ON DELETE RESTRICT,
    -- #11: 재료 질문과 플래그 질문 중 정확히 하나
    CONSTRAINT ck_staff_questions_target CHECK (num_nonnulls(ingredient_id, ambiguity_flag) = 1),
    CONSTRAINT ck_staff_questions_flag CHECK (ambiguity_flag IS NULL OR ambiguity_flag IN (
        'has_unclear_broth', 'has_unclear_jeotgal', 'has_unclear_seasoning', 'has_hidden_animal', 'has_variant')),
    CONSTRAINT ck_staff_questions_tags CHECK (
        (ambiguity_flag IS NULL AND asked_tags IS NULL)
        OR (ambiguity_flag IS NOT NULL AND asked_tags IS NOT NULL
            AND cardinality(asked_tags) > 0 AND is_sorted_distinct(asked_tags))),
    -- 질문 문구 = ko(직원이 읽음) + 사용자 언어 (⓪ §4-6, ⑧ §4)
    CONSTRAINT ck_staff_questions_question CHECK (
        jsonb_typeof(question_text) = 'object' AND question_text ? 'ko'
        AND question_text - ARRAY['ko', 'en', 'ar', 'zh-CN', 'zh-TW', 'ja', 'es'] = '{}'::jsonb),
    CONSTRAINT ck_staff_questions_status CHECK (status IN ('pending', 'answered', 'expired')),
    CONSTRAINT ck_staff_questions_answer CHECK (answer IS NULL OR answer IN ('present', 'absent', 'unknown')),
    CONSTRAINT ck_staff_questions_answered CHECK (
        (status = 'answered') = (answer IS NOT NULL AND answered_at IS NOT NULL)
        AND (status = 'answered' OR (answer IS NULL AND answered_at IS NULL))),
    -- #16: present/absent 답변은 같은 트랜잭션에서 확정값에 반영하고 질문 종류에 맞는 쪽을 반드시 연결한다.
    --      unknown·미답변·기한 종료는 연결하지 않는다(unknown 으로 고치면 연결을 끊고 확정값을 사용 중지). CASE 로 써서 NULL 이 "통과"로 새지 않게 한다.
    CONSTRAINT ck_staff_questions_resolved CHECK (CASE
        WHEN status = 'answered' AND answer IN ('present', 'absent') AND ingredient_id IS NOT NULL
            THEN resolved_confirmation_id IS NOT NULL AND resolved_flag_confirmation_id IS NULL
        WHEN status = 'answered' AND answer IN ('present', 'absent')
            THEN resolved_flag_confirmation_id IS NOT NULL AND resolved_confirmation_id IS NULL
        ELSE resolved_confirmation_id IS NULL AND resolved_flag_confirmation_id IS NULL END)
);
-- #17: 같은 스캔·가게 메뉴·대상 질문은 하나 (차수 없음)
CREATE UNIQUE INDEX uq_staff_questions_ingredient
    ON staff_questions (scan_session_id, store_menu_id, ingredient_id) WHERE ingredient_id IS NOT NULL;
CREATE UNIQUE INDEX uq_staff_questions_flag
    ON staff_questions (scan_session_id, store_menu_id, ambiguity_flag, asked_tags) WHERE ambiguity_flag IS NOT NULL;
-- 가게 메뉴별 질문 조회(대기 질문 포함)와 가게 메뉴 삭제 RESTRICT 검사
CREATE INDEX idx_staff_questions_store_menu ON staff_questions (store_menu_id, status);

-- 가게 증거 로그 = 관리자가 승인한 변형 근거(ocr_variant_tag)만. 직원 응답은 넣지 않는다(답 이력은 staff_questions, AI #25).
-- 웹 레시피는 가게 증거가 아니라 넣지 않는다.
CREATE TABLE ingredient_evidence_log (
    id              BIGINT         GENERATED ALWAYS AS IDENTITY,
    store_id        BIGINT         NOT NULL,
    menu_id         BIGINT         NOT NULL,
    ingredient_id   BIGINT         NOT NULL,
    source_type     VARCHAR(20)    NOT NULL,
    delta_alpha     NUMERIC(10, 4) NOT NULL,
    delta_beta      NUMERIC(10, 4) NOT NULL,
    review_item_id  BIGINT         NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_ingredient_evidence_log PRIMARY KEY (id),
    CONSTRAINT fk_ingredient_evidence_log_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ingredient_evidence_log_menu FOREIGN KEY (menu_id) REFERENCES menus (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ingredient_evidence_log_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ingredient_evidence_log_review FOREIGN KEY (review_item_id)
        REFERENCES knowledge_review_items (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ingredient_evidence_log_source CHECK (source_type IN ('ocr_variant_tag')),
    -- 변형 근거는 '있음' 쪽 soft 증거만 더한다
    CONSTRAINT ck_ingredient_evidence_log_delta CHECK (delta_alpha > 0 AND delta_beta = 0)
);
CREATE INDEX idx_ingredient_evidence_log_key ON ingredient_evidence_log (store_id, menu_id, ingredient_id, id);
-- 가게 prior VIEW 전용: 변형 근거만, 합계에 필요한 값까지 인덱스에서 읽는다
CREATE INDEX idx_ingredient_evidence_log_prior ON ingredient_evidence_log (store_id, menu_id, ingredient_id)
    INCLUDE (delta_alpha, delta_beta) WHERE source_type = 'ocr_variant_tag';
CREATE UNIQUE INDEX uq_ingredient_evidence_log_review
    ON ingredient_evidence_log (review_item_id, store_id, menu_id, ingredient_id);

-- 가게 prior = 승인된 변형 근거(ocr_variant_tag)만의 합 (⓪ §1-3).
-- 행이 적고(변형 승인 × 재료 × 가게) 로그가 원천이라 스냅샷 테이블 대신 VIEW 로 둔다. 재계산·직접 UPDATE 금지 규칙이 필요 없다.
-- AI ⑤ 문서 §2-4 의 조회(SELECT ingredient_id, alpha, beta, evidence_count FROM ingredient_risk_scores …)는 그대로 동작한다.
CREATE VIEW ingredient_risk_scores AS
SELECT store_id,
       menu_id,
       ingredient_id,
       sum(delta_alpha)      AS delta_alpha_sum,
       sum(delta_beta)       AS delta_beta_sum,
       1 + sum(delta_alpha)  AS alpha,
       1 + sum(delta_beta)   AS beta,
       count(*)              AS evidence_count,
       max(id)               AS last_evidence_id,
       max(created_at)       AS updated_at
FROM ingredient_evidence_log
WHERE source_type = 'ocr_variant_tag'
GROUP BY store_id, menu_id, ingredient_id;
COMMENT ON VIEW ingredient_risk_scores IS '가게 재료 확률 (VIEW: 변형 근거 합계 → α·β)';

-- 내용이 전역 데이터라 메뉴 단위 1행. AI 명령의 store_id 는 저장하지 않는다.
CREATE TABLE menu_ingredient_cache (
    menu_id               BIGINT       NOT NULL,
    menu_revision         INTEGER      NOT NULL,
    ingredients_snapshot  JSONB        NOT NULL,
    computed_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_menu_ingredient_cache PRIMARY KEY (menu_id),
    CONSTRAINT fk_menu_ingredient_cache_menu FOREIGN KEY (menu_id) REFERENCES menus (id) ON DELETE CASCADE,
    CONSTRAINT ck_menu_ingredient_cache_revision CHECK (menu_revision >= 1),
    CONSTRAINT ck_menu_ingredient_cache_snapshot CHECK (jsonb_typeof(ingredients_snapshot) = 'array')
);

CREATE TABLE source_reliability (
    source_type       VARCHAR(40)    NOT NULL,
    agree_count       INTEGER        NOT NULL DEFAULT 0,
    compared_count    INTEGER        NOT NULL DEFAULT 0,
    reliability       NUMERIC(5, 4)  GENERATED ALWAYS AS (round((agree_count + 1)::numeric / (compared_count + 2), 4)) STORED,
    last_evidence_id  BIGINT         NULL,
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_source_reliability PRIMARY KEY (source_type),
    CONSTRAINT fk_source_reliability_last_evidence FOREIGN KEY (last_evidence_id)
        REFERENCES ingredient_evidence_log (id) ON DELETE SET NULL,
    CONSTRAINT ck_source_reliability_type CHECK (source_type ~ '^(corpus:[a-z0-9_]+|curated|web_search|ocr_variant_tag)$'),
    CONSTRAINT ck_source_reliability_counts CHECK (agree_count >= 0 AND agree_count <= compared_count)
);

