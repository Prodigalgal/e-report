#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-e-report-test}"
PRODUCTION_NAMESPACE="${PRODUCTION_NAMESPACE:-e-report}"
MANIFEST="${MANIFEST:-$(dirname "$0")/e-report-test.yaml}"
KUBECTL="${KUBECTL:-kubectl}"

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "缺少命令: $1" >&2; exit 1; }
}

require_command "$KUBECTL"
require_command openssl

context="$($KUBECTL config current-context)"
if ! "$KUBECTL" -n "$PRODUCTION_NAMESPACE" get deployment/e-report-backend >/dev/null 2>&1; then
  echo "当前 kubectl context 无法访问生产 namespace $PRODUCTION_NAMESPACE（context=$context），已停止以避免读取错误集群" >&2
  exit 1
fi

read_secret() {
  "$KUBECTL" -n "$1" get secret "$2" -o "jsonpath={.data.$3}" | base64 -d
}

read_optional_secret() {
  "$KUBECTL" -n "$1" get secret "$2" -o "jsonpath={.data.$3}" 2>/dev/null | base64 -d || true
}

random_secret() {
  openssl rand -hex 32
}

echo "应用测试 namespace 资源: $NAMESPACE"
"$KUBECTL" apply -f "$MANIFEST"

production_mysql_password="$(read_secret "$PRODUCTION_NAMESPACE" e-report-mysql MYSQL_ROOT_PASSWORD)"
default_admin_username="$(read_secret "$PRODUCTION_NAMESPACE" e-report-default-admin DEFAULT_ADMIN_USERNAME)"
default_admin_password="$(read_secret "$PRODUCTION_NAMESPACE" e-report-default-admin DEFAULT_ADMIN_PASSWORD)"
default_admin_phone="$(read_optional_secret "$PRODUCTION_NAMESPACE" e-report-default-admin DEFAULT_ADMIN_PHONE)"

test_mysql_password="$(random_secret)"
test_redis_password="$(random_secret)"
test_auth_secret="$(random_secret)"

create_secret() {
  local secret_name="$1"
  shift
  "$KUBECTL" -n "$NAMESPACE" create secret generic "$secret_name" "$@" --dry-run=client -o yaml | "$KUBECTL" apply -f - >/dev/null
}

create_secret e-report-mysql \
  --from-literal=MYSQL_ROOT_PASSWORD="$test_mysql_password"
create_secret e-report-redis \
  --from-literal=REDIS_PASSWORD="$test_redis_password"
create_secret e-report-auth \
  --from-literal=AUTH_TOKEN_SECRET="$test_auth_secret"
create_secret e-report-default-admin \
  --from-literal=DEFAULT_ADMIN_USERNAME="$default_admin_username" \
  --from-literal=DEFAULT_ADMIN_PASSWORD="$default_admin_password" \
  --from-literal=DEFAULT_ADMIN_PHONE="$default_admin_phone"
create_secret e-report-test-sync \
  --from-literal=PRODUCTION_MYSQL_ROOT_PASSWORD="$production_mysql_password"

echo "等待测试 MySQL、Redis 就绪"
"$KUBECTL" -n "$NAMESPACE" rollout status deployment/e-mysql --timeout=300s
"$KUBECTL" -n "$NAMESPACE" rollout status deployment/e-report-redis --timeout=300s
"$KUBECTL" -n "$NAMESPACE" create job --from=cronjob/e-report-test-data-sync "e-report-test-data-sync-initial-$(date +%s)"
echo "首次同步已提交；使用 kubectl -n $NAMESPACE get jobs,pods 观察完成状态"
