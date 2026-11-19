import io
import tempfile
import unittest
from pathlib import Path

from scripts import load_store_menus


class LoadStoreMenusTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def write(self, name: str, text: str) -> Path:
        path = Path(self.tmp.name) / name
        path.write_text(text, encoding="utf-8")
        return path

    def test_store_name_fills_down_and_notices_are_skipped(self):
        path = self.write("menus.csv", (
            "음식점 명,메뉴명,메뉴사진\n"
            "가게A,김치찌개,\n"
            ",신규,\n"
            ",\"저녁은 코스만 운영합니다\",\n"
            ",,\n"
            "가게B, 된장찌개 ,\n"
        ))

        menus, notices = load_store_menus.read_menus(path)

        self.assertEqual([("가게A", "김치찌개"), ("가게B", "된장찌개")], menus)
        self.assertEqual([("가게A", "신규"), ("가게A", "저녁은 코스만 운영합니다")], notices)

    def test_menu_without_store_is_rejected(self):
        path = self.write("menus.csv", "음식점 명,메뉴명\n,김치찌개\n")

        with self.assertRaisesRegex(SystemExit, "가게명 없는 메뉴"):
            load_store_menus.read_menus(path)

    def test_missing_header_is_rejected(self):
        path = self.write("menus.csv", "가게,메뉴\n가게A,김치찌개\n")

        with self.assertRaisesRegex(SystemExit, "필수 열 없음"):
            load_store_menus.read_menus(path)

    def test_duplicate_store_in_map_is_rejected(self):
        path = self.write("map.csv", "음식점 명,store_no\n가게A,MA1\n가게A,MA2\n")

        with self.assertRaisesRegex(SystemExit, "음식점 명 중복"):
            load_store_menus.read_store_map(path)

    def test_store_missing_from_map_is_rejected(self):
        with self.assertRaisesRegex(SystemExit, "store-map 에 없는 가게"):
            load_store_menus.resolve_rows([("가게A", "김치찌개")], {})

    def test_blank_store_number_skips_store(self):
        rows, skipped = load_store_menus.resolve_rows(
            [("가게A", "김치찌개"), ("가게B", "된장찌개"), ("가게B", "제육볶음")],
            {"가게A": "MA1", "가게B": ""})

        self.assertEqual([["MA1", "김치찌개", 0]], rows)
        self.assertEqual(["가게B"], skipped)

    def test_dry_run_rolls_back(self):
        self.assertTrue(self.emit(dry_run=True).rstrip().endswith("ROLLBACK;"))
        self.assertTrue(self.emit(dry_run=False).rstrip().endswith("COMMIT;"))

    def test_existing_store_menus_are_kept(self):
        self.assertIn("ON CONFLICT (store_id, name_normalized) DO NOTHING", self.emit(dry_run=False))
        self.assertIn("s.sbiz_store_no = m.store_no OR s.localdata_mgmt_no = m.store_no", self.emit(dry_run=False))

    def test_menu_name_with_comma_is_quoted(self):
        sql = emit([["MA1", "라면, 우동", 0]], dry_run=True)

        self.assertIn('MA1,"라면, 우동",0\n', sql)

    def emit(self, dry_run: bool) -> str:
        return emit([["MA1", "김치찌개", 0]], dry_run)


def emit(rows, dry_run: bool) -> str:
    buf = io.StringIO()
    load_store_menus.emit(buf, rows, dry_run)
    return buf.getvalue()


if __name__ == "__main__":
    unittest.main()
