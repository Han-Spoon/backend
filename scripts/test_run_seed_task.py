import io
import tarfile
import tempfile
import unittest
from pathlib import Path

from scripts import run_seed_task as rst

ENV = {"DB_HOST": "db.example", "DB_NAME": "hanspoon", "DB_USER": "app", "PGPASSWORD": "pw"}


class RunSeedTaskTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)

    def archive(self, members):
        path = self.dir / "seed.tar.gz"
        with tarfile.open(path, "w:gz") as tar:
            for name, data in members.items():
                info = tarfile.TarInfo(name)
                info.size = len(data)
                tar.addfile(info, io.BytesIO(data))
        return path

    def test_extracts_seed_bundle(self):
        path = self.archive({"seed/menus.csv": b"id\n", "ingredient_translations.csv": b"name_ko\n"})

        seed_dir, translations = rst.extract(path, self.dir / "out")

        self.assertTrue((seed_dir / "menus.csv").is_file())
        self.assertEqual(self.dir / "out" / "ingredient_translations.csv", translations)

    def test_missing_layout_or_path_escape_stops(self):
        with self.assertRaisesRegex(SystemExit, "구성 불일치"):
            rst.extract(self.archive({"seed/menus.csv": b"id\n"}), self.dir / "a")

        with self.assertRaises(tarfile.FilterError):
            rst.extract(self.archive({"../escape.csv": b"x"}), self.dir / "b")

    def test_https_only(self):
        with self.assertRaisesRegex(SystemExit, "https"):
            rst.download("http://example.com/seed.tar.gz", self.dir / "x")

    def test_psql_requires_ssl_and_keeps_password_out_of_args(self):
        psql = rst.psql_command(ENV)

        self.assertIn("sslmode=require", psql)
        self.assertNotIn("pw", psql)
        with self.assertRaisesRegex(SystemExit, "PGPASSWORD"):
            rst.psql_command({**ENV, "PGPASSWORD": ""})

    def test_loader_always_reloads(self):
        cmd = rst.loader_command(Path("s"), Path("t.csv"), "psql x", dry_run=True)

        self.assertEqual(["--reload", "--dry-run"], cmd[-2:])
        self.assertEqual("--reload", rst.loader_command(Path("s"), Path("t.csv"), "psql x", dry_run=False)[-1])


if __name__ == "__main__":
    unittest.main()
