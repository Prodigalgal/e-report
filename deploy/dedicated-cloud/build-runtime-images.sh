#!/usr/bin/env bash
set -euo pipefail

RELEASE_TAG="${RELEASE_TAG:-20260804-dedicated-cloud-2}"
BACKEND_CONTEXT="${BACKEND_CONTEXT:-/opt/e-report/build/backend}"
FRONTEND_CONTEXT="${FRONTEND_CONTEXT:-/opt/e-report/build/frontend}"
MEDIA_IMAGE_WORKER_CONTEXT="${MEDIA_IMAGE_WORKER_CONTEXT:-/opt/e-report/build/media-image-worker}"
RELEASE_DIR="${RELEASE_DIR:-/opt/e-report/releases/${RELEASE_TAG}}"
SBOM_PATH="${SBOM_PATH:-/opt/e-report/build/security/e-report-source.cdx.json}"
PIP_INDEX_URL="${PIP_INDEX_URL:-https://mirrors.aliyun.com/pypi/simple}"
MODEL_VERSION="${MODEL_VERSION:-cmcc-brand-v1}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

command -v buildah >/dev/null
command -v ctr >/dev/null
test -f "${BACKEND_CONTEXT}/app.jar"
test -f "${BACKEND_CONTEXT}/database-migration.jar"
test -f "${BACKEND_CONTEXT}/database-admin.jar"
test -d "${FRONTEND_CONTEXT}/dist"
test -f "${FRONTEND_CONTEXT}/nginx.conf"
test -f "${MEDIA_IMAGE_WORKER_CONTEXT}/models/${MODEL_VERSION}.onnx"
test -f "${MEDIA_IMAGE_WORKER_CONTEXT}/models/${MODEL_VERSION}.json"
test -f "${SBOM_PATH}"

mkdir -p "${RELEASE_DIR}"
buildah bud --layers=false \
  -t "localhost/e-report-backend:${RELEASE_TAG}" \
  -f "${SCRIPT_DIR}/Dockerfile.backend-runtime" \
  "${BACKEND_CONTEXT}"
buildah bud --layers=false \
  -t "localhost/e-report-database-migration:${RELEASE_TAG}" \
  -f "${SCRIPT_DIR}/Dockerfile.database-migration-runtime" \
  "${BACKEND_CONTEXT}"
buildah bud --layers=false \
  -t "localhost/e-report-frontend:${RELEASE_TAG}" \
  -f "${SCRIPT_DIR}/Dockerfile.frontend-runtime" \
  "${FRONTEND_CONTEXT}"
buildah bud --layers=false \
  --env "PIP_INDEX_URL=${PIP_INDEX_URL}" \
  -t "localhost/e-report-media-image-worker:${RELEASE_TAG}" \
  "${MEDIA_IMAGE_WORKER_CONTEXT}"

buildah push "localhost/e-report-backend:${RELEASE_TAG}" "oci-archive:${RELEASE_DIR}/backend.oci"
buildah push "localhost/e-report-database-migration:${RELEASE_TAG}" "oci-archive:${RELEASE_DIR}/database-migration.oci"
buildah push "localhost/e-report-frontend:${RELEASE_TAG}" "oci-archive:${RELEASE_DIR}/frontend.oci"
buildah push "localhost/e-report-media-image-worker:${RELEASE_TAG}" "oci-archive:${RELEASE_DIR}/media-image-worker.oci"
install -m 0644 "${SBOM_PATH}" "${RELEASE_DIR}/e-report-source.cdx.json"
(
  cd "${RELEASE_DIR}"
  sha256sum backend.oci database-migration.oci frontend.oci media-image-worker.oci e-report-source.cdx.json > SHA256SUMS
  sha256sum -c SHA256SUMS
)
ctr -n k8s.io images import \
  --index-name "localhost/e-report-backend:${RELEASE_TAG}" \
  "${RELEASE_DIR}/backend.oci"
ctr -n k8s.io images import \
  --index-name "localhost/e-report-database-migration:${RELEASE_TAG}" \
  "${RELEASE_DIR}/database-migration.oci"
ctr -n k8s.io images import \
  --index-name "localhost/e-report-frontend:${RELEASE_TAG}" \
  "${RELEASE_DIR}/frontend.oci"
ctr -n k8s.io images import \
  --index-name "localhost/e-report-media-image-worker:${RELEASE_TAG}" \
  "${RELEASE_DIR}/media-image-worker.oci"

ctr -n k8s.io images ls | grep -E "localhost/e-report-(backend|database-migration|frontend|media-image-worker):${RELEASE_TAG}"
