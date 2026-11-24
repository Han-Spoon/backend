#!/usr/bin/env python3
"""
ECS 시드 적재 태스크 진입점

입력
  · --seed-url: 시드 묶음(tar.gz) presigned URL. 묶음 구성: seed/*.csv, ingredient_translations.csv
  · --dry-run: 출력 후 롤백
  · 환경변수 DB_HOST·DB_NAME·DB_USER·PGPASSWORD(태스크 정의 주입)

동작
  · 묶음 내려받기 → 임시 폴더에 압축 해제 → load_knowledge_seed.py --reload 실행(SSL 필수)
"""
from __future__ import annotations

import argparse
import os
import shlex
import subprocess
import sys
import tarfile
import tempfile
import urllib.parse
import urllib.request
from pathlib import Path

MAX_ARCHIVE_BYTES = 50 * 1024 * 1024
LOADER = Path(__file__).with_name("load_knowledge_seed.py")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--seed-url", required=True, help="시드 묶음 presigned URL(https)")
    p.add_argument("--dry-run", action="store_true", help="적재 결과 출력 후 롤백")
    return p.parse_args(argv)


def download(url: str, dest: Path) -> None:
    if urllib.parse.urlparse(url).scheme != "https":
        sys.exit("시드 URL 은 https 만 허용")
    with urllib.request.urlopen(url, timeout=60) as res, dest.open("wb") as out:
        size = 0
        while chunk := res.read(1 << 16):
            size += len(chunk)
            if size > MAX_ARCHIVE_BYTES:
                sys.exit("시드 묶음 크기 초과")
            out.write(chunk)


def extract(archive: Path, dest: Path) -> tuple[Path, Path]:
    """압축 해제 후 (시드 폴더, 번역 파일). 경로 탈출·링크는 data 필터가 차단."""
    with tarfile.open(archive, "r:gz") as tar:
        tar.extractall(dest, filter="data")
    seed_dir, translations = dest / "seed", dest / "ingredient_translations.csv"
    if not seed_dir.is_dir() or not translations.is_file():
        sys.exit("시드 묶음 구성 불일치(seed/, ingredient_translations.csv)")
    return seed_dir, translations


def psql_command(env: dict[str, str]) -> str:
    missing = [k for k in ("DB_HOST", "DB_NAME", "DB_USER", "PGPASSWORD") if not env.get(k)]
    if missing:
        sys.exit(f"환경변수 없음: {', '.join(missing)}")
    conninfo = f"host={env['DB_HOST']} dbname={env['DB_NAME']} user={env['DB_USER']} sslmode=require"
    return shlex.join(["psql", conninfo])


def loader_command(seed_dir: Path, translations: Path, psql: str, dry_run: bool) -> list[str]:
    cmd = [sys.executable, str(LOADER), "--seed-dir", str(seed_dir), "--translations", str(translations),
           "--psql", psql, "--reload"]
    return cmd + ["--dry-run"] if dry_run else cmd


def main() -> None:
    a = parse_args()
    psql = psql_command(dict(os.environ))
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        archive = work / "seed.tar.gz"
        download(a.seed_url, archive)
        seed_dir, translations = extract(archive, work / "bundle")
        print(f"모드: {'dry-run' if a.dry_run else 'apply'}", flush=True)
        sys.exit(subprocess.run(loader_command(seed_dir, translations, psql, a.dry_run)).returncode)


if __name__ == "__main__":
    main()
