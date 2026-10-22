#!/usr/bin/env bash
set -euo pipefail

export KUBECONFIG="${KUBECONFIG:-/etc/kubernetes/admin.conf}"

ENV_FILE="${1:-/tmp/e-report-deploy.env}"
WORKDIR="${WORKDIR:-/opt/e-report-src}"
MANIFEST="${MANIFEST:-$WORKDIR/deploy/local-k8s/e-report.yaml}"
HOST_DATA_ROOT="${HOST_DATA_ROOT:-/data/e-report}"
REGISTRY_HOST="${REGISTRY_HOST:-registry.mnnu.eu.org}"
REGISTRY_LOGIN_ENABLED="${REGISTRY_LOGIN_ENABLED:-true}"

if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source <(sed $'1s/^\xef\xbb\xbf//' "$ENV_FILE" | sed 's/\r$//')
fi

REGISTRY_USER="${REGISTRY_USER:-${REGISTRY_PUSH_USER:-}}"
REGISTRY_PASSWORD="${REGISTRY_PASSWORD:-${REGISTRY_PUSH_PASSWORD:-}}"
DEFAULT_ADMIN_USERNAME="${DEFAULT_ADMIN_USERNAME:-admin}"
DEFAULT_ADMIN_PASSWORD="${DEFAULT_ADMIN_PASSWORD:-}"
DEFAULT_ADMIN_PHONE="${DEFAULT_ADMIN_PHONE:-}"
DB_PASSWORD="${DB_PASSWORD:-}"
AMAP_API_KEY="${AMAP_API_KEY:-}"
NOTIFICATION_CENTER_ENABLED="${NOTIFICATION_CENTER_ENABLED:-true}"
AUTH_TOKEN_SECRET="${AUTH_TOKEN_SECRET:-}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"

GRADLE_BASE_IMAGE="${GRADLE_BASE_IMAGE:-${REGISTRY_HOST}/library/gradle:9.6.1-jdk25-alpine}"
TEMURIN_BASE_IMAGE="${TEMURIN_BASE_IMAGE:-${REGISTRY_HOST}/library/eclipse-temurin:25-jre-alpine}"
NODE_BASE_IMAGE="${NODE_BASE_IMAGE:-${REGISTRY_HOST}/library/node:24.19.0-alpine3.24}"
NGINX_BASE_IMAGE="${NGINX_BASE_IMAGE:-${REGISTRY_HOST}/library/nginx:1.30.4-alpine3.24}"
BACKEND_IMAGE="${BACKEND_IMAGE:-localhost/e-report-backend:local-jdk25}"
FRONTEND_IMAGE="${FRONTEND_IMAGE:-localhost/e-report-frontend:local}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:7.4-alpine@sha256:e7723ff73d963f5cc6d9c4643ea3d989527a402a319239054e9472a7fb9219a2}"

require_base_registry_credentials() {
  if [[ -z "$REGISTRY_USER" || -z "$REGISTRY_PASSWORD" ]]; then
    echo "REGISTRY_USER/REGISTRY_PASSWORD are required for private base image pulls." >&2
    exit 1
  fi
}

require_default_admin_credentials() {
  if [[ -z "$DEFAULT_ADMIN_USERNAME" || -z "$DEFAULT_ADMIN_PASSWORD" ]]; then
    echo "DEFAULT_ADMIN_USERNAME and DEFAULT_ADMIN_PASSWORD are required." >&2
    exit 1
  fi
}

import_image_to_containerd() {
  local image="$1"
  echo "import image into containerd: $image"
  docker save "$image" | ctr -n k8s.io images import -
}

import_optional_docker_image_to_containerd() {
  local image="$1"
  if docker image inspect "$image" >/dev/null 2>&1; then
    import_image_to_containerd "$image"
  else
    echo "docker image not present locally, kubelet will pull if needed: $image"
  fi
}

all_required_base_images_are_cached() {
  docker image inspect "$GRADLE_BASE_IMAGE" >/dev/null 2>&1 \
    && docker image inspect "$TEMURIN_BASE_IMAGE" >/dev/null 2>&1 \
    && docker image inspect "$NODE_BASE_IMAGE" >/dev/null 2>&1 \
    && docker image inspect "$NGINX_BASE_IMAGE" >/dev/null 2>&1
}

pin_cached_image_by_digest() {
  local image="$1"
  if [[ "$image" == *@sha256:* ]]; then
    printf '%s\n' "$image"
    return
  fi

  docker pull "$image" >/dev/null 2>&1 || docker image inspect "$image" >/dev/null
  local repository="${image%:*}"
  local digest_reference
  digest_reference="$(
    docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$image" \
      | awk -v prefix="${repository}@sha256:" 'index($0, prefix) == 1 { print; exit }'
  )"
  if [[ -z "$digest_reference" ]]; then
    echo "cannot resolve immutable RepoDigest for $image" >&2
    exit 1
  fi
  echo "pinned base image: $image -> $digest_reference" >&2
  printf '%s\n' "$digest_reference"
}

require_default_admin_credentials
if [[ "$REGISTRY_LOGIN_ENABLED" == "true" ]]; then
  require_base_registry_credentials
fi
command -v ctr >/dev/null 2>&1 || {
  echo "ctr is required on the single-node Kubernetes host." >&2
  exit 1
}

if [[ "$REGISTRY_LOGIN_ENABLED" != "true" ]]; then
  if all_required_base_images_are_cached; then
    echo "registry login disabled; using cached backend/frontend base images."
  else
    echo "registry login is disabled and required base images are not fully cached." >&2
    exit 1
  fi
elif getent hosts "$REGISTRY_HOST" >/dev/null 2>&1; then
  if ! echo "$REGISTRY_PASSWORD" | docker login "$REGISTRY_HOST" -u "$REGISTRY_USER" --password-stdin; then
    if all_required_base_images_are_cached; then
      echo "registry login failed; using cached backend/frontend base images."
    else
      echo "registry login failed and required base images are not fully cached." >&2
      exit 1
    fi
  fi
elif all_required_base_images_are_cached; then
  echo "registry DNS is unavailable; using cached backend/frontend base images."
else
  echo "registry DNS is unavailable and required base images are not cached." >&2
  exit 1
fi

GRADLE_BASE_IMAGE="$(pin_cached_image_by_digest "$GRADLE_BASE_IMAGE")"
TEMURIN_BASE_IMAGE="$(pin_cached_image_by_digest "$TEMURIN_BASE_IMAGE")"
NODE_BASE_IMAGE="$(pin_cached_image_by_digest "$NODE_BASE_IMAGE")"
NGINX_BASE_IMAGE="$(pin_cached_image_by_digest "$NGINX_BASE_IMAGE")"

cd "$WORKDIR/e-report-backen"
docker build --progress=plain \
  --build-arg GRADLE_BASE_IMAGE="$GRADLE_BASE_IMAGE" \
  --build-arg TEMURIN_BASE_IMAGE="$TEMURIN_BASE_IMAGE" \
  -t "$BACKEND_IMAGE" .
import_image_to_containerd "$BACKEND_IMAGE"

cd "$WORKDIR"
docker build --progress=plain \
  --build-arg NODE_BASE_IMAGE="$NODE_BASE_IMAGE" \
  --build-arg NGINX_BASE_IMAGE="$NGINX_BASE_IMAGE" \
  -f e-report/Dockerfile \
  -t "$FRONTEND_IMAGE" .
import_image_to_containerd "$FRONTEND_IMAGE"
import_optional_docker_image_to_containerd "$REDIS_IMAGE"

mkdir -p "$HOST_DATA_ROOT/mysql" "$HOST_DATA_ROOT/redis" "$HOST_DATA_ROOT/uploads"

kubectl create namespace e-report --dry-run=client -o yaml | kubectl apply -f -
if [[ -z "$DB_PASSWORD" ]]; then
  DB_PASSWORD="$(kubectl -n e-report get secret e-report-mysql -o jsonpath='{.data.MYSQL_ROOT_PASSWORD}' 2>/dev/null | base64 -d || true)"
fi
if [[ -z "$DB_PASSWORD" ]]; then
  echo "DB_PASSWORD is required for a fresh deployment." >&2
  exit 1
fi
if [[ -z "$AUTH_TOKEN_SECRET" ]]; then
  AUTH_TOKEN_SECRET="$(kubectl -n e-report get secret e-report-auth -o jsonpath='{.data.AUTH_TOKEN_SECRET}' 2>/dev/null | base64 -d || true)"
fi
if [[ -z "$REDIS_PASSWORD" ]]; then
  REDIS_PASSWORD="$(kubectl -n e-report get secret e-report-redis -o jsonpath='{.data.REDIS_PASSWORD}' 2>/dev/null | base64 -d || true)"
fi
if [[ -z "$REDIS_PASSWORD" ]]; then
  REDIS_PASSWORD="$(openssl rand -hex 32 2>/dev/null || head -c 32 /dev/urandom | base64 | tr -d '\n')"
fi
if [[ -z "$AUTH_TOKEN_SECRET" ]]; then
  AUTH_TOKEN_SECRET="$(openssl rand -hex 32 2>/dev/null || head -c 32 /dev/urandom | base64 | tr -d '\n')"
fi
kubectl -n e-report create secret generic e-report-mysql \
  --from-literal=MYSQL_ROOT_PASSWORD="$DB_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n e-report create secret generic e-report-auth \
  --from-literal=AUTH_TOKEN_SECRET="$AUTH_TOKEN_SECRET" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n e-report create secret generic e-report-redis \
  --from-literal=REDIS_PASSWORD="$REDIS_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n e-report create secret generic e-report-default-admin \
  --from-literal=DEFAULT_ADMIN_USERNAME="$DEFAULT_ADMIN_USERNAME" \
  --from-literal=DEFAULT_ADMIN_PASSWORD="$DEFAULT_ADMIN_PASSWORD" \
  --from-literal=DEFAULT_ADMIN_PHONE="$DEFAULT_ADMIN_PHONE" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n e-report delete secret e-report-default-test-user --ignore-not-found
kubectl -n e-report create secret generic e-report-external-integrations \
  --from-literal=AMAP_API_KEY="$AMAP_API_KEY" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n e-report create secret generic e-report-notification \
  --from-literal=NOTIFICATION_CENTER_ENABLED="$NOTIFICATION_CENTER_ENABLED" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f "$MANIFEST"

kubectl -n e-report rollout status deploy/e-mysql --timeout=180s
kubectl -n e-report rollout status deploy/e-report-redis --timeout=180s
kubectl -n e-report rollout restart deploy/e-report-backend deploy/e-report-frontend
kubectl -n e-report rollout status deploy/e-report-backend --timeout=240s
kubectl -n e-report rollout status deploy/e-report-frontend --timeout=180s
kubectl -n e-report get deploy,pod,svc,httproute -o wide

rm -f "$ENV_FILE"
