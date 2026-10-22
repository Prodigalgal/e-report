#!/usr/bin/env bash
set -euo pipefail

export KUBECONFIG=/etc/kubernetes/admin.conf

echo "== cloudflared workloads =="
kubectl -n cloudflared get deploy,pod,svc,cm -o wide

echo "== cloudflared configmaps =="
for cm in $(kubectl -n cloudflared get cm -o jsonpath='{.items[*].metadata.name}'); do
  echo "--- configmap/${cm} ---"
  kubectl -n cloudflared get cm "$cm" -o yaml
done

echo "== default gateway =="
kubectl -n default get gateway eg -o yaml

echo "== existing HTTPRoutes =="
kubectl get httproute -A -o yaml
