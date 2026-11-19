#!/usr/bin/env python3
"""
LOCALDATA 일반음식점 인허가 CSV → stores 적재 (가게 매핑 CSV 에 있는 관리번호만)

입력
  · --csv: 행정안전부 일반음식점 인허가 CSV (CP949).
  · --store-map: 가게 매핑 CSV. 열 'store_no' 중 LOCALDATA 관리번호 형식인 값만 적재.

설계 메모
  · 표준 라이브러리만 사용. 전체가 단일 트랜잭션, 관리번호 기준 멱등 upsert.
  · 상가정보에 없는 시연 가게 보완용. 전체 적재·비활성 스윕 없음.
  · 좌표: EPSG:5174(Bessel 중부원점 TM) → WGS84, 7-파라미터 변환. 상가정보 좌표 대비 중앙값 약 2m.
  · 업태 → 업종 분류: 한식·중국식·일식·경양식·분식 중분류, 그 외 음식 대분류(I2).
  · 주소는 상가정보와 같은 도로명+건물번호, 층은 상세주소의 첫 층.
  · 영업 중이 아닌 행, 파일에 없는 관리번호는 중단.

사용 예
  python3 scripts/load_localdata_stores.py --csv ~/Downloads/식품_일반음식점.csv \\
      --store-map scripts/data/demo_store_map.csv --source-version 202611 --dry-run
"""
from __future__ import annotations

import argparse
import csv
import io
import math
import re
import subprocess
import sys
from pathlib import Path

try:
    from scripts.load_stores import resolve_psql
except ImportError:
    from load_stores import resolve_psql

SOURCE = "localdata"
MGMT_NO = re.compile(r"^[0-9]{7}-[0-9]{3}-[0-9]{4}-[0-9]{5}$")   # ck_stores_localdata_mgmt_no
ACTIVE_STATUS = "영업/정상"
CATEGORY_BY_BUSINESS = {"한식": "I201", "중국식": "I202", "일식": "I203", "경양식": "I204", "분식": "I210"}
FALLBACK_CATEGORY = "I2"   # 음식 대분류
ROAD_ADDRESS = re.compile(r"^(.+?(?:로|길)\s+(?:지하\s*)?\d+(?:-\d+)?)(.*)$")
FLOOR = re.compile(r"(지하\s*)?(\d+)(?:\s*[,~-]\s*\d+)*\s*층")
COLUMNS = ("관리번호", "영업상태명", "사업장명", "업태구분명", "도로명주소", "좌표정보(X)", "좌표정보(Y)")

# EPSG:5174 — Bessel 1841, 중부원점(38N, 127°00'10.405"E), 가산 200000/500000
BESSEL = (6377397.155, 1 / 299.1528128)
WGS84 = (6378137.0, 1 / 298.257223563)
LAT0, LON0, X0, Y0 = 38.0, 127.0028902777778, 200000.0, 500000.0
TO_WGS84 = (-115.80, 474.99, 674.11, 1.16, -2.31, -1.63, 6.43)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--csv", required=True, type=Path, help="LOCALDATA 일반음식점 CSV (CP949)")
    p.add_argument("--store-map", required=True, type=Path, help="가게 매핑 CSV (열 store_no)")
    p.add_argument("--source-version", required=True, help="파일 기준 연월 YYYYMM (예: 202611)")
    p.add_argument("--dry-run", action="store_true", help="적재 결과 출력 후 롤백")
    p.add_argument("--psql", help="psql 실행 명령. 미지정 시 자동 탐지")
    p.add_argument("--dsn", default="postgresql://hanspoon:hanspoon@localhost:5432/hanspoon",
                   help="로컬 psql 사용 시 접속 문자열")
    p.add_argument("--container", default="hanspoon-postgres", help="docker 폴백에 사용할 컨테이너 이름")
    a = p.parse_args(argv)
    if not re.fullmatch(r"[0-9]{6}", a.source_version):
        p.error("--source-version 은 YYYYMM 형식")
    return a


def read_targets(path: Path) -> list[str]:
    with path.open(encoding="utf-8-sig", newline="") as fh:
        reader = csv.DictReader(fh)
        if "store_no" not in (reader.fieldnames or []):
            sys.exit(f"store_no 열 없음: {path}")
        targets = sorted({r["store_no"].strip() for r in reader if MGMT_NO.match((r["store_no"] or "").strip())})
    if not targets:
        sys.exit("적재할 LOCALDATA 관리번호 없음")
    return targets


def tm_to_wgs84(x: float, y: float) -> tuple[float, float]:
    """EPSG:5174 평면 좌표 → WGS84 (위도, 경도)."""
    a, f = BESSEL
    e2 = f * (2 - f)
    ep2 = e2 / (1 - e2)
    c0 = 1 - e2 / 4 - 3 * e2 ** 2 / 64 - 5 * e2 ** 3 / 256

    def meridian(phi: float) -> float:
        return a * (c0 * phi - (3 * e2 / 8 + 3 * e2 ** 2 / 32 + 45 * e2 ** 3 / 1024) * math.sin(2 * phi)
                    + (15 * e2 ** 2 / 256 + 45 * e2 ** 3 / 1024) * math.sin(4 * phi)
                    - (35 * e2 ** 3 / 3072) * math.sin(6 * phi))

    mu = (meridian(math.radians(LAT0)) + y - Y0) / (a * c0)
    e1 = (1 - math.sqrt(1 - e2)) / (1 + math.sqrt(1 - e2))
    phi1 = (mu + (3 * e1 / 2 - 27 * e1 ** 3 / 32) * math.sin(2 * mu)
            + (21 * e1 ** 2 / 16 - 55 * e1 ** 4 / 32) * math.sin(4 * mu)
            + (151 * e1 ** 3 / 96) * math.sin(6 * mu) + (1097 * e1 ** 4 / 512) * math.sin(8 * mu))
    sin1, cos1, tan1 = math.sin(phi1), math.cos(phi1), math.tan(phi1)
    n1 = a / math.sqrt(1 - e2 * sin1 ** 2)
    r1 = a * (1 - e2) / (1 - e2 * sin1 ** 2) ** 1.5
    t1, c1 = tan1 ** 2, ep2 * cos1 ** 2
    d = (x - X0) / n1
    lat = phi1 - (n1 * tan1 / r1) * (d ** 2 / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1 ** 2 - 9 * ep2) * d ** 4 / 24
                                     + (61 + 90 * t1 + 298 * c1 + 45 * t1 ** 2 - 252 * ep2 - 3 * c1 ** 2)
                                     * d ** 6 / 720)
    lon = math.radians(LON0) + (d - (1 + 2 * t1 + c1) * d ** 3 / 6
                                + (5 - 2 * c1 + 28 * t1 - 3 * c1 ** 2 + 8 * ep2 + 24 * t1 ** 2) * d ** 5 / 120) / cos1

    # Bessel → WGS84 (지심 좌표 7-파라미터)
    sin_lat = math.sin(lat)
    n = a / math.sqrt(1 - e2 * sin_lat ** 2)
    px, py, pz = n * math.cos(lat) * math.cos(lon), n * math.cos(lat) * math.sin(lon), n * (1 - e2) * sin_lat
    dx, dy, dz, rx, ry, rz, ds = TO_WGS84
    rx, ry, rz = (math.radians(v / 3600) for v in (rx, ry, rz))
    s = 1 + ds * 1e-6
    qx, qy, qz = (dx + s * (px - rz * py + ry * pz), dy + s * (rz * px + py - rx * pz),
                  dz + s * (-ry * px + rx * py + pz))

    a, f = WGS84
    e2 = f * (2 - f)
    p = math.hypot(qx, qy)
    lat = math.atan2(qz, p * (1 - e2))
    for _ in range(10):
        n = a / math.sqrt(1 - e2 * math.sin(lat) ** 2)
        lat = math.atan2(qz, p * (1 - e2 * n * math.cos(lat) / p))
    return math.degrees(lat), math.degrees(math.atan2(qy, qx))


def split_address(address: str) -> tuple[str, str]:
    """'서울특별시 용산구 한강대로15길 9, 103호 (한강로3가)' → (도로명+건물번호, 층)."""
    m = ROAD_ADDRESS.match(address.strip())
    if not m:
        return address.strip(), ""
    floor = FLOOR.search(m.group(2))
    if not floor:
        return m.group(1), ""
    return m.group(1), ("B" if floor.group(1) else "") + floor.group(2)


def prepare_row(r: dict[str, str]) -> list[object]:
    no = r["관리번호"].strip()
    if r["영업상태명"].strip() != ACTIVE_STATUS:
        sys.exit(f"영업 중 아님: {no} {r['사업장명']} ({r['영업상태명']})")
    try:
        lat, lng = tm_to_wgs84(float(r["좌표정보(X)"]), float(r["좌표정보(Y)"]))
    except ValueError:
        sys.exit(f"좌표 없음: {no} {r['사업장명']}")
    if not (33 <= lat <= 39 and 124 <= lng <= 132):
        sys.exit(f"좌표 범위 밖: {no} {r['사업장명']} ({lat}, {lng})")
    road, floor = split_address(r["도로명주소"])
    category = CATEGORY_BY_BUSINESS.get(r["업태구분명"].strip(), FALLBACK_CATEGORY)
    return [no, r["사업장명"].strip(), category, road, floor, round(lat, 7), round(lng, 7)]


def read_stores(path: Path, targets: list[str]) -> list[list[object]]:
    wanted = set(targets)
    found: dict[str, list[object]] = {}
    with path.open(encoding="cp949", errors="replace", newline="") as fh:
        reader = csv.DictReader(fh)
        missing = [c for c in COLUMNS if c not in (reader.fieldnames or [])]
        if missing:
            sys.exit(f"LOCALDATA 헤더 없음: {missing}")
        for r in reader:
            no = r["관리번호"].strip()
            if no in wanted:
                found[no] = prepare_row(r)
    absent = sorted(wanted - found.keys())
    if absent:
        sys.exit(f"LOCALDATA 에 없는 관리번호 {len(absent)}건: {absent}")
    return [found[no] for no in targets]


def emit(w: io.TextIOBase, rows: list[list[object]], version: str, dry_run: bool) -> None:
    w.write(f"""\
\\set ON_ERROR_STOP on
BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended('hanspoon:store-import:{SOURCE}', 0));

INSERT INTO store_import_batches (source, source_version, status, started_at)
VALUES ('{SOURCE}', '{version}', 'running', clock_timestamp())
ON CONFLICT (source, source_version)
DO UPDATE SET status = 'running', started_at = clock_timestamp(), finished_at = NULL
RETURNING id AS batch_id
\\gset

CREATE TEMP TABLE stg_localdata (
  mgmt_no text, name text, category_code text, road_address text, floor_info text, lat float8, lng float8
) ON COMMIT DROP;

\\copy stg_localdata FROM STDIN WITH (FORMAT csv)
""")
    csv.writer(w, lineterminator="\n").writerows(rows)
    w.write(f"""\
\\.

DO $localdata_categories$
DECLARE
    unknown TEXT := (
        SELECT string_agg(DISTINCT s.category_code, ', ') FROM stg_localdata s
        WHERE NOT EXISTS (SELECT 1 FROM store_categories c WHERE c.code = s.category_code));
BEGIN
    IF unknown IS NOT NULL THEN
        RAISE EXCEPTION 'unknown store category: % (상가정보 적재 선행 필요)', unknown;
    END IF;
END
$localdata_categories$;

\\echo '── LOCALDATA 가게 ──'
INSERT INTO stores (
  localdata_mgmt_no, name, category_id, road_address, floor_info, lat, lng,
  status, origin, last_batch_id, created_at, updated_at)
SELECT s.mgmt_no, s.name, c.id, coalesce(s.road_address, ''), coalesce(s.floor_info, ''), s.lat, s.lng,
       'active', '{SOURCE}', :batch_id, now(), now()
FROM stg_localdata s
JOIN store_categories c ON c.code = s.category_code
ON CONFLICT (localdata_mgmt_no) DO UPDATE SET
  name          = EXCLUDED.name,
  category_id   = EXCLUDED.category_id,
  road_address  = EXCLUDED.road_address,
  floor_info    = EXCLUDED.floor_info,
  lat           = EXCLUDED.lat,
  lng           = EXCLUDED.lng,
  last_batch_id = EXCLUDED.last_batch_id,
  updated_at    = now(),
  status      = CASE WHEN stores.status = 'inactive' THEN 'active' ELSE stores.status END,
  inactive_at = CASE WHEN stores.status = 'inactive' THEN NULL     ELSE stores.inactive_at END;

UPDATE store_import_batches
   SET status = 'completed', finished_at = clock_timestamp(), row_count = (SELECT count(*) FROM stg_localdata)
 WHERE id = :batch_id;

SELECT s.localdata_mgmt_no, s.name, c.code AS category, s.road_address, s.floor_info, s.lat, s.lng
FROM stores s
JOIN store_categories c ON c.id = s.category_id
WHERE s.localdata_mgmt_no IN (SELECT mgmt_no FROM stg_localdata)
ORDER BY s.localdata_mgmt_no;

""")
    w.write("ROLLBACK;\n" if dry_run else "COMMIT;\n")


def main() -> None:
    a = parse_args()
    targets = read_targets(a.store_map)
    rows = read_stores(a.csv, targets)
    print(f"LOCALDATA 가게 {len(rows)}곳 · 버전 {a.source_version}{' · dry-run' if a.dry_run else ''}",
          file=sys.stderr)

    cmd = resolve_psql(a)
    print(f"실행: {cmd[0]} …", file=sys.stderr)
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, text=True, encoding="utf-8")
    assert proc.stdin is not None
    try:
        emit(proc.stdin, rows, a.source_version, a.dry_run)
    finally:
        proc.stdin.close()
    if proc.wait() != 0:
        sys.exit(f"psql 실패 (exit {proc.returncode}) — 트랜잭션은 롤백되었습니다.")


if __name__ == "__main__":
    main()
