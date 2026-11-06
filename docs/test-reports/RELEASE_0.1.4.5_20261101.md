# v0.1.4.5 发布验证

日期：2026-11-01。版本：0.1.4.5 / 110。

## 变更

动画手动添加子菜单；链式代理与负载均衡置顶，直接编辑并添加服务器配置；单核、双核配置生成及备份支持；修复分享链接续行备注丢失与默认名称误用 SNI / Host。

## 本地检查

- dual JVM：149 项通过；single JVM：246 项通过；共 395 项，无失败或错误。
- lintDualDebug、lintSingleDebug 及 release lint 通过；两个 debug 变体各有 36 条警告，未作为零警告报告。
- 指定入口 E:/bulid/iceflow-release-packaging/build-release.sh 生成 8 个正式签名 APK。
- 打包时遇到压缩堆内存不足，使用 --no-parallel --max-workers=2 与 8 GiB Java 堆后构建成功。
- 8 包独立 apksigner 验证通过；应用 ID com.iceflow，版本码 110，版本号 0.1.4.5，ABI 与文件名一致。
- 证书 SHA256：762ca8438b9a449784d09b5d503baf781d85b40b039f57ab9a6e382ea0fdc025，与此前正式版一致。
- Gradle 原生库来源检查通过。本次应用打包复用现有原生库，没有重新编译 Go / Rust。
- CI 版本解析在 main / v0.1.4.5 两条路径均返回 0.1.4.5 / 110。
- CI 对已有 Release 的保护脚本通过已存在、不存在两种路径验证，保留手动签名附件。

## 设备检查

- emulator-5556 / x86_64 上分别覆盖安装 single、dual 的 110 正式包。
- 设备版本、已安装 APK 哈希与对应发布附件一致。
- 手动添加子菜单顺序、链式代理直接编辑、负载均衡直接编辑及返回均通过，未要求先选分组。
- 最终保留 dual 0.1.4.5；原有节点数据保留。
- 同一应用功能源码在前轮 109 包完成完整/续行长 Reality 链接的单核、双核剪贴板导入检查：备注 香港1(le)，SNI www.amazon.com 独立保留；本轮 110 包仅复验覆盖安装与菜单。
- ARM 真机与完整 CI 网络集成套件未在本地复验。

## 安装包

| 文件 | 字节数 | SHA256 |
|---|---:|---|
| IceFlow-0.1.4.5-dual-arm64-v8a-release.apk | 33688456 | e2c17601a2dda5939f7558c6e4a1d2451eedfe9c2d875ceca16ffa003ffc3468 |
| IceFlow-0.1.4.5-dual-armeabi-v7a-release.apk | 33873914 | a1495169247d9f29aadf10a3048caa62aa88256f9d33c9757fee99e912007bdf |
| IceFlow-0.1.4.5-dual-universal-release.apk | 84416015 | 6b4f66aa21aaea3634991ad3f50172ead5baa22e160bf811a6f71e1d41c86ac6 |
| IceFlow-0.1.4.5-dual-x86_64-release.apk | 35558023 | 6177dd2f03b64695bd2178c70084bf309fe46cd610cb1fe18d690400018cb540 |
| IceFlow-0.1.4.5-single-arm64-v8a-release.apk | 34339610 | b4f37b373c6574a26a716984aa005edf9617202d0ff252e9af7ebc98830e79e6 |
| IceFlow-0.1.4.5-single-armeabi-v7a-release.apk | 34698694 | b9d5174fb2513d09c4dec40e268607a7924fcafd7e8123a4ae98b05c004f42bb |
| IceFlow-0.1.4.5-single-universal-release.apk | 86532250 | b040c9f28f730b61b0fce46f86da2d49fbcf82ea93ecd3f15ed416aaf223b218 |
| IceFlow-0.1.4.5-single-x86_64-release.apk | 36243008 | 7497d91c753a4457510c4a0c907cab90f0c8a077aea5a2143403fc158b566897 |

## 产物位置

E:/bulid/iceflow-release-packaging/release-upload-v0.1.4.5-20261101/：APK、SHA256SUMS.txt、artifact-manifest.json、device-smoke.json。

设备截图：E:/tmp/iceflow-v0.1.4.5-device-check/。

发布采用同名新标签 v0.1.4.5；上传后按 GitHub 服务端 digest、文件大小、上传状态逐包核对，并将版本设为 Latest。
