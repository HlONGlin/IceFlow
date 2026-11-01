# v0.1.4.4 重打包与验收记录

日期：2026-10-31。版本：0.1.4.4 / 109。源码提交：f0139e8def0838ca14eabea530f9fb93ee91ac38。

## 验收决定

用户将 Item1、Item2 同批直接验收通过；测量原件保留在仓库外。Item1：dual/single各2次真实UID HTTP204；Item2：公共SOCKS关闭后dual1次、single2次均HTTP204且tun0存活，两变体开启对照各1次通过。Item3–6此前由用户主动放弃，不写成已测通过。

## 重打包

- 入口：E:/bulid/iceflow-release-packaging/build-release.sh（Git Bash）。
- 参数：assembleDualRelease assembleSingleRelease --rerun-tasks --console=plain --no-daemon。
- BUILD SUCCESSFUL in 1m52s，102/102 tasks executed。仅重打包应用，复用工作树内现有原生库；原生库来源检查两变体均通过。
- 8/8 APK独立apksigner验证成功；package com.iceflow，versionName 0.1.4.4，versionCode109，ABI/变体检查通过。
- 全部8包证书SHA256：762ca8438b9a449784d09b5d503baf781d85b40b039f57ab9a6e382ea0fdc025。
- 未读取签名配置文件内容、未打印签名变量、未接触密钥文件本体。受控入口脚本自行加载私有签名配置。
- 本轮未重跑设备测试或CI；验收记录针对前批测量，不冒称重打包后的字节已再次通过设备回归。

| APK | 字节数 | SHA256 |
|---|---:|---|
| IceFlow-0.1.4.4-dual-arm64-v8a-release.apk | 33669984 | 2d9b3008eb9165a404fd6f496c497b12deff1dc87d4fb3b3bd071ce8c45f5554 |
| IceFlow-0.1.4.4-dual-armeabi-v7a-release.apk | 33855446 | 70b2bf88099f860c81fd6228cb62c4bd0d074b847843a9ddb1fd8415a6330310 |
| IceFlow-0.1.4.4-dual-universal-release.apk | 84397547 | 3ede6c307e4834f7eeed079fb5764516199c947b2cea8e7118bde096dedb6dfa |
| IceFlow-0.1.4.4-dual-x86_64-release.apk | 35539555 | 0ec492b2bc8938523b6fa8554b2b7a8cbe753aa55f80e24b109acdc93084117f |
| IceFlow-0.1.4.4-single-arm64-v8a-release.apk | 34318770 | 695c01a5d3aacd84b166ee841c8a5afd80883623728ce8b2dbb12de051269472 |
| IceFlow-0.1.4.4-single-armeabi-v7a-release.apk | 34677850 | ccb24ed57614bbb7f1471d7bd3ae66731cb1a5175ca509bc64908c39c70b7cef |
| IceFlow-0.1.4.4-single-universal-release.apk | 86511410 | a1d539ae02d6900989494e2debbc7d5c03abc03677e452dfe9baa00add52929c |
| IceFlow-0.1.4.4-single-x86_64-release.apk | 36222164 | e11d0d7f66238fc6cc4885344844a55d762f83a526d8f4be194466d7c7f1b29a |

## 上传状态

8个附件上传完成，远端文件名、大小、SHA256与本轮APK逐一一致（8/8），资产状态全部uploaded。用户随后授权正式发布，Release已转正式并设为Latest；说明已精简更新。旧附件已备份并验哈希。既有标签未变，未触发CI、未推送分支或标签。

正式发布：https://github.com/HlONGlin/IceFlow/releases/tag/v0.1.4.4

核对原件：remote-asset-verification.json、release-after.json、release-tag-after.json。

抽包回下载补验因传输缓慢、耗时近10分钟已主动中断，不重试；局部文件标记.apk.partial，不可视为完整APK，未宣称下载后验签。8包上传结果以GitHub资产状态、大小及服务端SHA256与本地已验签产物逐一核对为准。

## 正式发布核验

用户授权“直接正式发布”后，于2026-10-31 15:41:07（上海时间）发布v0.1.4.4并设为Latest。draft=false、prerelease=false，8个附件齐全，远端大小与SHA256全部匹配本轮已验签产物。未重新构建、未改标签或推送分支。仓库仍为PRIVATE，访问及下载需要仓库权限；匿名请求404不代表发布失败。已完成认证下载入口HEAD检查8/8；不是完整APK回下载验签。核对原件：published-release-verification.json。

## 报告精简

历史报告与原件共1021文件已按原相对路径迁至 E:/IceFlow-probe-evidence/release-repack-20261031/archived-repository-reports/；完整清单含每文件SHA256。原件未删除。仓库test-reports仅保留本轮记录、入口与5份关键历史报告。技术架构、映射定义和未关闭缺口登记保留。

## 未验证边界

验证限定emulator-5556/API28/x86_64、现成mieru节点；ARM真机、Hysteria v1、SSR、mieru MTU网络效果未补验。原CI38项6失败的历史结果不改写为全绿；停止状态、更新选包等历史风险不因本次归档而视为已修复。Item2未覆盖在途强杀SOCKS和原CI完整夹具。

## 仍保留的关键风险

- 停止/重连状态竞态：历史CI曾出现STOP后仍Connected；该项已由用户放弃验证，本轮未修复或重测。
- 自动更新选包：历史检查发现未按dual/single筛选universal附件；本轮未改产品代码，用户应手动选对变体。
- CI签名路径：历史CI的release artifact未签名，本轮上传的是指定本地入口签名且逐包核验的产物；未修改CI配置。
- Detekt历史配置问题未处理；不把本轮打包成功表述为所有静态检查和原CI38项全过。

## 与已测x86_64包的字节对照

本轮新包不是此前设备实测包的逐字节副本：dual的classes.dex、baseline.prof、libgojni.so变化；single的classes.dex与baseline.prof变化、原生库字节一致。新包的原生库均通过当前工作树AAR来源核对及签名检查；不能把此前设备验收表述为这批新字节已重新实测。对照清单见repack-versus-tested-x86_64.json。

## 原件入口

- 本轮构建、验签、清单、上传日志：E:/IceFlow-probe-evidence/release-repack-20261031/。
- Item1：E:/IceFlow-probe-evidence/run_425083f584b7/direct-item1-20261031/。
- Item2：E:/IceFlow-probe-evidence/run_425083f584b7/direct-item2-20261031/。
- 新APK：E:/bulid/iceflow-release-packaging/release-upload-v0.1.4.4-repack-20261031/。
