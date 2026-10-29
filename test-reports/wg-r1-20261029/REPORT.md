# R1 WireGuard 真实服务端夹具补验

- 日期：2026-10-29（Asia/Shanghai）
- 设备：emulator-5556，雷电 index 1，x86_64，SM-S9180
- APK：E:\\IceFlow-core-variants\\app\\build\\outputs\\apk\\single\\debug\\app-single-x86_64-debug.apk
- 代码变更：无

## 环境与夹具

本轮确认设备在线并安装了 single x86_64 包；安装与设备 ABI 证据见 install-x86_64.txt、device-abi.txt、package-path.txt。当前主机未发现可用 Docker、WireGuard/wg 或 WireGuard 服务端程序，因此真实服务端夹具未能启动；没有伪造握手或 HTTP 成功结果。已补充 deploy-wireguard-fixture.sh 与 single-client-node-template.json，等待 Linux VPS 地址/服务端公钥回填后继续。

预期夹具配置（脱敏/未启动）如下，明确覆盖 G05 修正后的落点：

[Interface]
Address = 10.66.66.1/24
ListenPort = 51820
PrivateKey = <redacted-server-private-key>

[Peer]
PublicKey = <redacted-client-public-key>
AllowedIPs = 10.66.66.2/32

客户端节点应将 allowed-ips 放在显式 peer：0.0.0.0/0（G05 顶层非空值转换为单个显式 peer）；模板已保留该落点。本轮因服务端未启动，未向应用注入该节点。

## 矩阵结果

| 模式 | 连接/断开 | generate_204 | 结果 | 根因/证据 |
|---|---|---|---|---|
| TUN | 未执行 | 未执行 | 失败（环境阻塞） | 无可启动的真实 WireGuard 服务端夹具；见 host-capabilities.txt |
| SOCKS | 未执行 | 未执行 | 失败（环境阻塞） | 无可启动的真实 WireGuard 服务端夹具；见 host-capabilities.txt |

## 清场

已停止/重启应用进程，并执行 adb reverse --remove-all；清场后 reverse 列表见 adb-reverse-after.txt。本轮未启动夹具，故无夹具进程需要停止；应用未保持 VPN 连接。

## 原始证据

- install-x86_64.txt：x86_64 APK 安装成功。
- device-abi.txt：设备 ABI。
- package-path.txt：设备上的 APK 路径。
- host-capabilities.txt：Docker/wg/相关服务端能力检查。
- deploy-wireguard-fixture.sh：Linux VPS 一键部署真实服务端夹具。
- single-client-node-template.json：single x86_64 客户端节点模板，含 G05 显式 peer.allowed-ips 落点。
- launch.txt、logcat-launch.txt：启动与日志证据。
- adb-reverse-before.txt、adb-reverse-after.txt：ADB reverse 清场证据。

结论仅适用于 x86_64 模拟器环境；ARM 真机未验证。

