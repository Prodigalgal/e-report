#!/usr/bin/env bash
set -euo pipefail

REGISTRY_HOST="${REGISTRY_HOST:-registry.mnnu.eu.org}"
REGISTRY_PUSH_USER="${REGISTRY_PUSH_USER:-${REGISTRY_USER:-}}"
REGISTRY_PUSH_PASSWORD="${REGISTRY_PUSH_PASSWORD:-${REGISTRY_PASSWORD:-}}"
MIRROR_MYSQL_IMAGE="${MIRROR_MYSQL_IMAGE:-false}"

if [[ -z "$REGISTRY_PUSH_USER" || -z "$REGISTRY_PUSH_PASSWORD" ]]; then
  echo "REGISTRY_PUSH_USER/REGISTRY_PUSH_PASSWORD or REGISTRY_USER/REGISTRY_PASSWORD are required." >&2
  exit 1
fi

echo "$REGISTRY_PUSH_PASSWORD" | docker login "$REGISTRY_HOST" -u "$REGISTRY_PUSH_USER" --password-stdin

mirror_base_image() {
  local source_image="$1"
  local target_image="$2"

  if docker manifest inspect "$target_image" >/dev/null 2>&1; then
    echo "base image already exists: $target_image"
    return
  fi

  echo "mirror base image: $source_image -> $target_image"
  docker pull "$source_image"
  docker tag "$source_image" "$target_image"
  docker push "$target_image"
}

mirror_base_image "gradle:9.6.1-jdk25-alpine@sha256:4d50266538c871adbcd8ece20d61d10e42b6c8c7dec1f10498d9508d45759c35" "${REGISTRY_HOST}/library/gradle:9.6.1-jdk25-alpine"
mirror_base_image "eclipse-temurin:25-jre-alpine@sha256:3c0a9084927a221ccd1d007fcaf614465672c0af37aaa834c5184483afe56d61" "${REGISTRY_HOST}/library/eclipse-temurin:25-jre-alpine"
mirror_base_image "node:24.19.0-alpine3.24@sha256:d32cdf619f63fe0471182d08996dd516c6275bb5fd31ae06e55a570bd9e1ad43" "${REGISTRY_HOST}/library/node:24.19.0-alpine3.24"
mirror_base_image "nginx:1.30.6-alpine3.24@sha256:47928bf67eb62a1863cb08d9ecb52d3e704304ba614eaee17884204677702951" "${REGISTRY_HOST}/library/nginx:1.30.6-alpine3.24"
if [[ "$MIRROR_MYSQL_IMAGE" == "true" ]]; then
  mirror_base_image "mysql:8.4@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb" "${REGISTRY_HOST}/library/mysql:8.4"
fi
