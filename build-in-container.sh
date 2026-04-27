#!/usr/bin/env bash
set -euo pipefail

IMAGE=acal-build:latest
PROJECT_ROOT=$(cd "$(dirname "$0")" && pwd)
KEYSTORE_DIR=${HOME}/AndroidStudioProjects/keystore
GRADLE_CACHE=${HOME}/.cache/acal-gradle

if ! docker image inspect "${IMAGE}" >/dev/null 2>&1; then
    echo "Building ${IMAGE} (first run only, ~5 minutes)..."
    docker build -t "${IMAGE}" "${PROJECT_ROOT}"
fi

mkdir -p "${GRADLE_CACHE}"

# local.properties.sdk.dir overrides ANDROID_HOME, so any host-written value
# (e.g. from Android Studio) points Gradle at a path that doesn't exist in the
# container. Write a container-correct value on every run.
echo "sdk.dir=/opt/android-sdk" > "${PROJECT_ROOT}/local.properties"

MOUNTS=(
    -v "${PROJECT_ROOT}:/workspace"
    -v "${GRADLE_CACHE}:/gradle-cache"
)
if [ -d "${KEYSTORE_DIR}" ]; then
    MOUNTS+=(-v "${KEYSTORE_DIR}:${KEYSTORE_DIR}:ro")
fi

docker run --rm \
    --user "$(id -u):$(id -g)" \
    "${MOUNTS[@]}" \
    -e GRADLE_USER_HOME=/gradle-cache \
    -w /workspace \
    "${IMAGE}" \
    ./gradlew "$@"

APK_OUT="${PROJECT_ROOT}/apk"
mkdir -p "${APK_OUT}"
shopt -s globstar nullglob
for apk in "${PROJECT_ROOT}"/build/outputs/apk/**/*.apk; do
    cp -f "${apk}" "${APK_OUT}/"
    echo "Copied $(basename "${apk}") to apk/"
done
for aab in "${PROJECT_ROOT}"/build/outputs/bundle/**/*.aab; do
    cp -f "${aab}" "${APK_OUT}/"
    echo "Copied $(basename "${aab}") to apk/"
done
