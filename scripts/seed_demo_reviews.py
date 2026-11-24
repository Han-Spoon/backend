#!/usr/bin/env python3
"""
시연 후기 CSV → meal_reviews·items·answers 적재 (시연 계정 1개)

입력
  · --reviews: 열 store_no, review_no, menu_name, comment, comment_locale, answers, days_ago.
    같은 (store_no, review_no) 행 = 후기 1건의 메뉴 줄. answers 예: 'allergy:pork=present;allergy:wheat=unknown'.

설계 메모
  · 표준 라이브러리만 사용. 전체가 단일 트랜잭션.
  · 시연 계정(로그인 수단 없음) 후기를 지우고 CSV 로 다시 넣음 → 재실행 가능.
  · 식단 응답은 present·unknown 만. absent 는 실제 사용자 오도 방지로 거부.
  · 스캔 없는 후기(menu_analysis_id NULL). 작성 시각 = now() - days_ago 일.
  · --delete: 시연 계정 삭제, 후기 전부 CASCADE.

사용 예
  python3 scripts/seed_demo_reviews.py --reviews scripts/data/demo_reviews.csv --dry-run
  python3 scripts/seed_demo_reviews.py --delete --dry-run
"""
from __future__ import annotations

import argparse
import csv
import io
import subprocess
import sys
from pathlib import Path

try:
    from scripts.load_store_menus import MAX_MENU_NAME, MAX_STORE_NO, clean, open_csv
    from scripts.load_stores import resolve_psql
except ImportError:
    from load_store_menus import MAX_MENU_NAME, MAX_STORE_NO, clean, open_csv
    from load_stores import resolve_psql

DEMO_USER_ID = "00000000-0000-4000-8000-00000000de30"
DEMO_USER_EMAIL = "demo-reviews@han-spoon.invalid"
DEMO_USER_NICKNAME = "한스푼 시연"
COLUMNS = ("store_no", "review_no", "menu_name", "comment", "comment_locale", "answers", "days_ago")
MAX_COMMENT = 500
LOCALES = frozenset({"ko", "en", "ar", "zh-CN", "zh-TW", "ja", "es"})
ANSWERS = frozenset({"present", "unknown"})
DIET_CODES = frozenset({   # ck_meal_review_answers_diet_code
    "vegan:vegan", "vegan:lacto", "vegan:ovo", "vegan:lacto_ovo", "vegan:pesco",
    "religion:halal", "religion:kosher", "religion:hindu",
    "allergy:egg", "allergy:milk", "allergy:buckwheat", "allergy:peanut", "allergy:soybean", "allergy:wheat",
    "allergy:mackerel", "allergy:crab", "allergy:shrimp", "allergy:pork", "allergy:peach", "allergy:tomato",
    "allergy:sulfite", "allergy:walnut", "allergy:chicken", "allergy:beef", "allergy:squid", "allergy:shellfish",
    "allergy:pinenut",
    "preference:no-spicy", "preference:no-alcohol",
})


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = p.add_mutually_exclusive_group(required=True)
    mode.add_argument("--reviews", type=Path, help="시연 후기 CSV")
    mode.add_argument("--delete", action="store_true", help="시연 계정·후기 삭제")
    p.add_argument("--dry-run", action="store_true", help="반영 결과 출력 후 롤백")
    p.add_argument("--psql", help="psql 실행 명령. 미지정 시 자동 탐지")
    p.add_argument("--dsn", default="postgresql://hanspoon:hanspoon@localhost:5432/hanspoon",
                   help="로컬 psql 사용 시 접속 문자열")
    p.add_argument("--container", default="hanspoon-postgres", help="docker 폴백에 사용할 컨테이너 이름")
    p.add_argument("--out", type=Path, help="실행하지 않고 SQL 을 이 파일에 기록")
    return p.parse_args(argv)


def parse_answers(value: str, where: str) -> str:
    """'code=answer;…' 검증 후 정규화한 같은 형식."""
    pairs: dict[str, str] = {}
    for part in filter(None, (clean(x) for x in value.split(";"))):
        code, sep, answer = (clean(x) for x in part.partition("="))
        if not sep or code not in DIET_CODES:
            sys.exit(f"{where}: 식단 항목 오류 '{part}'")
        if answer not in ANSWERS:
            sys.exit(f"{where}: 응답은 present·unknown 만 '{part}'")
        if code in pairs:
            sys.exit(f"{where}: 식단 항목 중복 '{code}'")
        pairs[code] = answer
    return ";".join(f"{c}={a}" for c, a in pairs.items())


def read_reviews(path: Path) -> list[list[object]]:
    """(store_no, review_no, line_no, menu_name, comment, comment_locale, answers, days_ago)."""
    rows: list[list[object]] = []
    seen: set[tuple[str, int, str]] = set()
    ages: dict[tuple[str, int], float] = {}
    for line, row in enumerate(open_csv(path, COLUMNS), start=2):
        where = f"{path.name}:{line}"
        store_no, menu = clean(row["store_no"]), clean(row["menu_name"])
        comment, locale = clean(row["comment"]), clean(row["comment_locale"])
        if not store_no or len(store_no) > MAX_STORE_NO:
            sys.exit(f"{where}: store_no 오류")
        if not menu or len(menu) > MAX_MENU_NAME:
            sys.exit(f"{where}: menu_name 오류")
        if bool(comment) != bool(locale):
            sys.exit(f"{where}: comment·comment_locale 은 함께")
        if locale and locale not in LOCALES:
            sys.exit(f"{where}: comment_locale 오류 '{locale}'")
        if len(comment) > MAX_COMMENT:
            sys.exit(f"{where}: comment {MAX_COMMENT}자 초과")
        try:
            review_no, days_ago = int(row["review_no"]), float(row["days_ago"])
        except ValueError:
            sys.exit(f"{where}: review_no·days_ago 숫자 아님")
        if days_ago < 0:
            sys.exit(f"{where}: days_ago 음수")
        key = (store_no, review_no)
        if ages.setdefault(key, days_ago) != days_ago:
            sys.exit(f"{where}: 같은 후기의 days_ago 다름")
        if (store_no, review_no, menu) in seen:
            sys.exit(f"{where}: 같은 후기에 메뉴 중복 '{menu}'")
        seen.add((store_no, review_no, menu))
        answers = parse_answers(row["answers"], where)
        if not comment and not answers:
            sys.exit(f"{where}: comment·answers 둘 다 빈칸")
        rows.append([store_no, review_no, line, menu, comment, locale, answers, days_ago])
    if not rows:
        sys.exit(f"{path.name}: 후기 없음")
    return rows


def emit(w: io.TextIOBase, rows: list[list[object]] | None, dry_run: bool) -> None:
    w.write(f"""\
\\set ON_ERROR_STOP on
BEGIN;
\\echo '── 기존 시연 계정 후기 삭제 ──'
DELETE FROM meal_reviews WHERE user_id = '{DEMO_USER_ID}';
""")
    if rows is None:
        w.write(f"""\
\\echo '── 시연 계정 삭제 ──'
DELETE FROM users WHERE id = '{DEMO_USER_ID}';
""")
        w.write("ROLLBACK;\n" if dry_run else "COMMIT;\n")
        return

    w.write(f"""\
INSERT INTO users (id, email, nickname)
VALUES ('{DEMO_USER_ID}', '{DEMO_USER_EMAIL}', '{DEMO_USER_NICKNAME}')
ON CONFLICT (id) DO NOTHING;

CREATE TEMP TABLE stg_review (
    store_no text, review_no int, line_no int, menu_name text,
    comment text, comment_locale text, answers text, days_ago numeric) ON COMMIT DROP;
\\copy stg_review FROM STDIN WITH (FORMAT csv)
""")
    csv.writer(w, lineterminator="\n").writerows(rows)
    w.write("""\
\\.

DO $demo_review_stores$
DECLARE
    unknown TEXT := (
        SELECT string_agg(DISTINCT v.store_no, ', ')
        FROM stg_review v
        WHERE NOT EXISTS (
            SELECT 1 FROM stores s
            WHERE (s.sbiz_store_no = v.store_no OR s.localdata_mgmt_no = v.store_no) AND s.status = 'active'));
BEGIN
    IF unknown IS NOT NULL THEN
        RAISE EXCEPTION 'unknown or inactive store_no: %', unknown;
    END IF;
END
$demo_review_stores$;

CREATE TEMP TABLE stg_head ON COMMIT DROP AS
SELECT gen_random_uuid() AS id, s.id AS store_id, g.store_no, g.review_no,
       now() - g.days_ago * interval '1 day' AS created_at
FROM (SELECT DISTINCT store_no, review_no, days_ago FROM stg_review) g
JOIN stores s ON (s.sbiz_store_no = g.store_no OR s.localdata_mgmt_no = g.store_no) AND s.status = 'active';

CREATE TEMP TABLE stg_item ON COMMIT DROP AS
SELECT gen_random_uuid() AS id, h.id AS review_id, h.store_id,
       h.created_at + v.line_no * interval '1 millisecond' AS created_at,
       v.menu_name, nullif(v.comment, '') AS comment, nullif(v.comment_locale, '') AS comment_locale, v.answers
FROM stg_review v
JOIN stg_head h USING (store_no, review_no);

INSERT INTO meal_reviews (id, user_id, store_id, created_at, updated_at)
SELECT id, '""" + DEMO_USER_ID + """', store_id, created_at, created_at FROM stg_head;

INSERT INTO meal_review_items (id, review_id, menu_name, comment, comment_locale, created_at, updated_at)
SELECT id, review_id, menu_name, comment, comment_locale, created_at, created_at FROM stg_item;

INSERT INTO meal_review_answers (review_item_id, diet_code, answer)
SELECT i.id, split_part(a, '=', 1), split_part(a, '=', 2)
FROM stg_item i, unnest(string_to_array(i.answers, ';')) AS a
WHERE a <> '';

\\echo '── 적재 결과 ──'
SELECT (SELECT count(*) FROM stg_head)                         AS reviews,
       (SELECT count(DISTINCT store_id) FROM stg_head)         AS stores,
       (SELECT count(*) FROM stg_item)                         AS menu_lines,
       (SELECT count(*) FROM meal_review_answers a JOIN stg_item i ON i.id = a.review_item_id) AS answers;

\\echo '── 가게 메뉴에 없는 후기 메뉴명 ──'
SELECT s.name AS store, i.menu_name
FROM stg_item i
JOIN stores s ON s.id = i.store_id
WHERE NOT EXISTS (
    SELECT 1 FROM store_menus sm
    WHERE sm.store_id = i.store_id AND sm.name_normalized = normalize_food_name(i.menu_name))
ORDER BY 1, 2;

""")
    w.write("ROLLBACK;\n" if dry_run else "COMMIT;\n")


def main() -> None:
    a = parse_args()
    rows = None if a.delete else read_reviews(a.reviews)
    if rows is not None:
        heads = {(r[0], r[1]) for r in rows}
        print(f"후기 {len(heads)}건 · 가게 {len({h[0] for h in heads})}곳 · 메뉴 줄 {len(rows)}"
              f"{' · dry-run' if a.dry_run else ''}", file=sys.stderr)

    if a.out:
        with a.out.open("w", encoding="utf-8") as fh:
            emit(fh, rows, a.dry_run)
        print(f"SQL 기록: {a.out}", file=sys.stderr)
        return

    cmd = resolve_psql(a)
    print(f"실행: {cmd[0]} …", file=sys.stderr)
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, text=True, encoding="utf-8")
    assert proc.stdin is not None
    try:
        emit(proc.stdin, rows, a.dry_run)
    finally:
        proc.stdin.close()
    if proc.wait() != 0:
        sys.exit(f"psql 실패 (exit {proc.returncode}) — 트랜잭션은 롤백되었습니다.")


if __name__ == "__main__":
    main()
