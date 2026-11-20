# 스키마 v2 결정 문서

- **스키마의 원본은 Flyway 파일이다.** `src/main/resources/db/migration/V1~V13` + 보류 [`pending/V14__menu_analyses_contract.sql`](pending/V14__menu_analyses_contract.sql). 테이블·컬럼·제약 목록은 [`ERD.md`](ERD.md), 그림은 [`ERD.svg`](ERD.svg).
- Flyway 번호: V1~V6 = MVP(스키마 v1), V7 = 약관 동의(2026-11-14 배포), **V8~V13 = 스키마 v2 확장 단계**, V13.1 = LOCALDATA 가게 식별자(2026-11-20), V14 = 축소 단계(Prepare/Judge 연동과 함께). V14 전 추가 변경은 V13.x 소수 버전으로 넣는다.
- 상태: 2026-11-15 설계 확정, 미배포.
- 기준: 프론트 화면(`frontend-webapp-demo`), Notion **API 명세서_V2 (AWS)**, AI 설계 문서 hanspoon-ai `docs/`(schema_version 3.0). 사장님 페이지는 만들지 않는다(11-01).

| 규모 | 테이블 51 (+ VIEW 1) · 컬럼 475 · FK 80 · UNIQUE 45 · CHECK 183 · 인덱스 154 (V14 적용 후) |
|---|---|

---

## 1. 스키마 바꾸는 법

| 시점 | 방법 |
|---|---|
| V8~V13 을 운영에 올리기 전 | 해당 V 파일을 직접 고친다. 아직 적용된 곳이 없어 체크섬 문제가 없다 |
| 올린 뒤 | 새 파일을 추가한다(V14 전은 `V13_x__…sql`, 후는 `V15__…sql`). 이미 적용된 파일은 고치지 않는다 |
| 코멘트 | 같은 마이그레이션 안에 `COMMENT ON` 으로 단다. 형식 `'논리명 \| 비고'` |
| 확인 | `./gradlew test` (Testcontainers 로 V1~V13 적용 + JPA `validate` + `SchemaV2MigrationIntegrationTest`) |

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
| `display_order`·`recommendation` 은 백엔드가 Judge 결과로 계산해 저장 | AI 계약에 없다 |
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
| Judge 저장 | 한 트랜잭션: 판정(첫 판정이면 `ai_risk_level` 도) → `display_order`·`recommendation` → 매핑 갱신 → 카운터 → Judge 메타 → 명령은 `ai_command_inbox` |
| 표시 순서 | SAFE → CAUTION+estimated → CAUTION+unknown → DANGER, 같은 칸은 `source_index` |
| 스캔 내 추천 | `safe` + `complete` 만. 콘텐츠 태그가 금지 태그와 겹치면 추천 제외 |
| Bundle 확정값 | 스캔 메뉴의 `store_menu_id` 로 확정값(무효화 제외)을 읽고 `store_menus.menu_id` 를 붙여 보낸다 |
| 직원 응답 | 한 트랜잭션: 질문 행 → present/absent 면 확정값 upsert + 링크, unknown 이면 링크 해제 + `invalidated_at` → `present` + 식단 겹침이면 `risk_level = danger` + 카운터. `present` 를 고쳤고 남은 `present` 가 없으면 `risk_level = ai_risk_level` |
| 직원 응답 충돌 | 다른 스캔의 "없어요"는 "있어요" 확정값을 덮지 않는다 (R-04, 미구현) |
| 직원 질문 생성 | 후보 메뉴가 없는 메뉴와 ID 없는 재료에는 질문을 만들지 않는다 (R-06·R-07, 미구현) |
| 정리 작업 | `judge_raw` 90일 NULL 처리, `applied` 명령 90일 삭제 (부분 인덱스 있음) |
| 로그 조회 | 관리자 화면 없이 읽기 전용 DB 계정으로 본다. 쓰기 권한 직접 접속 금지 |
| 큐레이션 저장 | `ko` + 6개 언어를 한 트랜잭션, 원문 수정 시 전부 재번역 |
| 약관 | 새 원문은 새 `legal/{버전}.json`. 게시한 파일은 고치지 않는다. 현재 버전의 최신 결정만 유효 |
| 식사 후기 | 공개 응답에 작성자 정보 없음. 공개 목록 `hidden_at IS NULL` + 키셋(`created_at DESC, id DESC`). 줄의 `menu_analysis_id` 가 그 후기 스캔의 메뉴인지, 응답도 말도 없는 줄 거부(R-20)는 앱이 확인 |
| 후기 신고 | 본인 후기 신고 400, 재신고 409, 저장만 |
| 메뉴 찜 | 요청은 `menuAnalysisId` 하나, 서버가 `scan_session_id` 를 채운다 |
| 동네 가게 | `earth_box` + `earth_distance ≤ 반경`. 위치가 있으면 거리 → 후기 수, 없으면 후기 많은 순 |
| 계산 값 | 사진 만료, 큐레이션 읽기 시간, 가게 후기 수 |
| 검증 가게 | `stores.verified_at` 가게는 공공데이터 재적재 시 `name`·`branch_name`·`status` 유지, 비활성 스윕 제외. 시연 가게 표시명·중복 가게는 `scripts/curate_demo_stores.py` |

---

## 5. 배포 (expand–contract)

운영 파이프라인(OCR + 룰엔진)은 아직 `menu_name_en`·단수 `owner_card`·`is_spicy = false`·`risky_menu_count` 를 쓴다. 그래서 둘로 나눴다.

| 파일 | 내용 |
|---|---|
| `V8__foundation.sql` | 정규화·배열 함수, 기존 테이블 CHECK, 위험도 카운터·`locale`, `menu_images` → `scan_images`, `saved_cards.text` → `card_text`, 알레르기 배열 이관 + `user_allergies` 삭제 |
| `V9__ai_global_knowledge.sql` | 전역 지식 |
| `V10__ai_workflow.sql` | 관리자 역할 · 웹 원본 · 검토 · 명령 수신함 · 감사 로그 |
| `V11__ai_store_scoped_evidence.sql` | 가게 메뉴 · 확정값 · 직원 질문 · 증거 · VIEW · 캐시 · 출처 신뢰도 |
| `V12__store_menus_and_content.sql` | 스캔 AI 원본, `menu_analyses` 새 컬럼, 저장 카드, 찜, 이미지 · 큐레이션 · 번역 캐시 · 후기 · 신고 · 동네 |
| `V13__comments.sql` | 코멘트 (확장 단계 기준) |
| `V13_1__store_localdata_identity.sql` | `stores.localdata_mgmt_no`(LOCALDATA 관리번호) UNIQUE·형식·출처 CHECK |
| `erd/pending/V14__menu_analyses_contract.sql` | `menu_analyses` 이름 변경·삭제·카드 배열 전환·CHECK, `risky_menu_count` 삭제, 제목 30자 CHECK |

**배포 절차**: 배포 직전 RDS 수동 스냅샷을 만든다. 되돌릴 때는 스냅샷 복구로 한다(R-08).

**V14 를 Flyway 로 옮기는 조건** (Prepare/Judge 연동 PR 에서 함께):

1. Judge 결과를 `source_index`·`staff_cards`(배열)·판정 컬럼으로 저장하고 첫 판정을 `ai_risk_level` 에도 기록한다. `MenuAnalysis` 엔티티를 새 이름으로 바꾼다
2. 영어 메뉴명 응답을 `menus.name_i18n` 과 번역 캐시로 대체한다
3. `is_spicy` 를 "감지 시 true, 아니면 NULL" 로 기록한다
4. `riskyMenuCount` 를 `dangerCount + cautionCount` 로 바꾼다 (API 변경이면 프론트 공지)
5. `judge_raw` 90일 정리 스케줄러를 넣는다
6. 기록 제목 검증을 30자로 줄인다(지금 255자). 31자 이상 제목이 있으면 먼저 정리

---

## 6. 미결 사항

| 분류 | 할 일 |
|---|---|
| PM·프론트 | 직원 응답 규칙(있어요 → 위험, 없어요·모름 → 표시만) 공유. 프론트 문구는 지금 "판정은 자동으로 바뀌지 않는다"로 되어 있다. Notion No 16·23 수정(사용자 확인 후) |
| 화면 쪽 정리 | 환율 "매일 00:00 갱신" 문구(지금은 고정 환율), 출처 간 불일치 표시(AI 값 없음), 주문·빼기·덜 맵게 응답(저장 안 함), 파트너 식당·등록 레시피(시연 전용) |
| AI 팀 | ① 확정 "있음" + 금지 태그 겹침이면 Judge 도 `danger` 인가 ② 가게 prior·출처 신뢰도가 `not_found` 로 가도 되는가 ③ 테이블 이름 매핑(`staff_*`) 알림 ④ `recipe_probability` payload 제거 요청 ⑤ 출처 신뢰도 입력 계약 ⑥ `name-{canonical_name}` 카드가 질문 명령으로 오는지 |
| 법률·약관 | 원문의 14세 미만 조항 정리(보호자 절차 없음) + 버전 올림, 위치정보 약관 필요 여부(필요하면 문서 ID CHECK 에 추가), 탈퇴 후 동의 기록 보존 여부(지금 CASCADE, R-17) |
| 인증 | `user_auth_identities` 의 `email_verified` 규칙, `provider` 기본값·CHECK |

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

**출시 때 비어 있는 테이블** (코드·화면 없음, 그대로 둔다): `knowledge_review_items` · `knowledge_review_sources` · `ingredient_evidence_log` · `source_reliability` · `admin_audit_logs` · `areas` · `curation_articles` · `curation_article_translations` · `curation_article_menus` · `image_assets`

---

## 8. 검증 기록 (2026-11-15~16)

| 확인 | 결과 |
|---|---|
| 설계본과 Flyway V1~V14 | `pg_dump` 스키마 일치 |
| 무결성 시나리오 | 303/303 (FK·CHECK·부분 UNIQUE·CASCADE, 복합 FK, 직원 응답, 후기·신고, 찜) |
| `./gradlew test` | 210/210 (11-16, 원격 dev 합친 뒤) |
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
