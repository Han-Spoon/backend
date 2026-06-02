#!/usr/bin/env bash
#
# Han-Spoon 백엔드 — Azure 인프라 수동 프로비저닝 스크립트 (Phase ②)
# 계획서(keen-soaring-sonnet) ②단계의 az CLI 명령을 파라미터화한 것.
# DB 네트워크: PostgreSQL Flexible Server + Private Endpoint + VNet (공개 비활성).
#
# ⚠️ 유료 리소스를 생성합니다. 실행 전 `infra/README.md`를 반드시 읽으세요.
# 사용법:
#   export SUBSCRIPTION_ID=...        # 대상 구독
#   export DB_PW='...'                # PostgreSQL 관리자 비밀번호
#   export JWT_SECRET='...'           # HS256 서명 키(32바이트+). 미설정 시 자동 생성
#   export AOAI_KEY='...'             # Azure OpenAI 키 (없으면 빈 값으로 두고 나중에 set)
#   export GOOGLE_CLIENT_ID='...'     # Google OAuth Client ID
#   ./infra/azure-setup.sh
#
set -euo pipefail

# ─────────────────────────────────────────────────────────────
# 0. 변수 (필요 시 수정)
# ─────────────────────────────────────────────────────────────
RG="${RG:-smu-team1}"            # 고정 RG (Korea Central).
LOC="${LOC:-koreacentral}"
VNET="${VNET:-vnet-hanspoon-prod}"
SNET_ACA="${SNET_ACA:-snet-aca}"
SNET_PE="${SNET_PE:-snet-pe}"
LAW="${LAW:-log-hanspoon-prod}"
ACR="${ACR:-acrhanspoonprod}"            # 전역 유일, 영숫자만
PG="${PG:-psql-hanspoon-prod}"
KV="${KV:-kv-hanspoon-prod}"
SA="${SA:-sthanspoonprod}"               # 전역 유일, 소문자+숫자 3~24자
ACA_ENV="${ACA_ENV:-cae-hanspoon-prod}"
APP="${APP:-ca-hanspoon-api}"
DB_NAME="${DB_NAME:-hanspoon}"
DB_USER="${DB_USER:-hanspoon_app}"
PG_DNS_ZONE="privatelink.postgres.database.azure.com"

# 프론트 출처(CORS) / 도메인
CORS_ORIGINS="${CORS_ORIGINS:-https://han-spoon.vercel.app,https://www.han-spoon.com}"
JWT_ISSUER="${JWT_ISSUER:-https://api.han-spoon.com}"
AOAI_ENDPOINT="${AOAI_ENDPOINT:-https://aoai-hanspoon-prod.openai.azure.com}"

# 시크릿 (환경변수로 주입)
: "${SUBSCRIPTION_ID:?SUBSCRIPTION_ID 를 설정하세요}"
: "${DB_PW:?DB_PW 를 설정하세요}"
JWT_SECRET="${JWT_SECRET:-$(openssl rand -base64 48)}"
AOAI_KEY="${AOAI_KEY:-}"
GOOGLE_CLIENT_ID="${GOOGLE_CLIENT_ID:-}"

echo "▶ 구독 설정: $SUBSCRIPTION_ID"
az account set --subscription "$SUBSCRIPTION_ID"

echo "▶ 리소스 프로바이더 등록 (최초 1회)"
for ns in Microsoft.App Microsoft.OperationalInsights Microsoft.DBforPostgreSQL \
          Microsoft.KeyVault Microsoft.Storage Microsoft.ContainerRegistry Microsoft.Network; do
  az provider register --namespace "$ns" --only-show-errors >/dev/null || true
done
az extension add --name containerapp --upgrade --only-show-errors >/dev/null || true

# ─────────────────────────────────────────────────────────────
# 1. 리소스 그룹
# ─────────────────────────────────────────────────────────────
echo "▶ [1/9] Resource Group (고정 RG 사용 — 생성하지 않음)"
az group show -n "$RG" -o none          # 없거나 접근 권한 없으면 여기서 실패
LOC=$(az group show -n "$RG" --query location -o tsv)   # 리전을 RG에 맞춤
echo "  - RG=$RG, LOC=$LOC"

# ─────────────────────────────────────────────────────────────
# 2. VNet + 서브넷 (ACA 통합 /23, Private Endpoint /24)
# ─────────────────────────────────────────────────────────────
echo "▶ [2/9] VNet + Subnets"
az network vnet create -g "$RG" -n "$VNET" --address-prefixes 10.0.0.0/16 --only-show-errors -o none
az network vnet subnet create -g "$RG" --vnet-name "$VNET" -n "$SNET_ACA" \
  --address-prefixes 10.0.0.0/23 --delegations Microsoft.App/environments --only-show-errors -o none
az network vnet subnet create -g "$RG" --vnet-name "$VNET" -n "$SNET_PE" \
  --address-prefixes 10.0.2.0/24 --only-show-errors -o none

# ─────────────────────────────────────────────────────────────
# 3. Log Analytics
# ─────────────────────────────────────────────────────────────
echo "▶ [3/9] Log Analytics"
az monitor log-analytics workspace create -g "$RG" -n "$LAW" -l "$LOC" --only-show-errors -o none

# ─────────────────────────────────────────────────────────────
# 4. ACR (Basic)
# ─────────────────────────────────────────────────────────────
echo "▶ [4/9] Azure Container Registry"
az acr create -g "$RG" -n "$ACR" --sku Basic --only-show-errors -o none

# ─────────────────────────────────────────────────────────────
# 5. PostgreSQL Flexible Server + Private Endpoint (공개 비활성)
# ─────────────────────────────────────────────────────────────
echo "▶ [5/9] PostgreSQL Flexible Server (Private)"
az postgres flexible-server create -g "$RG" -n "$PG" -l "$LOC" \
  --tier Burstable --sku-name Standard_B1ms --storage-size 32 --version 16 \
  --admin-user "$DB_USER" --admin-password "$DB_PW" \
  --public-access Disabled --yes --only-show-errors -o none
az postgres flexible-server db create -g "$RG" -s "$PG" -d "$DB_NAME" --only-show-errors -o none
# pgvector 확장 허용
az postgres flexible-server parameter set -g "$RG" -s "$PG" \
  --name azure.extensions --value VECTOR --only-show-errors -o none

echo "  - Private Endpoint + Private DNS"
PG_ID=$(az postgres flexible-server show -g "$RG" -n "$PG" --query id -o tsv)
az network private-endpoint create -g "$RG" -n "pe-$PG" --vnet-name "$VNET" --subnet "$SNET_PE" \
  --private-connection-resource-id "$PG_ID" --group-id postgresqlServer \
  --connection-name "pec-$PG" --only-show-errors -o none
az network private-dns zone create -g "$RG" -n "$PG_DNS_ZONE" --only-show-errors -o none
az network private-dns link vnet create -g "$RG" -n dnslink-pg \
  -z "$PG_DNS_ZONE" -v "$VNET" -e false --only-show-errors -o none
az network private-endpoint dns-zone-group create -g "$RG" --endpoint-name "pe-$PG" \
  -n pgzg --private-dns-zone "$PG_DNS_ZONE" --zone-name postgres --only-show-errors -o none

# ─────────────────────────────────────────────────────────────
# 6. Key Vault (RBAC) + 시크릿
# ─────────────────────────────────────────────────────────────
echo "▶ [6/9] Key Vault + Secrets"
az keyvault create -g "$RG" -n "$KV" -l "$LOC" --enable-rbac-authorization true --only-show-errors -o none
ME=$(az ad signed-in-user show --query id -o tsv)
KV_ID=$(az keyvault show -n "$KV" --query id -o tsv)
az role assignment create --assignee "$ME" --role "Key Vault Secrets Officer" \
  --scope "$KV_ID" --only-show-errors -o none || true
echo "  - 시크릿 등록 (RBAC 전파까지 잠시 대기)"; sleep 20
az keyvault secret set --vault-name "$KV" -n kv-secret-jwt-signing-key --value "$JWT_SECRET" --only-show-errors -o none
az keyvault secret set --vault-name "$KV" -n kv-secret-db-password     --value "$DB_PW"     --only-show-errors -o none
if [ -n "$AOAI_KEY" ]; then
  az keyvault secret set --vault-name "$KV" -n kv-secret-azure-openai-api-key --value "$AOAI_KEY" --only-show-errors -o none
else
  echo "  ! AOAI_KEY 미설정 → kv-secret-azure-openai-api-key 는 나중에 등록 필요"
fi

# ─────────────────────────────────────────────────────────────
# 7. Storage(Blob) — 메뉴 이미지 + CORS(SAS 직접 업로드)
# ─────────────────────────────────────────────────────────────
echo "▶ [7/9] Storage(Blob)"
az storage account create -g "$RG" -n "$SA" -l "$LOC" --sku Standard_LRS --kind StorageV2 --only-show-errors -o none
az storage container create --account-name "$SA" -n menu-images --auth-mode login --only-show-errors -o none
IFS=',' read -ra ORIGINS <<< "$CORS_ORIGINS"
az storage cors add --account-name "$SA" --services b --methods PUT GET \
  --origins "${ORIGINS[@]}" --allowed-headers '*' --exposed-headers '*' --max-age 3600 --only-show-errors

# ─────────────────────────────────────────────────────────────
# 8. ACA 환경 + 컨테이너 앱 (VNet 통합, 매니지드 ID)
# ─────────────────────────────────────────────────────────────
echo "▶ [8/9] Container Apps Environment + App"
LAW_ID=$(az monitor log-analytics workspace show -g "$RG" -n "$LAW" --query customerId -o tsv)
LAW_KEY=$(az monitor log-analytics workspace get-shared-keys -g "$RG" -n "$LAW" --query primarySharedKey -o tsv)
ACA_SUBNET_ID=$(az network vnet subnet show -g "$RG" --vnet-name "$VNET" -n "$SNET_ACA" --query id -o tsv)
az containerapp env create -g "$RG" -n "$ACA_ENV" -l "$LOC" \
  --logs-workspace-id "$LAW_ID" --logs-workspace-key "$LAW_KEY" \
  --infrastructure-subnet-resource-id "$ACA_SUBNET_ID" --only-show-errors -o none
az containerapp create -g "$RG" -n "$APP" --environment "$ACA_ENV" \
  --image mcr.microsoft.com/k8se/quickstart:latest \
  --ingress external --target-port 8080 --system-assigned \
  --min-replicas 1 --max-replicas 5 --only-show-errors -o none

echo "  - 매니지드 ID 권한(KV/ACR)"
APP_PID=$(az containerapp show -g "$RG" -n "$APP" --query identity.principalId -o tsv)
az role assignment create --assignee "$APP_PID" --role "Key Vault Secrets User" \
  --scope "$KV_ID" --only-show-errors -o none || true
az role assignment create --assignee "$APP_PID" --role AcrPull \
  --scope "$(az acr show -n "$ACR" --query id -o tsv)" --only-show-errors -o none || true

echo "  - 환경변수 주입 (application-prod.yml 기대 키)"
az containerapp update -g "$RG" -n "$APP" --only-show-errors -o none --set-env-vars \
  SPRING_PROFILES_ACTIVE=prod \
  AZURE_KEYVAULT_ENDPOINT="https://$KV.vault.azure.net/" \
  DB_PRIVATE_ENDPOINT_URL="jdbc:postgresql://$PG.$PG_DNS_ZONE:5432/$DB_NAME?sslmode=require" \
  DB_USERNAME="$DB_USER" \
  GOOGLE_CLIENT_ID="$GOOGLE_CLIENT_ID" \
  JWT_ISSUER="$JWT_ISSUER" \
  AZURE_AI_PRIVATE_ENDPOINT="$AOAI_ENDPOINT" \
  CORS_ALLOWED_ORIGINS="$CORS_ORIGINS"

# ─────────────────────────────────────────────────────────────
# 9. 출력
# ─────────────────────────────────────────────────────────────
echo "▶ [9/9] 완료. FQDN:"
FQDN=$(az containerapp show -g "$RG" -n "$APP" --query properties.configuration.ingress.fqdn -o tsv)
echo "  https://$FQDN"
echo ""
echo "다음:"
echo "  - DNS(Cloudflare): api.han-spoon.com → ACA custom domain + managed cert"
echo "  - ③ CD: GitHub Actions가 ACR 빌드 후 'az containerapp update --image ...'로 실제 앱 배포"
echo "  - 헬스체크: curl https://$FQDN/actuator/health"
