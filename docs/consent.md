# 약관 원문과 동의 저장

`src/main/resources/legal/2026-11-14.json`이 운영 API의 단일 원문이다. `GET /api/v1/consents/documents`는 로그인 전에도 원문, 시행일, 버전과 필수 여부를 반환한다. 프런트 데모 사본 `src/app/demo/legalDocuments.json`과 내용이 동일해야 한다.

로그인 후 `GET /api/v1/consents/me`는 현재 문서 버전에 대한 각 동의 결정과 `hasRequiredConsents`를 반환한다. `POST /api/v1/consents/me`는 아래 요청을 받고 각 문서의 동의 여부를 별도 이력으로 기록한다. 필수 3개가 모두 `true`가 아니거나 버전이 다르면 400을 반환한다. 최신 버전의 동의가 없는 사용자는 프로필 생성·수정과 스캔 시작을 할 수 없다.

```json
{
  "version": "2026-11-14",
  "decisions": {
    "service": true,
    "privacy": true,
    "sensitive": true,
    "marketing": false
  }
}
```

마케팅 동의는 항상 선택이며, 사용자가 다시 제출하면 새 결정 이력이 추가된다. 새 원문을 게시할 때는 파일 이름·본문 버전·서비스의 파일 경로를 함께 바꾸고, 기존 버전의 원문은 기록 증빙을 위해 보관한다.

`infra/terraform/storage.tf`는 스캔 원본을 업로드 후 90일에 만료시키고 이전 버전을 추가 1일 후 정리한다. `infra/terraform/compute.tf`는 CloudWatch 요청 로그를 7일 보관한다. 기록 삭제·회원 탈퇴는 DB 행만 즉시 삭제하며 S3 원본은 수명주기 만료일까지 남을 수 있다. 이 사실을 개인정보 원문에 반영했다. 배포 전 실제 저장소 설정, 운영자 명칭·연락처, 제3자 제공·국외 이전·처리 위탁, 만 14세 미만 이용 정책과 다국어 법률 번역을 검토해야 한다.
