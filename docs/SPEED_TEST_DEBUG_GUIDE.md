# 测速功能问题诊断和修复指南

## 问题描述
启动按钮后弹出的启动栏右边的测速功能，不管什么节点能不能用都显示 200ms。

## 已完成的修复

### 1. 添加详细日志
- ✅ 在 `ConnectionProbe.kt` 中添加了详细的测速日志
- ✅ 在 `MainViewModel.kt` 中添加了测速流程日志
- ✅ 记录 SOCKS 连接时间、SSL 握手时间、HTTP 请求时间

### 2. 日志内容
现在每次测速都会输出：
```
ConnectionProbe: 开始测速: url=xxx, socksPort=10808, target=xxx
ConnectionProbe: SOCKS连接成功: XXms
ConnectionProbe: SSL握手成功: XXms  
ConnectionProbe: HTTP请求完成: XXms, status=HTTP/1.1 204
ConnectionProbe: 测速完成: XXms
MainViewModel: 测速成功: XXms
```

## 诊断步骤

### 方法 1: 使用诊断脚本（推荐）
```bash
bash diagnose-speed-test.sh
```

然后按照脚本提示操作。

### 方法 2: 手动诊断
```bash
# 1. 清空日志
adb -s emulator-5556 logcat -c

# 2. 启动应用并连接 VPN
adb -s emulator-5556 shell am start -n com.iceflow/.MainActivity

# 3. 在模拟器中点击测速

# 4. 查看日志
adb -s emulator-5556 logcat -d -s "ConnectionProbe:*" "MainViewModel:*"
```

## 可能的原因分析

### 原因 1: 测速功能未被触发
**症状：** 日志中没有 "开始测速" 的记录

**原因：**
- 点击区域不正确
- VPN 未连接
- UI 事件绑定问题

**解决方案：**
- 确保点击连接面板右侧区域（显示延迟/时间的地方）
- 确保 VPN 已连接（启动按钮显示蓝色）

### 原因 2: 测速总是返回固定值
**症状：** 每次测速都是相同的延迟（如 200ms）

**可能原因：**
- 网络请求被缓存
- SOCKS 代理未正常工作
- 测试 URL 响应固定
- 代码中有硬编码的返回值

**检查方法：**
查看日志中的详细时间：
```
ConnectionProbe: SOCKS连接成功: 50ms
ConnectionProbe: SSL握手成功: 120ms
ConnectionProbe: HTTP请求完成: 30ms
ConnectionProbe: 测速完成: 200ms  <-- 总时间应该是各部分之和
```

如果总时间与各部分不匹配，说明有问题。

### 原因 3: 代理未正常工作
**症状：** 测速超时或固定延迟

**检查方法：**
```bash
# 检查 SOCKS5 端口是否监听
adb -s emulator-5556 shell "netstat -tuln | grep 10808"

# 应该看到：
# tcp  0  0 127.0.0.1:10808  0.0.0.0:*  LISTEN
```

### 原因 4: 测试 URL 问题
**当前默认：** `https://www.gstatic.com/generate_204`

**可能问题：**
- URL 被墙或无法访问
- 响应时间固定

**解决方案：**
在设置中修改测试 URL，尝试：
- `https://www.google.com/generate_204`
- `https://1.1.1.1/cdn-cgi/trace`
- `https://www.cloudflare.com/cdn-cgi/trace`

## 下一步行动

### 立即执行
1. 运行诊断脚本：`bash diagnose-speed-test.sh`
2. 按照提示操作并查看结果
3. 将诊断输出发送给我

### 如果测速未触发
检查 `IceFlowRoot.kt` 第 347 行的点击事件：
```kotlin
.clickable(enabled = connected && !test.testing, onClick = onTest)
```

确保：
- `connected` 为 true（VPN 已连接）
- `test.testing` 为 false（没有正在测速）
- `onTest` 正确指向 `vm::testRunningConnection`

### 如果测速固定为 200ms
1. 查看详细日志中各个阶段的时间
2. 检查是否是网络环境问题（模拟器网络配置）
3. 尝试更换测试 URL

## 临时测试方案

如果想快速验证测速逻辑是否正常，可以在代码中硬编码一个随机延迟：

```kotlin
// 在 ConnectionProbe.measure() 开头添加（仅用于测试）
android.util.Log.w(TAG, "测试模式：返回随机延迟")
return (100..500).random()
```

如果这样改后延迟会变化，说明测速逻辑本身是正常的，问题在于网络层面。

## 需要的信息

请提供以下信息以便进一步诊断：

1. **诊断脚本的完整输出**
2. **是否看到 "测速中…" 的提示**
3. **每次测速是否都是相同的延迟值**
4. **VPN 连接是否正常（能否正常上网）**
5. **切换不同节点后延迟是否变化**

## 文件位置

- 诊断脚本：`diagnose-speed-test.sh`
- 修改的文件：
  - `app/src/main/java/com/iceflow/core/ConnectionProbe.kt`
  - `app/src/main/java/com/iceflow/ui/MainViewModel.kt`
- APK：`app/build/outputs/apk/debug/app-debug.apk`（已包含详细日志）
