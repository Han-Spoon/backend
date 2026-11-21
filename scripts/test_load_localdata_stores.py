import csv
import io
import tempfile
import unittest
from pathlib import Path

from scripts import load_localdata_stores as lls

HEADER = ["개방자치단체코드", "관리번호", "영업상태명", "사업장명", "업태구분명", "도로명주소", "좌표정보(X)", "좌표정보(Y)"]
OYOYOY = ["3020000", "3020000-101-2026-00318", "영업/정상", "오이오이오이 용산점", "한식",
          "서울특별시 용산구 한강대로15길 9, 103호 (한강로3가)", "196671.5", "447153.1"]
BAEKRANG = ["3020000", "3020000-101-2013-00377", "영업/정상", "백랑", "경양식",
            "서울특별시 용산구 한강대로15길 23, 삼정빌딩 1층 (한강로3가)", "196672.420900332", "447097.911760459"]


class LoadLocaldataStoresTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)

    def localdata(self, rows):
        path = self.dir / "localdata.csv"
        with path.open("w", encoding="cp949", newline="") as fh:
            w = csv.writer(fh)
            w.writerow(HEADER)
            w.writerows(rows)
        return path

    def store_map(self, numbers):
        path = self.dir / "map.csv"
        path.write_text("음식점 명,store_no\n" + "".join(f"가게{i},{n}\n" for i, n in enumerate(numbers)),
                        encoding="utf-8")
        return path

    def test_targets_only_localdata_numbers(self):
        path = self.store_map(["MA010120220800201168", "3020000-101-2026-00318", ""])

        self.assertEqual(["3020000-101-2026-00318"], lls.read_targets(path))

    def test_coordinates_match_public_store_data(self):
        # 백랑과 같은 건물(한강대로15길 23 1층) 김불시대 상가정보 좌표
        lat, lng = lls.tm_to_wgs84(196672.420900332, 447097.911760459)

        self.assertAlmostEqual(37.5261313, lat, delta=0.0001)
        self.assertAlmostEqual(126.9631765, lng, delta=0.0001)

    def test_address_keeps_road_and_first_floor(self):
        self.assertEqual(("서울특별시 중구 삼일대로12길 16", "1"),
                         lls.split_address("서울특별시 중구 삼일대로12길 16 (을지로2가, 1, 2층)"))
        self.assertEqual(("서울특별시 용산구 한강대로62길 52", "1"),
                         lls.split_address("서울특별시 용산구 한강대로62길 52 (한강로1가,(지상1층))"))
        self.assertEqual(("서울특별시 용산구 한강대로15길 9", ""),
                         lls.split_address("서울특별시 용산구 한강대로15길 9, 103호 (한강로3가)"))
        self.assertEqual(("서울특별시 용산구 한강대로53길 24", "1"),
                         lls.split_address("서울특별시 용산구 한강대로53길 24, 1-2층 (한강로2가)"))
        self.assertEqual(("서울특별시 용산구 새창로 120-2", "B1"),
                         lls.split_address("서울특별시 용산구 새창로 120-2, 지하1층 (용문동)"))

    def test_rows_map_business_type_to_category(self):
        rows = lls.read_stores(self.localdata([OYOYOY, BAEKRANG]),
                               ["3020000-101-2013-00377", "3020000-101-2026-00318"])

        self.assertEqual(["백랑", "I204", "서울특별시 용산구 한강대로15길 23", "1"], rows[0][1:5])
        self.assertEqual("I201", rows[1][2])

    def test_missing_or_closed_store_stops(self):
        with self.assertRaisesRegex(SystemExit, "없는 관리번호"):
            lls.read_stores(self.localdata([OYOYOY]), ["3020000-101-2013-00377"])

        closed = OYOYOY[:2] + ["폐업"] + OYOYOY[3:]
        with self.assertRaisesRegex(SystemExit, "영업 중 아님"):
            lls.read_stores(self.localdata([closed]), ["3020000-101-2026-00318"])

    def test_sql_upserts_by_management_number(self):
        buf = io.StringIO()
        lls.emit(buf, [["3020000-101-2026-00318", "오이오이오이 용산점", "I201", "주소", "", 37.5, 126.9]],
                 "202611", dry_run=True)
        sql = buf.getvalue()

        self.assertIn("ON CONFLICT (localdata_mgmt_no) DO UPDATE", sql)
        self.assertIn("VALUES ('localdata', '202611', 'running'", sql)
        self.assertIn("unknown store category", sql)
        self.assertIn("WHEN stores.verified_at IS NULL THEN EXCLUDED.name ELSE stores.name", sql)
        self.assertIn("stores.status = 'inactive' AND stores.verified_at IS NULL THEN 'active'", sql)
        self.assertTrue(sql.rstrip().endswith("ROLLBACK;"))


if __name__ == "__main__":
    unittest.main()
