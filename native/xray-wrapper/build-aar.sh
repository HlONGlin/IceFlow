#!/usr/bin/env bash
# 使用 gomobile 将 libxray 编译为 app/libs/xray.aar
# 依赖：Go 1.26+、ANDROID_HOME、ANDROID_NDK_HOME
set -euo pipefail
cd "$(dirname "$0")"

: "${ANDROID_HOME:?请设置 ANDROID_HOME}"
: "${ANDROID_NDK_HOME:?请设置 ANDROID_NDK_HOME}"

# init validates API 16 and installs gobind@latest; bind only needs pinned gobind.
tool_dir="$(mktemp -d)"
trap 'rm -rf "$tool_dir"' EXIT
go build -o "$tool_dir/gobind" golang.org/x/mobile/cmd/gobind
export PATH="$tool_dir:$PATH"

mkdir -p ../../app/libs
go tool gomobile bind -v -target android/arm64,android/arm,android/amd64 -androidapi 26 \
  -ldflags '-s -w -buildid=' -trimpath -o ../../app/libs/xray.aar ./libxray

echo "xray.aar build complete"
