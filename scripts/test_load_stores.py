import argparse
import csv
import io
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from scripts import load_stores


class LoadStoresSafetyTest(unittest.TestCase):

    def test_full_default_snapshot_allows_sweep(self):
        args = self.args(sweep_inactive=True)

        load_stores.validate_sweep_scope(args, self.files())

    def test_region_filter_rejects_sweep(self):
        args = self.args(sweep_inactive=True, regions="서울")

        with self.assertRaisesRegex(SystemExit, "--regions"):
            load_stores.validate_sweep_scope(args, self.files(["서울"]))

    def test_non_default_category_rejects_sweep(self):
        args = self.args(sweep_inactive=True, category="I202")

        with self.assertRaisesRegex(SystemExit, "기본 적재 범위"):
            load_stores.validate_sweep_scope(args, self.files())

    def test_missing_region_rejects_sweep(self):
        regions = load_stores.FULL_DATASET_REGIONS - {"서울"}

        with self.assertRaisesRegex(SystemExit, "서울"):
            load_stores.validate_sweep_scope(self.args(sweep_inactive=True), self.files(regions))

    def test_duplicate_region_rejects_sweep(self):
        files = self.files() + [Path("상가_정보_서울_202606.csv")]

        with self.assertRaisesRegex(SystemExit, r"중복=\['서울'\]"):
            load_stores.validate_sweep_scope(self.args(sweep_inactive=True), files)

    def test_partial_load_without_sweep_is_allowed(self):
        args = self.args(sweep_inactive=False, regions="서울", category="I202")

        load_stores.validate_sweep_scope(args, self.files(["서울"]))

    def test_source_version_must_match_file_version(self):
        with self.assertRaisesRegex(SystemExit, "파일 버전"):
            load_stores.resolve_version(self.files(), "202603")

    def test_detected_source_version_is_used(self):
        self.assertEqual("202606", load_stores.resolve_version(self.files(), None))

    def test_unexpected_filename_is_rejected(self):
        with self.assertRaisesRegex(SystemExit, "예상한 CSV 파일명"):
            load_stores.resolve_version([Path("stores.csv")], None)

    def test_inactive_sweep_sql_does_not_claim_store_is_closed(self):
        output = io.StringIO()

        load_stores.emit(output, [], load_stores.MIDDLE_CATEGORY, True, "202606")

        sql = output.getvalue()
        self.assertIn("status = 'inactive'", sql)
        self.assertIn("inactive_at = now()", sql)
        self.assertNotIn("is_verified", sql)
        self.assertNotIn("status = 'closed'", sql)
        self.assertIn("parent.code = 'I201'", sql)

    def test_import_sql_serializes_loaders_and_analyzes_before_commit(self):
        output = io.StringIO()

        load_stores.emit(output, [], load_stores.MIDDLE_CATEGORY, False, "202606")

        sql = output.getvalue()
        self.assertIn("pg_advisory_xact_lock", sql)
        self.assertIn("store import count mismatch", sql)
        self.assertLess(sql.index("ANALYZE stores;"), sql.index("COMMIT;"))

    def test_legacy_sweep_option_maps_to_inactive_sweep(self):
        args = load_stores.parse_args(["--csv-dir", "/tmp", "--sweep-closed"])

        self.assertTrue(args.sweep_inactive)

    def test_custom_psql_command_preserves_quoted_arguments(self):
        args = SimpleNamespace(psql='psql "postgresql://user:p w@localhost/db"')

        self.assertEqual(
            ["psql", "postgresql://user:p w@localhost/db"],
            load_stores.resolve_psql(args),
        )

    @patch("scripts.load_stores.subprocess.run")
    def test_failed_batch_is_recorded_without_overwriting_completed_batch(self, run):
        run.return_value = SimpleNamespace(returncode=0)

        recorded = load_stores.record_failed_batch(["psql", "postgresql://secret-dsn"], "202606")

        self.assertTrue(recorded)
        command = run.call_args.args[0]
        self.assertEqual("psql", command[0])
        self.assertIn("ON_ERROR_STOP=1", command)
        sql = command[-1]
        self.assertIn("status = 'failed'", sql)
        self.assertIn("status <> 'completed'", sql)

    @patch("scripts.load_stores.subprocess.run")
    def test_failed_batch_recording_failure_does_not_raise(self, run):
        run.return_value = SimpleNamespace(returncode=1)

        self.assertFalse(load_stores.record_failed_batch(["psql"], "202606"))

    @patch("scripts.load_stores.subprocess.run", side_effect=OSError("psql disappeared"))
    def test_failed_batch_recording_process_error_does_not_mask_original_failure(self, run):
        self.assertFalse(load_stores.record_failed_batch(["psql"], "202606"))

    def test_source_validation_accepts_valid_target_row(self):
        with self.csv_file([self.valid_row()]) as path:
            stats = load_stores.validate_source([path], load_stores.MIDDLE_CATEGORY, True)

        self.assertEqual(1, stats["source_rows"])
        self.assertEqual(1, stats["target_rows"])
        self.assertEqual(1, stats["valid_rows"])

    def test_source_validation_rejects_duplicate_store_ids(self):
        row = self.valid_row()
        with self.csv_file([row, row]) as path:
            with self.assertRaisesRegex(SystemExit, "중복 상가업소번호"):
                load_stores.validate_source([path], load_stores.MIDDLE_CATEGORY, False)

    def test_lossless_validation_rejects_invalid_coordinate(self):
        row = self.valid_row()
        row[load_stores.COL["lat"]] = ""
        with self.csv_file([row]) as path:
            with self.assertRaisesRegex(SystemExit, "무손실 원본"):
                load_stores.validate_source([path], load_stores.MIDDLE_CATEGORY, True)

    def test_source_validation_rejects_missing_header(self):
        row = self.valid_row()
        del row[load_stores.COL["lat"]]
        with self.csv_file([row]) as path:
            with self.assertRaisesRegex(SystemExit, "필수 CSV 헤더"):
                load_stores.validate_source([path], load_stores.MIDDLE_CATEGORY, False)

    @staticmethod
    def args(**overrides) -> argparse.Namespace:
        values = {
            "sweep_inactive": False,
            "regions": None,
            "category": load_stores.MIDDLE_CATEGORY,
        }
        values.update(overrides)
        return argparse.Namespace(**values)

    @staticmethod
    def files(regions=None) -> list[Path]:
        selected = regions or load_stores.FULL_DATASET_REGIONS
        return [Path(f"상가_정보_{region}_202606.csv") for region in sorted(selected)]

    @staticmethod
    def valid_row() -> dict[str, str]:
        return {
            load_stores.COL["no"]: "MA010120220800000001",
            load_stores.COL["name"]: "한스푼",
            load_stores.COL["branch"]: "강남점",
            load_stores.COL["l1c"]: "I2",
            load_stores.COL["l1n"]: "음식",
            load_stores.COL["l2c"]: "I201",
            load_stores.COL["l2n"]: "한식",
            load_stores.COL["l3c"]: "I20101",
            load_stores.COL["l3n"]: "백반/한정식",
            load_stores.COL["ksicc"]: "I56111",
            load_stores.COL["ksicn"]: "한식 일반 음식점업",
            load_stores.COL["dong"]: "11680640",
            load_stores.COL["addr"]: "서울특별시 강남구 테헤란로 1",
            load_stores.COL["floor"]: "1",
            load_stores.COL["lng"]: "127.0276",
            load_stores.COL["lat"]: "37.4979",
        }

    @staticmethod
    def csv_file(rows: list[dict[str, str]]):
        class CsvFixture:
            def __enter__(self):
                self.temp_dir = tempfile.TemporaryDirectory()
                self.path = Path(self.temp_dir.name) / "상가_정보_서울_202606.csv"
                with self.path.open("w", encoding="utf-8", newline="") as fh:
                    writer = csv.DictWriter(fh, fieldnames=list(rows[0]))
                    writer.writeheader()
                    writer.writerows(rows)
                return self.path

            def __exit__(self, exc_type, exc_value, traceback):
                self.temp_dir.cleanup()

        return CsvFixture()


if __name__ == "__main__":
    unittest.main()
