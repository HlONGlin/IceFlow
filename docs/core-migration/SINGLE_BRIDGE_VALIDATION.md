# 阶段 2 第 3 步：single bridge 真接线验收

工作树 E:/IceFlow-core-variants；分支 feat/core-variants；本轮起点 32e9bda。

## 第 2 步处置先行交账

| 缺口 | 本步命运 | 依据/实现 |
| --- | --- | --- |
| G04 | 撤销 SNI/name-cert-verify 顶替；模型 SOCKS sni 在调用共享 builder 前明确拒绝，raw name-cert-verify 独立直映 | c57e929；MihomoConfigBuilder.checkProfileFields；socks5.go:180,183 的 SNI/证书校验分工不等价 |
| G02 | 固定 WS/gRPC ALPN、普通 WS client-fingerprint 缺口继续明确 CONFIGURATION_OR_SUBSCRIPTION 拒绝；其他已审定 TLS 分支保留 | SINGLE_MAPPING_AUDIT 第5节的 pin 消费证据、既有拒绝单测 |
| G03 | WS Reality、Reality 用户 ALPN/allowInsecure=true 继续明确拒绝；spiderX 仅保留批准的初始根路径近似 | 不改 Reality 上游/dual，也不宣称生命周期等价 |
| G05 | 无消费的 VLESS ws-headers、WG 顶层单 peer allowed-ips 继续拒绝；reserved 保留 | 不擅自转换为 peer；既有单测与 pin 证据保留 |
| G06 | 未读别名、冲突来源、WG 多地址/前缀丢失、TUIC 版本分支、无转发 tfo/mptcp 继续明确拒绝 | 不静默丢字段、不修改 shared ExtendedConfigBuilder |
| G11 | 开工前补专门设计，再实现统计与 mieru MTU shim | SINGLE_BRIDGE_STEP3.md，设计先行 c57e929；实现 6cf7eae |

冻结 FEATURE_MAP 类别没有改判。处置为明确限制或 shim 实现，不把配置生成成功当成运行时等价。

## 接线与所有权

- single CoreVariantBridge → MihomoRuntime → gomobile Libmihomo；配置使用第2步 MihomoConfigBuilder。直接 Java API 引用保留 release 混淆可达性。readiness 检查真实绑定加载及 pin v1.19.32；healthy 另要求配置、已成功创建的 TUN 与 tunnel.Running。核心存在时不再套用“单核未就绪”，缺核心才保留占位文案。
- 配置在 service 的 buildConfig 阶段额外调用 wrapper Validate，使解析失败落在原 CONFIGURATION_OR_SUBSCRIPTION 阶段；未改 main catch/错误分类结构。
- Prepare 配置/启动监听；AttachTun 借用原 fd 后 dup。原始 fd 始终由 service 建立、持有和关闭；single 负责副本与 stack 的失败/停止清理。StopTun 保留 core/listeners 供现有快重连使用；Stop 另清理监听/连接/代理。主错误保留在前，清理诊断附后。
- 健康与统计接口供原统计循环消费；main 采样、persistTraffic、状态发布结构未改。相对本轮起点，app/src/main、app/src/dual、xray-wrapper、rust-core 均零 diff；applicationId、版本、签名设置和数据 schema 未改。

## G10/G11/G12 落点

G10：listeners 数组每项恰好6键：type=socks、name=tun-in 或 socks-in、listen=127.0.0.1、port=整数1..65535、udp=布尔、users=0或1个 username/password 对象。tun-in UDP=true，两入站独立凭据。拒绝额外键、非 loopback、重复名/端口、不成对凭据。wrapper 自己调用 Listen 并传播 bind 错误，不依赖上游 PatchInboundListeners 的只记日志行为。见 listeners.go 与 TestAppListeners*、TestListenerBindFailurePropagatesAndCleans、TestRealExecutorAppListenersLifecycle。

G11 统计：traffic.go 包装每个配置节点的 TCP Read/Write、buffer、优化 copy、UDP I/O。计数器保留已关闭连接累计；不轮询会消失的 tracker，不在读取/关闭时重复累加。DIRECT/REJECT 不包装。QueryStats 给各节点 tag；StatsJSON 按 wrapper-only iceflow-selected-outbound 选择当前节点，避免把链条相加重复累计。有落地节点时选中节点可能是 chain-0/chain-1，不能直接认定为链尾 proxy。成功 TUN 交接重置会话基线，对齐 service 重置 persistedUp/Down；tag 明细保留到本次 core 停止。现有 service 仍只持久化选中节点，不声称增加所有链成员持久化。见 TestTraffic*、TestSelectedMiddleNodeAndReattachSessionBaseline。

G11 mieru MTU：single-only 官方客户端 shim，把 mtu 真写入官方 ClientProfile，范围0或1280..1500；保留节点名、stream/packet 前置 dialer 与 DNS bypass 语义，独立关闭官方 client 和原 proxy。MihomoOptionSchema 的 mtu 是 wrapper 扩展，不谎称 upstream MieruOption 有入口。见 mieru.go、TestMieruMtuProfileAndCleanup、TestMieruMtuInvalidAndForcedDialerRetained；mapper 放行/非法值测试同步更新。上游和 dual shim 未改。真实 mieru 服务端/MTU 网络效果未验。

G12：TestForcedDialerPrivateAddressFixture 使用真实 loopback echo、命名前置代理与192.168.77.9目标元数据验证 forced named dialer 调用路径。该测试进入本步清单并通过；它不构成真实双跳服务端及私网 DIRECT 规则的完整端到端矩阵，后者明确留第4步。

## 本轮新跑单测

| 模块 | 总数/通过 | 失败/跳过 | 证据 |
| --- | --- | --- | --- |
| Kotlin dual | 129/129 | 0/0 | kotlin-final.log、Gradle XML |
| Kotlin single | 216/216 | 0/0 | 共享129 + single87；kotlin-final.log、Gradle XML |
| mihomo wrapper Go | 36/36 | 0/0 | 25顶层+11子例；原基线11保留；mihomo-go-final.jsonl |
| Xray wrapper Go | 57/57 | 0/0 | 25顶层+32子例；xray-go.jsonl |
| Rust | 7/7 | 0/0 | cargo test --all-targets；rust.log |

均在此 ASCII 工作树及其模块新跑。Kotlin 使用 testDualDebugUnitTest testSingleDebugUnitTest --rerun-tasks；Go 使用 go test -count=1 -json ./...。开发中一次 AAR 重建与 Gradle 并发读写导致 zip END header 错误，已串行后完整重跑通过；未借用旧结果。

三 ABI AAR 带 with_gvisor 重建成功；mihomo.aar 与 xray.aar 同口径仍 ignored，不入 git。Git Bash /e/bulid/iceflow-release-packaging/build-release.sh assembleSingleRelease exit0、BUILD SUCCESSFUL、DONE。本步新建 single 四包，不冒充收尾八包重建。未读取 signing.local.sh 内容、未使用 .cmd、未打印签名环境变量。

## APK 级原生库与签名

方法：逐 APK ZipFile 枚举 lib/ABI，读取 libgojni.so 字节算 SHA-256，与 mihomo.aar / xray.aar 同 ABI 比较；逐包 apksigner verify --print-certs。

新 single 四包证书 SHA-256 均为 762ca8438b9a449784d09b5d503baf781d85b40b039f57ab9a6e382ea0fdc025。

| APK | ABI | libgojni.so | libiceflow_core.so |
| --- | --- | --- | --- |
| app-single-arm64-v8a-release.apk | arm64-v8a | 等于新 mihomo AAR、不同于 Xray AAR | 无 |
| app-single-armeabi-v7a-release.apk | armeabi-v7a | 等于新 mihomo AAR、不同于 Xray AAR | 无 |
| app-single-x86_64-release.apk | x86_64 | 等于新 mihomo AAR、不同于 Xray AAR | 无 |
| app-single-universal-release.apk | arm64-v8a | 等于新 mihomo AAR、不同于 Xray AAR | 无 |
| app-single-universal-release.apk | armeabi-v7a | 等于新 mihomo AAR、不同于 Xray AAR | 无 |
| app-single-universal-release.apk | x86_64 | 等于新 mihomo AAR、不同于 Xray AAR | 无 |

各 ABI libgojni.so SHA-256：

- arm64-v8a：2356390a190f580f74db6a9de0d762fc91b5feaec16fb95323ca53d214e477ee
- armeabi-v7a：9dc165f6501e1cfcc5e866eea9625230ec9eab08f0e5ae1b8e83244a4d595624
- x86_64：a8000c66ba859a06ec65e25859741a028254185ca4be8aadfaa75d964f5caea2

详单 apk-verification.json。dual 本步未重建；其源码/依赖零 diff，新八包检查在收尾执行。

## emulator-5556 基本冒烟

| 项目 | 结果与证据 |
| --- | --- |
| 设备与安装 | SM-S9180、API28、x86_64；覆盖安装已验签 single x86_64 release 成功 |
| 冷启动 | Status ok，TotalTime569ms/WaitTime574ms；重启后304ms/310ms |
| 常见协议连接/断开 | 本地 VLESS TCP 真实夹具；service 两阶段启动成功；断开、重连完成；未伪装占位成功 |
| TUN generate_204 | 最终全局路由，设备 curl 对 fake-IP 198.18.0.4 发出请求，exit0/HTTP204；tun-final-proof.txt |
| SOCKS 模式 | 127.0.0.1:10808 SOCKS5h 握手成功，exit0/HTTP204；socks-final-proof.txt |
| 出网来源交叉核对 | 22:48:07 两次 www.gstatic.com:443 真实夹具记录对应最终两请求；vless-fixture.jsonl。移除 reverse 的负对照失败，核心日志指向127.0.0.1:18443 refused；negative-control.txt |
| 磁贴断开 | 已连接显示 Stage3-VLESS；点击断开后未连接；tile-connected.xml、tile-recovered.xml |
| 通知断开 | 展开有断开动作，点击后磁贴未连接、应用常驻通知移除，service Stop 对应；notification-expanded.xml、notification-recovered.xml、final-state.xml |
| gVisor/原生崩溃 | 带 with_gvisor 的 release 实际完成建栈和204；保存的启动/重启日志中 FATAL EXCEPTION、Fatal signal、UnsatisfiedLinkError、panic、NewStack、GVisorTun、interface conversion、start failed、cleanup failed 全0；runtime-checks.json，前后段 logcat 均保留 |

ADB：7个操作批次遇离线；可见 transport 18→19→20→21，主动重启恢复后24→25→26→27。使用 E:/工具/LDPlayer9/ldconsole.exe reboot --index 1 主动重启1次；其中一个离线批次处于重启恢复期，因此不把7批次宣称为精确7次独立瞬断。非连续监控下精确瞬断次数未知。恢复后重核状态并重建 reverse；第一台只枚举，未操作。

留痕：首条带通配符 curl 被 shell 展开，记录作废；连接尚未完成时的 SOCKS refused 也未当通过。最终改为 Python subprocess 精确参数、verbose curl 与夹具记录交叉确认。夹具 stdout 管道曾阻塞日志，改为输出文件后最终两请求均有实时证据；没有修改应用规避测试。

清理：VPN断开、测试节点删除、路由恢复原规则模式、fixture停止、reverse最终为空；保留新 single release。未覆盖回dual；双向互换数据保留留第5步。

## 提交与未验证

- c57e929：处置/设计先行，G04撤顶替。
- 6cf7eae：single wrapper生命周期、精确入站、统计/MTU shim与mapper元数据。
- ffa6676：mapper和Go shim回归测试。
- 9e9f85e：single bridge真接线与runtime测试。

完整协议/传输矩阵、HTTP Upgrade真实服务端、AnyTLS/Hy2/mieru服务端与MTU线上效果、DNS/IPv6/分应用矩阵、真实私网链路规则对照、默认测速URL/公网IP旧告警定因、所有链成员持久化策略、双向互换与数据保留、真机行为、性能/长稳、八包收尾新跑验签均未在本步宣称验证。main内独立Xray测速/路由资源更新/导入入口本步未迁移，不能宣称single全功能闭环。bittorrent/destOverride旧问题只登记未改。

本步只证明基本链路；未做第4步完整兼容矩阵、第5步互换或阶段3自动更新，不合main、不发版、不打tag。原始证据在 test-reports/phase2-step3-20261028；不批量add历史test-reports，不提交AAR或含临时连接认证信息的原始日志。
