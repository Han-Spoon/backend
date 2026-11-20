import io
import tempfile
import unittest
from pathlib import Path

from scripts import curate_demo_stores as cds


class CurateDemoStoresTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)

    def write(self, name, text):
        path = self.dir / name
        path.write_text(text, encoding="utf-8")
        return path

    def demo(self):
        return cds.read_demo_stores(self.write(
            "map.csv", "음식점 명,store_no\n기와,MA010120220800319266\n목계화원 분원,3020000-101-2021-00184\n"))

    def test_demo_stores_use_sheet_name(self):
        self.assertEqual([["3020000-101-2021-00184", "목계화원 분원"], ["MA010120220800319266", "기와"]],
                         self.demo())

    def test_store_without_number_stops(self):
        with self.assertRaisesRegex(SystemExit, "store_no 없는 가게"):
            cds.read_demo_stores(self.write("map.csv", "음식점 명,store_no\n기와,\n"))

    def test_duplicate_must_point_to_demo_store(self):
        demo = self.demo()
        dup = self.write("dup.csv", "store_no,음식점 명\nMA0101202307A0010884,목계화원 분원\n")
        self.assertEqual(["MA0101202307A0010884"], cds.read_duplicates(dup, demo))

        with self.assertRaisesRegex(SystemExit, "store-map 에 없는 가게"):
            cds.read_duplicates(self.write("dup.csv", "store_no,음식점 명\nMA1,없는집\n"), demo)
        with self.assertRaisesRegex(SystemExit, "시연 가게 번호"):
            cds.read_duplicates(self.write("dup.csv", "store_no,음식점 명\nMA010120220800319266,기와\n"), demo)

    def test_sql_renames_demo_and_deactivates_duplicate(self):
        buf = io.StringIO()
        cds.emit(buf, [["MA010120220800319266", "기와"]], ["MA0101202307A0010884"], dry_run=True)
        sql = buf.getvalue()

        self.assertIn("SET name = d.name, branch_name = '', verified_at = coalesce(s.verified_at, now())", sql)
        self.assertIn("SET status = 'inactive'", sql)
        self.assertIn("unknown or inactive demo store_no", sql)
        self.assertIn("MA010120220800319266,기와\n", sql)
        self.assertTrue(sql.rstrip().endswith("ROLLBACK;"))


if __name__ == "__main__":
    unittest.main()
