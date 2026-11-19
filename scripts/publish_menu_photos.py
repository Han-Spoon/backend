#!/usr/bin/env python3
"""
보관한 메뉴 사진 → 공개 경로(menus/) 게시 + image_assets 등록 + store_menus 연결

입력
  · --photo-dir: stage_menu_photos.py 의 내려받기 폴더(원본 + manifest.csv).
  · --store-map: 가게 매핑 CSV(load_store_menus.py 와 같은 파일). 번호 빈칸 가게는 건너뜀.
  · --permitted-stores: 열 '음식점 명'. 허락 받은 가게만 게시. 전체 게시는 --all-stores 로 명시.

설계 메모
  · 표준 라이브러리 + aws CLI + macOS sips. 긴 변 1280px 이하 JPEG 로 변환, 파일명은 변환본 SHA-256.
  · 업로드 후 DB 반영. 업로드 대상은 CloudFront /menus/* 로 즉시 공개.
  · image_assets: external_licensed, 작성자 = 가게명, 출처 = 네이버 플레이스 링크. 같은 해시는 건너뜀.
  · store_menus: 같은 가게·정규화 이름 행 중 사진 없는 행만 연결.
  · --dry-run: 업로드 없이 DB 반영 결과만 출력 후 롤백.

사용 예
  python3 scripts/publish_menu_photos.py --photo-dir ~/hanspoon-menu-photos \\
      --store-map scripts/data/demo_store_map.csv --all-stores \\
      --bucket <images 버킷> --work-dir ~/hanspoon-menu-photos-public --dry-run
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import io
import re
import subprocess
import sys
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

try:
    from scripts.load_store_menus import is_notice, read_store_map
    from scripts.load_stores import resolve_psql
except ImportError:
    from load_store_menus import is_notice, read_store_map
    from load_stores import resolve_psql

MANIFEST_NAME = "manifest.csv"
DEFAULT_PREFIX = "menus/stores/"
DEFAULT_LICENSE = "store-permission"
MAX_EDGE = 1280
JPEG_QUALITY = "80"
CACHE_CONTROL = "public, max-age=31536000, immutable"


@dataclass(frozen=True)
class Photo:
    store: str
    store_no: str   # 상가업소번호 또는 LOCALDATA 관리번호
    menu: str
    source_url: str
    original: str   # 원본 파일명(<sha256>.<ext>)


@dataclass(frozen=True)
class Published:
    sha256: str
    storage_key: str
    width: int
    height: int
    size: int


Convert = Callable[[Path, Path], tuple[int, int]]


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--photo-dir", required=True, type=Path, help="원본 사진 + manifest.csv 폴더")
    p.add_argument("--store-map", required=True, type=Path, help="가게 매핑 CSV (열 '음식점 명', 'store_no')")
    scope = p.add_mutually_exclusive_group(required=True)
    scope.add_argument("--permitted-stores", type=Path, help="허락 받은 가게 CSV (열 '음식점 명')")
    scope.add_argument("--all-stores", action="store_true", help="매니페스트의 모든 가게 게시")
    p.add_argument("--work-dir", required=True, type=Path, help="변환본 폴더")
    p.add_argument("--bucket", required=True, help="S3 버킷")
    p.add_argument("--prefix", default=DEFAULT_PREFIX, help=f"게시 경로 (기본 {DEFAULT_PREFIX})")
    p.add_argument("--license-code", default=DEFAULT_LICENSE, help=f"image_assets.license_code (기본 {DEFAULT_LICENSE})")
    p.add_argument("--dry-run", action="store_true", help="업로드 없이 DB 반영 결과 출력 후 롤백")
    p.add_argument("--psql", help="psql 실행 명령. 미지정 시 자동 탐지")
    p.add_argument("--dsn", default="postgresql://hanspoon:hanspoon@localhost:5432/hanspoon",
                   help="로컬 psql 사용 시 접속 문자열")
    p.add_argument("--container", default="hanspoon-postgres", help="docker 폴백에 사용할 컨테이너 이름")
    a = p.parse_args(argv)
    if not a.prefix.endswith("/"):
        a.prefix += "/"
    if not a.prefix.startswith("menus/"):
        p.error("게시 경로는 CloudFront 공개 경로 menus/ 아래여야 함")
    return a


def clean(value: str | None) -> str:
    return unicodedata.normalize("NFC", value or "").strip()


def read_permitted(path: Path) -> set[str]:
    with path.open(encoding="utf-8-sig", newline="") as fh:
        return {clean(r["음식점 명"]) for r in csv.DictReader(fh) if clean(r.get("음식점 명"))}


def read_photos(manifest: Path, permitted: set[str] | None, store_nos: dict[str, str]) -> list[Photo]:
    """보관 성공 + 가게 번호 있음 + 허락 가게 + 메뉴 행(안내 행 제외)."""
    with manifest.open(encoding="utf-8-sig", newline="") as fh:
        rows = [{k: clean(v) for k, v in r.items()} for r in csv.DictReader(fh)]
    photos = []
    for r in rows:
        store_no = store_nos.get(r["음식점 명"], "")
        if r["error"] or not store_no or not r["storage_key"] or is_notice(r["메뉴명"]):
            continue
        if permitted is not None and r["음식점 명"] not in permitted:
            continue
        photos.append(Photo(r["음식점 명"], store_no, r["메뉴명"], r["source_url"],
                            r["storage_key"].rsplit("/", 1)[-1]))
    return photos


def sips_size(path: Path) -> tuple[int, int]:
    out = subprocess.run(["sips", "-g", "pixelWidth", "-g", "pixelHeight", str(path)],
                         capture_output=True, text=True, check=True).stdout
    width = int(re.search(r"pixelWidth:\s*(\d+)", out).group(1))
    height = int(re.search(r"pixelHeight:\s*(\d+)", out).group(1))
    return width, height


def sips_convert(src: Path, dst: Path) -> tuple[int, int]:
    """긴 변 MAX_EDGE 이하 JPEG. 작은 사진은 확대하지 않음."""
    cmd = ["sips", "-s", "format", "jpeg", "-s", "formatOptions", JPEG_QUALITY]
    if max(sips_size(src)) > MAX_EDGE:
        cmd += ["-Z", str(MAX_EDGE)]
    subprocess.run([*cmd, str(src), "--out", str(dst)], capture_output=True, check=True)
    return sips_size(dst)


def convert_all(photos: list[Photo], photo_dir: Path, work_dir: Path, prefix: str,
                convert: Convert = sips_convert) -> dict[str, Published]:
    """원본 파일명 → 변환본. 원본별 1회 변환."""
    work_dir.mkdir(parents=True, exist_ok=True)
    tmp = work_dir / ".converting.jpg"
    published: dict[str, Published] = {}
    for original in sorted({p.original for p in photos}):
        width, height = convert(photo_dir / original, tmp)
        data = tmp.read_bytes()
        sha = hashlib.sha256(data).hexdigest()
        target = work_dir / f"{sha}.jpg"
        tmp.replace(target)
        published[original] = Published(sha, f"{prefix}{sha}.jpg", width, height, len(data))
    return published


def upload(work_dir: Path, bucket: str, prefix: str) -> None:
    cmd = ["aws", "s3", "sync", str(work_dir), f"s3://{bucket}/{prefix}", "--only-show-errors",
           "--exclude", ".*", "--content-type", "image/jpeg", "--cache-control", CACHE_CONTROL]
    if subprocess.run(cmd).returncode != 0:
        sys.exit("aws s3 sync 실패")


def emit(w: io.TextIOBase, photos: list[Photo], published: dict[str, Published],
         license_code: str, dry_run: bool) -> None:
    w.write("""\
\\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE stg_menu_photo (
  store_no text, store_name text, display_name text, source_url text,
  sha256 text, storage_key text, width int, height int, file_size bigint, ord int
) ON COMMIT DROP;

\\copy stg_menu_photo FROM STDIN WITH (FORMAT csv)
""")
    out = csv.writer(w, lineterminator="\n")
    for order, p in enumerate(photos):
        pub = published[p.original]
        out.writerow([p.store_no, p.store, p.menu, p.source_url,
                      pub.sha256, pub.storage_key, pub.width, pub.height, pub.size, order])
    w.write(f"""\
\\.

DO $menu_photo_stores$
DECLARE
    unknown TEXT := (
        SELECT string_agg(DISTINCT p.store_no, ', ')
        FROM stg_menu_photo p
        WHERE NOT EXISTS (
            SELECT 1 FROM stores s
            WHERE (s.sbiz_store_no = p.store_no OR s.localdata_mgmt_no = p.store_no) AND s.status = 'active'));
BEGIN
    IF unknown IS NOT NULL THEN
        RAISE EXCEPTION 'unknown or inactive store_no: %', unknown;
    END IF;
END
$menu_photo_stores$;

\\echo '── 이미지 등록 ──'
WITH ins AS (
    INSERT INTO image_assets (storage_key, content_sha256, mime_type, width, height, file_size,
                              source_type, author, license_code, source_url, attribution_text)
    SELECT DISTINCT ON (sha256) storage_key, sha256, 'image/jpeg', width, height, file_size,
           'external_licensed', store_name, {sql_literal(license_code)}, source_url,
           '사진 제공: ' || store_name
    FROM stg_menu_photo
    ORDER BY sha256, ord
    ON CONFLICT (content_sha256) DO NOTHING
    RETURNING id
)
SELECT (SELECT count(DISTINCT sha256) FROM stg_menu_photo) AS photos, count(*) AS inserted FROM ins;

\\echo '── 가게 메뉴 연결 ──'
WITH linked AS (
    UPDATE store_menus sm
       SET image_asset_id = ia.id, updated_at = now()
      FROM stg_menu_photo p
      JOIN stores s        ON s.sbiz_store_no = p.store_no OR s.localdata_mgmt_no = p.store_no
      JOIN image_assets ia ON ia.content_sha256 = p.sha256
     WHERE sm.store_id = s.id
       AND sm.name_normalized = normalize_food_name(p.display_name)
       AND sm.image_asset_id IS NULL
    RETURNING sm.id
)
SELECT count(*) AS linked_store_menus FROM linked;

\\echo '── 연결할 가게 메뉴 없는 사진 ──'
SELECT p.store_name, p.display_name
FROM stg_menu_photo p
JOIN stores s ON s.sbiz_store_no = p.store_no OR s.localdata_mgmt_no = p.store_no
WHERE NOT EXISTS (
    SELECT 1 FROM store_menus sm
    WHERE sm.store_id = s.id AND sm.name_normalized = normalize_food_name(p.display_name))
ORDER BY p.ord;

""")
    w.write("ROLLBACK;\n" if dry_run else "COMMIT;\n")


def sql_literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def main() -> None:
    a = parse_args()
    permitted = None if a.all_stores else read_permitted(a.permitted_stores)
    photos = read_photos(a.photo_dir / MANIFEST_NAME, permitted, read_store_map(a.store_map))
    if not photos:
        sys.exit("게시할 사진 없음")
    published = convert_all(photos, a.photo_dir, a.work_dir, a.prefix)
    print(f"사진 행 {len(photos)} · 가게 {len({p.store_no for p in photos})}곳"
          f" · 게시 파일 {len({v.sha256 for v in published.values()})}"
          f"{' · dry-run (업로드 안 함)' if a.dry_run else ''}", file=sys.stderr)

    if not a.dry_run:
        upload(a.work_dir, a.bucket, a.prefix)
        print(f"업로드 완료: s3://{a.bucket}/{a.prefix}", file=sys.stderr)

    cmd = resolve_psql(a)
    print(f"실행: {cmd[0]} …", file=sys.stderr)
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, text=True, encoding="utf-8")
    assert proc.stdin is not None
    try:
        emit(proc.stdin, photos, published, a.license_code, a.dry_run)
    finally:
        proc.stdin.close()
    if proc.wait() != 0:
        sys.exit(f"psql 실패 (exit {proc.returncode}) — 트랜잭션은 롤백되었습니다.")


if __name__ == "__main__":
    main()
