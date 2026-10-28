# IceFlow 核心迁移功能映射

## 0. 基线与判定口径

- 盘点分支：`feat/core-variants`。
- 盘点 HEAD：`47f95f059db2ca7e69ebc08526e852d47024d381`，与正式版基线提交 `47f95f0` 一致；当前 HEAD 也带有 `v0.1.3.0` 标签。
- 正式版基线：`versionName 0.1.3.0`、`versionCode 104`，使用仓库根目录正式签名密钥发布。本文不读取、不记录任何签名值。
- 当前架构：Xray 负责原生协议、SOCKS 入站、DNS、路由和统计；Mihomo v1.19.32 通过 `xray-wrapper` adapter 负责扩展协议；Rust `tun2proxy 0.8.6` 负责 TUN 到 SOCKS 的流量层。
- 相关 pin：xray-core `v1.260327.0`、Mihomo `v1.19.32`、mieru `v3.38.0`。

“Mihomo 落点”使用以下四种值：

- **原生支持**：固定版本的 Mihomo 已有对应协议或能力；仍可能需要把现有 `ServerProfile` 转成 Mihomo 配置。
- **需转换**：能力可以表达，但 Xray JSON、应用设置或字段语义不能直接复用，需要单核配置生成器/转换器。
- **需内核补丁**：固定版本缺少足够的运行时能力，必须在 Mihomo shim、adapter 或 Mihomo 补丁层实现，不能由 Kotlin 侧静默降级。
- **无等价**：当前固定版本没有可证明等价的落点；在解决前只能阻止该配置启动并给出可见提示，不能静默丢字段。

“保留现状”表示双核版必须继续走现有路径，不把迁移工作倒灌到双核行为中。

## 1. 协议与传输组合

### 1.1 协议总表

| 当前功能 | 当前双核实现 | Mihomo 落点 | 单核迁移要求 |
| --- | --- | --- | --- |
| VMess | `XrayConfigBuilder` 原生 `vmess` outbound；支持 UUID、`alterId`、cipher、flow 相关通用字段和 TLS/Reality/传输字段 | 原生支持 | 新生成器把 `ServerProfile` 转为 Mihomo VMess 配置；`alterId > 0` 另见风险 4 |
| VLESS | Xray 原生 `vless` outbound；支持 flow、TLS、Reality 和现有传输字段 | 原生支持 | 配置转换；VLESS+xhttp 是固定版本中唯一已确认的 xhttp 原生路径，另见风险 5 |
| Trojan | Xray 原生 `trojan` outbound；支持密码、TLS 和现有传输字段 | 原生支持 | 配置转换；gRPC/WS 字段和 TCP HTTP 头需单独验证 |
| Shadowsocks | Xray 原生 `shadowsocks` outbound；固定为普通 TCP、无 TLS 传输层 | 原生支持 | 配置转换；SS 插件/HTTP Upgrade 不是当前 Xray 原生字段的直接等价物 |
| Hysteria | `ExtendedConfigBuilder` 生成 adapter options，由 Mihomo adapter 运行 | 原生支持 | 迁移为单核 Mihomo outbound，保留 auth、带宽、端口跳跃、协议和 obfs 字段 |
| Hysteria2 | Mihomo adapter；保留 password、带宽、端口跳跃、obfs、ECH 等选项 | 原生支持 | 配置转换并对 UDP/obfs/ECH 建连回归 |
| TUIC | Mihomo adapter；支持 UUID/password 或 v4 token、拥塞、UDP relay、heartbeat 等 | 原生支持 | 配置转换并保留 v4 token 与 v5 凭据分支 |
| WireGuard | Mihomo adapter；支持 private/public key、地址、PSK、MTU、keepalive、reserved、allowed-ips、DNS | 原生支持 | 配置转换；确认 TUN 由 Mihomo 接管时地址和 DNS 不被重复配置 |
| ShadowsocksR | Mihomo adapter；支持 cipher、password、protocol/param、obfs/param | 原生支持 | 配置转换并逐字段回归 SSR 分享链接及 Clash 节点 |
| HTTP | Mihomo adapter；支持 TLS、用户名密码和原始 Clash 选项 | 原生支持 | 配置转换；明确 HTTP outbound 仅 TCP，不误开 UDP |
| SOCKS5 | Mihomo adapter；支持 TLS、用户名密码和原始 Clash 选项 | 原生支持 | 配置转换；区分远端 SOCKS5 节点与本地 SOCKS 入站 |
| mieru | Mihomo adapter + 当前 `mieru.go` 官方客户端逻辑；支持 `mieru`/`mierus` 链接、端口范围、TCP/UDP、复用、握手和 MTU | 需内核补丁 | 将官方客户端的 MTU、TCP、UDP、链式 dialer 和生命周期逻辑移入单核 shim；见风险 6 |
| AnyTLS | Mihomo adapter；支持密码、client fingerprint、session 选项和 ECH/ShadowTLS/Restls/JLS 原始 JSON 选项 | 原生支持 | 配置转换；不能丢失 `clashConfig` 中单 URI 无法表达的嵌套选项 |
| Sudoku | Mihomo adapter；支持 key、AEAD、table、padding、HTTP mask 和自定义表等选项 | 原生支持 | 配置转换；分享时当前使用 Clash YAML/JSON 保留原始字段 |

固定版本的 Mihomo 源码已包含上述 14 类 outbound 文件/解析入口。这里的“原生支持”只表示内核有能力，不表示现有 Kotlin 配置可直接喂给 Mihomo；单核必须建立独立生成器，不能复用 Xray JSON。

### 1.2 当前传输组合的精确边界

当前 `ServerProfile.network` 和 `XrayConfigBuilder.streamSettings` 支持：`tcp`/`raw`、`ws`、`grpc`、`httpupgrade`、`xhttp`；TCP/RAW 还可选 Xray TCP HTTP 头伪装，TLS 层支持 `none`/`tls`/`reality`，WS 支持 path/Host，gRPC 支持 serviceName。

| 传输组合 | 当前双核 | Mihomo 落点 | 说明 |
| --- | --- | --- | --- |
| TCP/RAW 无伪装 | Xray 原生 | 原生支持，需转换 | VMess/VLESS/Trojan 基础 TCP 可映射；SS 当前强制 TCP/none |
| TCP/RAW + HTTP 头 | Xray `tcpSettings.header` | 需转换；部分组合需内核补丁或无等价 | VMess/VLESS 有 `http-opts` 入口，但字段语义要验证；Trojan 等没有已确认的通用等价，见风险 3 |
| WebSocket | Xray 原生 | 原生支持，需转换 | Host/path/early-data 等嵌套字段必须完整映射，Clash 原始字段优先保留 |
| gRPC 单流 | Xray `grpcSettings` | 原生支持，需转换 | 至少保留 service name；Mihomo 有 `grpc-opts` 入口 |
| gRPC multi | Xray/Clash 可携带相关字段 | 需转换；行为需矩阵验证 | Mihomo 源码有 max/min streams/connections/ping 字段，当前 `ServerProfile` 仅有 serviceName，见风险 2 |
| HTTP Upgrade | Xray `httpupgradeSettings` | 无等价（固定版本未确认通用入口） | 不得改成普通 TCP 后继续连接，见风险 5 的同类阻断策略 |
| xhttp | Xray builder 对 native outbound 生成 `xhttpSettings` | VLESS 原生支持；非 VLESS 无等价 | 非 VLESS+xhttp 是明确迁移阻断项，见风险 5 |
| TLS/Reality | Xray `tlsSettings`/`realitySettings` | 原生支持，需转换 | SNI、ALPN、fingerprint、public key、short ID、spiderX、allow-insecure 必须逐字段保留 |

## 2. 导入、编辑与持久化

| 当前功能 | 当前行为/落点 | Mihomo 落点 | 保留要求 |
| --- | --- | --- | --- |
| VMess/VLESS/Trojan/SS 分享链接 | `ShareLinkParser` 解析/编码；标准字段写入 `ServerProfile` | 需转换 | 分享链接解析结果先进入统一模型，再由 flavor 选择配置生成器 |
| 扩展协议分享链接 | Hysteria/Hy2/TUIC/WireGuard/SSR/HTTP/SOCKS5/AnyTLS 由 `ExtendedShareLinks` 处理，mieru 由 `MieruShareLinks` 处理 | 需转换 | 现有 URI round-trip 行为必须保持；Unknown Clash 选项不能被编辑流程抹掉 |
| mieru 官方 protobuf 链接 | Go `NormalizeMieruLink` 调官方 `appctl` 转成 `mierus` 列表，再由 Kotlin 建节点 | 需内核补丁 | 单核 shim 继续提供同一归一化接口；禁止在 Kotlin 重写 protobuf 编解码造成字段损失 |
| Clash YAML/JSON 节点导入 | `ClashYamlParser` 安全解析 `proxies`；支持 14 种协议、嵌套 transport/plugin/options、YAML anchor；明确不自动拉取 `proxy-providers`，不把 proxy-groups 当节点 | 需转换 | 继续保留原始 `clashConfig`；single 生成器从原始节点配置生成 Mihomo outbound |
| Clash 规则导入 | 当前解析 DOMAIN、DOMAIN-SUFFIX、DOMAIN-KEYWORD、GEOSITE、IP-CIDR/IP-CIDR6、GEOIP；未知/不合法规则被过滤 | 需转换 | 保持当前用户可见导入语义；不能在单核转换阶段额外静默丢弃已保存的 IceFlow 规则字段 |
| 节点编辑/校验 | `NodeEditing` 保留 group/subscription/latency 归属，Clash 节点编辑同步嵌套字段 | 需转换 | 统一模型不改 schema；新增字段必须带默认值，并由两版同时支持 |
| 节点分组与排序 | 本地分组、订阅分组、原始/名称/延迟排序，节点通过 groupId/subscriptionId 归属 | 原生支持（外层保留） | 不进入核心迁移逻辑，覆盖安装后两版共用同一数据 |
| QR 扫描和批量导入 | UI 通过分享链接/Clash 解析器进入同一仓库 | 原生支持（外层保留） | 单核仅替换启动配置，不改变导入路径 |

## 3. 路由、DNS 与入站

### 3.1 路由模式和规则字段

当前 `RoutingMode` 有三种：

| 模式 | 当前 Xray 行为 | Mihomo 落点 |
| --- | --- | --- |
| 规则 `RULE` | 按 `routingRules` 顺序生成 field 规则；末尾由 Xray 默认行为处理 | 需转换；每条规则转换为 Mihomo rule，跨字段条件使用 `AND` |
| 全局 `GLOBAL` | 生成 TCP/UDP 全部到 `proxy` 的规则 | 需转换；用 Mihomo `MATCH,proxy` 等价表达 |
| 直连 `DIRECT` | 生成 TCP/UDP 全部到 `direct` 的规则 | 需转换；用 Mihomo `MATCH,DIRECT` 等价表达 |

`RoutingRule` 当前字段必须全部纳入单核转换器：`id`、`name`、`enabled`、`action`（proxy/direct/block）、`domains`、`ips`、`port`、`source`、`sourcePort`、`network`、`protocols`。Xray 当前把这些字段放在同一 field rule 中，表达跨字段 AND；单核不能把它们拆成多个独立规则而改变语义。

| 规则能力 | 当前双核落点 | Mihomo 落点 | 注意事项 |
| --- | --- | --- | --- |
| domain/full/domain/keyword/regexp | Xray `domain` 条件 | 需转换 | 根据前缀映射 DOMAIN/DOMAIN-SUFFIX/DOMAIN-KEYWORD/DOMAIN-REGEX；需要保留顺序 |
| geosite | Xray geo asset + `geosite:*` | 需转换 | 读取/更新 `geosite.dat` 的来源和 Mihomo geodata 格式要统一；不得把 geosite 当普通域名 |
| geoip | Xray geo asset + `geoip:*` | 需转换 | 映射 `GEOIP`；`geoip:private` 当前由固定私网 CIDR 替换以避免资源依赖 |
| destination IP/CIDR | Xray `ip` | 需转换 | 映射 IP-CIDR/IP-CIDR6；IPv4/IPv6 和 `ext:`/geoip 前缀需分别处理 |
| source IP/CIDR | Xray `source` | 需转换 | 映射 SRC-IP-CIDR；必须保持来源条件与目标条件的 AND |
| destination/source port | Xray `port`/`sourcePort` | 需转换 | 映射 DST-PORT/SRC-PORT；范围和逗号列表要逐项等价 |
| network | Xray `network` | 需转换 | 映射 NETWORK；`tcp,udp` 不能被错误拆成 OR 之外的匹配 |
| sniffed protocol | Xray `protocol`，依赖入站 sniffing | 需内核补丁或无等价 | 见风险 1；必须验证 HTTP/TLS/QUIC/BitTorrent 条件在 TUN 和 SOCKS 两个入口的可见性 |
| 多字段组合 | Xray field rule 的 AND 语义 | 需转换 | 使用 Mihomo `AND`/`OR`/`NOT` 逻辑规则表达；转换器需逐条比对测试 |

### 3.2 DNS 三态

当前设置由 `dnsProxyEnabled` 与 `fakeDns` 组合出三态：

1. DNS 路由关闭：使用 `DNS_DIRECT`，由 Rust 流量层直连 DNS。
2. DNS 路由开启且 `fakeDns=true`：使用 `DNS_VIRTUAL`，tun2proxy 虚拟 DNS/Fake-IP。
3. DNS 路由开启且 `fakeDns=false`：使用 `DNS_OVER_TCP`，DNS 经代理转发。

同时保留自定义 DNS 列表、remote/domestic DNS、IPv4/IPv6 查询策略、规则模式下国内域名 `geosite:cn` 的 DNS 分流，以及 TUN/DNS 泄漏防护。

| 功能 | Mihomo 落点 | 迁移要求 |
| --- | --- | --- |
| direct DNS | 需转换 | 用 Mihomo DNS nameserver/direct 规则表达，不能让 TUN 接管后绕过设置 |
| fake-IP/virtual DNS | 需转换 | 使用 Mihomo `enhanced-mode: fake-ip` 或等价单核实现，验证地址回收和 IPv6 行为 |
| DNS over proxy | 需转换 | nameserver-proxy/远端 DNS 必须沿当前 outbound 链路走 |
| geosite/geoip DNS 分流 | 需转换 | DNS 规则与路由规则使用同一资源版本和同一 action 语义 |

### 3.3 本地 SOCKS 入站、应用路由和 IPv6

- 当前 Xray 生成 `socks-in`，监听 `127.0.0.1:AppSettings.socksPort`；可关闭监听、可独立开启 UDP、用户名与密码同时非空时启用 password auth。
- VPN 内部 TUN 入站使用另一个随机凭据的临时 SOCKS 端口，公共 SOCKS 账号不能泄漏到内部入站。
- 分应用代理由 Android `VpnService.Builder` 的 allow/disallow application 实现；`bypassApps`、`proxyAppsOnly`、`appRoutingEnabled` 不能迁移成核心内的普通路由规则后丢失。
- IPv4 默认接管；`ipv6Enabled` 时额外接管 `::/0` 并配置 IPv6 地址/路由。

Mihomo 具备 SOCKS/mixed 入站和认证能力，但单核需要把本地监听、TUN fd、DNS、应用过滤边界组合起来，因此整体标记为 **需转换**。TUN fd 接入若不能由 Mihomo 稳定接管，必须停下确认“单核版保留 Rust 流量层”的备选方案，不能临时绕过 TUN 语义。

## 4. 链式代理、测速与统计

| 当前功能 | 当前双核行为 | Mihomo 落点 | 单核要求 |
| --- | --- | --- | --- |
| 分组前置 + 当前节点 + 落地链 | `XrayConfigBuilder` 生成 chain tags 和 `dialerProxy`；Go adapter 用 forced outbound dialer 串联 | 需转换 | 使用 Mihomo `dialer-proxy`；顺序、重复节点校验、失效节点错误都要保持 |
| 链路内私有地址强制走前置 | `taggedDialer` 设置 forced outbound tag，绕过原始目的地址的 bypass-LAN 规则 | 需内核补丁 | 单核 shim/adapter 必须对前置和落地拨号保留 forced dialer 语义；至少要有私网目标回归 |
| 节点测速 | 优先启动无入站临时 Xray 实例，使用配置后的 proxy 发起 HTTP/HTTPS `testUrl`；超时 5 秒；运行中测速走内部 SOCKS；无 Xray 时才退化 TCP 握手 | 需转换 | shim 提供同一接口和 HTTP 实际请求口径，不能把 TCP connect 当作成功替代；记录 p95 建连延迟 |
| 每节点累计流量 | Rust 提供会话总上/下行；Xray `queryStats` 按 outbound tag 提供分类；服务每 30 秒合并写入当前节点，释放资源时强制落盘 | 需内核补丁 | 单核在 shim 按 outbound 名聚合，输出与现有 `ServerProfile.uploadBytes/downloadBytes` 可衔接；禁止重复累计或把 direct/block 计入 proxy 节点 |
| UI 实时流量和速率 | 现有 `TrafficSampler` 每秒采样，保留 60 个速率点；磁贴/主页读取 `VpnController` 状态 | 原生支持（外层保留） | 单核只需提供相同统计输入；phase 4 的自适应轮询不在本阶段实施 |

## 5. 外层功能（核心替换不得回退）

| 当前功能 | 当前实现 | Mihomo 落点 | 保留要求 |
| --- | --- | --- | --- |
| 订阅自动更新 | HTTPS 拉取；WorkManager 按 1–720 小时周期；网络可用约束；IO 失败最多重试 3 次；节点更新保留订阅归属和分组链 | 原生支持（外层保留） | 核心 flavor 不影响订阅数据、更新调度和前置/落地字段 |
| 备份导出/导入 | `BackupDocument` 保存 `ServerStore` + `AppSettings`，格式版本 1；恢复过滤未安装应用；恢复前等待资源释放 | 原生支持（外层保留） | 两版共用同一 schema；只允许新增带默认值字段；覆盖安装后数据无损 |
| 关于页/自动更新当前行为 | `UpdateChecker` 查询私有仓库 latest release，当前只按版本和 APK 名选择 | 需转换（phase 3） | phase 3 才增加按 flavor 过滤，不在阶段 0 修改 |
| 磁贴 | `VpnTilePolicy` 根据 Idle/Connecting/WaitingNetwork/Connected/Stopping/Error 渲染状态、图标和失败短标签 | 原生支持（外层保留） | 单核启动/停止必须进入同一状态流，不另建一套状态机 |
| 失败分类与反馈 | `VpnErrorType` 覆盖授权、核心启动、配置/订阅、网络、重试耗尽、资源清理；磁贴与通知共享分类 | 原生支持（外层保留） | 单核错误必须映射到既有分类；不能用“启动失败”吞掉配置或清理错误 |
| 状态机 | phase-2 资产：资源释放完成后才回到 Idle；`desiredRunning`、重试 token、重试上限和 `ResourceReleaseTracker` 已有单测 | 原生支持（外层保留） | 迁移只替换核心调用；不改变 `ResourcesReleased -> Idle` 的时序或失败语义 |
| 公网 IP | 连接后经当前运行中的 SOCKS 使用 `ConnectionProbe.publicIp` 查询；连接状态变化会取消旧查询并防止旧结果回写 | 需转换 | 单核必须提供可用且同口径的本地 SOCKS/代理路径，保留取消和 stale-result 防护 |
| 日志与运行诊断 | 服务和核心错误写入 AppLog；资源、建连、测速和订阅失败可追踪 | 原生支持（外层保留） | 单核 shim 返回可分类错误，避免只返回空字符串 |

## 6. 已知风险与处理方案

以下风险是阶段 0 冻结项。没有通过对应验证前，不得把单核功能标记为完成，也不得静默把配置改成另一种协议或传输。

### 6.1 规则的“嗅探协议”条件

- **现状**：`RoutingRule.protocols` 允许 `http`、`tls`、`bittorrent`、`quic`；Xray SOCKS/TUN 入站开启 sniffing，但当前 `destOverride` 显式列出的是 `http`、`tls`、`quic`，规则命中还依赖核心是否能在对应入口识别协议。
- **风险**：Mihomo 的 metadata/sniff 结果、TUN 栈和 SOCKS 入站的可见字段不一定与 Xray 一致；BitTorrent 条件尤其不能假定可用。把 `protocol` 丢掉会改变用户已保存规则的结果。
- **方案**：单核转换器为协议条件生成独立 fixture，覆盖单条件与“域名 + IP + 端口 + network + protocol”组合；在 Mihomo TUN 和本地 SOCKS 两个入口分别验证命中。若固定版本无法提供等价识别，先实现 shim/内核补丁；仍无法等价时阻止该规则启用并显示具体“不支持嗅探协议条件”，不得降级为无 protocol 规则。

### 6.2 gRPC multi

- **现状**：当前通用 `ServerProfile` 主要保存 `serviceName`；Clash 原始配置可在 `clashConfig.grpc-opts` 中保留 `grpc-user-agent`、`ping-interval`、`max-connections`、`min-streams`、`max-streams` 等字段。Mihomo v1.19.32 源码有对应字段和 gUN transport 入口。
- **风险**：如果只生成 `grpc-service-name`，多连接/多 stream 的吞吐、复用和心跳行为会静默回退；如果丢弃 `clashConfig`，编辑后无法恢复原始字段。
- **方案**：配置转换器按字段白名单递归映射全部 `grpc-opts`；为 VMess/VLESS/Trojan 分别做配置快照和实际建连回归，至少覆盖 1/2/多连接、多 stream、ping 和用户代理。对手工节点缺失字段使用协议默认值；对未知字段继续保留 Clash 原始 JSON，不能编造值。

### 6.3 TCP HTTP 头伪装

- **现状**：Xray builder 对 `tcp`/`raw` + `headerType=http` 生成 `tcpSettings.header.request.path` 和 Host。
- **风险**：Mihomo v1.19.32 的 `http-opts` 支持范围按协议不同；VMess/VLESS 有入口，Trojan 等没有已确认的完全等价通道。直接改成普通 TCP 会使服务端握手失败且用户无从判断原因。
- **方案**：按协议分别完成 Xray JSON 与 Mihomo 配置/运行时对照。已有等价入口的协议补齐 method/path/headers 映射并加单测；没有等价入口的协议在 shim/内核层实现适配，或者在单核连接前给出明确“不支持该组合”的错误并保留旧配置。阶段 2 完成门槛是不能有静默降级。

### 6.4 VMess `alterId > 0`

- **现状**：模型和 Xray JSON 保留 `alterId`；Mihomo v1.19.32 的 VMess option 也有 `AlterID` 字段。
- **风险**：字段存在不等于与当前 Xray/服务端实现完全兼容，尤其是旧服务端、不同 cipher、WS/gRPC 传输组合。
- **方案**：在 0、1、多个非零 alterId 下分别做服务端兼容矩阵，覆盖 TCP/WS/gRPC 和 TLS；验证统计、重连、链式拨号。失败时优先补 Mihomo adapter/协议实现；在补丁未合入前必须保留可见“不支持 VMess alterId > 0”提示，不能强制改为 0。

### 6.5 非 VLESS+xhttp

- **现状**：当前 Xray builder 对 native 协议按 `network=xhttp` 生成 `xhttpSettings`；固定 Mihomo 源码明确有 VLESS `xhttp-opts`，未发现 VMess/Trojan/SS 的同语义 xhttp outbound。
- **风险**：把 VMess/Trojan/SS xhttp 当成 VLESS xhttp 或普通 HTTP/WS 会改变握手协议；这不是普通字段转换问题。
- **方案**：VLESS+xhttp 先按 Mihomo 原生字段完整转换并做 xhttp 模式/headers/reuse/download 组合测试。其余协议必须由 Mihomo adapter/核心补丁提供真正等价实现；若阶段 2 时仍未解决，单核对该节点拒绝启动并显示具体提示，且阶段 2 不能宣称“全部功能完成”。禁止静默改传输。

### 6.6 mieru MTU

- **现状**：双核的 `xray-wrapper/libxray/mieru.go` 已调用官方 mieru client API，读取 MTU，创建 `ClientConfig`，在链式场景注入 stream/packet dialer，并通过 `mieruMTUProxy` 覆盖 TCP/UDP 拨号；Mihomo 常规 options 不足以表达这一逻辑。
- **风险**：只把 `mtu` 作为 Mihomo 字段传入会被忽略，导致特定网络下分片、握手或 UDP 行为变化。
- **方案**：把官方客户端逻辑移植到单核 shim/adapter：MTU 0/1280–1500 校验、TCP/UDP、port range、复用/握手/traffic pattern、前置 dialer、packet dialer 和 Stop 生命周期全部覆盖；对无效 MTU 保持当前失败分类。以 mieru 官方 API 为源，不在 Kotlin 侧重写协议。

### 6.7 其他迁移阻断项

- Mihomo TUN fd 接管必须先证明 Android `VpnService` 的 fd 生命周期、DNS、IPv6、应用路由和 stop/重连都稳定；否则按任务要求暂停并请示是否保留 Rust 流量层。
- Xray 的 MPH cache、`ruleTag` 和 Xray geo asset 是当前实现细节，不是用户可见等价功能。单核可以改用 Mihomo geodata/规则缓存，但不能以移除缓存为由改变规则结果；容量和内存限制留到阶段 4。
- Clash `proxy-groups`/`proxy-providers` 当前导入器有意不作为节点或远程配置执行。阶段 0 不扩大现有行为；单核必须至少保持当前导入结果和原始 options 保留行为。

## 7. 阶段 0 验证基线

测试在 ASCII 路径 `E:\IceFlow-core-variants` 的 detached worktree 执行。因为主仓库路径含中文，Gradle 测试 worker 按项目既有说明使用该路径；被 Git 忽略的 `app/libs/xray.aar` 和 3 个 ABI 的 `libiceflow_core.so` 只从主工作区复制到该测试 worktree，未修改源文件或签名文件。

| 层级 | 命令/范围 | 结果 | 验证类型 |
| --- | --- | --- | --- |
| Kotlin/JVM | `testDebugUnitTest`，23 个测试类 | 128/128 通过，0 失败，0 error，0 skipped | 仅单测 |
| Go | `xray-wrapper` `go test ./...` | 57/57 通过 | 仅单测 |
| Rust | `rust-core` `cargo test` | 7/7 通过；doc-test 0 项 | 仅单测 |
| Android instrumentation | 未执行 | 无数据 | 未验证 |
| 真机协议矩阵 | 未执行 | 无数据 | 未验证 |
| 模拟器协议矩阵 | 未执行 | 无数据 | 未验证 |

阶段 0 不包含吞吐、CPU、建连 p95、功耗或发热数据；这些数据必须在后续 flavor 和单核实现完成后按目标判定线采集，不能用本阶段单测代替。

## 8. 阶段 1 前置验收边界

阶段 1 只能在本映射表冻结后开始，且只做 flavor 骨架：

1. 双核保留本表所述全部现有路径和状态机语义。
2. 单核仅提供占位 bridge 和“单核未就绪”可见提示，不在阶段 1 偷渡单核协议实现。
3. 后续单核实现必须逐项关闭本表的“需转换/需内核补丁/无等价”项；任何未关闭项都要在阶段汇报中明确列出。
