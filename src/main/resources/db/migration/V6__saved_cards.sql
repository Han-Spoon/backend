-- 자주 쓰는 카드(사장님 소통 카드 저장). 문구 생성은 FE, 서버는 저장/목록/삭제만 담당.
-- 템플릿형(order/ingredient_check/exclude)은 ingredients(hit 코드)만 저장 → FE가 언어별 문장 재렌더.
-- AI 사장님 질문(owner_question)은 재생성 불가라 완성 문구 text + flag 저장.
CREATE TABLE saved_cards (
    id              UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    card_type       VARCHAR(20)  NOT NULL,   -- order | ingredient_check | exclude | owner_question
    menu_name_ko    VARCHAR(255) NOT NULL,
    ingredients     JSONB        NULL,       -- string[] hit 코드(템플릿형). FE가 i18n으로 문장 조합
    text            JSONB        NULL,       -- {ko, en, ar} (owner_question 전용, AI 완성 문구)
    flag            VARCHAR(50)  NULL,       -- owner_question 애매 사유(예: has_unclear_broth)
    scan_session_id UUID         NULL,       -- 출처 스캔(선택)
    last_saved_at   TIMESTAMPTZ  NOT NULL,   -- 중복 재저장 시 갱신 → 목록 정렬(최신 저장순) 기준
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_saved_cards PRIMARY KEY (id),
    CONSTRAINT fk_saved_cards_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_cards_scan FOREIGN KEY (scan_session_id)
        REFERENCES scan_sessions (id) ON DELETE SET NULL
);

CREATE INDEX idx_saved_cards_user ON saved_cards (user_id, last_saved_at DESC);
