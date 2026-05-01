#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-single}"

REPO="ucdavisplse/scalelens"
PLATFORMS="linux/amd64,linux/arm64"

TAGS=(
  "HD-3.1.0-mini"
  "CA-3.11.0-mini"
  "IG-2.8.0-mini"
)

for TAG in "${TAGS[@]}"; do
  IMAGE="${REPO}:${TAG}"

  echo "Building ${IMAGE} from ${IMAGE}"

  case "$MODE" in
    single)
      docker build \
        --build-arg BASE_IMAGE="$IMAGE" \
        -t "$IMAGE" \
        .
      ;;

    single-push)
      docker build \
        --build-arg BASE_IMAGE="$IMAGE" \
        -t "$IMAGE" \
        .
      docker push "$IMAGE"
      ;;

    multiarch)
      docker buildx build \
        --platform "$PLATFORMS" \
        --build-arg BASE_IMAGE="$IMAGE" \
        -t "$IMAGE" \
        .
      ;;

    multiarch-push)
      docker buildx build \
        --platform "$PLATFORMS" \
        --build-arg BASE_IMAGE="$IMAGE" \
        -t "$IMAGE" \
        --push \
        .
      ;;

    *)
      echo "Unknown mode: $MODE"
      echo "Usage: $0 {single|single-push|multiarch|multiarch-push}"
      exit 1
      ;;
  esac
done