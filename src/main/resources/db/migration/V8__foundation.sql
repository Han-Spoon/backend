-- V8 스키마 v2 기반: 정규화 함수 · 기존 테이블(V1~V7) 제약 보강 · 스캔 카운터 · scan_images 이름 변경
-- ─────────────────────────────────────────────────────────────
-- 0. 함수
-- ─────────────────────────────────────────────────────────────

-- 메뉴·재료명 매칭 정규화. normalize_store_name(V5)과 같은 규칙이지만 변경 격리를 위해 분리한다.
-- ⚠ CREATE OR REPLACE 금지. 바꾸려면 새 함수 + 생성 컬럼 재정의 + REINDEX 를 한 마이그레이션에서 수행.
CREATE FUNCTION normalize_food_name(src text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    RETURN regexp_replace(lower(normalize(src, NFKC)), '[^[:alnum:]]', '', 'g');

-- 태그 배열이 정렬·중복 없음 상태인지. 같은 질문·확정값을 UNIQUE 로 묶으려면 배열 표현이 하나여야 한다.
-- COLLATE "C": AI(Python)의 코드포인트 정렬과 같게 맞춘다.
CREATE FUNCTION is_sorted_distinct(arr varchar[]) RETURNS boolean
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    RETURN arr = ARRAY(SELECT d.x FROM (SELECT DISTINCT x FROM unnest(arr) AS x) AS d ORDER BY d.x COLLATE "C");

DO $food_name_contract$
BEGIN
    IF normalize_food_name('김치 찌개') IS DISTINCT FROM '김치찌개' THEN
        RAISE EXCEPTION 'normalize_food_name contract failed: spacing';
    END IF;
    IF normalize_food_name('■ 김치찌개 –') IS DISTINCT FROM '김치찌개' THEN
        RAISE EXCEPTION 'normalize_food_name contract failed: menu-board decorations';
    END IF;
    IF normalize_food_name('ＢＢＱ 치킨') IS DISTINCT FROM 'bbq치킨' THEN
        RAISE EXCEPTION 'normalize_food_name contract failed: full-width characters';
    END IF;
    IF normalize_food_name('한 스푼! @강남점') IS DISTINCT FROM normalize_store_name('한 스푼! @강남점') THEN
        RAISE EXCEPTION 'normalize_food_name contract failed: parity with normalize_store_name';
    END IF;
    IF NOT is_sorted_distinct(ARRAY['is_fish', 'is_shellfish']::varchar[])
       OR is_sorted_distinct(ARRAY['is_shellfish', 'is_fish']::varchar[])
       OR is_sorted_distinct(ARRAY['is_fish', 'is_fish']::varchar[]) THEN
        RAISE EXCEPTION 'is_sorted_distinct contract failed';
    END IF;
END
$food_name_contract$;

-- ─────────────────────────────────────────────────────────────
-- A. 기존 테이블 변경 (V1~V6)
-- ─────────────────────────────────────────────────────────────

-- A-1. 사용자 · 프로필
ALTER TABLE users
    ALTER COLUMN language_code TYPE VARCHAR(5),
    ADD CONSTRAINT ck_users_language_code CHECK (language_code IN ('ko', 'en', 'ar', 'zh-CN', 'zh-TW', 'ja', 'es'));

ALTER TABLE user_profiles
    ADD CONSTRAINT ck_user_profiles_vegetarian_type CHECK (
        vegetarian_type IS NULL OR vegetarian_type IN ('vegan', 'lacto', 'ovo', 'lacto_ovo', 'pesco')),
    ADD CONSTRAINT ck_user_profiles_religion_type CHECK (
        religion_type IS NULL OR religion_type IN ('halal', 'kosher', 'hindu', 'none'));

-- 알레르기: user_allergies 테이블 → user_profiles.allergy_codes 배열 (2026-10-27). 고정 코드 목록 · 프로필 1개에 붙음 ·
-- 항상 프로필과 같이 읽음 · 통째로 교체 — 태그 배열(ingredients.tag_codes)과 같은 기준.
-- 저장은 접두사 없는 식약처 19종(AllergyCode, 알코올 제외). AI 전송 시 AiProfileMapper 가 is_ 를 붙인다.
-- 기본값을 두지 않는다: 빠뜨린 값이 '알레르기 없음'으로 저장되면 안 된다(FN-safe). 프로필 저장 API 는 hasAllergies 가 필수라
-- 프로필 행이 있으면 응답한 상태이고, 알레르기 행이 없던 기존 프로필은 '없음'으로 응답한 것이다.
-- 기존 행은 uq_user_allergies (user_profile_id, allergy_code) 로 중복이 없으므로 정렬만 한다.
ALTER TABLE user_profiles ADD COLUMN allergy_codes VARCHAR(20)[] NULL;
UPDATE user_profiles p
SET allergy_codes = COALESCE(
        (SELECT array_agg(a.allergy_code ORDER BY a.allergy_code COLLATE "C")
         FROM user_allergies a WHERE a.user_profile_id = p.id),
        '{}');
ALTER TABLE user_profiles
    ALTER COLUMN allergy_codes SET NOT NULL,
    ADD CONSTRAINT ck_user_profiles_allergy_codes CHECK (
        is_sorted_distinct(allergy_codes) AND allergy_codes <@ ARRAY['egg', 'milk', 'buckwheat', 'peanut', 'soybean',
            'wheat', 'mackerel', 'crab', 'shrimp', 'pork', 'peach', 'tomato', 'sulfite', 'walnut', 'chicken', 'beef',
            'squid', 'shellfish', 'pinenut']::varchar[]);
DROP TABLE user_allergies;

-- A-2. 스캔 세션: 위험도별 카운터, 판정 언어, 판정에 쓴 식단, 상태·제목·가게 식별 경로 CHECK, 복합 FK 대상, 이력 목록 키셋 인덱스
-- diet_snapshot: Judge 요청 user_profile 을 스캔 시작 때 그대로 저장한다(2026-11-10). 결과 화면 "내 식단"·직원 응답 뒤 재판정·
--               식사 후기 식단 항목이 모두 이 값을 쓴다. 나중에 프로필을 바꿔도 이 스캔의 판정 근거는 바뀌지 않는다.
--               NULL = 이 컬럼 도입 전(룰엔진) 스캔.
ALTER TABLE scan_sessions
    ADD COLUMN danger_count  INTEGER    NULL,
    ADD COLUMN caution_count INTEGER    NULL,
    ADD COLUMN safe_count    INTEGER    NULL,
    ADD COLUMN locale        VARCHAR(5) NULL,
    ADD COLUMN diet_snapshot JSONB      NULL;

-- 기존 행의 카운터는 menu_analyses 판정으로 채운다. risky_menu_count 는 현재 파이프라인이 쓰므로 V14 에서 지운다.
UPDATE scan_sessions s
SET danger_count  = a.d,
    caution_count = a.c,
    safe_count    = a.sf
FROM (SELECT scan_session_id,
             count(*) FILTER (WHERE risk_level = 'danger')  AS d,
             count(*) FILTER (WHERE risk_level = 'caution') AS c,
             count(*) FILTER (WHERE risk_level = 'safe')    AS sf
      FROM menu_analyses GROUP BY scan_session_id) a
WHERE a.scan_session_id = s.id;

ALTER TABLE scan_sessions
    ADD CONSTRAINT ck_scan_sessions_status CHECK (scan_status IN ('processing', 'completed', 'failed', 'needs_retake')),
    ADD CONSTRAINT ck_scan_sessions_counts CHECK (
        (danger_count IS NULL OR danger_count >= 0)
        AND (caution_count IS NULL OR caution_count >= 0)
        AND (safe_count IS NULL OR safe_count >= 0)
        AND (menu_count IS NULL OR menu_count >= 0)),
    ADD CONSTRAINT ck_scan_sessions_locale CHECK (
        locale IS NULL OR locale IN ('ko', 'en', 'ar', 'zh-CN', 'zh-TW', 'ja', 'es')),
    -- AI 계약 UserProfile 6개 키 그대로 (religion_type·is_vegetarian·vegetarian_type·no_alcohol·allergies·no_spicy)
    ADD CONSTRAINT ck_scan_sessions_diet_snapshot CHECK (
        diet_snapshot IS NULL OR (jsonb_typeof(diet_snapshot) = 'object'
            AND diet_snapshot ?& ARRAY['religion_type', 'is_vegetarian', 'vegetarian_type', 'no_alcohol', 'allergies', 'no_spicy']
            AND diet_snapshot - ARRAY['religion_type', 'is_vegetarian', 'vegetarian_type', 'no_alcohol', 'allergies', 'no_spicy']
                = '{}'::jsonb
            AND jsonb_typeof(diet_snapshot -> 'allergies') = 'array')),
    -- 질문·가게 메뉴가 스캔과 같은 가게임을 복합 FK 로 강제하기 위한 대상 키
    ADD CONSTRAINT uq_scan_sessions_id_store UNIQUE (id, store_id),
    -- 찜이 내 스캔의 메뉴만 가리키도록 하는 복합 FK 대상 키
    ADD CONSTRAINT uq_scan_sessions_id_user UNIQUE (id, user_id);

-- 가게 식별 경로: 가게 상세의 "이 식당 메뉴 스캔"(store_detail) 추가 (2026-11-01). V5 제약을 바꾼다
ALTER TABLE scan_sessions DROP CONSTRAINT ck_scan_sessions_match_method;
ALTER TABLE scan_sessions
    ADD CONSTRAINT ck_scan_sessions_match_method CHECK (store_match_method IS NULL OR store_match_method IN
        ('gps_candidate', 'name_search', 'kakao_fallback', 'store_detail'));

DROP INDEX idx_scan_sessions_user;
CREATE INDEX idx_scan_sessions_user_recent ON scan_sessions (user_id, created_at DESC, id DESC);

-- A-3. 스캔 원본 사진: menu_images → scan_images (새 menu_images 는 표준 메뉴 이미지)
ALTER TABLE menu_images RENAME TO scan_images;
ALTER TABLE scan_images RENAME CONSTRAINT pk_menu_images TO pk_scan_images;
ALTER TABLE scan_images RENAME CONSTRAINT uq_menu_images_session TO uq_scan_images_session;
ALTER TABLE scan_images RENAME CONSTRAINT fk_menu_images_session TO fk_scan_images_session;
ALTER TABLE scan_images
    ADD CONSTRAINT ck_scan_images_source CHECK (source IS NULL OR source IN ('camera', 'upload'));

-- A-4. 저장 카드: text → card_text, 카드 유형 CHECK (덜 맵게 less_spicy 2026-11-01 추가)
ALTER TABLE saved_cards RENAME COLUMN text TO card_text;
ALTER TABLE saved_cards
    ADD CONSTRAINT ck_saved_cards_type CHECK (card_type IN ('order', 'ingredient_check', 'exclude', 'less_spicy')),
    ADD CONSTRAINT ck_saved_cards_text CHECK (jsonb_typeof(card_text) = 'object' AND card_text ? 'ko');

-- A-5. 스캔 기록 보관(scan_records)은 그대로 둔다 (2026-11-15). 화면에 "기록 저장하기"와 식당 없는 스캔의
--      "식당 연결·연결 해제"가 남아 있어 attached_store_* 를 계속 쓴다. feedback 은 후기(meal_reviews)로 옮겨 가 새로 쓰지 않는다.

