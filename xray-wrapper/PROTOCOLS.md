# IceFlow 原生协议接入

## 运行路径

Android VPN → Rust TUN/SOCKS → Xray 本地入站与路由 → 原生协议出站。
分享链接中的 VMess、VLESS、Trojan、Shadowsocks 使用 Xray 出站；扩展协议及 Clash 节点使用固定版本
`github.com/metacubex/mihomo v1.19.32` 的协议适配器。适配器在同一 Go/JNI 库中运行，
由 Xray 的 `proxy` 出站管理生命周期，连接与测速使用相同配置。

`iceflowAdapter` 是本项目的配置扩展；不能只替换成普通 Xray AAR。
扩展协议的占位出站是 blackhole，缺少适配器时不会回落为直连。

## 新增链接格式

| 协议 | 链接前缀 | 必要参数与常用可选参数 |
| --- | --- | --- |
| Hysteria v1 | `hysteria://`、`hy://` | 认证 `auth`；`upmbps`、`downmbps` 带宽；`peer`、`alpn`、`obfs=xplus`、`obfsParam` |
| Hysteria2 | `hysteria2://`、`hy2://` | userinfo 中的认证密码（可包含用户名和密码）；`sni`、`insecure`、`obfs=salamander`、`obfs-password`、`pinSHA256`、`ech` |
| TUIC v5 / v4 | `tuic://` | v5 为 `UUID:password@host:port`，v4 为 `token@host:port`；`alpn`、`congestion_control`、`udp_relay_mode`、`reduce_rtt`、`disable_sni` |
| WireGuard | `wireguard://`、`wg://` | userinfo 私钥；`publickey`、`address` 隧道地址；可选 `presharedkey`、`reserved=1,2,3`、`mtu`、`keepalive`、`dns` |
| ShadowsocksR | `ssr://` | 标准 Base64 SSR 链接，含 cipher/password/protocol/obfs 及 Base64 备注、混淆参数 |
| HTTP / HTTPS 代理 | `http://`、`https://` | 可选 `username:password@`；默认端口 80 / 443，HTTPS 使用 TLS CONNECT |
| SOCKS5 | `socks://`、`socks5://`、`socks5h://` | 可选用户名密码或 Base64 userinfo；默认端口 1080；`tls=true` 可启用 TLS |
| mieru | `mieru://`、`mierus://` | 官方 Protobuf 完整配置或简化链接；用户名、密码、`profile`、成对 `port` / `protocol=TCP或UDP`；支持端口范围、`multiplexing`、`handshake-mode`、`traffic-pattern`、`mtu` |

IPv6 服务器地址使用方括号；用户信息和备注中的保留字符须百分号编码。
链接可从手动输入、剪贴板、文件、二维码以及普通/整体 Base64 链接列表订阅导入，
可以持久化、分享导出、进行节点测速和 VPN 连接。WireGuard 入口使用上述 URI。
HTTP 出站使用 CONNECT TCP 隧道；SOCKS5 及其他支持 UDP 的适配器同时连接 UDP 转发路径。

### mieru 示例

```text
mierus://username:password@proxy.example.com?profile=My%20mieru&port=12345&protocol=TCP
mierus://username:password@proxy.example.com?profile=My%20mieru&port=20000-20010&protocol=UDP&multiplexing=MULTIPLEXING_LOW&mtu=1400
```

`mieru://` 的 Base64 内容是官方 Protobuf，由固定版本官方解码器解析。
完整配置中的多个 profile、服务器、端口绑定全部导入；同服务器的不同传输/端口绑定
拆为独立可选择节点，端口范围保持为范围。分享导出使用可读的官方 `mierus://` 格式。
MTU 为 0 或未设置时使用默认值，也可显式设置 1280–1500；显式 MTU 由官方客户端 API
保留应用。其他配置沿用 Mihomo 的 mieru 适配器。两种路径共用 Xray 路由及测速入口。

## Clash YAML / JSON 导入

剪贴板、手动输入、文件与订阅更新共用解析入口，支持完整 Clash 配置中的 `proxies`、
provider 返回的 `proxies` 节点列表、裸节点数组，以及整体 Base64 编码的上述内容。
支持 YAML 锚点/合并、行内对象、中文名称和 IPv6；格式错误会显示导入失败，订阅更新失败时保留旧节点。

节点类型：`vmess`、`vless`、`trojan`、`ss`、`ssr`、`hysteria`、`hysteria2`、
`tuic`、`wireguard`、`http`、`socks5`、`mieru`。未支持的类型跳过；`dialer-proxy` 链式代理明确报错。
嵌套配置（例如 `ws-opts`、`grpc-opts`、`reality-opts`、`plugin-opts`、`smux`）保留原始类型
与内容，持久化后直接交给固定版本 Mihomo 适配器。协议参数有效性由对应原生适配器检查。

仅导入节点；不导入 Clash 的分流规则、策略组和本地监听配置，不自动下载 `proxy-providers`。
只有 provider 定义而没有 `proxies` 的配置会提示缺少节点列表；可直接订阅 provider 的实际节点 URL。
Clash 节点分享为保留全部参数的 JSON（有效 YAML）；混合导出用 `---` 分隔并可重新导入。
解析器采用 SnakeYAML `SafeConstructor`，限制文档大小、深度、别名和展开字段数量。

## 分组链式代理

分组设置可选择前置节点与落地节点，连接顺序为：
`本机 → 前置节点（可选）→ 本分组当前节点 → 落地节点（可选）→ 目标`。
保存后下次连接生效；节点测速也使用同一代理链。只能选择本分组之外的节点，
前置与落地不能为同一节点；选中节点自身所属分组的链式设置不会递归叠加。
订阅刷新尽量保留匹配节点的 ID；引用节点被删除后明确报错，不会静默改为直连。

原生 Xray 出站通过 `dialerProxy` 连接上一跳；协议适配器通过绑定上一跳的拨号器连接。
多适配器配置使用 `iceflowAdapters`，单节点仍兼容 `iceflowAdapter`。
本地 Go 与 Android 测试覆盖三跳 HTTP CONNECT 的顺序、实际转发、上一跳故障时失败，
以及绑定原生出站的 UDP 多包转发。

## 构建与验证

在此目录运行现有 `build-aar.ps1` 或 `build-aar.sh` 重新生成 `app/libs/xray.aar`。
产物包含 Android arm64、arm、amd64，最低 API 26。依赖由 go.mod/go.sum 固定。

- `go test ./libxray -v`：各适配器创建/关闭、HTTP CONNECT 认证与实际测速请求、
  SOCKS5 认证 TCP 及 UDP 多包/大包转发；mieru 官方链接展开、TCP/UDP 加密传输，
  各传输上的 TCP 请求与 UDP 多包/大包转发，以及自定义 MTU / NO_WAIT 握手。
- `ExtendedProtocolsTest`：导入/导出往返、参数类型、默认端口、IPv6、Base64、无效配置。
- `MieruProtocolTest`：官方简化链接、IPv6、范围/多端口、持久化、参数及无效链接。
- `ClashYamlParserTest`：12 种节点映射、嵌套参数、锚点合并、Base64、持久化/分享往返、无效输入。
- `NativeBridgeTest`：Android JNI 加载、各协议原生配置启动/停止、mieru 官方完整链接导入和现有直连路由。

远端 Hysteria、Hysteria2、TUIC、WireGuard、SSR 的握手和网络连通性
需要对应有效服务端进行验证；本地生命周期测试不等同于远端连通性测试。

## 依赖来源

- Xray-core：`github.com/xtls/xray-core v1.260327.0`，MPL-2.0。
- Mihomo：`github.com/metacubex/mihomo v1.19.32`，GPL-3.0，
  源码与原许可证随固定模块版本获取；发布含此依赖的二进制时需提供相应源码与许可信息。
- mieru：`github.com/enfein/mieru/v3 v3.38.0`，GPL-3.0；用于协议客户端及官方分享链接编解码。

项目自定义适配器桥接源码为 `libxray/extended.go`；构建步骤及固定依赖均保留在此目录。
