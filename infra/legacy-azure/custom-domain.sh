#!/usr/bin/env bash
#
# Han-Spoon 백엔드 — ACA 사용자 지정 도메인 + 무료 관리형 인증서 (Phase ② 후속)
# api.han-spoon.com → ca-hanspoon-api (ACA) 에 바인딩하고 Azure 관리형 TLS 인증서를 발급한다.
#
# 방침: Cloudflare는 **DNS only(회색 구름)** 로만 사용한다.
#   ⚠️ Cloudflare 프록시(주황 구름)를 켜면 CNAME이 Cloudflare를 경유해
#      ACA 생성 FQDN을 직접 가리키지 못하므로 관리형 인증서 발급·갱신이 실패한다.
#
# 사용 순서:
#   1) ./infra/custom-domain.sh show   # CNAME / asuid TXT 레코드 값 출력
#   2) (사용자) Cloudflare 대시보드에서 아래 2개 레코드를 DNS only 로 추가하고 전파 대기
#   3) ./infra/custom-domain.sh bind   # 호스트네임 추가 + 관리형 인증서 바인딩
#   4) ./infra/custom-domain.sh verify # 바인딩 상태 + HTTPS 헬스 확인
#
set -euo pipefail

# ─────────────────────────────────────────────────────────────
# 0. 변수 (필요 시 수정)
# ─────────────────────────────────────────────────────────────
RG="${RG:-smu-team1}"                  # 고정 RG (Korea Central)
APP="${APP:-ca-hanspoon-api}"
ACA_ENV="${ACA_ENV:-cae-hanspoon-prod}"
DOMAIN="${DOMAIN:-api.han-spoon.com}"

# 서브도메인 라벨(= DNS Host 이름). api.han-spoon.com → "api"
SUB="${DOMAIN%%.*}"

az extension add --name containerapp --upgrade --only-show-errors >/dev/null || true

# ─────────────────────────────────────────────────────────────
# show : Cloudflare 에 등록할 DNS 레코드 값 산출
# ─────────────────────────────────────────────────────────────
cmd_show() {
  echo "▶ Ingress 확인 (External + HTTP 8080 이어야 관리형 인증서 발급 가능)"
  az containerapp ingress show -g "$RG" -n "$APP" \
    --query "{external:external, targetPort:targetPort, transport:transport}" -o jsonc

  local fqdn verify
  fqdn=$(az containerapp show -g "$RG" -n "$APP" -o tsv \
           --query "properties.configuration.ingress.fqdn")
  verify=$(az containerapp show -g "$RG" -n "$APP" -o tsv \
           --query "properties.customDomainVerificationId")

  cat <<EOF

────────────────────────────────────────────────────────────────
Cloudflare( $DOMAIN 의 존 ) 에 아래 2개 레코드를 추가하세요.
⚠️ 둘 다 Proxy status = DNS only (회색 구름), TTL Auto.
────────────────────────────────────────────────────────────────
  [1] CNAME
      Name    : ${SUB}
      Content : ${fqdn}
  [2] TXT
      Name    : asuid.${SUB}
      Content : ${verify}
────────────────────────────────────────────────────────────────
전파 확인:
  dig +short ${DOMAIN} CNAME
  dig +short asuid.${DOMAIN} TXT
레코드가 보이면: ./infra/custom-domain.sh bind
EOF
}

# ─────────────────────────────────────────────────────────────
# bind : 호스트네임 추가 + 관리형 인증서 바인딩 (DNS 레코드 선행 필수)
# ─────────────────────────────────────────────────────────────
cmd_bind() {
  echo "▶ 호스트네임 추가: $DOMAIN"
  az containerapp hostname add -g "$RG" -n "$APP" --hostname "$DOMAIN" --only-show-errors

  echo "▶ 관리형 인증서 바인딩 (CNAME 검증) — 발급에 수 분 소요"
  az containerapp hostname bind -g "$RG" -n "$APP" \
    --environment "$ACA_ENV" --hostname "$DOMAIN" --validation-method CNAME

  echo "▶ 완료. 상태 확인: ./infra/custom-domain.sh verify"
}

# ─────────────────────────────────────────────────────────────
# verify : 바인딩 상태 + HTTPS 헬스
# ─────────────────────────────────────────────────────────────
cmd_verify() {
  echo "▶ 바인딩된 호스트네임 목록"
  az containerapp hostname list -g "$RG" -n "$APP" -o table

  echo "▶ HTTPS 헬스 (Secured 후 200/UP 이어야 정상)"
  curl -sS "https://${DOMAIN}/actuator/health" || true
  echo
}

case "${1:-show}" in
  show)   cmd_show ;;
  bind)   cmd_bind ;;
  verify) cmd_verify ;;
  *) echo "usage: $0 {show|bind|verify}" >&2; exit 1 ;;
esac
