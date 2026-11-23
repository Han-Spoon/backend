#!/usr/bin/env python3
"""
AI 지식 시드 CSV → 지식 테이블 적재(최초·재적재) + 가게 메뉴 표준 메뉴 연결

입력
  · --seed-dir: hanspoon-ai `crawling/db_seed.py --out` 출력 폴더(테이블별 CSV).
  · --translations: hanspoon-ai `crawling/ingredient_translations.csv`(재료 7개 언어 이름).

설계 메모
  · 표준 라이브러리만 사용. 전체가 단일 트랜잭션.
  · 기본은 최초 적재. 지식 테이블에 행이 있으면 중단.
  · --reload: 기존 메뉴·재료·구성은 시드와 같아야 함(다르면 중단), 신규 id 만 추가.
    시드 메뉴의 recipe_ingredients·corpora·priors 교체, 값이 바뀐 기존 메뉴 knowledge_revision +1,
    리비전 다른 menu_ingredient_cache 삭제. 배치는 새 id 로 추가, 이전 배치는 보존.
  · 시드 id 그대로 적재(OVERRIDING SYSTEM VALUE) 후 IDENTITY 위치를 최대 id 로 갱신.
  · ingredient_tags·ingredient_composition_tags → tag_codes 배열(정렬·중복 없음, COLLATE "C").
  · 재료 번역 → ingredients.name_i18n. 배치는 completed + finished_at = now().
  · recipe_ingredients.review_item_id 는 스키마 v2 에 없음. 값이 있으면 중단.
  · store_menus: mapping_source = none 행 중 정규화 이름 일치 → menu_id 연결(name_match).

사용 예
  python3 crawling/db_seed.py --out ~/hanspoon-seed        # hanspoon-ai 레포에서
  python3 scripts/load_knowledge_seed.py --seed-dir ~/hanspoon-seed \\
      --translations ../hanspoon-ai/ai/crawling/ingredient_translations.csv --dry-run
  python3 scripts/load_knowledge_seed.py --seed-dir ~/hanspoon-seed --translations … --reload --dry-run
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
    p.add_argument("--reload", action="store_true", help="기존 지식 위에 새 시드 재적재")
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


# 시드 CSV → 타입 지정 임시 테이블
TYPED_SQL = """
CREATE TEMP TABLE t_menu_categories ON COMMIT DROP AS
SELECT id::bigint AS id, name_ko FROM s_menu_categories;

CREATE TEMP TABLE t_menus ON COMMIT DROP AS
SELECT id::bigint AS id, name_ko, nullif(category_id, '')::bigint AS category_id,
       nullif(base_menu_id, '')::bigint AS base_menu_id, nullif(remain_token, '') AS remain_token,
       nullif(ambiguity_flags, '')::varchar[] AS ambiguity_flags, source, needs_review::boolean AS needs_review
FROM s_menus;

CREATE TEMP TABLE t_ingredients ON COMMIT DROP AS
SELECT i.id::bigint AS id, i.name_ko, i.taxonomy_category,
       coalesce((SELECT array_agg(d.tag ORDER BY d.tag COLLATE "C")
                 FROM (SELECT DISTINCT t.tag_code AS tag FROM s_ingredient_tags t WHERE t.ingredient_id = i.id) d),
                '{}')::varchar[] AS tag_codes,
       (SELECT jsonb_strip_nulls(jsonb_build_object(
                   'ko', tr.name_ko, 'en', nullif(tr.en, ''), 'ar', nullif(tr.ar, ''), 'zh-CN', nullif(tr.zh_cn, ''),
                   'zh-TW', nullif(tr.zh_tw, ''), 'ja', nullif(tr.ja, ''), 'es', nullif(tr.es, '')))
        FROM s_ingredient_translations tr WHERE tr.name_ko = i.name_ko) AS name_i18n
FROM s_ingredients i;

CREATE TEMP TABLE t_ingredient_compositions ON COMMIT DROP AS
SELECT c.parent_ingredient_id::bigint AS parent_ingredient_id, c.child_ingredient_id::bigint AS child_ingredient_id,
       c.relation_type, nullif(c.ambiguity_flag, '') AS ambiguity_flag,
       coalesce((SELECT array_agg(d.tag ORDER BY d.tag COLLATE "C")
                 FROM (SELECT DISTINCT t.tag_code AS tag FROM s_ingredient_composition_tags t
                       WHERE t.parent_ingredient_id = c.parent_ingredient_id
                         AND t.child_ingredient_id = c.child_ingredient_id) d),
                '{}')::varchar[] AS tag_codes
FROM s_ingredient_compositions c;

CREATE TEMP TABLE t_recipe_ingredients ON COMMIT DROP AS
SELECT menu_id::bigint AS menu_id, ingredient_id::bigint AS ingredient_id, evidence_type FROM s_recipe_ingredients;

-- 배치는 기존 최대 id 뒤로 새 id 부여(최초 적재면 시드 id 와 같음)
CREATE TEMP TABLE t_knowledge_import_batches ON COMMIT DROP AS
SELECT id::bigint AS seed_id,
       (SELECT coalesce(max(id), 0) FROM knowledge_import_batches) + row_number() OVER (ORDER BY id::bigint) AS id,
       source, source_version, row_count::int AS row_count, status
FROM s_knowledge_import_batches;

CREATE TEMP TABLE t_menu_recipe_corpora ON COMMIT DROP AS
SELECT menu_id::bigint AS menu_id, corpus, n_total::int AS n_total, import_batch_id::bigint AS import_batch_id
FROM s_menu_recipe_corpora;

CREATE TEMP TABLE t_menu_ingredient_priors ON COMMIT DROP AS
SELECT menu_id::bigint AS menu_id, corpus, ingredient_id::bigint AS ingredient_id, n_total::int AS n_total,
       k_count::int AS k_count
FROM s_menu_ingredient_priors;

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
    IF EXISTS (SELECT 1 FROM t_menu_recipe_corpora c
               WHERE c.import_batch_id NOT IN (SELECT seed_id FROM t_knowledge_import_batches)) THEN
        RAISE EXCEPTION '시드 배치 없는 menu_recipe_corpora 행';
    END IF;
END
$seed_shape$;

"""

# 기존 행과 시드 일치 확인. 신규 id 추가만 허용
MATCH_SQL = """
DO $seed_match$
BEGIN
    IF EXISTS (SELECT 1 FROM t_menu_categories s JOIN menu_categories c ON c.id = s.id OR c.name_ko = s.name_ko
               WHERE (c.id, c.name_ko) IS DISTINCT FROM (s.id, s.name_ko)) THEN
        RAISE EXCEPTION 'menu_categories: 기존 id·이름 불일치';
    END IF;
    IF EXISTS (SELECT 1 FROM t_menus s JOIN menus m ON m.name_normalized = normalize_food_name(s.name_ko)
               WHERE m.id <> s.id)
       OR EXISTS (SELECT 1 FROM t_menus s JOIN menus m ON m.id = s.id
                  WHERE (m.name_ko, m.category_id, m.base_menu_id, m.remain_token, m.ambiguity_flags, m.source,
                         m.needs_review)
                        IS DISTINCT FROM (s.name_ko, s.category_id, s.base_menu_id, s.remain_token,
                                          s.ambiguity_flags, s.source, s.needs_review)) THEN
        RAISE EXCEPTION 'menus: 기존 메뉴 id·속성 불일치(재적재 범위 밖)';
    END IF;
    IF EXISTS (SELECT 1 FROM t_ingredients s JOIN ingredients i ON i.name_normalized = normalize_food_name(s.name_ko)
               WHERE i.id <> s.id)
       OR EXISTS (SELECT 1 FROM t_ingredients s JOIN ingredients i ON i.id = s.id
                  WHERE (i.name_ko, i.taxonomy_category, i.tag_codes)
                        IS DISTINCT FROM (s.name_ko, s.taxonomy_category, s.tag_codes)) THEN
        RAISE EXCEPTION 'ingredients: 기존 재료 id·속성 불일치(재적재 범위 밖)';
    END IF;
    IF EXISTS (SELECT 1 FROM t_ingredient_compositions s JOIN ingredient_compositions c
                   USING (parent_ingredient_id, child_ingredient_id)
               WHERE (c.relation_type, c.ambiguity_flag, c.tag_codes)
                     IS DISTINCT FROM (s.relation_type, s.ambiguity_flag, s.tag_codes)) THEN
        RAISE EXCEPTION 'ingredient_compositions: 기존 구성 속성 불일치(재적재 범위 밖)';
    END IF;
    IF EXISTS (SELECT 1 FROM t_knowledge_import_batches s JOIN knowledge_import_batches b USING (source, source_version)) THEN
        RAISE EXCEPTION 'knowledge_import_batches: 이미 적재된 시드 버전';
    END IF;
END
$seed_match$;

-- 값이 바뀐 기존 메뉴(재료·코퍼스·확률). 배치 id 차이는 제외
CREATE TEMP TABLE t_changed_menus ON COMMIT DROP AS
SELECT DISTINCT menu_id FROM (
    SELECT menu_id FROM (SELECT menu_id, ingredient_id, evidence_type FROM recipe_ingredients
                         WHERE menu_id IN (SELECT id FROM t_menus)
                         EXCEPT SELECT menu_id, ingredient_id, evidence_type FROM t_recipe_ingredients) a
    UNION SELECT menu_id FROM (SELECT menu_id, ingredient_id, evidence_type FROM t_recipe_ingredients
                               EXCEPT SELECT menu_id, ingredient_id, evidence_type FROM recipe_ingredients) b
    UNION SELECT menu_id FROM (SELECT menu_id, corpus, n_total FROM menu_recipe_corpora
                               WHERE menu_id IN (SELECT id FROM t_menus)
                               EXCEPT SELECT menu_id, corpus, n_total FROM t_menu_recipe_corpora) c
    UNION SELECT menu_id FROM (SELECT menu_id, corpus, n_total FROM t_menu_recipe_corpora
                               EXCEPT SELECT menu_id, corpus, n_total FROM menu_recipe_corpora) d
    UNION SELECT menu_id FROM (SELECT menu_id, corpus, ingredient_id, n_total, k_count FROM menu_ingredient_priors
                               WHERE menu_id IN (SELECT id FROM t_menus)
                               EXCEPT SELECT menu_id, corpus, ingredient_id, n_total, k_count
                               FROM t_menu_ingredient_priors) e
    UNION SELECT menu_id FROM (SELECT menu_id, corpus, ingredient_id, n_total, k_count FROM t_menu_ingredient_priors
                               EXCEPT SELECT menu_id, corpus, ingredient_id, n_total, k_count
                               FROM menu_ingredient_priors) f
) x
WHERE menu_id IN (SELECT id FROM menus);

CREATE TEMP TABLE t_counts ON COMMIT DROP AS
SELECT (SELECT count(*) FROM t_menus WHERE id NOT IN (SELECT id FROM menus))             AS new_menus,
       (SELECT count(*) FROM t_ingredients WHERE id NOT IN (SELECT id FROM ingredients)) AS new_ingredients,
       (SELECT count(*) FROM t_changed_menus)                                            AS revised_menus;

"""

LOAD_SQL = """
INSERT INTO menu_categories (id, name_ko) OVERRIDING SYSTEM VALUE
SELECT id, name_ko FROM t_menu_categories WHERE id NOT IN (SELECT id FROM menu_categories);

INSERT INTO menus (id, name_ko, category_id, base_menu_id, remain_token, ambiguity_flags, source, needs_review)
OVERRIDING SYSTEM VALUE
SELECT id, name_ko, category_id, base_menu_id, remain_token, ambiguity_flags, source, needs_review
FROM t_menus WHERE id NOT IN (SELECT id FROM menus)
ORDER BY base_menu_id NULLS FIRST, id;

INSERT INTO ingredients (id, name_ko, taxonomy_category, tag_codes, name_i18n) OVERRIDING SYSTEM VALUE
SELECT id, name_ko, taxonomy_category, tag_codes, name_i18n
FROM t_ingredients WHERE id NOT IN (SELECT id FROM ingredients);

INSERT INTO ingredient_compositions (parent_ingredient_id, child_ingredient_id, relation_type, ambiguity_flag, tag_codes)
SELECT parent_ingredient_id, child_ingredient_id, relation_type, ambiguity_flag, tag_codes
FROM t_ingredient_compositions
ON CONFLICT (parent_ingredient_id, child_ingredient_id) DO NOTHING;

INSERT INTO knowledge_import_batches (id, source, source_version, row_count, status, finished_at) OVERRIDING SYSTEM VALUE
SELECT id, source, source_version, row_count, status, now() FROM t_knowledge_import_batches;

-- 시드 메뉴만 교체(priors 는 corpora 삭제 시 CASCADE). 이전 배치는 이력으로 보존
DELETE FROM recipe_ingredients WHERE menu_id IN (SELECT id FROM t_menus);
DELETE FROM menu_recipe_corpora WHERE menu_id IN (SELECT id FROM t_menus);

INSERT INTO recipe_ingredients (menu_id, ingredient_id, evidence_type)
SELECT menu_id, ingredient_id, evidence_type FROM t_recipe_ingredients;

INSERT INTO menu_recipe_corpora (menu_id, corpus, n_total, import_batch_id)
SELECT c.menu_id, c.corpus, c.n_total, b.id
FROM t_menu_recipe_corpora c JOIN t_knowledge_import_batches b ON b.seed_id = c.import_batch_id;

INSERT INTO menu_ingredient_priors (menu_id, corpus, ingredient_id, n_total, k_count)
SELECT menu_id, corpus, ingredient_id, n_total, k_count FROM t_menu_ingredient_priors;

UPDATE menus SET knowledge_revision = knowledge_revision + 1, updated_at = now()
WHERE id IN (SELECT menu_id FROM t_changed_menus);

"""


def emit(w: io.TextIOBase, tables: dict[str, list[list[str]]], dry_run: bool, reload: bool = False) -> None:
    w.write("\\set ON_ERROR_STOP on\nBEGIN;\n\n")
    if reload:
        w.write("""\
DO $knowledge_loaded$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM menus) OR NOT EXISTS (SELECT 1 FROM knowledge_import_batches) THEN
        RAISE EXCEPTION '지식 테이블 비어 있음: 최초 적재는 --reload 없이 실행';
    END IF;
END
$knowledge_loaded$;

""")
    else:
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
    w.write(TYPED_SQL)
    w.write(MATCH_SQL)
    w.write(LOAD_SQL)
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

\\echo '── 변경 ──'
WITH stale AS (
    DELETE FROM menu_ingredient_cache c USING menus m
     WHERE m.id = c.menu_id AND c.menu_revision <> m.knowledge_revision
    RETURNING c.menu_id
)
SELECT new_menus, new_ingredients, revised_menus, (SELECT count(*) FROM stale) AS stale_cache_deleted
FROM t_counts;

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
          f"{' · 재적재' if a.reload else ''}{' · dry-run' if a.dry_run else ''}", file=sys.stderr)

    cmd = resolve_psql(a)
    print(f"실행: {cmd[0]} …", file=sys.stderr)
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, text=True, encoding="utf-8")
    assert proc.stdin is not None
    try:
        emit(proc.stdin, tables, a.dry_run, a.reload)
    finally:
        proc.stdin.close()
    if proc.wait() != 0:
        sys.exit(f"psql 실패 (exit {proc.returncode}) — 트랜잭션은 롤백되었습니다.")


if __name__ == "__main__":
    main()
