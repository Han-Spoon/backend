import io
import tempfile
import unittest
from pathlib import Path

from scripts import seed_demo_reviews

HEADER = "store_no,review_no,menu_name,comment,comment_locale,answers,days_ago\n"


class SeedDemoReviewsTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def write(self, body: str) -> Path:
        path = Path(self.tmp.name) / "reviews.csv"
        path.write_text(HEADER + body, encoding="utf-8")
        return path

    def test_rows_keep_line_order_and_normalize_answers(self):
        path = self.write(
            "MA1,1,김치찌개,Spicy but good,en, allergy:pork = present ; preference:no-spicy=present,2.5\n"
            "MA1,1,공기밥,,,allergy:wheat=unknown,2.5\n"
            "MA1,2,김치찌개,맛있어요,ko,,1\n")

        rows = seed_demo_reviews.read_reviews(path)

        self.assertEqual(
            [["MA1", 1, 2, "김치찌개", "Spicy but good", "en", "allergy:pork=present;preference:no-spicy=present", 2.5],
             ["MA1", 1, 3, "공기밥", "", "", "allergy:wheat=unknown", 2.5],
             ["MA1", 2, 4, "김치찌개", "맛있어요", "ko", "", 1.0]],
            rows)

    def test_absent_answer_is_rejected(self):
        path = self.write("MA1,1,김치찌개,,,allergy:pork=absent,1\n")

        with self.assertRaisesRegex(SystemExit, "present·unknown 만"):
            seed_demo_reviews.read_reviews(path)

    def test_unknown_diet_code_is_rejected(self):
        path = self.write("MA1,1,김치찌개,,,allergy:alcohol=present,1\n")

        with self.assertRaisesRegex(SystemExit, "식단 항목 오류"):
            seed_demo_reviews.read_reviews(path)

    def test_comment_without_locale_is_rejected(self):
        path = self.write("MA1,1,김치찌개,맛있어요,,,1\n")

        with self.assertRaisesRegex(SystemExit, "함께"):
            seed_demo_reviews.read_reviews(path)

    def test_same_review_with_different_age_is_rejected(self):
        path = self.write("MA1,1,김치찌개,,,allergy:pork=present,1\nMA1,1,공기밥,,,allergy:wheat=unknown,2\n")

        with self.assertRaisesRegex(SystemExit, "days_ago 다름"):
            seed_demo_reviews.read_reviews(path)

    def test_duplicate_menu_in_review_is_rejected(self):
        path = self.write("MA1,1,김치찌개,,,allergy:pork=present,1\nMA1,1,김치찌개,좋아요,ko,,1\n")

        with self.assertRaisesRegex(SystemExit, "메뉴 중복"):
            seed_demo_reviews.read_reviews(path)

    def test_empty_line_content_is_rejected(self):
        path = self.write("MA1,1,김치찌개,,,,1\n")

        with self.assertRaisesRegex(SystemExit, "둘 다 빈칸"):
            seed_demo_reviews.read_reviews(path)

    def test_emit_replaces_demo_reviews_and_commits(self):
        out = io.StringIO()
        seed_demo_reviews.emit(out, [["MA1", 1, 2, "김치찌개", "맛있어요", "ko", "", 1.0]], dry_run=False)
        sql = out.getvalue()

        self.assertIn(f"DELETE FROM meal_reviews WHERE user_id = '{seed_demo_reviews.DEMO_USER_ID}'", sql)
        self.assertIn("MA1,1,2,김치찌개,맛있어요,ko,,1.0\n\\.", sql)
        self.assertNotIn("DELETE FROM users", sql)
        self.assertTrue(sql.endswith("COMMIT;\n"))

    def test_emit_delete_mode_removes_demo_user(self):
        out = io.StringIO()
        seed_demo_reviews.emit(out, None, dry_run=True)
        sql = out.getvalue()

        self.assertIn(f"DELETE FROM users WHERE id = '{seed_demo_reviews.DEMO_USER_ID}'", sql)
        self.assertNotIn("INSERT", sql)
        self.assertTrue(sql.endswith("ROLLBACK;\n"))


if __name__ == "__main__":
    unittest.main()
