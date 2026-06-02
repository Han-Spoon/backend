# Azure 인프라 세팅 (Phase ②)

`azure-setup.sh`는 Han-Spoon 백엔드 배포에 필요한 Azure 리소스를 **az CLI로 수동 프로비저닝**합니다.
DB는 **PostgreSQL Flexible Server + Private Endpoint + VNet**(공개 비활성) 구성입니다.

> ⚠️ **유료 리소스를 생성합니다.** 예산(30만원) 내 최소 SKU(B1ms / ACR Basic / LRS)로 설정돼 있지만, VNet·Private Endpoint·Private DNS는 추가 비용이 있습니다. 실행 전 비용을 확인하세요.

## 생성되는 리소스 (RG `smu-team1`, Korea Central — 고정/공유 RG)

| 리소스 | 이름(기본) | 용도 |
| --- | --- | --- |
| VNet + 서브넷 | `vnet-hanspoon-prod` (`snet-aca` /23, `snet-pe` /24) | ACA 통합 + Private Endpoint |
| Log Analytics | `log-hanspoon-prod` | ACA 로그 |
| ACR | `acrhanspoonprod` (Basic) | 백엔드 도커 이미지 |
| PostgreSQL | `psql-hanspoon-prod` (B1ms, 공개 비활성 + PE) | 핵심 DB (pgvector 허용) |
| Key Vault | `kv-hanspoon-prod` (RBAC) | 시크릿 |
| Storage(Blob) | `sthanspoonprod` | 메뉴 이미지 (CORS PUT 허용) |
| Container Apps | `cae-hanspoon-prod` / `ca-hanspoon-api` | Spring Boot 서버 |

리소스명은 `application-prod.yml`의 참조값과 1:1 일치합니다. (구독 RG가 `smu-team1` 등이면 스크립트 상단 변수 또는 환경변수로 치환)

## 사전 준비

1. **Azure CLI 설치 & 로그인**
   ```bash
   # macOS
   brew install azure-cli
   az login
   az account show   # 대상 구독 확인
   ```
2. **시크릿 값 준비**: PostgreSQL 비밀번호, (있으면) Azure OpenAI 키, Google Client ID. JWT 키는 미설정 시 스크립트가 자동 생성.

## 실행

```bash
export SUBSCRIPTION_ID="<구독 ID>"
export DB_PW='<강력한 DB 비밀번호>'
export GOOGLE_CLIENT_ID='<Google OAuth Client ID>'
export AOAI_KEY='<Azure OpenAI 키>'   # 없으면 생략(나중에 KV에 등록)
# JWT_SECRET 생략 시 자동 생성됨

chmod +x infra/azure-setup.sh
./infra/azure-setup.sh
```

스크립트는 9단계(RG→VNet→LogAnalytics→ACR→PostgreSQL+PE→KeyVault+시크릿→Blob→ACA→출력)를 순서대로 수행하고, 마지막에 앱 FQDN을 출력합니다.

> 처음엔 임시 이미지(`mcr.microsoft.com/k8se/quickstart`)로 앱을 띄웁니다. **실제 백엔드 이미지는 ③ CD 단계**(GitHub Actions → ACR 빌드 → `az containerapp update --image`)에서 배포됩니다.

## 검증

```bash
# 앱 FQDN
az containerapp show -g smu-team1 -n ca-hanspoon-api \
  --query properties.configuration.ingress.fqdn -o tsv
# (실제 백엔드 배포 후) 헬스체크
curl https://<FQDN>/actuator/health        # {"status":"UP"} 기대
# KV 시크릿 확인
az keyvault secret list --vault-name kv-hanspoon-prod --query "[].name" -o tsv
# DB private endpoint 연결 확인 (ACA 로그에서 Flyway 마이그레이션 성공 + privatelink 사설 IP resolve)
az containerapp logs show -g smu-team1 -n ca-hanspoon-api --tail 100
```

## 주의 / 알아둘 점

- **Private Endpoint 모드**: `--public-access Disabled`로 생성 후 Private Endpoint를 추가하는 패턴입니다. ACA가 VNet(`snet-aca`)에 통합되고 Private DNS Zone(`privatelink.postgres.database.azure.com`)이 VNet에 링크돼야 `psql-…privatelink…` 호스트가 **사설 IP로 resolve**됩니다.
- **ACA 서브넷**은 Consumption 환경 기준 `/23` 이상, `Microsoft.App/environments` 위임 필수.
- **재실행**: 일부 `az create`는 멱등이 아니어서 이미 존재하면 에러/경고가 날 수 있습니다. 부분 실패 시 해당 단계만 수동 재실행하세요.
- **시크릿 노출 금지**: `DB_PW`/`JWT_SECRET`/`AOAI_KEY`는 셸 히스토리에 남지 않게 주의(가능하면 `read -s` 또는 비밀 관리 도구 사용).
- 운영 보안을 더 높이려면 Key Vault/Blob/OpenAI도 Private Endpoint로 전환할 수 있습니다(추가 비용).
- **공유 구독 권한**: `smu-team1`은 학교/공유 RG입니다. 스크립트의 `az role assignment create`(KV/ACR 권한 부여, RBAC)는 **구독 Owner 또는 User Access Administrator** 권한이 필요합니다. 학생 계정에 권한이 없으면 이 단계에서 `AuthorizationFailed`가 납니다 → 담당자(구독 관리자)에게 역할 부여를 요청하거나, Key Vault를 RBAC 대신 **액세스 정책(access policy)** 모드로 만들어 매니지드 ID에 정책을 부여하는 방식으로 우회하세요.
- **리소스 이름 전역 유일성**: ACR(`acrhanspoonprod`)·Storage(`sthanspoonprod`)·Key Vault(`kv-hanspoon-prod`)는 전역 유일해야 합니다. 공유 구독에서 이미 점유됐다면 뒤에 접미사를 붙이고(`...prod2`) `application-prod.yml`도 함께 맞추세요.

## 정리(teardown)

> 🚫 **`smu-team1`은 고정·공유 RG입니다. `az group delete`로 RG를 통째로 지우지 마세요(다른 리소스/팀 영향).**
> 우리가 만든 리소스만 개별 삭제하세요:
```bash
RG=smu-team1
az containerapp delete -g $RG -n ca-hanspoon-api --yes
az containerapp env delete -g $RG -n cae-hanspoon-prod --yes
az postgres flexible-server delete -g $RG -n psql-hanspoon-prod --yes
az network private-endpoint delete -g $RG -n pe-psql-hanspoon-prod
az network private-dns zone delete -g $RG -n privatelink.postgres.database.azure.com --yes
az keyvault delete -g $RG -n kv-hanspoon-prod   # 필요 시 purge: az keyvault purge -n kv-hanspoon-prod
az storage account delete -g $RG -n sthanspoonprod --yes
az acr delete -g $RG -n acrhanspoonprod --yes
az monitor log-analytics workspace delete -g $RG -n log-hanspoon-prod --yes
az network vnet delete -g $RG -n vnet-hanspoon-prod
```

## 다음 (③ CD)

ACR/ACA가 올라오면:
- GitHub→Azure **OIDC**(federated credential) 구성
- `docker/Dockerfile`(멀티스테이지, temurin:21) + `.github/workflows/deploy-prod.yml`
- main 푸시 → ACR 빌드 → `az containerapp update`로 실제 이미지 배포
