import io
import tempfile
import unittest
from pathlib import Path

from scripts import load_menu_translations as lmt

HEADER = "name_ko,en,ar,zh-CN,zh-TW,ja,es\n"


class LoadMenuTranslationsTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / "menu_translations.csv"

    def read(self, body: str) -> list[list[str]]:
        self.path.write_text(HEADER + body, encoding="utf-8")
        return lmt.read_translations(self.path)

    def errors(self, body: str) -> str:
        with self.assertRaises(SystemExit) as ctx:
            self.read(body)
        return str(ctx.exception.code)

    def test_reads_trimmed_rows_and_skips_blank_lines(self):
        rows = self.read(" 비빔밥 ,Bibimbap (Mixed Rice),,拌饭,拌飯,ビビンバ,Bibimbap\n,,,,,,\n")

        self.assertEqual([["비빔밥", "Bibimbap (Mixed Rice)", "", "拌饭", "拌飯", "ビビンバ", "Bibimbap"]], rows)

    def test_collects_row_errors(self):
        message = self.errors(
            "비빔밥,Bibimbap,,,,,\n"
            "비빔밥,Bibimbap,,,,,\n"
            "냉면,,,,,,\n"
            "김밥,Gimbap\n")

        self.assertIn("3행: 비빔밥 중복", message)
        self.assertIn("4행: name_ko·en 필수", message)
        self.assertIn("5행: 열 2개", message)

    def test_changed_header_or_empty_file_stops(self):
        self.path.write_text("name_ko,en\n비빔밥,Bibimbap\n", encoding="utf-8")
        with self.assertRaisesRegex(SystemExit, "헤더 불일치"):
            lmt.read_translations(self.path)

        with self.assertRaisesRegex(SystemExit, "번역 행 없음"):
            self.read("")

    def sql(self, dry_run: bool) -> str:
        buf = io.StringIO()
        lmt.emit(buf, self.read("비빔밥,\"Bibimbap, Mixed Rice\",,拌饭,,,\n"), dry_run=dry_run)
        return buf.getvalue()

    def test_updates_only_changed_menus_by_normalized_name(self):
        sql = self.sql(dry_run=False)

        self.assertIn('비빔밥,"Bibimbap, Mixed Rice",,拌饭,,,,0\n\\.', sql)
        self.assertLess(sql.index("정규화하면 같은 메뉴명"), sql.index("UPDATE menus m"))
        self.assertIn("m.name_normalized = normalize_food_name(t.name_ko)", sql)
        self.assertIn("m.name_i18n IS DISTINCT FROM s.name_i18n", sql)
        self.assertIn("'ko', m.name_ko", sql)
        self.assertNotIn("knowledge_revision", sql)
        self.assertTrue(sql.rstrip().endswith("COMMIT;"))

    def test_dry_run_rolls_back(self):
        self.assertTrue(self.sql(dry_run=True).rstrip().endswith("ROLLBACK;"))

    def test_bundled_csv_is_valid(self):
        rows = lmt.read_translations(lmt.DEFAULT_TRANSLATIONS)

        self.assertGreaterEqual(len(rows), 100)
        self.assertTrue(all(r[1] for r in rows))


if __name__ == "__main__":
    unittest.main()
