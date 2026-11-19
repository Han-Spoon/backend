#!/usr/bin/env python3
"""
조사 시트 메뉴 사진 → S3 비공개 경로 보관 (공개·DB 연결 없음)

입력
  · --photos: 열 '음식점 명', '메뉴명', 'image_url', 'source_url'(네이버 플레이스 링크).

설계 메모
  · 표준 라이브러리 + aws CLI. 파일명은 내용 SHA-256 이라 같은 사진은 한 번만 저장.
  · 보관 경로(image-imports/)는 CloudFront 미연결·공개 차단. 가게 허락 전 노출 없음.
  · image_assets 는 만들지 않음(출처 CHECK). 허락 받은 가게만 menus/ 로 옮겨 연결.
  · 매니페스트에 가게·메뉴·출처·해시 기록, 같은 경로에 함께 업로드.

사용 예
  # 내려받기만 (업로드 없음)
  python3 scripts/stage_menu_photos.py --photos scripts/data/demo_menu_photos.csv \\
      --download-dir ~/hanspoon-photos --download-only

  # 내려받기 + S3 보관
  python3 scripts/stage_menu_photos.py ... --download-dir ~/hanspoon-photos --bucket <images 버킷>
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import subprocess
import sys
import time
import unicodedata
import urllib.request
from pathlib import Path

DEFAULT_PREFIX = "image-imports/naver/"
MANIFEST_NAME = "manifest.csv"
MAX_BYTES = 10 * 1024 * 1024
USER_AGENT = "Mozilla/5.0"
MANIFEST_COLUMNS = ["음식점 명", "메뉴명", "source_url", "image_url",
                    "content_sha256", "storage_key", "mime_type", "file_size", "error"]


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--photos", required=True, type=Path, help="사진 URL CSV")
    p.add_argument("--download-dir", required=True, type=Path, help="사진·매니페스트 로컬 경로")
    p.add_argument("--bucket", help="S3 버킷. --download-only 가 아니면 필수")
    p.add_argument("--prefix", default=DEFAULT_PREFIX, help=f"보관 경로 (기본 {DEFAULT_PREFIX})")
    p.add_argument("--download-only", action="store_true", help="업로드 없이 내려받기만")
    a = p.parse_args(argv)
    if not a.download_only and not a.bucket:
        p.error("--bucket 필요 (또는 --download-only)")
    if a.prefix.startswith("menus/"):
        p.error("menus/ 는 공개 경로라 보관 경로로 쓸 수 없음")
    if not a.prefix.endswith("/"):
        a.prefix += "/"
    return a


def clean(value: str | None) -> str:
    return unicodedata.normalize("NFC", value or "").strip()


def detect_mime(data: bytes) -> tuple[str, str] | None:
    """(MIME, 확장자). image_assets 허용 형식만."""
    if data.startswith(b"\xff\xd8\xff"):
        return "image/jpeg", ".jpg"
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png", ".png"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "image/webp", ".webp"
    return None


def read_photos(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as fh:
        rows = [{k: clean(v) for k, v in r.items()} for r in csv.DictReader(fh)]
    missing = [r for r in rows if not (r.get("음식점 명") and r.get("메뉴명") and r.get("image_url"))]
    if missing:
        sys.exit(f"{path.name}: 가게·메뉴·image_url 빈 행 {len(missing)}개")
    return rows


def download(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = resp.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError("10MB 초과")
    return data


def stage(rows: list[dict[str, str]], out_dir: Path, prefix: str,
          fetch=download, pause: float = 0.2) -> list[dict[str, str]]:
    """사진을 out_dir/<sha256>.<ext> 로 저장하고 매니페스트 행 반환. 실패는 error 열에 기록."""
    out_dir.mkdir(parents=True, exist_ok=True)
    fetched: dict[str, tuple[str, str, str, int]] = {}
    manifest: list[dict[str, str]] = []
    for r in rows:
        entry = {"음식점 명": r["음식점 명"], "메뉴명": r["메뉴명"], "source_url": r.get("source_url", ""), "image_url": r["image_url"]}
        try:
            if r["image_url"] not in fetched:
                data = fetch(r["image_url"])
                kind = detect_mime(data)
                if kind is None:
                    raise ValueError("지원하지 않는 이미지 형식")
                sha = hashlib.sha256(data).hexdigest()
                (out_dir / f"{sha}{kind[1]}").write_bytes(data)
                fetched[r["image_url"]] = (sha, kind[0], kind[1], len(data))
                time.sleep(pause)
            sha, mime, ext, size = fetched[r["image_url"]]
            entry.update(content_sha256=sha, storage_key=f"{prefix}{sha}{ext}",
                         mime_type=mime, file_size=str(size), error="")
        except (OSError, ValueError) as e:
            entry.update(content_sha256="", storage_key="", mime_type="", file_size="", error=str(e)[:200])
        manifest.append(entry)
    return manifest


def write_manifest(path: Path, manifest: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=MANIFEST_COLUMNS, lineterminator="\n")
        w.writeheader()
        w.writerows(manifest)


def upload(out_dir: Path, bucket: str, prefix: str) -> None:
    """사진·매니페스트를 보관 경로로 동기화. 이미 있는 파일은 건너뜀."""
    cmd = ["aws", "s3", "sync", str(out_dir), f"s3://{bucket}/{prefix}", "--only-show-errors"]
    if subprocess.run(cmd).returncode != 0:
        sys.exit("aws s3 sync 실패")


def main() -> None:
    a = parse_args()
    rows = read_photos(a.photos)
    manifest = stage(rows, a.download_dir, a.prefix)
    write_manifest(a.download_dir / MANIFEST_NAME, manifest)

    failed = [m for m in manifest if m["error"]]
    files = {m["content_sha256"] for m in manifest if m["content_sha256"]}
    print(f"사진 행 {len(manifest)} · 파일 {len(files)} · 실패 {len(failed)}", file=sys.stderr)
    for m in failed:
        print(f"  실패: {m['음식점 명']} / {m['메뉴명']}: {m['error']}", file=sys.stderr)

    if a.download_only:
        print(f"내려받기만 완료: {a.download_dir}", file=sys.stderr)
        return
    upload(a.download_dir, a.bucket, a.prefix)
    print(f"보관 완료: s3://{a.bucket}/{a.prefix} (매니페스트 {MANIFEST_NAME})", file=sys.stderr)


if __name__ == "__main__":
    main()
