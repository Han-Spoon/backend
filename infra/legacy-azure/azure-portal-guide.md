# Azure Portal(GUI) 인프라 세팅 가이드 (Phase ②)

`azure-setup.sh`와 동일한 리소스를 **Azure Portal 웹 화면**에서 직접 만드는 단계별 가이드입니다.
- 고정 RG: **`smu-team1` (Korea Central)** — 새로 만들지 말고 선택만.
- 리전 종속 리소스는 전부 **Korea Central**.
- DB는 **Private Endpoint + VNet**(공개 비활성).

> 공통: 화면 상단 검색창에 리소스 종류를 입력 → "만들기(Create)"로 진행. 모든 리소스의 **Resource group = smu-team1**, **Region = Korea Central** 선택.
> ⚠️ 공유 구독이라 일부 단계(역할 할당, 전역 고유 이름)에서 권한/충돌 이슈가 날 수 있습니다(각 단계 주의 참고).

---

## 0. 로그인 & 사전 확인
1. https://portal.azure.com 접속, 로그인.
2. 상단 검색창에 **"Resource groups"** → `smu-team1` 클릭 → 존재/접근 확인(리전 Korea Central).
3. 우측 상단 구독이 `smu-team1`이 속한 구독인지 확인(Directory/Subscription 전환 필요 시 상단 계정 메뉴).

---

## 1. 가상 네트워크 (VNet + 서브넷 2개)
검색창 **"Virtual networks"** → **만들기**.
- **기본 사항**: 구독 / RG `smu-team1` / 이름 `vnet-hanspoon-prod` / 지역 `Korea Central`.
- **IP 주소** 탭:
  - IPv4 주소 공간: `10.0.0.0/16`
  - 기본 서브넷 편집/추가로 **2개** 구성:
    - 서브넷 ①: 이름 `snet-aca`, 범위 `10.0.0.0/23`,
      **서브넷 위임(Subnet delegation)** = `Microsoft.App/environments` 선택
    - 서브넷 ②: 이름 `snet-pe`, 범위 `10.0.2.0/24` (위임 없음)
- **검토 + 만들기**.

> `snet-aca`는 ACA 전용이라 `/23` 이상 + `Microsoft.App/environments` 위임 필수. `snet-pe`는 DB Private Endpoint용.

---

## 2. Log Analytics 작업영역
검색창 **"Log Analytics workspaces"** → **만들기**.
- RG `smu-team1` / 이름 `log-hanspoon-prod` / 지역 `Korea Central`.
- **검토 + 만들기**.

---

## 3. Container Registry (ACR)
리소스 만들기(Marketplace) 검색 시 offer 이름은 **"Container Registry"(단수)**. (상단 서비스 검색창에서는 **"Container registries"(복수)** 로 표시 — 둘 다 같은 ACR.)
**만들기** 진행:
- RG `smu-team1` / 레지스트리 이름 `acrhanspoonprod`(전역 고유, 영숫자) / 지역 `Korea Central` / SKU **Basic**.
- **검토 + 만들기**.

> 이름이 이미 점유됐다면 `acrhanspoonprod2` 등으로 변경.

---

## 4. PostgreSQL Flexible Server
검색창 **"Azure Database for PostgreSQL flexible servers"** → **만들기** → "유연한 서버".
- **기본 사항**:
  - RG `smu-team1` / 서버 이름 `psql-hanspoon-prod` / 지역 `Korea Central` / PostgreSQL 버전 **16**
  - 워크로드 유형: **개발(Development)** (B1ms로 설정됨)
  - 관리자 사용자 이름 `hanspoon_app` / 비밀번호 설정(메모)
- **컴퓨팅+스토리지**: Burstable **Standard_B1ms**, 스토리지 32GB(기본).
- **네트워킹**: 연결 방법 = **퍼블릭 액세스** 선택하되 **방화벽 규칙을 추가하지 않음**(아무 IP도 허용 안 함). (Private Endpoint는 생성 후 5-2에서 추가)
  - "Azure 서비스 액세스 허용" **체크 해제**.
- **검토 + 만들기**.

### 4-2. pgvector 확장 허용
서버 생성 후 → 좌측 **설정 → 서버 매개 변수(Server parameters)** → 검색 `azure.extensions` → 목록에서 **VECTOR** 체크 → **저장**.

### 4-3. Private Endpoint 추가 (DB를 사설망으로)
검색창 **"Private endpoints"** → **만들기**.
- **기본 사항**: RG `smu-team1` / 이름 `pe-psql-hanspoon-prod` / 지역 `Korea Central`.
- **리소스**: 연결 방법 "내 디렉터리의 Azure 리소스" → 리소스 종류 `Microsoft.DBforPostgreSQL/flexibleServers` → 리소스 `psql-hanspoon-prod` → 대상 하위 리소스 **postgresqlServer**.
- **가상 네트워크**: VNet `vnet-hanspoon-prod` / 서브넷 **`snet-pe`**.
- **DNS** 탭: **프라이빗 DNS 영역과 통합 = 예** →
  자동으로 `privatelink.postgres.database.azure.com` 영역 생성 + VNet 링크 + A레코드 등록 ✅
- **검토 + 만들기**.

### 4-4. 퍼블릭 액세스 비활성(선택, 보안 강화)
PG 서버 → **설정 → 네트워킹** → "퍼블릭 네트워크 액세스" 사용 안 함으로 변경 → 저장. (Private Endpoint로만 접근)

> 이 4-3의 "DNS 통합=예"가 스크립트의 private-dns-zone/link/zone-group 3단계를 한 번에 처리합니다.

---

## 5. Key Vault + 시크릿
검색창 **"Key vaults"** → **만들기**.
- **기본 사항**: RG `smu-team1` / 이름 `kv-hanspoon-prod`(전역 고유) / 지역 `Korea Central` / 가격 **표준(Standard)**.
- **액세스 구성** 탭: 권한 모델 = **Azure 역할 기반 액세스 제어(RBAC)**.
- **검토 + 만들기**.

### 5-2. 본인에게 시크릿 쓰기 권한 부여
KV → **액세스 제어(IAM)** → **추가 → 역할 할당 추가** → 역할 **Key Vault Secrets Officer** → 멤버 = 본인 계정 → 검토 + 할당.

> ⚠️ **역할 할당이 "View"만 되고 할당이 안 되면 = 권한 없음**(Contributor만 있고 Owner/User Access Administrator 없음). 공유 구독에서 흔함.
>
> **우회(권장): Key Vault를 액세스 정책 모드로** — 역할 할당 권한 없이 Contributor만으로 가능.
> 1. (5에서 RBAC로 만들었다면) KV → **설정 → 액세스 구성** → 권한 모델 **"Vault access policy"** 로 변경 → 저장. (또는 처음부터 이 모드로 생성)
> 2. KV → **액세스 정책 → 만들기** → 비밀 권한 **Get, List, Set, Delete** → 보안 주체 = 본인 → 만들기.
> 3. → 5-3 시크릿 등록 가능. 앱용은 8-3에서 동일하게 **Get, List** 정책을 매니지드 ID에 부여.
>
> 또는 구독 관리자에게 `User Access Administrator`(또는 Owner) 부여를 요청.

### 5-3. 시크릿 등록
KV → **개체 → 비밀(Secrets)** → **생성/가져오기**로 아래 3개 등록(이름 정확히):
- `kv-secret-jwt-signing-key` = (HS256 키, 32바이트+)
- `kv-secret-db-password` = (4단계 DB 비밀번호)
- `kv-secret-azure-openai-api-key` = (Azure OpenAI 키; 없으면 나중에)

---

## 6. Storage 계정 (Blob, 메뉴 이미지)
검색창 **"Storage accounts"** → **만들기**.
- **기본 사항**:
  - RG `smu-team1` / 이름 `sthanspoonprod`(전역 고유, 소문자+숫자) / 지역 `Korea Central`
  - **Primary service(기본 서비스) = `Azure Blob Storage`** ← 메뉴 이미지 저장용. (Data Lake Gen2/Files는 선택하지 말 것 — Gen2는 계층형 네임스페이스가 켜져 불필요)
  - 성능 **표준(Standard)** / 중복(Redundancy) **LRS**
- **검토 + 만들기**.

> 참고: Primary service는 "주 용도" 힌트일 뿐이며, StorageV2 계정은 어차피 Blob 컨테이너를 만들어 사용합니다. 용도 명확화를 위해 Blob Storage 선택.

### 6-2. 컨테이너 생성
**만든 스토리지 계정(`sthanspoonprod`)을 클릭해 진입** → 계정 **왼쪽 메뉴**의 **데이터 스토리지(Data storage) → 컨테이너(Containers)** → **+ 컨테이너** → 이름 `menu-images`, 액세스 수준 **프라이빗**.
> "데이터 스토리지"는 스토리지 계정 **내부** 왼쪽 메뉴 섹션입니다(계정 목록 화면엔 없음). 안 보이면 왼쪽 메뉴 검색칸에 `Containers` 입력.

### 6-3. CORS 설정 (프론트 SAS 직접 업로드용)
스토리지 → **설정 → 리소스 공유(CORS)** → **Blob service** 탭에 규칙 추가:
- 허용된 원본(Allowed origins): 실제 프론트 출처 (예: `http://localhost:5173` 또는 `https://<vercel-url>`)
- 허용된 메서드: **PUT, GET**
- 허용된 헤더 / 노출된 헤더: `*`
- 최대 보존 기간: `3600` → **저장**.

---

## 7~8. Container App + 환경 (환경은 앱 생성 중 인라인으로 만듦)

> Container Apps **환경**은 보통 독립 메뉴가 없고, **Container App 만들기 위저드 안에서 "새로 만들기"** 로 생성합니다. 환경의 **VNet 지정은 이 팝업의 "네트워킹" 탭**에서 하며 **생성 후 변경 불가**입니다.

검색창 **"Container Apps"** → **만들기**.

### 7. 기본 사항 + 환경 생성(인라인)
- RG `smu-team1` / 앱 이름 `ca-hanspoon-api` / 지역 `Korea Central`.
- **"Container Apps 환경"** 항목 → **새로 만들기(Create new)** 클릭 → 팝업 탭 작성:
  - **기본 사항**: 환경 이름 `cae-hanspoon-prod`
  - **모니터링**: Log Analytics = `log-hanspoon-prod`
  - **네트워킹**:
    - **고유한 가상 네트워크 사용 = 예** → VNet `vnet-hanspoon-prod` / 인프라 서브넷 **`snet-aca`** ← ⚠️ 필수, 생성 후 변경 불가
    - **Virtual IP = External(외부)** ← 프론트가 인터넷에서 호출해야 하므로. (Internal로 하면 VNet 내부에서만 접근 → 안 됨. VNet 통합 + External은 양립 가능)
    - **Infrastructure resource group**: 비워두면 Azure가 자동 이름으로 생성(권장). 직접 지정하려면 이름만 입력(예: `rg-hanspoon-aca-infra`). `smu-team1`과 별개의 ACA 관리용 RG. ⚠️ 공유 구독에서 새 RG 생성 권한 없으면 실패 가능 → 관리자 문의
  - **만들기**(팝업 닫힘, 환경이 선택됨)
- (방법 B: 상단 검색창 **"Container Apps Environments"** 서비스 → +만들기 가 보이면 거기서 먼저 만들어도 됨)

### 8. 컨테이너 + 인그레스
- **컨테이너** 탭: **"빠른 시작 이미지 사용(Use quickstart image)" 체크** → 기본값 **"Simple hello world container"**(`mcr.microsoft.com/k8se/quickstart`) 선택.
  - ⚠️ **ACR/Docker Hub 지정하지 말 것** — 우리 백엔드 이미지는 아직 없음(③ CD에서 빌드·푸시 후 `az containerapp update --image`로 교체).
- **인그레스(Ingress)** 탭: **사용** / 트래픽 허용 **어디서나(External)** / 대상 포트 **8080**.
  - ⚠️ **포트 주의**: 퀵스타트 이미지는 80 포트라, 8080 인그레스에선 **임시 앱이 응답하지 않음**(정상 — placeholder). CD로 실제 Spring Boot 이미지(8080) 배포 후 정상 동작. (지금 인그레스 동작을 확인하고 싶으면 임시로 포트 80 → CD 때 8080으로 변경)
- **검토 + 만들기**.

### 8-2. 관리 ID(매니지드 ID) 켜기
앱 → **설정 → ID(Identity)** → **시스템 할당** = 켬(On) → 저장. (주체 ID 생성됨)

### 8-3. ID에 권한 부여
- **Key Vault** → IAM → 역할 할당 추가 → **Key Vault Secrets User** → 멤버 "관리 ID" → `ca-hanspoon-api` 선택.
- **ACR** → IAM → 역할 할당 추가 → **AcrPull** → 멤버 "관리 ID" → `ca-hanspoon-api`.

> ⚠️ **역할 할당 권한이 없을 때(공유 구독) 우회:**
> - **Key Vault**: 액세스 정책 모드(5-2 우회)에서 → 액세스 정책 만들기 → 비밀 권한 **Get, List** → 보안 주체 `ca-hanspoon-api`(매니지드 ID).
> - **ACR**(액세스 정책 없음 → admin user로 우회): ACR → **설정 → 액세스 키** → **관리자 사용자(Admin user)** 켜기 → username/password 메모 → Container App에 **레지스트리 자격증명 등록**(매니지드 ID `AcrPull` 대신).
>   - CLI:
>     ```bash
>     az acr update -n acrhanspoonprod --admin-enabled true
>     ACR_USER=$(az acr credential show -n acrhanspoonprod --query username -o tsv)
>     ACR_PWD=$(az acr credential show -n acrhanspoonprod --query 'passwords[0].value' -o tsv)
>     az containerapp registry set -g smu-team1 -n ca-hanspoon-api \
>       --server acrhanspoonprod.azurecr.io --username "$ACR_USER" --password "$ACR_PWD"
>     ```
>   - ⚠️ 이 **registry set(레지스트리 자격증명 등록)** 을 안 하면 배포 시 `UNAUTHORIZED: authentication required`로 이미지 pull 실패.

### 8-4. 환경 변수 주입
1. 앱 → **애플리케이션 → 컨테이너(Containers)** → 상단 **편집 및 배포(Edit and deploy)**.
2. "새 리비전 만들기" 페이지의 **컨테이너 이미지** 표에서 **컨테이너 이름(파란 링크)을 클릭** → 오른쪽 **"컨테이너 편집"** 패널이 열림. ← 이 클릭이 핵심
3. 패널을 아래로 스크롤 → **환경 변수(Environment variables)** 섹션 → **추가(Add)** 로 한 줄씩 입력(원본=**수동 입력/Manual entry**, 이름+값).
4. **저장** → 리비전 페이지 맨 아래 **만들기(Create)** 로 새 리비전 배포.

추가할 키(`application-prod.yml`이 기대, 전부 평문 수동 입력):

| 이름 | 값 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `AZURE_KEYVAULT_ENDPOINT` | `https://kv-hanspoon-prod.vault.azure.net/` |
| `DB_PRIVATE_ENDPOINT_URL` | `jdbc:postgresql://psql-hanspoon-prod.privatelink.postgres.database.azure.com:5432/hanspoon?sslmode=require` |
| `DB_USERNAME` | `hanspoon_app` |
| `GOOGLE_CLIENT_ID` | (Google OAuth Client ID) |
| `JWT_ISSUER` | `https://api.han-spoon.com` (식별자, 일관되면 OK) |
| `AZURE_AI_PRIVATE_ENDPOINT` | (실제 Azure OpenAI 엔드포인트, 없으면 생략) |
| `CORS_ALLOWED_ORIGINS` | 실제 프론트 출처(콤마 구분) |

→ **만들기(새 리비전 생성)**.

### 8-5. 헬스 프로브(선택 — 실제 이미지 배포 후 권장)
컨테이너 편집 → **상태 프로브(Health probes)**. 채울 항목은 **Transport/Path/Port**뿐 — **HTTP Method 입력칸은 없음**(ACA HTTP 프로브는 GET 고정).
- Liveness: **Transport=HTTP** / **Path=`/actuator/health/liveness`** / **Port=8080**
- Readiness: **Transport=HTTP** / **Path=`/actuator/health/readiness`** / **Port=8080**
> ⚠️ 퀵스타트 placeholder(포트 80) 상태에선 8080 프로브가 실패하니, **CD로 실제 백엔드 이미지를 올린 뒤** 설정/검증 권장(지금은 생략 가능).

### 8-6. FQDN 확인
앱 **개요(Overview)** → "애플리케이션 URL"이 외부 접속 주소. (실제 백엔드 배포 후 `…/actuator/health` = UP 확인)

---

## 9. 사용자 지정 도메인 + 무료 관리형 인증서 (`api.han-spoon.com`)
ACA 임의 FQDN(`ca-hanspoon-api.<rand>.koreacentral.azurecontainerapps.io`) 대신 고정 공개 주소를 붙인다.
방식: **Cloudflare는 DNS only(회색 구름)** + **Azure 무료 관리형 인증서**(자동 발급·갱신). 서브도메인이라 CNAME 검증.

> ⚠️ **Cloudflare 프록시(주황 구름)는 반드시 OFF.** 프록시를 켜면 CNAME이 Cloudflare를 경유하여
> ACA 생성 FQDN을 **직접** 가리키지 못해 관리형 인증서 발급·갱신이 실패한다(가장 흔한 함정).

### 9-0. 선행(도메인/존 준비)
- `han-spoon.com` 등록(미등록 시) → **Cloudflare에 존 추가** → 등록기관에서 NS 위임 → 존 *Active*.
- (선택) `han-spoon.com`에 **CAA 레코드가 있으면** `0 issue digicert.com` 추가. 신규 존이면 보통 불필요.

### 9-1. ACA 요건 확인
인그레스가 **External + HTTP, 포트 8080** 인지 확인(8-2/8-4에서 설정됨). 비공개면 관리형 인증서 발급 불가.

### 9-2. DNS 레코드 값 산출 (CLI)
```bash
RG=smu-team1; APP=ca-hanspoon-api
# CNAME 값 = ACA FQDN
az containerapp show -g $RG -n $APP -o tsv --query "properties.configuration.ingress.fqdn"
# asuid TXT 값 = 도메인 검증 코드
az containerapp show -g $RG -n $APP -o tsv --query "properties.customDomainVerificationId"
```
(헬퍼: `./infra/custom-domain.sh show` — 위 값을 레코드 형태로 정리해 출력)

### 9-3. Cloudflare 레코드 추가 (대시보드, 직접)
`han-spoon.com` 존 → **DNS → Records → Add record**. 둘 다 **Proxy status = DNS only**, TTL Auto:

| Type  | Name        | Content                          |
| ----- | ----------- | -------------------------------- |
| CNAME | `api`       | 9-2의 ACA FQDN                   |
| TXT   | `asuid.api` | 9-2의 customDomainVerificationId |

전파 확인: `dig +short api.han-spoon.com CNAME` / `dig +short asuid.api.han-spoon.com TXT`.

### 9-4. 호스트네임 추가 + 인증서 바인딩 (CLI)
```bash
ENV=cae-hanspoon-prod; DOMAIN=api.han-spoon.com
az containerapp hostname add  -g $RG -n $APP --hostname $DOMAIN
az containerapp hostname bind -g $RG -n $APP --environment $ENV \
  --hostname $DOMAIN --validation-method CNAME      # 발급에 수 분 소요
```
(헬퍼: `./infra/custom-domain.sh bind`)
Portal 대안: 앱 → **설정 → 사용자 지정 도메인 → 사용자 지정 도메인 추가** → 관리형 인증서 → CNAME 선택.

### 9-5. 검증
```bash
az containerapp hostname list -g $RG -n $APP -o table      # api.han-spoon.com = Secured
curl -sS https://api.han-spoon.com/actuator/health         # {"status":"UP"}
```
(헬퍼: `./infra/custom-domain.sh verify`)

### 9-6. (의사결정 2026-06-03) MVP 단계 — 도메인 보류, 기본 FQDN 사용
비용 절감을 위해 **MVP 시연까지는 커스텀 도메인을 사지 않고 ACA 기본 FQDN으로 진행**한다.
- 기본 FQDN(`ca-hanspoon-api.<rand>.koreacentral.azurecontainerapps.io`)은 이미 **HTTPS + 유효 인증서**
  (Microsoft 발급, 자동 갱신) → 보안 손실 없음.
- 우리 구성은 Cloudflare **DNS only**라 커스텀 도메인을 붙여도 WAF·오리진IP 숨김 등 추가 보안은 없음 → 보안 득실 없음.
- 백엔드는 API라 URL이 최종 사용자에게 노출되지 않음(사용자가 보는 건 프론트 도메인) → 완성도 영향 미미.
- ⚠️ 기본 FQDN은 **ACA 환경 재생성 시 변경**(stop/start·scale-0·재배포로는 불변). 프론트 설정 및 Google OAuth 허용 출처에 등록해 둘 것.
- 적용 시점: **실서비스 출시 직전**. 위 9-0~9-5(또는 `./infra/custom-domain.sh show|bind|verify`)로 ~10분 내 전환.

---

## 순서 요약
1 VNet → 2 Log Analytics → 3 ACR → 4 PostgreSQL(+pgvector +Private Endpoint) → 5 Key Vault(+시크릿) → 6 Storage(+CORS) → 7 ACA 환경 → 8 Container App(+ID·권한·env)

## 검증
- 앱 개요 URL 접속(임시 이미지면 퀵스타트 페이지). ③ CD로 실제 이미지 배포 후 `/actuator/health` = `{"status":"UP"}`.
- 앱 **로그 스트림**(앱 → 모니터링 → 로그 스트림)에서 Flyway 마이그레이션 성공 + KV 시크릿 로드 확인.

## 자주 막히는 곳
- **역할 할당 권한 없음**(공유 구독): 5-2 / 8-3 → 관리자 요청 or KV 액세스 정책 모드.
- **전역 고유 이름 충돌**: ACR/Storage/KV → 접미사 추가(+`application-prod.yml`도 맞춤).
- **ACA 환경 VNet은 생성 시 고정**: 잘못 만들면 환경 재생성.
- **DB 연결 실패**: Private Endpoint의 "DNS 통합=예"가 됐는지, ACA 환경이 `snet-aca`(같은 VNet)인지 확인.
