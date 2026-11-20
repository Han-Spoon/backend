#!/usr/bin/env python3
"""
시연 가게 표시명 반영 · 중복 가게 비활성

입력
  · --store-map: 열 '음식점 명', 'store_no'. 모든 가게 번호 필수.
  · --duplicates: 열 'store_no', '음식점 명'. 같은 식당의 공공데이터 중복 행과 원래 시연 가게.

설계 메모
  · 표준 라이브러리만 사용. 전체가 단일 트랜잭션, 재실행 가능.
  · 시연 가게: name = 시트 식당명, branch_name = '', verified_at 기록.
  · 중복 가게: inactive, verified_at 기록.
  · verified_at 가게는 상가정보·LOCALDATA 재적재 시 표시명·상태 유지.
  · DB에 없는 번호, 비활성 시연 가게는 중단.

사용 예
  python3 scripts/curate_demo_stores.py --store-map scripts/data/demo_store_map.csv \\
      --duplicates scripts/data/demo_store_duplicates.csv --dry-run
"""
from __future__ import annotations

import argparse
import csv
import io
import subprocess
import sys
from pathlib import Path

try:
    from scripts.load_store_menus import clean, open_csv, read_store_map
    from scripts.load_stores import resolve_psql
except ImportError:
    from load_store_menus import clean, open_csv, read_store_map
    from load_stores import resolve_psql

STORE_COL = "음식점 명"
STORE_NO_COL = "store_no"
MAX_NAME = 200   # stores.name


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--store-map", required=True, type=Path, help="가게 매핑 CSV (음식점 명, store_no)")
    p.add_argument("--duplicates", required=True, type=Path, help="중복 가게 CSV (store_no, 음식점 명)")
    p.add_argument("--dry-run", action="store_true", help="반영 결과 출력 후 롤백")
    p.add_argument("--psql", help="psql 실행 명령. 미지정 시 자동 탐지")
    p.add_argument("--dsn", default="postgresql://hanspoon:hanspoon@localhost:5432/hanspoon",
                   help="로컬 psql 사용 시 접속 문자열")
    p.add_argument("--container", default="hanspoon-postgres", help="docker 폴백에 사용할 컨테이너 이름")
    return p.parse_args(argv)


def read_demo_stores(path: Path) -> list[list[str]]:
    """(store_no, 식당명). 번호 빈칸·중복 번호·긴 이름은 중단."""
    mapping = read_store_map(path)
    missing = sorted(name for name, no in mapping.items() if not no)
    if missing:
        sys.exit(f"store_no 없는 가게 {len(missing)}곳: {missing}")
    too_long = sorted(name for name in mapping if len(name) > MAX_NAME)
    if too_long:
        sys.exit(f"식당명 {MAX_NAME}자 초과: {too_long}")
    numbers = list(mapping.values())
    repeated = sorted({no for no in numbers if numbers.count(no) > 1})
    if repeated:
        sys.exit(f"store_no 중복: {repeated}")
    return sorted([no, name] for name, no in mapping.items())


def read_duplicates(path: Path, demo: list[list[str]]) -> list[str]:
    """중복 가게 store_no. 원래 시연 가게가 매핑에 없거나 시연 가게 번호면 중단."""
    demo_names = {name for _, name in demo}
    demo_numbers = {no for no, _ in demo}
    numbers: list[str] = []
    for line, row in enumerate(open_csv(path, (STORE_NO_COL, STORE_COL)), start=2):
        no, store = clean(row.get(STORE_NO_COL)), clean(row.get(STORE_COL))
        if not no:
            sys.exit(f"{path.name}:{line}: store_no 없음")
        if store not in demo_names:
            sys.exit(f"{path.name}:{line}: store-map 에 없는 가게 '{store}'")
        if no in demo_numbers:
            sys.exit(f"{path.name}:{line}: 시연 가게 번호 {no}")
        numbers.append(no)
    if len(set(numbers)) != len(numbers):
        sys.exit(f"{path.name}: store_no 중복")
    return sorted(numbers)


def emit(w: io.TextIOBase, demo: list[list[str]], duplicates: list[str], dry_run: bool) -> None:
    w.write("""\
\\set ON_ERROR_STOP on
BEGIN;
-- 공공데이터 적재와 동시 실행 방지
SELECT pg_advisory_xact_lock(hashtextextended('hanspoon:store-import:sbiz', 0));
SELECT pg_advisory_xact_lock(hashtextextended('hanspoon:store-import:localdata', 0));

CREATE TEMP TABLE stg_demo (store_no text, name text) ON COMMIT DROP;
\\copy stg_demo FROM STDIN WITH (FORMAT csv)
""")
    csv.writer(w, lineterminator="\n").writerows(demo)
    w.write("""\
\\.

CREATE TEMP TABLE stg_duplicate (store_no text) ON COMMIT DROP;
\\copy stg_duplicate FROM STDIN WITH (FORMAT csv)
""")
    csv.writer(w, lineterminator="\n").writerows([no] for no in duplicates)
    w.write("""\
\\.

DO $demo_store_contract$
DECLARE
    unknown_demo TEXT := (
        SELECT string_agg(d.store_no, ', ') FROM stg_demo d
        WHERE NOT EXISTS (
            SELECT 1 FROM stores s
            WHERE (s.sbiz_store_no = d.store_no OR s.localdata_mgmt_no = d.store_no) AND s.status = 'active'));
    unknown_duplicate TEXT := (
        SELECT string_agg(d.store_no, ', ') FROM stg_duplicate d
        WHERE NOT EXISTS (
            SELECT 1 FROM stores s WHERE s.sbiz_store_no = d.store_no OR s.localdata_mgmt_no = d.store_no));
BEGIN
    IF unknown_demo IS NOT NULL THEN
        RAISE EXCEPTION 'unknown or inactive demo store_no: %', unknown_demo;
    END IF;
    IF unknown_duplicate IS NOT NULL THEN
        RAISE EXCEPTION 'unknown duplicate store_no: %', unknown_duplicate;
    END IF;
END
$demo_store_contract$;

\\echo '── 시연 가게 ──'
UPDATE stores s
   SET name = d.name, branch_name = '', verified_at = coalesce(s.verified_at, now()), updated_at = now()
  FROM stg_demo d
 WHERE s.sbiz_store_no = d.store_no OR s.localdata_mgmt_no = d.store_no;

\\echo '── 중복 가게 ──'
UPDATE stores s
   SET status = 'inactive', inactive_at = coalesce(s.inactive_at, now()),
       verified_at = coalesce(s.verified_at, now()), updated_at = now()
  FROM stg_duplicate d
 WHERE s.sbiz_store_no = d.store_no OR s.localdata_mgmt_no = d.store_no;

SELECT coalesce(s.sbiz_store_no, s.localdata_mgmt_no) AS store_no, s.name, s.status
FROM stores s
WHERE coalesce(s.sbiz_store_no, s.localdata_mgmt_no) IN (
    SELECT store_no FROM stg_demo UNION ALL SELECT store_no FROM stg_duplicate)
ORDER BY s.status, s.name;

""")
    w.write("ROLLBACK;\n" if dry_run else "COMMIT;\n")


def main() -> None:
    a = parse_args()
    demo = read_demo_stores(a.store_map)
    duplicates = read_duplicates(a.duplicates, demo)
    print(f"시연 가게 {len(demo)}곳 · 중복 가게 {len(duplicates)}곳{' · dry-run' if a.dry_run else ''}",
          file=sys.stderr)

    cmd = resolve_psql(a)
    print(f"실행: {cmd[0]} …", file=sys.stderr)
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, text=True, encoding="utf-8")
    assert proc.stdin is not None
    try:
        emit(proc.stdin, demo, duplicates, a.dry_run)
    finally:
        proc.stdin.close()
    if proc.wait() != 0:
        sys.exit(f"psql 실패 (exit {proc.returncode}) — 트랜잭션은 롤백되었습니다.")


if __name__ == "__main__":
    main()
