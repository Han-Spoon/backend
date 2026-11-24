# 스키마 v2 결정 문서

- **스키마의 원본은 Flyway 파일이다.** `src/main/resources/db/migration/V1~V15`. 테이블·컬럼·제약 목록은 [`ERD.md`](ERD.md), 그림은 [`ERD.svg`](ERD.svg).
- Flyway 번호: V1~V6 = MVP(스키마 v1), V7 = 약관 동의(2026-11-14 배포), **V8~V13 = 스키마 v2 확장 단계**, V13.1 = LOCALDATA 가게 식별자(2026-11-20), V14 = 축소 단계(Prepare/Judge 단일 경로, 2026-11-22), V15 = 가게 이름+지점명 검색 인덱스(2026-11-25). 이후 변경은 V16 부터.
- 상태: 2026-11-15 설계 확정. V8~V14 운영 적용 (V14 는 2026-11-23).
- 기준: 프론트 화면(`frontend-webapp-demo`), Notion **API 명세서_V2 (AWS)**, AI 설계 문서 hanspoon-ai `docs/`(schema_version 3.0). 사장님 페이지는 만들지 않는다(11-01).

| 규모 | 테이블 51 (+ VIEW 1) · 컬럼 475 · FK 80 · UNIQUE 45 · CHECK 183 · 인덱스 155 (V15 적용 후) |
|---|---|

---

## 1. 스키마 바꾸는 법

| 시점 | 방법 |
|---|---|
| 변경 | 새 파일을 추가한다(`V16__…sql` 부터). 이미 적용된 파일은 고치지 않는다 |
| 코멘트 | 같은 마이그레이션 안에 `COMMENT ON` 으로 단다. 형식 `'논리명 \| 비고'` |
| 확인 | `./gradlew test` (Testcontainers 로 V1~V15 적용 + JPA `validate` + `SchemaV2MigrationIntegrationTest`) |

`ERD.md`·`ERD.svg` 는 2026-11-15 카탈로그에서 만든 스냅샷이다. 스키마를 바꾸면 `ERD.md` 의 해당 테이블 부분만 손으로 고친다. 설계 단계의 생성 도구·전체 무결성 시나리오(303건)·성능 스크립트는 2026-11-16 에 저장소에서 뺐다. 필요하면 백엔드 담당의 로컬 백업에서 꺼낸다.

---

## 2. 도메인별 결정

### 2-1. 스캔 · AI 원본

| 결정 | 근거 |
|---|---|
| Prepare 원본과 Judge 메타(`knowledge_revision`·`bundle_hash`·`judge_evidence`·`judge_warnings`)는 `scan_ai_results` 한 행. Judge 메타 4개는 함께 채워지도록 CHECK | 판정 재현 정보가 한 곳에 모이고 이력 테이블이 가벼워진다 |
| `judge_raw`(JudgeResponse 전체)는 판정 후 90일만 보관 | AI 가 필드를 늘려도 원본이 남는다. 근거·경고는 별도 컬럼에 장기 보관 |
| AI `item_id` 컬럼 없음 → `UNIQUE (scan_session_id, source_index)` | AI 계약상 `{scan_session_id}:{source_index}` 파생값 |
| 해시 `VARCHAR(71)` + `^sha256:[0-9a-f]{64}$` | AI 계약의 접두사 포함 형식 |
| `message` 는 `scan_sessions.locale` 한 언어 | AI 는 요청 언어로만 쓴다. 다른 언어는 번역 캐시 |
| 위험도별 카운터 `danger_count`·`caution_count`·`safe_count` (`risky_menu_count` 는 V14 에서 삭제) | 이력 목록을 집계 없이 읽는다 |
| `display_order` 는 백엔드가 Judge 결과로 계산해 저장. `recommendation` 은 보류(NULL) | AI 계약에 없다. 추천은 쓰는 화면이 없다 |
| 스캔 시작 때 Judge `user_profile` 을 `diet_snapshot` 에 저장. 프로필이 없으면 스캔을 시작하지 않는다 | 결과 화면 '내 식단'·후기 식단 항목이 스캔 당시 기준이어야 한다. 프로필 없는 판정은 FN-safe 가 아니다 |
| 기록 제목 `title` 은 사용자가 고친 값만(미편집 NULL), 30자 CHECK 는 V14 | 마이페이지 연필 아이콘 |
| `scan_records` 유지 | 화면의 "기록 저장하기"와 식당 없는 스캔의 식당 연결·해제. `feedback` 은 새로 쓰지 않는다 |
| 진행 단계 컬럼 없음 | 1단계는 `prepared_at` 유무, 2~4단계는 프론트 애니메이션 |
| 원본 사진 만료는 `scan_images.created_at + 90일` 로 계산 | S3 수명주기가 90일 뒤 지운다 |

### 2-2. 가게 메뉴

| 결정 | 근거 |
|---|---|
| 표준 메뉴(`menus`) 아래 가게 메뉴(`store_menus`, `UNIQUE (store_id, name_normalized)`) | 같은 메뉴도 가게마다 표기·재료가 다르다. Prepare 저장은 `INSERT … ON CONFLICT` |
| `menu_analyses.store_id` 사본 + `(scan_session_id, store_id)`·`(store_menu_id, store_id)` 복합 FK | 스캔의 가게 = 가게 메뉴의 가게를 DB 가 강제 |
| 고정 매핑(`name_match`·`admin`)은 자동 교체하지 않고, 다른 결과가 2번 이상이면 관리자 검토 | 확정 매핑이 스캔마다 흔들리지 않게 |
| 영어 메뉴명·설명 컬럼 삭제(V14), 번역명은 `menus.name_i18n` | 언어가 늘어도 컬럼이 늘지 않는다 |

### 2-3. 직원 응답

"직원 승인" = 손님 화면의 질문 카드에 **가게 직원이 버튼으로 답하는 것**이다. 답은 누르는 즉시 가게 공용 확정값에 저장한다(11-09, 11-01). 위험도는 **서버가 위험 쪽으로만** 올린다(11-12).

| 결정 | 근거 |
|---|---|
| 이름은 직원 의미로: `staff_questions`·`flag_confirmations`·`staff_cards`·`staff_answer_status`, 출처 `staff_confirmed`. AI 계약 이름(`owner_*`)은 백엔드가 매핑 | 사장님 기능으로 오해하지 않게 |
| 확정값 키 = `store_menu_id` (+ 재료 / 플래그·태그), `(store_menu_id, store_id)` 복합 FK. 만료 없음 | 답이 표준 메뉴 매핑과 무관하게 그 가게 메뉴에 남는다 |
| 질문은 대상별 부분 UNIQUE 로 스캔당 1개. present/absent 면 확정값 upsert + 링크 필수, unknown 은 링크 금지 | 링크 빠진 답이 생기지 않는다 |
| 잘못 누르면 같은 질문에서 다시 답한다. 모름으로 고치면 링크를 끊고 확정값 `invalidated_at` | 모름은 확정이 아니므로 Judge override 에서 빠져야 한다 |
| AI Judge 를 다시 부르지 않는다. `present` 이고 대상이 내 식단과 겹치면 그 메뉴 `risk_level = danger` + 카운터 갱신. `absent`·`unknown` 은 표시만. **안전 쪽으로 내리지 않는다.** AI 첫 판정은 `ai_risk_level` 에 보존 | AI 설계에 부분 재판정 절차가 없다. 위험 쪽 규칙은 틀려도 과하게 경고하는 쪽이다. 안전 판정은 다음 스캔의 Judge 가 낸다 |
| 빼기·덜 맵게·주문 카드 응답은 저장하지 않는다 | 그 주문 한 번의 요청이라 가게 공용 사실이 아니다 |
| SAFE 는 정보 complete **그리고** 직원 응답 complete 일 때만(CHECK). Judge 행은 `information_status` 필수 | FN-safe 마지막 방어선 |
| 직원 응답은 증거 로그에 넣지 않는다. 답 이력 = `staff_questions` 행 | AI #25 |
| 이상 답변 잠금 없음(`flagged_anomaly`·`override_eligible`·`recipe_probability` 없음) | AI #26·#15 |

### 2-4. 웹 레시피 검토 · 전역 지식 (출시 범위 밖, 테이블만 둠)

| 결정 | 근거 |
|---|---|
| 검색 1회 = 검토 묶음 1개(`knowledge_review_items`) + 레시피(`knowledge_review_sources`). 이상 신호 없으면 `auto_approved`, 있으면 관리자 예/아니오 | 10-26 회의: 자동 등록이 기본 |
| 같은 메뉴명 승인 레시피 URL 30개면 `menus` 에 등록(`web_registered_at`) | 등록은 메뉴당 1회 |
| 웹 원본 `web_search_sources` 는 메뉴명당 1행, 이어 붙이기만 | 반려된 레시피도 판정에는 쓰인다 |
| 재료 태그 `tag_codes` 배열(정렬·중복 없음 CHECK) | 고정 코드, 항상 같이 읽고 통째로 바꾼다 |
| 가게 재료 확률 `ingredient_risk_scores` 는 VIEW (변형 근거만 합산) | 재계산 작업이 없다 |
| `menu_ingredient_cache` 는 메뉴당 1행(전역) | 내용이 전역 데이터뿐이다 |
| fuzzy 후보 조회는 GiST trigram KNN | GIN 보다 약 10배 빠르다 |

### 2-5. 로그성 테이블

| 결정 | 근거 |
|---|---|
| `ai_command_inbox.store_id`·`scan_session_id`, `admin_audit_logs.admin_user_id` 는 FK 없이 값만 | 감사 기록은 삭제 후에도 불변. 대용량 수신함이 삭제 연쇄에 끼지 않는다 |
| 판단·증거 테이블의 FK 는 유지 | FN 방지 장치 |
| 수신함은 전역 명령 3종만 `store_id IS NULL`, `applied` 는 90일 뒤 정리 | |

### 2-6. 사용자 · 약관

| 결정 | 근거 |
|---|---|
| 약관 동의는 V7 `user_consent_decisions` (문서 4종 × 버전 문자열, 결정마다 한 줄). 원문·필수 여부는 `legal/{버전}.json` | 이미 배포돼 화면이 쓴다. 원문이 JSON 한 곳에 있어 DB 와 어긋날 일이 없다 |
| 보호자 승인 없음 (원격 V8 은 11-16 되돌림) | 14세 미만 정책 미정 |
| 알레르기는 `user_profiles.allergy_codes` 배열(식약처 19종, 기본값 없음). `user_allergies` 삭제 | 조회 1번. 빠뜨린 값이 '없음'으로 저장되지 않게 |
| 관리자 역할 `user_roles` | 하위 역할 가능성 |

### 2-7. 이미지 · 큐레이션 · 번역 (출시 때 화면은 프론트 상수)

| 결정 | 근거 |
|---|---|
| 대표 이미지 1장만: `stores`·`store_menus`·`menus.image_asset_id` | 요구사항이 대표 1장 |
| 큐레이션은 작성·게시·공개/비공개·삭제만. `draft/published/hidden`, `published_at` = 첫 게시 | 10-27 |
| 한국어 원문으로 6개 언어 번역을 한 번에 저장, 언어별 검수 없음 | |
| 태그·메뉴 연결은 추천 계산에만, 열람 제한 없음 | 10-27 |
| 글 번호 `issue_no` 는 첫 게시 때 부여, 읽기 시간은 계산 | 번호는 다른 글을 지워도 바뀌면 안 된다 |
| 기계번역 캐시 `text_translations` (키 `sha256(source_locale:원문)` + 대상 언어) | 기록을 다른 언어로 열 때 |
| 스캔 결과 메뉴 사진: 가게 메뉴 사진 → 없으면 판정된 표준 메뉴(`menu_analyses.menu_id`) 이미지. 둘 다 `active` 만 | 가게 사진이 실제 메뉴에 가깝다. 판정 안 된 메뉴엔 표준 이미지 없음 |
| 표준 메뉴 이미지 출처: Wikimedia Commons(CC0·CC BY·CC BY-SA) + 허락받은 사진. 출처 문구는 `attribution_text` | 네이버·블로그 사진은 허락 없음, 가게 허락 사진은 그 가게 메뉴만 |
| 표준 메뉴 이미지 연결은 메뉴명 기준(`scripts/data/standard_menu_images.csv`). 없는 메뉴명은 대기, 시드 재적재 뒤 `publish_standard_menu_images.py` 다시 실행 | 메뉴 id 는 AI 시드가 정한다. 임시 `menus` 행은 시드 id 와 충돌 |
| 경로 `menus/standard/<sha256>.jpg`. 이미지 있는 메뉴는 `--replace` 때만 교체 | 내용 주소라 덮어쓰기 없음 |

### 2-8. 식사 후기 · 신고 (2026-11-15 화면대로 재설계)

| 테이블 | 키 | 핵심 |
|---|---|---|
| `meal_reviews` | `id` | `user_id`(탈퇴 CASCADE), `store_id` 필수, `scan_session_id` 선택 + UNIQUE, `(scan_session_id, user_id)` → 내 스캔만(스캔 삭제 CASCADE), `hidden_at` |
| `meal_review_items` | `id` | `menu_analysis_id` 선택(SET NULL), `menu_name` + 정규형, 후기당 같은 메뉴 1줄, `comment` ≤ 500 + `comment_locale` |
| `meal_review_answers` | `(review_item_id, diet_code)` | `vegan:*` 5 · `religion:*` 3 · `allergy:*` 19 · `preference:no-spicy/no-alcohol` × present/absent/unknown. 미응답은 행 없음 |
| `meal_review_reports` | `id` | `(review_id, reporter_user_id)` 유일(재신고 409), 사유 4종, 상세 선택, 처리 상태 없음 |

- 스캔 없이도 쓴다(가게 상세). 메뉴는 스캔 메뉴 또는 직접 입력. 후기 카드·"후기 N개"·수정·삭제·신고·숨김 모두 후기 단위.
- 식단 코드는 화면 ID 와 같은 값이라 변환이 없다.
- 가게 후기 수는 저장하지 않고 집계(`hidden_at IS NULL`).
- 신고는 저장만 하고 숨기지 않는다(11-03). 숨김 = `hidden_at` + 감사 로그.

### 2-9. 메뉴 찜 · 동네

| 결정 | 근거 |
|---|---|
| `favorite_menus` PK `(user_id, menu_analysis_id)`, 두 복합 FK 로 **내 스캔의 메뉴만** | 11-03. 남의 메뉴 ID 를 넣어도 DB 가 거부 |
| 스캔 기록을 지우면 찜도 CASCADE | 11-09 |
| 동네 `areas` = 다국어 이름 + 중심 좌표 + 반경, 연결 테이블 없음 | 11-01 반경 자동 배정 |

---

## 3. 설계 원칙

| 원칙 | 적용 |
|---|---|
| FN-safe | "안전해 보이는" 기본값을 두지 않는다. 모름은 NULL 또는 행 없음. 정보·직원 응답이 미완이면 SAFE 금지, `is_spicy` 는 true 또는 NULL |
| 코드 값 | `VARCHAR + CHECK` (ENUM 미사용) — 값 추가가 마이그레이션 한 줄 |
| 같은 가게·같은 스캔 | 복합 FK 로 다른 가게·다른 스캔 참조를 막는다 |
| 배열 | `is_sorted_distinct()` CHECK(`COLLATE "C"`)로 표현을 하나로 고정 |
| 이름 비교 | `normalize_food_name()` 생성 컬럼 + UNIQUE/인덱스 |
| NULL 함정 | CHECK 는 NULL 을 통과시킨다. `CASE`·`IS NOT NULL`·JSON 키 `?` 로 쓴다 |
| 제약 이름 | `pk_`·`uq_`·`fk_`·`ck_`·`idx_` + 테이블명 |
| FK 인덱스 | 부모를 지우거나 키를 바꾸는 경로가 있을 때만. 없는 13건: 마스터(`menus`·`ingredients`·`ksic_codes`) RESTRICT 참조, 지우지 않는 `stores`·확정값·증거 로그 참조, 관리자만 참조하는 `users` |

---

## 4. 구현 규칙 (스키마로 표현하지 않는 것)

| 규칙 | 내용 |
|---|---|
| 후보 조회 | 정확·별칭 일치 먼저, 미일치만 `ORDER BY name_normalized <-> :q LIMIT 10` → `ORDER BY similarity DESC, id LIMIT 5` |
| Prepare 저장 | 한 트랜잭션: `scan_ai_results` → 가게 있으면 `store_menus` upsert → `menu_analyses` |
| Judge 저장 | 한 트랜잭션: 판정(첫 판정이면 `ai_risk_level` 도) → `display_order` → 매핑 갱신 → 카운터 → Judge 메타 → 명령은 `ai_command_inbox`(직원 질문 생성만 실행) |
| 표시 순서 | SAFE → CAUTION+estimated → CAUTION+unknown → DANGER, 같은 칸은 `source_index` |
| 스캔 내 추천 | `safe` + `complete` 만. 콘텐츠 태그가 금지 태그와 겹치면 추천 제외 (보류) |
| Bundle 확정값 | 스캔 메뉴의 `store_menu_id` 로 확정값(무효화 제외)을 읽어 후보 메뉴 전부에 붙인다 |
| 직원 응답 | `PUT /scans/{scanId}/staff-questions/{questionId}/answer`(11-23 구현). 한 트랜잭션: 질문·스캔 메뉴·스캔 행 잠금 → present/absent 면 확정값 upsert + 링크, unknown 이면 링크 해제 + 기존 확정값 `invalidated_at` → 메뉴 `risk_level` = 식단과 겹친 `present` 가 있으면 danger, 없으면 `ai_risk_level` → `staff_answer_status`(질문 전부 present/absent 면 complete, 아니면 partial) → 카운터 재계산(`lock_version` 증가). 겹침 대상 태그: 재료 질문 `ingredients.tag_codes`, 플래그 질문 `asked_tags`. 금지 태그는 `diet_snapshot` 으로 AI `profile.py` 표 이식(`DietForbiddenTags`) |
| 직원 응답 예외 | `ai_risk_level = safe` 인데 응답이 complete 가 아니면 caution(SAFE CHECK). `display_order`·`matched_tags` 는 그대로(답하는 중 목록 안 움직임). 오류: 남의 스캔 404 `SCAN_NOT_FOUND`, 없는 질문 404 `STAFF_QUESTION_NOT_FOUND`, 기한 종료 409 `STAFF_QUESTION_EXPIRED`, 가게 없는 스캔 409 `STAFF_QUESTION_REQUIRES_STORE` |
| 직원 응답 충돌 | 다른 스캔의 "없어요"는 "있어요" 확정값을 덮지 않는다 (R-04, 미구현. 지금은 마지막 답이 확정값) |
| 결과의 직원 질문 | 메뉴별 `staffQuestions`: 같은 스캔·같은 `store_menu_id` 질문, 생성 순. 문구·재료명은 ko + 스캔 locale. 가게 없는 스캔은 빈 배열 |
| 직원 질문 생성 | 후보 메뉴가 없는 메뉴와 ID 없는 재료에는 질문을 만들지 않는다 (R-06·R-07) |
| 정리 작업 | `AiRetentionCleanup`(매일 04:30 KST): `judge_raw` 판정 90일 뒤 NULL, `applied` 명령 수신 90일 뒤 삭제. 둘 다 부분 인덱스 사용 |
| 로그 조회 | 관리자 화면 없이 읽기 전용 DB 계정으로 본다. 쓰기 권한 직접 접속 금지 |
| 큐레이션 저장 | `ko` + 6개 언어를 한 트랜잭션, 원문 수정 시 전부 재번역 |
| 약관 | 새 원문은 새 `legal/{버전}.json`. 게시한 파일은 고치지 않는다. 현재 버전의 최신 결정만 유효 |
| 식사 후기 | `/stores/{storeId}/reviews`·`/reviews/{reviewId}`·`/reviews/me`(11-23 구현). 공개 응답에 작성자 정보 없음, 남의 후기는 `scanId`·`scanMenuId` 가림. 목록은 페이지 없이 전체 배열 `created_at DESC, id DESC`, 가게 목록은 `hidden_at IS NULL`, 내 목록은 숨김 포함. 스캔 후기: 내 완료 스캔, 그 스캔 가게(스캔 가게 → 기록 연결 가게)가 경로 가게와 같아야 함(다르면 409, 없으면 422), 스캔당 1건(409). 앱 검증(400): 정규화 이름 중복, 응답도 말도 없는 줄(R-20), 줄의 `menu_analysis_id` 가 그 후기 스캔의 메뉴인지, 식단 코드는 **현재 프로필** 항목만(`ReviewDietCodes`, 채식은 `vegetarian_type`). `comment_locale` = 작성자 `users.language_code` |
| 후기 수정·삭제 | 수정은 메뉴 줄 전체 교체: 정규화 이름이 같은 줄은 id 유지, 빠진 줄 삭제, 줄 순서 = `created_at`(`clock_timestamp()`, 수정 때 요청 순으로 다시 매김), 메모가 그대로면 원래 `comment_locale`. 가게·스캔 연결·숨김은 그대로. 삭제는 줄·응답·신고 CASCADE, 스캔 기록은 남음 |
| 후기 신고 | `POST /reviews/{reviewId}/reports` 204. 숨긴·없는 후기 404, 본인 후기 신고 400, 재신고 409, 저장만 |
| 메뉴 찜 | `/api/v1/saved-menus`(11-23 구현). 요청은 `scanMenuId`(= `menu_analysis_id`) 하나, 서버가 `scan_session_id` 를 채운다. 찜은 내 스캔의 메뉴일 때만 `INSERT … SELECT … ON CONFLICT DO NOTHING`(멱등). 표시값은 스냅샷 없이 조회: 판정은 현재 `risk_level`, 번역명은 결과와 같은 `menus.name_i18n`, 가게는 스캔 가게 → 기록에 연결한 가게. 목록은 전체 배열, 찜한 최신순. 오류: 찜 404 `SCAN_MENU_NOT_FOUND`, 해제 404 `SAVED_MENU_NOT_FOUND` |
| 원본 사진 | `GET /scans/{scanId}/image`(11-23 구현). 내 스캔만, 사진 행 없음도 404 `SCAN_NOT_FOUND`. `created_at + 90일` 부터 410 `SCAN_IMAGE_EXPIRED`. 15분 presigned GET(`S3_READ_URL_TTL`), `object_version_id` 고정. 분석 중 스캔도 조회 |
| 동네 가게 | `earth_box` + `earth_distance ≤ 반경`. 위치가 있으면 거리 → 후기 수, 없으면 후기 많은 순 |
| 가게 이름 검색 | 검색어가 있으면 반경 없이 전국(좌표는 거리에만, `radiusMeters` 무시). 정규형 3글자 이상: 이름+지점명 포함 또는 이름 유사(`%`). 1~2글자: 이름+지점명 전국 접두 또는 반경 3km 안 포함. 메뉴명: 숨김 아닌 `store_menus.name_normalized` 포함(글자 수 무관), 응답 `matchedMenuName`. 빈 정규형은 빈 목록. 정렬: 확인 가게 → 이름 포함 → 메뉴 포함 → 유사, 같은 단계는 거리순(좌표 없으면 일치 → 접두 → 포함 → 짧은 이름, 유사는 유사도순) → 출처 → id. 이름·메뉴 함께 걸리면 앞 단계 한 번. 정렬 거리는 평면 근사, 응답 거리만 구면 거리(상위 행만 계산) |
| 계산 값 | 사진 만료, 큐레이션 읽기 시간, 가게 후기 수 |
| 검증 가게 | `stores.verified_at` 가게는 공공데이터 재적재 시 `name`·`branch_name`·`status` 유지, 비활성 스윕 제외. 시연 가게 표시명·중복 가게는 `scripts/curate_demo_stores.py` |

---

## 5. 배포 (expand–contract)

기존 파이프라인(OCR + 룰엔진)이 `menu_name_en`·단수 `owner_card`·`is_spicy = false`·`risky_menu_count` 를 써서 확장(V8~V13)과 축소(V14)로 나눴다.

| 파일 | 내용 |
|---|---|
| `V8__foundation.sql` | 정규화·배열 함수, 기존 테이블 CHECK, 위험도 카운터·`locale`, `menu_images` → `scan_images`, `saved_cards.text` → `card_text`, 알레르기 배열 이관 + `user_allergies` 삭제 |
| `V9__ai_global_knowledge.sql` | 전역 지식 |
| `V10__ai_workflow.sql` | 관리자 역할 · 웹 원본 · 검토 · 명령 수신함 · 감사 로그 |
| `V11__ai_store_scoped_evidence.sql` | 가게 메뉴 · 확정값 · 직원 질문 · 증거 · VIEW · 캐시 · 출처 신뢰도 |
| `V12__store_menus_and_content.sql` | 스캔 AI 원본, `menu_analyses` 새 컬럼, 저장 카드, 찜, 이미지 · 큐레이션 · 번역 캐시 · 후기 · 신고 · 동네 |
| `V13__comments.sql` | 코멘트 (확장 단계 기준) |
| `V13_1__store_localdata_identity.sql` | `stores.localdata_mgmt_no`(LOCALDATA 관리번호) UNIQUE·형식·출처 CHECK |
| `V14__menu_analyses_contract.sql` | `menu_analyses` 이름 변경·삭제·카드 배열 전환·CHECK, `risky_menu_count` 삭제, 제목 30자 CHECK |
| `V15__store_full_name_search.sql` | `idx_stores_full_name_trgm`(이름+지점명 정규형 GIN trigram, 영업 중만) |
| `V16__store_menu_display_order.sql` | `store_menus.display_order`(수동 표시 순서, NULL은 id 순) |

**배포 절차**: 배포 직전 RDS 수동 스냅샷을 만든다. 되돌릴 때는 스냅샷 복구로 한다(R-08).

**Prepare/Judge 연동은 PR 두 개** (2026-11-22):

- PR 1 (전환, PR #91·#92): `AI_PIPELINE` 토글로 경로 선택, V14 없이 기존 컬럼에 새 형식 저장
- PR 2 (축소, 이슈 #90): V14 Flyway 적용, 기존 AI 연동·토글 제거, 결과 API V2 필드, `judge_raw` 90일 정리

**PR 2 배포 결과** (2026-11-23, PR #93·#94, 태스크 정의 `:129`):

- 배포 전: 운영 Prepare → Judge 1회 완료(합성 메뉴판, prepare 1.7초·judge 4.3초), 제목 위반 0건, RDS 스냅샷 `hanspoon-prod-db-before-v14-20261123`
- Flyway 13.1 → 14 (0.15초). 기존 행 변환 확인(`source_index`, `staff_cards` 배열)
- 프론트는 같이 배포하지 않음. V2 필드를 먼저 읽고 예전 필드는 대체값으로만 쓴다
- 이전 리비전(`:128` 이하)은 삭제된 컬럼을 읽어 기동하지 못한다. 되돌리기는 스냅샷 복구만

---

## 6. 미결 사항

| 분류 | 할 일 |
|---|---|
| 프론트 | V2 필드 호환 확인(11-23). 남은 정리: 예전 필드 대체 코드(`menuNameKo/En`·`descriptionKo/En`·`hits`·`ownerCard`·`riskyMenuCount`) 삭제, 실패 코드 `PREPARE_SERVICE_ERROR`·`JUDGE_SERVICE_ERROR` 문구(지금 기본 문구) |
| PM·프론트 | 직원 응답 규칙(있어요 → 위험, 없어요·모름 → 표시만) 공유. 프론트 문구는 지금 "판정은 자동으로 바뀌지 않는다"로 되어 있다. Notion No 16·23 수정(사용자 확인 후) |
| 화면 쪽 정리 | 환율 "매일 00:00 갱신" 문구(지금은 고정 환율), 출처 간 불일치 표시(AI 값 없음), 주문·빼기·덜 맵게 응답(저장 안 함), 파트너 식당·등록 레시피(시연 전용) |
| AI 팀 | ① Prepare/Judge 타임아웃·Judge 재시도(백엔드 20초·30초, 웹서치로 30초 초과 가능) ② `persistence_commands` 적용 시점·실패 명령 재처리 ③ `recipe_probability` 코드에서 제거 요청(문서는 10/27 철회, 백엔드는 무시) ④ 확정값을 `store_menu_id` 기준으로 후보 메뉴 전부에 넣는 방식 확인 ⑤ ⑦ §8 "백엔드 반영 대기" 항목이 V11 에 반영됐다고 알림 ⑥ 사진 품질 판정: OCR 구조가 좋으면 이미지 경고 무시로 수정, 11-23 운영 반영(AI `19d4222`). 실사진 스캔 재확인 남음 ⑦ 게이트웨이(`OPENAI_BASE_URL`)의 Responses API·모델 이름 확인. LLM 실패는 규칙 판정으로 대체돼 AI 로그로만 보인다 ⑧ AI `deploy-prod.yml` 에 `OPENAI_BASE_URL`·`NORMALIZATION_OPENAI_MODEL` 추가(지금은 태스크 정의·Terraform 에만 있음) |
| 운영 설정 | Naver 웹 검색 키 미설정(실사진 스캔 확인 뒤 결정, 없으면 모르는 메뉴는 `no_information`). 게이트웨이 변수 Terraform 반영·쓰지 않는 백엔드 변수 삭제는 11-23 완료(`:132`) |
| 백엔드 (후속) | `save_web_search_cache`·`update_source_reliability`·`menu_ingredient_cache` 명령 실행과 Bundle 웹 캐시 조회(지금은 저장 안 하고 `not_found`, 모르는 메뉴는 매번 재검색). 한국어 fuzzy 점수 개선(AI 기준 0.65 는 v1 고정) |
| 법률·약관 | 원문의 14세 미만 조항 정리(보호자 절차 없음) + 버전 올림, 위치정보 약관 필요 여부(필요하면 문서 ID CHECK 에 추가), 탈퇴 후 동의 기록 보존 여부(지금 CASCADE, R-17) |
| 인증 | `user_auth_identities` 의 `email_verified` 규칙, `provider` 기본값·CHECK |
| 표준 메뉴 이미지 | 허락받은 사진 7장(수제비사리·부추사리·두부사리·공기밥·야채튀김·낙곱새·알탕) 작성자·원본 링크(지금 `출처 확인 필요`). 고기사리 사진 교체 검토. 공공누리 1유형 공공데이터(data.go.kr 15151269) 보강은 나중에 |

---

## 7. 하지 않은 것

| 하지 않은 것 | 필요해지면 |
|---|---|
| 직원 응답 뒤 AI 재판정 | 질문한 본인도 안전을 봐야 하면. 전체 Judge 재호출 후 그 메뉴만 반영 |
| 빼기·덜 맵게·주문 응답 저장 | 가게 공용 정보로 쓰기로 하면 질문 종류와 확정값 테이블 추가 |
| 가게 후기 수 카운터 | 지도·동네 집계가 느려지면 |
| 신고 처리 상태 | 관리자 신고 검토 화면(3순위)을 만들 때 |
| 대표 이미지 외 갤러리 | 부모별 이미지 연결 테이블 |
| 큐레이션 분류·출처·언어별 검수·상단 고정 | 운영에서 필요해지면 |
| 알레르기 별도 테이블 | 알레르기별 속성이 생기면 |
| 스캔 진행 단계 컬럼 | 서버가 단계별 진행을 실제로 알게 되면 |
| 보호자 승인 | 14세 미만 이용 정책이 정해지면 |

**출시 때 비어 있는 테이블** (코드·화면 없음, 그대로 둔다): `knowledge_review_items` · `knowledge_review_sources` · `ingredient_evidence_log` · `source_reliability` · `admin_audit_logs` · `areas` · `curation_articles` · `curation_article_translations` · `curation_article_menus`

---

## 8. 검증 기록 (2026-11-15~23)

| 확인 | 결과 |
|---|---|
| 설계본과 Flyway V1~V14 | `pg_dump` 스키마 일치 |
| 무결성 시나리오 | 303/303 (FK·CHECK·부분 UNIQUE·CASCADE, 복합 FK, 직원 응답, 후기·신고, 찜) |
| `./gradlew test` | 210/210 (11-16, 원격 dev 합친 뒤), 267/267 (11-22, V14 Flyway 적용), 268/268 (11-23, 명령 정리 추가), 286/286 (11-23, 직원 질문 노출·응답 API), 300/300 (11-23, 메뉴 찜 API), 325/325 (11-23, 식사 후기 API), 333/333 (11-23, 원본 사진 조회 API) |
| 운영 배포 (11-23) | V14 적용, 기존 행 변환 확인, Prepare → Judge 1회 완료 |
| 성능 (3년 가정 합성 데이터: 스캔 30만·메뉴 300만·후기 18만) | 대부분 1ms 미만. 가장 느린 것은 인기 가게 1곳(후기 줄 1.2만)의 후기 칩 집계 40~110ms, 동네 목록 51ms |

---

## 9. 결정 기록 (날짜순)

| 날짜 | 결정 |
|---|---|
| 10-26 | 직원 응답 = 가게 메뉴 기준, 만료 없음. 웹 레시피는 자동 등록 기본 |
| 10-27 | 큐레이션 운영 기능 최소화, 태그는 추천 계산용. AI 요청 4건(#14·#15·#25·#26) 수용 |
| 11-01 | 사장님 페이지 없음. 관리자 화면은 백엔드 Thymeleaf(`/admin/**`). 분석 취소 = 스캔 삭제. 국가 목록은 프론트 정적. 직원 응답은 가게 공용 확인, 식당 없는 스캔엔 직원 질문 없음. 메뉴판 사진 90일 보관. `store_detail` 경로 추가 |
| 11-03 | 결과 화면 하트 = 메뉴 찜. 같은 식당 재스캔은 새 기록. 동네 정렬은 거리 → 후기 수. 내 후기 수정·삭제, 남의 후기 신고(저장만) |
| 11-04 | 관리자 화면 전체 3순위 |
| 11-09 | 직원 승인 = 손님 카드에 직원이 버튼으로 답함, 즉시 반영. 스캔 기록 삭제 시 찜도 삭제 |
| 11-10 | API 명세 V2 반영: `owner_*` → `staff_*`, 찜·신고·동네 테이블, 식단 스냅샷, 제목 30자, 글 번호 |
| 11-12 | 직원 응답 뒤 AI 재판정 없음. 서버는 위험 쪽으로만 올림 (R-02) |
| 11-15 | 프론트 화면을 데이터 항목 근거로 사용. 약관은 V7 테이블 사용. 후기는 화면대로(스캔 선택·메뉴 직접 입력·후기 단위 신고). `scan_records` 유지. 식단 코드는 화면 ID |
| 11-16 | 원격 V8~V10(보호자 승인·찜·후기) 되돌리고 운영 DB 정리. 스키마 원본을 Flyway 로 하고 설계 도구·중간 문서 정리 |
| 11-20 | 시연 가게 표시명 = 시트 식당명(회사명 상호 미표시), `verified_at` 기록. 같은 식당 중복 행은 비활성 |
| 11-22 | Prepare/Judge 연동을 PR 두 개로(전환 → V14 축소). Bundle 확정값은 후보 메뉴 전부에. `recommendation` 보류. AI 문서로 확인: 확정 "있음" + 금지 태그 = DANGER, 가게 prior·출처 신뢰도 `not_found` 허용, fuzzy 라이브러리는 백엔드가 정함, 출처 신뢰도 = `(맞춘 수+1)/(비교한 수+2)`. PR 2: V14 Flyway 적용, 판정 컬럼은 JDBC 전용(엔티티 읽기만), 번역명은 `menus.name_i18n` 만(설명 번역 없음), 사진은 `store_menu_id` 우선 |
| 11-23 | PR 2 운영 배포(V14). 되돌리기는 스냅샷 복구만. 프론트는 V2 필드 우선 읽기로 같이 배포하지 않음. 사진 품질 판정 수정은 AI 레포에서, Naver 웹 검색 키는 그 뒤로 미룸. 직원 응답 API 구현: 메뉴 위험도 = 겹친 present 있으면 danger 아니면 `ai_risk_level`, 응답 미완이면 SAFE 대신 caution, 표시 순서 유지. 메뉴 찜 API 구현(표시값 조회, 멱등 찜). 식사 후기 API 구현(식단 항목은 현재 프로필, 수정은 줄 전체 교체, 목록은 전체 배열). 원본 사진 조회 API 구현(사진 행 없음은 404, 새 에러 코드는 410만) |
| 11-25 | 표준 메뉴 대표 이미지(#113): 스캔 결과 사진은 가게 메뉴 사진 → 판정 표준 메뉴 이미지 순. 출처는 Wikimedia Commons + 허락받은 사진, 연결은 메뉴명 기준 . 가게 이름 검색(#122): 검색어가 있으면 전국 검색 + 포함 매칭, 1~2글자는 전국 접두 + 반경 3km 포함, 같은 단계는 거리 우선. 지점명 포함 매칭은 식 인덱스(V15), OpenSearch·전문 검색 제외. 메뉴명 검색(#125): 한글 가게 메뉴명 포함 → 이름 포함 다음 단계, 영어 메뉴명은 운영 데이터 확인 뒤. 짧은 검색어 최적화: 지점명 없는 가게(87%)는 저장 정규형으로 매칭, 정렬 거리 평면 근사, 분류·후기 수는 상위 행만 |
