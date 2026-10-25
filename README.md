# IceFlow

基于 xray-core 的 Android 代理客户端（Android 8.0+）。设计文档见 `docs/IceFlow-Architecture.md`。

## 架构

```
Jetpack Compose UI ──► VpnService (TUN)
                          │ fd
                          ▼
              rust-core (tun2proxy, JNI)
                          │ SOCKS5 127.0.0.1:10808
                          ▼
              xray-core (Go, gomobile AAR) ──► 远端服务器
```

| 目录 | 内容 |
|------|------|
| `app/` | Kotlin + Compose 应用：首页 / 节点 / 统计 / 设置，VpnService，快捷开关 |
| `rust-core/` | Rust 流量层，基于 tun2proxy，JNI 接口在 `src/ffi.rs` |
| `xray-wrapper/` | xray-core 的 gomobile 封装（`libxray`），产物为 `app/libs/xray.aar` |

支持 VMess、VLESS（含 Reality）、Trojan、Shadowsocks、Mieru、Hysteria/Hysteria2、TUIC、WireGuard、SSR、HTTP、SOCKS、AnyTLS 和 Sudoku 节点；支持 Clash YAML、二维码导入/分享、HTTPS 订阅、规则分流与分应用代理。AnyTLS 支持 `anytls://` 分享链接；Sudoku 节点可通过 Clash YAML/JSON 导入、编辑和分享。

## 构建

需要：JDK 17、Android SDK 36 + NDK、Rust（含 `cargo-ndk`）、Go 1.26+。

```powershell
# 1. Rust 核心 → app/src/main/jniLibs/
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
.\rust-core\build-android.ps1

# 2. xray-core → app/libs/xray.aar
.\xray-wrapper\build-aar.ps1

# 3. APK
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease
```

Linux/macOS 使用对应的 `.sh` 脚本。缺失原生产物时仍可构建 debug 进行界面开发，连接时会提示缺少核心；release 构建会拒绝缺失原生库的产物。

## Windows 注意事项

项目路径含中文时，Gradle 单元测试会出现 `ClassNotFoundException`。可以把项目放到纯英文路径，或者临时映射一个盘符：

```powershell
subst X: "E:\脚本编写存放\git"
cd X:\IceFlow; .\gradlew.bat testDebugUnitTest
subst X: /D
```

rustup / gomobile 安装在用户目录，未自动加入 PATH，使用前执行：

```powershell
$env:PATH = "$env:USERPROFILE\.cargo\bin;$env:USERPROFILE\go\bin;$env:PATH"
```

`xray-wrapper/libxray` 中导出符号和包的文档注释会被 gomobile 复制进 Java 源码，中文 Windows 上 javac 按 GBK 读取会报错，因此这些注释只能用 ASCII。

## 测试

- `app/src/test`：分享链接解析、xray 配置生成、格式化（JVM 单元测试）
- `app/src/androidTest`：连接按钮 Compose UI 测试；`NativeBridgeTest` 验证 Rust/xray 原生库加载、各协议配置被 xray-core 接受、SOCKS 入站局域网直连（需设备/模拟器，不连外网）
- `rust-core`：`cargo test`
- `xray-wrapper`：`go test ./...`

`connectedDebugAndroidTest` 结束时会卸载应用（清空已导入节点）。想保留数据可以手动安装后运行：

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w com.iceflow.test/androidx.test.runner.AndroidJUnitRunner
```

## 已知限制

- 订阅仅接受 HTTPS 地址；下载内容上限为 4 MiB，路由数据库上限为 64 MiB。
- Lottie 动画尚未集成。
- 设置中的”备份与恢复”导出节点、订阅、分组、规则及设置。备份是包含凭据的 JSON 文件，恢复前会预览数量并要求确认；路由数据库不包含在备份中。
- 连接参数、所选节点或代理链改变后自动重连；外观设置不重连。
- 内部 TUN 入口使用随机生成的凭据保护，防止本机其他应用探测或滥用。
- 当前连接测速通过运行中的内部 SOCKS 入口，与节点测速共用 Go HTTP/TLS 实现，避免 Android 系统版本造成握手耗时差异。
- 网络中断和核心异常时保留已建立的 TUN，网络恢复后重试；核心重启最多连续重试 5 次。进程被系统终止时的强制阻断需启用 Android 系统的”始终开启 VPN”和”阻止无 VPN 连接”。
- DNS 分流：直连域名使用国内 DNS（默认 223.5.5.5），代理域名使用远端 DNS，避免 CDN 污染。
- 统计页面显示按出站（代理/直连/阻止）的流量分布，帮助排查分流规则。

## 发布与回归

构建使用 `JAVA_HOME` 或 IDE 的 Gradle JDK 设置，仓库不指定本机 JDK 路径。
`assembleRelease` 会检查三个 ABI 的 Rust/Xray 原生产物。未提供签名配置时输出 unsigned APK；正式签名通过四个环境变量配置：
`ICEFLOW_KEYSTORE_PATH`、`ICEFLOW_KEYSTORE_PASSWORD`、`ICEFLOW_KEY_ALIAS`、`ICEFLOW_KEY_PASSWORD`。密钥和密码不写入仓库。
版本通过 `-Piceflow.versionCode=2 -Piceflow.versionName=0.1.1` 指定；每次正式更新需递增 versionCode。

CI 运行单元测试、lint、debug/release 构建和 Android 35 模拟器测试。
本地多设备测试请手动安装并明确指定设备，避免 `connectedDebugAndroidTest` 对所有设备安装和卸载应用：

```powershell
adb -s emulator-5556 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5556 install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s emulator-5556 shell am instrument -w com.iceflow.test/androidx.test.runner.AndroidJUnitRunner
```

`VpnLifecycleTest` 暂时替换并恢复原节点及设置，使用独立 UID 验证真实 IPv4/IPv6 TUN 转发。
物理网络开关测试需要 `-e networkToggle true`；仅在 ADB 不依赖被切换网络时启用。
