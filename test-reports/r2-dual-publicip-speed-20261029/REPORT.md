# R2 dual x86_64 公网 IP / 默认测速对照报告

- 测试日期：2026-10-29（Asia/Shanghai）
- 设备：仅 emulator-5556；ro.product.cpu.abi=x86_64、primaryCpuAbi=x86_64，设备标识 SM-S9180 / star2qltechn。结论仅适用于 x86_64 模拟器，ARM 真机未验证。
- dual 包：app/build/outputs/apk/dual/debug/app-dual-x86_64-debug.apk
- dual APK SHA-256：D52AD349F48AFF008182BB7967D6978EB14C61B887C56EA7BA8FDFCD09C20D79
- 当前 app/libs/xray.aar：74078454 bytes，SHA-256 32B2AA6FB9DFF8E8AB474E5AA960C318F82BCFB93295B3E007984353FECDE915。
- 网络条件：沿用 single 测试同一模拟器、同一 Wi-Fi、同一 Reality-spider-root 配置和 https://www.google.com/generate_204 默认测速 URL；未切换网络或 VPN 环境。

## 1. xray.aar 缺失诊断

结论：**环境/产物问题，非 dual 产品路径问题**。single 失败日志（phase2-step4-20261029/default-speed-test-logcat.txt）显示运行时直接报“未集成 xray-core（缺少 app/libs/xray.aar）”；源码中 XrayBridge 的反射类缺失时会抛出同一保护性错误，而 Gradle 只将 xray.aar 加入 dualImplementation。本次 dual APK 内含 lib/x86_64/libgojni.so 与 lib/x86_64/libiceflow_core.so，安装后记录 Xray 26.3.27 started，证明此前缺失来自测试/构建产物或安装包，不是 dual 产品路径缺陷。

## 2. dual 公网 IP 查询

结果：**失败；既有问题，不是 single 迁移缺口**。

dual 已启动 Xray 并建立 VPN（本批次 `dual-connect-run-logcat.txt`，同样保留 phase2-step4-20261029/dual-connected-logcat.txt），但公网 IP 日志为：

    Xray 26.3.27 started
    VPN started: Matrix-VLESS-TCP
    PublicIpLookup: Public IP lookup failed
    javax.net.ssl.SSLHandshakeException: Connection closed by peer

本批次日志同时记录 `PublicIpLookup: Public IP lookup failed` 与上述 TLS 异常；single 对照仍见下述同类异常。

single 对照日志（phase2-step4-20261029/public-ip-logcat.txt）同样记录 SSLHandshakeException: Connection closed by peer。fixture 侧 phase2-step4-20261029/reality-error.log 记录 api.ipify.org:443 出站后远端强制关闭连接；dual 与 single 结果一致，登记为共享网络/远端 TLS 既有问题，不计迁移缺口。

## 3. dual 默认测速

结果：**dual 对照同样失败，但失败原因不是缺 xray.aar；定性为既有网络/夹具问题，不是 single 迁移缺口**。

single 原始失败记录：

    开始测速节点 Reality-spider-root, testUrl=https://www.google.com/generate_204
    测速失败: 未集成 xray-core（缺少 app/libs/xray.aar）
    latency=-1ms, 耗时=0ms

本轮重新安装并启动 dual x86_64 包，启动日志记录 `Xray 26.3.27 started` 与 `VPN started: Matrix-VLESS-TCP`；点击默认测速后的完整证据见 `dual-default-speed-logcat.txt`：

    ConnectionPanel: 点击测速: connected=true, testing=false
    MainViewModel: testRunningConnection: 开始测速节点 Matrix-VLESS-TCP, testUrl=https://www.google.com/generate_204
    MainViewModel: 测速失败: Get "https://www.google.com/generate_204?...": EOF
    go.Universe$proxyerror: ... EOF
    MainViewModel: testRunningConnection: 测速完成, latency=-1ms, 耗时=34ms

这证明 dual 已进入 `ConnectionProbe.measure` → `XrayBridge.measureSocksDelay`，没有出现“未集成 xray-core（缺少 app/libs/xray.aar）”；失败是 Reality fixture/远端链路返回 EOF。dual 公网 IP 与测速均在同一批次先后执行，dual 对照结论成立。

## 4. 定性与审计登记

- 公网 IP 查询：**既有问题**。dual 与 single 均失败，错误均为 TLS 对端关闭；不计 single 迁移缺口。
- 默认测速缺 AAR：**环境产物问题**。single 失败由缺失/未打入 xray.aar 触发；dual 同样测速失败但走到了 Xray 测速核心，实际错误为 EOF；未发现 single 特有产品路径根因。
- 未修改代码；无需修复，也没有 single 补测或测试数变化。
- 测试边界：仅 x86_64 模拟器 emulator-5556，ARM 真机未验证；默认测速未得到有效延迟值，保留 EOF 作为共享环境/远端阻断证据。

## 5. 清场

已停止 com.iceflow、停止本地 fixture 进程，并执行 adb reverse --remove-all；最终 `adb-reverse-final.txt` 为空、`app-pid-final.txt` 为空。未保留 VPN 连接。

原始日志与 UI 证据保存在 test-reports/phase2-step4-20261029/；本报告为 R2 对照审计登记。
