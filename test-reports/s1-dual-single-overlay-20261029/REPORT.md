# S1 dual <-> single 覆盖安装与数据保留

- 日期：2026-10-29（Asia/Shanghai）
- 工作树：`E:/IceFlow-core-variants`
- 分支：`feat/core-variants`
- 设备：`emulator-5556`，Android 9，`x86_64`；未验证 ARM 真机
- APK：debug `versionCode=104` / `versionName=0.1.3.0`
- ADB：`C:/Users/honglin/AppData/Local/Android/Sdk/platform-tools/adb.exe`

## 夹具数据

dual 初始数据由受控文件写入应用私有目录，包含 3 个节点（`S1-Primary`、`S1-Backup`、`S1-Trojan`）、1 个本地分组（`S1-Overlay-Group`）、自定义选中节点与排序、流量/延迟字段、14 项设置，以及 2 条路由规则（Proxy、Direct）。临时 VLESS fixture 仅用于验证 dual VPN 启动，使用 `adb reverse tcp:18443 tcp:18443`；测速请求未到达 fixture，最终为超时，单独记录为夹具探测失败。

## 结果表：dual -> single

| 核对项 | 结果 | 证据 |
|---|---|---|
| 覆盖安装成功 | 通过 | `batch2-single-install.txt`：Success |
| 节点数量及名称 | 通过，3 个 | `batch2-single-servers.json`、`batch2-single-after-overlay.xml` |
| 节点详细字段、选中节点、流量/延迟 | 通过 | `batch2-single-servers.json`；字段可解析 |
| 分组数量、名称、排序、选中分组 | 通过 | `batch2-single-servers.json`、UI XML |
| 设置项 | 通过 | `batch2-single-settings.xml`：DNS、SOCKS 32123、认证、主题、夜间模式、字号、语言、日志保留等 |
| 路由规则 | 通过，2 条 | `batch2-single-settings.xml`：`s1-rule-proxy`、`s1-rule-direct` |
| App 冷启动 | 通过 | `batch2-single-start.txt`：Status ok |
| single 实际连接 | **失败** | `batch2-single-after-connect.xml` 显示 `Single-core build is not ready`；`batch2-single-connect-logcat.txt` 显示缺少 `libiceflow_core.so` |

结论：数据保留通过；single 连接不通过，失败为 single APK/核心未就绪，而非覆盖安装导致的数据丢失。

## 结果表：single -> dual

| 核对项 | 结果 | 证据 |
|---|---|---|
| 覆盖安装成功 | 通过 | `batch3-dual-restore-install.txt`：Success |
| 节点数量及名称 | 通过，3 个 | `batch3-dual-restored-servers.json`、`batch3-dual-restored.xml` |
| 节点详细字段、选中节点、流量/延迟 | 通过（连接尝试会更新运行时延迟字段） | `batch3-dual-restored-servers.json` |
| 分组数量、名称、排序、选中分组 | 通过 | `batch3-dual-restored-servers.json`、UI XML |
| 设置项 | 通过 | `batch3-dual-restored-settings.xml` |
| 路由规则 | 通过，2 条 | `batch3-dual-restored-settings.xml` |
| App 冷启动 | 通过 | `batch3-dual-restore-start.txt`：Status ok |
| dual 实际连接 | 通过 | `batch3-dual-connect-logcat.txt`：`VPN started: S1-Primary`；`batch3-dual-connectivity.txt`：VPN/tun0 connected |

## ADB / 清场

本轮无 ADB 掉线；每批次均保存安装与状态证据。结束时已断开 VPN、移除全部 reverse、停止临时 fixture，并将设备恢复到测试前的 dual 安装数据：`preexisting-servers.json` 的 SHA-256 与设备恢复文件均为 `CD1A6D52020F81134DBE0A6C84042CF6641355BB6E62C3A4AC56B99824629311`；最终设备仍在线，未发现 reverse 映射或活动 VPN。

## 总结

双向覆盖安装的数据保留结果均通过；dual 两次均可建立 VPN。single 覆盖后的连接结果失败，证据为 single 包缺少 `libiceflow_core.so` 并显示 `Single-core build is not ready`，因此本任务整体验收为“数据保留通过、single 连接失败”。
