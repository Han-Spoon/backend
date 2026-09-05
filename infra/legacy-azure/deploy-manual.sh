#!/usr/bin/env bash
#
# 수동 배포 (③ CD 자동화 전 검증용)
# 내 PC(az login 상태)에서 실제 백엔드 이미지를 ACR에 빌드하고 ACA에 배포한다.
# 로컬 Docker 불필요 — 'az acr build'가 클라우드에서 이미지를 빌드함.
#
# 사용: 레포 루트에서
#   ./infra/deploy-manual.sh
#
set -euo pipefail

RG="${RG:-smu-team1}"
ACR="${ACR:-acrhanspoonprod}"
APP="${APP:-ca-hanspoon-api}"
TAG="${TAG:-manual-$(date +%Y%m%d-%H%M%S)}"
IMAGE_REPO="hanspoon-api"
IMAGE="$ACR.azurecr.io/$IMAGE_REPO:$TAG"

echo "▶ [1/3] ACR 빌드 (클라우드): $IMAGE"
az acr build -r "$ACR" -t "$IMAGE_REPO:$TAG" -f docker/Dockerfile .

echo "▶ [2/3] ACA 배포 (새 리비전)"
az containerapp update -g "$RG" -n "$APP" --image "$IMAGE" -o none

echo "▶ [3/3] 상태 확인"
FQDN=$(az containerapp show -g "$RG" -n "$APP" --query properties.configuration.ingress.fqdn -o tsv)
echo "  앱 URL : https://$FQDN"
echo "  헬스   : curl https://$FQDN/actuator/health   ('{\"status\":\"UP\"}' 기대)"
echo "  로그   : az containerapp logs show -g $RG -n $APP --tail 100 --follow"
