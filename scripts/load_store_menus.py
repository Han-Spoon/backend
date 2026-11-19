#!/usr/bin/env python3
"""
가게 메뉴 조사 시트 CSV → store_menus 적재 (메뉴 이름만)

입력
  · --menus: 조사 시트 CSV. 열 '음식점 명', '메뉴명'. 가게명은 첫 메뉴 행에만 있음.
  · --store-map: 열 '음식점 명', 'store_no'(상가업소번호 또는 LOCALDATA 관리번호). 시트의 모든 가게 필수, 번호 빈칸은 건너뜀.

설계 메모
  · 표준 라이브러리만 사용. 전체가 단일 트랜잭션.
  · 이미 있는 (가게, 정규화 이름) 행은 건드리지 않음.
  · 표준 메뉴(menus)와 정규화 이름이 같으면 menu_id 연결(name_match).
  · 사진 열은 읽지 않음.

사용 예
  # 적재 결과만 확인하고 롤백
  python3 scripts/load_store_menus.py --menus menus.csv --store-map store_map.csv --dry-run

  # 실행 없이 SQL 만 확인
  python3 scripts/load_store_menus.py --menus menus.csv --store-map store_map.csv --out /tmp/menus.sql
"""
from __future__ import annotations

import argparse
import csv
import io
import subprocess
import sys
import unicodedata
from pathlib import Path

try:
    from scripts.load_stores import resolve_psql
except ImportError:
    from load_stores import resolve_psql

STORE_COL = "음식점 명"
MENU_COL = "메뉴명"
STORE_NO_COL = "store_no"
MAX_MENU_NAME = 255   # store_menus.display_name
MAX_STORE_NO = 24     # stores.sbiz_store_no(24)·localdata_mgmt_no(22)
NOTICE_NAMES = frozenset({"신규"})
NOTICE_KEYWORDS = ("운영합니다",)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--menus", required=True, type=Path, help="조사 시트 CSV")
    p.add_argument("--store-map", required=True, type=Path, help="음식점 명 → store_no CSV")
    p.add_argument("--dry-run", action="store_true", help="적재 결과 출력 후 롤백")
    p.add_argument("--psql", help="psql 실행 명령. 미지정 시 자동 탐지")
    p.add_argument("--dsn", default="postgresql://hanspoon:hanspoon@localhost:5432/hanspoon",
                   help="로컬 psql 사용 시 접속 문자열")
    p.add_argument("--container", default="hanspoon-postgres", help="docker 폴백에 사용할 컨테이너 이름")
    p.add_argument("--out", type=Path, help="실행하지 않고 SQL 을 이 파일에 기록")
    return p.parse_args(argv)


def clean(value: str | None) -> str:
    return unicodedata.normalize("NFC", value or "").strip()


def is_notice(name: str) -> bool:
    """메뉴가 아닌 안내 행."""
    return name in NOTICE_NAMES or any(k in name for k in NOTICE_KEYWORDS)


def open_csv(path: Path, required: tuple[str, ...]) -> csv.DictReader:
    reader = csv.DictReader(path.open(encoding="utf-8-sig", newline=""))
    headers = {clean(h) for h in reader.fieldnames or []}
    missing = [h for h in required if h not in headers]
    if missing:
        sys.exit(f"{path.name}: 필수 열 없음 {missing}")
    reader.fieldnames = [clean(h) for h in reader.fieldnames or []]
    return reader


def read_menus(path: Path) -> tuple[list[tuple[str, str]], list[tuple[str, str]]]:
    """(가게명, 메뉴명) 목록과 제외한 안내 행. 가게명 빈칸은 위 행 값."""
    menus: list[tuple[str, str]] = []
    notices: list[tuple[str, str]] = []
    store = ""
    for line, row in enumerate(open_csv(path, (STORE_COL, MENU_COL)), start=2):
        store = clean(row.get(STORE_COL)) or store
        name = clean(row.get(MENU_COL))
        if not name:
            continue
        if not store:
            sys.exit(f"{path.name}:{line}: 가게명 없는 메뉴 '{name}'")
        if len(name) > MAX_MENU_NAME:
            sys.exit(f"{path.name}:{line}: 메뉴명 {MAX_MENU_NAME}자 초과")
        (notices if is_notice(name) else menus).append((store, name))
    return menus, notices


def read_store_map(path: Path) -> dict[str, str]:
    """음식점 명 → store_no. 빈 번호는 적재 제외."""
    mapping: dict[str, str] = {}
    for line, row in enumerate(open_csv(path, (STORE_COL, STORE_NO_COL)), start=2):
        store = clean(row.get(STORE_COL))
        if not store:
            continue
        if store in mapping:
            sys.exit(f"{path.name}:{line}: 음식점 명 중복 '{store}'")
        store_no = clean(row.get(STORE_NO_COL))
        if len(store_no) > MAX_STORE_NO:
            sys.exit(f"{path.name}:{line}: store_no {MAX_STORE_NO}자 초과")
        mapping[store] = store_no
    return mapping


def resolve_rows(menus: list[tuple[str, str]], mapping: dict[str, str]) -> tuple[list[list[object]], list[str]]:
    """적재 행(store_no, 메뉴명, 순서)과 번호 없어 건너뛴 가게."""
    unmapped = sorted({store for store, _ in menus if store not in mapping})
    if unmapped:
        sys.exit(f"store-map 에 없는 가게 {len(unmapped)}곳: {unmapped}")
    rows: list[list[object]] = []
    skipped: list[str] = []
    for order, (store, name) in enumerate(menus):
        store_no = mapping[store]
        if not store_no:
            if store not in skipped:
                skipped.append(store)
            continue
        rows.append([store_no, name, order])
    return rows, skipped


def emit(w: io.TextIOBase, rows: list[list[object]], dry_run: bool) -> None:
    w.write("""\
\\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE stg_store_menu (store_no text, display_name text, ord int) ON COMMIT DROP;

\\copy stg_store_menu FROM STDIN WITH (FORMAT csv)
""")
    out = csv.writer(w, lineterminator="\n")
    out.writerows(rows)
    w.write("""\
\\.

DO $store_menu_stores$
DECLARE
    unknown TEXT := (
        SELECT string_agg(DISTINCT m.store_no, ', ')
        FROM stg_store_menu m
        WHERE NOT EXISTS (
            SELECT 1 FROM stores s
            WHERE (s.sbiz_store_no = m.store_no OR s.localdata_mgmt_no = m.store_no) AND s.status = 'active'));
BEGIN
    IF unknown IS NOT NULL THEN
        RAISE EXCEPTION 'unknown or inactive store_no: %', unknown;
    END IF;
END
$store_menu_stores$;

\\echo '── 적재 결과 ──'
WITH src AS (
    SELECT DISTINCT ON (s.id, normalize_food_name(m.display_name))
           s.id AS store_id, normalize_food_name(m.display_name) AS name_normalized, m.display_name
    FROM stg_store_menu m
    JOIN stores s ON s.sbiz_store_no = m.store_no OR s.localdata_mgmt_no = m.store_no
    WHERE normalize_food_name(m.display_name) <> ''
    ORDER BY s.id, normalize_food_name(m.display_name), m.ord
), ins AS (
    INSERT INTO store_menus (store_id, name_normalized, display_name, menu_id, mapping_source)
    SELECT src.store_id, src.name_normalized, src.display_name, mn.id,
           CASE WHEN mn.id IS NULL THEN 'none' ELSE 'name_match' END
    FROM src
    LEFT JOIN menus mn ON mn.name_normalized = src.name_normalized
    ON CONFLICT (store_id, name_normalized) DO NOTHING
    RETURNING menu_id
)
SELECT (SELECT count(*) FROM src)  AS distinct_menus,
       count(*)                    AS inserted,
       count(menu_id)              AS linked_to_menus
FROM ins;

""")
    w.write("ROLLBACK;\n" if dry_run else "COMMIT;\n")


def main() -> None:
    a = parse_args()
    menus, notices = read_menus(a.menus)
    rows, skipped = resolve_rows(menus, read_store_map(a.store_map))
    print(f"메뉴 {len(menus):,} · 적재 대상 {len(rows):,} · 안내 행 제외 {len(notices)}"
          f" · 번호 없는 가게 {len(skipped)}곳{' · dry-run' if a.dry_run else ''}", file=sys.stderr)
    for store, name in notices:
        print(f"  안내 행 제외: {store} / {name}", file=sys.stderr)
    if skipped:
        print(f"  건너뛴 가게: {', '.join(skipped)}", file=sys.stderr)

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
