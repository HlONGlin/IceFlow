# IceFlow v0.1.5.3

[English](#english)

## 2026-11-21 热修复

- 单核 Mihomo 校验器接受应用生成的本地 HTTP 入站和局域网绑定选项，同时继续校验内部 TUN 的监听地址、各入站端口及凭据。此前开启本地 HTTP 代理可能导致所有节点被误报为无效，问题不在订阅本身。
- 公网 IP 查询改用 `api64.ipify.org`。VPN 建立后，查询失败时最多额外尝试两次，不改变 IPv6 TUN 路由或 VPN 连接状态。
- 单核和双核 JVM 单元测试通过，`orca-linux` 中 Mihomo wrapper 的 Go 测试和 Rust 单元测试通过。两种签名 APK 均在 Android 实机上用同一个 IPv6 节点建立 VPN。

## Reality 握手

单核版在节点编辑、分享链接和 Clash YAML 导入中支持 `support-x25519mlkem768`。旧节点默认关闭；双核版当前不应用此设置。

## 安装

热修复已替换 [v0.1.5.3 Release](https://github.com/HlONGlin/IceFlow/releases/tag/v0.1.5.3) 的 APK。版本码仍为 `115`，签名证书未变。已安装同版本的用户需手动下载对应变体并覆盖安装。

## English

### November 21, 2026 hotfix

- The single-core Mihomo validator now accepts the app's local HTTP inbound and LAN binding options. It still validates the internal TUN bind address, listener ports and credentials. Previously, enabling the local HTTP proxy could incorrectly make every node appear invalid; the subscription itself was not the cause.
- Public IP lookup now uses `api64.ipify.org` and retries up to twice after an initial failure. IPv6 TUN routing and VPN connection state are unchanged.
- JVM unit tests passed for both variants. Mihomo wrapper Go tests and Rust unit tests passed in `orca-linux`. Both signed APK variants established a VPN session with the same IPv6 node on a physical Android device.

### Reality handshake

The single variant supports `support-x25519mlkem768` in the node editor, share links and Clash YAML imports. The option is off for existing nodes by default. The dual variant does not apply this setting.

### Installation

The hotfix replaces the APKs on the [v0.1.5.3 release page](https://github.com/HlONGlin/IceFlow/releases/tag/v0.1.5.3). The version code is still `115` and the signing certificate is unchanged. Users already on this version need to download their variant and install it over the existing app.
