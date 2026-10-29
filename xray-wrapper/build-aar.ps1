# 使用 gomobile 将 libxray 编译为 app/libs/xray.aar（Windows）
# 依赖：Go 1.26+、Android SDK + NDK（ANDROID_HOME / ANDROID_NDK_HOME）
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not $env:ANDROID_NDK_HOME) {
    $ndk = Get-ChildItem (Join-Path $env:ANDROID_HOME 'ndk') -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if (-not $ndk) { throw '找不到 NDK，请设置 ANDROID_NDK_HOME' }
    $env:ANDROID_NDK_HOME = $ndk.FullName
}

# go.mod 的 tool 指令固定 gomobile / gobind，避免 @latest 改变构建结果。
$toolDir = Join-Path ([System.IO.Path]::GetTempPath()) ('iceflow-gobind-' + [guid]::NewGuid())
$originalPath = $env:PATH
New-Item -ItemType Directory $toolDir | Out-Null
try {
    # Avoid init's API 16 validation and gobind@latest installation.
    go build -o (Join-Path $toolDir 'gobind.exe') golang.org/x/mobile/cmd/gobind
    if ($LASTEXITCODE -ne 0) { throw "gobind build failed ($LASTEXITCODE)" }
    $env:PATH = "$toolDir;$originalPath"

    New-Item -ItemType Directory -Force ..\app\libs | Out-Null
    go tool gomobile bind -v -target 'android/arm64,android/arm,android/amd64' -androidapi 26 `
        -ldflags '-s -w -buildid=' -trimpath -o ..\app\libs\xray.aar ./libxray
    if ($LASTEXITCODE -ne 0) { throw "gomobile bind failed ($LASTEXITCODE)" }
    Write-Host 'xray.aar build complete'
} finally {
    $env:PATH = $originalPath
    Remove-Item -LiteralPath (Join-Path $toolDir 'gobind.exe') -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $toolDir -Force
}
