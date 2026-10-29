#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

: "${ANDROID_HOME:?ANDROID_HOME is required}"
: "${ANDROID_NDK_HOME:?ANDROID_NDK_HOME is required}"

# init validates API 16 and installs gobind@latest; bind only needs pinned gobind.
tool_dir="$(mktemp -d)"
trap 'rm -rf "$tool_dir"' EXIT
go build -o "$tool_dir/gobind" golang.org/x/mobile/cmd/gobind
export PATH="$tool_dir:$PATH"
mkdir -p ../app/libs/single
go tool gomobile bind -v \
  -target android/arm64,android/arm,android/amd64 \
  -androidapi 26 \
  -tags with_gvisor \
  -ldflags '-s -w -buildid=' \
  -trimpath \
  -o ../app/libs/single/mihomo.aar \
  ./libmihomo

echo "single mihomo.aar build complete"
