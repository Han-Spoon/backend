import unittest
from pathlib import Path


class StoreDomainMigrationSyncTest(unittest.TestCase):

    def test_staging_ddl_matches_final_flyway_migration(self):
        project_root = Path(__file__).resolve().parents[1]
        staging = project_root / "scripts" / "store_domain.sql"
        migration = project_root / "src" / "main" / "resources" / "db" / "migration" / "V5__store_domain.sql"

        self.assertEqual(staging.read_text(encoding="utf-8"), migration.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
