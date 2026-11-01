<div align="center">

# ❄️ IceFlow

<p align="center">
  <b>Android Proxy Client with dual xray + mihomo cores</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android%208.0%2B-blue?style=flat-square" alt="Platform">
  <img src="https://img.shields.io/badge/language-Kotlin-orange?style=flat-square" alt="Language">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-green?style=flat-square" alt="UI">
  <img src="https://img.shields.io/badge/license-MIT-brightgreen?style=flat-square" alt="License">
</p>

[简体中文](README.md) | English

</div>

---

## 📖 Introduction

IceFlow is a modern Android proxy client featuring a fluid Jetpack Compose UI. It ships two core variants — **dual (xray + mihomo)** and **single (mihomo)** — that share the same UI, subscription and routing features; the dual variant additionally packages xray and the Rust TUN layer. Supports Android 8.0 and above.

### ✨ Key Features

- 🚀 **Multi-Protocol Support** - VMess, VLESS (with Reality), Trojan, Shadowsocks, Mieru, Hysteria/Hysteria2, TUIC, WireGuard, SSR, HTTP, SOCKS, AnyTLS and Sudoku
- 📱 **Modern UI** - Smooth user interface built with Jetpack Compose
- 🔄 **Flexible Import** - Supports Clash YAML, QR code scanning, subscription links
- 🔗 **Deep Link Import** - Open a link from a browser or another app to import a subscription or node, always after confirmation
- 🎯 **Smart Routing** - Rule-based routing and per-app proxy
- 🔐 **Privacy & Security** - Local data encryption, protected internal TUN with random credentials
- 📊 **Traffic Statistics** - Detailed traffic analysis by outbound type (proxy/direct/block)
- 🌐 **DNS Optimization** - Smart DNS routing to avoid CDN pollution

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────┐
│         Jetpack Compose UI (Kotlin)             │
│       User Interface & Node Management          │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│         Android VpnService (TUN)                │
│           TUN Virtual Network Interface         │
└────────────────┬────────────────────────────────┘
                 │ File Descriptor
                 ▼
┌─────────────────────────────────────────────────┐
│       rust-core (tun2proxy, JNI)                │
│      TUN Traffic Capture & SOCKS5 Forward       │
└────────────────┬────────────────────────────────┘
                 │ SOCKS5 127.0.0.1:10808
                 ▼
┌─────────────────────────────────────────────────┐
│     xray-core (Go, gomobile AAR)                │
│       Proxy Protocol Handling & Forward         │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
           🌐 Remote Proxy Server
```

> The diagram above describes the **dual** variant (xray + mihomo): the TUN device is owned by `rust-core` (tun2proxy) and handed to the core through an internal SOCKS5 listener.
> The **single** variant only packages mihomo, which drives TUN and protocol handling itself, so it carries no Rust layer and no `libiceflow_core.so`.

## 📁 Project Structure

| Directory | Description |
|------|------|
| `app/` | Kotlin + Compose application layer: UI, VpnService, quick toggle |
| `rust-core/` | Rust TUN forwarding layer (tun2proxy, JNI), packaged by the dual variant only |
| `xray-wrapper/` | gomobile wrapper for xray-core, outputs `app/libs/xray.aar` (dual) |
| `mihomo-wrapper/` | gomobile wrapper for mihomo, outputs `app/libs/single/mihomo.aar` (single) |
| `docs/` | Architecture documentation and design specifications |

## 🛠️ Build Guide

### Prerequisites

- ☕ JDK 17
- 📱 Android SDK 36 + NDK
- 🦀 Rust (with `cargo-ndk`)
- 🐹 Go 1.26+

### Build Steps

#### Windows

```powershell
# 1. Build the Rust TUN layer → app/src/dual/jniLibs/ (dual variant only)
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
.\rust-core\build-android.ps1

# 2. Build xray-core → app/libs/xray.aar (dual variant only)
.\xray-wrapper\build-aar.ps1

# 2b. Build mihomo → app/libs/single/mihomo.aar (single variant)
.\mihomo-wrapper\build-aar.ps1

# 3. Build APKs (both dual and single variants)
.\gradlew.bat testDualDebugUnitTest testSingleDebugUnitTest assembleDualDebug assembleSingleDebug
```

#### Linux/macOS

```bash
# 1. Build the Rust TUN layer → app/src/dual/jniLibs/ (dual variant only)
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
./rust-core/build-android.sh

# 2. Build xray-core → app/libs/xray.aar (dual variant only)
./xray-wrapper/build-aar.sh

# 2b. Build mihomo → app/libs/single/mihomo.aar (single variant)
./mihomo-wrapper/build-aar.sh

# 3. Build APKs (both dual and single variants)
./gradlew testDualDebugUnitTest testSingleDebugUnitTest assembleDualDebug assembleSingleDebug
```

### ⚠️ Windows Notes

**Non-ASCII Path Issue**: Gradle unit tests will encounter `ClassNotFoundException` if the project path contains non-ASCII characters.

**Solutions**:

```powershell
# Method 1: Map drive letter
subst X: "E:\脚本编写存放\git"
cd X:\IceFlow
.\gradlew.bat testDebugUnitTest
subst X: /D

# Method 2: Use ASCII-only path
```

**Environment Variables**:

```powershell
$env:PATH = "$env:USERPROFILE\.cargo\bin;$env:USERPROFILE\go\bin;$env:PATH"
```

## 🧪 Testing

### Test Structure

- **app/src/test** - JVM unit tests: share/deep link parsing, config generation, formatting (shared by dual and single)
- **app/src/testSingle** - JVM unit tests specific to the single (mihomo) variant
- **app/src/androidTest** - Android instrumentation tests: Compose UI tests, native library validation
- **rust-core** - Rust unit tests: `cargo test`
- **xray-wrapper** - Go unit tests: `go test ./...`

### Run Android Tests

```powershell
# Auto install and test (will uninstall app)
.\gradlew.bat connectedDebugAndroidTest

# Manual test preserving data
.\gradlew.bat assembleDebug assembleDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w com.iceflow.test/androidx.test.runner.AndroidJUnitRunner

# Test on specific device
adb -s emulator-5556 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5556 install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s emulator-5556 shell am instrument -w com.iceflow.test/androidx.test.runner.AndroidJUnitRunner
```

## 🔗 Deep Links & Shared Text

Browsers and other apps can hand links straight to IceFlow. Every external import first shows a confirmation dialog (subscription name plus the full URL, or a node summary and count) — **nothing is written and no connection is started before you confirm**.

| Entry point | Example |
|------|------|
| Clash subscription | `clash://install-config?url=https%3A%2F%2Fexample.com%2Fsub` |
| IceFlow subscription | `iceflow://install-config?url=https%3A%2F%2Fexample.com%2Fsub&name=My%20Sub` (`iceflow://import-config` also works) |
| v2rayN subscription | `v2rayn://install-sub?url=https%3A%2F%2Fexample.com%2Fsub` |
| sing-box remote profile | `sing-box://import-remote-profile?url=https%3A%2F%2Fexample.com%2Fsub` |
| Single node share link | `vmess://`, `vless://`, `trojan://`, `ss://`, `ssr://`, `hysteria2://`, `hy2://`, `tuic://`, `wireguard://`, `anytls://`, `mierus://` |
| Shared text | Another app's "Share" → IceFlow (`text/plain`) |
| Config file | Open `.yaml` / `.yml` / `.json` / `.txt` from a file manager (4 MiB limit) |

- Subscription URLs must be HTTPS; `http://`, malformed or oversized links are rejected with an explicit reason.
- A subscription with the same URL is reported, never added twice.
- Full `mieru://` Protobuf links need xray to decode, so only the **dual** variant accepts them; the **single** variant shows an explicit "this core cannot open the link" message instead of silently ignoring it.
- Each Intent is consumed once: rotation, activity re-creation and repeated `onNewIntent` never re-open the dialog.

## 🔄 Auto Update

The app silently checks GitHub Releases on startup and prompts when a new version is available.

### Configuration Options

- ✅ Auto check on startup
- 🔧 Manual check: Settings → About → IceFlow
- 🔕 Disable reminders: Check "Don't remind me again" in dialog

### Private Repository Setup

Private repositories require a GitHub Fine-grained token:

1. Create token: Repository access → `IceFlow`
2. Permissions: Contents: Read
3. Enter token in dialog on first failed check
4. Token is stored locally only, not in backups

### CI/CD

Pushing `v*` tags automatically triggers GitHub Actions:
- ✅ Build debug and release APKs
- ✅ Generate ABI-specific and universal packages
- ✅ Auto-create GitHub Release

## ⚙️ Release Configuration

### Version Settings

```powershell
.\gradlew.bat assembleRelease -Piceflow.versionCode=2 -Piceflow.versionName=0.1.1
```

### Signing Configuration

Configure signing via environment variables (not stored in repository):

```powershell
$env:ICEFLOW_KEYSTORE_PATH="path/to/keystore.jks"
$env:ICEFLOW_KEYSTORE_PASSWORD="keystore_password"
$env:ICEFLOW_KEY_ALIAS="key_alias"
$env:ICEFLOW_KEY_PASSWORD="key_password"
```

## 📋 Known Limitations

- 📦 **Subscription Limits** - HTTPS only, 4 MiB content limit, 64 MiB routing database limit
- 🎨 **Animation** - Lottie animations not yet integrated
- 💾 **Backup** - Exports nodes, subscriptions, groups, rules and settings (routing database excluded)
- 🔄 **Auto Reconnect** - Reconnects on connection parameter, node or chain changes (not on appearance settings)
- 🔒 **Security** - Internal TUN protected with random credentials to prevent abuse
- ⚡ **Speed Test** - Via internal SOCKS inbound, shares implementation with node testing
- 🌐 **Network Recovery** - TUN preserved during interruptions, auto-retry up to 5 times
- 🛡️ **Force Block** - Requires system "Always-on VPN" and "Block connections without VPN"
- 🎯 **DNS Routing** - Direct domains use domestic DNS, proxied domains use remote DNS

## 📊 Features

### Node Management
- ✅ Multi-protocol support
- ✅ Group management
- ✅ Subscription updates
- ✅ QR code scanning
- ✅ Deep link & shared-text import (confirmation required)
- ✅ Latency testing
- ✅ Batch import

### Proxy Features
- ✅ Rule-based routing
- ✅ Per-app proxy
- ✅ DNS routing
- ✅ IPv6 support
- ✅ Proxy chaining
- ✅ Traffic statistics

### User Interface
- ✅ Material Design 3
- ✅ Dark mode
- ✅ Theme switching
- ✅ Smooth animations
- ✅ Responsive layout

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details

---

<div align="center">

**If you find this project helpful, please give it a Star ⭐**

</div>
