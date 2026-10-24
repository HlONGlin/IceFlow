# 测速功能修复报告

## 问题分析

### 原因
测速结果随机在 200ms 左右，不管什么节点都是这个延迟，根本原因是：

1. **IceFlow 应用被排除在 VPN 之外**
   - 在 `IceFlowVpnService.kt` 中，应用通过 `.addDisallowedApplication(packageName)` 将自己排除在 VPN 隧道外
   - 这是正确的设计，防止代理流量回环

2. **旧的测速方法使用 SOCKS 代理**
   - `ConnectionProbe.measure()` 通过连接 `127.0.0.1:10808` 的 SOCKS 端口进行测速
   - 但由于应用自身被排除在 VPN 外，实际的 HTTP 请求走的是**系统直连**
   - 因此测的是本地网络到 `www.gstatic.com` 的延迟，而不是通过代理节点的延迟

3. **日志证据**
   ```
   SOCKS连接成功: 0ms  ← 本地回环，几乎不耗时
   SSL握手成功: 153ms  ← 直连到 gstatic.com
   测速完成: 215ms     ← 总延迟 = 本地直连延迟
   ```

## 修复方案

使用 `XrayBridge.measureDelay()` 替代 `ConnectionProbe.measure()`：

### 修改前
```kotlin
ConnectionProbe.measure(session.socksPort, settings.value.testUrl)
```
- 通过已连接的 SOCKS 端口测速
- 受 VPN 排除影响，走直连

### 修改后
```kotlin
val config = XrayConfigBuilder.build(profile, settings.value, store)
XrayBridge.measureDelay(config, settings.value.testUrl, 5000L).toInt()
```
- 创建临时的 xray-core 实例直接测试代理节点
- 不依赖 VPN 连接状态
- 真正测量代理节点到目标的延迟

## 优势

1. **准确性**：真正测量代理节点的延迟
2. **独立性**：不受 VPN 连接状态影响
3. **一致性**：与节点列表的测速功能使用相同的方法

## 修改的文件

- `app/src/main/java/com/iceflow/ui/MainViewModel.kt`
  - 修改 `testRunningConnection()` 方法
  - 使用 `XrayBridge.measureDelay` 进行测速

## 测试方法

1. 连接不同地区的节点（香港、美国、日本等）
2. 点击连接面板触发测速
3. 观察延迟值是否有明显差异
4. 预期结果：
   - 香港节点：50-150ms
   - 美国节点：150-300ms
   - 欧洲节点：200-400ms
   - 测速失败或节点不可用：显示"超时 · 重试"

## 备注

- `ConnectionProbe` 保留用于未来可能的其他测速场景
- 节点列表的批量测速已经使用 `XrayBridge.measureDelay`，现在连接面板的测速也保持一致
- 测速会创建临时的 xray-core 实例，可能会比直接使用 SOCKS 端口略慢，但结果更准确
