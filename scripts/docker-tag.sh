#!/bin/bash
set -e

if [ -n "$TAG_NAME" ]; then
    IMAGE_TAG="${TAG_NAME#v}"
else
    LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "0.0.0")
    IMAGE_TAG="${LATEST_TAG}-${BUILD_NUMBER}"
fi

echo "$IMAGE_TAG"
