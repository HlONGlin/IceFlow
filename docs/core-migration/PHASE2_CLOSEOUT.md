# 阶段 2 收尾报告（S4）

> 2026-10-31 归档说明：本文件引用的历史 test-reports 原始证据已移至 E:/IceFlow-probe-evidence/release-repack-20261031/archived-repository-reports/，保留原相对路径；精简入口见 test-reports/README.md。历史结论与未验证项不变。


日期：2026-10-29。工作树：E:/IceFlow-core-variants，分支 feat/core-variants。
验证限定：仅 x86_64 模拟器（emulator-5556）；**ARM 真机未验证**（长期限定，所有结论仅适用 x86_64 模拟器）。

---

## 1. R1–R4 结论汇总

### R1 WireGuard 真实夹具连接验证 — 已关闭（通过）

- 初轮（提交 a2ade81）：无本地 WireGuard/Docker/wg 能力，真实服务端夹具未就绪，登记"等待 Linux VPS 夹具"；产出部署脚本 `deploy-wireguard-fixture.sh` 与 G05 显式 peer 客户端节点模板（top-level `allowed-ips: 0.0.0.0/0`）。
- 重试（提交 2e3d17b）：远程真实夹具 `54.255.193.12:51820` 就绪后完成验证。TUN 与 SOCKS（127.0.0.1:10808）均通过 `generate_204`（HTTP/1.1 204）；G05 显式 peer 映射路径在真实服务端握手/请求下覆盖。证据：`test-reports/wg-r1-retry-20261029/`。
- 清场完整：VPN 断开、servers.json 恢复（json_equal=True）、adb reverse 清空、无残留 tun0。

### R2 dual x86_64 公网 IP / 默认测速对照 — 已关闭（提交 65f1fa9）

- xray.aar 缺失定性为**环境/产物问题**，非 dual 产品路径问题：dual APK 内含 libgojni.so 与 libiceflow_core.so，Xray 26.3.27 started。
- 公网 IP 查询：dual 与 single 均失败（SSLHandshakeException: Connection closed by peer；fixture 记录 api.ipify.org 远端强制关闭）→ **既有网络/远端 TLS 既有问题**，不计迁移缺口。
- 默认测速：dual 走到 `XrayBridge.measureSocksDelay`，失败为 Reality fixture 远端 EOF → 既有网络/夹具问题，不计迁移缺口。
- 未修改代码。证据：`test-reports/r2-dual-publicip-speed-20261029/`。

### R3 TUIC 版本门控 — 已关闭（提交 7a6892d）

- 门控范围已记录于 `docs/core-migration/STEP4_GATE_CORRECTIONS.md`；新增 raw version reject + default pass 两个单测（MihomoConfigBuilderTest.kt），single 测试数 221→223。

### R4 缺口登记 — 已登记（提交 461bec6 / 685e766 / 4f5f8da）

三项均**未验证、未关闭**，正式登记于 `docs/core-migration/GAP_REGISTRY.md`：

- R4-1 Hysteria v1：无真实服务端夹具，协议建连从未发生。
- R4-2 SSR：无真实服务端夹具，加密/混淆协商从未发生。
- R4-3 mieru MTU：仅 Go 测试证明 ClientProfile 传递，无独立抓包证明 MTU 实际网络效果。

---

## 2. S1–S3 结果汇总

### S1 dual/single overlay 数据保留 — 通过（提交 a07460d）

dual 与 single 连接/探测/断开全流程后，既有 servers.json 及备份保留完整。证据：`test-reports/s1-dual-single-overlay-20261029/`。

### S2 全量测试强制重跑 — 无倒退（提交 a97ddd4）

| 组 | 基线 | 新跑 | 判定 |
|---|---:|---:|---|
| Kotlin dual | 129 | 129 | 无倒退 |
| Kotlin single | 221 | 223（+2 为 R3 新增） | 无倒退 |
| mihomo Go | 36 | 36 | 无倒退 |
| Xray Go | 57 | 57 | 无倒退 |
| Rust | 7 | 7 | 无倒退 |

失败/error/skipped 全部为 0。证据：`test-reports/s2-full-rerun-20261029/`。

### S3 8 个 release APK 重建与验签 — 通过

- `assembleSingleRelease` / `assembleDualRelease` 均 BUILD SUCCESSFUL；8 个 APK 独立 apksigner 验证全部通过，证书 SHA-256 与基线一致（762ca843…025）。
- 原生库核对：dual 含 libgojni.so（=Xray AAR）+ libiceflow_core.so；single 含 libgojni.so（=mihomo AAR，≠Xray AAR）、不含 libiceflow_core.so，全部 ABI 均通过。
- 证据：`test-reports/s3-release-rebuild-20261029/REPORT.md`（本次随 S4 一并入仓）。

---

## 3. 仍开放的缺口清单

1. **ARM 真机未验证（长期限定）**：本阶段全部验证仅限 x86_64 模拟器（emulator-5556）。ARM 真机（arm64-v8a / armeabi-v7a）的安装、连接、测速、协议行为均未验证；S3 的 arm64/v7a APK 仅完成验签与原生库静态核对，无真机运行证据。
2. **R4-1 Hysteria v1**：无真实服务端夹具，未验证（见 GAP_REGISTRY.md R4-1）。
3. **R4-2 SSR**：无真实服务端夹具，未验证（见 GAP_REGISTRY.md R4-2）。
4. **R4-3 mieru MTU**：仅 Go 测试，无独立抓包证明实际网络效果（见 GAP_REGISTRY.md R4-3）。
5. **共享既有问题（非迁移缺口，留档）**：公网 IP 查询在 dual/single 均因远端 TLS 强制关闭失败；Reality fixture 默认测速远端 EOF。属网络/远端环境问题。

---

## 4. 验收与提交索引

| 项 | 提交 | 状态 |
|---|---|---|
| R1 初轮 | a2ade81 | 已验收（夹具未就绪） |
| R1 重试 | 2e3d17b | 已验收通过 |
| R2 | 65f1fa9 | 已验收通过 |
| R3 | 7a6892d | 已验收通过 |
| R4 登记 | 461bec6 / 685e766 / 4f5f8da | 已验收通过 |
| S1 | a07460d | 已验收通过 |
| S2 | a97ddd4 | 已验收通过 |
| S3 报告 | 本次随 S4 入仓 | 已验收通过 |

阶段 2 收尾完成；未合 main、未发版、未打 tag。
