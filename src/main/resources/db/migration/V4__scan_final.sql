-- Scan final result (V4)
-- ai_result(최종 단계) 출력의 표시용 필드 추가. menu_analyses 에 사용자 안내문/사장님 소통 카드 저장.
-- hits 는 기존 hit_tags 컬럼 재사용. 기존 룰 중간 컬럼(triggered_flags 등)은 유지(디버깅용).

ALTER TABLE menu_analyses
    ADD COLUMN message    JSONB NULL,   -- {ko, en, ar} 사용자 안내문
    ADD COLUMN owner_card JSONB NULL;   -- {menu_name, flag, question:{ko,en,ar}} 사장님 소통 카드
