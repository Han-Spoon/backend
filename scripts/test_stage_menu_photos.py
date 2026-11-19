import hashlib
import tempfile
import unittest
from pathlib import Path

from scripts import stage_menu_photos as smp

JPEG = b"\xff\xd8\xff\xe0" + b"jpeg-body"
PNG = b"\x89PNG\r\n\x1a\n" + b"png-body"


def row(store, menu, url):
    return {"음식점 명": store, "메뉴명": menu, "image_url": url, "source_url": "https://naver.me/x"}


class StageMenuPhotosTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.out = Path(self.tmp.name) / "photos"

    def test_detects_allowed_formats_only(self):
        self.assertEqual(("image/jpeg", ".jpg"), smp.detect_mime(JPEG))
        self.assertEqual(("image/png", ".png"), smp.detect_mime(PNG))
        self.assertEqual(("image/webp", ".webp"), smp.detect_mime(b"RIFF\x00\x00\x00\x00WEBPVP8 "))
        self.assertIsNone(smp.detect_mime(b"GIF89a"))

    def test_public_prefix_is_rejected(self):
        with self.assertRaises(SystemExit):
            smp.parse_args(["--photos", "p.csv", "--download-dir", "d", "--bucket", "b", "--prefix", "menus/naver/"])

    def test_bucket_required_unless_download_only(self):
        with self.assertRaises(SystemExit):
            smp.parse_args(["--photos", "p.csv", "--download-dir", "d"])
        a = smp.parse_args(["--photos", "p.csv", "--download-dir", "d", "--download-only", "--prefix", "imports"])
        self.assertEqual("imports/", a.prefix)

    def test_same_url_fetched_once_and_same_content_stored_once(self):
        calls = []
        bodies = {"u1": JPEG, "u2": JPEG, "u3": PNG}

        def fetch(url):
            calls.append(url)
            return bodies[url]

        manifest = smp.stage(
            [row("가게A", "김치찌개", "u1"), row("가게A", "김치찌개 2인", "u1"),
             row("가게B", "된장찌개", "u2"), row("가게B", "제육", "u3")],
            self.out, "image-imports/naver/", fetch=fetch, pause=0)

        sha = hashlib.sha256(JPEG).hexdigest()
        self.assertEqual(["u1", "u2", "u3"], calls)
        self.assertEqual(2, len(list(self.out.iterdir())))
        self.assertEqual(f"image-imports/naver/{sha}.jpg", manifest[0]["storage_key"])
        self.assertEqual(manifest[0]["storage_key"], manifest[2]["storage_key"])

    def test_failure_is_recorded_without_stopping(self):
        def fetch(url):
            if url == "bad":
                raise OSError("HTTP Error 404")
            return b"GIF89a" if url == "gif" else JPEG

        manifest = smp.stage([row("가게A", "a", "bad"), row("가게A", "b", "gif"), row("가게A", "c", "ok")],
                             self.out, "p/", fetch=fetch, pause=0)

        self.assertEqual(["HTTP Error 404", "지원하지 않는 이미지 형식", ""], [m["error"] for m in manifest])
        self.assertEqual("", manifest[0]["storage_key"])
        self.assertEqual("image/jpeg", manifest[2]["mime_type"])

    def test_rows_without_url_are_rejected(self):
        path = Path(self.tmp.name) / "photos.csv"
        path.write_text("음식점 명,메뉴명,image_url,source_url\n가게A,김치찌개,,\n", encoding="utf-8")

        with self.assertRaisesRegex(SystemExit, "빈 행"):
            smp.read_photos(path)


if __name__ == "__main__":
    unittest.main()
