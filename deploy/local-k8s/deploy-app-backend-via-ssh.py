#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import posixpath
import shlex
import tarfile
import tempfile
import time
from pathlib import Path

import paramiko


ROOT = Path(__file__).resolve().parents[2]
SERVER_WORKSPACE = Path(r"D:\WorkSpace\Project\服务器管理")
LOCAL_CREDENTIALS = SERVER_WORKSPACE / "private" / "local-credentials.env"
REGISTRY_ENV = SERVER_WORKSPACE / "private" / "registry" / "zot.env"
NOTIFICATION_ENV = SERVER_WORKSPACE / "private" / "e-report-notification.env"
RUNTIME_ENV = SERVER_WORKSPACE / "private" / "e-report-runtime.env"


def parse_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for raw_line in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def add_tree(tar: tarfile.TarFile, source: Path, arc_root: str) -> None:
    excluded_dirs = {
        ".expo",
        ".gradle",
        ".idea",
        ".vite",
        "build",
        "dist",
        "fault_uploads",
        "logs",
        "node_modules",
        "__pycache__",
    }
    excluded_suffixes = {".pyc", ".pyo"}
    for path in source.rglob("*"):
        relative = path.relative_to(source)
        if any(part in excluded_dirs for part in relative.parts):
            continue
        if path.is_file() and path.suffix in excluded_suffixes:
            continue
        tar.add(path, arcname=posixpath.join(arc_root, source.name, relative.as_posix()))


def create_bundle() -> Path:
    fd, bundle_name = tempfile.mkstemp(prefix="e-report-backend-", suffix=".tar.gz")
    os.close(fd)
    bundle = Path(bundle_name)
    with tarfile.open(bundle, mode="w:gz") as tar:
        add_tree(tar, ROOT / "e-report-backen", "e-report-src")
        add_tree(tar, ROOT / "e-report", "e-report-src")
        add_tree(tar, ROOT / "deploy" / "local-k8s", "e-report-src/deploy")
    return bundle


def build_remote_env() -> str:
    registry = parse_env(REGISTRY_ENV)
    notification = parse_env(NOTIFICATION_ENV)
    runtime = parse_env(RUNTIME_ENV)
    values = {
        "REGISTRY_HOST": registry.get("REGISTRY_HOST", "registry.mnnu.eu.org"),
        "REGISTRY_LOGIN_ENABLED": "false",
        "REGISTRY_USER": registry.get("REGISTRY_USER", ""),
        "REGISTRY_PASSWORD": registry.get("REGISTRY_PASSWORD", ""),
        "REGISTRY_PUSH_USER": registry.get("REGISTRY_PUSH_USER", registry.get("REGISTRY_USER", "")),
        "REGISTRY_PUSH_PASSWORD": registry.get("REGISTRY_PUSH_PASSWORD", registry.get("REGISTRY_PASSWORD", "")),
        "GRADLE_BASE_IMAGE": "gradle:9.6.1-jdk25-alpine",
        "TEMURIN_BASE_IMAGE": "eclipse-temurin:25-jre-alpine",
        "NODE_BASE_IMAGE": "node:20-alpine",
        "NGINX_BASE_IMAGE": "nginx:1.25-alpine",
        "DB_PASSWORD": runtime.get("DB_PASSWORD", ""),
        "DEFAULT_ADMIN_USERNAME": runtime.get("DEFAULT_ADMIN_USERNAME", "admin"),
        "DEFAULT_ADMIN_PASSWORD": runtime.get("DEFAULT_ADMIN_PASSWORD", ""),
        "AMAP_API_KEY": runtime.get("AMAP_API_KEY", ""),
        "NOTIFICATION_CENTER_ENABLED": notification.get("NOTIFICATION_CENTER_ENABLED", "true"),
    }
    return "".join(f"{key}={shlex.quote(value)}\n" for key, value in values.items())


def connect() -> paramiko.SSHClient:
    credentials = parse_env(LOCAL_CREDENTIALS)
    host = credentials.get("LOCAL_CMCC_DEBIAN_TAILNET_IPV4", "100.64.0.5")
    username = credentials.get("LOCAL_CMCC_DEBIAN_LOGIN", "root")
    password = credentials.get("LOCAL_CMCC_DEBIAN_PASSWORD", "")
    if not password:
        raise RuntimeError(f"missing LOCAL_CMCC_DEBIAN_PASSWORD in {LOCAL_CREDENTIALS}")

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(
        hostname=host,
        username=username,
        password=password,
        timeout=20,
        banner_timeout=20,
        auth_timeout=20,
        look_for_keys=False,
        allow_agent=False,
    )
    return client


def upload_text(sftp: paramiko.SFTPClient, remote_path: str, content: str) -> None:
    with sftp.file(remote_path, "w") as remote_file:
        remote_file.write(content)
    sftp.chmod(remote_path, 0o600)


def run(client: paramiko.SSHClient, command: str) -> None:
    transport = client.get_transport()
    if transport is None:
        raise RuntimeError("ssh transport is not available")
    channel = transport.open_session()
    channel.get_pty()
    channel.exec_command(command)

    while not channel.exit_status_ready():
        if channel.recv_ready():
            print(channel.recv(65535).decode("utf-8", errors="replace"), end="")
        if channel.recv_stderr_ready():
            print(channel.recv_stderr(65535).decode("utf-8", errors="replace"), end="")
        time.sleep(0.2)

    while channel.recv_ready():
        print(channel.recv(65535).decode("utf-8", errors="replace"), end="")
    while channel.recv_stderr_ready():
        print(channel.recv_stderr(65535).decode("utf-8", errors="replace"), end="")
    exit_code = channel.recv_exit_status()
    if exit_code != 0:
        raise RuntimeError(f"remote command failed with exit code {exit_code}")


def remote_script(bundle_path: str, env_path: str) -> str:
    return f"""set -euo pipefail
export KUBECONFIG="${{KUBECONFIG:-/etc/kubernetes/admin.conf}}"
ENV_FILE={shlex.quote(env_path)}
WORKDIR="${{WORKDIR:-/opt/e-report-src}}"
HOST_DATA_ROOT="${{HOST_DATA_ROOT:-/data/e-report}}"
source <(sed $'1s/^\\xef\\xbb\\xbf//' "$ENV_FILE" | sed 's/\\r$//')

rm -rf "$WORKDIR/e-report-backen" "$WORKDIR/e-report" "$WORKDIR/deploy/local-k8s"
mkdir -p "$WORKDIR" "$HOST_DATA_ROOT/mysql" "$HOST_DATA_ROOT/uploads"
tar -xzf {shlex.quote(bundle_path)} -C /opt

chmod +x "$WORKDIR/deploy/local-k8s/deploy-local-import-containerd.sh"
WORKDIR="$WORKDIR" HOST_DATA_ROOT="$HOST_DATA_ROOT" "$WORKDIR/deploy/local-k8s/deploy-local-import-containerd.sh" "$ENV_FILE"
rm -f "$ENV_FILE" {shlex.quote(bundle_path)}
"""


def remote_probe_script() -> str:
    return r"""set -e
hostname
echo "=== dns ==="
cat /etc/resolv.conf || true
getent hosts registry.mnnu.eu.org || true
getent hosts google.com || true
echo "=== docker images ==="
docker image ls --format '{{.Repository}}:{{.Tag}}' | grep -E 'gradle|temurin|e-report-backend|node|nginx' || true
echo "=== kubernetes ==="
kubectl -n e-report get deploy,pod,svc -o wide || true
"""


def remote_dns_probe_script() -> str:
    return r"""set -e
echo "=== resolv.conf ==="
ls -l /etc/resolv.conf || true
cat /etc/resolv.conf || true
echo "=== tailscale dns ==="
tailscale version || true
tailscale debug prefs 2>/dev/null | grep -E 'CorpDNS|RouteAll|ExitNode|MagicDNS' || true
tailscale status --json 2>/dev/null | grep -E '"MagicDNSSuffix"|"DNS"' || true
echo "=== systemd-resolved ==="
systemctl is-active systemd-resolved || true
resolvectl status 2>/dev/null | sed -n '1,80p' || true
echo "=== connectivity ==="
getent hosts registry.mnnu.eu.org || true
getent hosts google.com || true
"""


def remote_fix_dns_script() -> str:
    return r"""set -euo pipefail
echo "=== before ==="
ls -l /etc/resolv.conf || true
cat /etc/resolv.conf || true

if command -v tailscale >/dev/null 2>&1; then
  if tailscale set --accept-dns=false >/tmp/e-report-tailscale-dns.log 2>&1; then
    echo "tailscale accept-dns disabled via tailscale set"
  else
    echo "tailscale set failed, trying tailscale up --accept-dns=false"
    tailscale up --accept-dns=false >/tmp/e-report-tailscale-dns.log 2>&1 || {
      cat /tmp/e-report-tailscale-dns.log >&2
      exit 1
    }
  fi
fi

if systemctl is-active --quiet systemd-resolved && [[ -e /run/systemd/resolve/stub-resolv.conf ]]; then
  ln -sf /run/systemd/resolve/stub-resolv.conf /etc/resolv.conf
  mkdir -p /etc/systemd/resolved.conf.d
  cat >/etc/systemd/resolved.conf.d/e-report-dns.conf <<'EOF'
[Resolve]
DNS=223.5.5.5 119.29.29.29 1.1.1.1 8.8.8.8
FallbackDNS=1.0.0.1 8.8.4.4
Domains=~.
EOF
  systemctl restart systemd-resolved
else
  rm -f /etc/resolv.conf
  cat >/etc/resolv.conf <<'EOF'
# Managed by e-report deployment automation.
# Tailscale DNS takeover is disabled on this node because it previously wrote
# an empty resolv.conf with only "search hs.internal".
nameserver 223.5.5.5
nameserver 119.29.29.29
nameserver 1.1.1.1
nameserver 8.8.8.8
options timeout:2 attempts:2
EOF
fi

echo "=== after ==="
ls -l /etc/resolv.conf || true
cat /etc/resolv.conf || true
getent hosts registry.mnnu.eu.org
getent hosts google.com
"""


def main() -> None:
    parser = argparse.ArgumentParser(description="Deploy e-report backend to local single-node Kubernetes over SSH.")
    parser.add_argument("--dry-run", action="store_true", help="Create bundle and print target only.")
    parser.add_argument("--probe", action="store_true", help="Probe remote DNS, image cache and Kubernetes state.")
    parser.add_argument("--dns-probe", action="store_true", help="Probe remote DNS takeover state.")
    parser.add_argument("--fix-dns", action="store_true", help="Disable Tailscale DNS takeover and restore host DNS.")
    args = parser.parse_args()

    credentials = parse_env(LOCAL_CREDENTIALS)
    host = credentials.get("LOCAL_CMCC_DEBIAN_TAILNET_IPV4", "100.64.0.5")
    username = credentials.get("LOCAL_CMCC_DEBIAN_LOGIN", "root")
    bundle = create_bundle()
    print(f"bundle={bundle}", flush=True)
    print(f"target={username}@{host}", flush=True)
    if args.dry_run:
        bundle.unlink(missing_ok=True)
        return

    client = connect()
    try:
        if args.probe:
            bundle.unlink(missing_ok=True)
            run(client, remote_probe_script())
            return
        if args.dns_probe:
            bundle.unlink(missing_ok=True)
            run(client, remote_dns_probe_script())
            return
        if args.fix_dns:
            bundle.unlink(missing_ok=True)
            run(client, remote_fix_dns_script())
            return

        remote_bundle = f"/tmp/{bundle.name}"
        remote_env = f"/tmp/e-report-deploy-{int(time.time())}.env"
        sftp = client.open_sftp()
        try:
            sftp.put(str(bundle), remote_bundle)
            upload_text(sftp, remote_env, build_remote_env())
        finally:
            sftp.close()
        run(client, remote_script(remote_bundle, remote_env))
    finally:
        client.close()
        bundle.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
