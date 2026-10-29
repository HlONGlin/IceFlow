# Build single-only pinned mihomo gomobile AAR. No signing inputs are read here.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
if (-not $env:ANDROID_NDK_HOME) {
    $ndk = Get-ChildItem (Join-Path $env:ANDROID_HOME 'ndk') -Directory | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $ndk) { throw 'Android NDK is required' }
    $env:ANDROID_NDK_HOME = $ndk.FullName
}
$toolDir = Join-Path ([System.IO.Path]::GetTempPath()) ('iceflow-gobind-' + [guid]::NewGuid())
$originalPath = $env:PATH
New-Item -ItemType Directory $toolDir | Out-Null
try {
    # Avoid init's API 16 validation and gobind@latest installation.
    go build -o (Join-Path $toolDir 'gobind.exe') golang.org/x/mobile/cmd/gobind
    if ($LASTEXITCODE -ne 0) { throw "gobind build failed ($LASTEXITCODE)" }
    $env:PATH = "$toolDir;$originalPath"
    New-Item -ItemType Directory -Force ../app/libs/single | Out-Null
    go tool gomobile bind -v -target 'android/arm64,android/arm,android/amd64' -androidapi 26 -tags with_gvisor -ldflags '-s -w -buildid=' -trimpath -o ../app/libs/single/mihomo.aar ./libmihomo
    if ($LASTEXITCODE -ne 0) { throw "gomobile bind failed ($LASTEXITCODE)" }
    Write-Host 'single mihomo.aar build complete'
} finally {
    $env:PATH = $originalPath
    Remove-Item -LiteralPath (Join-Path $toolDir 'gobind.exe') -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $toolDir -Force
}
