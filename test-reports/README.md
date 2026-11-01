# 发布验证摘要

截至 2026-10-31，保留本页与少量关键历史结论；重复报告、原始日志、截图、设备数据已从仓库移出，原件未删除。

## 本轮验收

- 用户明确将 item 1（白名单真实 UID TUN）与 item 2（关闭公共 SOCKS 后 TUN）同批直接验收通过。
- Item 1：dual、single 各2次真实UID请求，均HTTP204，含分阶段计时。
- Item 2：dual1次、single2次关闭后请求，均HTTP204；10808拒绝连接，tun0存活；两变体开启对照均HTTP204。
- 固定节点 mieru 新加坡1(aws)；范围仅API28 x86_64 emulator-5556。Item2未覆盖在途强杀SOCKS或原CI完整夹具。
- Item3–6此前由用户主动放弃，不表述为测试通过。ARM真机、Hysteria v1、SSR、mieru MTU网络效果仍未补验。

## 保留入口

- RELEASE_0.1.4.4_20261031.md：本轮重打包、签名、8附件和上传核对。
- s1-dual-single-overlay-20261029/REPORT.md：双核/单核覆盖安装与数据保留。
- s2-full-rerun-20261029/S2-REPORT.md：历史测试数；不是本次新跑结果。
- s3-release-rebuild-20261029/REPORT.md：历史8包验签和原生库核对。
- wg-r1-retry-20261029/REPORT.md：WireGuard历史真实夹具验证。
- r2-dual-publicip-speed-20261029/REPORT.md：历史测速/公网IP问题结论。
- ../docs/core-migration/GAP_REGISTRY.md、PHASE2_CLOSEOUT.md：保留的技术结论与未关闭缺口。

## 原件位置

- 历史仓库报告与原始证据归档：E:/IceFlow-probe-evidence/release-repack-20261031/archived-repository-reports/（保留原相对路径）。
- 完整迁移清单、SHA256：E:/IceFlow-probe-evidence/release-repack-20261031/report-cleanup-manifest.json。
- Item1原件：E:/IceFlow-probe-evidence/run_425083f584b7/direct-item1-20261031/。
- Item2原件：E:/IceFlow-probe-evidence/run_425083f584b7/direct-item2-20261031/。

历史报告中的旧test-reports路径，可在上述归档目录按原相对路径查找。历史上线检查中的风险未因报告归档而视为修复。v0.1.4.4已按用户随后授权正式发布并设为Latest（2026-10-31），8包远端哈希匹配；仓库保持私有，访问需权限；未触发CI。
