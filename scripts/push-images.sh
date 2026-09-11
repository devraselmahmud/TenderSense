#!/usr/bin/env bash
set -euo pipefail

DOCKERHUB_USER="${DOCKERHUB_USER:-raselmahmudbits}"
TAG="${1:-latest}"
PLATFORMS="${PLATFORMS:-linux/amd64}"
PUSH="${PUSH:-1}"

if [[ "${TAG}" == "-h" || "${TAG}" == "--help" ]]; then
  cat <<'USAGE'
Usage: scripts/push-images.sh [TAG]

Builds and (optionally) pushes TenderSense service images to Docker Hub.

Environment variables:
  DOCKERHUB_USER  Docker Hub org/user (default: raselmahmudbits)
  TAG             Image tag (default: latest; common: git short SHA, semver)
  PLATFORMS       Target platforms (default: linux/amd64)
  PUSH            1 to push, 0 to build only (default: 1)

Examples:
  scripts/push-images.sh latest
  TAG=1.0.2 scripts/push-images.sh
  TAG=$(git rev-parse --short HEAD) scripts/push-images.sh
  PLATFORMS=linux/amd64,linux/arm64 scripts/push-images.sh 1.0.2
USAGE
  exit 0
fi

services=(
  "backend"
  "python-service"
  "frontend"
)

build_args=(
  --platform "${PLATFORMS}"
)

if [[ "${PUSH}" == "1" ]]; then
  build_args+=(--push)
else
  build_args+=(--load)
fi

for service in "${services[@]}"; do
  repo="${DOCKERHUB_USER}/tendersense-${service}"
  echo "==> Building ${repo}:${TAG}"
  docker buildx build "${build_args[@]}" \
    -t "${repo}:${TAG}" \
    "./${service}"
done

echo "==> Done."
