-- V9 전역 지식: 메뉴 · 재료 · 온톨로지 · 레시피 · 코퍼스 관측
-- ─────────────────────────────────────────────────────────────
-- B. 전역 지식 (가게 무관)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE menu_categories (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY,
    name_ko     VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_menu_categories PRIMARY KEY (id),
    CONSTRAINT uq_menu_categories_name UNIQUE (name_ko),
    CONSTRAINT ck_menu_categories_name CHECK (btrim(name_ko) <> '' AND name_ko = btrim(name_ko))
);

CREATE TABLE menus (
    id                  BIGINT        GENERATED ALWAYS AS IDENTITY,
    name_ko             VARCHAR(100)  NOT NULL,
    name_normalized     VARCHAR(100)  GENERATED ALWAYS AS (normalize_food_name(name_ko)) STORED,
    name_i18n           JSONB         NULL,
    category_id         BIGINT        NULL,
    base_menu_id        BIGINT        NULL,
    remain_token        VARCHAR(30)   NULL,
    ambiguity_flags     VARCHAR(30)[] NULL,
    source              VARCHAR(30)   NOT NULL,
    needs_review        BOOLEAN       NOT NULL,
    knowledge_revision  INTEGER       NOT NULL DEFAULT 1,
    web_registered_at          TIMESTAMPTZ  NULL,
    web_approved_source_count  INTEGER      NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_menus PRIMARY KEY (id),
    CONSTRAINT uq_menus_name_normalized UNIQUE (name_normalized),
    CONSTRAINT fk_menus_category FOREIGN KEY (category_id) REFERENCES menu_categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_menus_base FOREIGN KEY (base_menu_id) REFERENCES menus (id) ON DELETE RESTRICT,
    CONSTRAINT ck_menus_name CHECK (btrim(name_ko) <> '' AND name_ko = btrim(name_ko)),
    CONSTRAINT ck_menus_name_normalized CHECK (name_normalized <> ''),
    CONSTRAINT ck_menus_name_i18n CHECK (
        name_i18n IS NULL OR (jsonb_typeof(name_i18n) = 'object'
            AND name_i18n - ARRAY['ko', 'en', 'ar', 'zh-CN', 'zh-TW', 'ja', 'es'] = '{}'::jsonb)),
    CONSTRAINT ck_menus_variant_pair CHECK ((base_menu_id IS NULL) = (remain_token IS NULL)),
    CONSTRAINT ck_menus_variant_self CHECK (base_menu_id IS NULL OR base_menu_id <> id),
    CONSTRAINT ck_menus_remain_token CHECK (
        remain_token IS NULL OR (btrim(remain_token) <> '' AND remain_token = btrim(remain_token))),
    CONSTRAINT ck_menus_source CHECK (source IN ('curated', 'web_search_generated', 'variant_generated')),
    CONSTRAINT ck_menus_variant_source CHECK (source <> 'variant_generated' OR base_menu_id IS NOT NULL),
    CONSTRAINT ck_menus_ambiguity_flags CHECK (
        ambiguity_flags IS NULL OR ambiguity_flags <@ ARRAY['has_unclear_broth', 'has_unclear_jeotgal',
            'has_unclear_seasoning', 'has_hidden_animal', 'has_variant']::varchar[]),
    CONSTRAINT ck_menus_reviewed_metadata CHECK (needs_review OR (category_id IS NOT NULL AND ambiguity_flags IS NOT NULL)),
    CONSTRAINT ck_menus_knowledge_revision CHECK (knowledge_revision >= 1),
    -- 승인 레시피 30개 이상으로 등록된 웹 메뉴만 등록 정보를 가진다 (메뉴당 1회라 별도 테이블 대신 컬럼).
    -- 등록은 30개가 되는 순간 백엔드가 자동으로 하므로 등록자 컬럼은 두지 않는다 (2026-10-26 회의).
    CONSTRAINT ck_menus_web_registration CHECK (
        (source = 'web_search_generated') = (web_registered_at IS NOT NULL)
        AND (web_registered_at IS NULL) = (web_approved_source_count IS NULL)
        AND (web_approved_source_count IS NULL OR web_approved_source_count >= 30))
);
-- fuzzy 후보 조회(②-B 후보 최대 5개). 정확·별칭 조회는 UNIQUE 인덱스가 처리한다.
-- GiST: ORDER BY name_normalized <-> :q LIMIT 5 의 최근접(KNN) 탐색을 인덱스가 직접 수행한다.
-- 실측(메뉴 2.1만, 메뉴명 10개): GIN + similarity 정렬 265ms → GiST KNN 26ms, 결과 동일, 인덱스 1/3 크기.
CREATE INDEX idx_menus_name_trgm ON menus USING gist (name_normalized gist_trgm_ops);
CREATE INDEX idx_menus_category ON menus (category_id) WHERE category_id IS NOT NULL;
CREATE INDEX idx_menus_base ON menus (base_menu_id) WHERE base_menu_id IS NOT NULL;

CREATE TABLE menu_aliases (
    id                BIGINT        GENERATED ALWAYS AS IDENTITY,
    menu_id           BIGINT        NOT NULL,
    alias_ko          VARCHAR(100)  NOT NULL,
    alias_normalized  VARCHAR(100)  GENERATED ALWAYS AS (normalize_food_name(alias_ko)) STORED,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_menu_aliases PRIMARY KEY (id),
    CONSTRAINT uq_menu_aliases_alias UNIQUE (alias_normalized),
    CONSTRAINT fk_menu_aliases_menu FOREIGN KEY (menu_id) REFERENCES menus (id) ON DELETE CASCADE,
    CONSTRAINT ck_menu_aliases_alias CHECK (btrim(alias_ko) <> '' AND alias_ko = btrim(alias_ko)),
    CONSTRAINT ck_menu_aliases_alias_normalized CHECK (alias_normalized <> '')
);
CREATE INDEX idx_menu_aliases_menu ON menu_aliases (menu_id);
CREATE INDEX idx_menu_aliases_trgm ON menu_aliases USING gist (alias_normalized gist_trgm_ops);

CREATE TABLE ingredients (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY,
    name_ko            VARCHAR(60)  NOT NULL,
    name_normalized    VARCHAR(60)  GENERATED ALWAYS AS (normalize_food_name(name_ko)) STORED,
    name_i18n          JSONB        NULL,
    taxonomy_category  VARCHAR(20)  NOT NULL DEFAULT 'unknown',
    tag_codes          VARCHAR(20)[] NOT NULL DEFAULT '{}',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_ingredients PRIMARY KEY (id),
    CONSTRAINT uq_ingredients_name_normalized UNIQUE (name_normalized),
    CONSTRAINT ck_ingredients_name CHECK (btrim(name_ko) <> '' AND name_ko = btrim(name_ko)),
    CONSTRAINT ck_ingredients_name_normalized CHECK (name_normalized <> ''),
    CONSTRAINT ck_ingredients_name_i18n CHECK (
        name_i18n IS NULL OR (jsonb_typeof(name_i18n) = 'object'
            AND name_i18n - ARRAY['ko', 'en', 'ar', 'zh-CN', 'zh-TW', 'ja', 'es'] = '{}'::jsonb)),
    CONSTRAINT ck_ingredients_taxonomy CHECK (taxonomy_category IN (
        'fermented', 'broth', 'sauce', 'processed', 'seasoning', 'oil', 'garnish', 'base', 'unknown')),
    -- 재료 노드 태그(#21): is_* 22종, 정렬·중복 없음. 기존 ingredient_tags 테이블을 배열로 통합
    CONSTRAINT ck_ingredients_tag_codes CHECK (
        is_sorted_distinct(tag_codes) AND tag_codes <@ ARRAY['is_egg', 'is_milk', 'is_buckwheat', 'is_peanut', 'is_soybean', 'is_wheat', 'is_mackerel', 'is_crab',
            'is_shrimp', 'is_pork', 'is_peach', 'is_tomato', 'is_sulfite', 'is_walnut', 'is_chicken', 'is_beef',
            'is_squid', 'is_shellfish', 'is_pinenut', 'is_fish', 'is_duck', 'is_alcohol']::varchar[])
);

CREATE TABLE ingredient_aliases (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY,
    ingredient_id     BIGINT       NOT NULL,
    alias_ko          VARCHAR(60)  NOT NULL,
    alias_normalized  VARCHAR(60)  GENERATED ALWAYS AS (normalize_food_name(alias_ko)) STORED,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_ingredient_aliases PRIMARY KEY (id),
    CONSTRAINT uq_ingredient_aliases_alias UNIQUE (alias_normalized),
    CONSTRAINT fk_ingredient_aliases_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id) ON DELETE CASCADE,
    CONSTRAINT ck_ingredient_aliases_alias CHECK (btrim(alias_ko) <> '' AND alias_ko = btrim(alias_ko)),
    CONSTRAINT ck_ingredient_aliases_alias_normalized CHECK (alias_normalized <> '')
);
CREATE INDEX idx_ingredient_aliases_ingredient ON ingredient_aliases (ingredient_id);

CREATE TABLE ingredient_compositions (
    parent_ingredient_id  BIGINT       NOT NULL,
    child_ingredient_id   BIGINT       NOT NULL,
    relation_type         VARCHAR(10)  NOT NULL DEFAULT 'part_of',
    ambiguity_flag        VARCHAR(30)  NULL,
    tag_codes             VARCHAR(20)[] NOT NULL DEFAULT '{}',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_ingredient_compositions PRIMARY KEY (parent_ingredient_id, child_ingredient_id),
    CONSTRAINT fk_ingredient_compositions_parent FOREIGN KEY (parent_ingredient_id) REFERENCES ingredients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ingredient_compositions_child FOREIGN KEY (child_ingredient_id) REFERENCES ingredients (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ingredient_compositions_not_self CHECK (parent_ingredient_id <> child_ingredient_id),
    CONSTRAINT ck_ingredient_compositions_relation CHECK (relation_type = 'part_of'),
    CONSTRAINT ck_ingredient_compositions_flag CHECK (ambiguity_flag IS NULL OR ambiguity_flag IN (
        'has_unclear_broth', 'has_unclear_jeotgal', 'has_unclear_seasoning', 'has_hidden_animal')),
    -- 이 간선(상위 맥락)에서만 성립하는 위험 태그. 기존 ingredient_composition_tags 테이블을 배열로 통합
    CONSTRAINT ck_ingredient_compositions_tag_codes CHECK (
        is_sorted_distinct(tag_codes) AND tag_codes <@ ARRAY['is_egg', 'is_milk', 'is_buckwheat', 'is_peanut', 'is_soybean', 'is_wheat', 'is_mackerel', 'is_crab',
            'is_shrimp', 'is_pork', 'is_peach', 'is_tomato', 'is_sulfite', 'is_walnut', 'is_chicken', 'is_beef',
            'is_squid', 'is_shellfish', 'is_pinenut', 'is_fish', 'is_duck', 'is_alcohol']::varchar[])
);
CREATE INDEX idx_ingredient_compositions_child ON ingredient_compositions (child_ingredient_id);

-- 메뉴 직접 재료(depth 0). 가게 무관. 웹 재료(web_search)의 출처는 그 메뉴의 웹 등록 정보(menus.web_*)
CREATE TABLE recipe_ingredients (
    menu_id          BIGINT       NOT NULL,
    ingredient_id    BIGINT       NOT NULL,
    evidence_type    VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_recipe_ingredients PRIMARY KEY (menu_id, ingredient_id),
    CONSTRAINT fk_recipe_ingredients_menu FOREIGN KEY (menu_id) REFERENCES menus (id) ON DELETE RESTRICT,
    CONSTRAINT fk_recipe_ingredients_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id) ON DELETE RESTRICT,
    CONSTRAINT ck_recipe_ingredients_evidence CHECK (evidence_type IN ('explicit', 'variant', 'name_rule', 'web_search'))
);
CREATE INDEX idx_recipe_ingredients_ingredient ON recipe_ingredients (ingredient_id);

CREATE TABLE knowledge_import_batches (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY,
    source          VARCHAR(30)  NOT NULL,
    source_version  VARCHAR(40)  NOT NULL,
    row_count       INTEGER      NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'running',
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ  NULL,
    CONSTRAINT pk_knowledge_import_batches PRIMARY KEY (id),
    CONSTRAINT uq_knowledge_import_batches UNIQUE (source, source_version),
    CONSTRAINT ck_knowledge_import_batches_source CHECK (source IN (
        'menus_csv', 'hidden_rules', 'variant_ingredients', 'ingredient_rules',
        'semie_prior', 'wtable_prior', '10000recipe_prior')),
    CONSTRAINT ck_knowledge_import_batches_version CHECK (source_version ~ '^[0-9a-f]{7,40}$'),
    CONSTRAINT ck_knowledge_import_batches_status CHECK (status IN ('running', 'completed', 'failed')),
    CONSTRAINT ck_knowledge_import_batches_row_count CHECK (row_count >= 0),
    CONSTRAINT ck_knowledge_import_batches_finished_at CHECK (
        (status = 'running' AND finished_at IS NULL) OR (status IN ('completed', 'failed') AND finished_at IS NOT NULL))
);

CREATE TABLE menu_recipe_corpora (
    menu_id          BIGINT       NOT NULL,
    corpus           VARCHAR(20)  NOT NULL,
    n_total          INTEGER      NOT NULL,
    import_batch_id  BIGINT       NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_menu_recipe_corpora PRIMARY KEY (menu_id, corpus),
    CONSTRAINT uq_menu_recipe_corpora_n UNIQUE (menu_id, corpus, n_total),
    CONSTRAINT fk_menu_recipe_corpora_menu FOREIGN KEY (menu_id) REFERENCES menus (id) ON DELETE RESTRICT,
    CONSTRAINT fk_menu_recipe_corpora_batch FOREIGN KEY (import_batch_id) REFERENCES knowledge_import_batches (id) ON DELETE RESTRICT,
    CONSTRAINT ck_menu_recipe_corpora_corpus CHECK (corpus IN ('semie', 'wtable', '10000recipe')),
    CONSTRAINT ck_menu_recipe_corpora_n CHECK (n_total > 0)
);
CREATE INDEX idx_menu_recipe_corpora_batch ON menu_recipe_corpora (import_batch_id);

CREATE TABLE menu_ingredient_priors (
    menu_id        BIGINT       NOT NULL,
    corpus         VARCHAR(20)  NOT NULL,
    ingredient_id  BIGINT       NOT NULL,
    n_total        INTEGER      NOT NULL,
    k_count        INTEGER      NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_menu_ingredient_priors PRIMARY KEY (menu_id, corpus, ingredient_id),
    CONSTRAINT fk_menu_ingredient_priors_corpus FOREIGN KEY (menu_id, corpus, n_total)
        REFERENCES menu_recipe_corpora (menu_id, corpus, n_total) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_menu_ingredient_priors_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id) ON DELETE RESTRICT,
    CONSTRAINT ck_menu_ingredient_priors_k CHECK (k_count >= 1 AND k_count <= n_total)
);
CREATE INDEX idx_menu_ingredient_priors_ingredient ON menu_ingredient_priors (ingredient_id);

