#!/usr/bin/env python3
"""
표준 메뉴 대표 이미지 → 공개 경로(menus/standard/) 게시 + image_assets 등록 + menus 연결(메뉴명 기준)

입력
  · --list: 이미지 목록 CSV. 열 name_ko, file, source_type, author, license_code, source_url, attribution_text.
    아직 menus 에 없는 메뉴명도 가능. 한 파일을 여러 메뉴명이 같이 써도 됨(출처 값은 같아야 함).
  · --image-dir: 목록의 file 이 있는 폴더.

설계 메모
  · 변환·업로드는 publish_menu_photos.py 와 같음(긴 변 1280px JPEG, 파일명 = 변환본 SHA-256).
  · 목록의 모든 이미지를 게시·등록. 연결은 menus.name_normalized = normalize_food_name(name_ko) 일치 메뉴만,
    없는 메뉴명은 대기 목록으로 출력. 시드 재적재로 메뉴가 늘면 다시 실행.
  · image_assets: 같은 해시는 건너뜀. menus: 이미지 없는 메뉴만 연결, --replace 면 교체(이전 이미지 행은 남김).
  · 재실행 안전: 같은 파일 = 같은 키·해시라 새 행·새 업로드 없음.
  · --dry-run: 업로드 없이 DB 반영 결과만 출력 후 롤백.

사용 예
  python3 scripts/publish_standard_menu_images.py --list scripts/data/standard_menu_images.csv \\
      --image-dir ~/hanspoon-standard-images --work-dir ~/hanspoon-standard-images-public \\
      --bucket <images 버킷> --dry-run
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import io
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

try:
    from scripts.load_stores import resolve_psql
    from scripts.publish_menu_photos import Convert, Published, clean, sips_convert, upload
except ImportError:
    from load_stores import resolve_psql
    from publish_menu_photos import Convert, Published, clean, sips_convert, upload

DEFAULT_PREFIX = "menus/standard/"
COLUMNS = ["name_ko", "file", "source_type", "author", "license_code", "source_url", "attribution_text"]
SOURCE_TYPES = {"team_original", "external_licensed", "public_domain"}
LICENSED_REQUIRED = ("author", "license_code", "source_url")
# 같은 파일을 쓰는 행끼리 같아야 하는 출처 값
SOURCE_FIELDS = ("source_type", "author", "license_code", "source_url", "attribution_text")


@dataclass(frozen=True)
class MenuImage:
    name_ko: str
    file: str
    source_type: str
    author: str
    license_code: str
    source_url: str
    attribution_text: str


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--list", required=True, type=Path, help="이미지 목록 CSV")
    p.add_argument("--image-dir", required=True, type=Path, help="원본 이미지 폴더")
    p.add_argument("--work-dir", required=True, type=Path, help="변환본 폴더(이 스크립트 전용)")
    p.add_argument("--bucket", required=True, help="S3 버킷")
    p.add_argument("--prefix", default=DEFAULT_PREFIX, help=f"게시 경로 (기본 {DEFAULT_PREFIX})")
    p.add_argument("--replace", action="store_true", help="이미 이미지가 있는 메뉴도 교체")
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


def read_list(path: Path, image_dir: Path) -> list[MenuImage]:
    """검증된 목록. 오류는 모아서 한 번에 중단."""
    with path.open(encoding="utf-8-sig", newline="") as fh:
        reader = csv.DictReader(fh)
        if reader.fieldnames is None or [clean(c) for c in reader.fieldnames] != COLUMNS:
            sys.exit(f"{path.name}: 헤더 불일치 (기대 {','.join(COLUMNS)})")
        rows = [{k: clean(v) for k, v in r.items()} for r in reader]

    errors: list[str] = []
    images: list[MenuImage] = []
    seen_names: set[str] = set()
    by_file: dict[str, MenuImage] = {}
    for line, r in enumerate(rows, start=2):
        if not any(r.values()):
            continue
        where = f"{line}행"
        image = MenuImage(*(r[c] for c in COLUMNS))
        if not image.name_ko or not image.file or not image.attribution_text:
            errors.append(f"{where}: name_ko·file·attribution_text 필수")
        elif image.name_ko in seen_names:
            errors.append(f"{where}: {image.name_ko} 중복")
        seen_names.add(image.name_ko)
        if image.source_type not in SOURCE_TYPES:
            errors.append(f"{where}: source_type '{image.source_type}' 허용 안 됨")
        if image.source_type == "external_licensed" and not all(r[c] for c in LICENSED_REQUIRED):
            errors.append(f"{where}: external_licensed 는 author·license_code·source_url 필수")
        if image.file and not (image_dir / image.file).is_file():
            errors.append(f"{where}: 파일 없음 {image.file}")
        first = by_file.setdefault(image.file, image)
        if any(getattr(first, f) != getattr(image, f) for f in SOURCE_FIELDS):
            errors.append(f"{where}: {image.file} 출처 값이 {first.name_ko} 행과 다름")
        images.append(image)
    if errors:
        sys.exit("목록 오류\n  " + "\n  ".join(errors))
    if not images:
        sys.exit("게시할 이미지 없음")
    return images


def convert_all(images: list[MenuImage], image_dir: Path, work_dir: Path, prefix: str,
                convert: Convert = sips_convert) -> dict[str, Published]:
    """원본 파일명 → 변환본. 파일별 1회 변환."""
    work_dir.mkdir(parents=True, exist_ok=True)
    tmp = work_dir / ".converting.jpg"
    published: dict[str, Published] = {}
    for name in sorted({i.file for i in images}):
        width, height = convert(image_dir / name, tmp)
        data = tmp.read_bytes()
        sha = hashlib.sha256(data).hexdigest()
        tmp.replace(work_dir / f"{sha}.jpg")
        published[name] = Published(sha, f"{prefix}{sha}.jpg", width, height, len(data))
    return published


def emit(w: io.TextIOBase, images: list[MenuImage], published: dict[str, Published],
         replace: bool, dry_run: bool) -> None:
    w.write("""\
\\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE stg_menu_image (
  name_ko text, sha256 text, storage_key text, width int, height int, file_size bigint,
  source_type text, author text, license_code text, source_url text, attribution_text text, ord int
) ON COMMIT DROP;

\\copy stg_menu_image FROM STDIN WITH (FORMAT csv)
""")
    out = csv.writer(w, lineterminator="\n")
    for order, i in enumerate(images):
        pub = published[i.file]
        # 빈 값은 NULL(csv 기본)
        out.writerow([i.name_ko, pub.sha256, pub.storage_key, pub.width, pub.height, pub.size,
                      i.source_type, i.author, i.license_code, i.source_url, i.attribution_text, order])
    link_scope = "" if replace else "\n       AND m.image_asset_id IS NULL"
    w.write(f"""\
\\.

DO $menu_image_names$
DECLARE
    dup TEXT := (
        SELECT string_agg(names, ' / ')
        FROM (SELECT string_agg(name_ko, ', ' ORDER BY ord) AS names
              FROM stg_menu_image GROUP BY normalize_food_name(name_ko) HAVING count(*) > 1) d);
BEGIN
    IF dup IS NOT NULL THEN
        RAISE EXCEPTION '정규화하면 같은 메뉴명: %', dup;
    END IF;
END
$menu_image_names$;

\\echo '── 이미지 등록 ──'
WITH ins AS (
    INSERT INTO image_assets (storage_key, content_sha256, mime_type, width, height, file_size,
                              source_type, author, license_code, source_url, attribution_text)
    SELECT DISTINCT ON (sha256) storage_key, sha256, 'image/jpeg', width, height, file_size,
           source_type, author, license_code, source_url, attribution_text
    FROM stg_menu_image
    ORDER BY sha256, ord
    ON CONFLICT (content_sha256) DO NOTHING
    RETURNING id
)
SELECT (SELECT count(DISTINCT sha256) FROM stg_menu_image) AS images, count(*) AS inserted FROM ins;

\\echo '── 표준 메뉴 연결 ──'
WITH linked AS (
    UPDATE menus m
       SET image_asset_id = ia.id, updated_at = now()
      FROM stg_menu_image p
      JOIN image_assets ia ON ia.content_sha256 = p.sha256
     WHERE m.name_normalized = normalize_food_name(p.name_ko)
       AND m.image_asset_id IS DISTINCT FROM ia.id{link_scope}
    RETURNING m.id
)
SELECT count(*) AS linked_menus FROM linked;

\\echo '── 다른 이미지가 이미 있어 건너뛴 메뉴 ──'
SELECT m.id, m.name_ko, p.name_ko AS list_name
FROM stg_menu_image p
JOIN menus m ON m.name_normalized = normalize_food_name(p.name_ko)
JOIN image_assets ia ON ia.content_sha256 = p.sha256
WHERE m.image_asset_id IS DISTINCT FROM ia.id
ORDER BY m.id;

\\echo '── 대기: 아직 menus 에 없는 메뉴명 ──'
SELECT p.name_ko
FROM stg_menu_image p
WHERE NOT EXISTS (SELECT 1 FROM menus m WHERE m.name_normalized = normalize_food_name(p.name_ko))
ORDER BY p.ord;

\\echo '── 이미지 없는 표준 메뉴 ──'
SELECT id, name_ko FROM menus WHERE image_asset_id IS NULL ORDER BY id;

""")
    w.write("ROLLBACK;\n" if dry_run else "COMMIT;\n")


def main() -> None:
    a = parse_args()
    images = read_list(a.list, a.image_dir)
    published = convert_all(images, a.image_dir, a.work_dir, a.prefix)
    print(f"메뉴명 {len(images)} · 게시 파일 {len({v.sha256 for v in published.values()})}"
          f"{' · 교체' if a.replace else ''}{' · dry-run (업로드 안 함)' if a.dry_run else ''}", file=sys.stderr)

    if not a.dry_run:
        upload(a.work_dir, a.bucket, a.prefix)
        print(f"업로드 완료: s3://{a.bucket}/{a.prefix}", file=sys.stderr)

    cmd = resolve_psql(a)
    print(f"실행: {cmd[0]} …", file=sys.stderr)
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, text=True, encoding="utf-8")
    assert proc.stdin is not None
    try:
        emit(proc.stdin, images, published, a.replace, a.dry_run)
    finally:
        proc.stdin.close()
    if proc.wait() != 0:
        sys.exit(f"psql 실패 (exit {proc.returncode}) — 트랜잭션은 롤백되었습니다.")


if __name__ == "__main__":
    main()
