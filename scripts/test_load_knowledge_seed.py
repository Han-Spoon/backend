import io
import tempfile
import unittest
from pathlib import Path

from scripts import load_knowledge_seed as lks

TRANSLATIONS = "name_ko,en,ar,zh-CN,zh-TW,ja,es\n돼지고기,pork,,猪肉,豬肉,豚肉,cerdo\n"


class LoadKnowledgeSeedTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        for name, header in lks.SEED_FILES.items():
            (self.dir / f"{name}.csv").write_text(",".join(header) + "\n", encoding="utf-8")
        self.write("menus", "1,김치찌개,1,,,{has_unclear_broth},curated,false\n")
        self.write("ingredients", "1,돼지고기,unknown\n")
        self.write("knowledge_import_batches", "1,semie_prior,abc1234,10,completed\n")
        self.translations = self.dir / "ingredient_translations.csv"
        self.translations.write_text(TRANSLATIONS, encoding="utf-8")

    def write(self, name, rows):
        path = self.dir / f"{name}.csv"
        path.write_text(path.read_text(encoding="utf-8") + rows, encoding="utf-8")

    def test_reads_all_tables_with_translations(self):
        tables = lks.read_seed(self.dir, self.translations)

        self.assertEqual([["1", "김치찌개", "1", "", "", "{has_unclear_broth}", "curated", "false"]], tables["menus"])
        self.assertEqual("猪肉", tables["ingredient_translations"][0][3])

    def test_missing_file_or_changed_header_stops(self):
        (self.dir / "menu_ingredient_priors.csv").unlink()
        with self.assertRaisesRegex(SystemExit, "파일 없음"):
            lks.read_seed(self.dir, self.translations)

        (self.dir / "menu_ingredient_priors.csv").write_text("menu_id,corpus\n", encoding="utf-8")
        with self.assertRaisesRegex(SystemExit, "헤더 불일치"):
            lks.read_seed(self.dir, self.translations)

    def test_empty_menus_stop(self):
        (self.dir / "menus.csv").write_text(",".join(lks.SEED_FILES["menus"]) + "\n", encoding="utf-8")

        with self.assertRaisesRegex(SystemExit, "비어 있음"):
            lks.read_seed(self.dir, self.translations)

    def sql(self, dry_run):
        buf = io.StringIO()
        lks.emit(buf, lks.read_seed(self.dir, self.translations), dry_run=dry_run)
        return buf.getvalue()

    def test_sql_guards_first_load_and_keeps_seed_ids(self):
        sql = self.sql(dry_run=True)

        self.assertLess(sql.index("최초 적재 전용"), sql.index("INSERT INTO menu_categories"))
        self.assertEqual(4, sql.count("OVERRIDING SYSTEM VALUE"))
        for table in lks.IDENTITY_TABLES:
            self.assertIn(f"pg_get_serial_sequence('{table}', 'id')", sql)
        self.assertIn('ORDER BY d.tag COLLATE "C"', sql)
        self.assertIn("1,김치찌개,1,,,{has_unclear_broth},curated,false\n\\.", sql)

    def test_store_menus_linked_only_when_unmapped(self):
        sql = self.sql(dry_run=False)

        self.assertIn("WHERE sm.mapping_source = 'none'", sql)
        self.assertIn("mapping_source = 'name_match'", sql)
        self.assertTrue(sql.rstrip().endswith("COMMIT;"))
        self.assertTrue(self.sql(dry_run=True).rstrip().endswith("ROLLBACK;"))


if __name__ == "__main__":
    unittest.main()
