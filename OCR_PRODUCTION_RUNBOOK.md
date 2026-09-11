# OCR 운영 배포·장애 대응 런북

## 최초 배포 순서

1. `infra/terraform`에서 `terraform plan`을 검토한 뒤 `terraform apply`한다.
   - ECS task role의 `s3:GetObjectVersion` 권한
   - GitHub Actions용 DynamoDB 배포 락과 IAM 권한
   - S3 버전 및 수명주기 정책이 먼저 준비되어야 한다.
2. AI 저장소의 `Deploy (prod)`를 실행한다.
   - `storage_key + version_id + expected_etag` 기반 S3 IAM 조회를 지원하는 AI가 먼저 올라가야 한다.
3. backend 저장소의 `Deploy (prod)`를 실행한다.
   - 운영에서는 Presigned GET fallback을 끄므로 구버전 AI보다 먼저 배포하면 OCR 요청이 실패한다.
4. 실제 메뉴판 한 장으로 업로드 → 스캔 → 결과 폴링까지 smoke test한다.

최초 배포 이후에는 두 저장소 워크플로가 DynamoDB 락으로 ECS 태스크 정의 갱신을 직렬화한다. 각 워크플로는 패밀리의 최신 리비전이 아니라 ECS 서비스가 실제 사용 중인 리비전에서 시작하므로 반대쪽 컨테이너 이미지가 되돌아가지 않는다.

## 프론트 업로드 계약

`POST /api/v1/uploads/sas` 응답의 `uploadHeaders`를 임의로 재구성하지 말고 그대로 Presigned PUT 요청에 포함한다.

```javascript
await fetch(ticket.uploadUrl, {
  method: "PUT",
  headers: ticket.uploadHeaders,
  body: imageFile,
});
```

현재 필수 헤더는 다음과 같다.

- `Content-Type`: 티켓 발급 요청에서 검증한 이미지 MIME 타입
- `If-None-Match: *`: 같은 URL을 재사용해 객체를 덮어쓰지 못하게 하는 조건

업로드 성공 후 `storageKey`로 `POST /api/v1/scans`를 호출한다. 같은 사용자와 `storageKey`의 중복 요청은 같은 `scanId`를 반환한다.

## 상태 및 재시도

- `processing`: 클라이언트가 짧은 간격으로 폴링한다.
- `completed`: 메뉴 결과를 표시한다.
- `needs_retake`: `retakeReasons`를 이용해 재촬영을 안내한다.
- `failed`: `failureCode`로 안내 문구와 재시도 가능 여부를 결정한다.
- 스캔 시작이 HTTP 503 `SCAN_CAPACITY_EXCEEDED`이면 `Retry-After` 이후 같은 `storageKey`로 다시 요청할 수 있다. 거절된 세션은 서버가 제거한다.
- 처리 중 실패한 세션을 자동으로 다시 실행하지 않는다. 외부 OCR의 성공 여부가 불명확한 타임아웃에서 자동 재시도하면 중복 과금될 수 있기 때문이다. 사용자가 명시적으로 재촬영·재업로드하도록 안내한다.

## 확인할 로그

- `OCR completed`: 백엔드/AI 처리 시간, OCR 호출 횟수, 전처리 선택, 이미지 조회 경로, AI 큐 대기 시간
- `AI stage completed`: rule engine/result 단계별 지연
- `Scan failed`: `failureCode`와 전체 처리 시간
- `Recovered ... stale scan sessions`: 서버 재시작 등으로 유실된 인메모리 작업 회수

## 롤백

ECS 서비스의 이전 정상 **태스크 정의 리비전 전체**로 롤백한다. backend와 AI는 같은 태스크 정의에 있으므로 컨테이너 하나의 태그만 수동 교체하면 API 계약이 어긋날 수 있다. DB 마이그레이션 V2는 새 컬럼과 인덱스를 추가하는 방식이라 구버전 애플리케이션과 호환된다.
