import io
import tempfile
import unittest
from pathlib import Path

from scripts import publish_menu_photos as pmp

MANIFEST = (
    "음식점 명,sbiz_store_no,메뉴명,source_url,image_url,content_sha256,storage_key,mime_type,file_size,error\n"
    "가게A,MA1,김치찌개,https://naver.me/a,u1,s1,image-imports/naver/s1.jpg,image/jpeg,10,\n"
    "가게A,MA1,김치찌개 2인,https://naver.me/a,u1,s1,image-imports/naver/s1.jpg,image/jpeg,10,\n"
    "가게B,MA2,된장찌개,https://naver.me/b,u2,s2,image-imports/naver/s2.png,image/png,10,\n"
    "가게C,,제육,https://naver.me/c,u3,s3,image-imports/naver/s3.jpg,image/jpeg,10,\n"
    "가게A,MA1,웃주,https://naver.me/a,u4,,,,,지원하지 않는 이미지 형식\n"
)


class PublishMenuPhotosTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        self.manifest = self.dir / "manifest.csv"
        self.manifest.write_text(MANIFEST, encoding="utf-8")

    def args(self, *extra):
        return ["--photo-dir", "d", "--work-dir", "w", "--bucket", "b", *extra]

    def test_publish_path_must_be_public_menus_prefix(self):
        with self.assertRaises(SystemExit):
            pmp.parse_args(self.args("--all-stores", "--prefix", "image-imports/naver/"))
        self.assertEqual("menus/x/", pmp.parse_args(self.args("--all-stores", "--prefix", "menus/x")).prefix)

    def test_store_scope_must_be_explicit(self):
        with self.assertRaises(SystemExit):
            pmp.parse_args(self.args())

    def test_only_stored_mapped_rows_are_published(self):
        photos = pmp.read_photos(self.manifest, None)

        self.assertEqual(["김치찌개", "김치찌개 2인", "된장찌개"], [p.menu for p in photos])
        self.assertEqual("s2.png", photos[2].original)

    def test_permitted_stores_filter(self):
        photos = pmp.read_photos(self.manifest, {"가게B"})

        self.assertEqual(["가게B"], [p.store for p in photos])

    def test_each_original_converted_once(self):
        calls = []

        def convert(src, dst):
            calls.append(src.name)
            dst.write_bytes(b"jpeg-of-" + src.name.encode())
            return 1280, 960

        photos = pmp.read_photos(self.manifest, None)
        published = pmp.convert_all(photos, self.dir, self.dir / "out", "menus/stores/", convert=convert)

        self.assertEqual(["s1.jpg", "s2.png"], calls)
        pub = published["s2.png"]
        self.assertEqual(f"menus/stores/{pub.sha256}.jpg", pub.storage_key)
        self.assertTrue((self.dir / "out" / f"{pub.sha256}.jpg").exists())
        self.assertEqual((1280, 960), (pub.width, pub.height))

    def test_sql_keeps_existing_links_and_rolls_back_on_dry_run(self):
        photos = pmp.read_photos(self.manifest, {"가게B"})
        published = {"s2.png": pmp.Published("ab" * 32, "menus/stores/x.jpg", 10, 10, 5)}
        buf = io.StringIO()

        pmp.emit(buf, photos, published, "store-permission", dry_run=True)
        sql = buf.getvalue()

        self.assertIn("ON CONFLICT (content_sha256) DO NOTHING", sql)
        self.assertIn("AND sm.image_asset_id IS NULL", sql)
        self.assertIn("'external_licensed', store_name, 'store-permission'", sql)
        self.assertTrue(sql.rstrip().endswith("ROLLBACK;"))

    def test_license_code_is_escaped(self):
        self.assertEqual("'it''s'", pmp.sql_literal("it's"))


if __name__ == "__main__":
    unittest.main()
