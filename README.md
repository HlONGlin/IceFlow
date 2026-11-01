<div align="center">

# ❄️ IceFlow

<p align="center">
  <b>基于 xray + mihomo 双核心变体的 Android 代理客户端</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android%208.0%2B-blue?style=flat-square" alt="Platform">
  <img src="https://img.shields.io/badge/language-Kotlin-orange?style=flat-square" alt="Language">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-green?style=flat-square" alt="UI">
  <img src="https://img.shields.io/badge/license-MIT-brightgreen?style=flat-square" alt="License">
</p>

简体中文 | [English](README.en.md)

</div>

---

## 📖 简介

IceFlow 是一款现代化的 Android 代理客户端，采用 Jetpack Compose 构建流畅的用户界面，提供 **dual（xray + mihomo）** 与 **single（mihomo）** 两种核心变体：两者共享同一套 UI、订阅与路由能力，dual 变体额外打包 xray 与 Rust TUN 层。支持 Android 8.0 及以上版本。

### ✨ 主要特性

- 🚀 **多协议支持** - VMess、VLESS (含 Reality)、Trojan、Shadowsocks、Mieru、Hysteria/Hysteria2、TUIC、WireGuard、SSR、HTTP、SOCKS、AnyTLS 和 Sudoku
- 📱 **现代化 UI** - 基于 Jetpack Compose 的流畅用户界面
- 🔄 **灵活导入** - 支持 Clash YAML、二维码扫描、订阅链接
- 🔗 **深链接导入** - 浏览器或其它 App 点链接即可导入订阅/节点，导入前必须确认
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

> 上图对应 **dual** 变体（xray + mihomo）：TUN 由 `rust-core`（tun2proxy）接管，经内部 SOCKS5 交给核心处理。
> **single** 变体只打包 mihomo，由 mihomo 自行接管 TUN 与协议处理，因此没有 Rust 层与 `libiceflow_core.so`。

## 📁 项目结构

| 目录 | 说明 |
|------|------|
| `app/` | Kotlin + Compose 应用层：UI、VpnService、快捷开关 |
| `rust-core/` | Rust TUN 转发层（tun2proxy，JNI），仅 dual 变体打包 |
| `xray-wrapper/` | xray-core 的 gomobile 封装，产物为 `app/libs/xray.aar`（dual） |
| `mihomo-wrapper/` | mihomo 的 gomobile 封装，产物为 `app/libs/single/mihomo.aar`（single） |
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
# 1. 构建 Rust TUN 层 → app/src/dual/jniLibs/（仅 dual 变体使用）
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
.\rust-core\build-android.ps1

# 2. 构建 xray-core → app/libs/xray.aar（仅 dual 变体使用）
.\xray-wrapper\build-aar.ps1

# 2b. 构建 mihomo → app/libs/single/mihomo.aar（single 变体使用）
.\mihomo-wrapper\build-aar.ps1

# 3. 构建 APK（dual 与 single 两种变体）
.\gradlew.bat testDualDebugUnitTest testSingleDebugUnitTest assembleDualDebug assembleSingleDebug
```

#### Linux/macOS

```bash
# 1. 构建 Rust TUN 层 → app/src/dual/jniLibs/（仅 dual 变体使用）
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
./rust-core/build-android.sh

# 2. 构建 xray-core → app/libs/xray.aar（仅 dual 变体使用）
./xray-wrapper/build-aar.sh

# 2b. 构建 mihomo → app/libs/single/mihomo.aar（single 变体使用）
./mihomo-wrapper/build-aar.sh

# 3. 构建 APK（dual 与 single 两种变体）
./gradlew testDualDebugUnitTest testSingleDebugUnitTest assembleDualDebug assembleSingleDebug
```

### ⚠️ Windows 注意事项

**中文路径问题**：项目路径含中文时，Gradle 单元测试会出现 `ClassNotFoundException`。

**解决方案**：

```powershell
# 方法 1：映射盘符
subst X: "E:\脚本编写存放\git"
cd X:\IceFlow
.\gradlew.bat testDebugUnitTest
subst X: /D

# 方法 2：使用纯英文路径
```

**环境变量配置**：

```powershell
$env:PATH = "$env:USERPROFILE\.cargo\bin;$env:USERPROFILE\go\bin;$env:PATH"
```

## 🧪 测试

### 测试结构

- **app/src/test** - JVM 单元测试：分享链接/深链接解析、配置生成、格式化（dual 与 single 共用）
- **app/src/testSingle** - single（mihomo）变体专用 JVM 单元测试
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

## 🔗 深链接与分享导入

浏览器或其它 App 可以直接把链接交给 IceFlow。所有外部导入都会先弹出确认框（展示订阅名称与完整地址，或节点摘要与数量），**确认前不写入任何配置、也不会自动连接**。

| 入口 | 示例 |
|------|------|
| Clash 订阅 | `clash://install-config?url=https%3A%2F%2Fexample.com%2Fsub` |
| IceFlow 订阅 | `iceflow://install-config?url=https%3A%2F%2Fexample.com%2Fsub&name=My%20Sub`（也支持 `iceflow://import-config`） |
| v2rayN 订阅 | `v2rayn://install-sub?url=https%3A%2F%2Fexample.com%2Fsub` |
| sing-box 远程配置 | `sing-box://import-remote-profile?url=https%3A%2F%2Fexample.com%2Fsub` |
| 单节点分享链接 | `vmess://`、`vless://`、`trojan://`、`ss://`、`ssr://`、`hysteria2://`、`hy2://`、`tuic://`、`wireguard://`、`anytls://`、`mierus://` |
| 分享文本 | 其它 App「分享」→ IceFlow（`text/plain`） |
| 打开配置文件 | 文件管理器打开 `.yaml` / `.yml` / `.json` / `.txt`（内容上限 4 MiB） |

- 订阅地址仅接受 HTTPS，`http://`、畸形或超长链接会被直接拒绝并提示原因。
- 已存在相同地址的订阅只提示，不会重复添加。
- `mieru://` 完整 Protobuf 链接依赖 xray 解码，仅 **dual** 变体支持；**single** 变体导入时会明确提示「当前内核不支持」，不会静默忽略。
- 同一个 Intent 只会被消费一次，旋转屏幕、重建界面或重复 `onNewIntent` 都不会重复弹窗。

## 🔄 自动更新

应用启动后会静默检查 GitHub Releases，发现新版本时弹窗提醒。

### 配置选项

- ✅ 自动检查更新（启动时）
- 🔧 手动检查：设置 → 关于 → IceFlow
- 🔕 禁用提醒：弹窗中勾选「不再自动提醒」

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
$env:ICEFLOW_KEYSTORE_PATH="path/to/keystore.jks"
$env:ICEFLOW_KEYSTORE_PASSWORD="keystore_password"
$env:ICEFLOW_KEY_ALIAS="key_alias"
$env:ICEFLOW_KEY_PASSWORD="key_password"
```

## 📋 已知限制

- 📦 **订阅限制** - 仅支持 HTTPS 地址，内容上限 4 MiB，路由数据库上限 64 MiB
- 🎨 **动画** - Lottie 动画尚未集成
- 💾 **备份** - 导出节点、订阅、分组、规则及设置（不含路由数据库）
- 🔄 **自动重连** - 连接参数、节点或代理链改变时自动重连（外观设置不重连）
- 🔒 **安全性** - 内部 TUN 使用随机凭据保护，防止其他应用滥用
- ⚡ **测速** - 通过内部 SOCKS 入口，与节点测速共用实现
- 🌐 **网络恢复** - 中断时保留 TUN，自动重试最多 5 次
- 🛡️ **强制阻断** - 需启用系统「始终开启 VPN」和「阻止无 VPN 连接」
- 🎯 **DNS 分流** - 直连域名用国内 DNS，代理域名用远端 DNS

## 📊 功能特性

### 节点管理
- ✅ 多协议支持
- ✅ 分组管理
- ✅ 订阅更新
- ✅ 二维码扫描
- ✅ 深链接与分享导入（导入前确认）
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

<div align="center">

**如果觉得这个项目有帮助，欢迎 Star ⭐**

</div>
