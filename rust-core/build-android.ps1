# Build Rust into app/src/main/jniLibs/<abi>/libiceflow_core.so on Windows.
# Requires: rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
#       cargo install cargo-ndk
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not $env:ANDROID_NDK_HOME) {
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $ndk = Get-ChildItem (Join-Path $sdk 'ndk') -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if (-not $ndk) { throw 'NDK not found; set ANDROID_NDK_HOME' }
    $env:ANDROID_NDK_HOME = $ndk.FullName
}
Write-Host "Using NDK: $env:ANDROID_NDK_HOME"

cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -P 26 -o ..\app\src\main\jniLibs build --release
if ($LASTEXITCODE -ne 0) { throw "cargo ndk failed ($LASTEXITCODE)" }

# tun2proxy is already statically linked; remove its redundant cdylib copies.
Get-ChildItem ..\app\src\main\jniLibs -Recurse -Filter 'libtun2proxy-*.so' | Remove-Item
Write-Host 'Rust core build complete'
