-- V16 가게 메뉴 표시 순서. 수동 지정, 없으면 id 순
-- NULL 허용·기본값 없음: 테이블 재작성 없음

ALTER TABLE store_menus ADD COLUMN display_order INTEGER NULL;

COMMENT ON COLUMN store_menus.display_order IS '표시 순서 | 가게 상세 메뉴·대표 사진 정렬, 작은 값 먼저; NULL은 뒤(id 순)';
