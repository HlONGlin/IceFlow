# IceFlow 快速开发指南

> 从零到一，30 分钟启动项目 🚀

---

## 🛠️ 环境准备

### 必需工具清单

```bash
# 1. Android Studio (最新稳定版)
# 下载: https://developer.android.com/studio

# 2. Rust 工具链
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

# 3. Go 环境 (1.21+)
# 下载: https://go.dev/dl/

# 4. Android NDK
# 在 Android Studio 中: SDK Manager → SDK Tools → NDK (26.x)

# 5. cargo-ndk
cargo install cargo-ndk

# 6. gomobile
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init
```

### 验证安装

```bash
# 检查所有工具
rustc --version          # rust 1.75+
go version              # go 1.21+
cargo --version         # cargo 1.75+
$ANDROID_HOME/ndk/*/ndk-build --version  # NDK 26+
```

---

## 📁 项目初始化

### 第一步：创建项目结构

```bash
# 创建根目录
mkdir IceFlow && cd IceFlow

# 创建 Android 项目
# 使用 Android Studio: New Project → Empty Activity → Jetpack Compose

# 手动创建完整结构
mkdir -p app/src/main/{java/com/iceflow,cpp,res}
mkdir -p app/libs
mkdir -p native/rust-core/src
mkdir -p native/xray-wrapper
```

### 第二步：初始化 Rust 模块

```bash
cd native/rust-core
cargo init --lib

# 修改 Cargo.toml
cat > Cargo.toml << 'EOF'
[package]
name = "iceflow_core"
version = "0.1.0"
edition = "2021"

[lib]
crate-type = ["cdylib", "staticlib"]

[dependencies]
jni = "0.21"
tokio = { version = "1.35", features = ["rt-multi-thread", "net", "io-util"] }
log = "0.4"

[target.'cfg(target_os = "android")'.dependencies]
android_logger = "0.13"

[profile.release]
opt-level = 3
lto = true
codegen-units = 1
strip = true
EOF
```

### 第三步：编写 Rust JNI 桥接

```rust
// native/rust-core/src/lib.rs
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jstring, jint};

#[no_mangle]
pub extern "C" fn Java_com_iceflow_core_RustBridge_startTunDevice(
    env: JNIEnv,
    _class: JClass,
    fd: jint,
) -> jstring {
    #[cfg(target_os = "android")]
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Debug),
    );
    
    log::info!("Starting TUN device with fd: {}", fd);
    
    // TODO: 实现 TUN 设备逻辑
    let result = format!("TUN device started on fd {}", fd);
    
    env.new_string(result)
        .expect("Couldn't create java string!")
        .into_raw()
}

#[no_mangle]
pub extern "C" fn Java_com_iceflow_core_RustBridge_stopTunDevice(
    _env: JNIEnv,
    _class: JClass,
) {
    log::info!("Stopping TUN device");
    // TODO: 清理资源
}
```

### 第四步：编译 Rust 库

```bash
cd native/rust-core

# 创建编译脚本
cat > build-android.sh << 'EOF'
#!/bin/bash
set -e

# NDK 路径 (根据实际情况修改)
export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/26.1.10909125"

# 编译所有架构
for TARGET in aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
do
    echo "Building for $TARGET..."
    cargo ndk -t $TARGET build --release
done

# 复制到 Android 项目
mkdir -p ../app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}
cp target/aarch64-linux-android/release/libiceflow_core.so ../app/src/main/jniLibs/arm64-v8a/
cp target/armv7-linux-androideabi/release/libiceflow_core.so ../app/src/main/jniLibs/armeabi-v7a/
cp target/x86_64-linux-android/release/libiceflow_core.so ../app/src/main/jniLibs/x86_64/

echo "✅ Rust build complete!"
EOF

chmod +x build-android.sh
./build-android.sh
```

---

## 📱 Android 项目配置

### build.gradle.kts (app 级别)

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    kotlin("android")
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
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

### 创建 Rust 桥接类

```kotlin
// app/src/main/java/com/iceflow/core/RustBridge.kt
package com.iceflow.core

object RustBridge {
    init {
        System.loadLibrary("iceflow_core")
    }
    
    external fun startTunDevice(fd: Int): String
    external fun stopTunDevice()
}
```

### 创建 VPN 服务

```kotlin
// app/src/main/java/com/iceflow/service/IceFlowVpnService.kt
package com.iceflow.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.iceflow.core.RustBridge

class IceFlowVpnService : VpnService() {
    
    companion object {
        private const val TAG = "IceFlowVpnService"
    }
    
    private var vpnInterface: ParcelFileDescriptor? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Starting VPN service...")
        
        // 建立 VPN 隧道
        vpnInterface = Builder()
            .setSession("IceFlow")
            .addAddress("10.0.0.2", 24)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)
            .establish()
        
        vpnInterface?.let { vpn ->
            // 启动 Rust 核心
            val result = RustBridge.startTunDevice(vpn.fd)
            Log.i(TAG, "Rust core started: $result")
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        Log.i(TAG, "Stopping VPN service...")
        RustBridge.stopTunDevice()
        vpnInterface?.close()
        super.onDestroy()
    }
}
```

### 创建主界面

```kotlin
// app/src/main/java/com/iceflow/ui/screen/HomeScreen.kt
package com.iceflow.ui.screen

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iceflow.service.IceFlowVpnService

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var isConnected by remember { mutableStateOf(false) }
    
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 大圆形连接按钮
            FilledTonalButton(
                onClick = {
                    if (!isConnected) {
                        // 请求 VPN 权限
                        val intent = android.net.VpnService.prepare(context)
                        if (intent != null) {
                            // TODO: 处理权限请求
                        } else {
                            // 启动服务
                            context.startService(
                                Intent(context, IceFlowVpnService::class.java)
                            )
                            isConnected = true
                        }
                    } else {
                        // 停止服务
                        context.stopService(
                            Intent(context, IceFlowVpnService::class.java)
                        )
                        isConnected = false
                    }
                },
                modifier = Modifier.size(200.dp),
                shape = CircleShape
            ) {
                Text(
                    text = if (isConnected) "断开连接" else "点击连接",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 状态文本
            Text(
                text = if (isConnected) "已连接" else "未连接",
                style = MaterialTheme.typography.titleLarge,
                color = if (isConnected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

### MainActivity

```kotlin
// app/src/main/java/com/iceflow/MainActivity.kt
package com.iceflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.iceflow.ui.screen.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    HomeScreen()
                }
            }
        }
    }
}
```

### AndroidManifest.xml

```xml
<!-- app/src/main/AndroidManifest.xml -->
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="IceFlow"
        android:theme="@style/Theme.Material3.DayNight">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
        <service
            android:name=".service.IceFlowVpnService"
            android:permission="android.permission.BIND_VPN_SERVICE"
            android:exported="false">
            <intent-filter>
                <action android:name="android.net.VpnService" />
            </intent-filter>
        </service>
        
    </application>
</manifest>
```

---

## 🧪 运行测试

### 编译并运行

```bash
# 1. 确保 Rust 库已编译
./native/rust-core/build-android.sh

# 2. 同步 Gradle
./gradlew clean

# 3. 构建 APK
./gradlew assembleDebug

# 4. 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk

# 5. 查看日志
adb logcat | grep -E "IceFlow|RustBridge"
```

### 预期输出

```
I/IceFlowVpnService: Starting VPN service...
I/RustBridge: Starting TUN device with fd: 42
I/IceFlowVpnService: Rust core started: TUN device started on fd 42
```

---

## 📋 下一步开发清单

### 第一周任务

- [ ] 完善 Rust TUN 设备读写逻辑
- [ ] 实现基础 SOCKS5 服务器
- [ ] 添加流量统计功能
- [ ] 完善 UI 交互动画

### xray-core 集成 (第二周)

```bash
# 1. Clone xray-core
cd native/xray-wrapper
git clone https://github.com/XTLS/Xray-core.git

# 2. 创建 Go 封装
cat > wrapper.go << 'EOF'
package xraywrapper

import "C"
import (
    "github.com/xtls/xray-core/core"
)

//export StartXray
func StartXray(configJSON *C.char) *C.char {
    // TODO: 解析配置并启动
    return C.CString("Xray started")
}

//export StopXray
func StopXray() {
    // TODO: 停止 Xray
}
EOF

# 3. 编译为 AAR
gomobile bind -target=android -o=../app/libs/xray.aar .
```

### Mieru 协议支持 (第三周)

参考主文档第 3 节详细实现。

---

## 🐛 常见问题排查

### 问题 1: Rust 编译失败

```bash
# 检查 NDK 环境变量
echo $ANDROID_NDK_HOME

# 重新安装 targets
rustup target add aarch64-linux-android --force
```

### 问题 2: JNI 找不到方法

```bash
# 检查库是否正确加载
adb shell run-as com.iceflow ls -la lib/

# 验证符号表
nm app/src/main/jniLibs/arm64-v8a/libiceflow_core.so | grep Java
```

### 问题 3: VPN 无法启动

```kotlin
// 添加详细日志
override fun onStartCommand(...) {
    val intent = VpnService.prepare(this)
    if (intent != null) {
        Log.e(TAG, "VPN permission not granted!")
        return START_NOT_STICKY
    }
    // ...
}
```

---

## 📚 学习资源

### 推荐阅读顺序

1. **第一天**: Android VpnService 官方文档
2. **第二天**: Rust JNI 绑定教程
3. **第三天**: Jetpack Compose 基础
4. **第四天**: xray-core 配置格式
5. **第五天**: 网络协议基础 (TCP/UDP/SOCKS5)

### 代码参考

- [v2rayNG](https://github.com/2dust/v2rayNG/tree/master/V2rayNG/app/src/main/kotlin/com/v2ray/ang/service)
- [rust-jni examples](https://github.com/jni-rs/jni-rs/tree/master/example)
- [Now in Android](https://github.com/android/nowinandroid) - Compose 最佳实践

---

## ✅ 检查清单

完成基础搭建后，确认以下功能：

- [ ] APK 成功安装到测试设备
- [ ] 点击连接按钮能看到 VPN 图标
- [ ] Logcat 能看到 Rust 日志输出
- [ ] UI 动画流畅无卡顿
- [ ] 应用能正常前后台切换

**下一步**: 开始实现完整 TUN 设备逻辑 → 参考主文档第 4.2 节

---

*最后更新: 2026-10-22*  
*有问题？查看主文档 `IceFlow-Architecture.md`* ❄️
