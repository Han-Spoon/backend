import argparse
import unittest
from pathlib import Path

from scripts import load_stores


class LoadStoresSafetyTest(unittest.TestCase):

    def test_full_default_snapshot_allows_sweep(self):
        args = self.args(sweep_closed=True)

        load_stores.validate_sweep_scope(args, self.files())

    def test_region_filter_rejects_sweep(self):
        args = self.args(sweep_closed=True, regions="서울")

        with self.assertRaisesRegex(SystemExit, "--regions"):
            load_stores.validate_sweep_scope(args, self.files(["서울"]))

    def test_non_default_category_rejects_sweep(self):
        args = self.args(sweep_closed=True, category="I202")

        with self.assertRaisesRegex(SystemExit, "기본 적재 범위"):
            load_stores.validate_sweep_scope(args, self.files())

    def test_missing_region_rejects_sweep(self):
        regions = load_stores.FULL_DATASET_REGIONS - {"서울"}

        with self.assertRaisesRegex(SystemExit, "서울"):
            load_stores.validate_sweep_scope(self.args(sweep_closed=True), self.files(regions))

    def test_duplicate_region_rejects_sweep(self):
        files = self.files() + [Path("상가_정보_서울_202606.csv")]

        with self.assertRaisesRegex(SystemExit, r"중복=\['서울'\]"):
            load_stores.validate_sweep_scope(self.args(sweep_closed=True), files)

    def test_partial_load_without_sweep_is_allowed(self):
        args = self.args(sweep_closed=False, regions="서울", category="I202")

        load_stores.validate_sweep_scope(args, self.files(["서울"]))

    def test_source_version_must_match_file_version(self):
        with self.assertRaisesRegex(SystemExit, "파일 버전"):
            load_stores.resolve_version(self.files(), "202603")

    def test_detected_source_version_is_used(self):
        self.assertEqual("202606", load_stores.resolve_version(self.files(), None))

    def test_unexpected_filename_is_rejected(self):
        with self.assertRaisesRegex(SystemExit, "예상한 CSV 파일명"):
            load_stores.resolve_version([Path("stores.csv")], None)

    @staticmethod
    def args(**overrides) -> argparse.Namespace:
        values = {
            "sweep_closed": False,
            "regions": None,
            "category": load_stores.MIDDLE_CATEGORY,
        }
        values.update(overrides)
        return argparse.Namespace(**values)

    @staticmethod
    def files(regions=None) -> list[Path]:
        selected = regions or load_stores.FULL_DATASET_REGIONS
        return [Path(f"상가_정보_{region}_202606.csv") for region in sorted(selected)]


if __name__ == "__main__":
    unittest.main()
