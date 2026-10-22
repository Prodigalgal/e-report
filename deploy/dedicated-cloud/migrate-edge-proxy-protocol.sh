#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PUBLIC_HTTP_PORT="${PUBLIC_HTTP_PORT:-60080}"
PUBLIC_HOST="${PUBLIC_HOST:-e-report.fantong.eu.org}"
export KUBECONFIG="${KUBECONFIG:-/etc/kubernetes/admin.conf}"
POLICY_NAME="platform-proxy-protocol"
LEGACY_SERVICES=(e-report-edge-http.service e-report-edge-https.service)

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script as root." >&2
  exit 1
fi

temporary_policy="$(mktemp)"
cleanup() {
  rm -f "${temporary_policy}"
}

wait_for_policy() {
  local expected_optional="$1"
  local accepted optional
  for attempt in {1..60}; do
    accepted="$(kubectl -n gateway-system get clienttrafficpolicy "${POLICY_NAME}" \
      -o jsonpath='{range .status.ancestors[*].conditions[?(@.type=="Accepted")]}{.status}{end}')"
    optional="$(kubectl -n gateway-system get clienttrafficpolicy "${POLICY_NAME}" \
      -o jsonpath='{.spec.proxyProtocol.optional}')"
    if [[ "${accepted}" == "True" && "${optional}" == "${expected_optional}" ]]; then
      return 0
    fi
    sleep 2
  done
  echo "ClientTrafficPolicy did not reach Accepted=True, optional=${expected_optional}." >&2
  return 1
}

rollback() {
  local exit_code=$?
  trap - ERR
  set +e
  echo "Edge migration failed; restoring legacy listeners." >&2
  kubectl apply -f "${temporary_policy}" >/dev/null
  wait_for_policy true
  systemctl disable --now e-report-edge-haproxy.service >/dev/null 2>&1
  systemctl daemon-reload
  systemctl enable --now "${LEGACY_SERVICES[@]}"
  curl --fail --silent --show-error \
    --header "Host: ${PUBLIC_HOST}" \
    "http://127.0.0.1:${PUBLIC_HTTP_PORT}/" >/dev/null
  echo "Legacy listeners restored; ClientTrafficPolicy remains optional for compatibility." >&2
  exit "${exit_code}"
}

trap cleanup EXIT
trap rollback ERR
sed 's/optional: false/optional: true/' \
  "${SCRIPT_DIR}/envoy-client-traffic-policy.yaml" >"${temporary_policy}"

# During migration Envoy accepts both the legacy socat stream and PROXY v2.
kubectl apply -f "${temporary_policy}"
wait_for_policy true

"${SCRIPT_DIR}/configure-edge-ports.sh"
curl --fail --silent --show-error \
  --header "Host: ${PUBLIC_HOST}" \
  "http://127.0.0.1:${PUBLIC_HTTP_PORT}/" >/dev/null

# After HAProxy is healthy, reject direct listener traffic without PROXY protocol.
kubectl apply -f "${SCRIPT_DIR}/envoy-client-traffic-policy.yaml"
wait_for_policy false
curl --fail --silent --show-error \
  --header "Host: ${PUBLIC_HOST}" \
  "http://127.0.0.1:${PUBLIC_HTTP_PORT}/" >/dev/null

trap - ERR
echo "e-report edge now uses mandatory HAProxy PROXY protocol v2."
