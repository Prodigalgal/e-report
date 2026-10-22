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
trap 'rm -f "${temporary_policy}"' EXIT
sed 's/optional: false/optional: true/' \
  "${SCRIPT_DIR}/envoy-client-traffic-policy.yaml" >"${temporary_policy}"

kubectl apply -f "${temporary_policy}"
for attempt in {1..60}; do
  accepted="$(kubectl -n gateway-system get clienttrafficpolicy "${POLICY_NAME}" \
    -o jsonpath='{range .status.ancestors[*].conditions[?(@.type=="Accepted")]}{.status}{end}')"
  optional="$(kubectl -n gateway-system get clienttrafficpolicy "${POLICY_NAME}" \
    -o jsonpath='{.spec.proxyProtocol.optional}')"
  [[ "${accepted}" == "True" && "${optional}" == "true" ]] && break
  sleep 2
done
if [[ "${accepted}" != "True" || "${optional}" != "true" ]]; then
  echo "ClientTrafficPolicy did not enter optional compatibility mode." >&2
  exit 1
fi

systemctl disable --now e-report-edge-haproxy.service
systemctl daemon-reload
systemctl enable --now "${LEGACY_SERVICES[@]}"
curl --fail --silent --show-error \
  --header "Host: ${PUBLIC_HOST}" \
  "http://127.0.0.1:${PUBLIC_HTTP_PORT}/" >/dev/null

ss -lntp | grep -E ":(${PUBLIC_HTTP_PORT}|60443) "
echo "Legacy edge listeners restored; PROXY protocol remains optional."
