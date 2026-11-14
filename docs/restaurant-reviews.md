# 식당 후기 API

식당 후기는 스캔 기록과 별도 `restaurant_reviews` 리소스다. `store_id`가 필수이며 `scan_session_id`는 선택 사항이다. 스캔을 연결하면 본인의 완료된 스캔이 같은 식당에 연결되어 있는지 서버가 확인한다. 후기에는 선택한 메뉴별 이름, 현재 식단 항목에 대한 `yes`/`no`/`unknown` 응답, 최대 500자 메모를 저장한다. 한 후기에서 메뉴 이름은 중복할 수 없다.

- `GET /api/v1/stores/{storeId}/reviews`: 식당 후기 최신순. 작성자 ID를 노출하지 않고 로그인한 사용자에게만 `mine`을 표시한다.
- `POST /api/v1/stores/{storeId}/reviews`: 식당 후기 작성.
- `GET /api/v1/reviews/me`: 본인 후기 목록.
- `PATCH /api/v1/reviews/{reviewId}`, `DELETE /api/v1/reviews/{reviewId}`: 본인 후기만 가능.
- `POST /api/v1/reviews/{reviewId}/reports`: 다른 사람의 후기 신고. 사유는 `INAPPROPRIATE`, `FALSE_INFORMATION`, `AD_SPAM`, `OTHER`. 신고 ID·후기 ID·신고자 ID·사유·시각을 별도 저장한다. 같은 신고자의 반복 요청은 추가 행을 만들지 않는다.

신고 접수만으로 후기를 숨기거나 삭제하지 않는다. 관리자 검토 UI는 이 범위에 없다. 가게 후보 응답의 `reviewCount`는 현재 저장된 후기 행 수다. 위치 조회는 50m 구간 안에서 후기 수를 보조 정렬한다.
