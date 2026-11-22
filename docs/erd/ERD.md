# 스키마 v2 ERD

- **원본은 Flyway** `src/main/resources/db/migration/V1~V14`(+V13.1). 이 문서와 [`ERD.svg`](ERD.svg)는 **V14 까지 적용한 상태**를 2026-11-15 DB 카탈로그에서 만든 스냅샷이다. 스키마를 바꾸면 해당 테이블 부분을 손으로 고친다
- 결정 근거·구현 규칙·배포·미결: [`DECISIONS.md`](DECISIONS.md)
- 컬럼 설명 형식: `논리명 | N(NOT NULL)/Y(NULL 허용) | 비고`. 비고 끝 `[#n]`은 AI 백엔드 변경 요청 번호(AI 레포 `docs/caution-db-schema.md` §6), `[V5]`는 기존 마이그레이션 번호

| 규모 | 값 |
|---|---|
| 테이블 | 51 (+ VIEW 1) |
| 컬럼 | 474 |
| 관계(FK) | 80 |
| UNIQUE (제약 + 부분 UNIQUE 인덱스) | 44 |
| CHECK (테이블) | 181 |
| 인덱스 (PK·UNIQUE 포함) | 154 |

| 색 | 도메인 | 테이블 |
|---|---|---|
| 파랑 | 사용자·약관 | users, user_auth_identities, user_sessions, user_profiles, user_roles, user_consent_decisions |
| 초록 | 스캔·결과·찜 | scan_sessions, scan_images, scan_records, scan_ai_results, menu_analyses, saved_cards, favorite_menus, text_translations |
| 보라 | 가게·가게 메뉴·동네 | store_categories, ksic_codes, store_import_batches, stores, brand_aliases, store_external_refs, store_menus, areas |
| 주황 | 전역 지식 | menu_categories, menus, menu_aliases, ingredients, ingredient_aliases, ingredient_compositions, recipe_ingredients, knowledge_import_batches, menu_recipe_corpora, menu_ingredient_priors, menu_ingredient_cache |
| 빨강 | 직원 응답·가게 증거 | ingredient_confirmations, flag_confirmations, staff_questions, ingredient_evidence_log, source_reliability (+ VIEW ingredient_risk_scores) |
| 분홍 | 운영·검토·명령 | web_search_sources, knowledge_review_items, knowledge_review_sources, ai_command_inbox, admin_audit_logs |
| 연두 | 이미지·큐레이션 | image_assets, curation_articles, curation_article_translations, curation_article_menus |
| 청록 | 식사 후기·신고 | meal_reviews, meal_review_items, meal_review_answers, meal_review_reports |

핵심 결정 (근거는 [`DECISIONS.md`](DECISIONS.md) §2):

- **직원 응답**: 사장님 페이지는 없다. 손님 화면의 질문 카드에 가게 직원이 버튼(있어요/없어요/모름)으로 답하면 가게 메뉴(`store_menus`) 기준 확정값에 바로 저장한다. AI Judge 는 다시 부르지 않는다. "있어요"이고 내 식단과 겹치면 서버가 그 메뉴를 위험으로만 올리고(AI 첫 판정 `ai_risk_level` 은 보존), 안전 판정은 다음 스캔의 Judge 가 낸다. 잘못 누르면 같은 질문에서 다시 답한다
- **식단 스냅샷**: 스캔 시작 때 Judge 에 보낸 식단(`scan_sessions.diet_snapshot`)을 저장해 결과 화면 '내 식단'·후기 식단 항목의 기준으로 쓴다
- **웹 레시피**: 검색 1회 = 검토 묶음 1개. 이상 신호(`review_notes`)가 없으면 자동 승인, 있으면 관리자가 묶음 단위 예/아니오. 같은 메뉴명 승인 레시피 URL 30개면 백엔드가 `menus` 에 자동 등록
- **배열로 둔 것**: 재료 태그 `tag_codes`, 사용자 알레르기 `user_profiles.allergy_codes`(식약처 19종, 기본값 없음). 정렬·중복 없음 CHECK
- **VIEW**: 가게 재료 확률 `ingredient_risk_scores` (변형 근거만 합산)
- **로그 테이블**: `ai_command_inbox`·`admin_audit_logs` 는 FK 없이 ID 값만 보관. Judge 원본 `judge_raw` 는 90일 보관
- **큐레이션**: 작성·게시·공개/비공개·삭제만. 한국어 원문으로 6개 언어 번역을 한 번에 저장, 언어별 검수 없음. 태그·메뉴 연결은 추천 계산용
- **약관 동의**: 스키마 v2 가 만들지 않는다. 운영 Flyway `V7` 의 `user_consent_decisions`(문서 4종 × 버전, 결정 이력)를 쓰고 원문은 `legal/{버전}.json`
- **식사 후기**: 후기 1건 = 한 사람이 한 식당에 남긴 글(식당 필수, 스캔 연결은 선택·스캔당 1건) → 먹은 메뉴 줄(스캔 메뉴 또는 직접 입력) → 내 식단 항목별 응답(있었어요/없었어요/잘 모르겠어요). 후기 수·카드·수정·삭제·신고·숨김 모두 후기 단위. 신고는 사유 4종, 저장만 하고 숨기지 않는다
- **메뉴 찜**: 내 스캔의 메뉴만(복합 FK), 스캔 기록을 지우면 함께 삭제
- **스캔 기록 보관**: `scan_records` 유지 — 화면의 "기록 저장하기"와 식당 없는 스캔의 식당 연결·해제
- **동네**: 중심 좌표 + 반경. 반경 안 가게가 자동으로 그 동네(연결 테이블 없음)
- **계산으로 두는 값**: 사진 만료(S3 90일 수명주기), 큐레이션 읽기 시간(본문 길이), 후기 수(집계). 저장하지 않는다

관계선: 실선 `--` = 식별 관계(FK 가 자식 PK 의 일부), 점선 `..` = 비식별 관계. `||` = 반드시 하나, `|o` = 없거나 하나, `o{` = 0개 이상, `o|` = 0~1개(자식 쪽 UNIQUE).

## 다이어그램
```mermaid
erDiagram
    direction LR

    users ||..o{ user_auth_identities : "사용자 ID"
    users ||..o{ user_sessions : "사용자 ID"
    users ||..o| user_profiles : "사용자 ID"
    users ||--o{ user_roles : "사용자 ID"
    users ||..o{ user_consent_decisions : "사용자 ID"
    stores |o..o{ scan_sessions : "스캔 시점 가게 ID"
    users ||..o{ scan_sessions : "사용자 ID"
    scan_sessions ||..o| scan_images : "스캔 ID"
    stores |o..o{ scan_records : "사후 연결 가게 ID"
    scan_sessions ||--o| scan_records : "스캔 ID"
    scan_sessions ||--o| scan_ai_results : "스캔 ID"
    menus |o..o{ menu_analyses : "판정 메뉴 ID"
    scan_sessions |o..o{ menu_analyses : "스캔 ID (복합 FK)"
    scan_sessions ||..o{ menu_analyses : "스캔 ID"
    store_menus |o..o{ menu_analyses : "가게 메뉴 ID (복합 FK)"
    menus |o..o{ saved_cards : "표준 메뉴 ID"
    scan_sessions |o..o{ saved_cards : "출처 스캔 ID"
    store_menus |o..o{ saved_cards : "가게 메뉴 ID"
    users ||..o{ saved_cards : "사용자 ID"
    menu_analyses ||..o{ favorite_menus : "스캔 ID (복합 FK)"
    scan_sessions ||..o{ favorite_menus : "스캔 ID (복합 FK)"
    store_categories |o..o{ store_categories : "상위 분류 ID"
    store_import_batches ||..o{ stores : "최근 적재 배치 ID"
    store_categories ||..o{ stores : "업종 분류 ID"
    image_assets |o..o{ stores : "대표 이미지 ID"
    ksic_codes |o..o{ stores : "KSIC 코드"
    stores ||..o{ store_external_refs : "가게 ID"
    image_assets |o..o{ store_menus : "대표 이미지 ID"
    scan_sessions |o..o{ store_menus : "마지막 관측 스캔"
    menus |o..o{ store_menus : "표준 메뉴 ID"
    stores ||..o{ store_menus : "가게 ID"
    menus |o..o{ menus : "원본 메뉴 ID"
    menu_categories |o..o{ menus : "메뉴 분류 ID"
    image_assets |o..o{ menus : "대표 이미지 ID"
    menus ||..o{ menu_aliases : "메뉴 ID"
    ingredients ||..o{ ingredient_aliases : "재료 ID"
    ingredients ||--o{ ingredient_compositions : "하위 재료 ID"
    ingredients ||--o{ ingredient_compositions : "상위 재료 ID"
    ingredients ||--o{ recipe_ingredients : "재료 ID"
    menus ||--o{ recipe_ingredients : "메뉴 ID"
    knowledge_import_batches ||..o{ menu_recipe_corpora : "적재 배치 ID"
    menus ||--o{ menu_recipe_corpora : "메뉴 ID"
    menu_recipe_corpora ||..o{ menu_ingredient_priors : "코퍼스 (복합 FK)"
    ingredients ||--o{ menu_ingredient_priors : "재료 ID"
    menus ||--o| menu_ingredient_cache : "메뉴 ID"
    ingredients ||..o{ ingredient_confirmations : "재료 ID"
    store_menus ||..o{ ingredient_confirmations : "가게 메뉴 ID (복합 FK)"
    store_menus ||..o{ flag_confirmations : "가게 메뉴 ID (복합 FK)"
    ingredient_confirmations |o..o{ staff_questions : "반영 확정 ID (복합 FK)"
    flag_confirmations |o..o{ staff_questions : "반영 플래그 확정 ID (복합 FK)"
    ingredients |o..o{ staff_questions : "재료 ID"
    menus |o..o{ staff_questions : "표준 메뉴 ID"
    scan_sessions |o..o{ staff_questions : "발생 스캔 ID (복합 FK)"
    store_menus ||..o{ staff_questions : "가게 메뉴 ID (복합 FK)"
    ingredients ||..o{ ingredient_evidence_log : "재료 ID"
    menus ||..o{ ingredient_evidence_log : "메뉴 ID"
    knowledge_review_items ||..o{ ingredient_evidence_log : "검토 항목 ID"
    stores ||..o{ ingredient_evidence_log : "가게 ID"
    ingredient_evidence_log |o..o{ source_reliability : "마지막 반영 증거 ID"
    menus |o..o{ knowledge_review_items : "변형 원본 메뉴 ID"
    menus |o..o{ knowledge_review_items : "결과 메뉴 ID"
    users |o..o{ knowledge_review_items : "검토자 ID"
    scan_sessions |o..o{ knowledge_review_items : "발견 스캔 ID"
    stores ||..o{ knowledge_review_items : "발견 가게 ID"
    web_search_sources |o..o{ knowledge_review_items : "웹 원본 ID"
    knowledge_review_items ||..o{ knowledge_review_sources : "검토 묶음 ID"
    users |o..o{ image_assets : "등록자 ID"
    image_assets |o..o{ curation_articles : "대표 이미지 ID"
    users |o..o{ curation_articles : "작성자 ID"
    curation_articles ||--o{ curation_article_translations : "글 ID"
    curation_articles ||--o{ curation_article_menus : "글 ID"
    menus ||--o{ curation_article_menus : "메뉴 ID"
    scan_sessions |o..o{ meal_reviews : "스캔 ID (복합 FK)"
    stores ||..o{ meal_reviews : "가게 ID"
    users ||..o{ meal_reviews : "작성자 ID"
    menu_analyses |o..o{ meal_review_items : "스캔 메뉴"
    meal_reviews ||..o{ meal_review_items : "후기 ID"
    meal_review_items ||--o{ meal_review_answers : "후기 줄 ID"
    users ||..o{ meal_review_reports : "신고자 ID"
    meal_reviews ||..o{ meal_review_reports : "신고한 후기"

    users["사용자 (users)"] {
        UUID id PK "사용자 ID | N | 애플리케이션 생성"
        VARCHAR(255) email UK "이메일 | N | 전체 유일"
        VARCHAR(100) nickname "닉네임 | N | 표시명"
        VARCHAR(5) language_code "언어 코드 | N | 기본 en; ko/en/ar/zh-CN/zh-TW/ja/es (CHECK) [#19]"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    user_auth_identities["사용자 인증 식별자 (user_auth_identities)"] {
        UUID id PK "식별자 ID | N | 애플리케이션 생성"
        UUID user_id FK "사용자 ID | N | users.id; 삭제 시 CASCADE"
        VARCHAR(10) provider UK "인증 제공자 | N | 기본 google; provider_id와 복합 유일"
        VARCHAR(255) provider_id UK "제공자 사용자 ID | N | provider와 복합 유일"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    user_sessions["사용자 로그인 세션 (user_sessions)"] {
        UUID id PK "세션 ID | N | 애플리케이션 생성"
        UUID user_id FK "사용자 ID | N | users.id; 삭제 시 CASCADE"
        VARCHAR(255) refresh_token_hash UK "리프레시 토큰 해시 | N | SHA-256; 유일 인덱스"
        TIMESTAMPTZ expires_at "만료 시각 | N | 토큰 유효기간"
        TIMESTAMPTZ revoked_at "폐기 시각 | Y | 로그아웃 및 강제 폐기"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
    }

    user_profiles["사용자 식단 프로필 (user_profiles)"] {
        UUID id PK "프로필 ID | N | 애플리케이션 생성"
        UUID user_id FK,UK "사용자 ID | N | users.id; 사용자별 최대 1개; CASCADE"
        VARCHAR(2) nationality "국적 코드 | Y | ISO 3166-1 alpha-2"
        BOOLEAN is_first_time_korean_food "한식 첫 경험 여부 | Y | 미응답 허용"
        BOOLEAN is_vegetarian "채식 여부 | Y | 미응답 허용"
        VARCHAR(50) vegetarian_type "채식 유형 | Y | vegan/lacto/ovo/lacto_ovo/pesco"
        VARCHAR(50) religion_type "종교 식이 유형 | Y | halal/kosher/hindu/none; none=해당 없음(응답), NULL=미응답; AI 전송 시 none→null"
        BOOLEAN no_spicy "매운맛 회피 | Y | 미응답 허용"
        BOOLEAN no_alcohol "알코올 회피 | Y | 미응답 허용"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
        VARCHAR_ARRAY allergy_codes "알레르기 코드 | N | VARCHAR(20)[]; 식약처 19종 배열, 접두사 없음(egg/pinenut 등), 정렬·중복 없음(CHECK); 빈 배열=없음으로 응답; 기본값 없음(FN-safe); AI 전송 시 is_ 추가; is_fish/is_duck/is_alcohol 은 재료 태그 전용 (기존 user_allergies 테이블 통합)"
    }

    user_roles["사용자 역할 (user_roles)"] {
        UUID user_id PK,FK "사용자 ID | N | users.id; CASCADE"
        VARCHAR(20) role PK "역할 | N | admin"
        TIMESTAMPTZ created_at "부여 시각 | N | 기본 now()"
    }

    user_consent_decisions["약관 동의 이력 (user_consent_decisions)"] {
        UUID id PK "동의 결정 ID | N | UUID"
        UUID user_id FK "사용자 ID | N | users FK CASCADE"
        VARCHAR(32) document_id "약관 문서 | N | service/privacy/sensitive/marketing (CHECK); 원문은 legal/{버전}.json"
        VARCHAR(32) document_version "약관 버전 | N | 원문 파일의 버전(예: 2026-11-14)"
        BOOLEAN accepted "동의 여부 | N | true=동의, false=거부·철회; 다시 제출하면 행 추가(이력)"
        TIMESTAMPTZ decided_at "결정 시각 | N | 기본 now(); 문서별 최신 행이 현재 상태"
    }

    scan_sessions["메뉴판 스캔 세션 (scan_sessions)"] {
        UUID id PK,UK "스캔 ID | N | 애플리케이션 생성; AI scan_session_id"
        UUID user_id FK,UK "사용자 ID | N | users.id; CASCADE; (id, user_id) 복합 유일(찜 복합 FK 대상)"
        VARCHAR(255) title "기록 제목 | Y | 사용자가 고친 경우만(미편집 NULL, 앱이 스캔 시각으로 표시); 30자 이하·공백만 금지(V14 CHECK)"
        INTEGER menu_count "인식 메뉴 수 | Y | 분석 후 기록"
        VARCHAR(20) scan_status "스캔 상태 | N | processing/completed/failed/needs_retake"
        TIMESTAMPTZ scanned_at "분석 완료 시각 | Y | 완료 전 NULL"
        JSONB retake_reasons "재촬영 사유 | Y | OCR 사유 배열"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
        VARCHAR(512) storage_key "S3 객체 키 | Y | [V2] user_id와 부분 복합 유일"
        VARCHAR(64) failure_code "실패 코드 | Y | [V2] 실패 원인"
        BIGINT lock_version "낙관적 락 버전 | N | [V2] 기본 0"
        JSONB retake_suggestions "재촬영 제안 | Y | [V3] OCR 개선 안내"
        BIGINT store_id FK,UK "스캔 시점 가게 ID | Y | [V5] stores.id; RESTRICT; 불변; (id, store_id) 복합 유일(질문·스캔 메뉴의 복합 FK 대상)"
        VARCHAR(200) store_name_snapshot "스캔 시점 상호 | Y | [V5] 서버가 복사한 스냅샷"
        VARCHAR(20) store_match_method "가게 식별 경로 | Y | [V5] gps_candidate/name_search/kakao_fallback + store_detail(가게 상세 '이 식당 메뉴 스캔')"
        INTEGER danger_count "DANGER 메뉴 수 | Y | Judge 저장 트랜잭션에서 갱신; 이력 목록용 비정규화"
        INTEGER caution_count "CAUTION 메뉴 수 | Y | Judge 저장 시 갱신"
        INTEGER safe_count "SAFE 메뉴 수 | Y | Judge 저장 시 갱신"
        VARCHAR(5) locale "판정 언어 | Y | Judge 요청 locale; message 가 이 언어로 저장됨. 다른 언어로 볼 때 백엔드 번역; ko/en/ar/zh-CN/zh-TW/ja/es"
        JSONB diet_snapshot "판정에 쓴 식단 | Y | 스캔 시작 때 Judge user_profile 그대로 {religion_type, is_vegetarian, vegetarian_type, no_alcohol, allergies, no_spicy}(CHECK); 결과 화면 '내 식단'·후기 식단 항목의 기준; NULL=도입 전 스캔"
    }

    scan_images["스캔 원본 이미지 (scan_images)"] {
        UUID id PK "이미지 ID | N | 애플리케이션 생성; 기존 menu_images 에서 이름 변경"
        UUID scan_session_id FK,UK "스캔 ID | N | scan_sessions.id; 스캔당 최대 1개; CASCADE"
        VARCHAR(20) source "이미지 출처 | Y | camera/upload"
        VARCHAR(512) storage_key "S3 객체 키 | Y | 업로드한 이미지 위치"
        VARCHAR(1024) image_url "이미지 URL | Y | 저장된 이미지 참조"
        VARCHAR(100) mime_type "MIME 타입 | Y | 예: image/jpeg"
        BIGINT file_size "파일 크기 | Y | 바이트"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
        VARCHAR(1024) object_version_id "S3 객체 버전 ID | Y | [V4] 검증한 OCR 입력 객체 버전"
        VARCHAR(255) etag "S3 ETag | Y | [V4] 업로드 객체 동일성 확인"
    }

    scan_records["스캔 기록 보관 (scan_records)"] {
        UUID scan_session_id PK,FK "스캔 ID | N | scan_sessions.id; 기록 PK; CASCADE"
        BIGINT attached_store_id FK "사후 연결 가게 ID | Y | stores.id; 삭제 RESTRICT"
        VARCHAR(200) attached_store_name_snapshot "사후 연결 상호 | Y | stores.name 복사"
        VARCHAR(20) attached_store_match_method "사후 가게 식별 경로 | Y | gps_candidate/name_search/kakao_fallback"
        JSONB feedback "식단 소통 피드백 | N | 기본 빈 객체; yes/no/unknown; 증거 아님(확정)"
        TIMESTAMPTZ saved_at "저장 시각 | N | Keep this scan 실행 시각"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    scan_ai_results["스캔 AI 원본 (scan_ai_results)"] {
        UUID scan_session_id PK,FK "스캔 ID | N | scan_sessions.id; 스캔당 1행; CASCADE"
        UUID prepare_id UK "Prepare ID | N | AI 발급 UUID; Judge 요청에 그대로 전달"
        VARCHAR(10) schema_version "계약 버전 | N | 예: 3.0"
        VARCHAR(71) prepared_items_hash "항목 해시 | N | 'sha256:'+64 hex; {prepared_items, prepare_signals} JCS 해시"
        JSONB prepared_items "Prepare 항목 원본 | N | 가공 없이 Judge 에 재전송"
        JSONB prepare_signals "Prepare 안전 신호 | N | 가공 없이 Judge 에 재전송"
        TIMESTAMPTZ prepared_at "Prepare 저장 시각 | N | 기본 now()"
        VARCHAR(64) knowledge_revision "지식 리비전 | Y | Judge 요청에 보낸 KnowledgeBundle 식별 문자열"
        VARCHAR(71) bundle_hash "번들 해시 | Y | Judge 응답 'sha256:'+64 hex"
        JSONB judge_evidence "판정 근거 | Y | Judge evidence[] 배열; risk_ingredients 의 evidence_refs 해석용"
        JSONB judge_warnings "판정 경고 | Y | Judge 최상위 warnings[]"
        JSONB judge_raw "Judge 응답 원본 | Y | JudgeResponse 전체 객체; 디버깅·재처리용, 판정 후 90일 보존 뒤 NULL (근거·경고는 별도 컬럼에 장기 보관)"
        TIMESTAMPTZ judged_at "판정 시각 | Y | Judge 저장 시각; 판정 메타 4개와 함께 채움(CHECK)"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now(); Judge 재시도 시 갱신"
    }

    menu_analyses["스캔 메뉴 분석 (menu_analyses)"] {
        UUID id PK,UK "메뉴 분석 ID | N | 애플리케이션 생성; (id, scan_session_id) 복합 유일(후기 줄·찜의 복합 FK 대상)"
        UUID scan_session_id FK,UK "스캔 ID | N | scan_sessions.id; CASCADE; source_index 와 복합 유일"
        INTEGER display_order "표시 순서 | Y | 백엔드가 Judge 결과로 계산(SAFE → CAUTION 추정 → CAUTION 정보없음 → DANGER, 같은 칸은 source_index 순); Judge 전 NULL"
        VARCHAR(255) display_name "메뉴판 표기 | Y | Prepare display_name 스냅샷 (기존 menu_name_ko)"
        TEXT description "메뉴판 설명 | Y | Prepare description 스냅샷 (기존 description_ko)"
        VARCHAR(100) price_text "가격 문자열 | Y | Prepare price_text 스냅샷"
        BOOLEAN is_spicy "매운맛 관측 | Y | OCR risk_tag_observations 감지 시 true, 아니면 NULL; false 저장 금지"
        VARCHAR(20) risk_level "위험도 | Y | 지금 보여 주는 판정 danger/caution/safe; 직원이 있어요라고 답하고 내 식단과 겹치면 서버가 danger 로 올림(AI 첫 판정은 ai_risk_level); 서버는 안전으로 내리지 않음"
        JSONB matched_tags "일치 태그 | Y | 사용자 금지 태그와 겹친 is_* 배열 (기존 hit_tags)"
        JSONB message "사용자 안내문 | Y | {verdict, reason, notes[]} 객체, scan_sessions.locale 한 언어; 레거시는 {ko,en,ar}"
        JSONB staff_cards "직원 소통 카드 | N | 배열, 기본 []; card_id 로 staff_questions 행과 연결; 문구는 ko + 사용자 언어 (기존 owner_card)"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
        INTEGER source_index UK "메뉴판 순서 | Y | Prepare source_index (0부터); AI item_id = scan_session_id || ':' || source_index"
        JSONB price_options "가격 옵션 | N | Prepare price_options 스냅샷 배열"
        VARCHAR(20) normalization_status "정규화 상태 | Y | normalized/ambiguous/unmatched/invalid (②-B)"
        VARCHAR(20) status "판정 처리 상태 | Y | completed/partial_failed/failed; Judge 전 NULL"
        VARCHAR(20) evidence_basis "판정 근거 | Y | confirmed/estimated/unknown"
        VARCHAR(20) information_status "정보 상태 | Y | complete/partial/none; complete + 직원 응답 complete 아니면 SAFE 금지(CHECK)"
        JSONB risk_ingredients "위험 재료 | Y | Judge risk_ingredients 스냅샷 배열; evidence_refs 는 scan_ai_results.judge_evidence 로 해석"
        JSONB recommendation "스캔 내 추천 | Y | 백엔드 계산 {rank, score, reason_codes, content_ids}; SAFE+complete 만; 아니면 NULL"
        BIGINT store_id FK "스캔 가게 ID | Y | scan_sessions.store_id 사본; (scan_session_id, store_id) 복합 FK — 가게 메뉴가 같은 가게임을 보장"
        BIGINT store_menu_id FK "가게 메뉴 ID | Y | store_menus.id; (store_menu_id, store_id) 복합 FK; 삭제 시 이 컬럼만 SET NULL; 가게 없는 스캔은 NULL"
        BIGINT menu_id FK "판정 메뉴 ID | Y | Judge items[].menu_id 스냅샷(판정에 쓴 표준 메뉴); menus.id RESTRICT"
        VARCHAR(20) menu_match_type "메뉴 매칭 방식 | Y | exact/alias/fuzzy/runtime_variant/unmatched; AI match_type 매핑"
        VARCHAR(10) staff_answer_status "직원 응답 상태 | N | none/partial/complete (Judge items[].owner_answer_status 스냅샷; 직원 응답으로 바꾸지 않음); 가게 없는 스캔은 none; SAFE 는 complete 만(CHECK) [#24]"
        VARCHAR(10) ai_risk_level "AI 첫 판정 | Y | 이 스캔의 첫 Judge 위험도 danger/caution/safe; 이후 변경 금지; 판정이 있으면 필수(V14 CHECK)"
    }

    saved_cards["저장한 소통 카드 (saved_cards)"] {
        UUID id PK "카드 ID | N | 애플리케이션 생성"
        UUID user_id FK "사용자 ID | N | users.id; CASCADE"
        VARCHAR(20) card_type "카드 유형 | N | order/ingredient_check/exclude/less_spicy(덜 맵게) (CHECK)"
        VARCHAR(255) menu_name_ko "메뉴명 | N | 표시용 스냅샷; 원본이 사라져도 표시"
        JSONB card_text "카드 문구 | N | ko 필수 + 사용자 언어 스냅샷 (기존 text)"
        UUID scan_session_id FK "출처 스캔 ID | Y | scan_sessions.id; 삭제 시 SET NULL"
        TIMESTAMPTZ last_saved_at "최근 저장 시각 | N | 재저장 시 갱신; 목록 정렬"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
        BIGINT store_menu_id FK "가게 메뉴 ID | Y | store_menus.id; SET NULL"
        BIGINT menu_id FK "표준 메뉴 ID | Y | menus.id; SET NULL; 언어 변경 시 name_i18n 표시"
    }

    favorite_menus["메뉴 찜 (favorite_menus)"] {
        UUID user_id PK,FK "사용자 ID | N | (scan_session_id, user_id) → scan_sessions(id, user_id) 복합 FK — 내 스캔만; PK 일부"
        UUID menu_analysis_id PK,FK "찜한 메뉴 | N | (scan_session_id, menu_analysis_id) → menu_analyses(scan_session_id, id) 복합 FK; CASCADE; API favorite-menus/{menuAnalysisId}"
        UUID scan_session_id FK "스캔 ID | N | 두 복합 FK 공통; 스캔 기록 삭제 시 찜도 CASCADE"
        TIMESTAMPTZ created_at "찜한 시각 | N | 기본 now(); 저장한 메뉴 목록 최신순"
    }

    text_translations["기계번역 캐시 (text_translations)"] {
        CHAR(64) source_hash PK "원문 해시 | N | sha256(source_locale || ':' || source_text) hex"
        VARCHAR(5) target_locale PK "대상 언어 | N | ko/en/ar/zh-CN/zh-TW/ja/es"
        VARCHAR(5) source_locale "원문 언어 | N | ko/en/ar/zh-CN/zh-TW/ja/es; 대상과 달라야 함"
        TEXT source_text "원문 | N | 해시 충돌 검증·재번역용"
        TEXT translated_text "번역문 | N | 기계번역 결과"
        VARCHAR(30) provider "번역 엔진 | N | 예: deepl/amazon_translate"
        TIMESTAMPTZ created_at "생성 시각 | N | 오래된 행 정리 기준"
    }

    store_categories["상권 업종 분류 (store_categories)"] {
        BIGINT id PK "분류 ID | N | IDENTITY"
        VARCHAR(6) code UK "분류 코드 | N | 대/중/소 분류 코드"
        VARCHAR(60) name "분류명 | N | 상권 업종명"
        SMALLINT level "계층 수준 | N | 1=대; 2=중; 3=소"
        BIGINT parent_id FK "상위 분류 ID | Y | 같은 테이블 id; 대분류는 NULL"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    ksic_codes["한국표준산업분류 (ksic_codes)"] {
        VARCHAR(6) code PK "KSIC 코드 | N | 자연키"
        VARCHAR(120) name "산업분류명 | N | 표시명"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    store_import_batches["가게 데이터 적재 배치 (store_import_batches)"] {
        BIGINT id PK "적재 배치 ID | N | IDENTITY"
        VARCHAR(20) source UK "데이터 출처 | N | sbiz/localdata; 버전과 복합 유일"
        VARCHAR(10) source_version UK "출처 버전 | N | YYYYMM; 출처와 복합 유일"
        INTEGER row_count "적재 행 수 | N | 기본 0; 음수 금지"
        VARCHAR(20) status "적재 상태 | N | running/completed/failed"
        TIMESTAMPTZ started_at "시작 시각 | N | 기본 now()"
        TIMESTAMPTZ finished_at "종료 시각 | Y | 실행 중 NULL"
    }

    stores["가게 마스터 (stores)"] {
        BIGINT id PK "가게 ID | N | IDENTITY"
        VARCHAR(24) sbiz_store_no UK "상가업소번호 | Y | 공공데이터 멱등 키; localdata는 NULL"
        VARCHAR(200) name "상호명 | N | 화면 표시 원본"
        VARCHAR(100) branch_name "지점명 | N | 기본 빈 문자열"
        VARCHAR(200) name_normalized "정규화 상호명 | Y | GENERATED STORED; 검색 전용"
        BIGINT category_id FK "업종 분류 ID | N | store_categories.id"
        VARCHAR(6) ksic_code FK "KSIC 코드 | Y | ksic_codes.code; 원천 결측 허용"
        VARCHAR(8) admin_dong_code "행정동 코드 | N | 기본 빈 문자열"
        VARCHAR(300) road_address "도로명 주소 | N | 기본 빈 문자열"
        VARCHAR(20) floor_info "층 정보 | N | 기본 빈 문자열"
        DOUBLE_PRECISION lat "위도 | N | 범위 33~39"
        DOUBLE_PRECISION lng "경도 | N | 범위 124~132"
        VARCHAR(20) status "노출 상태 | N | active/inactive; 폐업 확정 아님"
        VARCHAR(20) origin "데이터 출처 | N | sbiz/localdata"
        TIMESTAMPTZ inactive_at "비활성 시각 | Y | inactive일 때 필수"
        TIMESTAMPTZ verified_at "서비스 검증 시각 | Y | 공공데이터 수록과 별개"
        BIGINT last_batch_id FK "최근 적재 배치 ID | N | store_import_batches.id"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
        BIGINT image_asset_id FK "대표 이미지 ID | Y | image_assets.id; RESTRICT; 적재 스크립트가 덮어쓰지 않는 컬럼"
        VARCHAR(22) localdata_mgmt_no UK "LOCALDATA 관리번호 | Y | [V13.1] 인허가 데이터 멱등 키; sbiz는 NULL"
    }

    brand_aliases["브랜드 별칭 (brand_aliases)"] {
        BIGINT id PK "별칭 ID | N | IDENTITY"
        VARCHAR(200) variant_normalized UK "변형 표기 | N | 정규화된 검색어"
        VARCHAR(200) canonical_normalized "대표 표기 | N | 정규화된 치환 결과"
        VARCHAR(200) note "비고 | N | 기본 빈 문자열"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    store_external_refs["외부 지도 장소 참조 (store_external_refs)"] {
        BIGINT id PK "외부참조 ID | N | IDENTITY"
        BIGINT store_id FK,UK "가게 ID | N | stores.id; CASCADE; provider와 복합 유일"
        VARCHAR(20) provider UK "지도 제공자 | N | kakao; store_id/external_id와 각각 복합 유일"
        VARCHAR(64) external_id UK "외부 장소 ID | N | provider와 복합 유일"
        VARCHAR(512) external_url "외부 장소 URL | N | 기본 빈 문자열"
        TIMESTAMPTZ linked_at "연결 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    store_menus["가게 메뉴 (store_menus)"] {
        BIGINT id PK,UK "가게 메뉴 ID | N | IDENTITY; (id, store_id) 복합 유일 — 스캔 메뉴·직원 질문·확정값의 복합 FK 대상"
        BIGINT store_id FK,UK "가게 ID | N | stores.id; RESTRICT; name_normalized 와 복합 유일"
        VARCHAR(255) name_normalized UK "정규화 메뉴명 | N | AI normalized_menu_name → normalize_food_name; 가게 안 식별 키"
        VARCHAR(255) display_name "메뉴판 표기 | N | 최근 스캔의 display_name"
        TEXT description "메뉴판 설명 | Y | 최근 스캔의 description"
        VARCHAR(100) price_text "가격 문자열 | Y | 최근 스캔의 price_text"
        JSONB price_options "가격 옵션 | N | 최근 스캔의 price_options 배열"
        BIGINT menu_id FK "표준 메뉴 ID | Y | menus.id; RESTRICT; NULL=매칭 없음(고유 메뉴·미승인 변형)"
        VARCHAR(20) mapping_source "매핑 근거 | N | none/fuzzy/name_match/admin; name_match·admin 은 자동 교체 안 함(매핑 고정)"
        INTEGER mapping_conflict_count "매핑 충돌 수 | N | 고정 매핑과 다른 결과가 나온 횟수; 2 이상이면 관리자 검토"
        VARCHAR(20) status "노출 상태 | N | active/hidden"
        TIMESTAMPTZ first_seen_at "처음 관측 | N | 기본 now()"
        TIMESTAMPTZ last_seen_at "마지막 관측 | N | 스캔마다 갱신"
        INTEGER seen_count "관측 스캔 수 | N | 1 이상"
        UUID last_scan_session_id FK "마지막 관측 스캔 | Y | scan_sessions.id; SET NULL"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
        BIGINT image_asset_id FK "대표 이미지 ID | Y | image_assets.id; RESTRICT; 없으면 표준 메뉴 이미지"
    }

    areas["동네 (areas)"] {
        BIGINT id PK "동네 ID | N | IDENTITY; API areaId"
        JSONB name_i18n "동네 이름 | N | ko 필수 + ko/en/ar/zh-CN/zh-TW/ja/es 안(CHECK); 없는 언어는 ko 로 표시"
        DOUBLE_PRECISION center_lat "중심 위도 | N | 33~39(CHECK, stores 와 같음)"
        DOUBLE_PRECISION center_lng "중심 경도 | N | 124~132(CHECK)"
        INTEGER radius_m "반경(m) | N | 0 초과; 중심에서 이 거리 안의 가게가 자동으로 이 동네(수동 배정 없음)"
        SMALLINT display_order "표시 순서 | N | 기본 0; 홈 동네 칩 정렬"
        VARCHAR(20) status "노출 상태 | N | 기본 active; active/hidden — hidden 은 칩에서 뺌"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    menu_categories["메뉴 분류 (menu_categories)"] {
        BIGINT id PK "메뉴 분류 ID | N | IDENTITY"
        VARCHAR(20) name_ko UK "분류명 | N | 예: 찌개/구이/김치"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    menus["메뉴 마스터 (menus)"] {
        BIGINT id PK "메뉴 ID | N | IDENTITY; AI 계약 정수"
        VARCHAR(100) name_ko "메뉴명 | N | 변형이면 변형 표기 그대로"
        VARCHAR(100) name_normalized UK "정규화 메뉴명 | Y | GENERATED STORED normalize_food_name"
        JSONB name_i18n "번역명 | Y | 키 7개 ko/en/ar/zh-CN/zh-TW/ja/es; NULL=미번역 [#7]"
        BIGINT category_id FK "메뉴 분류 ID | Y | menu_categories.id; NULL이면 cluster 생략"
        BIGINT base_menu_id FK "원본 메뉴 ID | Y | menus.id; remain_token과 함께 존재"
        VARCHAR(30) remain_token "변형 토큰 | Y | 예: 차돌"
        VARCHAR_ARRAY ambiguity_flags "애매함 플래그 | Y | VARCHAR(30)[]; 5종 부분집합; NULL=미검토"
        VARCHAR(30) source "등록 출처 | N | curated/web_search_generated/variant_generated"
        BOOLEAN needs_review "큐레이션 보완 필요 | N | 기본값 없음; false면 분류·플래그 필수"
        INTEGER knowledge_revision "지식 리비전 | N | 기본 1; 캐시 무효화 기준; 재료 단위 변경 시 관련 메뉴 +1 [#9]"
        TIMESTAMPTZ web_registered_at "웹 등록 시각 | Y | source=web_search_generated 면 필수(CHECK); 승인 레시피 30개가 되면 백엔드가 자동 등록"
        INTEGER web_approved_source_count "웹 승인 레시피 수 | Y | 30 이상(CHECK); 웹 등록 메뉴만 [#22]"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
        BIGINT image_asset_id FK "대표 이미지 ID | Y | image_assets.id; RESTRICT; 스캔 결과에서 가게 메뉴 이미지가 없을 때 사용"
    }

    menu_aliases["메뉴 별칭 (menu_aliases)"] {
        BIGINT id PK "별칭 ID | N | IDENTITY"
        BIGINT menu_id FK "메뉴 ID | N | menus.id; CASCADE"
        VARCHAR(100) alias_ko "별칭 원문 | N | 예: 돈까스"
        VARCHAR(100) alias_normalized UK "정규화 별칭 | Y | GENERATED STORED; 전역 유일"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    ingredients["재료 마스터 (ingredients)"] {
        BIGINT id PK "재료 ID | N | IDENTITY; AI 계약 정수"
        VARCHAR(60) name_ko "재료명 | N | 대표 표기"
        VARCHAR(60) name_normalized UK "정규화 재료명 | Y | GENERATED STORED"
        JSONB name_i18n "번역명 | Y | 키 7개 ko/en/ar/zh-CN/zh-TW/ja/es [#7]"
        VARCHAR(20) taxonomy_category "재료 분류 | N | 기본 unknown; fermented/broth/sauce/processed/seasoning/oil/garnish/base/unknown [#6]"
        VARCHAR_ARRAY tag_codes "재료 태그 | N | VARCHAR(20)[]; is_* 22종 배열(식약처 19 + is_fish/is_duck/is_alcohol); 정렬·중복 없음(CHECK); 기존 ingredient_tags 통합 [#21]"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    ingredient_aliases["재료 별칭 (ingredient_aliases)"] {
        BIGINT id PK "별칭 ID | N | IDENTITY"
        BIGINT ingredient_id FK "재료 ID | N | ingredients.id; CASCADE"
        VARCHAR(60) alias_ko "별칭 원문 | N | 예: 쇠고기"
        VARCHAR(60) alias_normalized UK "정규화 별칭 | Y | GENERATED STORED; 전역 유일"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    ingredient_compositions["재료 구성 간선 (ingredient_compositions)"] {
        BIGINT parent_ingredient_id PK,FK "상위 재료 ID | N | ingredients.id; RESTRICT"
        BIGINT child_ingredient_id PK,FK "하위 재료 ID | N | ingredients.id; RESTRICT; 상위와 달라야 함"
        VARCHAR(10) relation_type "관계 | N | part_of 만 (CHECK) [#5]"
        VARCHAR(30) ambiguity_flag "유발 플래그 | Y | 4종; 질문 생성 근거"
        VARCHAR_ARRAY tag_codes "경로 태그 | N | VARCHAR(20)[]; 이 간선(상위 맥락)에서만 성립하는 is_* 배열; 정렬·중복 없음; 기존 ingredient_composition_tags 통합"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    recipe_ingredients["메뉴 레시피 재료 (recipe_ingredients)"] {
        BIGINT menu_id PK,FK "메뉴 ID | N | menus.id; RESTRICT"
        BIGINT ingredient_id PK,FK "재료 ID | N | ingredients.id; RESTRICT"
        VARCHAR(20) evidence_type "근거 유형 | N | explicit/variant/name_rule/web_search"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    knowledge_import_batches["지식 적재 배치 (knowledge_import_batches)"] {
        BIGINT id PK "적재 배치 ID | N | IDENTITY"
        VARCHAR(30) source UK "원천 | N | menus_csv/hidden_rules/variant_ingredients/ingredient_rules/semie_prior/wtable_prior/10000recipe_prior [#4]"
        VARCHAR(40) source_version UK "원천 버전 | N | AI 레포 커밋 SHA; 원천과 복합 유일"
        INTEGER row_count "적재 행 수 | N | 기본 0"
        VARCHAR(20) status "적재 상태 | N | running/completed/failed"
        TIMESTAMPTZ started_at "시작 시각 | N | 기본 now()"
        TIMESTAMPTZ finished_at "종료 시각 | Y | 실행 중 NULL"
    }

    menu_recipe_corpora["메뉴별 코퍼스 레시피 수 (menu_recipe_corpora)"] {
        BIGINT menu_id PK,FK,UK "메뉴 ID | N | menus.id; RESTRICT"
        VARCHAR(20) corpus PK,UK "코퍼스 | N | semie/wtable/10000recipe [#3]"
        INTEGER n_total UK "레시피 수 | N | 1 이상; menu_id·corpus와 복합 유일(FK 대상)"
        BIGINT import_batch_id FK "적재 배치 ID | N | knowledge_import_batches.id"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    menu_ingredient_priors["메뉴×재료 코퍼스 관측 (menu_ingredient_priors)"] {
        BIGINT menu_id PK,FK "메뉴 ID | N | 복합 FK → menu_recipe_corpora"
        VARCHAR(20) corpus PK,FK "코퍼스 | N | 복합 FK; semie/wtable/10000recipe [#3]"
        BIGINT ingredient_id PK,FK "재료 ID | N | ingredients.id; RESTRICT"
        INTEGER n_total FK "레시피 수 | N | 복합 FK로 부모와 일치 강제; ON UPDATE CASCADE"
        INTEGER k_count "등장 레시피 수 | N | 1 이상 n_total 이하"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    menu_ingredient_cache["메뉴 재료 확장 캐시 (menu_ingredient_cache)"] {
        BIGINT menu_id PK,FK "메뉴 ID | N | menus.id; CASCADE; 메뉴당 1행(전역 데이터)"
        INTEGER menu_revision "계산 기준 리비전 | N | menus.knowledge_revision과 다르면 무효"
        JSONB ingredients_snapshot "확장 스냅샷 | N | 배열; 전역 데이터만(가게 확정값 제외)"
        TIMESTAMPTZ computed_at "계산 시각 | N | 기본 now()"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    ingredient_confirmations["재료 확정값 (ingredient_confirmations)"] {
        BIGINT id PK,UK "확정 ID | N | IDENTITY; (id, store_menu_id, ingredient_id) 복합 유일(질문 링크 FK 대상)"
        BIGINT store_id FK "가게 ID | N | (store_menu_id, store_id) → store_menus 복합 FK 일부; 가게 범위 조회용"
        BIGINT store_menu_id FK,UK "가게 메뉴 ID | N | store_menus.id; RESTRICT; 재료와 복합 유일 — 표준 메뉴가 아니라 이 가게의 이 메뉴에 대한 답"
        BIGINT ingredient_id FK,UK "재료 ID | N | ingredients.id; RESTRICT"
        BOOLEAN present "존재 여부 | N | 직원 최신 답 present=true/absent=false; 행 없음=unknown; 이상 답변 잠금 없이 그대로 override 에 사용 [#26]"
        VARCHAR(20) source "확정 출처 | N | staff_confirmed(직원 응답)만 (CHECK)"
        TIMESTAMPTZ confirmed_at "확정 시각 | N | 최근 답변을 반영한 시각; 감사·이력용, 유효기간 없음 [#18]"
        TIMESTAMPTZ invalidated_at "사용 중지 시각 | Y | 직원이 unknown(모름)으로 고치거나 관리자가 무효화하면 채움, Judge 확정값에서 제외; 새 답(present/absent)이 오면 NULL"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    flag_confirmations["메뉴 애매함 플래그 확정값 (flag_confirmations)"] {
        BIGINT id PK,UK "플래그 확정 ID | N | IDENTITY; (id, store_menu_id, ambiguity_flag) 복합 유일(질문 링크 FK 대상) [#12]"
        BIGINT store_id FK "가게 ID | N | (store_menu_id, store_id) → store_menus 복합 FK 일부 [#12]"
        BIGINT store_menu_id FK,UK "가게 메뉴 ID | N | store_menus.id; RESTRICT; 플래그·태그와 복합 유일 [#12]"
        VARCHAR(30) ambiguity_flag UK "애매함 플래그 | N | menus.ambiguity_flags 값 하나 [#12]"
        VARCHAR_ARRAY asked_tags UK "물어본 태그 | N | VARCHAR(20)[]; is_* 배열; 정렬·중복 없음(CHECK) [#12]"
        BOOLEAN present "존재 여부 | N | 하나 이상 있음=true; 전부 없음=false; 행 없음=unknown [#12]"
        TIMESTAMPTZ confirmed_at "확정 시각 | N | 최근 답변을 반영한 시각; 유효기간 없음 [#12]"
        TIMESTAMPTZ invalidated_at "사용 중지 시각 | Y | 직원이 unknown 으로 고치거나 관리자가 무효화하면 채움, Judge 확정값에서 제외; 새 답이 오면 NULL"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now() [#12]"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now() [#12]"
    }

    staff_questions["직원 질문 (staff_questions)"] {
        UUID id PK "질문 ID | N | 애플리케이션 생성; API staff-questions/{questionId}"
        BIGINT store_id FK "가게 ID | N | 스캔·가게 메뉴와 각각 복합 FK"
        UUID scan_session_id FK,UK "발생 스캔 ID | Y | (scan_session_id, store_id) 복합 FK; ON DELETE SET NULL (scan_session_id); 대상별 부분 UK 2종 + card_id 복합 UK [#17]"
        BIGINT store_menu_id FK,UK "가게 메뉴 ID | N | (store_menu_id, store_id) → store_menus 복합 FK; 답변이 반영되는 대상"
        BIGINT menu_id FK "표준 메뉴 ID | Y | 질문 당시 판정에 쓴 menus.id 스냅샷; 웹 경로 메뉴는 NULL; RESTRICT"
        BIGINT ingredient_id FK "재료 ID | Y | ingredients.id; 재료 질문만; 플래그 질문은 NULL [#11]"
        VARCHAR(30) ambiguity_flag FK,UK "애매함 플래그 | Y | 플래그 질문만 [#11]"
        VARCHAR_ARRAY asked_tags "물어본 태그 | Y | VARCHAR(20)[]; 플래그 질문만; 정렬·중복 없음(CHECK) [#11]"
        JSONB question_text "질문 문구 | N | ko 필수(직원이 읽음) + 사용자 언어(⓪ §4-6); 키는 7개 언어 안"
        VARCHAR(400) card_id UK "카드 ID | Y | AI owner_cards.card_id (card:{item_id}:{대상}) = menu_analyses.staff_cards 의 card_id; scan_session_id 와 복합 유일"
        VARCHAR(20) status "상태 | N | 기본 pending; answered/expired — expired 는 미답변 질문의 처리 기한 종료(확정값 만료 아님)"
        VARCHAR(10) answer "답변 | Y | present(있어요)/absent(없어요)/unknown(모름); 잘못 누르면 같은 질문에서 다시 답함(덮어쓰기)"
        TIMESTAMPTZ answered_at "답변 시각 | Y | answered 면 필수(CHECK); 다시 답하면 최신 시각"
        BIGINT resolved_confirmation_id FK "반영 확정 ID | Y | (id, store_menu_id, ingredient_id) 복합 FK; 재료 질문 present/absent 답이면 필수(CHECK) [#16]"
        BIGINT resolved_flag_confirmation_id FK "반영 플래그 확정 ID | Y | (id, store_menu_id, ambiguity_flag) → flag_confirmations 복합 FK; 플래그 질문 present/absent 답이면 필수(CHECK) [#16]"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    ingredient_evidence_log["재료 증거 로그 (ingredient_evidence_log)"] {
        BIGINT id PK "증거 ID | N | IDENTITY; append-only"
        BIGINT store_id FK,UK "가게 ID | N | stores.id; RESTRICT"
        BIGINT menu_id FK,UK "메뉴 ID | N | menus.id; RESTRICT"
        BIGINT ingredient_id FK,UK "재료 ID | N | ingredients.id; RESTRICT"
        VARCHAR(20) source_type "증거 출처 | N | ocr_variant_tag(관리자가 승인한 변형 근거)만; 직원 응답·웹 레시피는 넣지 않음 [#25]"
        NUMERIC delta_alpha "α 증가분 | N | NUMERIC(10,4); NUMERIC(10,4); 0 초과(있음 쪽 soft 근거)"
        NUMERIC delta_beta "β 증가분 | N | NUMERIC(10,4); NUMERIC(10,4); 0 고정(CHECK)"
        BIGINT review_item_id FK,UK "검토 항목 ID | N | knowledge_review_items.id; RESTRICT; 이 변형 근거를 승인한 검토 항목"
        TIMESTAMPTZ created_at "기록 시각 | N | 기본 now()"
    }

    source_reliability["출처 신뢰도 (source_reliability)"] {
        VARCHAR(40) source_type PK "출처 | N | 예: corpus:10000recipe/web_search/curated; 모든 가게 공통 1벌 [#1]"
        INTEGER agree_count "맞춘 수 | N | 기본 0; compared_count 이하(CHECK) [#1]"
        INTEGER compared_count "비교한 수 | N | 기본 0 [#1]"
        NUMERIC reliability "신뢰도 | Y | NUMERIC(5,4); GENERATED (agree_count+1)/(compared_count+2); 0~1 [#1]"
        BIGINT last_evidence_id FK "마지막 반영 증거 ID | Y | ingredient_evidence_log.id [#1]"
        TIMESTAMPTZ updated_at "갱신 시각 | N | 기본 now() [#1]"
    }

    web_search_sources["웹 검색 원본 (web_search_sources)"] {
        BIGINT id PK "원본 ID | N | IDENTITY"
        VARCHAR(100) normalized_menu_name UK "정규화 메뉴명 | N | normalize_food_name 결과와 같아야 함(CHECK); 전역 유일"
        VARCHAR(255) original_menu_name "원문 메뉴명 | Y | OCR 원문"
        VARCHAR(20) search_status "검색 상태 | N | searched/unresolved"
        JSONB payload "검색 원본 | N | 출처 배열; 갱신 시 기존 배열에 이어 붙임; 레시피로 간주 금지; 삭제 금지·백업 대상 [#14]"
        TIMESTAMPTZ searched_at "검색 시각 | N | 마지막 검색 시각"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    knowledge_review_items["관리자 검토 항목 (knowledge_review_items)"] {
        BIGINT id PK "검토 항목 ID | N | IDENTITY"
        BIGINT store_id FK "발견 가게 ID | N | stores.id; RESTRICT; 웹 묶음=검색이 일어난 스캔의 가게(출처 기록), 변형 항목=증거 귀속 가게"
        VARCHAR(30) item_type UK "항목 유형 | N | web_search_menu/runtime_variant (AI review_origin과 매핑)"
        VARCHAR(20) status "검토 상태 | N | 웹 묶음: 이상 신호 없으면 auto_approved, 있으면 pending → approved(예)/rejected(아니오); 변형: pending → approved/rejected"
        VARCHAR(100) proposed_name_ko "제안 메뉴명 | N | 변형이면 전체 표기"
        VARCHAR(100) proposed_name_normalized "정규화 제안명 | Y | GENERATED STORED; 변형 대기 항목은 부분 유일, 웹 30개 판정 키"
        BIGINT base_menu_id FK "변형 원본 메뉴 ID | Y | menus.id; runtime_variant면 필수"
        VARCHAR(30) remain_token "변형 토큰 | Y | base_menu_id와 함께 존재"
        JSONB payload "불변 스냅샷 | N | create_review_item 원본(변형 제안 variant_suggestion 등). 웹 레시피는 knowledge_review_sources"
        JSONB review_notes "이상 신호 | N | ⑥ review_notes 배열 [{rule_code, message}]; 비어 있으면 자동 승인, 있으면 관리자 검토 화면에 표시"
        BIGINT web_search_source_id FK "웹 원본 ID | Y | web_search_sources.id; SET NULL; 웹 항목만"
        UUID source_scan_session_id FK "발견 스캔 ID | Y | scan_sessions.id; SET NULL"
        BIGINT resolved_menu_id FK "결과 메뉴 ID | Y | menus.id; RESTRICT; 변형 승인 시 새 변형 메뉴(필수), 웹 묶음은 30개로 등록된 메뉴(등록 전 NULL)"
        UUID reviewed_by FK "검토자 ID | Y | users.id; SET NULL; 자동 승인은 NULL"
        TIMESTAMPTZ reviewed_at "검토 시각 | Y | 자동 승인은 저장 시각; pending 이면 NULL"
        VARCHAR(500) review_note "관리자 메모 | Y | 반려 사유 등"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    knowledge_review_sources["검토 레시피 출처 (knowledge_review_sources)"] {
        BIGINT id PK "레시피 출처 ID | N | IDENTITY"
        BIGINT review_item_id FK,UK "검토 묶음 ID | N | knowledge_review_items.id; CASCADE; source_url 과 복합 유일; 승인·반려는 묶음 단위"
        VARCHAR(1024) source_url UK "레시피 URL | N | http(s); 30개 판정은 승인 묶음 URL 중복 제거 개수"
        VARCHAR(500) title "레시피 제목 | Y | AI web_sources[].title"
        TIMESTAMPTZ fetched_at "수집 시각 | Y | AI web_sources[].fetched_at"
        JSONB extracted_ingredients "추출 재료 | N | AI web_sources[].ingredients 배열 (ingredient_id 없을 수 있음)"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    ai_command_inbox["AI 명령 수신함 (ai_command_inbox)"] {
        VARCHAR(40) action PK "명령 종류 | N | 6종 create_review_item/create_owner_verification_request(→ staff_questions)/record_owner_answer(직원 응답 기록)/update_source_reliability/save_web_search_cache/save_menu_ingredient_cache; 이름은 AI 계약 그대로 [#2]"
        VARCHAR(128) idempotency_key PK "멱등 키 | N | AI 생성 'sha256:'+hex; 내용 기준이라 재시도해도 같은 값"
        BIGINT store_id "가게 ID | Y | stores.id 값(FK 없음, 반영 단계에서 검증); 전역 명령 3종은 NULL, 나머지 필수(CHECK)"
        UUID scan_session_id "발생 스캔 ID | Y | scan_sessions.id 값(FK 없음); 스캔 밖 명령(직원 응답 기록)은 NULL"
        CHAR(64) request_hash "요청 해시 | N | payload SHA-256 hex(백엔드 계산); 같은 키에 다른 내용이면 거부"
        JSONB payload "명령 원문 | N | 재처리용"
        VARCHAR(20) status "처리 상태 | N | 기본 received; received/applied/failed/dead"
        INTEGER attempt_count "시도 횟수 | N | 기본 0"
        TIMESTAMPTZ next_attempt_at "다음 시도 시각 | Y | received/failed일 때"
        VARCHAR(1000) last_error "마지막 오류 | | Y"
        JSONB result "처리 결과 | Y | 생성 ID 등 재응답용; applied면 필수"
        TIMESTAMPTZ created_at "수신 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now(); applied는 90일 보존"
    }

    admin_audit_logs["관리자 감사 로그 (admin_audit_logs)"] {
        BIGINT id PK "감사 ID | N | IDENTITY; 추가만"
        UUID admin_user_id "관리자 ID | Y | users.id 값(FK 없음 — 계정 삭제 후에도 감사 기록 불변)"
        VARCHAR(60) action "행위 | N | 대상.동작 형식, 예: review_item.approve"
        VARCHAR(40) target_type "대상 유형 | N | 테이블·도메인 이름"
        VARCHAR(64) target_id "대상 ID | N | 문자열(UUID·BIGINT 공용)"
        JSONB detail "상세 | N | 바뀐 내용 요약 객체"
        INET ip_address "접속 IP | Y | 개인정보; 보존 기간 정책 대상"
        TIMESTAMPTZ created_at "기록 시각 | N | 기본 now()"
    }

    image_assets["이미지 자산 (image_assets)"] {
        BIGINT id PK "이미지 ID | N | IDENTITY [P4]"
        VARCHAR(512) storage_key UK "S3 객체 키 | N | 전체 유일; URL 은 응답 시 CDN 주소로 생성(저장 안 함)"
        CHAR(64) content_sha256 UK "파일 해시 | N | SHA-256 hex; 같은 파일 중복 업로드 방지"
        VARCHAR(100) mime_type "MIME 타입 | N | image/jpeg/png/webp"
        INTEGER width "가로 | Y | 픽셀"
        INTEGER height "세로 | Y | 픽셀"
        BIGINT file_size "파일 크기 | Y | 바이트"
        VARCHAR(20) source_type "출처 유형 | N | team_original/external_licensed/public_domain"
        VARCHAR(200) author "저작자 | Y | external_licensed면 필수(CHECK)"
        VARCHAR(50) license_code "라이선스 | Y | 예: CC-BY-SA-4.0, KOGL-1; external_licensed면 필수"
        VARCHAR(1024) source_url "원본 링크 | Y | external_licensed면 필수"
        VARCHAR(500) attribution_text "출처 표기 문구 | Y | 화면 표기용"
        VARCHAR(20) status "상태 | N | 기본 active; active/hidden"
        UUID created_by FK "등록자 ID | Y | users.id; SET NULL"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    curation_articles["큐레이션 글 (curation_articles)"] {
        BIGINT id PK "글 ID | N | IDENTITY"
        VARCHAR(100) slug UK "글 슬러그 | N | 전체 유일; 기존 하드코딩 ID 이관(korean-table 등)"
        VARCHAR(20) category_code "분류 코드 | N | culture/restaurant/food/tips (CHECK); 화면 이름은 앱 다국어 리소스"
        BIGINT cover_image_asset_id FK "대표 이미지 ID | Y | image_assets.id; RESTRICT"
        VARCHAR_ARRAY constraint_tags "재료 태그 | N | VARCHAR(20)[]; 글이 언급하는 재료의 is_* 태그(+is_spicy); 추천 계산에만 사용, 열람 제한 없음"
        VARCHAR_ARRAY audience_tags "대상 사용자 유형 | N | VARCHAR(30)[]; first_timer/vegetarian/vegan/halal/kosher/hindu/no_spicy/no_alcohol/allergy_care; 추천 계산에만 사용, 열람 제한 없음"
        VARCHAR(300) source_title "참고 자료명 | Y | 출처 정보가 있으면 필수(CHECK)"
        VARCHAR(200) source_publisher "발행처 | Y | 예: 한식진흥원, 한국관광공사"
        VARCHAR(1024) source_url "자료 링크 | Y | 원문 위치"
        VARCHAR(50) source_license "이용 조건 | Y | 공공누리 유형 등; 원문을 옮겨 쓸 때 필수(운영 규칙)"
        VARCHAR(20) status "게시 상태 | N | 기본 draft; draft(첫 게시 전)/published(공개)/hidden(비공개); 삭제는 행 삭제"
        TIMESTAMPTZ published_at "첫 게시 시각 | Y | draft 이면 NULL, 그 외 필수(CHECK); 공개/비공개 전환에도 유지"
        INTEGER issue_no UK "글 번호 | Y | 'HAN SPOON · 01' 번호; 첫 게시 때 부여, draft 면 NULL(CHECK); 전체 유일; 숨김·삭제 뒤에도 다른 글 번호 불변"
        UUID created_by FK "작성자 ID | Y | users.id; SET NULL"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    curation_article_translations["큐레이션 글 언어별 본문 (curation_article_translations)"] {
        BIGINT article_id PK,FK "글 ID | N | curation_articles.id; CASCADE [P4]"
        VARCHAR(5) locale PK "언어 | N | ko(운영자 원문)/en/ar/zh-CN/zh-TW/ja/es (CHECK); ko 외는 원문에서 한 번에 만든 번역"
        VARCHAR(200) title "제목 | N | 사용자 언어 제목"
        VARCHAR(300) subtitle "부제 | Y | 화면 부제"
        VARCHAR(500) summary "요약 | Y | 목록·추천 카드용"
        TEXT body "본문 | N | 문단 구분 빈 줄"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now(); ko 행보다 이르면 원문 수정 후 재번역 안 된 행"
    }

    curation_article_menus["큐레이션 글-메뉴 연결 (curation_article_menus)"] {
        BIGINT article_id PK,FK "글 ID | N | curation_articles.id; CASCADE [P4]"
        BIGINT menu_id PK,FK "메뉴 ID | N | menus.id; RESTRICT; (menu_id, article_id) 인덱스"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
    }

    meal_reviews["식사 후기 (meal_reviews)"] {
        UUID id PK "후기 ID | N | UUID; 후기 카드 1장 = 공개 API reviewId; 수정·삭제·신고 단위"
        UUID user_id FK "작성자 ID | N | users FK CASCADE; 공개 응답에는 넣지 않음(mine 여부만)"
        BIGINT store_id FK "가게 ID | N | 필수 — 후기는 식당에 연결되는 데이터"
        UUID scan_session_id FK,UK "스캔 ID | Y | 선택; 결과 화면에서 쓴 후기만 연결(스캔당 1건 UNIQUE); (scan_session_id, user_id) → scan_sessions 복합 FK CASCADE — 내 스캔만"
        TIMESTAMPTZ hidden_at "숨김 시각 | Y | NULL=공개; 관리자 숨김(신고 검토 포함); 신고만으로 숨기지 않음"
        TIMESTAMPTZ created_at "작성 시각 | N | 가게 후기 최신순 정렬 기준"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    meal_review_items["식사 후기 메뉴 줄 (meal_review_items)"] {
        UUID id PK "후기 줄 ID | N | UUID"
        UUID review_id FK,UK "후기 ID | N | meal_reviews FK CASCADE"
        UUID menu_analysis_id FK "스캔 메뉴 | Y | 선택; 스캔 결과에서 고른 메뉴면 채움, 직접 입력이면 NULL; 메뉴 분석 삭제 시 SET NULL; 같은 스캔인지는 앱이 확인"
        VARCHAR(255) menu_name "메뉴명 | N | 스캔 메뉴의 메뉴판 표기 또는 사용자가 직접 입력한 이름"
        VARCHAR(255) menu_name_normalized UK "메뉴 정규형 | Y | GENERATED; 생성 컬럼 normalize_food_name(menu_name); 후기당 메뉴 1줄(UNIQUE); 가게 후기 메뉴 필터 묶음 키"
        TEXT comment "더 남기고 싶은 말 | Y | 선택, 500자 이내, 빈 문자열 금지(NULL)"
        VARCHAR(5) comment_locale "원문 언어 | Y | comment 가 있으면 필수; ko/en/ar/zh-CN/zh-TW/ja/es; text_translations 키 sha256(locale:원문)"
        TIMESTAMPTZ created_at "생성 시각 | N | 기본 now()"
        TIMESTAMPTZ updated_at "수정 시각 | N | 기본 now()"
    }

    meal_review_answers["식사 후기 식단 응답 (meal_review_answers)"] {
        UUID review_item_id PK,FK "후기 줄 ID | N | meal_review_items FK CASCADE"
        VARCHAR(30) diet_code PK "식단 항목 | N | 화면 식단 항목 ID 와 같은 값: vegan:*·religion:*·allergy:*(식약처 19종)·preference:no-spicy/no-alcohol (CHECK); 식단 필터 키"
        VARCHAR(10) answer "응답 | N | present(있었어요)/absent(없었어요)/unknown(잘 모르겠어요); 미응답은 행 없음"
    }

    meal_review_reports["후기 신고 (meal_review_reports)"] {
        UUID id PK "신고 ID | N | UUID"
        UUID review_id FK,UK "신고한 후기 | N | meal_reviews.id; CASCADE; 신고자와 복합 유일(재신고 409)"
        UUID reporter_user_id FK,UK "신고자 ID | N | users.id; CASCADE; 작성자 본인 신고는 앱이 거부(400)"
        VARCHAR(20) reason "사유 | N | inappropriate(부적절)/false_info(허위 정보)/spam(광고·스팸)/other(기타) (CHECK)"
        TEXT detail "상세 | Y | 선택(화면은 사유 선택만), 500자 이내, 빈 문자열 금지(NULL)"
        TIMESTAMPTZ created_at "신고 시각 | N | 기본 now()"
    }

    classDef user fill:#e8f1ff,stroke:#2563eb,color:#10233f
    classDef scan fill:#e3f6ee,stroke:#168451,color:#102b21
    classDef store fill:#f1e9ff,stroke:#7c3aed,color:#29164f
    classDef knowledge fill:#fff1e6,stroke:#ea580c,color:#431407
    classDef evidence fill:#fee2e2,stroke:#dc2626,color:#450a0a
    classDef workflow fill:#fde7f3,stroke:#db2777,color:#500724
    classDef content fill:#ecfccb,stroke:#65a30d,color:#1a2e05
    classDef review fill:#e0f7fa,stroke:#0891b2,color:#083344
    class users,user_auth_identities,user_sessions,user_profiles,user_roles,user_consent_decisions user
    class scan_sessions,scan_images,scan_records,scan_ai_results,menu_analyses,saved_cards,favorite_menus,text_translations scan
    class store_categories,ksic_codes,store_import_batches,stores,brand_aliases,store_external_refs,store_menus,areas store
    class menu_categories,menus,menu_aliases,ingredients,ingredient_aliases,ingredient_compositions,recipe_ingredients,knowledge_import_batches,menu_recipe_corpora,menu_ingredient_priors,menu_ingredient_cache knowledge
    class ingredient_confirmations,flag_confirmations,staff_questions,ingredient_evidence_log,source_reliability evidence
    class web_search_sources,knowledge_review_items,knowledge_review_sources,ai_command_inbox,admin_audit_logs workflow
    class image_assets,curation_articles,curation_article_translations,curation_article_menus content
    class meal_reviews,meal_review_items,meal_review_answers,meal_review_reports review
```

## 다이어그램 밖 규칙 (카탈로그에서 생성)

CHECK, 부분·식 인덱스, 생성 컬럼, 특수 FK 동작처럼 erDiagram 으로 그릴 수 없는 규칙을 테이블별로 모두 적는다. 원문 SQL 은 해당 Flyway 파일(V1~V14)이다.

### 사용자·약관

**`users`** — 사용자

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_users_email`: `(email)` |
| CHECK | `ck_users_language_code`: `(((language_code)::text = ANY ((ARRAY['ko'::character varying, 'en'::character varying, 'ar'::character varying, 'zh-CN'::character varying, 'zh-TW'::character varying, 'ja'::character varying, 'es'::character varying])::text[])))` |

**`user_auth_identities`** — 사용자 인증 식별자

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_user_auth_identities_provider`: `(provider, provider_id)` |
| 인덱스 | `idx_user_auth_identities_user`: `btree (user_id)` |

**`user_sessions`** — 사용자 로그인 세션

| 종류 | 내용 |
|---|---|
| 인덱스 | `idx_user_sessions_user`: `btree (user_id)` |
| UNIQUE 인덱스 | `uq_user_sessions_refresh_token_hash`: `btree (refresh_token_hash)` |

**`user_profiles`** — 사용자 식단 프로필

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_user_profiles_user`: `(user_id)` |
| CHECK | `ck_user_profiles_allergy_codes`: `((is_sorted_distinct(allergy_codes) AND (allergy_codes <@ ARRAY['egg'::character varying, 'milk'::character varying, 'buckwheat'::character varying, 'peanut'::character varying, 'soybean'::character varying, 'wheat'::character varying, 'mackerel'::character varying, 'crab'::character varying, 'shrimp'::character varying, 'pork'::character varying, 'peach'::character varying, 'tomato'::character varying, 'sulfite'::character varying, 'walnut'::character varying, 'chicken'::character varying, 'beef'::character varying, 'squid'::character varying, 'shellfish'::character varying, 'pinenut'::character varying])))` |
| CHECK | `ck_user_profiles_religion_type`: `(((religion_type IS NULL) OR ((religion_type)::text = ANY ((ARRAY['halal'::character varying, 'kosher'::character varying, 'hindu'::character varying, 'none'::character varying])::text[]))))` |
| CHECK | `ck_user_profiles_vegetarian_type`: `(((vegetarian_type IS NULL) OR ((vegetarian_type)::text = ANY ((ARRAY['vegan'::character varying, 'lacto'::character varying, 'ovo'::character varying, 'lacto_ovo'::character varying, 'pesco'::character varying])::text[]))))` |

**`user_roles`** — 사용자 역할

| 종류 | 내용 |
|---|---|
| CHECK | `ck_user_roles_role`: `(((role)::text = 'admin'::text))` |

**`user_consent_decisions`** — 약관 동의 이력

| 종류 | 내용 |
|---|---|
| CHECK | `ck_consent_document_id`: `(((document_id)::text = ANY ((ARRAY['service'::character varying, 'privacy'::character varying, 'sensitive'::character varying, 'marketing'::character varying])::text[])))` |
| 인덱스 | `idx_user_consent_decisions_latest`: `btree (user_id, document_id, decided_at DESC)` |

### 스캔·결과·찜

**`scan_sessions`** — 메뉴판 스캔 세션

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_scan_sessions_id_store`: `(id, store_id)` |
| UNIQUE | `uq_scan_sessions_id_user`: `(id, user_id)` |
| CHECK | `ck_scan_sessions_counts`: `((((danger_count IS NULL) OR (danger_count >= 0)) AND ((caution_count IS NULL) OR (caution_count >= 0)) AND ((safe_count IS NULL) OR (safe_count >= 0)) AND ((menu_count IS NULL) OR (menu_count >= 0))))` |
| CHECK | `ck_scan_sessions_diet_snapshot`: `(((diet_snapshot IS NULL) OR ((jsonb_typeof(diet_snapshot) = 'object'::text) AND (diet_snapshot ?& ARRAY['religion_type'::text, 'is_vegetarian'::text, 'vegetarian_type'::text, 'no_alcohol'::text, 'allergies'::text, 'no_spicy'::text]) AND ((diet_snapshot - ARRAY['religion_type'::text, 'is_vegetarian'::text, 'vegetarian_type'::text, 'no_alcohol'::text, 'allergies'::text, 'no_spicy'::text]) = '{}'::jsonb) AND (jsonb_typeof((diet_snapshot -> 'allergies'::text)) = 'array'::text))))` |
| CHECK | `ck_scan_sessions_locale`: `(((locale IS NULL) OR ((locale)::text = ANY ((ARRAY['ko'::character varying, 'en'::character varying, 'ar'::character varying, 'zh-CN'::character varying, 'zh-TW'::character varying, 'ja'::character varying, 'es'::character varying])::text[]))))` |
| CHECK | `ck_scan_sessions_match_method`: `(((store_match_method IS NULL) OR ((store_match_method)::text = ANY ((ARRAY['gps_candidate'::character varying, 'name_search'::character varying, 'kakao_fallback'::character varying, 'store_detail'::character varying])::text[]))))` |
| CHECK | `ck_scan_sessions_status`: `(((scan_status)::text = ANY ((ARRAY['processing'::character varying, 'completed'::character varying, 'failed'::character varying, 'needs_retake'::character varying])::text[])))` |
| CHECK | `ck_scan_sessions_store_context`: `((((store_id IS NULL) AND (store_name_snapshot IS NULL) AND (store_match_method IS NULL)) OR ((store_id IS NOT NULL) AND (store_name_snapshot IS NOT NULL) AND (btrim((store_name_snapshot)::text) <> ''::text) AND (store_match_method IS NOT NULL))))` |
| CHECK | `ck_scan_sessions_title`: `(((title IS NULL) OR ((btrim((title)::text) <> ''::text) AND (char_length((title)::text) <= 30))))` |
| 인덱스 | `idx_scan_sessions_store`: `btree (store_id) WHERE (store_id IS NOT NULL)` |
| 인덱스 | `idx_scan_sessions_user_recent`: `btree (user_id, created_at DESC, id DESC)` |
| 부분 UNIQUE | `uq_scan_sessions_user_storage_key`: `btree (user_id, storage_key) WHERE (storage_key IS NOT NULL)` |

**`scan_images`** — 스캔 원본 이미지

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_scan_images_session`: `(scan_session_id)` |
| CHECK | `ck_scan_images_source`: `(((source IS NULL) OR ((source)::text = ANY ((ARRAY['camera'::character varying, 'upload'::character varying])::text[]))))` |

**`scan_records`** — 스캔 기록 보관

| 종류 | 내용 |
|---|---|
| CHECK | `ck_scan_records_feedback_object`: `((jsonb_typeof(feedback) = 'object'::text))` |
| CHECK | `ck_scan_records_match_method`: `(((attached_store_match_method IS NULL) OR ((attached_store_match_method)::text = ANY ((ARRAY['gps_candidate'::character varying, 'name_search'::character varying, 'kakao_fallback'::character varying])::text[]))))` |
| CHECK | `ck_scan_records_store_context`: `((((attached_store_id IS NULL) AND (attached_store_name_snapshot IS NULL) AND (attached_store_match_method IS NULL)) OR ((attached_store_id IS NOT NULL) AND (attached_store_name_snapshot IS NOT NULL) AND (btrim((attached_store_name_snapshot)::text) <> ''::text) AND (attached_store_match_method IS NOT NULL))))` |
| 인덱스 | `idx_scan_records_attached_store`: `btree (attached_store_id) WHERE (attached_store_id IS NOT NULL)` |

**`scan_ai_results`** — 스캔 AI 원본

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_scan_ai_results_prepare`: `(prepare_id)` |
| CHECK | `ck_scan_ai_results_bundle_hash`: `(((bundle_hash IS NULL) OR ((bundle_hash)::text ~ '^sha256:[0-9a-f]{64}$'::text)))` |
| CHECK | `ck_scan_ai_results_items`: `((jsonb_typeof(prepared_items) = 'array'::text))` |
| CHECK | `ck_scan_ai_results_judge`: `((((judged_at IS NULL) = (bundle_hash IS NULL)) AND ((judged_at IS NULL) = (knowledge_revision IS NULL)) AND ((judged_at IS NULL) = (judge_evidence IS NULL)) AND ((judged_at IS NULL) = (judge_warnings IS NULL))))` |
| CHECK | `ck_scan_ai_results_judge_json`: `(((judged_at IS NULL) OR ((jsonb_typeof(judge_evidence) = 'array'::text) AND (jsonb_typeof(judge_warnings) = 'array'::text))))` |
| CHECK | `ck_scan_ai_results_judge_raw`: `(((judge_raw IS NULL) OR ((judged_at IS NOT NULL) AND (jsonb_typeof(judge_raw) = 'object'::text))))` |
| CHECK | `ck_scan_ai_results_prepared_hash`: `(((prepared_items_hash)::text ~ '^sha256:[0-9a-f]{64}$'::text))` |
| CHECK | `ck_scan_ai_results_signals`: `((jsonb_typeof(prepare_signals) = 'object'::text))` |
| 인덱스 | `idx_scan_ai_results_judge_raw_purge`: `btree (judged_at) WHERE (judge_raw IS NOT NULL)` |

**`menu_analyses`** — 스캔 메뉴 분석

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_menu_analyses_item`: `(scan_session_id, source_index)` |
| UNIQUE | `uq_menu_analyses_session_link`: `(id, scan_session_id)` |
| CHECK | `ck_menu_analyses_ai_risk_level`: `(((ai_risk_level IS NULL) OR ((ai_risk_level)::text = ANY ((ARRAY['danger'::character varying, 'caution'::character varying, 'safe'::character varying])::text[]))))` |
| CHECK | `ck_menu_analyses_ai_risk_pair`: `(((risk_level IS NULL) = (ai_risk_level IS NULL)))` |
| CHECK | `ck_menu_analyses_evidence_basis`: `(((evidence_basis IS NULL) OR ((evidence_basis)::text = ANY ((ARRAY['confirmed'::character varying, 'estimated'::character varying, 'unknown'::character varying])::text[]))))` |
| CHECK | `ck_menu_analyses_information_status`: `(((information_status IS NULL) OR ((information_status)::text = ANY ((ARRAY['complete'::character varying, 'partial'::character varying, 'none'::character varying])::text[]))))` |
| CHECK | `ck_menu_analyses_is_spicy`: `(((is_spicy IS NULL) OR is_spicy))` |
| CHECK | `ck_menu_analyses_judged_information`: `(((status IS NULL) OR (information_status IS NOT NULL)))` |
| CHECK | `ck_menu_analyses_match`: `(
CASE
    WHEN (menu_match_type IS NULL) THEN (menu_id IS NULL)
    WHEN ((menu_match_type)::text = ANY ((ARRAY['exact'::character varying, 'alias'::character varying, 'fuzzy'::character varying])::text[])) THEN (menu_id IS NOT NULL)
    WHEN ((menu_match_type)::text = 'unmatched'::text) THEN (menu_id IS NULL)
    ELSE true
END)` |
| CHECK | `ck_menu_analyses_match_type`: `(((menu_match_type IS NULL) OR ((menu_match_type)::text = ANY ((ARRAY['exact'::character varying, 'alias'::character varying, 'fuzzy'::character varying, 'runtime_variant'::character varying, 'unmatched'::character varying])::text[]))))` |
| CHECK | `ck_menu_analyses_matched_tags`: `(((matched_tags IS NULL) OR (jsonb_typeof(matched_tags) = 'array'::text)))` |
| CHECK | `ck_menu_analyses_message`: `(((message IS NULL) OR (jsonb_typeof(message) = 'object'::text)))` |
| CHECK | `ck_menu_analyses_normalization`: `(((normalization_status IS NULL) OR ((normalization_status)::text = ANY ((ARRAY['normalized'::character varying, 'ambiguous'::character varying, 'unmatched'::character varying, 'invalid'::character varying])::text[]))))` |
| CHECK | `ck_menu_analyses_price_options`: `((jsonb_typeof(price_options) = 'array'::text))` |
| CHECK | `ck_menu_analyses_recommendation`: `(((recommendation IS NULL) OR (jsonb_typeof(recommendation) = 'object'::text)))` |
| CHECK | `ck_menu_analyses_risk_ingredients`: `(((risk_ingredients IS NULL) OR (jsonb_typeof(risk_ingredients) = 'array'::text)))` |
| CHECK | `ck_menu_analyses_risk_level`: `(((risk_level IS NULL) OR ((risk_level)::text = ANY ((ARRAY['danger'::character varying, 'caution'::character varying, 'safe'::character varying])::text[]))))` |
| CHECK | `ck_menu_analyses_safe_requires_complete`: `((((risk_level)::text IS DISTINCT FROM 'safe'::text) OR (information_status IS NULL) OR (((information_status)::text = 'complete'::text) AND ((staff_answer_status)::text = 'complete'::text))))` |
| CHECK | `ck_menu_analyses_source_index`: `(((source_index IS NULL) OR (source_index >= 0)))` |
| CHECK | `ck_menu_analyses_staff_answer_scope`: `(((store_id IS NOT NULL) OR ((staff_answer_status)::text = 'none'::text)))` |
| CHECK | `ck_menu_analyses_staff_answer_status`: `(((staff_answer_status)::text = ANY ((ARRAY['none'::character varying, 'partial'::character varying, 'complete'::character varying])::text[])))` |
| CHECK | `ck_menu_analyses_staff_cards`: `((jsonb_typeof(staff_cards) = 'array'::text))` |
| CHECK | `ck_menu_analyses_status`: `(((status IS NULL) OR ((status)::text = ANY ((ARRAY['completed'::character varying, 'partial_failed'::character varying, 'failed'::character varying])::text[]))))` |
| CHECK | `ck_menu_analyses_store_menu_scope`: `(((store_menu_id IS NULL) OR (store_id IS NOT NULL)))` |
| FK 동작 | `fk_menu_analyses_store_menu`: `FOREIGN KEY (store_menu_id, store_id) REFERENCES store_menus(id, store_id) ON DELETE SET NULL (store_menu_id)` |
| 인덱스 | `idx_menu_analyses_menu`: `btree (menu_id) WHERE (menu_id IS NOT NULL)` |
| 인덱스 | `idx_menu_analyses_store_menu`: `btree (store_menu_id) WHERE (store_menu_id IS NOT NULL)` |

**`saved_cards`** — 저장한 소통 카드

| 종류 | 내용 |
|---|---|
| CHECK | `ck_saved_cards_text`: `(((jsonb_typeof(card_text) = 'object'::text) AND (card_text ? 'ko'::text)))` |
| CHECK | `ck_saved_cards_type`: `(((card_type)::text = ANY ((ARRAY['order'::character varying, 'ingredient_check'::character varying, 'exclude'::character varying, 'less_spicy'::character varying])::text[])))` |
| 인덱스 | `idx_saved_cards_menu`: `btree (menu_id) WHERE (menu_id IS NOT NULL)` |
| 인덱스 | `idx_saved_cards_scan`: `btree (scan_session_id) WHERE (scan_session_id IS NOT NULL)` |
| 인덱스 | `idx_saved_cards_store_menu`: `btree (store_menu_id) WHERE (store_menu_id IS NOT NULL)` |
| 인덱스 | `idx_saved_cards_user`: `btree (user_id, last_saved_at DESC)` |

**`favorite_menus`** — 메뉴 찜

| 종류 | 내용 |
|---|---|
| 인덱스 | `idx_favorite_menus_scan`: `btree (scan_session_id, menu_analysis_id)` |
| 인덱스 | `idx_favorite_menus_user_recent`: `btree (user_id, created_at DESC, menu_analysis_id DESC)` |

**`text_translations`** — 기계번역 캐시

| 종류 | 내용 |
|---|---|
| CHECK | `ck_text_translations_hash`: `((source_hash ~ '^[0-9a-f]{64}$'::text))` |
| CHECK | `ck_text_translations_locales`: `((((source_locale)::text = ANY ((ARRAY['ko'::character varying, 'en'::character varying, 'ar'::character varying, 'zh-CN'::character varying, 'zh-TW'::character varying, 'ja'::character varying, 'es'::character varying])::text[])) AND ((target_locale)::text = ANY ((ARRAY['ko'::character varying, 'en'::character varying, 'ar'::character varying, 'zh-CN'::character varying, 'zh-TW'::character varying, 'ja'::character varying, 'es'::character varying])::text[])) AND ((source_locale)::text <> (target_locale)::text)))` |
| 인덱스 | `idx_text_translations_created`: `btree (created_at)` |

### 가게·가게 메뉴·동네

**`store_categories`** — 상권 업종 분류

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_store_categories_code`: `(code)` |
| CHECK | `ck_store_categories_level`: `(((level >= 1) AND (level <= 3)))` |
| CHECK | `ck_store_categories_root`: `(((level = 1) = (parent_id IS NULL)))` |
| 인덱스 | `idx_store_categories_parent`: `btree (parent_id)` |

**`store_import_batches`** — 가게 데이터 적재 배치

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_store_import_batches`: `(source, source_version)` |
| CHECK | `ck_store_import_batches_finished_at`: `(((((status)::text = 'running'::text) AND (finished_at IS NULL)) OR (((status)::text = ANY ((ARRAY['completed'::character varying, 'failed'::character varying])::text[])) AND (finished_at IS NOT NULL))))` |
| CHECK | `ck_store_import_batches_row_count`: `((row_count >= 0))` |
| CHECK | `ck_store_import_batches_source`: `(((source)::text = ANY ((ARRAY['sbiz'::character varying, 'localdata'::character varying])::text[])))` |
| CHECK | `ck_store_import_batches_status`: `(((status)::text = ANY ((ARRAY['running'::character varying, 'completed'::character varying, 'failed'::character varying])::text[])))` |
| CHECK | `ck_store_import_batches_version`: `(((source_version)::text ~ '^[0-9]{6}$'::text))` |

**`stores`** — 가게 마스터

| 종류 | 내용 |
|---|---|
| 생성 컬럼 | `name_normalized` = `normalize_store_name((name)::text)` |
| UNIQUE | `uq_stores_sbiz_no`: `(sbiz_store_no)` |
| UNIQUE | `uq_stores_localdata_mgmt_no`: `(localdata_mgmt_no)` |
| CHECK | `ck_stores_inactive_at`: `((((status)::text = 'inactive'::text) = (inactive_at IS NOT NULL)))` |
| CHECK | `ck_stores_localdata_identity`: `((((origin)::text = 'localdata'::text) = (localdata_mgmt_no IS NOT NULL)))` |
| CHECK | `ck_stores_localdata_mgmt_no`: `(((localdata_mgmt_no IS NULL) OR ((localdata_mgmt_no)::text ~ '^[0-9]{7}-[0-9]{3}-[0-9]{4}-[0-9]{5}$'::text)))` |
| CHECK | `ck_stores_lat`: `(((lat >= (33)::double precision) AND (lat <= (39)::double precision)))` |
| CHECK | `ck_stores_lng`: `(((lng >= (124)::double precision) AND (lng <= (132)::double precision)))` |
| CHECK | `ck_stores_name`: `(((btrim((name)::text) <> ''::text) AND ((name)::text = btrim((name)::text))))` |
| CHECK | `ck_stores_name_normalized`: `(((name_normalized)::text <> ''::text))` |
| CHECK | `ck_stores_origin`: `(((origin)::text = ANY ((ARRAY['sbiz'::character varying, 'localdata'::character varying])::text[])))` |
| CHECK | `ck_stores_sbiz_identity`: `((((origin)::text = 'sbiz'::text) = (sbiz_store_no IS NOT NULL)))` |
| CHECK | `ck_stores_status`: `(((status)::text = ANY ((ARRAY['active'::character varying, 'inactive'::character varying])::text[])))` |
| 인덱스 | `idx_stores_batch`: `btree (last_batch_id)` |
| 인덱스 | `idx_stores_category`: `btree (category_id) WHERE ((status)::text = 'active'::text)` |
| 인덱스 | `idx_stores_geo_active`: `gist (ll_to_earth(lat, lng)) WHERE ((status)::text = 'active'::text)` |
| 인덱스 | `idx_stores_image`: `btree (image_asset_id) WHERE (image_asset_id IS NOT NULL)` |
| 인덱스 | `idx_stores_name_trgm`: `gin (name_normalized gin_trgm_ops) WHERE ((status)::text = 'active'::text)` |

**`brand_aliases`** — 브랜드 별칭

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_brand_aliases_variant`: `(variant_normalized)` |
| CHECK | `ck_brand_aliases_values`: `((((variant_normalized)::text <> ''::text) AND ((canonical_normalized)::text <> ''::text) AND ((variant_normalized)::text = normalize_store_name((variant_normalized)::text)) AND ((canonical_normalized)::text = normalize_store_name((canonical_normalized)::text)) AND ((variant_normalized)::text <> (canonical_normalized)::text)))` |

**`store_external_refs`** — 외부 지도 장소 참조

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_store_external_refs_ext`: `(provider, external_id)` |
| UNIQUE | `uq_store_external_refs_store`: `(store_id, provider)` |
| CHECK | `ck_store_external_refs_external_id`: `(((btrim((external_id)::text) <> ''::text) AND ((external_id)::text = btrim((external_id)::text))))` |
| CHECK | `ck_store_external_refs_provider`: `(((provider)::text = 'kakao'::text))` |

**`store_menus`** — 가게 메뉴

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_store_menus_link`: `(id, store_id)` |
| UNIQUE | `uq_store_menus_name`: `(store_id, name_normalized)` |
| CHECK | `ck_store_menus_counts`: `(((seen_count >= 1) AND (mapping_conflict_count >= 0)))` |
| CHECK | `ck_store_menus_display_name`: `((btrim((display_name)::text) <> ''::text))` |
| CHECK | `ck_store_menus_mapping`: `(((mapping_source)::text = ANY ((ARRAY['none'::character varying, 'fuzzy'::character varying, 'name_match'::character varying, 'admin'::character varying])::text[])))` |
| CHECK | `ck_store_menus_mapping_pair`: `((((mapping_source)::text = 'none'::text) = (menu_id IS NULL)))` |
| CHECK | `ck_store_menus_name`: `((((name_normalized)::text <> ''::text) AND ((name_normalized)::text = normalize_food_name((name_normalized)::text))))` |
| CHECK | `ck_store_menus_price_options`: `((jsonb_typeof(price_options) = 'array'::text))` |
| CHECK | `ck_store_menus_seen`: `((last_seen_at >= first_seen_at))` |
| CHECK | `ck_store_menus_status`: `(((status)::text = ANY ((ARRAY['active'::character varying, 'hidden'::character varying])::text[])))` |
| 인덱스 | `idx_store_menus_conflicts`: `btree (updated_at DESC) WHERE (mapping_conflict_count >= 2)` |
| 인덱스 | `idx_store_menus_image`: `btree (image_asset_id) WHERE (image_asset_id IS NOT NULL)` |
| 인덱스 | `idx_store_menus_last_scan`: `btree (last_scan_session_id) WHERE (last_scan_session_id IS NOT NULL)` |
| 인덱스 | `idx_store_menus_menu`: `btree (menu_id) WHERE (menu_id IS NOT NULL)` |

**`areas`** — 동네

| 종류 | 내용 |
|---|---|
| CHECK | `ck_areas_center`: `((((center_lat >= (33)::double precision) AND (center_lat <= (39)::double precision)) AND ((center_lng >= (124)::double precision) AND (center_lng <= (132)::double precision))))` |
| CHECK | `ck_areas_name`: `(((jsonb_typeof(name_i18n) = 'object'::text) AND (name_i18n ? 'ko'::text) AND (jsonb_typeof((name_i18n -> 'ko'::text)) = 'string'::text) AND (btrim((name_i18n ->> 'ko'::text)) <> ''::text) AND ((name_i18n - ARRAY['ko'::text, 'en'::text, 'ar'::text, 'zh-CN'::text, 'zh-TW'::text, 'ja'::text, 'es'::text]) = '{}'::jsonb)))` |
| CHECK | `ck_areas_radius`: `((radius_m > 0))` |
| CHECK | `ck_areas_status`: `(((status)::text = ANY ((ARRAY['active'::character varying, 'hidden'::character varying])::text[])))` |

### 전역 지식

**`menu_categories`** — 메뉴 분류

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_menu_categories_name`: `(name_ko)` |
| CHECK | `ck_menu_categories_name`: `(((btrim((name_ko)::text) <> ''::text) AND ((name_ko)::text = btrim((name_ko)::text))))` |

**`menus`** — 메뉴 마스터

| 종류 | 내용 |
|---|---|
| 생성 컬럼 | `name_normalized` = `normalize_food_name((name_ko)::text)` |
| UNIQUE | `uq_menus_name_normalized`: `(name_normalized)` |
| CHECK | `ck_menus_ambiguity_flags`: `(((ambiguity_flags IS NULL) OR (ambiguity_flags <@ ARRAY['has_unclear_broth'::character varying, 'has_unclear_jeotgal'::character varying, 'has_unclear_seasoning'::character varying, 'has_hidden_animal'::character varying, 'has_variant'::character varying])))` |
| CHECK | `ck_menus_knowledge_revision`: `((knowledge_revision >= 1))` |
| CHECK | `ck_menus_name`: `(((btrim((name_ko)::text) <> ''::text) AND ((name_ko)::text = btrim((name_ko)::text))))` |
| CHECK | `ck_menus_name_i18n`: `(((name_i18n IS NULL) OR ((jsonb_typeof(name_i18n) = 'object'::text) AND ((name_i18n - ARRAY['ko'::text, 'en'::text, 'ar'::text, 'zh-CN'::text, 'zh-TW'::text, 'ja'::text, 'es'::text]) = '{}'::jsonb))))` |
| CHECK | `ck_menus_name_normalized`: `(((name_normalized)::text <> ''::text))` |
| CHECK | `ck_menus_remain_token`: `(((remain_token IS NULL) OR ((btrim((remain_token)::text) <> ''::text) AND ((remain_token)::text = btrim((remain_token)::text)))))` |
| CHECK | `ck_menus_reviewed_metadata`: `((needs_review OR ((category_id IS NOT NULL) AND (ambiguity_flags IS NOT NULL))))` |
| CHECK | `ck_menus_source`: `(((source)::text = ANY ((ARRAY['curated'::character varying, 'web_search_generated'::character varying, 'variant_generated'::character varying])::text[])))` |
| CHECK | `ck_menus_variant_pair`: `(((base_menu_id IS NULL) = (remain_token IS NULL)))` |
| CHECK | `ck_menus_variant_self`: `(((base_menu_id IS NULL) OR (base_menu_id <> id)))` |
| CHECK | `ck_menus_variant_source`: `((((source)::text <> 'variant_generated'::text) OR (base_menu_id IS NOT NULL)))` |
| CHECK | `ck_menus_web_registration`: `(((((source)::text = 'web_search_generated'::text) = (web_registered_at IS NOT NULL)) AND ((web_registered_at IS NULL) = (web_approved_source_count IS NULL)) AND ((web_approved_source_count IS NULL) OR (web_approved_source_count >= 30))))` |
| 인덱스 | `idx_menus_base`: `btree (base_menu_id) WHERE (base_menu_id IS NOT NULL)` |
| 인덱스 | `idx_menus_category`: `btree (category_id) WHERE (category_id IS NOT NULL)` |
| 인덱스 | `idx_menus_image`: `btree (image_asset_id) WHERE (image_asset_id IS NOT NULL)` |
| 인덱스 | `idx_menus_name_trgm`: `gist (name_normalized gist_trgm_ops)` |

**`menu_aliases`** — 메뉴 별칭

| 종류 | 내용 |
|---|---|
| 생성 컬럼 | `alias_normalized` = `normalize_food_name((alias_ko)::text)` |
| UNIQUE | `uq_menu_aliases_alias`: `(alias_normalized)` |
| CHECK | `ck_menu_aliases_alias`: `(((btrim((alias_ko)::text) <> ''::text) AND ((alias_ko)::text = btrim((alias_ko)::text))))` |
| CHECK | `ck_menu_aliases_alias_normalized`: `(((alias_normalized)::text <> ''::text))` |
| 인덱스 | `idx_menu_aliases_menu`: `btree (menu_id)` |
| 인덱스 | `idx_menu_aliases_trgm`: `gist (alias_normalized gist_trgm_ops)` |

**`ingredients`** — 재료 마스터

| 종류 | 내용 |
|---|---|
| 생성 컬럼 | `name_normalized` = `normalize_food_name((name_ko)::text)` |
| UNIQUE | `uq_ingredients_name_normalized`: `(name_normalized)` |
| CHECK | `ck_ingredients_name`: `(((btrim((name_ko)::text) <> ''::text) AND ((name_ko)::text = btrim((name_ko)::text))))` |
| CHECK | `ck_ingredients_name_i18n`: `(((name_i18n IS NULL) OR ((jsonb_typeof(name_i18n) = 'object'::text) AND ((name_i18n - ARRAY['ko'::text, 'en'::text, 'ar'::text, 'zh-CN'::text, 'zh-TW'::text, 'ja'::text, 'es'::text]) = '{}'::jsonb))))` |
| CHECK | `ck_ingredients_name_normalized`: `(((name_normalized)::text <> ''::text))` |
| CHECK | `ck_ingredients_tag_codes`: `((is_sorted_distinct(tag_codes) AND (tag_codes <@ ARRAY['is_egg'::character varying, 'is_milk'::character varying, 'is_buckwheat'::character varying, 'is_peanut'::character varying, 'is_soybean'::character varying, 'is_wheat'::character varying, 'is_mackerel'::character varying, 'is_crab'::character varying, 'is_shrimp'::character varying, 'is_pork'::character varying, 'is_peach'::character varying, 'is_tomato'::character varying, 'is_sulfite'::character varying, 'is_walnut'::character varying, 'is_chicken'::character varying, 'is_beef'::character varying, 'is_squid'::character varying, 'is_shellfish'::character varying, 'is_pinenut'::character varying, 'is_fish'::character varying, 'is_duck'::character varying, 'is_alcohol'::character varying])))` |
| CHECK | `ck_ingredients_taxonomy`: `(((taxonomy_category)::text = ANY ((ARRAY['fermented'::character varying, 'broth'::character varying, 'sauce'::character varying, 'processed'::character varying, 'seasoning'::character varying, 'oil'::character varying, 'garnish'::character varying, 'base'::character varying, 'unknown'::character varying])::text[])))` |

**`ingredient_aliases`** — 재료 별칭

| 종류 | 내용 |
|---|---|
| 생성 컬럼 | `alias_normalized` = `normalize_food_name((alias_ko)::text)` |
| UNIQUE | `uq_ingredient_aliases_alias`: `(alias_normalized)` |
| CHECK | `ck_ingredient_aliases_alias`: `(((btrim((alias_ko)::text) <> ''::text) AND ((alias_ko)::text = btrim((alias_ko)::text))))` |
| CHECK | `ck_ingredient_aliases_alias_normalized`: `(((alias_normalized)::text <> ''::text))` |
| 인덱스 | `idx_ingredient_aliases_ingredient`: `btree (ingredient_id)` |

**`ingredient_compositions`** — 재료 구성 간선

| 종류 | 내용 |
|---|---|
| CHECK | `ck_ingredient_compositions_flag`: `(((ambiguity_flag IS NULL) OR ((ambiguity_flag)::text = ANY ((ARRAY['has_unclear_broth'::character varying, 'has_unclear_jeotgal'::character varying, 'has_unclear_seasoning'::character varying, 'has_hidden_animal'::character varying])::text[]))))` |
| CHECK | `ck_ingredient_compositions_not_self`: `((parent_ingredient_id <> child_ingredient_id))` |
| CHECK | `ck_ingredient_compositions_relation`: `(((relation_type)::text = 'part_of'::text))` |
| CHECK | `ck_ingredient_compositions_tag_codes`: `((is_sorted_distinct(tag_codes) AND (tag_codes <@ ARRAY['is_egg'::character varying, 'is_milk'::character varying, 'is_buckwheat'::character varying, 'is_peanut'::character varying, 'is_soybean'::character varying, 'is_wheat'::character varying, 'is_mackerel'::character varying, 'is_crab'::character varying, 'is_shrimp'::character varying, 'is_pork'::character varying, 'is_peach'::character varying, 'is_tomato'::character varying, 'is_sulfite'::character varying, 'is_walnut'::character varying, 'is_chicken'::character varying, 'is_beef'::character varying, 'is_squid'::character varying, 'is_shellfish'::character varying, 'is_pinenut'::character varying, 'is_fish'::character varying, 'is_duck'::character varying, 'is_alcohol'::character varying])))` |
| 인덱스 | `idx_ingredient_compositions_child`: `btree (child_ingredient_id)` |

**`recipe_ingredients`** — 메뉴 레시피 재료

| 종류 | 내용 |
|---|---|
| CHECK | `ck_recipe_ingredients_evidence`: `(((evidence_type)::text = ANY ((ARRAY['explicit'::character varying, 'variant'::character varying, 'name_rule'::character varying, 'web_search'::character varying])::text[])))` |
| 인덱스 | `idx_recipe_ingredients_ingredient`: `btree (ingredient_id)` |

**`knowledge_import_batches`** — 지식 적재 배치

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_knowledge_import_batches`: `(source, source_version)` |
| CHECK | `ck_knowledge_import_batches_finished_at`: `(((((status)::text = 'running'::text) AND (finished_at IS NULL)) OR (((status)::text = ANY ((ARRAY['completed'::character varying, 'failed'::character varying])::text[])) AND (finished_at IS NOT NULL))))` |
| CHECK | `ck_knowledge_import_batches_row_count`: `((row_count >= 0))` |
| CHECK | `ck_knowledge_import_batches_source`: `(((source)::text = ANY ((ARRAY['menus_csv'::character varying, 'hidden_rules'::character varying, 'variant_ingredients'::character varying, 'ingredient_rules'::character varying, 'semie_prior'::character varying, 'wtable_prior'::character varying, '10000recipe_prior'::character varying])::text[])))` |
| CHECK | `ck_knowledge_import_batches_status`: `(((status)::text = ANY ((ARRAY['running'::character varying, 'completed'::character varying, 'failed'::character varying])::text[])))` |
| CHECK | `ck_knowledge_import_batches_version`: `(((source_version)::text ~ '^[0-9a-f]{7,40}$'::text))` |

**`menu_recipe_corpora`** — 메뉴별 코퍼스 레시피 수

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_menu_recipe_corpora_n`: `(menu_id, corpus, n_total)` |
| CHECK | `ck_menu_recipe_corpora_corpus`: `(((corpus)::text = ANY ((ARRAY['semie'::character varying, 'wtable'::character varying, '10000recipe'::character varying])::text[])))` |
| CHECK | `ck_menu_recipe_corpora_n`: `((n_total > 0))` |
| 인덱스 | `idx_menu_recipe_corpora_batch`: `btree (import_batch_id)` |

**`menu_ingredient_priors`** — 메뉴×재료 코퍼스 관측

| 종류 | 내용 |
|---|---|
| CHECK | `ck_menu_ingredient_priors_k`: `(((k_count >= 1) AND (k_count <= n_total)))` |
| FK 동작 | `fk_menu_ingredient_priors_corpus`: `FOREIGN KEY (menu_id, corpus, n_total) REFERENCES menu_recipe_corpora(menu_id, corpus, n_total) ON UPDATE CASCADE ON DELETE CASCADE` |
| 인덱스 | `idx_menu_ingredient_priors_ingredient`: `btree (ingredient_id)` |

**`menu_ingredient_cache`** — 메뉴 재료 확장 캐시

| 종류 | 내용 |
|---|---|
| CHECK | `ck_menu_ingredient_cache_revision`: `((menu_revision >= 1))` |
| CHECK | `ck_menu_ingredient_cache_snapshot`: `((jsonb_typeof(ingredients_snapshot) = 'array'::text))` |

### 직원 응답·가게 증거

**`ingredient_confirmations`** — 재료 확정값

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_ingredient_confirmations`: `(store_menu_id, ingredient_id)` |
| UNIQUE | `uq_ingredient_confirmations_link`: `(id, store_menu_id, ingredient_id)` |
| CHECK | `ck_ingredient_confirmations_invalidated`: `(((invalidated_at IS NULL) OR (invalidated_at >= confirmed_at)))` |
| CHECK | `ck_ingredient_confirmations_source`: `(((source)::text = 'staff_confirmed'::text))` |

**`flag_confirmations`** — 메뉴 애매함 플래그 확정값

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_flag_confirmations`: `(store_menu_id, ambiguity_flag, asked_tags)` |
| UNIQUE | `uq_flag_confirmations_link`: `(id, store_menu_id, ambiguity_flag)` |
| CHECK | `ck_flag_confirmations_flag`: `(((ambiguity_flag)::text = ANY ((ARRAY['has_unclear_broth'::character varying, 'has_unclear_jeotgal'::character varying, 'has_unclear_seasoning'::character varying, 'has_hidden_animal'::character varying, 'has_variant'::character varying])::text[])))` |
| CHECK | `ck_flag_confirmations_invalidated`: `(((invalidated_at IS NULL) OR (invalidated_at >= confirmed_at)))` |
| CHECK | `ck_flag_confirmations_tags`: `(((cardinality(asked_tags) > 0) AND is_sorted_distinct(asked_tags) AND (asked_tags <@ ARRAY['is_egg'::character varying, 'is_milk'::character varying, 'is_buckwheat'::character varying, 'is_peanut'::character varying, 'is_soybean'::character varying, 'is_wheat'::character varying, 'is_mackerel'::character varying, 'is_crab'::character varying, 'is_shrimp'::character varying, 'is_pork'::character varying, 'is_peach'::character varying, 'is_tomato'::character varying, 'is_sulfite'::character varying, 'is_walnut'::character varying, 'is_chicken'::character varying, 'is_beef'::character varying, 'is_squid'::character varying, 'is_shellfish'::character varying, 'is_pinenut'::character varying, 'is_fish'::character varying, 'is_duck'::character varying, 'is_alcohol'::character varying])))` |

**`staff_questions`** — 직원 질문

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_staff_questions_card`: `(scan_session_id, card_id)` |
| CHECK | `ck_staff_questions_answer`: `(((answer IS NULL) OR ((answer)::text = ANY ((ARRAY['present'::character varying, 'absent'::character varying, 'unknown'::character varying])::text[]))))` |
| CHECK | `ck_staff_questions_answered`: `(((((status)::text = 'answered'::text) = ((answer IS NOT NULL) AND (answered_at IS NOT NULL))) AND (((status)::text = 'answered'::text) OR ((answer IS NULL) AND (answered_at IS NULL)))))` |
| CHECK | `ck_staff_questions_flag`: `(((ambiguity_flag IS NULL) OR ((ambiguity_flag)::text = ANY ((ARRAY['has_unclear_broth'::character varying, 'has_unclear_jeotgal'::character varying, 'has_unclear_seasoning'::character varying, 'has_hidden_animal'::character varying, 'has_variant'::character varying])::text[]))))` |
| CHECK | `ck_staff_questions_question`: `(((jsonb_typeof(question_text) = 'object'::text) AND (question_text ? 'ko'::text) AND ((question_text - ARRAY['ko'::text, 'en'::text, 'ar'::text, 'zh-CN'::text, 'zh-TW'::text, 'ja'::text, 'es'::text]) = '{}'::jsonb)))` |
| CHECK | `ck_staff_questions_resolved`: `(
CASE
    WHEN (((status)::text = 'answered'::text) AND ((answer)::text = ANY ((ARRAY['present'::character varying, 'absent'::character varying])::text[])) AND (ingredient_id IS NOT NULL)) THEN ((resolved_confirmation_id IS NOT NULL) AND (resolved_flag_confirmation_id IS NULL))
    WHEN (((status)::text = 'answered'::text) AND ((answer)::text = ANY ((ARRAY['present'::character varying, 'absent'::character varying])::text[]))) THEN ((resolved_flag_confirmation_id IS NOT NULL) AND (resolved_confirmation_id IS NULL))
    ELSE ((resolved_confirmation_id IS NULL) AND (resolved_flag_confirmation_id IS NULL))
END)` |
| CHECK | `ck_staff_questions_status`: `(((status)::text = ANY ((ARRAY['pending'::character varying, 'answered'::character varying, 'expired'::character varying])::text[])))` |
| CHECK | `ck_staff_questions_tags`: `((((ambiguity_flag IS NULL) AND (asked_tags IS NULL)) OR ((ambiguity_flag IS NOT NULL) AND (asked_tags IS NOT NULL) AND (cardinality(asked_tags) > 0) AND is_sorted_distinct(asked_tags))))` |
| CHECK | `ck_staff_questions_target`: `((num_nonnulls(ingredient_id, ambiguity_flag) = 1))` |
| FK 동작 | `fk_staff_questions_scan`: `FOREIGN KEY (scan_session_id, store_id) REFERENCES scan_sessions(id, store_id) ON DELETE SET NULL (scan_session_id)` |
| 인덱스 | `idx_staff_questions_store_menu`: `btree (store_menu_id, status)` |
| 부분 UNIQUE | `uq_staff_questions_flag`: `btree (scan_session_id, store_menu_id, ambiguity_flag, asked_tags) WHERE (ambiguity_flag IS NOT NULL)` |
| 부분 UNIQUE | `uq_staff_questions_ingredient`: `btree (scan_session_id, store_menu_id, ingredient_id) WHERE (ingredient_id IS NOT NULL)` |

**`ingredient_evidence_log`** — 재료 증거 로그

| 종류 | 내용 |
|---|---|
| CHECK | `ck_ingredient_evidence_log_delta`: `(((delta_alpha > (0)::numeric) AND (delta_beta = (0)::numeric)))` |
| CHECK | `ck_ingredient_evidence_log_source`: `(((source_type)::text = 'ocr_variant_tag'::text))` |
| 인덱스 | `idx_ingredient_evidence_log_key`: `btree (store_id, menu_id, ingredient_id, id)` |
| 인덱스 | `idx_ingredient_evidence_log_prior`: `btree (store_id, menu_id, ingredient_id) INCLUDE (delta_alpha, delta_beta) WHERE ((source_type)::text = 'ocr_variant_tag'::text)` |
| UNIQUE 인덱스 | `uq_ingredient_evidence_log_review`: `btree (review_item_id, store_id, menu_id, ingredient_id)` |

**`source_reliability`** — 출처 신뢰도

| 종류 | 내용 |
|---|---|
| 생성 컬럼 | `reliability` = `round((((agree_count + 1))::numeric / ((compared_count + 2))::numeric), 4)` |
| CHECK | `ck_source_reliability_counts`: `(((agree_count >= 0) AND (agree_count <= compared_count)))` |
| CHECK | `ck_source_reliability_type`: `(((source_type)::text ~ '^(corpus:[a-z0-9_]+\|curated\|web_search\|ocr_variant_tag)$'::text))` |

### 운영·검토·명령

**`web_search_sources`** — 웹 검색 원본

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_web_search_sources_name`: `(normalized_menu_name)` |
| CHECK | `ck_web_search_sources_name`: `((((normalized_menu_name)::text <> ''::text) AND ((normalized_menu_name)::text = normalize_food_name((normalized_menu_name)::text))))` |
| CHECK | `ck_web_search_sources_payload`: `((jsonb_typeof(payload) = 'array'::text))` |
| CHECK | `ck_web_search_sources_status`: `(((search_status)::text = ANY ((ARRAY['searched'::character varying, 'unresolved'::character varying])::text[])))` |

**`knowledge_review_items`** — 관리자 검토 항목

| 종류 | 내용 |
|---|---|
| 생성 컬럼 | `proposed_name_normalized` = `normalize_food_name((proposed_name_ko)::text)` |
| CHECK | `ck_knowledge_review_items_name`: `(((btrim((proposed_name_ko)::text) <> ''::text) AND ((proposed_name_ko)::text = btrim((proposed_name_ko)::text))))` |
| CHECK | `ck_knowledge_review_items_payload`: `((jsonb_typeof(payload) = 'object'::text))` |
| CHECK | `ck_knowledge_review_items_resolution`: `(((((status)::text = 'pending'::text) AND (reviewed_at IS NULL) AND (reviewed_by IS NULL) AND (resolved_menu_id IS NULL)) OR (((status)::text = 'auto_approved'::text) AND (reviewed_at IS NOT NULL) AND (reviewed_by IS NULL)) OR (((status)::text = 'approved'::text) AND (reviewed_at IS NOT NULL) AND (((item_type)::text = 'web_search_menu'::text) OR (resolved_menu_id IS NOT NULL))) OR (((status)::text = 'rejected'::text) AND (reviewed_at IS NOT NULL) AND (resolved_menu_id IS NULL))))` |
| CHECK | `ck_knowledge_review_items_review_notes`: `((jsonb_typeof(review_notes) = 'array'::text))` |
| CHECK | `ck_knowledge_review_items_routing`: `(
CASE
    WHEN ((item_type)::text = 'web_search_menu'::text) THEN (((status)::text = 'auto_approved'::text) = (review_notes = '[]'::jsonb))
    ELSE ((status)::text <> 'auto_approved'::text)
END)` |
| CHECK | `ck_knowledge_review_items_status`: `(((status)::text = ANY ((ARRAY['pending'::character varying, 'auto_approved'::character varying, 'approved'::character varying, 'rejected'::character varying])::text[])))` |
| CHECK | `ck_knowledge_review_items_type`: `(((item_type)::text = ANY ((ARRAY['web_search_menu'::character varying, 'runtime_variant'::character varying])::text[])))` |
| CHECK | `ck_knowledge_review_items_variant`: `(((((item_type)::text = 'runtime_variant'::text) = (base_menu_id IS NOT NULL)) AND ((base_menu_id IS NULL) = (remain_token IS NULL))))` |
| CHECK | `ck_knowledge_review_items_web_source_type`: `((((item_type)::text = 'web_search_menu'::text) OR (web_search_source_id IS NULL)))` |
| 인덱스 | `idx_knowledge_review_items_name`: `btree (item_type, proposed_name_normalized, status)` |
| 인덱스 | `idx_knowledge_review_items_pending_recent`: `btree (created_at DESC, id DESC) WHERE ((status)::text = 'pending'::text)` |
| 인덱스 | `idx_knowledge_review_items_resolved_menu`: `btree (resolved_menu_id) WHERE (resolved_menu_id IS NOT NULL)` |
| 인덱스 | `idx_knowledge_review_items_reviewer`: `btree (reviewed_by) WHERE (reviewed_by IS NOT NULL)` |
| 인덱스 | `idx_knowledge_review_items_scan`: `btree (source_scan_session_id) WHERE (source_scan_session_id IS NOT NULL)` |
| 인덱스 | `idx_knowledge_review_items_web_source`: `btree (web_search_source_id) WHERE (web_search_source_id IS NOT NULL)` |
| 인덱스 | `idx_knowledge_review_items_web_unregistered`: `btree (proposed_name_normalized) WHERE (((item_type)::text = 'web_search_menu'::text) AND ((status)::text = ANY ((ARRAY['auto_approved'::character varying, 'approved'::character varying])::text[])) AND (resolved_menu_id IS NULL))` |
| 부분 UNIQUE | `uq_knowledge_review_items_pending`: `btree (item_type, proposed_name_normalized) WHERE (((status)::text = 'pending'::text) AND ((item_type)::text = 'runtime_variant'::text))` |

**`knowledge_review_sources`** — 검토 레시피 출처

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_knowledge_review_sources_url`: `(review_item_id, source_url)` |
| CHECK | `ck_knowledge_review_sources_ingredients`: `((jsonb_typeof(extracted_ingredients) = 'array'::text))` |
| CHECK | `ck_knowledge_review_sources_url`: `(((source_url)::text ~ '^https?://'::text))` |

**`ai_command_inbox`** — AI 명령 수신함

| 종류 | 내용 |
|---|---|
| CHECK | `ck_ai_command_inbox_action`: `(((action)::text = ANY ((ARRAY['create_review_item'::character varying, 'create_owner_verification_request'::character varying, 'record_owner_answer'::character varying, 'update_source_reliability'::character varying, 'save_web_search_cache'::character varying, 'save_menu_ingredient_cache'::character varying])::text[])))` |
| CHECK | `ck_ai_command_inbox_attempts`: `((attempt_count >= 0))` |
| CHECK | `ck_ai_command_inbox_hash`: `((request_hash ~ '^[0-9a-f]{64}$'::text))` |
| CHECK | `ck_ai_command_inbox_key`: `(((idempotency_key)::text ~ '^[A-Za-z0-9:_-]{16,128}$'::text))` |
| CHECK | `ck_ai_command_inbox_payload`: `((jsonb_typeof(payload) = 'object'::text))` |
| CHECK | `ck_ai_command_inbox_result`: `((((status)::text <> 'applied'::text) OR (result IS NOT NULL)))` |
| CHECK | `ck_ai_command_inbox_status`: `(((status)::text = ANY ((ARRAY['received'::character varying, 'applied'::character varying, 'failed'::character varying, 'dead'::character varying])::text[])))` |
| CHECK | `ck_ai_command_inbox_store_scope`: `((((action)::text = ANY ((ARRAY['update_source_reliability'::character varying, 'save_web_search_cache'::character varying, 'save_menu_ingredient_cache'::character varying])::text[])) = (store_id IS NULL)))` |
| 인덱스 | `idx_ai_command_inbox_purge`: `btree (created_at) WHERE ((status)::text = 'applied'::text)` |
| 인덱스 | `idx_ai_command_inbox_retry`: `btree (next_attempt_at) WHERE ((status)::text = ANY ((ARRAY['received'::character varying, 'failed'::character varying])::text[]))` |

**`admin_audit_logs`** — 관리자 감사 로그

| 종류 | 내용 |
|---|---|
| CHECK | `ck_admin_audit_logs_action`: `(((action)::text ~ '^[a-z_]+\.[a-z_]+$'::text))` |
| CHECK | `ck_admin_audit_logs_detail`: `((jsonb_typeof(detail) = 'object'::text))` |
| 인덱스 | `idx_admin_audit_logs_admin`: `btree (admin_user_id, created_at DESC)` |
| 인덱스 | `idx_admin_audit_logs_target`: `btree (target_type, target_id, created_at DESC)` |

### 이미지·큐레이션

**`image_assets`** — 이미지 자산

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_image_assets_sha256`: `(content_sha256)` |
| UNIQUE | `uq_image_assets_storage_key`: `(storage_key)` |
| CHECK | `ck_image_assets_license`: `((((source_type)::text <> 'external_licensed'::text) OR ((author IS NOT NULL) AND (license_code IS NOT NULL) AND (source_url IS NOT NULL))))` |
| CHECK | `ck_image_assets_mime`: `(((mime_type)::text = ANY ((ARRAY['image/jpeg'::character varying, 'image/png'::character varying, 'image/webp'::character varying])::text[])))` |
| CHECK | `ck_image_assets_sha256`: `((content_sha256 ~ '^[0-9a-f]{64}$'::text))` |
| CHECK | `ck_image_assets_size`: `((((width IS NULL) OR (width > 0)) AND ((height IS NULL) OR (height > 0)) AND ((file_size IS NULL) OR (file_size > 0))))` |
| CHECK | `ck_image_assets_source`: `(((source_type)::text = ANY ((ARRAY['team_original'::character varying, 'external_licensed'::character varying, 'public_domain'::character varying])::text[])))` |
| CHECK | `ck_image_assets_status`: `(((status)::text = ANY ((ARRAY['active'::character varying, 'hidden'::character varying])::text[])))` |

**`curation_articles`** — 큐레이션 글

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_curation_articles_issue`: `(issue_no)` |
| UNIQUE | `uq_curation_articles_slug`: `(slug)` |
| CHECK | `ck_curation_articles_audience_tags`: `((audience_tags <@ ARRAY['first_timer'::character varying, 'vegetarian'::character varying, 'vegan'::character varying, 'halal'::character varying, 'kosher'::character varying, 'hindu'::character varying, 'no_spicy'::character varying, 'no_alcohol'::character varying, 'allergy_care'::character varying]))` |
| CHECK | `ck_curation_articles_category`: `(((category_code)::text = ANY ((ARRAY['culture'::character varying, 'restaurant'::character varying, 'food'::character varying, 'tips'::character varying])::text[])))` |
| CHECK | `ck_curation_articles_constraint_tags`: `((constraint_tags <@ ARRAY['is_egg'::character varying, 'is_milk'::character varying, 'is_buckwheat'::character varying, 'is_peanut'::character varying, 'is_soybean'::character varying, 'is_wheat'::character varying, 'is_mackerel'::character varying, 'is_crab'::character varying, 'is_shrimp'::character varying, 'is_pork'::character varying, 'is_peach'::character varying, 'is_tomato'::character varying, 'is_sulfite'::character varying, 'is_walnut'::character varying, 'is_chicken'::character varying, 'is_beef'::character varying, 'is_squid'::character varying, 'is_shellfish'::character varying, 'is_pinenut'::character varying, 'is_fish'::character varying, 'is_duck'::character varying, 'is_alcohol'::character varying, 'is_spicy'::character varying]))` |
| CHECK | `ck_curation_articles_issue`: `(((((status)::text = 'draft'::text) = (issue_no IS NULL)) AND ((issue_no IS NULL) OR (issue_no > 0))))` |
| CHECK | `ck_curation_articles_published`: `((((status)::text = 'draft'::text) = (published_at IS NULL)))` |
| CHECK | `ck_curation_articles_slug`: `(((slug)::text ~ '^[a-z0-9]+(-[a-z0-9]+)*$'::text))` |
| CHECK | `ck_curation_articles_source`: `(((source_title IS NOT NULL) OR ((source_publisher IS NULL) AND (source_url IS NULL) AND (source_license IS NULL))))` |
| CHECK | `ck_curation_articles_status`: `(((status)::text = ANY ((ARRAY['draft'::character varying, 'published'::character varying, 'hidden'::character varying])::text[])))` |
| 인덱스 | `idx_curation_articles_category`: `btree (category_code, published_at DESC, id DESC) WHERE ((status)::text = 'published'::text)` |
| 인덱스 | `idx_curation_articles_cover`: `btree (cover_image_asset_id) WHERE (cover_image_asset_id IS NOT NULL)` |
| 인덱스 | `idx_curation_articles_published`: `btree (published_at DESC, id DESC) WHERE ((status)::text = 'published'::text)` |

**`curation_article_translations`** — 큐레이션 글 언어별 본문

| 종류 | 내용 |
|---|---|
| CHECK | `ck_curation_article_translations_locale`: `(((locale)::text = ANY ((ARRAY['ko'::character varying, 'en'::character varying, 'ar'::character varying, 'zh-CN'::character varying, 'zh-TW'::character varying, 'ja'::character varying, 'es'::character varying])::text[])))` |

**`curation_article_menus`** — 큐레이션 글-메뉴 연결

| 종류 | 내용 |
|---|---|
| 인덱스 | `idx_curation_article_menus_menu`: `btree (menu_id, article_id)` |

### 식사 후기·신고

**`meal_reviews`** — 식사 후기

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_meal_reviews_scan`: `(scan_session_id)` |
| CHECK | `ck_meal_reviews_hidden`: `(((hidden_at IS NULL) OR (hidden_at >= created_at)))` |
| 인덱스 | `idx_meal_reviews_store_recent`: `btree (store_id, created_at DESC, id DESC)` |
| 인덱스 | `idx_meal_reviews_user_recent`: `btree (user_id, created_at DESC, id DESC)` |

**`meal_review_items`** — 식사 후기 메뉴 줄

| 종류 | 내용 |
|---|---|
| 생성 컬럼 | `menu_name_normalized` = `normalize_food_name((menu_name)::text)` |
| UNIQUE | `uq_meal_review_items_menu`: `(review_id, menu_name_normalized)` |
| CHECK | `ck_meal_review_items_comment`: `(((comment IS NULL) OR ((btrim(comment) <> ''::text) AND (char_length(comment) <= 500))))` |
| CHECK | `ck_meal_review_items_comment_locale`: `((((comment IS NULL) = (comment_locale IS NULL)) AND ((comment_locale IS NULL) OR ((comment_locale)::text = ANY ((ARRAY['ko'::character varying, 'en'::character varying, 'ar'::character varying, 'zh-CN'::character varying, 'zh-TW'::character varying, 'ja'::character varying, 'es'::character varying])::text[])))))` |
| CHECK | `ck_meal_review_items_menu_name`: `(((menu_name_normalized)::text <> ''::text))` |
| 인덱스 | `idx_meal_review_items_menu`: `btree (menu_analysis_id) WHERE (menu_analysis_id IS NOT NULL)` |

**`meal_review_answers`** — 식사 후기 식단 응답

| 종류 | 내용 |
|---|---|
| CHECK | `ck_meal_review_answers_answer`: `(((answer)::text = ANY ((ARRAY['present'::character varying, 'absent'::character varying, 'unknown'::character varying])::text[])))` |
| CHECK | `ck_meal_review_answers_diet_code`: `(((diet_code)::text = ANY ((ARRAY['vegan:vegan'::character varying, 'vegan:lacto'::character varying, 'vegan:ovo'::character varying, 'vegan:lacto_ovo'::character varying, 'vegan:pesco'::character varying, 'religion:halal'::character varying, 'religion:kosher'::character varying, 'religion:hindu'::character varying, 'allergy:egg'::character varying, 'allergy:milk'::character varying, 'allergy:buckwheat'::character varying, 'allergy:peanut'::character varying, 'allergy:soybean'::character varying, 'allergy:wheat'::character varying, 'allergy:mackerel'::character varying, 'allergy:crab'::character varying, 'allergy:shrimp'::character varying, 'allergy:pork'::character varying, 'allergy:peach'::character varying, 'allergy:tomato'::character varying, 'allergy:sulfite'::character varying, 'allergy:walnut'::character varying, 'allergy:chicken'::character varying, 'allergy:beef'::character varying, 'allergy:squid'::character varying, 'allergy:shellfish'::character varying, 'allergy:pinenut'::character varying, 'preference:no-spicy'::character varying, 'preference:no-alcohol'::character varying])::text[])))` |

**`meal_review_reports`** — 후기 신고

| 종류 | 내용 |
|---|---|
| UNIQUE | `uq_meal_review_reports_reporter`: `(review_id, reporter_user_id)` |
| CHECK | `ck_meal_review_reports_detail`: `(((detail IS NULL) OR ((btrim(detail) <> ''::text) AND (char_length(detail) <= 500))))` |
| CHECK | `ck_meal_review_reports_reason`: `(((reason)::text = ANY ((ARRAY['inappropriate'::character varying, 'false_info'::character varying, 'spam'::character varying, 'other'::character varying])::text[])))` |
| 인덱스 | `idx_meal_review_reports_reporter`: `btree (reporter_user_id)` |

### VIEW

테이블이 아니라 저장하지 않는 조회 정의다. 다이어그램에는 그리지 않는다.

**`ingredient_risk_scores`** — 가게 재료 확률 (VIEW: 변형 근거 합계 → α·β)

컬럼: `store_id, menu_id, ingredient_id, delta_alpha_sum, delta_beta_sum, alpha, beta, evidence_count, last_evidence_id, updated_at`

```sql
SELECT store_id,
    menu_id,
    ingredient_id,
    sum(delta_alpha) AS delta_alpha_sum,
    sum(delta_beta) AS delta_beta_sum,
    1::numeric + sum(delta_alpha) AS alpha,
    1::numeric + sum(delta_beta) AS beta,
    count(*) AS evidence_count,
    max(id) AS last_evidence_id,
    max(created_at) AS updated_at
   FROM ingredient_evidence_log
  WHERE source_type::text = 'ocr_variant_tag'::text
  GROUP BY store_id, menu_id, ingredient_id;
```
