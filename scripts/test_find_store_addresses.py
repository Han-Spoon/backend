import tempfile
import unittest
from pathlib import Path

from scripts import find_store_addresses as fsa


MIDDLE_NAMES = {"I201": "한식", "I203": "일식", "I210": "기타 간이"}


def sbiz_row(no, name, address, middle="I201", small="I20101", branch="", floor="1"):
    return {"상가업소번호": no, "상호명": name, "지점명": branch, "층정보": floor,
            "상권업종중분류코드": middle, "상권업종중분류명": MIDDLE_NAMES[middle],
            "상권업종소분류코드": small, "도로명주소": address}


class FindStoreAddressesTest(unittest.TestCase):

    def test_kakao_and_sbiz_addresses_share_key(self):
        self.assertEqual(fsa.address_key("서울 용산구 한강대로21나길 40"),
                         fsa.address_key("서울특별시 용산구 한강대로21나길 40"))
        self.assertEqual(("용산구", "새창로", "213-12"), fsa.address_key("서울 용산구 새창로 213-12 1층"))
        self.assertEqual(("중구", "을지로", "30"), fsa.address_key("서울 중구 을지로 지하 30"))
        self.assertIsNone(fsa.address_key("서울 용산구 한강로3가 40-1"))

    def test_targets_skip_mapped_and_none(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "map.csv"
            path.write_text(
                "음식점 명,sbiz_store_no,매칭,후보\n"
                "가게A,MA1,exact,\n"
                "가게B,,none,\n"
                "가게C,,review,\n", encoding="utf-8")

            self.assertEqual(["가게C"], fsa.read_targets(path))

    def test_candidates_match_by_road_address(self):
        index = {fsa.address_key("서울특별시 용산구 한강대로 128"): [
            sbiz_row("MA1", "호보갈비", "서울특별시 용산구 한강대로 128"),
            sbiz_row("MA2", "호보스시", "서울특별시 용산구 한강대로 128", middle="I203", small="I20301"),
            sbiz_row("MA3", "호보김밥", "서울특별시 용산구 한강대로 128", middle="I210", small="I21007"),
        ]}
        search = lambda q: [
            {"place_name": "호보갈비 용산본점", "category_name": "음식점 > 한식",
             "road_address_name": "서울 용산구 한강대로 128"},
            {"place_name": "호보갈비 강남점", "road_address_name": "서울 강남구 테헤란로 1"},
        ]

        rows = fsa.find_candidates(["호보갈비 용산본점"], search, index, {"용산구"})

        self.assertEqual(1, len(rows))
        self.assertEqual("서울 용산구 한강대로 128", rows[0][3])
        self.assertIn("MA1 호보갈비 [한식] 1층", rows[0][4])
        self.assertIn("MA2 호보스시 [일식] 1층 (DB 없음: 적재 범위 밖 업종)", rows[0][4])
        self.assertIn("MA3 호보김밥 [기타 간이] 1층", rows[0][4])
        self.assertNotIn("MA3 호보김밥 [기타 간이] 1층 (DB 없음", rows[0][4])

    def test_queries_fall_back_to_brand_then_district(self):
        self.assertEqual(["호보갈비 용산본점", "호보갈비", "호보갈비 마포구", "호보갈비 용산구"],
                         fsa.queries("호보갈비 용산본점", {"용산구", "마포구"}))
        self.assertEqual(["전주집", "전주집 중구"], fsa.queries("전주집", {"중구"}))

    def test_search_stops_at_first_query_with_district_result(self):
        calls = []

        def search(query):
            calls.append(query)
            if query == "호보갈비":
                return [{"place_name": "호보갈비", "road_address_name": "서울 용산구 한강대로 128"}]
            return [{"place_name": "호보갈비 강남점", "road_address_name": "서울 강남구 테헤란로 1"}]

        rows = fsa.find_candidates(["호보갈비 용산본점"], search, {}, {"용산구"})

        self.assertEqual(["호보갈비 용산본점", "호보갈비"], calls)
        self.assertEqual("서울 용산구 한강대로 128", rows[0][3])

    def test_same_district_name_outside_seoul_is_excluded(self):
        search = lambda q: [{"place_name": "뜯고기 대구종로점", "road_address_name": "대구 중구 종로 48"}]

        rows = fsa.find_candidates(["뜯고기"], search, {}, {"중구"})

        self.assertEqual("카카오 검색 결과 없음", rows[0][4])

    def test_no_result_and_no_store_at_address_are_reported(self):
        empty = fsa.find_candidates(["가게A"], lambda q: [], {}, {"용산구"})
        missing = fsa.find_candidates(
            ["가게B"], lambda q: [{"place_name": "가게B", "road_address_name": "서울 용산구 청파로 1"}],
            {}, {"용산구"})

        self.assertEqual("카카오 검색 결과 없음", empty[0][4])
        self.assertEqual("같은 주소 음식점 없음", missing[0][4])


if __name__ == "__main__":
    unittest.main()
