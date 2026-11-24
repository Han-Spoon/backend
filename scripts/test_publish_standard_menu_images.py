import io
import tempfile
import unittest
from pathlib import Path

from scripts import publish_standard_menu_images as psm

HEADER = "menu_id,name_ko,file,source_type,author,license_code,source_url,attribution_text\n"
WIKI = "external_licensed,작성자,CC-BY-SA-4.0,https://commons.wikimedia.org/wiki/File:a.jpg,대표 이미지 · 작성자 · CC BY-SA 4.0"


class PublishStandardMenuImagesTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        for name in ("a.jpg", "b.png"):
            (self.dir / name).write_bytes(name.encode())

    def write(self, body: str) -> Path:
        path = self.dir / "list.csv"
        path.write_text(HEADER + body, encoding="utf-8")
        return path

    def errors(self, body: str) -> str:
        with self.assertRaises(SystemExit) as ctx:
            psm.read_list(self.write(body), self.dir)
        return str(ctx.exception.code)

    def test_publish_path_must_be_public_menus_prefix(self):
        base = ["--list", "l.csv", "--image-dir", "i", "--work-dir", "w", "--bucket", "b"]
        with self.assertRaises(SystemExit):
            psm.parse_args([*base, "--prefix", "scans/"])
        self.assertEqual("menus/standard/", psm.parse_args(base).prefix)

    def test_reads_rows_and_allows_shared_file(self):
        images = psm.read_list(self.write(
            f"39,붕어빵,a.jpg,{WIKI}\n"
            f"51,슈크림 붕어빵,a.jpg,{WIKI}\n"
            "41,비빔밥,b.png,team_original,,,,대표 이미지\n"
            ",,,,,,,\n"), self.dir)

        self.assertEqual([39, 51, 41], [i.menu_id for i in images])
        self.assertEqual("", images[2].license_code)

    def test_collects_row_errors(self):
        message = self.errors(
            f"1,간장게장,a.jpg,{WIKI}\n"
            f"1,간장게장,a.jpg,{WIKI}\n"
            "x,갈비구이,a.jpg,team_original,,,,대표 이미지\n"
            "3,갈비찜,missing.jpg,team_original,,,,대표 이미지\n"
            "4,갈비탕,b.png,external_licensed,작성자,,,대표 이미지\n"
            "5,감자전,b.png,naver,,,,대표 이미지\n"
            "6,감자탕,b.png,team_original,,,,\n")

        for expected in ("3행: menu_id 1 중복", "4행: menu_id 숫자 아님", "5행: 파일 없음 missing.jpg",
                         "6행: external_licensed 는", "7행: source_type 'naver'", "8행: name_ko·file·attribution_text"):
            self.assertIn(expected, message)

    def test_shared_file_needs_same_source(self):
        message = self.errors(
            "1,간장게장,b.png,team_original,,,,대표 이미지\n"
            "2,갈비구이,b.png,team_original,,,,다른 문구\n")

        self.assertIn("3행: b.png 출처 값이 menu_id 1 행과 다름", message)

    def test_header_must_match(self):
        (self.dir / "list.csv").write_text("menu_id,file\n1,a.jpg\n", encoding="utf-8")
        with self.assertRaises(SystemExit):
            psm.read_list(self.dir / "list.csv", self.dir)

    def test_each_file_converted_once(self):
        calls = []

        def convert(src, dst):
            calls.append(src.name)
            dst.write_bytes(b"jpeg-of-" + src.name.encode())
            return 800, 600

        images = psm.read_list(self.write(
            f"39,붕어빵,a.jpg,{WIKI}\n51,슈크림 붕어빵,a.jpg,{WIKI}\n"), self.dir)
        published = psm.convert_all(images, self.dir, self.dir / "out", "menus/standard/", convert=convert)

        self.assertEqual(["a.jpg"], calls)
        pub = published["a.jpg"]
        self.assertEqual(f"menus/standard/{pub.sha256}.jpg", pub.storage_key)
        self.assertTrue((self.dir / "out" / f"{pub.sha256}.jpg").exists())

    def test_sql_links_only_empty_menus_and_rolls_back_on_dry_run(self):
        images = psm.read_list(self.write(f"39,붕어빵,a.jpg,{WIKI}\n"), self.dir)
        published = {"a.jpg": psm.Published("ab" * 32, "menus/standard/x.jpg", 10, 10, 5)}
        buf = io.StringIO()

        psm.emit(buf, images, published, replace=False, dry_run=True)
        sql = buf.getvalue()

        self.assertIn("ON CONFLICT (content_sha256) DO NOTHING", sql)
        self.assertIn("AND m.image_asset_id IS NULL", sql)
        self.assertIn("m.name_ko <> p.name_ko", sql)
        self.assertIn("39,붕어빵," + "ab" * 32 + ",menus/standard/x.jpg", sql)
        self.assertTrue(sql.rstrip().endswith("ROLLBACK;"))

    def test_replace_drops_empty_only_condition(self):
        images = psm.read_list(self.write(f"39,붕어빵,a.jpg,{WIKI}\n"), self.dir)
        published = {"a.jpg": psm.Published("ab" * 32, "menus/standard/x.jpg", 10, 10, 5)}
        buf = io.StringIO()

        psm.emit(buf, images, published, replace=True, dry_run=False)

        self.assertNotIn("AND m.image_asset_id IS NULL", buf.getvalue())
        self.assertTrue(buf.getvalue().rstrip().endswith("COMMIT;"))


if __name__ == "__main__":
    unittest.main()
