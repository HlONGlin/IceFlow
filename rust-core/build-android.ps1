# 编译 Rust 核心并输出到 app/src/main/jniLibs/<abi>/libiceflow_core.so（Windows）
# 依赖：rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
#       cargo install cargo-ndk
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not $env:ANDROID_NDK_HOME) {
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $ndk = Get-ChildItem (Join-Path $sdk 'ndk') -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if (-not $ndk) { throw '找不到 NDK，请设置 ANDROID_NDK_HOME' }
    $env:ANDROID_NDK_HOME = $ndk.FullName
}
Write-Host "Using NDK: $env:ANDROID_NDK_HOME"

cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -P 26 -o ..\app\src\main\jniLibs build --release
if ($LASTEXITCODE -ne 0) { throw "cargo ndk failed ($LASTEXITCODE)" }

# tun2proxy 自身的 cdylib 会被一并复制；它已静态链接进 libiceflow_core.so，删除冗余文件
Get-ChildItem ..\app\src\main\jniLibs -Recurse -Filter 'libtun2proxy-*.so' | Remove-Item
Write-Host 'Rust core build complete'
