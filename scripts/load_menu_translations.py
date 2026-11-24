#!/usr/bin/env python3
"""
표준 메뉴 다국어 이름 CSV → menus.name_i18n 갱신(메뉴명 기준)

입력
  · --translations: 메뉴 번역 CSV. 헤더 name_ko,en,ar,zh-CN,zh-TW,ja,es(재료 번역과 같음).
    아직 menus 에 없는 메뉴명도 가능(대기 목록으로 출력).

설계 메모
  · 표준 라이브러리만 사용. 단일 트랜잭션.
  · 연결은 menus.name_normalized = normalize_food_name(name_ko). 빈 칸 언어는 키 없음, ko 는 menus.name_ko.
  · CSV 값으로 통째 교체. 값이 같은 메뉴는 건드리지 않아 재실행 안전.
  · knowledge_revision 은 그대로(번역은 판정 지식 아님). 시드 재적재로 메뉴가 늘면 다시 실행.
  · --dry-run: 반영 결과만 출력 후 롤백.

사용 예
  python3 scripts/load_menu_translations.py --dry-run
  python3 scripts/load_menu_translations.py --translations scripts/data/menu_translations.csv
"""
from __future__ import annotations

import argparse
import csv
import io
import subprocess
import sys
from pathlib import Path

try:
    from scripts.load_knowledge_seed import TRANSLATION_HEADER, read_csv
    from scripts.load_stores import resolve_psql
except ImportError:
    from load_knowledge_seed import TRANSLATION_HEADER, read_csv
    from load_stores import resolve_psql

DEFAULT_TRANSLATIONS = Path(__file__).with_name("data") / "menu_translations.csv"


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--translations", type=Path, default=DEFAULT_TRANSLATIONS,
                   help=f"메뉴 번역 CSV (기본 {DEFAULT_TRANSLATIONS.relative_to(Path(__file__).parent.parent)})")
    p.add_argument("--dry-run", action="store_true", help="반영 결과 출력 후 롤백")
    p.add_argument("--psql", help="psql 실행 명령. 미지정 시 자동 탐지")
    p.add_argument("--dsn", default="postgresql://hanspoon:hanspoon@localhost:5432/hanspoon",
                   help="로컬 psql 사용 시 접속 문자열")
    p.add_argument("--container", default="hanspoon-postgres", help="docker 폴백에 사용할 컨테이너 이름")
    return p.parse_args(argv)


def read_translations(path: Path) -> list[list[str]]:
    """검증된 행(앞뒤 공백 제거). 오류는 모아서 한 번에 중단."""
    rows = [[c.strip() for c in r] for r in read_csv(path, TRANSLATION_HEADER)]
    errors: list[str] = []
    seen: set[str] = set()
    for line, r in enumerate(rows, start=2):
        if len(r) != len(TRANSLATION_HEADER):
            errors.append(f"{line}행: 열 {len(r)}개 (기대 {len(TRANSLATION_HEADER)})")
            continue
        if not r[0] or not r[1]:
            errors.append(f"{line}행: name_ko·en 필수")
        elif r[0] in seen:
            errors.append(f"{line}행: {r[0]} 중복")
        seen.add(r[0])
    if errors:
        sys.exit("번역 오류\n  " + "\n  ".join(errors))
    if not rows:
        sys.exit("번역 행 없음")
    return rows


def emit(w: io.TextIOBase, rows: list[list[str]], dry_run: bool) -> None:
    w.write("""\
\\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE stg_menu_translation (
  name_ko text, en text, ar text, zh_cn text, zh_tw text, ja text, es text, ord int
) ON COMMIT DROP;

\\copy stg_menu_translation FROM STDIN WITH (FORMAT csv)
""")
    out = csv.writer(w, lineterminator="\n")
    for order, r in enumerate(rows):
        out.writerow([*r, order])
    w.write("""\
\\.

DO $menu_translation_names$
DECLARE
    dup TEXT := (
        SELECT string_agg(names, ' / ')
        FROM (SELECT string_agg(name_ko, ', ' ORDER BY ord) AS names
              FROM stg_menu_translation GROUP BY normalize_food_name(name_ko) HAVING count(*) > 1) d);
BEGIN
    IF dup IS NOT NULL THEN
        RAISE EXCEPTION '정규화하면 같은 메뉴명: %', dup;
    END IF;
END
$menu_translation_names$;

\\echo '── 번역 갱신 ──'
WITH src AS (
    SELECT m.id, jsonb_strip_nulls(jsonb_build_object(
               'ko', m.name_ko, 'en', nullif(t.en, ''), 'ar', nullif(t.ar, ''), 'zh-CN', nullif(t.zh_cn, ''),
               'zh-TW', nullif(t.zh_tw, ''), 'ja', nullif(t.ja, ''), 'es', nullif(t.es, ''))) AS name_i18n
    FROM stg_menu_translation t
    JOIN menus m ON m.name_normalized = normalize_food_name(t.name_ko)
), updated AS (
    UPDATE menus m
       SET name_i18n = s.name_i18n, updated_at = now()
      FROM src s
     WHERE m.id = s.id
       AND m.name_i18n IS DISTINCT FROM s.name_i18n
    RETURNING m.id
)
SELECT (SELECT count(*) FROM src) AS matched, (SELECT count(*) FROM updated) AS updated;

\\echo '── 대기: 아직 menus 에 없는 메뉴명 ──'
SELECT t.name_ko
FROM stg_menu_translation t
WHERE NOT EXISTS (SELECT 1 FROM menus m WHERE m.name_normalized = normalize_food_name(t.name_ko))
ORDER BY t.ord;

\\echo '── 영어명 없는 표준 메뉴 ──'
SELECT id, name_ko, source FROM menus WHERE NOT coalesce(name_i18n ? 'en', false) ORDER BY id;

""")
    w.write("ROLLBACK;\n" if dry_run else "COMMIT;\n")


def main() -> None:
    a = parse_args()
    rows = read_translations(a.translations)
    print(f"메뉴 번역 {len(rows)}{' · dry-run' if a.dry_run else ''}", file=sys.stderr)

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
