#!/usr/bin/env bash
set -euo pipefail

PUBLIC_HTTP_PORT="${PUBLIC_HTTP_PORT:-60080}"
PUBLIC_HTTPS_PORT="${PUBLIC_HTTPS_PORT:-60443}"
ENVOY_HTTP_NODE_PORT="${ENVOY_HTTP_NODE_PORT:-30080}"
ENVOY_HTTPS_NODE_PORT="${ENVOY_HTTPS_NODE_PORT:-30443}"
CONFIG_DIR="${CONFIG_DIR:-/etc/e-report-edge}"
CONFIG_FILE="${CONFIG_DIR}/haproxy.cfg"
SERVICE_NAME="e-report-edge-haproxy.service"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script as root." >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y haproxy
install -d -m 0755 "${CONFIG_DIR}"

cat >"${CONFIG_FILE}" <<EOF
global
    log /dev/log local0
    maxconn 4096

defaults
    log global
    mode tcp
    option tcplog
    timeout connect 5s
    timeout client 5m
    timeout server 5m

frontend e_report_http
    bind 0.0.0.0:${PUBLIC_HTTP_PORT} backlog 4096
    default_backend envoy_http

backend envoy_http
    server envoy 127.0.0.1:${ENVOY_HTTP_NODE_PORT} send-proxy-v2 check maxconn 4096

frontend e_report_https
    bind 0.0.0.0:${PUBLIC_HTTPS_PORT} backlog 4096
    default_backend envoy_https

backend envoy_https
    server envoy 127.0.0.1:${ENVOY_HTTPS_NODE_PORT} send-proxy-v2 check maxconn 4096
EOF

/usr/sbin/haproxy -c -f "${CONFIG_FILE}"

cat >"/etc/systemd/system/${SERVICE_NAME}" <<EOF
[Unit]
Description=e-report edge HAProxy with PROXY protocol v2
After=network-online.target kubelet.service
Wants=network-online.target

[Service]
Type=notify
ExecStart=/usr/sbin/haproxy -Ws -f ${CONFIG_FILE} -p /run/e-report-edge-haproxy/haproxy.pid
ExecReload=/usr/sbin/haproxy -Ws -f ${CONFIG_FILE} -c -q
ExecReload=/bin/kill -USR2 \$MAINPID
KillMode=mixed
Restart=always
RestartSec=2
RuntimeDirectory=e-report-edge-haproxy
RuntimeDirectoryMode=0750
LimitNOFILE=65536
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true

[Install]
WantedBy=multi-user.target
EOF

systemctl disable --now haproxy.service >/dev/null 2>&1 || true
for legacy_service in e-report-edge-http.service e-report-edge-https.service; do
  systemctl disable --now "${legacy_service}" >/dev/null 2>&1 || true
done

systemctl daemon-reload
systemctl enable --now "${SERVICE_NAME}"
systemctl restart "${SERVICE_NAME}"
systemctl is-active --quiet "${SERVICE_NAME}"
systemctl --no-pager --full status "${SERVICE_NAME}"

listen_pattern="$(printf '%s|%s' "${PUBLIC_HTTP_PORT}" "${PUBLIC_HTTPS_PORT}")"
for attempt in {1..10}; do
  listening_count="$(ss -lntp | grep -Ec ":(${listen_pattern}) " || true)"
  if [[ "${listening_count}" -eq 2 ]]; then
    ss -lntp | grep -E ":(${listen_pattern}) "
    exit 0
  fi
  sleep 1
done

echo "Edge HAProxy ports did not enter LISTEN state." >&2
exit 1
