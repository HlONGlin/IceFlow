<div align="center">

# ❄️ IceFlow

<p align="center">
  <b>Android Proxy Client Based on xray-core</b>
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

IceFlow is a modern Android proxy client featuring a fluid Jetpack Compose UI and powerful proxy capabilities powered by xray-core. Supports Android 8.0 and above.

### ✨ Key Features

- 🚀 **Multi-Protocol Support** - VMess, VLESS (with Reality), Trojan, Shadowsocks, Mieru, Hysteria/Hysteria2, TUIC, WireGuard, SSR, HTTP, SOCKS, AnyTLS and Sudoku
- 📱 **Modern UI** - Smooth user interface built with Jetpack Compose
- 🔄 **Flexible Import** - Supports Clash YAML, QR code scanning, subscription links
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

## 📁 Project Structure

| Directory | Description |
|------|------|
| `app/` | Kotlin + Compose application layer: UI, VpnService, quick toggle |
| `rust-core/` | Rust traffic processing layer based on tun2proxy with JNI interface |
| `xray-wrapper/` | gomobile wrapper for xray-core, outputs `app/libs/xray.aar` |
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
# 1. Build Rust core → app/src/main/jniLibs/
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
.\rust-core\build-android.ps1

# 2. Build xray-core → app/libs/xray.aar
.\xray-wrapper\build-aar.ps1

# 3. Build APK
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease
```

#### Linux/macOS

```bash
# 1. Build Rust core
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
./rust-core/build-android.sh

# 2. Build xray-core
./xray-wrapper/build-aar.sh

# 3. Build APK
./gradlew testDebugUnitTest assembleDebug assembleRelease
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

- **app/src/test** - JVM unit tests: share link parsing, xray config generation, formatting
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
