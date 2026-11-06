# 阶段 2 / 第 2 步：批量清查与映射草稿验证

> 2026-10-31 归档说明：本文件引用的历史 test-reports 原始证据已移至 E:/IceFlow-probe-evidence/release-repack-20261031/archived-repository-reports/，保留原相对路径；精简入口见 docs/test-reports/README.md。历史结论与未验证项不变。


日期：2026-10-28。工作树：E:/IceFlow-core-variants，分支 feat/core-variants。

本次交付是映射层实现、单测、完整消费表草稿与一次性缺口清单。第 2 步尚未整体验收关闭；缺口需统一处置。没有接 single bridge，没有设备操作，没有进入第 3 步。

## 本次新跑结果

| 层级 | 总数 | 通过 | 失败 / error / skipped | 命令 |
| --- | ---: | ---: | --- | --- |
| Kotlin dual | 129 | 129 | 0 / 0 / 0 | Git Bash：./gradlew :app:testSingleDebugUnitTest :app:testDualDebugUnitTest --rerun-tasks --console=plain |
| Kotlin single | 209 | 209 | 0 / 0 / 0 | 同上；原 129 + 新增 single-only 80 项 |
| Go mihomo-wrapper | 11 | 11 | 0 / 0 / 0 | 子目录 mihomo-wrapper：go test -count=1 -json ./... |
| Go xray-wrapper | 57 | 57 | 0 / 0 / 0 | 子目录 xray-wrapper：go test -count=1 -json ./... |

最终 Kotlin 命令强制重新执行，52 actionable tasks 全部 executed，BUILD SUCCESSFUL。JUnit XML 按 flavor 分别汇总，未引用旧结果。两侧 Go 也禁用结果缓存并逐项数 run/pass 事件；Go wrapper 本步没有改动，基线不减。

本步未改 Rust，未重跑 Rust 测试；不将历史 Rust 数目计入本次新跑。设备/8包签名/原生库清单属于后续步骤，本步未执行、未援引历史结果冒充本步验证。

## 本次证据位置

原始日志保存在工作树 test-reports/phase2-step2-batch-20261028/：

- kotlin-final.log：最终全量双 flavor 强制重跑日志。
- mihomo-go.jsonl、mihomo-go-stderr.log：single wrapper Go 新跑。
- xray-go.jsonl、xray-go-stderr.log：dual wrapper Go 新跑。
- validation-summary.json：按模块/variant 分开计数。
- upstream-option-audit.json：初次声明/引用清查；最终限定接收对象后的表为已提交的 SINGLE_MAPPING_CONSUMERS.json。
- finish_audit.py / validate_artifacts.py：本地审计辅助脚本；未将整个历史 test-reports 目录加入提交。

JUnit 原始结果位于 app/build/test-results/testDualDebugUnitTest/ 与 testSingleDebugUnitTest/。

## 覆盖与边界

32 个 ServerProfile 字段覆盖全部 14 个协议适用范围，27 个 AppSettings 字段、全部 RoutingRule 字段与生成器全局输出逐项登记。递归清查 43 个 option/nested 结构、487 个字段；每个实际读取的 file:line 与原始文本逐项对照本机 pin 源码。BasicOption 的每协议转发另查，未转发项没有借用别的协议同名字段冒充入口。

HTTP/SOCKS5 ALPN 前提核对成立，登记为双核既有问题；保留同 dual 输出，不声称消费。mihomo pin 下 972 个 Go 文件 spider 不区分大小写检索为 0；Xray 空值归一与根路径种子证据、仅初始路径近似边界已写入 FEATURE_MAP 6.9 和完整落点表。

新增测试覆盖三协议 Upgrade 转换、两标志 conjunction、SS plugin、gRPC 全选项、XHTTP 下载/复用嵌套、TLS/Reality、四个 spiderX 用例、HTTP/SOCKS ALPN parity、三种 DNS 模式、IPv6、独立 SOCKS 鉴权、路由 AND/OR、链路顺序、未知键/条件忽略/冲突选项拒绝、不改原数据与错误不泄露字段值。每份配置显式输出三项自动路由 false，由测试遍历设置与 includeInbound 分支锁定。

SINGLE_MAPPING_AUDIT.md 第 5 节 G01–G14 是本次唯一集中待处置清单，未逐字段暂停、未自行修改 FEATURE_MAP 的既有分类。配置生成成功不等于 Go wrapper 接受或实际协议建连成功；特别是 G10：当前 wrapper 明确拒绝 cfg.Listeners，生成的独立鉴权 listener 配置只是有上游入口的映射草稿，不能宣称 single 运行时已就绪。

本步范围检查：main、dual、app/build.gradle.kts、两个 Go wrapper、Rust、持久化 schema 无 diff；上游 module cache 只读。签名文件/密码未读取或修改，无新增二进制产物进入 git。

提交分离：政策文档 f32845b；实现 16ff794；单测 30b118c。最终推送后以 git ls-remote 核对完整 HEAD。
