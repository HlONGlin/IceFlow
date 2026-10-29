# R4 缺口登记（未关闭）

日期：2026-10-29。工作树：E:/IceFlow-core-variants，分支 feat/core-variants。

本文件是正式缺口登记，逐项记录现状、未验证原因与后续补验条件。**三项均为未验证、未关闭状态；不得据此宣称已验证或已关闭。**验证限定：仅 x86_64 模拟器；ARM 真机未验证。与既有登记（FEATURE_MAP、SINGLE_MAPPING_AUDIT 第 5/8 节、SINGLE_BRIDGE_VALIDATION 边界段）一致，不改变任何冻结分类。

---

## R4-1 Hysteria v1：无真实服务端夹具，未验证

**现状**

- 配置层已实现：`MihomoConfigBuilder.kt:418` 输出 `hysteria`，protocolOptions 白名单见 `MihomoConfigBuilder.kt:398`；单测 `hysteriaAliasesAndObfsConvert`（app/src/testSingle/java/com/iceflow/data/MihomoConfigBuilderTest.kt:241）与 raw Clash 用例（:294）仅验证配置生成/别名/覆盖语义。
- dual wrapper 侧 `xray-wrapper/libxray/extended_test.go:78-79` 仅覆盖 adapter.ParseProxy 解析路径。
- 全仓 test-reports/ 中没有任何 Hysteria v1 服务端夹具或真实建连记录；phase2-step4 的夹具仅 xray 与 mihomo。

**未验证原因**

- 无可用 Hysteria v1 服务端二进制/夹具，QUIC+auth/obfs 完整握手从未在设备上发生；现有测试都是配置解析与生成，不构成协议建连证据。

**后续补验条件**

1. 构建/获取 pinned 版本匹配的 Hysteria v1 服务端夹具并给出配置与日志。
2. 在 x86_64 模拟器上经 single 生成配置真实建连，记录 TUN 流量证明（生成 204/下载至少一档）。
3. 覆盖至少一组：auth-str + up/down 别名映射、obfs（xplus）与 obfs-password、可选 ports 跳跃。
4. 结果写入 test-reports 并回链本条；在此之前不得改判为已验证。

---

<!-- 已提交：R4-1（commit 461bec6） -->

## R4-2 SSR：无真实服务端夹具，未验证

**现状**

- 配置层已实现：`MihomoConfigBuilder.kt:419` 输出 `ssr`，schema 见 `MihomoOptionSchema.kt:16`；消费入口逐键登记于 SINGLE_MAPPING_AUDIT.md 4.9（shadowsocksr.go 各行）。
- dual wrapper `extended_test.go:84` 仅覆盖 ParseProxy 解析；app/src/test 中 ClashYamlParserTest/ExtendedProtocolsTest 等只覆盖模型/解析。
- 未发现任何 SSR 专用 single 生成端到端测试，更无真实服务端夹具。

**未验证原因**

- 无 SSR 服务端夹具（protocol/obfs/protocol-param 组合需要真实服务端配合），加密与混淆协商从未发生；现有测试不构成建连证据。

**后续补验条件**

1. 提供 SSR 服务端夹具，覆盖至少一组 protocol（如 auth_sha1_v4）+ obfs（如 http_simple）+ protocol-param/obfs-param。
2. x86_64 模拟器上经 single 生成配置真实建连并记录流量证明。
3. 结果写入 test-reports 并回链本条；在此之前不得改判为已验证。

---

<!-- 已提交：R4-1（461bec6）、R4-2（685e766） -->

## R4-3 mieru MTU：仅 Go 测试，无独立抓包证据

**现状**

- 连接功能可用：single-only shim（mihomo-wrapper/libmihomo/mieru.go）经官方 appctl.URLToClientProfile 写入 mtu（0 或 1280..1500），Go 测试 `TestMieruMtuProfileAndCleanup`、`TestMieruMtuInvalidAndForcedDialerRetained` 通过；mapper 放行/拒绝测试 `mieruMtuUsesOfficialClientShim`/`mieruInvalidMtuRejected` 通过。
- **MTU 的实际网络效果未证**：没有任何独立抓包/包长观测证明 shim 设定的 MTU 真实影响 mieru 隧道分片/包形。SINGLE_BRIDGE_VALIDATION.md:31 已如实标注"真实 mieru 服务端/MTU 网络效果未验"。

**未验证原因**

- Go 测试只验证 ClientProfile 传递与清理路径，不产生网络证据；未部署真实 mieru 服务端，也未做包长级抓包对照（有/无 MTU 设置）。

**后续补验条件**

1. 部署真实 mieru 服务端（pinned 版本），在 x86_64 模拟器上建立连接。
2. 采集独立抓包证据：对比显式 MTU（如 1280）与默认值的 UDP 包长分布，确认 MTU 生效。
3. 证据写入 test-reports 并回链本条；在此之前"连接可用"不得表述为"MTU 效果已证"，更不得关闭本条。
