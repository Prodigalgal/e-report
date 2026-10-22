#!/usr/bin/env python3
import argparse
import hashlib
import http.cookiejar
import json
import math
import os
import random
import re
import statistics
import string
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from typing import Callable


NOTIFICATION_CENTER_KEY = "notification.center.enabled"


@dataclass(frozen=True)
class Sample:
    ok: bool
    status: int
    elapsed_ms: float
    bytes_read: int
    error: str = ""


class ApiClient:
    def __init__(self, base_url: str, timeout: float = 15.0):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def request(
        self,
        method: str,
        path: str,
        *,
        cookie_header: str = "",
        json_body=None,
        body: bytes | None = None,
        content_type: str | None = None,
        expected_statuses: set[int] | None = None,
        extra_headers: dict[str, str] | None = None,
    ) -> tuple[Sample, bytes]:
        url = self.base_url + path
        headers = {"Accept": "application/json"}
        if cookie_header:
            headers["Cookie"] = cookie_header
        if extra_headers:
            headers.update(extra_headers)

        data = body
        if json_body is not None:
            data = json.dumps(json_body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json; charset=utf-8"
        elif content_type:
            headers["Content-Type"] = content_type

        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        started = time.perf_counter()
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                response_body = response.read()
                elapsed_ms = (time.perf_counter() - started) * 1000
                status = response.status
        except urllib.error.HTTPError as exc:
            response_body = exc.read()
            elapsed_ms = (time.perf_counter() - started) * 1000
            status = exc.code
        except Exception as exc:
            elapsed_ms = (time.perf_counter() - started) * 1000
            return Sample(False, 0, elapsed_ms, 0, str(exc)), b""

        expected = expected_statuses or set(range(200, 300))
        return Sample(status in expected, status, elapsed_ms, len(response_body)), response_body

    def request_json(self, method: str, path: str, **kwargs):
        sample, body = self.request(method, path, **kwargs)
        if not sample.ok:
            raise RuntimeError(f"{method} {path} failed: status={sample.status}, body={body[:500]!r}, error={sample.error}")
        if not body:
            return None
        return json.loads(body.decode("utf-8"))


def solve_captcha(question: str) -> str:
    match = re.search(r"(\d+)\s*([+-])\s*(\d+)", question)
    if not match:
        raise ValueError(f"Unsupported captcha question: {question}")
    left = int(match.group(1))
    right = int(match.group(3))
    answer = left + right if match.group(2) == "+" else left - right
    return str(answer)


def solve_pow(challenge_id: str, pow_prefix: str, difficulty: int) -> str:
    target = "0" * difficulty
    nonce = 0
    while True:
        candidate = str(nonce)
        payload = f"{challenge_id}:{pow_prefix}:{candidate}".encode("utf-8")
        if hashlib.sha256(payload).hexdigest().startswith(target):
            return candidate
        nonce += 1


def web_auth_environment(client_instance_id: str) -> dict:
    fingerprint_hash = hashlib.sha256(
        f"e-report-perf-web-v1:{client_instance_id}".encode("utf-8")
    ).hexdigest()
    return {
        "schemaVersion": 1,
        "fingerprintHash": fingerprint_hash,
        "browserName": "Python smoke client",
        "browserVersion": f"{sys.version_info.major}.{sys.version_info.minor}",
        "osName": sys.platform,
        "osVersion": "",
        "deviceName": "只读压测客户端",
        "manufacturer": "",
        "model": "",
        "localeTag": "zh-CN",
        "timeZone": "Asia/Shanghai",
        "appVersion": "performance-test",
        "securitySignals": [],
    }


def auth_challenge_payload(username: str, client_instance_id: str, web_environment: dict) -> dict:
    return {
        "username": username,
        "clientType": "WEB",
        "clientInstanceId": client_instance_id,
        "webFingerprintHash": web_environment["fingerprintHash"],
        "webSecuritySignals": web_environment["securitySignals"],
    }


def login(client: ApiClient, username: str, password: str) -> str:
    cookie_jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookie_jar))

    client_instance_id = str(uuid.uuid4())
    web_environment = web_auth_environment(client_instance_id)
    challenge_url = client.base_url + "/auth/challenge"
    challenge_request = urllib.request.Request(
        challenge_url,
        data=json.dumps(auth_challenge_payload(username, client_instance_id, web_environment)).encode("utf-8"),
        headers={"Content-Type": "application/json; charset=utf-8", "Accept": "application/json"},
        method="POST",
    )
    with opener.open(challenge_request, timeout=client.timeout) as response:
        challenge = json.loads(response.read().decode("utf-8"))

    payload = {
        "username": username,
        "password": password,
        "challengeId": challenge["challengeId"],
        "captchaAnswer": solve_captcha(challenge["captchaQuestion"]) if challenge.get("captchaRequired") else None,
        "powNonce": solve_pow(challenge["challengeId"], challenge["powPrefix"], int(challenge["powDifficulty"])),
        "clientType": "WEB",
        "clientInstanceId": client_instance_id,
        "webEnvironment": web_environment,
        "rememberMe": False,
    }
    request = urllib.request.Request(
        client.base_url + "/auth/login",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json; charset=utf-8", "Accept": "application/json"},
        method="POST",
    )
    with opener.open(request, timeout=client.timeout) as response:
        response.read()

    cookies = [f"{cookie.name}={cookie.value}" for cookie in cookie_jar]
    if not cookies:
        raise RuntimeError(f"Login succeeded but no cookie was issued for {username}.")
    return "; ".join(cookies)


def quote_path(value: str) -> str:
    return urllib.parse.quote(value, safe="")


def get_config(client: ApiClient, admin_cookie: str, key: str) -> str | None:
    sample, body = client.request(
        "GET",
        f"/admin/configs/{quote_path(key)}",
        cookie_header=admin_cookie,
        expected_statuses={200, 404},
    )
    if sample.status == 404:
        return None
    if not sample.ok:
        raise RuntimeError(f"Failed to fetch config {key}: status={sample.status}")
    return json.loads(body.decode("utf-8")).get("configValue")


def set_config(client: ApiClient, admin_cookie: str, key: str, value: str, description: str) -> None:
    payload = {"configValue": value, "description": description}
    sample, _ = client.request(
        "PUT",
        f"/admin/configs/{quote_path(key)}",
        cookie_header=admin_cookie,
        json_body=payload,
        expected_statuses={200, 404},
    )
    if sample.status == 200:
        return
    create_payload = {
        "configKey": key,
        "configValue": value,
        "description": description,
        "sensitive": False,
        "displayValue": value,
    }
    sample, body = client.request(
        "POST",
        "/admin/configs",
        cookie_header=admin_cookie,
        json_body=create_payload,
        expected_statuses={201},
    )
    if not sample.ok:
        raise RuntimeError(f"Failed to create config {key}: status={sample.status}, body={body[:500]!r}")


def percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    if len(values) == 1:
        return values[0]
    sorted_values = sorted(values)
    rank = (len(sorted_values) - 1) * p
    lower = math.floor(rank)
    upper = math.ceil(rank)
    if lower == upper:
        return sorted_values[int(rank)]
    return sorted_values[lower] * (upper - rank) + sorted_values[upper] * (rank - lower)


def summarize(name: str, group: str, concurrency: int, samples: list[Sample], wall_seconds: float) -> dict:
    durations = [sample.elapsed_ms for sample in samples]
    ok_count = sum(1 for sample in samples if sample.ok)
    statuses: dict[str, int] = {}
    for sample in samples:
        key = str(sample.status) if sample.status else "ERR"
        statuses[key] = statuses.get(key, 0) + 1
    return {
        "group": group,
        "name": name,
        "concurrency": concurrency,
        "count": len(samples),
        "ok": ok_count,
        "fail": len(samples) - ok_count,
        "rps": round(len(samples) / max(wall_seconds, 0.001), 2),
        "avg_ms": round(statistics.mean(durations), 2) if durations else 0,
        "p50_ms": round(percentile(durations, 0.50), 2),
        "p90_ms": round(percentile(durations, 0.90), 2),
        "p95_ms": round(percentile(durations, 0.95), 2),
        "p99_ms": round(percentile(durations, 0.99), 2),
        "max_ms": round(max(durations), 2) if durations else 0,
        "statuses": statuses,
        "errors": sorted({sample.error for sample in samples if sample.error})[:5],
    }


def run_callable_benchmark(
    name: str,
    group: str,
    concurrency: int,
    iterations: int,
    call: Callable[[], Sample],
) -> dict:
    samples: list[Sample] = []
    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(call) for _ in range(iterations)]
        for future in as_completed(futures):
            samples.append(future.result())
    wall_seconds = time.perf_counter() - started
    return summarize(name, group, concurrency, samples, wall_seconds)


def run_http_benchmark(
    client: ApiClient,
    name: str,
    group: str,
    method: str,
    path: str,
    cookie_header: str,
    concurrency: int,
    iterations: int,
    expected_statuses: set[int] | None = None,
    json_body=None,
) -> dict:
    return run_callable_benchmark(
        name,
        group,
        concurrency,
        iterations,
        lambda: client.request(
            method,
            path,
            cookie_header=cookie_header,
            json_body=json_body,
            expected_statuses=expected_statuses,
        )[0],
    )


def build_fault_multipart(report_type: str = "NETWORK_FAULT") -> tuple[bytes, str]:
    boundary = "----e-report-perf-" + uuid.uuid4().hex
    suffix = "".join(random.choices(string.ascii_uppercase + string.digits, k=6))
    fault_data = {
        "location": f"[压测数据] 网络光缆巡检点 {suffix}",
        "gpsCoordinates": "118.842100,31.953800",
        "township": "压测乡镇",
        "villageOrStreet": "压测街道",
        "reason": "[压测数据] 光缆、光纤、分光器或分光箱体现场巡检异常。",
        "phone": "13000000095",
        "contactPerson": "压测用户",
        "reportType": report_type,
        "boxQuantity": 1 if report_type == "BOX_DOOR_OPEN" else None,
        "replacementRequired": report_type == "DAMAGED_BOX_REPLACEMENT",
    }
    fault_data = {key: value for key, value in fault_data.items() if value is not None}
    parts = [
        f"--{boundary}\r\n".encode("utf-8"),
        b'Content-Disposition: form-data; name="faultData"\r\n',
        b"Content-Type: application/json; charset=utf-8\r\n\r\n",
        json.dumps(fault_data, ensure_ascii=False).encode("utf-8"),
        b"\r\n",
        f"--{boundary}--\r\n".encode("utf-8"),
    ]
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def create_work_order(client: ApiClient, test_cookie: str) -> tuple[Sample, int | None]:
    report_type = random.choice(["NETWORK_FAULT", "BOX_DOOR_OPEN", "DAMAGED_BOX_REPLACEMENT"])
    body, content_type = build_fault_multipart(report_type)
    sample, response_body = client.request(
        "POST",
        "/work-orders",
        cookie_header=test_cookie,
        body=body,
        content_type=content_type,
        expected_statuses={201},
        extra_headers={"Accept": "application/json"},
    )
    if not sample.ok:
        return sample, None
    return sample, json.loads(response_body.decode("utf-8")).get("id")


def discover_samples(client: ApiClient, admin_cookie: str) -> dict:
    page = client.request_json("GET", "/work-orders/search?page=0&size=50", cookie_header=admin_cookie)
    content = page.get("content") or []
    sample_id = content[0]["id"] if content else None
    attachment = None
    candidate_items = content
    if sample_id:
        detail = client.request_json("GET", f"/work-orders/{sample_id}", cookie_header=admin_cookie)
        candidate_items = [detail, *content]
    for item in candidate_items:
        attachments = item.get("attachments") or []
        if attachments:
            attachment = {
                "faultReportId": item["id"],
                "attachmentId": attachments[0]["id"],
                "storedFileName": attachments[0]["storedFileName"],
            }
            break
    return {"work_order_id": sample_id, "attachment": attachment}


def print_summary(results: list[dict]) -> None:
    print("\n## API performance summary")
    print("| group | endpoint | c | n | ok/fail | rps | avg | p50 | p95 | p99 | max | statuses |")
    print("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|")
    for row in results:
        statuses = ",".join(f"{key}:{value}" for key, value in sorted(row["statuses"].items()))
        print(
            f"| {row['group']} | {row['name']} | {row['concurrency']} | {row['count']} | "
            f"{row['ok']}/{row['fail']} | {row['rps']} | {row['avg_ms']} | "
            f"{row['p50_ms']} | {row['p95_ms']} | {row['p99_ms']} | {row['max_ms']} | {statuses} |"
        )


def parse_concurrency_levels(raw_value: str) -> list[int]:
    levels = []
    for part in raw_value.split(","):
        part = part.strip()
        if part:
            levels.append(max(1, int(part)))
    return levels or [1]


def main() -> int:
    parser = argparse.ArgumentParser(description="Run e-report API performance tests against an internal backend URL.")
    parser.add_argument("--base-url", default=os.getenv("E_REPORT_API_BASE_URL", "http://127.0.0.1:8083/api"))
    parser.add_argument("--admin-username", default=os.getenv("DEFAULT_ADMIN_USERNAME", "admin"))
    parser.add_argument("--admin-password", default=os.getenv("DEFAULT_ADMIN_PASSWORD", ""))
    parser.add_argument(
        "--test-username",
        default=os.getenv("PERF_REPORTER_USERNAME", os.getenv("DEFAULT_ADMIN_USERNAME", "admin")),
    )
    parser.add_argument(
        "--test-password",
        default=os.getenv("PERF_REPORTER_PASSWORD", os.getenv("DEFAULT_ADMIN_PASSWORD", "")),
    )
    parser.add_argument("--iterations", type=int, default=80)
    parser.add_argument("--concurrency-levels", default="1,8,16")
    parser.add_argument("--external-iterations", type=int, default=5)
    parser.add_argument("--write-count", type=int, default=6)
    parser.add_argument("--include-writes", action="store_true")
    parser.add_argument("--keep-notifications", action="store_true")
    parser.add_argument("--output", default="")
    args = parser.parse_args()
    if not args.admin_password:
        parser.error("--admin-password or DEFAULT_ADMIN_PASSWORD is required")
    if not args.test_password:
        parser.error("--test-password, PERF_REPORTER_PASSWORD, or DEFAULT_ADMIN_PASSWORD is required")

    client = ApiClient(args.base_url)
    concurrency_levels = parse_concurrency_levels(args.concurrency_levels)
    results: list[dict] = []
    original_notification_center = None

    print(f"Target API: {args.base_url}")
    print("Logging in admin and performance reporter users...")
    admin_cookie = login(client, args.admin_username, args.admin_password)
    test_cookie = login(client, args.test_username, args.test_password)

    if not args.keep_notifications:
        original_notification_center = get_config(client, admin_cookie, NOTIFICATION_CENTER_KEY)
        set_config(client, admin_cookie, NOTIFICATION_CENTER_KEY, "false", "压测期间临时关闭通知中心")
        print("Notification center disabled for this performance run.")

    try:
        samples = discover_samples(client, admin_cookie)
        work_order_id = samples["work_order_id"]
        attachment = samples["attachment"]

        query_status_pending = urllib.parse.urlencode({"status": "待处理", "page": 0, "size": 20})
        read_endpoints = [
            ("auth_me_admin", "auth", "GET", "/auth/me", admin_cookie, {200}),
            ("admin_statistics", "work-order", "GET", "/work-orders/statistics", admin_cookie, {200}),
            ("admin_list_page_10", "work-order", "GET", "/work-orders/search?page=0&size=10", admin_cookie, {200}),
            ("admin_list_page_50", "work-order", "GET", "/work-orders/search?page=0&size=50", admin_cookie, {200}),
            ("admin_list_network_fault", "work-order", "GET", "/work-orders/search?reportType=NETWORK_FAULT&page=0&size=20", admin_cookie, {200}),
            ("admin_list_pending", "work-order", "GET", f"/work-orders/search?{query_status_pending}", admin_cookie, {200}),
            ("admin_users", "admin", "GET", "/admin/users?page=0&size=20", admin_cookie, {200}),
            ("admin_permissions", "admin", "GET", "/admin/permissions?page=0&size=50", admin_cookie, {200}),
            ("admin_configs", "admin", "GET", "/admin/configs?page=0&size=30", admin_cookie, {200}),
            ("auth_me_test", "auth", "GET", "/auth/me", test_cookie, {200}),
            ("visible_work_orders_test", "work-order", "GET", "/work-orders/search?page=0&size=10", test_cookie, {200}),
            (
                "test_statistics_scope",
                "security",
                "GET",
                "/work-orders/statistics",
                test_cookie,
                {200} if args.test_username == args.admin_username else {403},
            ),
        ]
        if work_order_id:
            read_endpoints.append(("work_order_detail", "work-order", "GET", f"/work-orders/{work_order_id}", admin_cookie, {200}))
        if attachment:
            read_endpoints.append((
                "attachment_download",
                "attachment",
                "GET",
                f"/work-orders/attachments/{attachment['faultReportId']}/file/{attachment['attachmentId']}",
                admin_cookie,
                {200},
            ))
            read_endpoints.append((
                "attachment_zip",
                "attachment",
                "GET",
                f"/work-orders/{attachment['faultReportId']}/attachments/zip",
                admin_cookie,
                {200},
            ))

        for concurrency in concurrency_levels:
            for name, group, method, path, cookie, expected in read_endpoints:
                results.append(run_http_benchmark(
                    client,
                    name,
                    group,
                    method,
                    path,
                    cookie,
                    concurrency,
                    args.iterations,
                    expected_statuses=expected,
                ))

        results.append(run_http_benchmark(
            client,
            "auth_challenge",
            "auth",
            "POST",
            "/auth/challenge",
            "",
            max(concurrency_levels),
            args.iterations,
            expected_statuses={200},
            json_body=auth_challenge_payload(
                args.admin_username,
                "00000000-0000-4000-8000-000000000001",
                web_auth_environment("00000000-0000-4000-8000-000000000001"),
            ),
        ))

        login_iterations = max(3, min(20, args.iterations // 4))
        results.append(run_callable_benchmark(
            "auth_login_full_flow",
            "auth",
            min(4, max(concurrency_levels)),
            login_iterations,
            lambda: sample_login(client, args.admin_username, args.admin_password),
        ))

        location_path = "/location/reverse-geocode?" + urllib.parse.urlencode({"gpsCoordinates": "118.842100,31.953800"})
        results.append(run_http_benchmark(
            client,
            "location_reverse_geocode_external",
            "external",
            "GET",
            location_path,
            test_cookie,
            1,
            args.external_iterations,
            expected_statuses={200},
        ))

        if args.include_writes:
            created_ids: list[int] = []

            def create_call() -> Sample:
                sample, created_id = create_work_order(client, test_cookie)
                if created_id is not None:
                    created_ids.append(created_id)
                return sample

            results.append(run_callable_benchmark(
                "work_order_create",
                "write",
                min(4, max(concurrency_levels)),
                args.write_count,
                create_call,
            ))
            for created_id in created_ids:
                resolve_payload = {
                    "resolutionDetails": "[压测数据] 已确认并完成回单。",
                    "resolutionMethod": "压测回单",
                    "replacementCompleted": True,
                    "replacementResult": "[压测数据] 换箱后光纤链路正常。",
                }
                results.append(run_http_benchmark(
                    client,
                    "work_order_resolve",
                    "write",
                    "POST",
                    f"/work-orders/{created_id}/responses",
                    admin_cookie,
                    1,
                    1,
                    expected_statuses={200},
                    json_body=resolve_payload,
                ))

        print_summary(results)
        if args.output:
            with open(args.output, "w", encoding="utf-8") as file:
                json.dump(results, file, ensure_ascii=False, indent=2)
            print(f"\nJSON report written to {args.output}")
    finally:
        if not args.keep_notifications:
            restore_value = original_notification_center if original_notification_center is not None else "true"
            set_config(client, admin_cookie, NOTIFICATION_CENTER_KEY, restore_value, "是否启用通知中心")
            print(f"Notification center restored to {restore_value}.")

    failed_samples = sum(result["fail"] for result in results)
    if failed_samples > 0:
        print(
            f"\nPerformance smoke rejected: {failed_samples} request(s) returned an unexpected result.",
            file=sys.stderr,
        )
        return 1
    return 0


def sample_login(client: ApiClient, username: str, password: str) -> Sample:
    started = time.perf_counter()
    try:
        login(client, username, password)
        return Sample(True, 200, (time.perf_counter() - started) * 1000, 0)
    except urllib.error.HTTPError as exc:
        return Sample(False, exc.code, (time.perf_counter() - started) * 1000, 0, str(exc))
    except Exception as exc:
        return Sample(False, 0, (time.perf_counter() - started) * 1000, 0, str(exc))


if __name__ == "__main__":
    sys.exit(main())
