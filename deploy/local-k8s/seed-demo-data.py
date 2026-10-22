#!/usr/bin/env python3
"""Seed repeatable demo work orders for the single-node local K8S deployment."""

from __future__ import annotations

import argparse
import base64
import random
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path


NAMESPACE = "e-report"
MYSQL_RESOURCE = "deploy/e-mysql"
MYSQL_DATABASE = "fault_reporter_db"
DEMO_PREFIX = "[模拟数据]"
DEFAULT_COUNT = 1000


@dataclass(frozen=True)
class Area:
    township: str
    village_or_street: str
    lng: float
    lat: float


@dataclass(frozen=True)
class Asset:
    stored_name: str
    original_name: str
    title: str
    accent: str
    foreground: str


ASSETS = [
    Asset("demo-optical-cable-damage.svg", "光缆外皮破损现场.svg", "光缆外皮破损", "#2563eb", "#dbeafe"),
    Asset("demo-fiber-break.svg", "光纤中断告警现场.svg", "光纤中断告警", "#dc2626", "#fee2e2"),
    Asset("demo-splitter-port.svg", "分光器端口异常现场.svg", "分光器端口异常", "#7c3aed", "#ede9fe"),
    Asset("demo-box-door-open.svg", "分光箱门未关现场.svg", "分光箱门未关", "#0F766E", "#F0FDFA"),
    Asset("demo-damaged-box-replacement.svg", "破损箱体更换现场.svg", "破损箱体更换", "#4338CA", "#EEF2FF"),
    Asset("demo-water-ingress.svg", "光交箱进水现场.svg", "光交箱进水", "#0891b2", "#cffafe"),
]

AREAS = [
    Area("东山街道", "上元大街", 118.8421, 31.9538),
    Area("秣陵街道", "双龙大道", 118.8202, 31.8895),
    Area("淳化街道", "土桥社区", 118.9564, 31.9128),
    Area("禄口街道", "空港社区", 118.8726, 31.7362),
    Area("汤山街道", "汤泉社区", 119.0608, 32.0524),
    Area("横溪街道", "丹阳社区", 118.7423, 31.7164),
    Area("麒麟街道", "麒麟门社区", 118.9147, 32.0439),
    Area("江宁街道", "陆郎社区", 118.6249, 31.8397),
    Area("谷里街道", "周村社区", 118.7375, 31.8722),
    Area("湖熟街道", "湖熟社区", 118.9418, 31.8246),
    Area("高新园", "方山社区", 118.8334, 31.9089),
    Area("滨江开发区", "盛安大道", 118.5948, 31.8792),
]

NETWORK_FAULT_REASONS = [
    ("光缆", "主干光缆疑似被施工挖断，影响周边基站传输链路。", "熔接修复并加装警示标识，传输光功率恢复正常。"),
    ("光缆", "光缆外皮破损裸露，存在雨水渗入和后续中断风险。", "更换破损护套并完成防水包封。"),
    ("光纤", "入户光纤弯折半径过小，现场测试衰耗偏高。", "重新布放尾纤并复测通过。"),
    ("光纤", "纤芯标签缺失，巡检无法快速定位业务路由。", "补齐纤芯标签并同步台账。"),
    ("分光器", "分光器端口衰耗异常，多户宽带业务间歇掉线。", "更换异常端口并完成光功率复测。"),
    ("分光器", "分光器盘纤混乱，尾纤压迫导致接头松动。", "整理盘纤并固定连接器。"),
    ("光交箱", "光交箱底部进水，存在跳纤接头受潮风险。", "清理积水并加固密封条。"),
    ("分光箱体", "分光箱门锁松动，门体晃动影响设备保护。", "加固门锁并更换老化螺丝。"),
]

BOX_DOOR_OPEN_REASONS = [
    ("分光箱体", "巡检发现分光箱门未关闭，内部尾纤裸露在外。", "已关闭箱门并检查门锁状态。"),
    ("光交箱", "道路旁光交箱箱门未关，箱内跳纤存在被拉扯风险。", "已关箱并复核门锁闭合。"),
    ("分光箱体", "居民区楼道分光箱未上锁，现场存在误触风险。", "已上锁并补拍回单照片。"),
    ("户外箱体", "雨后巡检发现箱门虚掩，内部湿度较高。", "已关箱并擦拭内部水汽。"),
]

DAMAGED_REASONS = [
    ("分光箱体", "分光箱箱体变形，箱门无法正常闭合，需更换箱体。", "已完成箱体更换，尾纤整理后业务恢复。"),
    ("光交箱", "光交箱铰链断裂，门板脱落，现场需整体换箱。", "已更换门体和铰链，复测无异常。"),
    ("户外箱体", "箱体底座锈蚀严重，存在倾倒风险，需安排换箱。", "已更换底座并重新固定箱体。"),
    ("分纤箱", "分纤箱面板破裂，雨水可直接进入箱体。", "已更换分纤箱并完成防水封堵。"),
]

CONTACT_NAMES = [
    "王磊", "李娜", "陈伟", "刘洋", "张敏", "黄杰", "周强", "吴静", "赵鹏", "孙涛",
    "郑凯", "许晨", "唐俊", "冯越", "邵琳", "蒋峰", "朱宇", "高航", "林楠", "何亮",
]

RESOLUTION_METHODS = ["现场修复", "更换备件", "熔接处理", "关闭并加固", "更换箱体", "整理跳纤", "防水封堵"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Seed e-report demo work orders into local K8S MySQL.")
    parser.add_argument("--count", type=int, default=DEFAULT_COUNT, help="number of demo work orders to create")
    parser.add_argument("--namespace", default=NAMESPACE, help="Kubernetes namespace")
    parser.add_argument("--mysql-resource", default=MYSQL_RESOURCE, help="kubectl exec target for MySQL")
    parser.add_argument("--database", default=MYSQL_DATABASE, help="MySQL database name")
    parser.add_argument("--host-upload-dir", default="/data/e-report/uploads", help="hostPath directory mounted as backend upload dir")
    parser.add_argument("--keep-sql", type=Path, help="write generated SQL to this path instead of a temporary file")
    parser.add_argument("--dry-run", action="store_true", help="generate assets and SQL but do not import into MySQL")
    return parser.parse_args()


def run_command(command: list[str], *, input_text: str | None = None) -> str:
    completed = subprocess.run(
        command,
        input=input_text,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(
            "Command failed: "
            + " ".join(command)
            + "\nSTDOUT:\n"
            + completed.stdout
            + "\nSTDERR:\n"
            + completed.stderr
        )
    return completed.stdout


def read_mysql_password(namespace: str) -> str:
    encoded = run_command([
        "kubectl",
        "-n",
        namespace,
        "get",
        "secret",
        "e-report-mysql",
        "-o",
        "jsonpath={.data.MYSQL_ROOT_PASSWORD}",
    ]).strip()
    if not encoded:
        raise RuntimeError("Secret e-report-mysql does not contain MYSQL_ROOT_PASSWORD.")
    return base64.b64decode(encoded).decode("utf-8")


def sql_string(value: str | None) -> str:
    if value is None:
        return "NULL"
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def sql_value(value: object) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, datetime):
        return sql_string(value.strftime("%Y-%m-%d %H:%M:%S"))
    return sql_string(str(value))


def build_asset_svg(asset: Asset) -> str:
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="960" height="640" viewBox="0 0 960 640">
  <rect width="960" height="640" fill="{asset.foreground}"/>
  <rect x="64" y="64" width="832" height="512" rx="24" fill="#ffffff" stroke="{asset.accent}" stroke-width="10"/>
  <rect x="118" y="132" width="724" height="88" rx="12" fill="{asset.accent}"/>
  <text x="480" y="188" text-anchor="middle" font-family="Arial, sans-serif" font-size="42" fill="#ffffff" font-weight="700">{asset.title}</text>
  <path d="M160 358 C260 278, 360 438, 460 358 S660 278, 800 358" fill="none" stroke="{asset.accent}" stroke-width="24" stroke-linecap="round"/>
  <circle cx="238" cy="358" r="34" fill="#ffffff" stroke="{asset.accent}" stroke-width="12"/>
  <circle cx="480" cy="358" r="34" fill="#ffffff" stroke="{asset.accent}" stroke-width="12"/>
  <circle cx="722" cy="358" r="34" fill="#ffffff" stroke="{asset.accent}" stroke-width="12"/>
  <rect x="230" y="462" width="500" height="40" rx="20" fill="{asset.foreground}" stroke="{asset.accent}" stroke-width="4"/>
  <text x="480" y="532" text-anchor="middle" font-family="Arial, sans-serif" font-size="24" fill="#334155">网络现场巡检示例图</text>
</svg>
"""


def ensure_demo_assets(upload_dir: Path) -> dict[str, int]:
    upload_dir.mkdir(parents=True, exist_ok=True)
    sizes: dict[str, int] = {}
    for asset in ASSETS:
        path = upload_dir / asset.stored_name
        path.write_text(build_asset_svg(asset), encoding="utf-8")
        sizes[asset.stored_name] = path.stat().st_size
    return sizes


def choose_status(index: int) -> str:
    slot = index % 20
    if slot < 9:
        return "待处理"
    if slot < 18:
        return "已解决"
    return "已关闭"


def choose_type(index: int) -> str:
    slot = index % 12
    if slot < 6:
        return "NETWORK_FAULT"
    if slot < 9:
        return "BOX_DOOR_OPEN"
    return "DAMAGED_BOX_REPLACEMENT"


def jitter_coordinate(base: float, rng: random.Random) -> float:
    return base + rng.uniform(-0.012, 0.012)


def record_parts(report_type: str, rng: random.Random) -> tuple[str, str, str]:
    if report_type == "BOX_DOOR_OPEN":
        return rng.choice(BOX_DOOR_OPEN_REASONS)
    if report_type == "DAMAGED_BOX_REPLACEMENT":
        return rng.choice(DAMAGED_REASONS)
    return rng.choice(NETWORK_FAULT_REASONS)


def replacement_result(report_type: str, status: str, handled_text: str) -> str | None:
    if report_type != "DAMAGED_BOX_REPLACEMENT":
        return None
    if status == "待处理":
        return "待现场确认换箱方案。"
    return handled_text


def build_sql(count: int, asset_sizes: dict[str, int]) -> str:
    rng = random.Random(20260702)
    now = datetime.now().replace(second=0, microsecond=0)
    sql: list[str] = [
        "SET NAMES utf8mb4;",
        "START TRANSACTION;",
        "SET @demo_reporter_id = (SELECT id FROM users WHERE username = 'test' LIMIT 1);",
        "SET @demo_resolver_id = (SELECT id FROM users WHERE username = 'admin' LIMIT 1);",
        "SET @demo_reporter_id = COALESCE(@demo_reporter_id, @demo_resolver_id);",
        "SET @demo_resolver_id = COALESCE(@demo_resolver_id, @demo_reporter_id);",
        "DELETE a FROM attachments a JOIN fault_reports f ON a.fault_report_id = f.id WHERE f.reason LIKE '[模拟数据]%';",
        "DELETE FROM fault_reports WHERE reason LIKE '[模拟数据]%';",
    ]

    for index in range(count):
        report_type = choose_type(index)
        status = choose_status(index)
        area = AREAS[index % len(AREAS)]
        asset_name, reason_text, handled_text = record_parts(report_type, rng)
        contact = CONTACT_NAMES[index % len(CONTACT_NAMES)]
        report_time = now - timedelta(days=rng.randint(0, 89), hours=rng.randint(0, 23), minutes=rng.randint(0, 59))
        resolution_time = None if status == "待处理" else report_time + timedelta(hours=rng.randint(3, 72), minutes=rng.randint(0, 45))
        last_updated_time = resolution_time or (report_time + timedelta(hours=rng.randint(1, 12)))
        lng = jitter_coordinate(area.lng, rng)
        lat = jitter_coordinate(area.lat, rng)
        gps_coordinates = f"{lng:.6f}, {lat:.6f}"
        site_no = f"{1000 + index:04d}"
        location = f"{area.township}{area.village_or_street}网络点{site_no} {asset_name}"
        reason = f"{DEMO_PREFIX}{reason_text} 现场对象：{asset_name}；影响范围：{rng.randint(4, 180)}户或{rng.randint(1, 6)}个小区接入点。"
        box_quantity = rng.randint(1, 5) if report_type == "BOX_DOOR_OPEN" else rng.randint(1, 3)
        replacement_required = report_type == "DAMAGED_BOX_REPLACEMENT"
        replacement_completed = None
        if report_type == "DAMAGED_BOX_REPLACEMENT":
            replacement_completed = status != "待处理"
        resolution_method = None if status == "待处理" else rng.choice(RESOLUTION_METHODS)
        resolution_details = None if status == "待处理" else handled_text
        phone = "139" + f"{(36000000 + index * 37) % 100000000:08d}"
        asset = ASSETS[index % len(ASSETS)]
        should_attach = index % 3 != 1

        values = [
            location,
            gps_coordinates,
            area.township,
            area.village_or_street,
            reason,
            phone,
            f"装维人员-{contact}",
            status,
            report_type,
            box_quantity,
            replacement_required,
            replacement_completed,
            replacement_result(report_type, status, handled_text),
            report_time,
            last_updated_time,
            resolution_details,
            resolution_method,
            resolution_time,
            "@demo_reporter_id",
            "NULL" if status == "待处理" else "@demo_resolver_id",
        ]
        columns = [
            "location",
            "gps_coordinates",
            "township",
            "village_or_street",
            "reason",
            "phone",
            "contact_person",
            "status",
            "report_type",
            "box_quantity",
            "replacement_required",
            "replacement_completed",
            "replacement_result",
            "report_time",
            "last_updated_time",
            "resolution_details",
            "resolution_method",
            "resolution_time",
            "reported_by_user_id",
            "resolved_by_user_id",
        ]
        rendered_values = [
            item if isinstance(item, str) and item.startswith("@") or item == "NULL" else sql_value(item)
            for item in values
        ]
        sql.append(f"INSERT INTO fault_reports ({', '.join(columns)}) VALUES ({', '.join(rendered_values)});")
        if should_attach:
            sql.append("SET @fault_report_id = LAST_INSERT_ID();")
            sql.append(
                "INSERT INTO attachments (file_name, stored_file_name, file_type, file_size, file_path, fault_report_id) "
                f"VALUES ({sql_string(asset.original_name)}, {sql_string(asset.stored_name)}, 'image/svg+xml', "
                f"{asset_sizes[asset.stored_name]}, {sql_string(asset.stored_name)}, @fault_report_id);"
            )

    sql.extend([
        "COMMIT;",
        "SELECT COUNT(*) AS demo_total FROM fault_reports WHERE reason LIKE '[模拟数据]%';",
        "SELECT report_type, status, COUNT(*) AS total FROM fault_reports WHERE reason LIKE '[模拟数据]%' GROUP BY report_type, status ORDER BY report_type, status;",
        "SELECT COUNT(*) AS demo_attachment_total FROM attachments a JOIN fault_reports f ON a.fault_report_id = f.id WHERE f.reason LIKE '[模拟数据]%';",
    ])
    return "\n".join(sql) + "\n"


def write_sql(sql_text: str, keep_sql: Path | None) -> Path:
    if keep_sql:
        keep_sql.parent.mkdir(parents=True, exist_ok=True)
        keep_sql.write_text(sql_text, encoding="utf-8")
        return keep_sql

    handle = tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".sql", delete=False)
    with handle:
        handle.write(sql_text)
    return Path(handle.name)


def import_sql(namespace: str, mysql_resource: str, database: str, password: str, sql_path: Path) -> str:
    sql_text = sql_path.read_text(encoding="utf-8")
    return run_command([
        "kubectl",
        "-n",
        namespace,
        "exec",
        "-i",
        mysql_resource,
        "--",
        "mysql",
        "-uroot",
        f"-p{password}",
        database,
    ], input_text=sql_text)


def main() -> int:
    args = parse_args()
    if args.count < 1:
        raise ValueError("--count must be positive.")

    upload_dir = Path(args.host_upload_dir)
    asset_sizes = ensure_demo_assets(upload_dir)
    sql_text = build_sql(args.count, asset_sizes)
    sql_path = write_sql(sql_text, args.keep_sql)

    print(f"Generated {args.count} demo work orders.")
    print(f"Demo assets: {upload_dir}")
    print(f"SQL file: {sql_path}")

    if args.dry_run:
        print("Dry run enabled; MySQL import skipped.")
        return 0

    password = read_mysql_password(args.namespace)
    output = import_sql(args.namespace, args.mysql_resource, args.database, password, sql_path)
    print(output.strip())
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)
