-- V14 (보류) 축소 단계 — API 명세 V2 · Prepare/Judge 연동 코드와 같은 배포에서만 적용한다.
-- 적용 전 조건: 백엔드가 Judge 결과를 staff_cards 배열·source_index 로 저장하고 AI 첫 판정(ai_risk_level)을 함께 기록하며,
--             영어 메뉴명 대신 menus.name_i18n·번역 캐시를 쓰고, is_spicy 를 '감지 시 true, 아니면 NULL' 로 기록하고,
--             이력 API 가 danger/caution 카운터를 쓰고, 기록 제목을 30자로 검증하는 상태.
-- 이 파일을 src/main/resources/db/migration 으로 옮기면 Flyway 가 적용한다. 배포를 나눠야 하면 문단 단위로 파일을 나눈다.

-- scan_sessions: risky_menu_count 제거 (V8 부터 danger/caution/safe 카운터를 함께 기록)
ALTER TABLE scan_sessions DROP COLUMN risky_menu_count;

-- 기록 제목: 사용자가 고친 경우만 저장(미편집 NULL). 30자 이하, 공백만은 금지 (API 명세 V2 No 18, 마이페이지 연필 아이콘)
ALTER TABLE scan_sessions
    ADD CONSTRAINT ck_scan_sessions_title CHECK (title IS NULL OR (btrim(title) <> '' AND char_length(title) <= 30));

-- menu_analyses: 표시 필드 정리, 태그·카드 이름
ALTER TABLE menu_analyses RENAME COLUMN menu_name_ko TO display_name;
ALTER TABLE menu_analyses RENAME COLUMN description_ko TO description;
ALTER TABLE menu_analyses RENAME COLUMN hit_tags TO matched_tags;
ALTER TABLE menu_analyses RENAME COLUMN owner_card TO staff_cards;

-- 기존 행 이전: OCR 순서 복사, 단수 카드를 배열로, false 매운맛 → NULL, 기존 판정을 AI 첫 판정으로 복사(직원 응답 전이라 같다)
-- display_order 를 그대로 복사하면 한 스캔 안에서 값이 겹칠 때 UNIQUE 위반 → 스캔별 순번으로 채운다
UPDATE menu_analyses m SET source_index = r.rn
FROM (SELECT id, row_number() OVER (PARTITION BY scan_session_id ORDER BY display_order NULLS LAST, created_at, id) - 1 AS rn
      FROM menu_analyses WHERE source_index IS NULL) r
WHERE m.id = r.id;
UPDATE menu_analyses
SET staff_cards = CASE
        WHEN staff_cards IS NULL THEN '[]'::jsonb
        WHEN jsonb_typeof(staff_cards) = 'array' THEN staff_cards
        ELSE jsonb_build_array(staff_cards)
    END;
UPDATE menu_analyses SET is_spicy = NULL WHERE is_spicy = false;
UPDATE menu_analyses SET ai_risk_level = risk_level WHERE ai_risk_level IS NULL AND risk_level IS NOT NULL;

ALTER TABLE menu_analyses
    DROP COLUMN menu_name_en,
    DROP COLUMN description_en,
    DROP COLUMN image_url,
    ALTER COLUMN staff_cards SET DEFAULT '[]'::jsonb,
    ALTER COLUMN staff_cards SET NOT NULL,
    ADD CONSTRAINT ck_menu_analyses_staff_cards CHECK (jsonb_typeof(staff_cards) = 'array'),
    ADD CONSTRAINT ck_menu_analyses_matched_tags CHECK (matched_tags IS NULL OR jsonb_typeof(matched_tags) = 'array'),
    ADD CONSTRAINT ck_menu_analyses_message CHECK (message IS NULL OR jsonb_typeof(message) = 'object'),
    ADD CONSTRAINT ck_menu_analyses_is_spicy CHECK (is_spicy IS NULL OR is_spicy),
    ADD CONSTRAINT ck_menu_analyses_risk_level CHECK (risk_level IS NULL OR risk_level IN ('danger', 'caution', 'safe')),
    -- 판정이 있으면 AI 첫 판정도 있다 (현재 룰엔진 코드는 ai_risk_level 을 쓰지 않으므로 V14 에서 적용)
    ADD CONSTRAINT ck_menu_analyses_ai_risk_pair CHECK ((risk_level IS NULL) = (ai_risk_level IS NULL));

-- 코멘트: 이름이 바뀌거나 의미가 확정된 컬럼
COMMENT ON COLUMN menu_analyses.display_name IS '메뉴판 표기 | Prepare display_name 스냅샷 (기존 menu_name_ko)';
COMMENT ON COLUMN menu_analyses.description IS '메뉴판 설명 | Prepare description 스냅샷 (기존 description_ko)';
COMMENT ON COLUMN menu_analyses.matched_tags IS '일치 태그 | 사용자 금지 태그와 겹친 is_* 배열 (기존 hit_tags)';
COMMENT ON COLUMN menu_analyses.staff_cards IS '직원 소통 카드 | 배열, 기본 []; card_id 로 staff_questions 행과 연결; 문구는 ko + 사용자 언어 (기존 owner_card)';
COMMENT ON COLUMN menu_analyses.is_spicy IS '매운맛 관측 | OCR risk_tag_observations 감지 시 true, 아니면 NULL; false 저장 금지';
COMMENT ON COLUMN menu_analyses.message IS '사용자 안내문 | {verdict, reason, notes[]} 객체, scan_sessions.locale 한 언어; 레거시는 {ko,en,ar}';
