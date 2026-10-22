#!/usr/bin/env bash
set -euo pipefail

CERT_MANAGER_VERSION="${CERT_MANAGER_VERSION:-v1.21.1}"
ENVOY_GATEWAY_VERSION="${ENVOY_GATEWAY_VERSION:-v1.8.2}"
ENVOY_GATEWAY_CHART="${ENVOY_GATEWAY_CHART:-/opt/e-report/bootstrap/gateway-helm-v1.8.2.tgz}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export KUBECONFIG="${KUBECONFIG:-/etc/kubernetes/admin.conf}"

helm upgrade --install cert-manager oci://quay.io/jetstack/charts/cert-manager \
  --version "${CERT_MANAGER_VERSION}" \
  --namespace cert-manager \
  --create-namespace \
  --set crds.enabled=true
kubectl -n cert-manager rollout status deployment/cert-manager --timeout=10m
kubectl -n cert-manager rollout status deployment/cert-manager-cainjector --timeout=10m
kubectl -n cert-manager rollout status deployment/cert-manager-webhook --timeout=10m

if [[ ! -f "${ENVOY_GATEWAY_CHART}" ]]; then
  echo "Envoy Gateway chart not found: ${ENVOY_GATEWAY_CHART}" >&2
  exit 1
fi
if ! kubectl -n envoy-gateway-system get secret registry-pull-secret >/dev/null 2>&1; then
  echo "Missing envoy-gateway-system/registry-pull-secret." >&2
  exit 1
fi
helm upgrade --install eg "${ENVOY_GATEWAY_CHART}" \
  --namespace envoy-gateway-system \
  --create-namespace \
  --values "${SCRIPT_DIR}/envoy-gateway-values.yaml"
kubectl -n envoy-gateway-system rollout status deployment/envoy-gateway --timeout=10m

kubectl apply -f "${SCRIPT_DIR}/envoy-platform.yaml"
kubectl apply -f "${SCRIPT_DIR}/envoy-client-traffic-policy.yaml"
kubectl get gatewayclass envoy-gateway
kubectl get envoyproxy -n envoy-gateway-system
kubectl wait --for=condition=Programmed gateway/platform-gateway -n gateway-system --timeout=10m
kubectl get pods -A
