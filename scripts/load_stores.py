#!/usr/bin/env python3
"""
소상공인시장진흥공단 상가(상권)정보 CSV → stores 적재

설계 메모
  · 표준 라이브러리만 사용.
  · 전체가 단일 트랜잭션. 중간 실패 시 부분 적재가 남지 않음.

사용 예
  # 전국 적재 (로컬 docker)
  python3 scripts/load_stores.py --csv-dir ~/Downloads/소상공인..._20260630 --sweep-inactive

  # 개발용 일부 지역만
  python3 scripts/load_stores.py --csv-dir ... --regions 경북,서울

  # 실행 없이 SQL 만 확인
  python3 scripts/load_stores.py --csv-dir ... --out /tmp/load.sql
"""
from __future__ import annotations

import argparse
from collections import Counter
import csv
import io
import shlex
import shutil
import subprocess
import unicodedata
import sys
from pathlib import Path

MIDDLE_CATEGORY = "I201"          # 한식. MVP 범위
SOURCE = "sbiz"
FULL_DATASET_REGIONS = frozenset({
    "강원", "경기", "경남", "경북", "대구", "대전", "부산", "서울",
    "세종", "울산", "인천", "전남광주", "전북", "제주", "충남", "충북",
})

COL = {  # CSV 헤더 → 내부 키
    "no": "상가업소번호", "name": "상호명", "branch": "지점명",
    "l1c": "상권업종대분류코드", "l1n": "상권업종대분류명",
    "l2c": "상권업종중분류코드", "l2n": "상권업종중분류명",
    "l3c": "상권업종소분류코드", "l3n": "상권업종소분류명",
    "ksicc": "표준산업분류코드", "ksicn": "표준산업분류명",
    "dong": "행정동코드", "addr": "도로명주소", "floor": "층정보",
    "lng": "경도", "lat": "위도",
}


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--csv-dir", required=True, type=Path, help="지역별 CSV 가 들어있는 디렉터리")
    p.add_argument("--source-version", help="스냅샷 버전. 미지정 시 파일명에서 추출 (예: 202606)")
    p.add_argument("--regions", help="쉼표 구분 지역 필터 (예: 경북,서울). 미지정 시 전체")
    p.add_argument("--category", default=MIDDLE_CATEGORY, help=f"적재할 상권업종 중분류 코드 (기본 {MIDDLE_CATEGORY})")
    p.add_argument("--sweep-inactive", "--sweep-closed", dest="sweep_inactive", action="store_true",
                   help="이번 배치에 없는 sbiz 가게를 비활성 처리. 전국 16개·기본 업종의 완전한 적재에서만 허용")
    p.add_argument("--psql", help="psql 실행 명령. 미지정 시 자동 탐지")
    p.add_argument("--dsn", default="postgresql://hanspoon:hanspoon@localhost:5432/hanspoon",
                   help="로컬 psql 사용 시 접속 문자열")
    p.add_argument("--container", default="hanspoon-postgres", help="docker 폴백에 사용할 컨테이너 이름")
    p.add_argument("--out", type=Path, help="실행하지 않고 SQL 을 이 파일에 기록")
    return p.parse_args(argv)


def resolve_psql(a: argparse.Namespace) -> list[str]:
    if a.psql:
        return shlex.split(a.psql)
    if shutil.which("psql"):
        return ["psql", a.dsn]
    if shutil.which("docker"):
        running = subprocess.run(["docker", "ps", "--format", "{{.Names}}"],
                                 capture_output=True, text=True).stdout.split()
        if a.container in running:
            return ["docker", "exec", "-i", a.container, "psql", "-U", "hanspoon", "-d", "hanspoon"]
    sys.exit("psql 을 찾지 못했습니다. --psql 로 실행 명령을 직접 지정하세요.")


def record_failed_batch(cmd: list[str], version: str) -> bool:
    """본 적재 트랜잭션이 롤백된 뒤 실패 감사 기록을 별도 트랜잭션으로 남긴다.

    같은 버전의 성공 이력이 이미 있으면 실패한 재실행이 완료 상태를 덮어쓰지 않는다.
    DB 자체가 연결 불가한 경우에는 기록도 실패할 수 있으므로 원래 오류를 가리지 않고 False를 반환한다.
    """
    sql = f"""
INSERT INTO store_import_batches
    (source, source_version, row_count, status, started_at, finished_at)
VALUES ('{SOURCE}', '{version}', 0, 'failed', now(), now())
ON CONFLICT (source, source_version) DO UPDATE
SET row_count = 0,
    status = 'failed',
    finished_at = now()
WHERE store_import_batches.status <> 'completed';
"""
    try:
        result = subprocess.run(
            [*cmd, "-v", "ON_ERROR_STOP=1", "-c", sql],
            capture_output=True,
            text=True,
        )
    except OSError:
        return False
    return result.returncode == 0


def file_metadata(f: Path) -> tuple[str, str]:
    """파일명 끝의 지역·스냅샷 버전을 읽는다.

    접두부에 밑줄이 추가돼도 영향을 받지 않도록 오른쪽에서 두 토큰만 분리한다.
    macOS가 한글 파일명을 NFD로 저장할 수 있어 비교 전 NFC로 맞춘다.
    """
    parts = unicodedata.normalize("NFC", f.stem).rsplit("_", 2)
    if len(parts) != 3 or len(parts[2]) != 6 or not parts[2].isdigit():
        sys.exit(f"예상한 CSV 파일명이 아닙니다: {f.name}")
    return parts[1], parts[2]


def discover(csv_dir: Path, regions: str | None) -> list[Path]:
    files = sorted(f for f in csv_dir.glob("*.csv"))
    if not files:
        sys.exit(f"CSV 를 찾을 수 없습니다: {csv_dir}")
    if regions:
        want = {unicodedata.normalize("NFC", r.strip()) for r in regions.split(",")}
        files = [f for f in files if file_metadata(f)[0] in want]
        if not files:
            sys.exit(f"지역 필터에 맞는 파일이 없습니다: {regions}")
    return files


def version_of(files: list[Path]) -> str:
    vs = {file_metadata(f)[1] for f in files}
    if len(vs) != 1:
        sys.exit(f"파일들의 스냅샷 버전이 섞여 있습니다: {sorted(vs)}")
    return vs.pop()


def resolve_version(files: list[Path], override: str | None) -> str:
    """파일명 버전을 기준으로 배치 버전을 확정한다.

    사용자가 잘못된 버전을 강제로 지정하면 동일 스냅샷의 감사 기록과 비활성 스윕 범위가
    어긋날 수 있으므로, override는 파일명에서 확인한 버전과 같을 때만 허용한다.
    """
    detected = version_of(files)
    if override and override != detected:
        sys.exit(f"--source-version({override})이 파일 버전({detected})과 다릅니다.")
    return detected


def validate_sweep_scope(a: argparse.Namespace, files: list[Path]) -> None:
    """비활성 스윕이 전국·기본 업종의 완전한 스냅샷에서만 실행되도록 강제한다."""
    if not a.sweep_inactive:
        return
    if a.regions:
        sys.exit("--sweep-inactive는 --regions와 함께 사용할 수 없습니다.")
    if a.category != MIDDLE_CATEGORY:
        sys.exit(
            f"--sweep-inactive는 기본 적재 범위({MIDDLE_CATEGORY})에서만 사용할 수 있습니다. "
            f"현재 범위: {a.category}"
        )

    regions = [file_metadata(f)[0] for f in files]
    counts = Counter(regions)
    duplicates = sorted(region for region, count in counts.items() if count > 1)
    missing = sorted(FULL_DATASET_REGIONS - counts.keys())
    unexpected = sorted(counts.keys() - FULL_DATASET_REGIONS)
    if missing or unexpected or duplicates:
        sys.exit(
            "--sweep-inactive에는 전국 전체 스냅샷이 필요합니다. "
            f"누락={missing or '없음'}, 예상외={unexpected or '없음'}, 중복={duplicates or '없음'}"
        )


def emit(w: io.TextIOBase, files: list[Path], category: str, sweep: bool, version: str) -> dict:
    """SQL 전문을 w 에 스트리밍하고 집계를 돌려준다."""
    stat = {"rows": 0, "cats": 0, "ksic": 0, "skipped": 0}
    out = csv.writer(w, lineterminator="\n")

    w.write(f"""\
\\set ON_ERROR_STOP on
BEGIN;

INSERT INTO store_import_batches (source, source_version, status, started_at)
VALUES ('{SOURCE}', '{version}', 'running', now())
ON CONFLICT (source, source_version)
DO UPDATE SET status = 'running', started_at = now(), finished_at = NULL
RETURNING id AS batch_id
\\gset

CREATE TEMP TABLE stg_category (code text, name text, level smallint, parent_code text) ON COMMIT DROP;
CREATE TEMP TABLE stg_ksic (code text, name text) ON COMMIT DROP;
CREATE TEMP TABLE stg_store (
  sbiz_store_no text, name text, branch_name text, category_code text, ksic_code text,
  admin_dong_code text, road_address text, floor_info text, lat float8, lng float8
) ON COMMIT DROP;

""")

    def copy_block(table: str, rows) -> int:
        w.write(f"\\copy {table} FROM STDIN WITH (FORMAT csv)\n")
        n = 0
        for row in rows:
            out.writerow(row)
            n += 1
        w.write("\\.\n\n")
        return n

    # 업종/KSIC 는 음식 외 대분류까지 전부 모은다 — 참조 데이터는 비용이 없고,
    # 일식·중식 확장 시 재적재 없이 stores 적재 범위만 넓히면 된다.
    cats: dict[str, tuple[str, int, str]] = {}
    ksic: dict[str, str] = {}

    def store_rows():
        """CSV 를 한 행씩 흘려보내며 분류/KSIC 를 부수적으로 수집한다.
        전량을 메모리에 쌓지 않으므로 적재 범위를 전체 업종으로 넓혀도 견딘다."""
        for f in files:
            print(f"  읽는 중 {f.name}", file=sys.stderr)
            with f.open(encoding="utf-8", newline="") as fh:
                for r in csv.DictReader(fh):
                    if r[COL["l1c"]]:
                        cats.setdefault(r[COL["l1c"]], (r[COL["l1n"]], 1, ""))
                    if r[COL["l2c"]]:
                        cats.setdefault(r[COL["l2c"]], (r[COL["l2n"]], 2, r[COL["l1c"]]))
                    if r[COL["l3c"]]:
                        cats.setdefault(r[COL["l3c"]], (r[COL["l3n"]], 3, r[COL["l2c"]]))
                    if r[COL["ksicc"]].strip():
                        ksic.setdefault(r[COL["ksicc"]].strip(), r[COL["ksicn"]].strip())

                    if r[COL["l2c"]] != category:
                        continue
                    # sbiz_store_no 는 멱등 upsert 의 충돌 키다. 비면 중복 행이 쌓이므로 버린다.
                    if not r[COL["no"]].strip() or not r[COL["name"]].strip():
                        stat["skipped"] += 1
                        continue
                    try:
                        lat, lng = float(r[COL["lat"]]), float(r[COL["lng"]])
                    except ValueError:
                        stat["skipped"] += 1
                        continue
                    # 스키마 CHECK 와 같은 범위. 여기서 거르면 트랜잭션 전체가 죽는 일이 없다.
                    if not (33 <= lat <= 39 and 124 <= lng <= 132):
                        stat["skipped"] += 1
                        continue
                    yield [
                        r[COL["no"]].strip(), r[COL["name"]].strip(), r[COL["branch"]].strip(),
                        r[COL["l3c"]].strip(), r[COL["ksicc"]].strip(),
                        r[COL["dong"]].strip(), r[COL["addr"]].strip(), r[COL["floor"]].strip(),
                        lat, lng,
                    ]

    stat["rows"] = copy_block("stg_store", store_rows())
    stat["cats"] = copy_block("stg_category", ([c, v[0], v[1], v[2]] for c, v in sorted(cats.items())))
    stat["ksic"] = copy_block("stg_ksic", ([c, n] for c, n in sorted(ksic.items())))

    w.write("""\
INSERT INTO store_categories (code, name, level)
SELECT DISTINCT ON (code) code, name, 1 FROM stg_category WHERE level = 1 ORDER BY code
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, updated_at = now();

INSERT INTO store_categories (code, name, level, parent_id)
SELECT DISTINCT ON (s.code) s.code, s.name, 2, p.id
FROM stg_category s JOIN store_categories p ON p.code = s.parent_code
WHERE s.level = 2 ORDER BY s.code
ON CONFLICT (code) DO UPDATE
  SET name = EXCLUDED.name, parent_id = EXCLUDED.parent_id, updated_at = now();

INSERT INTO store_categories (code, name, level, parent_id)
SELECT DISTINCT ON (s.code) s.code, s.name, 3, p.id
FROM stg_category s JOIN store_categories p ON p.code = s.parent_code
WHERE s.level = 3 ORDER BY s.code
ON CONFLICT (code) DO UPDATE
  SET name = EXCLUDED.name, parent_id = EXCLUDED.parent_id, updated_at = now();

INSERT INTO ksic_codes (code, name)
SELECT DISTINCT ON (code) code, name FROM stg_ksic ORDER BY code
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, updated_at = now();

INSERT INTO stores (
  sbiz_store_no, name, branch_name, category_id, ksic_code,
  admin_dong_code, road_address, floor_info, lat, lng,
  status, origin, last_batch_id, created_at, updated_at)
SELECT s.sbiz_store_no, s.name, coalesce(s.branch_name, ''), c.id,
       (SELECT k.code FROM ksic_codes k WHERE k.code = NULLIF(s.ksic_code, '')),
       coalesce(s.admin_dong_code, ''), coalesce(s.road_address, ''), coalesce(s.floor_info, ''),
       s.lat, s.lng,
       'active', 'sbiz', :batch_id, now(), now()
FROM stg_store s
JOIN store_categories c ON c.code = s.category_code
ON CONFLICT (sbiz_store_no) DO UPDATE SET
  name            = EXCLUDED.name,
  branch_name     = EXCLUDED.branch_name,   -- EXCLUDED 는 위 SELECT 의 coalesce 결과라 NULL 이 아니다
  category_id     = EXCLUDED.category_id,
  ksic_code       = EXCLUDED.ksic_code,
  admin_dong_code = EXCLUDED.admin_dong_code,
  road_address    = EXCLUDED.road_address,
  floor_info      = EXCLUDED.floor_info,
  lat             = EXCLUDED.lat,
  lng             = EXCLUDED.lng,
  last_batch_id   = EXCLUDED.last_batch_id,
  updated_at      = now(),
  -- 이전 스냅샷에서 빠졌던 가게가 다시 나타나면 활성 상태로 되돌린다.
  status      = CASE WHEN stores.status = 'inactive' THEN 'active' ELSE stores.status END,
  inactive_at = CASE WHEN stores.status = 'inactive' THEN NULL     ELSE stores.inactive_at END;

""")

    if sweep:
        w.write("""\
-- 이번 스냅샷에 없는 sbiz 가게를 비활성 처리. 실제 폐업 확정으로 해석하지 않는다.
-- Python 사전 검증을 통과한 전국 16개·기본 업종의 완전한 스냅샷에서만 실행된다.
UPDATE stores SET status = 'inactive', inactive_at = now(), updated_at = now()
WHERE origin = 'sbiz' AND status = 'active' AND last_batch_id IS DISTINCT FROM :batch_id;

""")

    w.write("""\
UPDATE store_import_batches
   SET status = 'completed', finished_at = now(), row_count = (SELECT count(*) FROM stg_store)
 WHERE id = :batch_id;

COMMIT;

\\echo '── 적재 결과 ──'
SELECT (SELECT count(*) FROM store_categories) AS categories,
       (SELECT count(*) FROM ksic_codes)       AS ksic_codes,
       (SELECT count(*) FROM stores WHERE status = 'active') AS active_stores,
       (SELECT count(*) FROM stores WHERE status = 'inactive') AS inactive_stores;
ANALYZE stores;
""")
    return stat


def main() -> None:
    a = parse_args()
    files = discover(a.csv_dir, a.regions)
    version = resolve_version(files, a.source_version)
    validate_sweep_scope(a, files)
    print(f"대상 파일 {len(files)}개 · 스냅샷 {version} · 중분류 {a.category}"
          f"{' · 비활성 스윕 ON' if a.sweep_inactive else ''}", file=sys.stderr)

    if a.out:
        with a.out.open("w", encoding="utf-8") as fh:
            stat = emit(fh, files, a.category, a.sweep_inactive, version)
        print(f"SQL 기록: {a.out} ({a.out.stat().st_size / 1e6:.1f} MB)", file=sys.stderr)
    else:
        cmd = resolve_psql(a)
        # DSN에는 비밀번호가 포함될 수 있으므로 실행 파일 이름 외에는 로그에 남기지 않는다.
        print(f"실행: {cmd[0]} …", file=sys.stderr)
        proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, text=True, encoding="utf-8")
        assert proc.stdin is not None
        try:
            stat = emit(proc.stdin, files, a.category, a.sweep_inactive, version)
        finally:
            proc.stdin.close()
        if proc.wait() != 0:
            if not record_failed_batch(cmd, version):
                print("경고: 적재 실패 감사 기록을 DB에 남기지 못했습니다.", file=sys.stderr)
            sys.exit(f"psql 실패 (exit {proc.returncode}) — 트랜잭션은 롤백되었습니다.")

    print(f"분류 {stat['cats']:,} · KSIC {stat['ksic']:,} · 가게 {stat['rows']:,}"
          f" · 좌표 이상으로 제외 {stat['skipped']:,}", file=sys.stderr)


if __name__ == "__main__":
    main()
