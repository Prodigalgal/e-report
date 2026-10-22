#!/usr/bin/env python3
"""Inspect, reset, and verify e-report production data on the single-node K8S host."""

from __future__ import annotations

import argparse
import importlib.util
import re
import shlex
from pathlib import Path
from types import ModuleType


ROOT = Path(__file__).resolve().parents[2]
DEPLOY_MODULE = ROOT / "deploy" / "local-k8s" / "deploy-app-backend-via-ssh.py"
CONFIRMATION = "RESET-E-REPORT-PRODUCTION"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Prepare the e-report database and attachment volume for production."
    )
    parser.add_argument("action", choices=("inspect", "execute", "verify"))
    parser.add_argument("--confirm", help=f"required for execute: {CONFIRMATION}")
    parser.add_argument("--admin-username", default="admin")
    args = parser.parse_args()
    if args.action == "execute" and args.confirm != CONFIRMATION:
        parser.error(f"execute requires --confirm {CONFIRMATION}")
    if not re.fullmatch(r"[A-Za-z0-9_.-]{1,50}", args.admin_username):
        parser.error("--admin-username contains unsupported characters")
    return args


def load_deploy_module() -> ModuleType:
    spec = importlib.util.spec_from_file_location("e_report_deploy", DEPLOY_MODULE)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load deployment module: {DEPLOY_MODULE}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def common_shell() -> str:
    return r"""
set -euo pipefail
export KUBECONFIG="${KUBECONFIG:-/etc/kubernetes/admin.conf}"
NAMESPACE="e-report"
DATABASE="fault_reporter_db"
DATA_ROOT="/data/e-report"
MYSQL_POD="$(kubectl -n "$NAMESPACE" get pod -l app=e-mysql --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')"
MYSQL_PASSWORD="$(kubectl -n "$NAMESPACE" get secret e-report-mysql -o jsonpath='{.data.MYSQL_ROOT_PASSWORD}' | base64 -d)"

if [[ -z "$MYSQL_POD" || -z "$MYSQL_PASSWORD" ]]; then
  echo "MySQL pod or credential is unavailable." >&2
  exit 1
fi

mysql_exec() {
  kubectl -n "$NAMESPACE" exec "$MYSQL_POD" -- env MYSQL_PWD="$MYSQL_PASSWORD" \
    mysql -uroot --default-character-set=utf8mb4 -N -B "$DATABASE" -e "$1"
}

mysql_dump() {
  kubectl -n "$NAMESPACE" exec "$MYSQL_POD" -- env MYSQL_PWD="$MYSQL_PASSWORD" \
    mysqldump -uroot --single-transaction --routines --triggers --hex-blob "$DATABASE"
}

print_state() {
  echo "=== users ==="
  mysql_exec "SELECT id, username, COALESCE(name, ''), status FROM users ORDER BY id;"
  echo "=== exact table counts ==="
  while IFS= read -r table; do
    printf '%s\t' "$table"
    mysql_exec "SELECT COUNT(*) FROM ${table};"
  done < <(mysql_exec "SHOW TABLES;")
  echo "=== upload volume ==="
  find "$DATA_ROOT/uploads" -type f -printf '%P\t%s\n' | sort
  printf 'TOTAL\t'
  find "$DATA_ROOT/uploads" -type f -printf '%s\n' | awk '{count += 1; bytes += $1} END {print count + 0 " files, " bytes + 0 " bytes"}'
  echo "=== app release volume ==="
  find "$DATA_ROOT/app-releases" -type f -printf '%P\t%s\n' | sort
  echo "=== default test secret ==="
  if kubectl -n "$NAMESPACE" get secret e-report-default-test-user >/dev/null 2>&1; then
    echo "present"
  else
    echo "absent"
  fi
}
"""


def inspect_script() -> str:
    return common_shell() + r"""
print_state
echo "=== foreign keys ==="
mysql_exec "SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME, CONSTRAINT_NAME;"
"""


def execute_script(admin_username: str) -> str:
    admin = shlex.quote(admin_username)
    return common_shell() + f"""
ADMIN_USERNAME={admin}
ADMIN_COUNT="$(mysql_exec "SELECT COUNT(*) FROM users WHERE username = '$ADMIN_USERNAME';")"
if [[ "$ADMIN_COUNT" != "1" ]]; then
  echo "Expected exactly one administrator '$ADMIN_USERNAME', found $ADMIN_COUNT. Nothing was changed." >&2
  exit 1
fi

ORIGINAL_REPLICAS="$(kubectl -n "$NAMESPACE" get deploy e-report-backend -o jsonpath='{{.spec.replicas}}')"
BACKEND_RESTORED=false
restore_backend() {{
  if [[ "$BACKEND_RESTORED" != "true" ]]; then
    kubectl -n "$NAMESPACE" scale deploy/e-report-backend --replicas="$ORIGINAL_REPLICAS" >/dev/null || true
  fi
}}
trap restore_backend EXIT

echo "Scaling backend to zero before backup and cleanup."
kubectl -n "$NAMESPACE" scale deploy/e-report-backend --replicas=0
kubectl -n "$NAMESPACE" wait --for=delete pod -l app=e-report-backend --timeout=180s || true

BACKUP_DIR="$DATA_ROOT/backups/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$BACKUP_DIR"
echo "Creating backup in $BACKUP_DIR"
mysql_dump | gzip -1 > "$BACKUP_DIR/fault_reporter_db.sql.gz"
tar -C "$DATA_ROOT" -czf "$BACKUP_DIR/uploads.tar.gz" uploads
print_state > "$BACKUP_DIR/pre-cleanup-state.txt"
sha256sum "$BACKUP_DIR/fault_reporter_db.sql.gz" "$BACKUP_DIR/uploads.tar.gz" > "$BACKUP_DIR/SHA256SUMS"

mysql_exec "
START TRANSACTION;
DELETE FROM work_order_material_usages;
DELETE FROM attachments;
DELETE FROM fault_reports;
DELETE FROM file_transfer_upload_sessions;
DELETE FROM external_command_records;
DELETE FROM external_user_bindings;
DELETE FROM wecom_pending_materials;
DELETE FROM wecom_group_chat_configs;
DELETE ur FROM user_roles ur JOIN users u ON u.id = ur.user_id WHERE u.username <> '$ADMIN_USERNAME';
DELETE FROM users WHERE username <> '$ADMIN_USERNAME';
COMMIT;
ALTER TABLE work_order_material_usages AUTO_INCREMENT = 1;
ALTER TABLE attachments AUTO_INCREMENT = 1;
ALTER TABLE fault_reports AUTO_INCREMENT = 1;
"

UPLOAD_ROOT="$(realpath -m "$DATA_ROOT/uploads")"
if [[ "$UPLOAD_ROOT" != "/data/e-report/uploads" ]]; then
  echo "Refusing to clean unexpected upload path: $UPLOAD_ROOT" >&2
  exit 1
fi
find "$UPLOAD_ROOT" -mindepth 1 -delete

REDIS_POD="$(kubectl -n "$NAMESPACE" get pod -l app=e-report-redis --field-selector=status.phase=Running -o jsonpath='{{.items[0].metadata.name}}')"
kubectl -n "$NAMESPACE" exec "$REDIS_POD" -- redis-cli FLUSHDB >/dev/null
kubectl -n "$NAMESPACE" delete secret e-report-default-test-user --ignore-not-found

echo "Restoring backend replicas."
kubectl -n "$NAMESPACE" scale deploy/e-report-backend --replicas="$ORIGINAL_REPLICAS"
if [[ "$ORIGINAL_REPLICAS" != "0" ]]; then
  kubectl -n "$NAMESPACE" rollout status deploy/e-report-backend --timeout=240s
fi
BACKEND_RESTORED=true

echo "BACKUP_DIR=$BACKUP_DIR"
print_state
"""


def verify_script(admin_username: str) -> str:
    admin = shlex.quote(admin_username)
    return common_shell() + f"""
ADMIN_USERNAME={admin}
USER_COUNT="$(mysql_exec "SELECT COUNT(*) FROM users;")"
ADMIN_COUNT="$(mysql_exec "SELECT COUNT(*) FROM users WHERE username = '$ADMIN_USERNAME' AND status = 'ACTIVE';")"
WORK_ORDER_COUNT="$(mysql_exec "SELECT COUNT(*) FROM fault_reports;")"
ATTACHMENT_COUNT="$(mysql_exec "SELECT COUNT(*) FROM attachments;")"
MATERIAL_COUNT="$(mysql_exec "SELECT COUNT(*) FROM work_order_material_usages;")"
UPLOAD_SESSION_COUNT="$(mysql_exec "SELECT COUNT(*) FROM file_transfer_upload_sessions;")"
EXTERNAL_COMMAND_COUNT="$(mysql_exec "SELECT COUNT(*) FROM external_command_records;")"
EXTERNAL_BINDING_COUNT="$(mysql_exec "SELECT COUNT(*) FROM external_user_bindings;")"
WECOM_PENDING_COUNT="$(mysql_exec "SELECT COUNT(*) FROM wecom_pending_materials;")"
WECOM_GROUP_COUNT="$(mysql_exec "SELECT COUNT(*) FROM wecom_group_chat_configs;")"
UPLOAD_FILE_COUNT="$(find "$DATA_ROOT/uploads" -type f | wc -l)"
RELEASE_COUNT="$(mysql_exec "SELECT COUNT(*) FROM app_releases WHERE published = 1;")"
RELEASE_FILE_COUNT="$(find "$DATA_ROOT/app-releases" -type f | wc -l)"
LATEST_BACKUP="$(find "$DATA_ROOT/backups" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1)"

printf 'users=%s admin_active=%s work_orders=%s attachments=%s materials=%s upload_sessions=%s external_commands=%s external_bindings=%s wecom_pending=%s wecom_groups=%s upload_files=%s published_releases=%s release_files=%s\n' \
  "$USER_COUNT" "$ADMIN_COUNT" "$WORK_ORDER_COUNT" "$ATTACHMENT_COUNT" "$MATERIAL_COUNT" "$UPLOAD_SESSION_COUNT" "$EXTERNAL_COMMAND_COUNT" "$EXTERNAL_BINDING_COUNT" "$WECOM_PENDING_COUNT" "$WECOM_GROUP_COUNT" "$UPLOAD_FILE_COUNT" "$RELEASE_COUNT" "$RELEASE_FILE_COUNT"

[[ "$USER_COUNT" == "1" ]]
[[ "$ADMIN_COUNT" == "1" ]]
[[ "$WORK_ORDER_COUNT" == "0" ]]
[[ "$ATTACHMENT_COUNT" == "0" ]]
[[ "$MATERIAL_COUNT" == "0" ]]
[[ "$UPLOAD_SESSION_COUNT" == "0" ]]
[[ "$EXTERNAL_COMMAND_COUNT" == "0" ]]
[[ "$EXTERNAL_BINDING_COUNT" == "0" ]]
[[ "$WECOM_PENDING_COUNT" == "0" ]]
[[ "$WECOM_GROUP_COUNT" == "0" ]]
[[ "$UPLOAD_FILE_COUNT" == "0" ]]
[[ "$RELEASE_COUNT" -ge "1" ]]
[[ "$RELEASE_FILE_COUNT" -ge "1" ]]
[[ -n "$LATEST_BACKUP" ]]
gzip -t "$LATEST_BACKUP/fault_reporter_db.sql.gz"
tar -tzf "$LATEST_BACKUP/uploads.tar.gz" >/dev/null
(cd "$LATEST_BACKUP" && sha256sum -c SHA256SUMS)
! kubectl -n "$NAMESPACE" get secret e-report-default-test-user >/dev/null 2>&1
kubectl -n "$NAMESPACE" get secret e-report-external-integrations >/dev/null
kubectl -n "$NAMESPACE" rollout status deploy/e-report-backend --timeout=30s
BACKEND_POD="$(kubectl -n "$NAMESPACE" get pod -l app=e-report-backend --field-selector=status.phase=Running -o jsonpath='{{.items[0].metadata.name}}')"
RECENT_LOGS="$(kubectl -n "$NAMESPACE" logs "$BACKEND_POD" --since=10m)"
if grep -Eq ' ERROR |FATAL|Exception' <<<"$RECENT_LOGS"; then
  echo "Backend logs contain an error after production cleanup." >&2
  grep -E ' ERROR |FATAL|Exception' <<<"$RECENT_LOGS" >&2
  exit 1
fi
if grep -q 'DEBUG org.hibernate.SQL' <<<"$RECENT_LOGS"; then
  echo "Hibernate SQL debug logging is still enabled in production." >&2
  exit 1
fi
echo "=== backend secret references ==="
kubectl -n "$NAMESPACE" get deploy e-report-backend \
  -o jsonpath='{{range .spec.template.spec.containers[0].env[*]}}{{.name}}{{"="}}{{.valueFrom.secretKeyRef.name}}{{"/"}}{{.valueFrom.secretKeyRef.key}}{{";"}}{{end}}' \
  | grep -E 'SPRING_DATASOURCE_PASSWORD|APP_DEFAULT_ADMIN|AMAP_API_KEY|AUTH_TOKEN_SECRET'
kubectl -n "$NAMESPACE" get deploy,pod -o wide
echo "latest_backup=$LATEST_BACKUP"
echo "Production data verification passed."
"""


def main() -> None:
    args = parse_args()
    deploy_module = load_deploy_module()
    scripts = {
        "inspect": inspect_script,
        "execute": lambda: execute_script(args.admin_username),
        "verify": lambda: verify_script(args.admin_username),
    }
    client = deploy_module.connect()
    try:
        deploy_module.run(client, scripts[args.action]())
    finally:
        client.close()


if __name__ == "__main__":
    main()
