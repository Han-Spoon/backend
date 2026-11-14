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

## 만 14세 미만 이용자

`POST /api/v1/auth/google`은 `ageGroup`을 필수로 받고, `UNDER_14`에는 `guardianCode`를 추가로 요구한다. 서버는 현재 약관 버전의 활성 보호자 승인 코드를 Google ID Token 검증보다 먼저 확인한다. 코드는 해시로 저장하고 최초 아동 계정에 연결되며 다른 계정에서 사용할 수 없다. 승인 만료·철회 시 필수 동의 상태가 무효가 되고 토큰 갱신, 프로필 저장, 새 스캔 시작을 차단한다. 아동의 직접 `POST /api/v1/consents/me` 요청도 거부하며 마케팅 동의는 항상 거부로 기록한다.

운영 절차: (1) 법정대리인이 `support@han-spoon.com`에 문의한다. (2) 운영자가 현재 서비스 약관, 일반 개인정보 및 민감정보 동의의 **전문과 버전**을 이메일로 보낸다. (3) 법정대리인의 명시적 동의 답장과 관계를 확인하고 안전한 증빙 관리 시스템에 보관한다. (4) `scripts/issue_guardian_approval.py --guardian-email ... --evidence-reference ... --document-version ... --verified-email-reply`로 코드와 SQL을 생성하고, 감사 가능한 데이터베이스 변경 경로에서 SQL을 실행한다. (5) 코드는 법정대리인에게만 전달한다. 발송·답장 확인·코드 등록은 자동화되지 않았다. 단순 이메일 링크 클릭만으로 승인 처리하지 않는다.

코드는 해당 문서 버전, 한 계정, 발급 후 365일에 묶인다. 법정대리인 철회 요청을 확인하면 `guardian_approvals.revoked_at`을 기록하고 세션을 폐기하며, 관련 정보 삭제 요청을 처리한다. 약관 버전이 바뀌거나 코드가 만료되면 새로운 동의 확인과 코드 발급이 필요하다. 이 운영 절차와 실제 연락처 수신 가능 여부를 검증하기 전에는 아동 가입 경로를 공개하지 않는다.
