#!/usr/bin/env bash
set -euo pipefail

ACME_EMAIL="${ACME_EMAIL:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERTIFICATE_TEMPLATE="${SCRIPT_DIR}/cert-manager-fantong-resources.yaml.tpl"

export KUBECONFIG="${KUBECONFIG:-/etc/kubernetes/admin.conf}"

if [[ -z "${ACME_EMAIL}" ]]; then
  echo "ACME_EMAIL is required." >&2
  exit 1
fi
if [[ "${ACME_EMAIL}" == *$'\n'* || "${ACME_EMAIL}" == *$'\r'* || "${ACME_EMAIL}" == *'|'* ]]; then
  echo "ACME_EMAIL contains unsupported characters." >&2
  exit 1
fi
if ! kubectl -n cert-manager get secret cloudflare-api-token-fantong >/dev/null 2>&1; then
  echo "Missing cert-manager/cloudflare-api-token-fantong." >&2
  exit 1
fi
TOKEN_LENGTH="$(kubectl -n cert-manager get secret cloudflare-api-token-fantong -o jsonpath='{.data.api-token}' | base64 -d | wc -c)"
if [[ "${TOKEN_LENGTH}" -lt 20 ]]; then
  echo "cert-manager/cloudflare-api-token-fantong contains an empty or invalid api-token." >&2
  exit 1
fi

sed "s|__ACME_EMAIL__|${ACME_EMAIL}|g" "${CERTIFICATE_TEMPLATE}" | kubectl apply -f -
kubectl wait --for=condition=Ready certificate/fantong-wildcard -n gateway-system --timeout=15m
kubectl apply -f "${SCRIPT_DIR}/envoy-platform-tls.yaml"
kubectl wait --for=condition=Programmed gateway/platform-gateway -n gateway-system --timeout=10m
kubectl get clusterissuer letsencrypt-prod-fantong
kubectl get certificate fantong-wildcard -n gateway-system
kubectl get gateway platform-gateway -n gateway-system -o wide
