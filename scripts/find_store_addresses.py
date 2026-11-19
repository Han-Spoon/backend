#!/usr/bin/env python3
"""
가게 매핑 보조: 카카오 로컬 검색으로 도로명 주소를 찾고, 같은 주소의 소상공인 음식점 후보 출력

입력
  · --store-map: load_store_menus.py 의 가게 매핑 CSV. 번호 빈칸이고 매칭이 none 이 아닌 가게만 검색.
  · --sbiz-csv: 소상공인 상가정보 원본 CSV (업종 구분 없이 음식 대분류 전체 사용).
  · 환경 변수 KAKAO_REST_API_KEY.

설계 메모
  · 표준 라이브러리만 사용. 매핑 파일은 수정하지 않음, 후보 CSV 만 기록.
  · 카카오로는 가게 이름만 전송. 결과는 저장하지 않고 후보 확인용.

사용 예
  KAKAO_REST_API_KEY=... python3 scripts/find_store_addresses.py \\
      --store-map scripts/data/demo_store_map.csv --sbiz-csv .../상가(상권)정보_서울_202606.csv \\
      --out scripts/data/address_candidates.csv
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict
from pathlib import Path
from typing import Callable

KAKAO_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"
FOOD_GROUP = "FD6"          # 카카오 음식점 카테고리
FOOD_SECTOR = "I2"          # 소상공인 음식 대분류
LOADED_CATEGORY = "I201"    # stores 적재 범위(한식)
SIDO = "서울"
DEFAULT_DISTRICTS = "용산구,중구,마포구"
ROAD_ADDRESS = re.compile(r"(\S+구)\s+(\S+(?:로|길))\s+(?:지하\s*)?(\d+(?:-\d+)?)")

Search = Callable[[str], list[dict]]


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--store-map", required=True, type=Path, help="가게 매핑 CSV")
    p.add_argument("--sbiz-csv", required=True, type=Path, help="소상공인 상가정보 원본 CSV")
    p.add_argument("--out", required=True, type=Path, help="후보 CSV 경로")
    p.add_argument("--districts", default=DEFAULT_DISTRICTS, help=f"남길 자치구 (기본 {DEFAULT_DISTRICTS})")
    return p.parse_args(argv)


def clean(value: str | None) -> str:
    return unicodedata.normalize("NFC", value or "").strip()


def address_key(address: str) -> tuple[str, str, str] | None:
    """(구, 도로명, 건물번호). 시도 표기('서울'/'서울특별시')·건물명 차이 무시."""
    m = ROAD_ADDRESS.search(clean(address))
    return m.groups() if m else None


def read_targets(path: Path) -> list[str]:
    """번호 빈칸이고 none 아닌 가게."""
    with path.open(encoding="utf-8-sig", newline="") as fh:
        return [clean(r["음식점 명"]) for r in csv.DictReader(fh)
                if not clean(r.get("sbiz_store_no")) and clean(r.get("매칭")) != "none"]


def index_sbiz(path: Path, districts: set[str]) -> dict[tuple[str, str, str], list[dict]]:
    """주소 키 → 같은 주소의 음식점 행."""
    index: dict[tuple[str, str, str], list[dict]] = defaultdict(list)
    with path.open(encoding="utf-8", newline="") as fh:
        for r in csv.DictReader(fh):
            if r["상권업종대분류코드"] != FOOD_SECTOR or r["시군구명"] not in districts:
                continue
            key = address_key(r["도로명주소"])
            if key:
                index[key].append(r)
    return index


def kakao_search(api_key: str) -> Search:
    def search(query: str) -> list[dict]:
        params = urllib.parse.urlencode({"query": query, "category_group_code": FOOD_GROUP, "size": 15})
        req = urllib.request.Request(f"{KAKAO_URL}?{params}", headers={"Authorization": f"KakaoAK {api_key}"})
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                return json.load(resp)["documents"]
        except urllib.error.HTTPError as e:
            sys.exit(f"카카오 API 오류 {e.code}: {e.read().decode('utf-8', 'replace')[:200]}")
    return search


def describe(row: dict) -> str:
    loaded = "" if row["상권업종중분류코드"] == LOADED_CATEGORY else " (DB 없음: 한식 외 업종)"
    branch = f" {row['지점명']}" if row["지점명"] else ""
    floor = f" {row['층정보']}층" if row["층정보"] else ""
    return f"{row['상가업소번호']} {row['상호명']}{branch} [{row['상권업종중분류명']}]{floor}{loaded}"


def queries(name: str, districts: set[str]) -> list[str]:
    """검색어 순서: 전체 이름 → 브랜드명(첫 단어) → 브랜드명 + 자치구."""
    brand = name.split()[0]
    first = [name] if brand == name else [name, brand]
    return first + [f"{brand} {d}" for d in sorted(districts)]


def in_districts(road_address: str, districts: set[str]) -> bool:
    """서울 지정 자치구 주소 여부. 다른 시도의 같은 구 이름(대구 중구 등) 제외."""
    key = address_key(road_address)
    return clean(road_address).startswith(SIDO) and key is not None and key[0] in districts


def search_in_districts(name: str, search: Search, districts: set[str]) -> list[dict]:
    """지정 자치구 결과가 나올 때까지 검색어 차례로 시도."""
    for query in queries(name, districts):
        docs = [d for d in search(query) if in_districts(d.get("road_address_name", ""), districts)]
        if docs:
            return docs
    return []


def find_candidates(targets: list[str], search: Search,
                    index: dict[tuple[str, str, str], list[dict]], districts: set[str]) -> list[list[str]]:
    """가게별 카카오 결과 1행씩. 지정 자치구 밖 결과 제외."""
    out: list[list[str]] = []
    for name in targets:
        docs = search_in_districts(name, search, districts)
        if not docs:
            out.append([name, "", "", "", "카카오 검색 결과 없음"])
        for d in docs:
            key = address_key(d["road_address_name"])
            rows = index.get(key, []) if key else []
            candidates = " ; ".join(describe(r) for r in rows) or "같은 주소 음식점 없음"
            out.append([name, d["place_name"], d.get("category_name", ""), d["road_address_name"], candidates])
    return out


def main() -> None:
    a = parse_args()
    api_key = os.environ.get("KAKAO_REST_API_KEY")
    if not api_key:
        sys.exit("환경 변수 KAKAO_REST_API_KEY 가 없습니다.")
    districts = {clean(d) for d in a.districts.split(",") if clean(d)}
    targets = read_targets(a.store_map)
    index = index_sbiz(a.sbiz_csv, districts)
    print(f"검색 대상 {len(targets)}곳 · 주소 색인 {len(index):,}개", file=sys.stderr)

    search = kakao_search(api_key)

    def throttled(query: str) -> list[dict]:
        time.sleep(0.1)
        return search(query)

    rows = find_candidates(targets, throttled, index, districts)
    with a.out.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh, lineterminator="\n")
        w.writerow(["음식점 명", "카카오 상호", "카카오 업종", "카카오 도로명 주소", "같은 주소 공공데이터 후보"])
        w.writerows(rows)
    print(f"후보 기록: {a.out} ({len(rows)}행)", file=sys.stderr)


if __name__ == "__main__":
    main()
