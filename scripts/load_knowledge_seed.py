#!/usr/bin/env python3
"""
AI 지식 시드 CSV → 지식 테이블 최초 적재 + 가게 메뉴 표준 메뉴 연결

입력
  · --seed-dir: hanspoon-ai `crawling/db_seed.py --out` 출력 폴더(테이블별 CSV).
  · --translations: hanspoon-ai `crawling/ingredient_translations.csv`(재료 7개 언어 이름).

설계 메모
  · 표준 라이브러리만 사용. 전체가 단일 트랜잭션.
  · 최초 적재 전용. 지식 테이블에 행이 있으면 중단(재적재·knowledge_revision 갱신은 범위 밖).
  · 시드 id 그대로 적재(OVERRIDING SYSTEM VALUE) 후 IDENTITY 위치를 최대 id 로 갱신.
  · ingredient_tags·ingredient_composition_tags → tag_codes 배열(정렬·중복 없음, COLLATE "C").
  · 재료 번역 → ingredients.name_i18n. 배치는 completed + finished_at = now().
  · recipe_ingredients.review_item_id 는 스키마 v2 에 없음. 값이 있으면 중단.
  · store_menus: mapping_source = none 행 중 정규화 이름 일치 → menu_id 연결(name_match).

사용 예
  python3 crawling/db_seed.py --out ~/hanspoon-seed        # hanspoon-ai 레포에서
  python3 scripts/load_knowledge_seed.py --seed-dir ~/hanspoon-seed \\
      --translations ../hanspoon-ai/ai/crawling/ingredient_translations.csv --dry-run
"""
from __future__ import annotations

import argparse
import csv
import io
import subprocess
import sys
from pathlib import Path

try:
    from scripts.load_stores import resolve_psql
except ImportError:
    from load_stores import resolve_psql

# 시드 CSV 이름 → 헤더(열 순서 그대로 스테이징)
SEED_FILES: dict[str, list[str]] = {
    "menu_categories": ["id", "name_ko"],
    "menus": ["id", "name_ko", "category_id", "base_menu_id", "remain_token", "ambiguity_flags", "source",
              "needs_review"],
    "ingredients": ["id", "name_ko", "taxonomy_category"],
    "ingredient_tags": ["ingredient_id", "tag_code"],
    "recipe_ingredients": ["menu_id", "ingredient_id", "evidence_type", "review_item_id"],
    "ingredient_compositions": ["parent_ingredient_id", "child_ingredient_id", "relation_type", "ambiguity_flag"],
    "ingredient_composition_tags": ["parent_ingredient_id", "child_ingredient_id", "tag_code"],
    "knowledge_import_batches": ["id", "source", "source_version", "row_count", "status"],
    "menu_recipe_corpora": ["menu_id", "corpus", "n_total", "import_batch_id"],
    "menu_ingredient_priors": ["menu_id", "corpus", "ingredient_id", "n_total", "k_count"],
}
TRANSLATION_HEADER = ["name_ko", "en", "ar", "zh-CN", "zh-TW", "ja", "es"]
IDENTITY_TABLES = ("menu_categories", "menus", "ingredients", "knowledge_import_batches")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--seed-dir", required=True, type=Path, help="db_seed.py 출력 폴더")
    p.add_argument("--translations", required=True, type=Path, help="ingredient_translations.csv")
    p.add_argument("--dry-run", action="store_true", help="적재 결과 출력 후 롤백")
    p.add_argument("--psql", help="psql 실행 명령. 미지정 시 자동 탐지")
    p.add_argument("--dsn", default="postgresql://hanspoon:hanspoon@localhost:5432/hanspoon",
                   help="로컬 psql 사용 시 접속 문자열")
    p.add_argument("--container", default="hanspoon-postgres", help="docker 폴백에 사용할 컨테이너 이름")
    return p.parse_args(argv)


def read_csv(path: Path, header: list[str]) -> list[list[str]]:
    """헤더 일치 확인 후 데이터 행."""
    if not path.is_file():
        sys.exit(f"{path}: 파일 없음")
    with path.open(encoding="utf-8-sig", newline="") as fh:
        rows = list(csv.reader(fh))
    if not rows or [c.strip() for c in rows[0]] != header:
        sys.exit(f"{path.name}: 헤더 불일치 (기대 {','.join(header)})")
    return [r for r in rows[1:] if any(c.strip() for c in r)]


def read_seed(seed_dir: Path, translations: Path) -> dict[str, list[list[str]]]:
    tables = {name: read_csv(seed_dir / f"{name}.csv", header) for name, header in SEED_FILES.items()}
    tables["ingredient_translations"] = read_csv(translations, TRANSLATION_HEADER)
    if not tables["menus"] or not tables["ingredients"]:
        sys.exit("menus·ingredients 시드 비어 있음")
    return tables


def stage(w: io.TextIOBase, name: str, columns: str, rows: list[list[str]]) -> None:
    w.write(f"CREATE TEMP TABLE s_{name} ({columns}) ON COMMIT DROP;\n")
    w.write(f"\\copy s_{name} FROM STDIN WITH (FORMAT csv)\n")
    csv.writer(w, lineterminator="\n").writerows(rows)
    w.write("\\.\n")


def emit(w: io.TextIOBase, tables: dict[str, list[list[str]]], dry_run: bool) -> None:
    w.write("\\set ON_ERROR_STOP on\nBEGIN;\n\n")
    w.write("""\
DO $knowledge_empty$
BEGIN
    IF EXISTS (SELECT 1 FROM menu_categories) OR EXISTS (SELECT 1 FROM menus)
       OR EXISTS (SELECT 1 FROM ingredients) OR EXISTS (SELECT 1 FROM knowledge_import_batches) THEN
        RAISE EXCEPTION '지식 테이블에 이미 행이 있음: 최초 적재 전용';
    END IF;
END
$knowledge_empty$;

""")
    for name, header in SEED_FILES.items():
        stage(w, name, ", ".join(f"{c} text" for c in header), tables[name])
    stage(w, "ingredient_translations", "name_ko text, en text, ar text, zh_cn text, zh_tw text, ja text, es text",
          tables["ingredient_translations"])
    w.write("""
INSERT INTO menu_categories (id, name_ko) OVERRIDING SYSTEM VALUE
SELECT id::bigint, name_ko FROM s_menu_categories;

INSERT INTO menus (id, name_ko, category_id, base_menu_id, remain_token, ambiguity_flags, source, needs_review)
OVERRIDING SYSTEM VALUE
SELECT id::bigint, name_ko, nullif(category_id, '')::bigint, nullif(base_menu_id, '')::bigint,
       nullif(remain_token, ''), nullif(ambiguity_flags, '')::varchar[], source, needs_review::boolean
FROM s_menus
ORDER BY nullif(base_menu_id, '') NULLS FIRST, id::bigint;

INSERT INTO ingredients (id, name_ko, taxonomy_category, tag_codes, name_i18n) OVERRIDING SYSTEM VALUE
SELECT i.id::bigint, i.name_ko, i.taxonomy_category,
       coalesce((SELECT array_agg(d.tag ORDER BY d.tag COLLATE "C")
                 FROM (SELECT DISTINCT t.tag_code AS tag FROM s_ingredient_tags t WHERE t.ingredient_id = i.id) d),
                '{}')::varchar[],
       (SELECT jsonb_strip_nulls(jsonb_build_object(
                   'ko', tr.name_ko, 'en', nullif(tr.en, ''), 'ar', nullif(tr.ar, ''), 'zh-CN', nullif(tr.zh_cn, ''),
                   'zh-TW', nullif(tr.zh_tw, ''), 'ja', nullif(tr.ja, ''), 'es', nullif(tr.es, '')))
        FROM s_ingredient_translations tr WHERE tr.name_ko = i.name_ko)
FROM s_ingredients i;

INSERT INTO recipe_ingredients (menu_id, ingredient_id, evidence_type)
SELECT menu_id::bigint, ingredient_id::bigint, evidence_type FROM s_recipe_ingredients;

INSERT INTO ingredient_compositions (parent_ingredient_id, child_ingredient_id, relation_type, ambiguity_flag, tag_codes)
SELECT c.parent_ingredient_id::bigint, c.child_ingredient_id::bigint, c.relation_type, nullif(c.ambiguity_flag, ''),
       coalesce((SELECT array_agg(d.tag ORDER BY d.tag COLLATE "C")
                 FROM (SELECT DISTINCT t.tag_code AS tag FROM s_ingredient_composition_tags t
                       WHERE t.parent_ingredient_id = c.parent_ingredient_id
                         AND t.child_ingredient_id = c.child_ingredient_id) d),
                '{}')::varchar[]
FROM s_ingredient_compositions c;

DO $seed_shape$
BEGIN
    IF EXISTS (SELECT 1 FROM s_recipe_ingredients WHERE review_item_id <> '') THEN
        RAISE EXCEPTION 'recipe_ingredients.review_item_id 컬럼 없음(스키마 v2)';
    END IF;
    IF EXISTS (SELECT 1 FROM s_ingredient_composition_tags t WHERE NOT EXISTS (
        SELECT 1 FROM s_ingredient_compositions c
        WHERE c.parent_ingredient_id = t.parent_ingredient_id AND c.child_ingredient_id = t.child_ingredient_id)) THEN
        RAISE EXCEPTION '구성 관계 없는 ingredient_composition_tags 행';
    END IF;
END
$seed_shape$;

INSERT INTO knowledge_import_batches (id, source, source_version, row_count, status, finished_at) OVERRIDING SYSTEM VALUE
SELECT id::bigint, source, source_version, row_count::int, status, now() FROM s_knowledge_import_batches;

INSERT INTO menu_recipe_corpora (menu_id, corpus, n_total, import_batch_id)
SELECT menu_id::bigint, corpus, n_total::int, import_batch_id::bigint FROM s_menu_recipe_corpora;

INSERT INTO menu_ingredient_priors (menu_id, corpus, ingredient_id, n_total, k_count)
SELECT menu_id::bigint, corpus, ingredient_id::bigint, n_total::int, k_count::int FROM s_menu_ingredient_priors;

""")
    for table in IDENTITY_TABLES:
        w.write(f"SELECT setval(pg_get_serial_sequence('{table}', 'id'), (SELECT max(id) FROM {table})) \\gset\n")
    w.write("""
\\echo '── 적재 결과 ──'
SELECT (SELECT count(*) FROM menu_categories)          AS categories,
       (SELECT count(*) FROM menus)                    AS menus,
       (SELECT count(*) FROM ingredients)              AS ingredients,
       (SELECT count(*) FROM ingredients WHERE name_i18n IS NOT NULL) AS translated,
       (SELECT count(*) FROM recipe_ingredients)       AS recipe_ingredients,
       (SELECT count(*) FROM ingredient_compositions)  AS compositions,
       (SELECT count(*) FROM menu_recipe_corpora)      AS corpora,
       (SELECT count(*) FROM menu_ingredient_priors)   AS priors;

\\echo '── 가게 메뉴 연결 ──'
WITH linked AS (
    UPDATE store_menus sm
       SET menu_id = m.id, mapping_source = 'name_match', updated_at = now()
      FROM menus m
     WHERE sm.mapping_source = 'none'
       AND sm.name_normalized = m.name_normalized
    RETURNING sm.id
)
SELECT count(*) AS linked_store_menus FROM linked;

""")
    w.write("ROLLBACK;\n" if dry_run else "COMMIT;\n")


def main() -> None:
    a = parse_args()
    tables = read_seed(a.seed_dir, a.translations)
    batches = tables["knowledge_import_batches"]
    print(f"메뉴 {len(tables['menus'])} · 재료 {len(tables['ingredients'])}"
          f" · 재료 확률 {len(tables['menu_ingredient_priors']):,} · 시드 버전 {batches[0][2] if batches else '-'}"
          f"{' · dry-run' if a.dry_run else ''}", file=sys.stderr)

    cmd = resolve_psql(a)
    print(f"실행: {cmd[0]} …", file=sys.stderr)
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, text=True, encoding="utf-8")
    assert proc.stdin is not None
    try:
        emit(proc.stdin, tables, a.dry_run)
    finally:
        proc.stdin.close()
    if proc.wait() != 0:
        sys.exit(f"psql 실패 (exit {proc.returncode}) — 트랜잭션은 롤백되었습니다.")


if __name__ == "__main__":
    main()
