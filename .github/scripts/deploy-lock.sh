#!/usr/bin/env bash
# ECS 배포 lock(DynamoDB). deploy-prod.yml 과 같은 키 사용
# 사용: deploy-lock.sh acquire|release <owner>
set -euo pipefail

TABLE="${DEPLOY_LOCK_TABLE:?}"
ACTION="$1"
OWNER="$2"

if [ "$ACTION" = "release" ]; then
  VALUES="$(jq -nc --arg owner "$OWNER" '{":owner":{S:$owner}}')"
  aws dynamodb delete-item --table-name "$TABLE" \
    --key '{"lock_name":{"S":"prod-ecs"}}' \
    --condition-expression '#owner = :owner' \
    --expression-attribute-names '{"#owner":"owner"}' \
    --expression-attribute-values "$VALUES" || true
  exit 0
fi

ERR="$(mktemp)"
for i in $(seq 1 60); do
  NOW="$(date +%s)"
  ITEM="$(jq -nc --arg owner "$OWNER" --argjson expires "$((NOW + 900))" \
    '{lock_name:{S:"prod-ecs"},owner:{S:$owner},expires_at:{N:($expires|tostring)}}')"
  VALUES="$(jq -nc --argjson now "$NOW" '{":now":{N:($now|tostring)}}')"
  if aws dynamodb put-item --table-name "$TABLE" --item "$ITEM" \
       --condition-expression 'attribute_not_exists(#lock) OR #expires < :now' \
       --expression-attribute-names '{"#lock":"lock_name","#expires":"expires_at"}' \
       --expression-attribute-values "$VALUES" 2>"$ERR"; then
    exit 0
  fi
  if ! grep -q 'ConditionalCheckFailedException' "$ERR"; then
    sed -n '1,20p' "$ERR"
    exit 1
  fi
  echo "다른 배포가 lock 보유 중... ($i/60)"
  sleep 5
done
echo "lock 대기 시간 초과"
exit 1
