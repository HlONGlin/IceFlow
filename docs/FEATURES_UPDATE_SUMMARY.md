# IceFlow 功能优化总结

## 已完成的功能更新

### 1. ✅ 节点分享功能优化
- **新增二维码分享**：点击分享节点后，可以选择显示二维码或分享链接
- **一键复制到剪贴板**：直接复制分享链接到剪贴板
- **优化界面**：使用对话框展示，支持二维码/链接切换

### 2. ✅ 分应用代理优化
- **统一开关设计**：绕过应用和分应用代理改为单一开关
  - 关闭：绕过选中应用（其他应用走代理）
  - 开启：仅代理选中应用（其他应用直连）
  - 两者都关闭：全部按路由规则代理
- **优化的应用选择器**：
  - 显示应用图标
  - 支持搜索应用名称和包名
  - 可以筛选显示/隐藏系统应用
  - 显示已选择应用数量
  - 美观的 GlassCard 设计

### 3. ✅ 运行日志优化
- **自动滚动到底部**：打开日志页面自动显示最新日志
- **日志自动清理功能**：
  - 可选保留时间：1天、3天、7天、14天、30天、永久保留
  - 默认保留3天
  - VPN 连接时自动清理过期日志
- **优化界面**：显示当前日志保留策略

### 4. ✅ SOCKS5 代理增强
- **删除 HTTP 路由功能**
- **新增 SOCKS5 认证**：
  - 支持设置用户名和密码
  - 密码可显示/隐藏
  - 未设置时显示安全提示
- **显示端口信息**：默认端口 10808

### 5. ✅ 路由模式选择
- **三种模式**：
  - **规则模式**：根据路由规则分流（默认）
  - **全局模式**：所有流量走代理
  - **直连模式**：所有流量直连
- **下拉选择**：带说明的模式选择器

### 6. ✅ 流量统计功能
- **显示开关**：可选择是否在节点卡片显示使用流量
- **实时统计**：开启后显示每个节点的流量使用情况

### 7. ✅ 测速 URL 更新
- **新 URL**：`https://www.google.com/generate_204`
- **修复测速问题**：使用 `XrayBridge.measureDelay` 直接测试节点延迟
- **准确性提升**：真正测量代理节点到目标的延迟，不受 VPN 排除影响

### 8. ✅ 设置界面全面重构
- **现代化设计**：使用 GlassCard 和 Material Design 3
- **更好的组织**：
  - 路由模式配置
  - 代理配置（SOCKS5 端口、认证）
  - 分应用代理
  - DNS 和网络
  - 流量统计
  - 运行日志
  - 关于信息
- **一致的主题**：与应用整体 UI 风格保持一致

## 数据模型更新

### AppSettings 新增字段
```kotlin
data class AppSettings(
    // ... 原有字段 ...
    val socksUsername: String = "",              // SOCKS5 用户名
    val socksPassword: String = "",              // SOCKS5 密码
    val routingMode: RoutingMode = RoutingMode.RULE,  // 路由模式
    val showTrafficStats: Boolean = false,       // 显示流量统计
    val logRetentionDays: Int = 3,              // 日志保留天数
    val appProxyMode: Boolean = false,          // 应用代理模式
)
```

### 新增枚举
```kotlin
enum class RoutingMode(val label: String) {
    RULE("规则"),
    GLOBAL("全局"),
    DIRECT("直连")
}
```

## 技术实现细节

### 1. 二维码生成
- 使用 ZXing 库生成二维码
- 512x512 像素高清二维码
- 支持所有协议的分享链接

### 2. 应用选择器
- 异步加载已安装应用列表
- 使用协程在 IO 线程读取应用信息
- 实时搜索过滤
- 图标缓存优化

### 3. 日志自动清理
- 基于时间戳解析日志行
- 过滤保留指定天数内的日志
- VPN 连接时触发清理任务

### 4. 测速修复
- 从 `ConnectionProbe.measure` 改为 `XrayBridge.measureDelay`
- 创建临时 xray-core 实例进行测速
- 避免因 VPN 排除自身应用导致的测速不准

## 文件结构

### 新增文件
- `app/src/main/java/com/iceflow/ui/screen/AppSelectorScreen.kt` - 应用选择器
- `app/src/main/java/com/iceflow/ui/component/ShareNodeDialog.kt` - 节点分享对话框

### 修改文件
- `app/src/main/java/com/iceflow/data/model/ServerProfile.kt` - 数据模型
- `app/src/main/java/com/iceflow/data/repository/SettingsRepository.kt` - 设置持久化
- `app/src/main/java/com/iceflow/ui/screen/SettingsScreen.kt` - 设置界面完全重构
- `app/src/main/java/com/iceflow/ui/RuntimeLogPage.kt` - 日志页面优化
- `app/src/main/java/com/iceflow/ui/IceFlowRoot.kt` - 主界面集成
- `app/src/main/java/com/iceflow/ui/MainViewModel.kt` - 添加日志清理逻辑
- `app/src/main/java/com/iceflow/service/AppLog.kt` - 日志清理功能

## 用户体验改进

### 视觉优化
- ✅ 所有新界面使用 GlassCard 毛玻璃效果
- ✅ 保持与现有主题一致的配色
- ✅ 清晰的层级结构和分组
- ✅ 适当的间距和对齐

### 交互优化
- ✅ 下拉菜单替代多级导航
- ✅ 开关直观显示当前状态
- ✅ 实时搜索提供即时反馈
- ✅ 长按复制等常用手势支持

### 信息展示
- ✅ 关键信息前置显示
- ✅ 次要信息适当隐藏
- ✅ 错误提示清晰明确
- ✅ 加载状态友好提示

## 测试建议

1. **节点分享**：测试二维码生成和扫码导入
2. **应用选择器**：测试搜索、过滤、选择功能
3. **日志清理**：设置不同保留天数，连接 VPN 验证清理
4. **SOCKS5 认证**：设置用户名密码，测试其他应用连接
5. **路由模式**：切换三种模式，验证流量走向
6. **流量统计**：开启后验证节点卡片显示
7. **测速功能**：连接不同节点测试延迟准确性

## 兼容性说明

- ✅ 所有新功能向后兼容
- ✅ 旧版本设置自动迁移到新版本
- ✅ 默认值确保无需额外配置即可使用

## 性能优化

- ✅ 应用列表异步加载，不阻塞 UI
- ✅ 二维码按需生成
- ✅ 日志清理在后台线程执行
- ✅ 搜索过滤使用 remember 缓存

## 安全性改进

- ✅ SOCKS5 认证防止未授权访问
- ✅ 密码输入支持隐藏显示
- ✅ 敏感信息不记录到日志
- ✅ 应用权限最小化原则
