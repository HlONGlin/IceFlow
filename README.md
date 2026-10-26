<div align=”center”>

# ❄️ IceFlow

<p align=”center”>
  <b>基于 xray-core 的 Android 代理客户端</b>
</p>

<p align=”center”>
  <img src=”https://img.shields.io/badge/platform-Android%208.0%2B-blue?style=flat-square” alt=”Platform”>
  <img src=”https://img.shields.io/badge/language-Kotlin-orange?style=flat-square” alt=”Language”>
  <img src=”https://img.shields.io/badge/UI-Jetpack%20Compose-green?style=flat-square” alt=”UI”>
  <img src=”https://img.shields.io/badge/license-MIT-brightgreen?style=flat-square” alt=”License”>
</p>

简体中文 | [English](README.en.md)

</div>

---

## 📖 简介

IceFlow 是一款现代化的 Android 代理客户端，采用 Jetpack Compose 构建流畅的用户界面，基于 xray-core 提供强大的代理能力。支持 Android 8.0 及以上版本。

### ✨ 主要特性

- 🚀 **多协议支持** - VMess、VLESS (含 Reality)、Trojan、Shadowsocks、Mieru、Hysteria/Hysteria2、TUIC、WireGuard、SSR、HTTP、SOCKS、AnyTLS 和 Sudoku
- 📱 **现代化 UI** - 基于 Jetpack Compose 的流畅用户界面
- 🔄 **灵活导入** - 支持 Clash YAML、二维码扫描、订阅链接
- 🎯 **智能分流** - 规则分流与分应用代理
- 🔐 **隐私安全** - 本地数据加密，内部 TUN 入口随机凭据保护
- 📊 **流量统计** - 按出站类型（代理/直连/阻止）的详细流量分析
- 🌐 **DNS 优化** - 智能 DNS 分流，避免 CDN 污染

## 🏗️ 架构设计

```
┌─────────────────────────────────────────────────┐
│         Jetpack Compose UI (Kotlin)             │
│          用户界面 & 节点管理 & 设置              │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│         Android VpnService (TUN)                │
│            TUN 虚拟网络接口                      │
└────────────────┬────────────────────────────────┘
                 │ File Descriptor
                 ▼
┌─────────────────────────────────────────────────┐
│       rust-core (tun2proxy, JNI)                │
│      TUN 流量捕获与 SOCKS5 转发                  │
└────────────────┬────────────────────────────────┘
                 │ SOCKS5 127.0.0.1:10808
                 ▼
┌─────────────────────────────────────────────────┐
│     xray-core (Go, gomobile AAR)                │
│          代理协议处理 & 流量转发                 │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
           🌐 远端代理服务器
```

## 📁 项目结构

| 目录 | 说明 |
|------|------|
| `app/` | Kotlin + Compose 应用层：UI、VpnService、快捷开关 |
| `rust-core/` | Rust 流量处理层，基于 tun2proxy，JNI 接口 |
| `xray-wrapper/` | xray-core 的 gomobile 封装，产物为 `app/libs/xray.aar` |
| `docs/` | 架构文档与设计说明 |

## 🛠️ 构建指南

### 前置要求

- ☕ JDK 17
- 📱 Android SDK 36 + NDK
- 🦀 Rust (含 `cargo-ndk`)
- 🐹 Go 1.26+

### 构建步骤

#### Windows

```powershell
# 1. 构建 Rust 核心 → app/src/main/jniLibs/
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
.\rust-core\build-android.ps1

# 2. 构建 xray-core → app/libs/xray.aar
.\xray-wrapper\build-aar.ps1

# 3. 构建 APK
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease
```

#### Linux/macOS

```bash
# 1. 构建 Rust 核心
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
./rust-core/build-android.sh

# 2. 构建 xray-core
./xray-wrapper/build-aar.sh

# 3. 构建 APK
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

### ⚠️ Windows 注意事项

**中文路径问题**：项目路径含中文时，Gradle 单元测试会出现 `ClassNotFoundException`。

**解决方案**：

```powershell
# 方法 1：映射盘符
subst X: “E:\脚本编写存放\git”
cd X:\IceFlow
.\gradlew.bat testDebugUnitTest
subst X: /D

# 方法 2：使用纯英文路径
```

**环境变量配置**：

```powershell
$env:PATH = “$env:USERPROFILE\.cargo\bin;$env:USERPROFILE\go\bin;$env:PATH”
```

## 🧪 测试

### 测试结构

- **app/src/test** - JVM 单元测试：分享链接解析、xray 配置生成、格式化
- **app/src/androidTest** - Android 仪器测试：Compose UI 测试、原生库加载验证
- **rust-core** - Rust 单元测试：`cargo test`
- **xray-wrapper** - Go 单元测试：`go test ./...`

### 运行 Android 测试

```powershell
# 自动安装并测试（会卸载应用）
.\gradlew.bat connectedDebugAndroidTest

# 保留数据的手动测试
.\gradlew.bat assembleDebug assembleDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w com.iceflow.test/androidx.test.runner.AndroidJUnitRunner

# 指定设备测试
adb -s emulator-5556 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5556 install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s emulator-5556 shell am instrument -w com.iceflow.test/androidx.test.runner.AndroidJUnitRunner
```

## 🔄 自动更新

应用启动后会静默检查 GitHub Releases，发现新版本时弹窗提醒。

### 配置选项

- ✅ 自动检查更新（启动时）
- 🔧 手动检查：设置 → 关于 → IceFlow
- 🔕 禁用提醒：弹窗中勾选”不再自动提醒”

### 私有仓库配置

私有仓库需要 GitHub Fine-grained token：

1. 创建 token：Repository access 选择 `IceFlow`
2. 权限设置：Contents: Read
3. 首次检查失败时在弹窗中填入 token
4. Token 仅保存在本地，不写入备份文件

### CI/CD

推送 `v*` 标签自动触发 GitHub Actions：
- ✅ 构建 debug 和 release APK
- ✅ 生成 ABI 特定包和 universal 包
- ✅ 自动创建 GitHub Release

## ⚙️ 发布配置

### 版本设置

```powershell
.\gradlew.bat assembleRelease -Piceflow.versionCode=2 -Piceflow.versionName=0.1.1
```

### 签名配置

通过环境变量配置签名（不写入仓库）：

```powershell
$env:ICEFLOW_KEYSTORE_PATH=”path/to/keystore.jks”
$env:ICEFLOW_KEYSTORE_PASSWORD=”keystore_password”
$env:ICEFLOW_KEY_ALIAS=”key_alias”
$env:ICEFLOW_KEY_PASSWORD=”key_password”
```

## 📋 已知限制

- 📦 **订阅限制** - 仅支持 HTTPS 地址，内容上限 4 MiB，路由数据库上限 64 MiB
- 🎨 **动画** - Lottie 动画尚未集成
- 💾 **备份** - 导出节点、订阅、分组、规则及设置（不含路由数据库）
- 🔄 **自动重连** - 连接参数、节点或代理链改变时自动重连（外观设置不重连）
- 🔒 **安全性** - 内部 TUN 使用随机凭据保护，防止其他应用滥用
- ⚡ **测速** - 通过内部 SOCKS 入口，与节点测速共用实现
- 🌐 **网络恢复** - 中断时保留 TUN，自动重试最多 5 次
- 🛡️ **强制阻断** - 需启用系统”始终开启 VPN”和”阻止无 VPN 连接”
- 🎯 **DNS 分流** - 直连域名用国内 DNS，代理域名用远端 DNS

## 📊 功能特性

### 节点管理
- ✅ 多协议支持
- ✅ 分组管理
- ✅ 订阅更新
- ✅ 二维码扫描
- ✅ 延迟测试
- ✅ 批量导入

### 代理功能
- ✅ 规则分流
- ✅ 分应用代理
- ✅ DNS 分流
- ✅ IPv6 支持
- ✅ 链式代理
- ✅ 流量统计

### 用户界面
- ✅ Material Design 3
- ✅ 深色模式
- ✅ 主题切换
- ✅ 流畅动画
- ✅ 响应式布局

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

---

<div align=”center”>

**如果觉得这个项目有帮助，欢迎 Star ⭐**

</div>
