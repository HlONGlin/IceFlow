# IceFlow 项目架构文档

> 基于 xray-core 的高性能 Android 代理客户端  
> 设计理念：性能与美学的完美平衡

---

## 📋 项目概览

**项目名称：** IceFlow (冰流)  
**目标平台：** Android 8.0+ (API 26+)  
**核心特性：**
- 基于 xray-core 的完整协议支持
- 原生支持 mieru 协议
- Exclave 级别的 UI/UX 体验
- 生产级性能优化

**开发周期：** 4-5 个月  
**团队规模：** 2-3 人（1 后端 + 1 Android + 0.5 UI/UX）

---

## 🏗️ 系统架构

### 架构分层

```
┌─────────────────────────────────────────┐
│         Jetpack Compose UI Layer         │  ← Material You + 玻璃态设计
├─────────────────────────────────────────┤
│      Android Application Layer           │  ← ViewModel + Repository
├─────────────────────────────────────────┤
│         VPN Service Layer                │  ← Android VpnService
├─────────────────────────────────────────┤
│  Rust Traffic Layer (tun2socks-rust)    │  ← 高性能流量捕获
├─────────────────────────────────────────┤
│    xray-core Protocol Engine (Go)        │  ← 协议转换核心
├─────────────────────────────────────────┤
│  Mieru Protocol Handler (Rust)           │  ← 自研协议支持
└─────────────────────────────────────────┘
          ↓
    [Internet] 互联网出口
```

---

## 🔧 技术栈详解

### 1. UI 层技术栈

| 组件 | 技术选型 | 用途 |
|------|---------|------|
| UI 框架 | Jetpack Compose 1.6+ | 声明式 UI |
| 设计系统 | Material 3 (Material You) | 动态配色 + 现代组件 |
| 动画 | Lottie + Compose Animation | 复杂动画 + 流体过渡 |
| 图表 | Canvas 自绘 + Vico | 实时流量可视化 |
| 导航 | Compose Navigation | 类型安全路由 |
| 主题 | accompanist-systemuicontroller | 沉浸式状态栏 |

**关键 UI 特性：**
```kotlin
// 玻璃态卡片效果
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            )
            .blur(radius = 30.dp, edgeTreatment = BlurEdgeTreatment.Unbounded),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        content()
    }
}
```

---

### 2. 核心层架构

#### 2.1 Rust 流量处理层

**模块结构：**
```
native/rust-core/
├── src/
│   ├── tun/          # TUN 设备管理
│   ├── socks/        # SOCKS5 服务器
│   ├── routing/      # 路由规则引擎
│   ├── mieru/        # Mieru 协议实现
│   └── ffi/          # JNI 桥接层
├── Cargo.toml
└── build.gradle      # Android NDK 编译配置
```

**关键依赖：**
```toml
[dependencies]
tokio = { version = "1.35", features = ["full", "io-uring"] }
tun = "0.6"
smoltcp = "0.11"
jni = "0.21"
prost = "0.12"  # Protobuf 支持
trust-dns-resolver = "0.23"

[profile.release]
opt-level = 3
lto = "fat"
codegen-units = 1
```

**性能优化配置：**
- 启用 io_uring (Linux 5.1+)
- 零拷贝缓冲区复用
- SIMD 加速加密运算
- 协程池预热

#### 2.2 xray-core 集成

**编译配置：**
```go
// build.gradle 中的 gomobile 配置
gomobile bind -target=android \
    -androidapi=26 \
    -ldflags="-s -w" \
    -o=app/libs/xray.aar \
    ./xray-core-wrapper
```

**Go 侧封装：**
```go
package xraywrapper

import (
    "context"
    "github.com/xtls/xray-core/core"
    "github.com/xtls/xray-core/infra/conf"
)

type XrayInstance struct {
    server *core.Instance
    ctx    context.Context
    cancel context.CancelFunc
}

func (x *XrayInstance) Start(configJSON string) error {
    config := &conf.Config{}
    // 解析配置并启动
    x.server, _ = core.New(config)
    return x.server.Start()
}

func (x *XrayInstance) QueryStats() (up, down int64) {
    // 实时流量统计
    return
}
```

---

### 3. Mieru 协议魔改方案

#### 3.1 协议核心实现

**在 xray-core 中新增 Transport：**
```
xray-core/
└── transport/internet/mieru/
    ├── config.pb.go       # Protobuf 配置定义
    ├── dialer.go          # 客户端拨号器
    ├── hub.go             # 服务端监听器
    ├── session.go         # 会话管理
    └── crypto.go          # 加密实现
```

**配置结构：**
```protobuf
// config.proto
syntax = "proto3";
package xray.transport.internet.mieru;

message Config {
    string password = 1;
    uint32 mtu = 2;
    string encryption = 3;  // AES-128-GCM / ChaCha20-Poly1305
    uint32 multiplexing = 4;
}
```

**Rust 侧实现（性能热路径）：**
```rust
// native/rust-core/src/mieru/session.rs
use tokio::net::UdpSocket;
use chacha20poly1305::{ChaCha20Poly1305, Key, Nonce};

pub struct MieruSession {
    socket: UdpSocket,
    cipher: ChaCha20Poly1305,
    underlay_sessions: Vec<UnderlaySession>,
}

impl MieruSession {
    pub async fn send_packet(&mut self, data: &[u8]) -> Result<()> {
        // 多路径并发发送
        let chunks = self.split_data(data);
        let futures = chunks.iter()
            .zip(&mut self.underlay_sessions)
            .map(|(chunk, session)| session.send(chunk));
        
        try_join_all(futures).await?;
        Ok(())
    }
}
```

#### 3.2 协议特性支持

| 特性 | 实现方式 |
|------|---------|
| 多路复用 | 每个 TCP 连接映射到多条 UDP Underlay |
| 拥塞控制 | BBR 算法 (基于 quiche) |
| 前向纠错 | Reed-Solomon 编码 |
| 流量混淆 | 填充至固定长度 + 随机延迟 |
| 协议探测抵抗 | 首包随机化 + TLS 1.3 伪装 |

---

## 📱 Android 应用层设计

### 4.1 项目结构

```
app/
├── src/main/
│   ├── java/com/iceflow/
│   │   ├── ui/
│   │   │   ├── screen/
│   │   │   │   ├── HomeScreen.kt
│   │   │   │   ├── ServerScreen.kt
│   │   │   │   ├── SettingsScreen.kt
│   │   │   │   └── StatsScreen.kt
│   │   │   ├── component/
│   │   │   │   ├── GlassCard.kt
│   │   │   │   ├── FlowChart.kt
│   │   │   │   └── ServerItem.kt
│   │   │   └── theme/
│   │   │       ├── Color.kt
│   │   │       ├── Theme.kt
│   │   │       └── Type.kt
│   │   ├── data/
│   │   │   ├── model/
│   │   │   ├── repository/
│   │   │   └── local/
│   │   ├── domain/
│   │   │   ├── usecase/
│   │   │   └── repository/
│   │   ├── service/
│   │   │   ├── VpnService.kt
│   │   │   └── TileService.kt
│   │   └── util/
│   ├── cpp/              # JNI 桥接代码
│   └── rust/             # Rust 库编译产物
├── libs/
│   └── xray.aar          # xray-core AAR
└── build.gradle.kts
```

### 4.2 核心服务实现

**VPN 服务：**
```kotlin
class IceFlowVpnService : VpnService() {
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private lateinit var rustCore: RustTunCore
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 建立 VPN 隧道
        vpnInterface = Builder()
            .setSession("IceFlow")
            .addAddress("10.0.0.2", 24)
            .addDnsServer("1.1.1.1")
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)
            .establish()
        
        // 启动 Rust 核心
        val fd = vpnInterface!!.fd
        rustCore = RustTunCore.create(fd)
        rustCore.start()
        
        // 启动 xray-core
        XrayWrapper.start(loadConfig())
        
        return START_STICKY
    }
    
    private external fun startRustCore(fd: Int): Long
}
```

**实时流量统计：**
```kotlin
@Composable
fun TrafficChart(
    trafficData: State<List<TrafficPoint>>
) {
    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val path = Path()
        trafficData.value.forEachIndexed { index, point ->
            if (index == 0) {
                path.moveTo(point.x, point.y)
            } else {
                path.cubicTo(/* 贝塞尔曲线平滑 */)
            }
        }
        
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Cyan.copy(alpha = 0.7f),
                    Color.Blue.copy(alpha = 0.3f)
                )
            ),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
```

---

## 🎨 Exclave 风格 UI/UX 设计

### 5.1 设计语言

**色彩系统：**
```kotlin
// 冰蓝主题
val IceBlue = Color(0xFF00D4FF)
val IceCyan = Color(0xFF4DFFFF)
val FrostWhite = Color(0xFFF0F8FF)
val DeepBlue = Color(0xFF001F3F)

// 动态配色
@Composable
fun IceFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(LocalContext.current)
            else dynamicLightColorScheme(LocalContext.current)
        }
        darkTheme -> darkColorScheme(primary = IceBlue, ...)
        else -> lightColorScheme(primary = IceCyan, ...)
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = IceFlowTypography,
        content = content
    )
}
```

**动画规范：**
```kotlin
// 全局动画配置
object IceFlowMotion {
    val FastDuration = 200.milliseconds
    val MediumDuration = 400.milliseconds
    val SlowDuration = 600.milliseconds
    
    val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    
    // 共享元素转场
    @Composable
    fun SharedAxisTransition() {
        slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(MediumDuration, easing = EaseInOutCubic)
        ) + fadeIn()
    }
}
```

### 5.2 核心页面设计

#### 首页 - 连接控制
```
┌─────────────────────────────┐
│  ❄️ IceFlow        ⚙️ 设置  │
├─────────────────────────────┤
│                             │
│    [玻璃态大圆形按钮]        │
│         未连接              │
│      点击以连接              │
│                             │
│   ┌─────────────────────┐   │
│   │ 🌐 Hong Kong 01     │   │
│   │ 📶 25ms  ↑ 0 KB/s  │   │
│   └─────────────────────┘   │
│                             │
│   [实时流量曲线图]           │
│                             │
└─────────────────────────────┘
```

#### 服务器列表 - 延迟热力图
```kotlin
@Composable
fun ServerListScreen() {
    LazyColumn {
        items(servers) { server ->
            ServerCard(
                server = server,
                pingColor = when (server.latency) {
                    in 0..50 -> Color.Green
                    in 51..150 -> Color.Yellow
                    else -> Color.Red
                },
                onClick = { selectServer(server) }
            )
        }
    }
}
```

---

## 🚀 开发路线图

### Phase 1: 基础架构 (4-6 周)

**Week 1-2: 环境搭建**
- [ ] 配置 Rust + NDK 工具链
- [ ] 集成 xray-core (gomobile 编译)
- [ ] 搭建 Android 项目骨架
- [ ] 配置 CI/CD (GitHub Actions)

**Week 3-4: 核心功能**
- [ ] 实现 VpnService 基础逻辑
- [ ] Rust tun2socks 集成
- [ ] xray-core 配置解析
- [ ] 基础 UI 框架 (Compose + Navigation)

**Week 5-6: 协议支持**
- [ ] xray-core 常用协议测试 (VMess/VLESS/Trojan)
- [ ] 实现配置导入 (订阅/二维码)
- [ ] 基础连接功能完成

### Phase 2: Mieru 协议开发 (3-4 周)

**Week 7-8: 协议实现**
- [ ] 在 xray-core 中添加 mieru transport
- [ ] Rust 侧实现 mieru 加密层
- [ ] 多路径复用逻辑
- [ ] 单元测试 + 集成测试

**Week 9-10: 性能优化**
- [ ] 启用 io_uring
- [ ] 零拷贝优化
- [ ] 内存池管理
- [ ] 性能基准测试

### Phase 3: UI/UX 打磨 (4-5 周)

**Week 11-12: 视觉设计**
- [ ] Material You 动态配色
- [ ] 玻璃态组件库
- [ ] Lottie 动画集成
- [ ] 深色模式适配

**Week 13-14: 交互优化**
- [ ] 实时流量图表
- [ ] 延迟热力地图
- [ ] 手势交互 (滑动/长按)
- [ ] 触觉反馈

**Week 15: 细节完善**
- [ ] 空状态设计
- [ ] 加载动画
- [ ] 错误提示优化
- [ ] 无障碍支持

### Phase 4: 测试与发布 (2-3 周)

**Week 16-17: 测试**
- [ ] 单元测试覆盖 >80%
- [ ] UI 测试 (Compose Test)
- [ ] 性能测试 (Profiler)
- [ ] 多设备兼容性测试

**Week 18: 发布准备**
- [ ] 代码混淆 (R8)
- [ ] 签名配置
- [ ] Google Play 素材准备
- [ ] Beta 测试 (内部)

---

## 📊 性能指标

### 目标性能

| 指标 | 目标值 | 测试方法 |
|------|--------|---------|
| 连接延迟 | < 8ms | iperf3 本地测试 |
| 吞吐量 | > 500 Mbps | 千兆网卡环境 |
| 内存占用 | < 80MB | 连接状态稳定后 |
| 电池消耗 | < 5%/h | 息屏挂机 8 小时 |
| 启动时间 | < 2s | 冷启动到连接成功 |
| UI 帧率 | 60 FPS | 滚动/动画场景 |

### 性能测试工具

```bash
# 网络性能
adb shell am instrument -w \
    -e class com.iceflow.benchmark.NetworkBenchmark \
    com.iceflow.test/androidx.test.runner.AndroidJUnitRunner

# UI 性能
adb shell dumpsys gfxinfo com.iceflow framestats

# 内存分析
adb shell dumpsys meminfo com.iceflow

# 电量统计
adb shell dumpsys batterystats --reset
# 运行 8 小时后
adb shell dumpsys batterystats com.iceflow
```

---

## 🔒 安全设计

### 数据安全
- 敏感配置使用 Android Keystore 加密存储
- 内存中的密码使用 SecureString (native 侧)
- 禁用截屏 (连接状态下)

### 网络安全
- 证书 Pinning (防中间人攻击)
- DNS over HTTPS/TLS
- 防 DNS 泄漏机制

### 代码安全
```gradle
android {
    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt')
            
            // R8 完整模式
            shrinkResources true
            
            // Native 符号表剥离
            ndk {
                debugSymbolLevel 'SYMBOL_TABLE'
            }
        }
    }
}
```

---

## 📦 构建配置

### 完整 build.gradle.kts

```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("org.mozilla.rust-android-gradle.rust-android")
}

android {
    namespace = "com.iceflow"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.iceflow"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

cargo {
    module = "./native/rust-core"
    libname = "iceflow_core"
    targets = listOf("arm64", "arm", "x86_64")
    profile = "release"
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Rust JNI
    implementation(files("libs/iceflow_core.aar"))
    
    // xray-core
    implementation(files("libs/xray.aar"))
    
    // 其他依赖...
}
```

### Rust 编译配置

```toml
# native/rust-core/Cargo.toml
[package]
name = "iceflow_core"
version = "1.0.0"
edition = "2021"

[lib]
crate-type = ["cdylib"]

[dependencies]
jni = "0.21"
tokio = { version = "1.35", features = ["rt-multi-thread", "io-util", "net"] }
tun = "0.6"

[target.'cfg(target_os = "android")'.dependencies]
android_logger = "0.13"

[profile.release]
opt-level = 3
lto = true
codegen-units = 1
strip = true
```

---

## 🧪 测试策略

### 单元测试覆盖

```kotlin
// app/src/test/java/com/iceflow/
class ConfigParserTest {
    @Test
    fun `parse vmess config correctly`() {
        val json = """{"protocol": "vmess", ...}"""
        val config = ConfigParser.parse(json)
        assertEquals("vmess", config.protocol)
    }
}
```

### Compose UI 测试

```kotlin
// app/src/androidTest/java/com/iceflow/
@Test
fun connectButton_clickChangesState() {
    composeTestRule.setContent {
        HomeScreen()
    }
    
    composeTestRule
        .onNodeWithTag("ConnectButton")
        .performClick()
    
    composeTestRule
        .onNodeWithText("连接中...")
        .assertIsDisplayed()
}
```

---

## 📚 参考资源

### 技术文档
- [xray-core 官方文档](https://xtls.github.io/)
- [Mieru 协议规范](https://github.com/enfein/mieru)
- [Jetpack Compose 指南](https://developer.android.com/jetpack/compose)
- [Android VpnService API](https://developer.android.com/reference/android/net/VpnService)

### 开源参考
- [Exclave](https://github.com/ExclaveNetwork/Exclave) - UI/UX 参考
- [v2rayNG](https://github.com/2dust/v2rayNG) - xray-core 集成
- [clash-rs](https://github.com/Watfaq/clash-rs) - Rust 代理实现
- [leaf](https://github.com/eycorsican/leaf) - 轻量级代理框架

---

## 🎯 里程碑检查清单

### Alpha 版本 (Week 8)
- [x] 基础 VPN 连接
- [x] xray-core 协议支持
- [x] 简单 UI 界面
- [ ] 配置导入功能

### Beta 版本 (Week 15)
- [ ] Mieru 协议支持
- [ ] 完整 UI/UX 实现
- [ ] 性能达标
- [ ] 基础测试完成

### 正式版本 (Week 18)
- [ ] 所有功能完善
- [ ] 通过完整测试
- [ ] 文档齐全
- [ ] 准备发布

---

## 📞 技术支持

### 关键技术难点预警

1. **JNI 内存管理**
   - 问题：Rust/Go/Java 三层内存交互容易泄漏
   - 解决：使用 RAII + 智能指针，定期内存快照对比

2. **协议兼容性**
   - 问题：xray-core 更新可能破坏 mieru 集成
   - 解决：锁定 xray-core 版本，自行维护分支

3. **Android 电源管理**
   - 问题：Doze 模式会杀后台服务
   - 解决：前台服务 + WorkManager 心跳

4. **性能调优**
   - 问题：Rust 和 Go 双运行时开销
   - 解决：热路径用 Rust，协议栈用 Go，减少跨语言调用

---

## 🔄 持续集成配置

```yaml
# .github/workflows/build.yml
name: Build IceFlow

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          
      - name: Setup Rust
        uses: actions-rs/toolchain@v1
        with:
          toolchain: stable
          target: aarch64-linux-android
          
      - name: Setup Go
        uses: actions/setup-go@v4
        with:
          go-version: '1.21'
          
      - name: Setup Android NDK
        run: |
          echo "ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.1.10909125" >> $GITHUB_ENV
          
      - name: Build Rust Core
        run: |
          cd native/rust-core
          cargo ndk build --release --target aarch64-linux-android
          
      - name: Build xray-core AAR
        run: |
          cd native/xray-wrapper
          gomobile bind -target=android -o=../../app/libs/xray.aar
          
      - name: Build APK
        run: ./gradlew assembleRelease
        
      - name: Run Tests
        run: ./gradlew test
```

---

**文档版本：** v1.0  
**最后更新：** 2026-10-22  
**维护者：** IceFlow Team

---

*"如冰川般稳固，似流水般顺畅"* ❄️
