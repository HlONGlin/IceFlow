#!/usr/bin/env bash
# 编译 Rust 核心并输出到 app/src/main/jniLibs/<abi>/libiceflow_core.so
# 依赖：rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
#       cargo install cargo-ndk
#       ANDROID_NDK_HOME 指向 NDK（如 $ANDROID_HOME/ndk/28.2.13676358）
set -euo pipefail
cd "$(dirname "$0")"

: "${ANDROID_NDK_HOME:?请设置 ANDROID_NDK_HOME}"

cargo ndk \
  -t arm64-v8a -t armeabi-v7a -t x86_64 \
  -P 26 \
  -o ../app/src/main/jniLibs \
  build --release

# tun2proxy 自身的 cdylib 会被一并复制；它已静态链接进 libiceflow_core.so，删除冗余文件
find ../app/src/main/jniLibs -name 'libtun2proxy-*.so' -delete

echo "Rust core build complete"
