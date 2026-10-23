# IceFlow

基于 xray-core 的 Android 代理客户端（Android 8.0+）。设计文档见 `IceFlow-Architecture.md`。

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

支持 VMess、VLESS（含 Reality）、Trojan、Shadowsocks、Mieru、Hysteria/Hysteria2、TUIC、WireGuard、SSR、HTTP 和 SOCKS 节点；支持 Clash YAML、二维码导入/分享、HTTPS 订阅、规则分流与分应用代理。扩展协议的可用参数取决于分享链接或 Clash 配置所提供的信息。

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

Linux/macOS 使用对应的 `.sh` 脚本。两个原生产物缺失时 app 仍能编译运行，但连接时会提示缺少核心。

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
