#!/usr/bin/env python3
"""Backup, verify and remove the reserved historical E2E dataset on the VPS."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from typing import Any


NAMESPACE = "e-report"
DATABASE_NAME = "fault_reporter_db"
CHANGESET_ID = "2026-08-10-019-remove-production-test-data"
BACKUP_ROOT = Path("/data/e-report/backups/v1.0.39-test-cleanup")
UPLOAD_ROOT = Path("/data/e-report/uploads")
FINALIZE_CONFIRMATION = "DELETE-RESERVED-E2E-DATA"
SAFE_UPLOAD_NAME = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?:\.[A-Za-z0-9]{1,20})?$")

RESERVED_TEST_DATA_BASELINE = {
    "test_users": 70,
    "test_work_orders": 52,
    "test_attachments": 84,
    "test_upload_sessions": 88,
}

TEST_USER_FILTER = "(u.username LIKE 'e2e-%' OR u.username LIKE 'appqa-%' OR u.username LIKE 'video-smoke-%')"
TEST_REPORTER_FILTER = "(reporter.username LIKE 'e2e-%' OR reporter.username LIKE 'appqa-%' OR reporter.username LIKE 'video-smoke-%')"

INVENTORY_SQL = f"""
SELECT 'total_users', COUNT(*) FROM users
UNION ALL SELECT 'test_users', COUNT(*) FROM users u WHERE {TEST_USER_FILTER}
UNION ALL SELECT 'active_test_users', COUNT(*) FROM users u WHERE {TEST_USER_FILTER} AND u.status <> 'DELETED'
UNION ALL SELECT 'total_work_orders', COUNT(*) FROM fault_reports
UNION ALL SELECT 'test_work_orders', COUNT(*) FROM fault_reports f JOIN users reporter ON reporter.id=f.reported_by_user_id WHERE {TEST_REPORTER_FILTER}
UNION ALL SELECT 'nonterminal_test_work_orders', COUNT(*) FROM fault_reports f JOIN users reporter ON reporter.id=f.reported_by_user_id WHERE {TEST_REPORTER_FILTER} AND f.status_code NOT IN ('CLOSED','CANCELLED')
UNION ALL SELECT 'total_attachments', COUNT(*) FROM attachments
UNION ALL SELECT 'test_attachments', COUNT(*) FROM attachments a JOIN fault_reports f ON f.id=a.fault_report_id JOIN users reporter ON reporter.id=f.reported_by_user_id WHERE {TEST_REPORTER_FILTER}
UNION ALL SELECT 'total_upload_sessions', COUNT(*) FROM file_transfer_upload_sessions
UNION ALL SELECT 'test_upload_sessions', COUNT(DISTINCT upload_session.id) FROM file_transfer_upload_sessions upload_session LEFT JOIN users u ON u.id=upload_session.created_by_membership_id WHERE {TEST_USER_FILTER} OR upload_session.created_by_username LIKE 'e2e-%' OR upload_session.created_by_username LIKE 'appqa-%' OR upload_session.created_by_username LIKE 'video-smoke-%'
UNION ALL SELECT 'test_assignments', COUNT(*) FROM work_order_assignments assignment JOIN fault_reports f ON f.id=assignment.work_order_id JOIN users reporter ON reporter.id=f.reported_by_user_id WHERE {TEST_REPORTER_FILTER}
UNION ALL SELECT 'test_responses', COUNT(*) FROM work_order_responses response JOIN fault_reports f ON f.id=response.work_order_id JOIN users reporter ON reporter.id=f.reported_by_user_id WHERE {TEST_REPORTER_FILTER}
UNION ALL SELECT 'test_verifications', COUNT(*) FROM work_order_verifications verification JOIN fault_reports f ON f.id=verification.work_order_id JOIN users reporter ON reporter.id=f.reported_by_user_id WHERE {TEST_REPORTER_FILTER}
UNION ALL SELECT 'test_events', COUNT(*) FROM work_order_events event_row JOIN fault_reports f ON f.id=event_row.work_order_id JOIN users reporter ON reporter.id=f.reported_by_user_id WHERE {TEST_REPORTER_FILTER}
UNION ALL SELECT 'test_outbox_events', COUNT(*) FROM outbox_events outbox JOIN fault_reports f ON f.id=outbox.aggregate_id JOIN users reporter ON reporter.id=f.reported_by_user_id WHERE outbox.aggregate_type='WORK_ORDER' AND {TEST_REPORTER_FILTER}
UNION ALL SELECT 'test_role_bundles', COUNT(*) FROM role_bundles WHERE built_in=0 AND display_name LIKE 'E2E-%'
UNION ALL SELECT 'business_orders_with_test_participants', COUNT(DISTINCT f.id) FROM fault_reports f WHERE NOT EXISTS (SELECT 1 FROM users reporter WHERE reporter.id=f.reported_by_user_id AND (reporter.username LIKE 'e2e-%' OR reporter.username LIKE 'appqa-%' OR reporter.username LIKE 'video-smoke-%')) AND (EXISTS (SELECT 1 FROM users participant WHERE participant.id IN (f.resolved_by_user_id,f.closed_by_user_id) AND (participant.username LIKE 'e2e-%' OR participant.username LIKE 'appqa-%' OR participant.username LIKE 'video-smoke-%')) OR EXISTS (SELECT 1 FROM work_order_assignments a JOIN users participant ON participant.id IN (a.assignee_membership_id,a.assigned_by_membership_id) WHERE a.work_order_id=f.id AND (participant.username LIKE 'e2e-%' OR participant.username LIKE 'appqa-%' OR participant.username LIKE 'video-smoke-%')) OR EXISTS (SELECT 1 FROM work_order_responses r JOIN users participant ON participant.id=r.submitted_by_membership_id WHERE r.work_order_id=f.id AND (participant.username LIKE 'e2e-%' OR participant.username LIKE 'appqa-%' OR participant.username LIKE 'video-smoke-%')) OR EXISTS (SELECT 1 FROM work_order_verifications v JOIN users participant ON participant.id=v.verified_by_membership_id WHERE v.work_order_id=f.id AND (participant.username LIKE 'e2e-%' OR participant.username LIKE 'appqa-%' OR participant.username LIKE 'video-smoke-%')) OR EXISTS (SELECT 1 FROM work_order_events e JOIN users participant ON participant.id=e.operator_membership_id WHERE e.work_order_id=f.id AND (participant.username LIKE 'e2e-%' OR participant.username LIKE 'appqa-%' OR participant.username LIKE 'video-smoke-%')))
""".strip()

FILE_NAMES_SQL = f"""
SELECT DISTINCT candidate.file_name
FROM (
    SELECT a.stored_file_name AS file_name
    FROM attachments a
    JOIN fault_reports f ON f.id=a.fault_report_id
    JOIN users reporter ON reporter.id=f.reported_by_user_id
    WHERE {TEST_REPORTER_FILTER}
    UNION
    SELECT a.file_path AS file_name
    FROM attachments a
    JOIN fault_reports f ON f.id=a.fault_report_id
    JOIN users reporter ON reporter.id=f.reported_by_user_id
    WHERE {TEST_REPORTER_FILTER}
    UNION
    SELECT upload_session.stored_file_name AS file_name
    FROM file_transfer_upload_sessions upload_session
    LEFT JOIN users u ON u.id=upload_session.created_by_membership_id
    WHERE upload_session.purpose='WORK_ORDER_ATTACHMENT'
      AND ({TEST_USER_FILTER} OR upload_session.created_by_username LIKE 'e2e-%' OR upload_session.created_by_username LIKE 'appqa-%' OR upload_session.created_by_username LIKE 'video-smoke-%')
) candidate
WHERE candidate.file_name IS NOT NULL AND candidate.file_name <> ''
ORDER BY candidate.file_name
""".strip()


def run(command: list[str], *, capture_output: bool = True, text: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(command, check=True, capture_output=capture_output, text=text)


def kubectl_mysql(sql: str) -> str:
    command = [
        "kubectl", "-n", NAMESPACE, "exec", "deploy/e-mysql", "--",
        "env", f"E_REPORT_SQL={sql}", "sh", "-lc",
        'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --batch --raw --skip-column-names '
        f'{DATABASE_NAME} -e "$E_REPORT_SQL"',
    ]
    return run(command).stdout


def inventory() -> dict[str, int]:
    rows: dict[str, int] = {}
    for line in kubectl_mysql(INVENTORY_SQL).splitlines():
        key, value = line.split("\t", 1)
        rows[key] = int(value)
    return rows


def assert_safe_inventory(rows: dict[str, int], *, enforce_baseline: bool) -> None:
    unsafe = {
        "active_test_users": rows.get("active_test_users", -1),
        "nonterminal_test_work_orders": rows.get("nonterminal_test_work_orders", -1),
        "business_orders_with_test_participants": rows.get("business_orders_with_test_participants", -1),
    }
    failures = {key: value for key, value in unsafe.items() if value != 0}
    if failures:
        raise RuntimeError(f"测试数据安全前置条件不满足：{failures}")
    if enforce_baseline:
        drift = {
            key: {"expected": expected, "actual": rows.get(key)}
            for key, expected in RESERVED_TEST_DATA_BASELINE.items()
            if rows.get(key) != expected
        }
        if drift:
            raise RuntimeError(f"保留测试数据集合已漂移，拒绝继续：{drift}")


def sha256_file(file_path: Path) -> str:
    digest = hashlib.sha256()
    with file_path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def resolve_under(root: Path, child: str) -> Path:
    if not SAFE_UPLOAD_NAME.fullmatch(child):
        raise RuntimeError(f"待清理文件名不符合 UUID 白名单：{child}")
    resolved_root = root.resolve(strict=True)
    resolved_child = (resolved_root / child).resolve(strict=False)
    if resolved_child.parent != resolved_root:
        raise RuntimeError(f"待清理文件越出上传目录：{child}")
    return resolved_child


def candidate_files(upload_root: Path) -> list[dict[str, Any]]:
    entries: list[dict[str, Any]] = []
    for file_name in filter(None, kubectl_mysql(FILE_NAMES_SQL).splitlines()):
        target = resolve_under(upload_root, file_name)
        exists = target.is_file()
        entries.append({
            "file_name": file_name,
            "exists": exists,
            "size": target.stat().st_size if exists else 0,
            "sha256": sha256_file(target) if exists else None,
        })
    return entries


def create_database_backup(target: Path) -> None:
    temporary = target.with_suffix(target.suffix + ".tmp")
    command = [
        "kubectl", "-n", NAMESPACE, "exec", "deploy/e-mysql", "--", "sh", "-lc",
        'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --quick '
        '--routines --events --triggers --set-gtid-purged=OFF --no-tablespaces '
        f'--databases {DATABASE_NAME}',
    ]
    with temporary.open("wb") as output:
        subprocess.run(command, check=True, stdout=output)
    temporary.replace(target)


def create_upload_backup(upload_root: Path, target: Path) -> None:
    temporary = target.with_suffix(target.suffix + ".tmp")
    run([
        "tar", "--create", "--gzip", "--file", str(temporary),
        "--directory", str(upload_root.parent), upload_root.name,
    ])
    temporary.replace(target)


def write_json_atomic(target: Path, payload: dict[str, Any]) -> None:
    temporary = target.with_suffix(target.suffix + ".tmp")
    temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(target)


def prepare(args: argparse.Namespace) -> None:
    rows = inventory()
    assert_safe_inventory(rows, enforce_baseline=not args.skip_baseline)
    stamp = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).strftime("%Y%m%d-%H%M%S")
    target_directory = BACKUP_ROOT / stamp
    target_directory.mkdir(parents=True, exist_ok=False)
    database_backup = target_directory / f"{DATABASE_NAME}-{stamp}.sql"
    upload_backup = target_directory / f"uploads-{stamp}.tar.gz"
    manifest_path = target_directory / "manifest.json"

    files = candidate_files(UPLOAD_ROOT)
    create_database_backup(database_backup)
    create_upload_backup(UPLOAD_ROOT, upload_backup)
    payload = {
        "schema_version": 1,
        "prepared_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "changeset_id": CHANGESET_ID,
        "inventory_before": rows,
        "expected_after": {
            "total_users": rows["total_users"] - rows["test_users"],
            "total_work_orders": rows["total_work_orders"] - rows["test_work_orders"],
            "total_attachments": rows["total_attachments"] - rows["test_attachments"],
            "total_upload_sessions": rows["total_upload_sessions"] - rows["test_upload_sessions"],
        },
        "database_backup": {"path": str(database_backup), "sha256": sha256_file(database_backup)},
        "upload_backup": {"path": str(upload_backup), "sha256": sha256_file(upload_backup)},
        "files": files,
    }
    write_json_atomic(manifest_path, payload)
    print(json.dumps({
        "prepared": True,
        "manifest": str(manifest_path),
        "candidate_files": len(files),
        "candidate_file_bytes": sum(entry["size"] for entry in files),
        "inventory": rows,
    }, ensure_ascii=False, indent=2))


def load_manifest(path_value: str) -> tuple[Path, dict[str, Any]]:
    manifest_path = Path(path_value).resolve(strict=True)
    backup_root = BACKUP_ROOT.resolve(strict=True)
    if backup_root not in manifest_path.parents:
        raise RuntimeError("manifest 必须位于版本专用备份目录内。")
    return manifest_path, json.loads(manifest_path.read_text(encoding="utf-8"))


def assert_backup_integrity(manifest: dict[str, Any]) -> None:
    for key in ("database_backup", "upload_backup"):
        backup = Path(manifest[key]["path"])
        if not backup.is_file() or sha256_file(backup) != manifest[key]["sha256"]:
            raise RuntimeError(f"备份完整性校验失败：{backup}")


def assert_changeset_applied() -> None:
    sql = (
        "SELECT COUNT(*) FROM DATABASECHANGELOG "
        f"WHERE ID='{CHANGESET_ID}' AND AUTHOR='e-report'"
    )
    if int(kubectl_mysql(sql).strip() or "0") != 1:
        raise RuntimeError(f"Liquibase changeset {CHANGESET_ID} 尚未执行。")


def assert_after_inventory(rows: dict[str, int], manifest: dict[str, Any]) -> None:
    residual_keys = [
        "test_users", "test_work_orders", "test_attachments", "test_upload_sessions",
        "test_assignments", "test_responses", "test_verifications", "test_events",
        "test_outbox_events", "test_role_bundles", "business_orders_with_test_participants",
    ]
    residuals = {key: rows.get(key) for key in residual_keys if rows.get(key) != 0}
    if residuals:
        raise RuntimeError(f"Liquibase 清理后仍存在测试数据：{residuals}")
    expected = manifest["expected_after"]
    drift = {
        key: {"expected": value, "actual": rows.get(key)}
        for key, value in expected.items()
        if rows.get(key) != value
    }
    if drift:
        raise RuntimeError(f"业务数据保留计数异常：{drift}")


def finalize(args: argparse.Namespace) -> None:
    if args.confirm != FINALIZE_CONFIRMATION:
        raise RuntimeError(f"必须提供 --confirm {FINALIZE_CONFIRMATION}。")
    manifest_path, manifest = load_manifest(args.manifest)
    assert_backup_integrity(manifest)
    assert_changeset_applied()
    rows = inventory()
    assert_after_inventory(rows, manifest)

    deleted_files = 0
    deleted_bytes = 0
    for entry in manifest["files"]:
        target = resolve_under(UPLOAD_ROOT, entry["file_name"])
        if not target.exists():
            continue
        if not target.is_file():
            raise RuntimeError(f"清理目标不是普通文件：{target}")
        actual_sha256 = sha256_file(target)
        if entry["sha256"] and actual_sha256 != entry["sha256"]:
            raise RuntimeError(f"文件内容在备份后发生变化，拒绝删除：{target.name}")
        deleted_bytes += target.stat().st_size
        target.unlink()
        deleted_files += 1

    run([
        "kubectl", "-n", NAMESPACE, "exec", "deploy/e-report-redis", "--",
        "sh", "-lc", 'redis-cli -a "$REDIS_PASSWORD" FLUSHDB >/dev/null',
    ])
    manifest["finalized_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
    manifest["inventory_after"] = rows
    manifest["deleted_files"] = deleted_files
    manifest["deleted_file_bytes"] = deleted_bytes
    write_json_atomic(manifest_path, manifest)
    print(json.dumps({
        "finalized": True,
        "manifest": str(manifest_path),
        "deleted_files": deleted_files,
        "deleted_file_bytes": deleted_bytes,
        "inventory": rows,
    }, ensure_ascii=False, indent=2))


def verify(args: argparse.Namespace) -> None:
    manifest_path, manifest = load_manifest(args.manifest)
    assert_backup_integrity(manifest)
    assert_changeset_applied()
    rows = inventory()
    assert_after_inventory(rows, manifest)
    remaining_files = [
        entry["file_name"]
        for entry in manifest["files"]
        if resolve_under(UPLOAD_ROOT, entry["file_name"]).exists()
    ]
    if remaining_files:
        raise RuntimeError(f"仍存在 {len(remaining_files)} 个已登记测试文件。")
    print(json.dumps({"verified": True, "manifest": str(manifest_path), "inventory": rows}, ensure_ascii=False, indent=2))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="1.0.39 历史 E2E 数据备份与受限清理工具")
    subparsers = parser.add_subparsers(dest="action", required=True)
    subparsers.add_parser("inspect", help="只读输出测试数据盘点")
    prepare_parser = subparsers.add_parser("prepare", help="校验基线并备份数据库、上传目录和文件清单")
    prepare_parser.add_argument("--skip-baseline", action="store_true", help="仅用于演练，不校验已确认的测试集合数量")
    finalize_parser = subparsers.add_parser("finalize", help="Liquibase 成功后删除清单文件并清理 Redis")
    finalize_parser.add_argument("--manifest", required=True)
    finalize_parser.add_argument("--confirm", required=True)
    verify_parser = subparsers.add_parser("verify", help="只读复核数据库、备份和文件清理结果")
    verify_parser.add_argument("--manifest", required=True)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.action == "inspect":
        print(json.dumps(inventory(), ensure_ascii=False, indent=2))
    elif args.action == "prepare":
        prepare(args)
    elif args.action == "finalize":
        finalize(args)
    elif args.action == "verify":
        verify(args)
    else:
        raise AssertionError(f"未知操作：{args.action}")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, OSError, ValueError) as error:
        print(f"[失败] {error}", file=sys.stderr)
        raise SystemExit(1) from error
