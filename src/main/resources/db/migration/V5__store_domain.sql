-- 가게 도메인
-- 마스터: 소상공인시장진흥공단 상가(상권)정보 한식(I201) — 2026-06 기준 전국 359,832건
-- 설계 문서: docs/12-store-domain-erd.svg
--
-- PK 이원화 원칙
--   · 공개 참조 마스터(stores/categories/ksic/batches) = BIGINT IDENTITY
--     → FK 폭을 좁혀 store-scoped 대용량 테이블의 인덱스 비용을 줄이고, 벌크 적재 시 순차 삽입 이점을 얻는다.
--       상가정보는 공개 데이터라 순차 ID 노출로 잃을 것이 없다.
--   · 사용자 귀속 리소스(users/scan_sessions/…) = UUID (V1 그대로)
--     → URL 노출 시 열거 공격 방어.
--
-- 데이터 타입 근거: 전국 CSV 실측 최대 길이
--   상가업소번호 20 · 상호명 32 · 지점명 9 · 도로명주소 34 · 층정보 4 · 행정동코드 8
--   (사용자 제출 가게를 감안해 여유를 둔 값으로 지정)

-- 반경 검색(GiST) / 상호명 유사도(GIN trigram)에 필요.
-- AWS RDS PostgreSQL 16. 세 확장 모두 RDS 지원 목록에 있고, 접속 계정(hanspoon_app)이
-- 마스터 사용자라 rds_superuser 권한으로 CREATE EXTENSION 이 가능하다. shared_preload_libraries 변경 불필요.
CREATE EXTENSION IF NOT EXISTS cube;
CREATE EXTENSION IF NOT EXISTS earthdistance;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 상호명 매칭용 정규화. DB 와 애플리케이션이 반드시 같은 규칙을 써야 하므로 함수로 고정한다.
-- 검색어도 이 함수를 거쳐야 한다:  WHERE s.name_normalized % normalize_store_name(:q)
--
--   · NFKC 정규화로 전각 문자를 반각으로 접는다. 실측에서 'ＣＵ 마트 Ｂ１' 이 '마트' 로,
--     '３６９' 이 빈 문자열로 뭉개지는 사례가 나왔다(전국 3건). 간판·OCR 에 전각이 흔하다.
--   · POSIX alnum 문자군으로 모든 유니코드 문자·숫자를 보존한다. 한글 호환 자모는 NFKC 후
--     현대 한글 자모로 바뀌므로 '가-힣ㄱ-ㅎ' 같은 고정 범위만 허용하면 일부 글자가 유실된다.
--     한자·일본어 가나·CJK 확장 문자도 음식점 상호 검색을 위해 보존한다.
--
-- ⚠ 이 함수를 CREATE OR REPLACE 로 바꾸면 기존 생성 컬럼 값과 새 값의 규칙이 어긋나고
--    trigram 인덱스가 실제 데이터와 불일치한다. 변경 시 컬럼 재계산 + REINDEX 가 함께 필요하다.
CREATE FUNCTION normalize_store_name(src text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    RETURN regexp_replace(lower(normalize(src, NFKC)), '[^[:alnum:]]', '', 'g');

-- 운영 DB의 인코딩·locale 차이로 정규화 결과가 달라지면 마이그레이션 단계에서 즉시 실패시킨다.
DO $normalization_contract$
BEGIN
    IF normalize_store_name('ＣＵ 마트 Ｂ１') IS DISTINCT FROM 'cu마트b1' THEN
        RAISE EXCEPTION 'normalize_store_name contract failed: full-width characters';
    END IF;
    IF normalize_store_name('３６９') IS DISTINCT FROM '369' THEN
        RAISE EXCEPTION 'normalize_store_name contract failed: full-width digits';
    END IF;
    IF normalize_store_name('竹田家') IS DISTINCT FROM '竹田家' THEN
        RAISE EXCEPTION 'normalize_store_name contract failed: CJK characters';
    END IF;
    IF normalize_store_name('스시 さくら') IS DISTINCT FROM '스시さくら' THEN
        RAISE EXCEPTION 'normalize_store_name contract failed: Japanese characters';
    END IF;
    IF length(normalize_store_name('ㄱㅎ')) IS DISTINCT FROM 2 THEN
        RAISE EXCEPTION 'normalize_store_name contract failed: Hangul Jamo';
    END IF;
    IF normalize_store_name('한 스푼! @강남점') IS DISTINCT FROM '한스푼강남점' THEN
        RAISE EXCEPTION 'normalize_store_name contract failed: separators';
    END IF;
END
$normalization_contract$;

-- ─────────────────────────────────────────────────────────────
-- 참조 마스터
-- ─────────────────────────────────────────────────────────────

-- 상권업종 분류 (대2 / 중4 / 소6자리) 자기참조 3계층.
-- 음식 외 대분류까지 247개 전체를 적재한다 — 참조 데이터는 비용이 없고,
-- 일식·중식 확장 시 스키마 변경 없이 stores 적재 범위만 넓히면 되기 때문.
CREATE TABLE store_categories (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY,
    code        VARCHAR(6)  NOT NULL,   -- I2 / I201 / I20101
    name        VARCHAR(60) NOT NULL,
    level       SMALLINT    NOT NULL,   -- 1=대분류 2=중분류 3=소분류
    parent_id   BIGINT      NULL,       -- level 1 은 NULL
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_store_categories PRIMARY KEY (id),
    CONSTRAINT uq_store_categories_code UNIQUE (code),
    CONSTRAINT fk_store_categories_parent FOREIGN KEY (parent_id)
        REFERENCES store_categories (id),
    CONSTRAINT ck_store_categories_level CHECK (level BETWEEN 1 AND 3),
    CONSTRAINT ck_store_categories_root CHECK ((level = 1) = (parent_id IS NULL))
);
CREATE INDEX idx_store_categories_parent ON store_categories (parent_id);

COMMENT ON TABLE  store_categories IS '상권업종 분류 3계층. 소분류(level 3)가 Bayesian store_cluster prior 의 축이 된다.';
COMMENT ON COLUMN store_categories.level IS '1=대분류(2자리) 2=중분류(4자리) 3=소분류(6자리)';

-- 한국표준산업분류(KSIC 10차). 상권업종분류와 독립된 축이라 별도 테이블로 둔다.
CREATE TABLE ksic_codes (
    code        VARCHAR(6)   NOT NULL,
    name        VARCHAR(120) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_ksic_codes PRIMARY KEY (code)
);

-- 분기 갱신 적재 이력. "이 행이 어느 스냅샷에서 왔는가"를 추적해 롤백 판단 근거로 쓴다.
CREATE TABLE store_import_batches (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY,
    source          VARCHAR(20) NOT NULL,   -- sbiz | localdata
    source_version  VARCHAR(10) NOT NULL,   -- '202606'
    row_count       INTEGER     NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'running',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ NULL,
    CONSTRAINT pk_store_import_batches PRIMARY KEY (id),
    CONSTRAINT uq_store_import_batches UNIQUE (source, source_version),
    CONSTRAINT ck_store_import_batches_source CHECK (source IN ('sbiz', 'localdata')),
    CONSTRAINT ck_store_import_batches_status CHECK (status IN ('running', 'completed', 'failed')),
    CONSTRAINT ck_store_import_batches_row_count CHECK (row_count >= 0),
    CONSTRAINT ck_store_import_batches_finished_at CHECK (
        (status = 'running' AND finished_at IS NULL)
        OR (status IN ('completed', 'failed') AND finished_at IS NOT NULL)
    )
);

-- ─────────────────────────────────────────────────────────────
-- 가게 마스터
-- ─────────────────────────────────────────────────────────────
CREATE TABLE stores (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY,

    -- 원천 식별자. 멱등 upsert 키. localdata/user_submitted 출처면 NULL
    -- (PostgreSQL UNIQUE 는 NULL 을 서로 다른 값으로 보므로 다중 NULL 허용).
    sbiz_store_no    VARCHAR(24)  NULL,

    -- 표시 · 매칭
    name             VARCHAR(200) NOT NULL,
    branch_name      VARCHAR(100) NOT NULL DEFAULT '',
    -- 매칭용 정규형. 애플리케이션이 따로 채우지 않도록 생성 컬럼으로 둔다.
    name_normalized  VARCHAR(200) GENERATED ALWAYS AS (normalize_store_name(name)) STORED,

    -- 분류
    -- 공공데이터 행은 항상 분류가 있지만, 신규 사용자 제보는 검증 전까지 분류를 모를 수 있다.
    category_id      BIGINT       NULL,
    ksic_code        VARCHAR(6)   NULL,   -- 원천 결측 존재(전국 한식 343건)

    -- 위치. 행정동은 코드만 보존한다 — 행정동'명'을 함께 저장하지 않으므로 이행 종속이 없고,
    -- 나중에 regions 테이블이 필요해지면 재적재 없이 조인만 붙이면 된다.
    admin_dong_code  VARCHAR(8)   NOT NULL DEFAULT '',
    road_address     VARCHAR(300) NOT NULL DEFAULT '',
    floor_info       VARCHAR(20)  NOT NULL DEFAULT '',   -- 결측 48% 이나 동일좌표 다중매장 구분 단서
    lat              DOUBLE PRECISION NOT NULL,
    lng              DOUBLE PRECISION NOT NULL,

    -- 상태 · 출처. 분기 스냅샷에서 사라졌다는 사실만으로 실제 폐업을 단정하지 않는다.
    status           VARCHAR(20)  NOT NULL DEFAULT 'active',   -- active | inactive
    origin           VARCHAR(20)  NOT NULL,
    inactive_at      TIMESTAMPTZ  NULL,
    -- 공공데이터 수록 여부와 서비스의 검증 완료는 다른 개념이다. 실제 검증 전에는 NULL.
    verified_at      TIMESTAMPTZ  NULL,
    submitted_by     UUID         NULL,   -- origin='user_submitted' 인 경우의 제보자
    last_batch_id    BIGINT       NULL,

    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_stores PRIMARY KEY (id),
    CONSTRAINT uq_stores_sbiz_no UNIQUE (sbiz_store_no),
    CONSTRAINT fk_stores_category FOREIGN KEY (category_id)
        REFERENCES store_categories (id),
    CONSTRAINT fk_stores_ksic FOREIGN KEY (ksic_code)
        REFERENCES ksic_codes (code),
    CONSTRAINT fk_stores_batch FOREIGN KEY (last_batch_id)
        REFERENCES store_import_batches (id),
    CONSTRAINT fk_stores_submitter FOREIGN KEY (submitted_by)
        REFERENCES users (id) ON DELETE SET NULL,
    -- 원천 실측 이상치 0건. 사용자 제출 가게의 오입력을 막는 방어선.
    CONSTRAINT ck_stores_lat CHECK (lat BETWEEN 33 AND 39),
    CONSTRAINT ck_stores_lng CHECK (lng BETWEEN 124 AND 132),
    CONSTRAINT ck_stores_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_stores_name_normalized CHECK (name_normalized <> ''),
    CONSTRAINT ck_stores_status CHECK (status IN ('active', 'inactive')),
    CONSTRAINT ck_stores_origin CHECK (origin IN ('sbiz', 'localdata', 'user_submitted')),
    CONSTRAINT ck_stores_inactive_at CHECK ((status = 'inactive') = (inactive_at IS NOT NULL)),
    -- 출처별 식별자·배치 관계를 DB에서도 강제해 잘못 조합된 가게 행을 막는다.
    CONSTRAINT ck_stores_sbiz_identity CHECK ((origin = 'sbiz') = (sbiz_store_no IS NOT NULL)),
    CONSTRAINT ck_stores_batch_origin CHECK (
        (origin IN ('sbiz', 'localdata')) = (last_batch_id IS NOT NULL)
    ),
    CONSTRAINT ck_stores_submitter_origin CHECK (submitted_by IS NULL OR origin = 'user_submitted'),
    CONSTRAINT ck_stores_category_origin CHECK (category_id IS NOT NULL OR origin = 'user_submitted')
);

-- 반경 후보 검색. status 동등조건을 부분 인덱스 조건으로 흡수해 스캔 대상을 영업중 행으로 한정.
CREATE INDEX idx_stores_geo_active ON stores USING gist (ll_to_earth(lat, lng))
    WHERE status = 'active';
-- 상호명 유사도 매칭(실측: 상호명 단독으로는 고유율 83% 라 좌표와 병행 필수).
CREATE INDEX idx_stores_name_trgm ON stores USING gin (name_normalized gin_trgm_ops)
    WHERE status = 'active';
CREATE INDEX idx_stores_category  ON stores (category_id) WHERE status = 'active';
CREATE INDEX idx_stores_batch     ON stores (last_batch_id);

COMMENT ON TABLE  stores IS '가게 마스터. 상가정보 한식(I201) 기반, 분기 스냅샷을 sbiz_store_no 기준으로 멱등 upsert.';
COMMENT ON COLUMN stores.status IS '데이터 소스 기준 노출 상태. inactive는 실제 폐업 확정이 아니라 최신 스냅샷 미수록을 뜻한다.';
COMMENT ON COLUMN stores.verified_at IS '서비스가 사업자·관리자 검증을 완료한 시각. 공공데이터 수록만으로 채우지 않는다.';
COMMENT ON COLUMN stores.origin IS '레코드 출처. 이 스캔에서 어떻게 식별했는지(match_method)와는 다른 축이다.';
COMMENT ON COLUMN stores.name_normalized IS '매칭 전용 정규형(생성 컬럼). 표시에는 name 을 쓸 것. 검색어도 normalize_store_name() 을 거쳐야 한다.';

-- ─────────────────────────────────────────────────────────────
-- 별칭 — 두 종류를 분리한다.
--   가게별 별칭은 store 에 종속되지만, 브랜드 표기 변형(서브웨이 ↔ 써브웨이)은 특정 가게와 무관.
--   후자를 store_aliases 에 넣으면 같은 브랜드 지점 수만큼 행이 복제되어 삽입·수정 이상이 생김.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE store_aliases (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY,
    store_id          BIGINT       NOT NULL,
    alias             VARCHAR(200) NOT NULL,
    alias_normalized  VARCHAR(200) GENERATED ALWAYS AS (normalize_store_name(alias)) STORED,
    source            VARCHAR(20)  NOT NULL DEFAULT 'manual',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_store_aliases PRIMARY KEY (id),
    CONSTRAINT fk_store_aliases_store FOREIGN KEY (store_id)
        REFERENCES stores (id) ON DELETE CASCADE,
    CONSTRAINT ck_store_aliases_source CHECK (source IN ('manual', 'user_reported')),
    CONSTRAINT ck_store_aliases_alias CHECK (btrim(alias) <> '' AND alias_normalized <> '')
);
CREATE UNIQUE INDEX uq_store_aliases ON store_aliases (store_id, alias_normalized);
CREATE INDEX idx_store_aliases_trgm ON store_aliases USING gin (alias_normalized gin_trgm_ops);

-- 전역 표기 변형 사전. 검색어를 대표표기로 치환한 뒤 stores 를 조회한다. store FK 없음(앱 레벨 조회).
CREATE TABLE brand_aliases (
    id                    BIGINT       GENERATED ALWAYS AS IDENTITY,
    variant_normalized    VARCHAR(200) NOT NULL,
    canonical_normalized  VARCHAR(200) NOT NULL,
    note                  VARCHAR(200) NOT NULL DEFAULT '',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_brand_aliases PRIMARY KEY (id),
    CONSTRAINT uq_brand_aliases_variant UNIQUE (variant_normalized),
    CONSTRAINT ck_brand_aliases_values CHECK (
        variant_normalized <> ''
        AND canonical_normalized <> ''
        AND variant_normalized = normalize_store_name(variant_normalized)
        AND canonical_normalized = normalize_store_name(canonical_normalized)
        AND variant_normalized <> canonical_normalized
    )
);

-- ─────────────────────────────────────────────────────────────
-- 외부 지도 서비스 참조
-- 컬럼이 아니라 테이블로 분리한 이유:
-- kakao에서 저장이 허용되는 필드만 담는 테이블로 격리해 약관 경계를 스키마에 남기기 위함.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE store_external_refs (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY,
    store_id      BIGINT       NOT NULL,
    provider      VARCHAR(20)  NOT NULL,
    external_id   VARCHAR(64)  NOT NULL,             -- 카카오 place_id
    external_url  VARCHAR(512) NOT NULL DEFAULT '',  -- 카카오 place_url
    linked_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_store_external_refs PRIMARY KEY (id),
    -- 양방향 1:1 — 같은 외부 장소가 두 가게에 붙거나, 한 가게에 같은 제공자가 둘 붙는 것을 막는다.
    CONSTRAINT uq_store_external_refs_ext   UNIQUE (provider, external_id),
    CONSTRAINT uq_store_external_refs_store UNIQUE (store_id, provider),
    CONSTRAINT fk_store_external_refs_store FOREIGN KEY (store_id)
        REFERENCES stores (id) ON DELETE CASCADE,
    CONSTRAINT ck_store_external_refs_provider CHECK (provider IN ('kakao')),
    CONSTRAINT ck_store_external_refs_external_id CHECK (btrim(external_id) <> '')
);

COMMENT ON TABLE store_external_refs IS
    '외부 지도 서비스 참조. 카카오 약관상 place_id/place_url 만 저장 허용 — 상호명·주소·좌표·전화번호 저장 금지.';

-- ─────────────────────────────────────────────────────────────
-- 스캔 세션 연결
-- 기존 운영 스캔은 가게 정보 없이 생성됐으므로 세 컬럼을 NULL 허용.
--
-- 사용자 GPS 원본 컬럼은 개인위치정보(위치정보법) 위반에 해당해 의도적으로 두지 않음.
-- 후보 조회에만 쓰고 결과(store_id)만 남김.
-- ─────────────────────────────────────────────────────────────
ALTER TABLE scan_sessions
    ADD COLUMN store_id            BIGINT       NULL,
    ADD COLUMN store_name_snapshot VARCHAR(200) NULL,
    ADD COLUMN store_match_method  VARCHAR(20)  NULL;

ALTER TABLE scan_sessions
    -- RESTRICT: 스캔 이력이 참조하는 가게는 삭제 불가. 검색 제외는 stores.status 비활성 전이로 표현한다.
    ADD CONSTRAINT fk_scan_sessions_store FOREIGN KEY (store_id)
        REFERENCES stores (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_scan_sessions_match_method CHECK (store_match_method IS NULL OR store_match_method IN
        ('gps_candidate', 'name_search', 'kakao_fallback', 'user_created')),
    ADD CONSTRAINT ck_scan_sessions_store_context CHECK (
        (store_id IS NULL AND store_name_snapshot IS NULL AND store_match_method IS NULL)
        OR
        (store_id IS NOT NULL
            AND store_name_snapshot IS NOT NULL
            AND btrim(store_name_snapshot) <> ''
            AND store_match_method IS NOT NULL)
    );

CREATE INDEX idx_scan_sessions_store ON scan_sessions (store_id) WHERE store_id IS NOT NULL;

COMMENT ON COLUMN scan_sessions.store_name_snapshot IS
    '스캔 시점 상호명 동결. 서버가 stores.name에서 복사하며 클라이언트 입력을 신뢰하지 않는다.';
COMMENT ON COLUMN scan_sessions.store_id IS
    '가게 도입 전 레거시 스캔만 NULL. 신규 store-scoped AI 호출은 값이 확정된 세션에만 허용한다.';
COMMENT ON COLUMN scan_sessions.store_match_method IS
    '가게 식별 경로. 의사결정 근거로 사용하지 않는 관측용 메타데이터이며 store context와 함께 저장한다.';
