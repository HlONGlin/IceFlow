# IceFlow 核心改进总结

本次改进基于分支 `feature/core-improvements`，解决了必须补充和应该补充的功能缺陷。

## 改进内容

### 1. 工程结构优化 (701adc2)
- 将文档移至 `docs/` 目录
- 将脚本移至 `scripts/` 目录
- 添加 `.opencode/` 到 `.gitignore`，避免会话数据污染仓库

### 2. 内部 SOCKS 隧道鉴权 (e909d78) ✅ 必须补
**问题**：TUN 入站（tun2proxy → xray）使用 noauth SOCKS，本机其他应用可探测或滥用。

**解决方案**：
- 每次连接时生成随机用户名和密码（UUID）
- 通过 SOCKS5 URL (`socks5://user:pass@127.0.0.1:port`) 传递给 tun2proxy
- xray 配置中为 TUN 入站添加鉴权
- 外部 SOCKS 服务器设置不受影响

**影响文件**：
- `app/src/main/java/com/iceflow/data/XrayConfigBuilder.kt`
- `app/src/main/java/com/iceflow/service/IceFlowVpnService.kt`

### 3. DNS 分流 (ab3e7f5) ✅ 必须补
**问题**：只有一个远端 DNS，直连域名解析结果不理想，CDN 可能返回海外节点。

**解决方案**：
- 添加 `domesticDns` 设置（默认 223.5.5.5）
- xray DNS 配置中添加两个服务器：
  - 国内 DNS：匹配直连规则的域名 + geosite:cn
  - 远端 DNS：默认，用于代理域名
- 根据路由规则动态生成 DNS 匹配列表

**影响文件**：
- `app/src/main/java/com/iceflow/data/model/ServerProfile.kt`（AppSettings 增加 domesticDns 字段）
- `app/src/main/java/com/iceflow/data/repository/SettingsRepository.kt`
- `app/src/main/java/com/iceflow/data/XrayConfigBuilder.kt`

### 4. 流量明细（Stats API）(216f51a) ✅ 应该补
**问题**：只有 Rust 层的总流量，无法查看按出站（proxy / direct / block）的流量分布。

**解决方案**：
- 在 xray 配置中启用 stats 和 policy
- 添加 Go 函数 `QueryStats()` 查询 xray 统计
- Kotlin 侧解析统计数据并分类
- 统计页面展示各出站的流量明细

**新增文件**：
- `native/xray-wrapper/libxray/stats.go`
- `app/src/main/java/com/iceflow/data/XrayStats.kt`

**影响文件**：
- `app/src/main/java/com/iceflow/core/XrayBridge.kt`（添加 queryStats 方法）
- `app/src/main/java/com/iceflow/service/VpnController.kt`（TrafficSnapshot 增加 byOutbound 字段）
- `app/src/main/java/com/iceflow/service/IceFlowVpnService.kt`（statsLoop 中查询 xray stats）
- `app/src/main/java/com/iceflow/ui/screen/StatsScreen.kt`（展示分流统计）

### 5. Clash 规则导入 (b11c654 + 522a5fc) ✅ 应该补
**问题**：无法导入 Clash 配置中的 rules，用户需要手动逐条配置。

**解决方案**：
- 添加 `ClashYamlParser.parseRules()` 函数
- 支持规则类型：
  - DOMAIN / DOMAIN-SUFFIX / DOMAIN-KEYWORD / GEOSITE
  - IP-CIDR / IP-CIDR6 / GEOIP
- 映射 Clash 策略（PROXY / DIRECT / REJECT）到 IceFlow 动作
- UI 中添加"从剪贴板导入 Clash 规则"菜单项

**影响文件**：
- `app/src/main/java/com/iceflow/data/ClashYamlParser.kt`
- `app/src/main/java/com/iceflow/ui/RoutingPage.kt`

## 测试建议

### 1. 内部 SOCKS 鉴权
- 连接 VPN 后，尝试用第三方 SOCKS 客户端连接内部端口
- 应该无法连接（需要凭据）

### 2. DNS 分流
- 配置规则：国内域名直连
- 连接后检查直连域名的解析结果（应通过国内 DNS，延迟更低）
- 代理域名应通过远端 DNS

### 3. Stats API
- 连接 VPN 后，访问不同类型的网站（国内直连、国外代理）
- 在统计页面查看"分流统计"，应显示 proxy / direct 的流量分布

### 4. Clash 规则导入
- 复制 Clash 配置（包含 rules 部分）到剪贴板
- 在路由页面选择"从剪贴板导入 Clash 规则"
- 检查导入的规则是否正确

## 构建说明

本次改动包含 native/xray-wrapper 的新文件，需要重新编译 xray.aar：

```powershell
.\native/xray-wrapper\build-aar.ps1
.\gradlew.bat assembleDebug
```

## 剩余待办

以下问题未在本次改进中解决：

1. **自动选择和故障转移**（必须补）：需要实现 balancer / observatory 或 Clash 策略组解析
2. **许可证声明**（必须补）：添加 LICENSE 文件和第三方许可页面
3. **setUnderlyingNetworks**（应该补）：在 VpnService 中调用，告知系统 VPN 底层网络
4. **崩溃收集**（应该补）：添加 UncaughtExceptionHandler 写入运行日志
5. **国际化**（应该补）：提取 strings.xml，支持多语言

这些功能可在后续 PR 中逐步完成。
