#!/usr/bin/env bash
# 시드 적재 태스크 실행 → 종료 대기 → 로그를 job summary 로 출력, 컨테이너 종료 코드로 종료
# 사용: run-seed-task.sh <task-definition-arn> <s3-uri> [--dry-run]
set -euo pipefail

TASK_DEF="$1"
SEED_URI="$2"
MODE_ARG="${3:-}"
CLUSTER="${ECS_CLUSTER:?}"
LOG_GROUP="${SEED_LOG_GROUP:?}"

SEED_URL="$(aws s3 presign "$SEED_URI" --expires-in 900)"
COMMAND="$(jq -nc --arg url "$SEED_URL" --arg mode "$MODE_ARG" \
  '["--seed-url", $url] + (if $mode == "" then [] else [$mode] end)')"
OVERRIDES="$(jq -nc --argjson cmd "$COMMAND" '{containerOverrides:[{name:"seed-loader",command:$cmd}]}')"

RESULT="$(aws ecs run-task --cluster "$CLUSTER" --task-definition "$TASK_DEF" \
  --started-by knowledge-seed --overrides "$OVERRIDES" --output json)"
if [ "$(jq '.failures | length' <<<"$RESULT")" != "0" ]; then
  echo "태스크 배치 실패:"
  jq '.failures' <<<"$RESULT"
  exit 1
fi
TASK_ARN="$(jq -r '.tasks[0].taskArn' <<<"$RESULT")"
echo "태스크: $TASK_ARN"

aws ecs wait tasks-stopped --cluster "$CLUSTER" --tasks "$TASK_ARN"

TASK="$(aws ecs describe-tasks --cluster "$CLUSTER" --tasks "$TASK_ARN" --output json)"
EXIT_CODE="$(jq -r '.tasks[0].containers[0].exitCode // "null"' <<<"$TASK")"
REASON="$(jq -r '.tasks[0].stoppedReason // ""' <<<"$TASK")"

LOGS="$(aws logs get-log-events --log-group-name "$LOG_GROUP" \
  --log-stream-name "seed/seed-loader/${TASK_ARN##*/}" --start-from-head \
  --query 'events[].message' --output text 2>/dev/null | tr '\t' '\n' || true)"
echo "$LOGS"

{
  echo "### 시드 적재 ${MODE_ARG:-apply}"
  echo "- 시드: \`$SEED_URI\`"
  echo "- 종료 코드: \`$EXIT_CODE\` ${REASON:+($REASON)}"
  echo '```'
  echo "$LOGS"
  echo '```'
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"

[ "$EXIT_CODE" = "0" ]
