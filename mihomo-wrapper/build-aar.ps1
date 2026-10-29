# Build single-only pinned mihomo gomobile AAR. No signing inputs are read here.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
if (-not $env:ANDROID_NDK_HOME) {
    $ndk = Get-ChildItem (Join-Path $env:ANDROID_HOME 'ndk') -Directory | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $ndk) { throw 'Android NDK is required' }
    $env:ANDROID_NDK_HOME = $ndk.FullName
}
go tool gomobile init
if ($LASTEXITCODE -ne 0) { throw "gomobile init failed ($LASTEXITCODE)" }
New-Item -ItemType Directory -Force ../app/libs/single | Out-Null
go tool gomobile bind -v -target 'android/arm64,android/arm,android/amd64' -androidapi 26 -tags with_gvisor -ldflags '-s -w -buildid=' -trimpath -o ../app/libs/single/mihomo.aar ./libmihomo
if ($LASTEXITCODE -ne 0) { throw "gomobile bind failed ($LASTEXITCODE)" }
Write-Host 'single mihomo.aar build complete'
