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
go tool gomobile init
if ($LASTEXITCODE -ne 0) { throw "gomobile init failed ($LASTEXITCODE)" }

New-Item -ItemType Directory -Force ..\..\app\libs | Out-Null
go tool gomobile bind -v -target 'android/arm64,android/arm,android/amd64' -androidapi 26 `
    -ldflags '-s -w -buildid=' -trimpath -o ..\..\app\libs\xray.aar ./libxray
if ($LASTEXITCODE -ne 0) { throw "gomobile bind failed ($LASTEXITCODE)" }
Write-Host 'xray.aar build complete'
