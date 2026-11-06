# Single 配置映射全量消费入口清查（阶段 2 / 第 2 步草稿）

> 2026-10-31 归档说明：本文件引用的历史 test-reports 原始证据已移至 E:/IceFlow-probe-evidence/release-repack-20261031/archived-repository-reports/，保留原相对路径；精简入口见 docs/test-reports/README.md。历史结论与未验证项不变。


状态：批量清查已完成；有消费证据的映射与拒绝护栏已实现并单测。缺口尚待一次性处置，不能据此宣布第 2 步整体验收完成。生成器仅在 single source set，尚未接 bridge；未上设备。main、dual、wrapper、schema 与上游源码均未改动。

工作树：E:/IceFlow-core-variants，分支 feat/core-variants。pin：mihomo v1.19.32；Xray v1.260327.0。源码来自本机 Go module cache；下列 mihomo 相对路径均相对该 pin 根目录。落点 B = app/src/single/java/com/iceflow/data/MihomoConfigBuilder.kt；S = MihomoOptionSchema.kt；T = app/src/testSingle/java/com/iceflow/data/MihomoConfigBuilderTest.kt。表中的“消费”是实际 option/receiver 读取或传给 transport 的入口，不仅是 struct 声明。传入 transport 不代表全部组合已做运行时等价验证。条件消费须同时满足第 5 节限制。

## 1. 已批准两项处置的源码复核

### spiderX

- Xray infra/conf/transport_internet.go:961-989 将空值归一为 /，并解析 SpiderY 查询控制；transport/internet/reality/reality.go:196-217 以根路径种子初始化缓存并构造首个 GET；:243-258 收集响应链接；:289-299 从路径缓存抽取后续请求。
- Mihomo adapter/outbound/reality.go:13-18 的 RealityOptions 只有 PublicKey、ShortID、SupportX25519MLKEM768；:20-44 转为运行时配置。component/tls/reality.go:31-36 同样无 spiderX；:134-163 fallback 在 :151 固定 GET https://serverName。递归检索 pin 模块 972 个 Go 文件，spider 不区分大小写零命中。
- 只允许空值和精确 /，性质是“仅初始根路径近似”。路径缓存、响应链接采集、SpiderY、延时与 padding 生命周期不等价；绝不称为保留 spiderX 语义。任意查询参数或非根路径显式 CONFIGURATION_OR_SUBSCRIPTION，点名字段与不支持原因，不改原数据。FEATURE_MAP 风险 6.9 已同步。
- T：spiderEmptyIsInitialPathApproximation / spiderRootIsInitialPathApproximation / spiderQueryIsRejectedWithoutMutation / spiderNonRootIsRejected。

### HTTP/SOCKS5 ALPN 前提核对：成立

- dual CoreVariantBridge.buildConfig → XrayConfigBuilder；XrayConfigBuilder.kt:64-69 → ExtendedConfigBuilder；ExtendedConfigBuilder.kt:21 输出 alpn；native/xray-wrapper/libxray/extended.go:41-45,60-78 → adapter.ParseProxy，:82-87 专用适配仅 mieru。
- adapter/parser.go:44-57 对 HTTP/SOCKS5 走 Decode + New；HttpOption（http.go:28-43）、Socks5Option（socks5.go:30-44）及 BasicOption（base.go:193-207）均无 ALPN 字段；http.go:148-168、socks5.go:173-189 TLS 构造没有用户 NextProtos。
- 定性：双核既有同病，非迁移差异。single 与 dual 一样输出 alpn、不拒绝、不声称消费；只此经批准的例外。真正 ALPN 消费入口属于另立内核补丁，FEATURE_MAP 风险 6.10 登记。其他协议按真实消费分支处理。
- T：httpAlpnMatchesExistingDualOutput / socksAlpnMatchesExistingDualOutput。

## 2. ServerProfile 全字段 × 协议矩阵

14 协议均覆盖；“其他协议非默认拒绝”避免把不适用值静默丢弃。默认空值/模型默认值不构成待保留的用户选项。clashConfig 存在时与 dual 一致以原始 JSON 为权威，通用字段是导入/编辑投影（ClashYamlParser.kt:53-74,79-82）；不会重新从投影覆盖原始 JSON。未知原始字段拒绝，原数据保留。name 转内部稳定 tag 是链路转换，不改变持久化名称。

| 字段/声明 | 适用协议与行为 | 落点 | mihomo 消费入口（文件:行） | 对应测试（T） |
| --- | --- | --- | --- | --- |
| id (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:30) | 全部：链路去重/定位，app-owned | B.chain | 无核心消费；B.chain 与 ServerStore.selected | chainOrderAndDialersPreserved / duplicateChainNodeRejected |
| name (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:31) | 全部：内部 tag 重命名，原数据不变 | B.outbound | 各协议 name：第 4 节 | metadataDoesNotMutatePersistedProfile |
| protocol (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:32) | 14 协议 dispatch | B.type/outbound | adapter/parser.go:26-81 | allFourteenProtocolEndpointsMap |
| address (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:33) | 14 协议 → server；WG peers 时按 peer 端点 | B.native/extended/outbound | 逐协议见第 4 节对应键的实际消费行 | allFourteenProtocolEndpointsMap |
| port (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:34) | 14 协议 → port；mieru port-range 替代 | B.native/extended/outbound | 逐协议见第 4 节对应键的实际消费行 | allFourteenProtocolEndpointsMap |
| uuid (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:36) | VMess/VLESS/TUIC v5 → uuid；其他非空拒绝 | B.native/extended/checkProfileFields | 逐协议见第 4 节对应键的实际消费行 | allFourteenProtocolEndpointsMap |
| password (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:38) | Trojan/SS/SSR/HTTP/SOCKS/mieru/AnyTLS/Hy2 → password；Hy1 → auth-str；TUIC 分支；Sudoku → key；VMess/VLESS/WG 非空拒绝 | B.native/extended | 逐协议见第 4 节对应键的实际消费行 | allFourteenProtocolEndpointsMap / tuicV4TokenAndFastOpenMapped |
| alterId (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:39) | VMess 0 直映；>0 按 FEATURE_MAP 风险6.4拒绝；其他非0拒绝 | B.native/checkGaps | adapter/outbound/vmess.go:485；兼容尚未批准 | unsupportedAlterIdRejected |
| security (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:41) | VMess → cipher；SS/SSR → cipher；其他非默认拒绝 | B.native/checkProfileFields | 逐协议见第 4 节对应键的实际消费行 | allFourteenProtocolEndpointsMap |
| flow (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:42) | VLESS → flow；其他非空拒绝 | B.native | adapter/outbound/vless.go:464-487 | realityKeysAndVisionPreserved |
| encryption (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:43) | VLESS → encryption；其他非默认拒绝 | B.native | adapter/outbound/vless.go:511 | allFourteenProtocolEndpointsMap |
| network (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:45) | VMess/VLESS/Trojan tcp/raw/ws/grpc；VLESS xhttp；Upgrade 转 ws；扩展协议不得携带通用非默认 transport | B.native/normalizeTransport/checkGaps | vmess.go:175-281；vless.go:156-261,560-614；trojan.go:79-165 | rawTcpNormalized / 三协议 Upgrade / grpcAllOptionsPreserved / xhttpNestedOptionsPreserved |
| headerType (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:46) | native 非 none 拒绝；raw Clash 并存非 none 拒绝 | B.native/checkProfileFields | TCP HTTP 等价性待处置，见 G01 | tcpHttpHeaderRejected |
| host (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:47) | ws → headers.Host；xhttp → host；TLS 的空 sni fallback；被其他值覆盖则拒绝 | B.native | vmess.go:191-203；vless.go:172-183,615-620；trojan.go:96-103 | 三协议 Upgrade / tlsFieldsHaveDistinctDestinations |
| path (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:48) | ws/httpupgrade → path；VLESS xhttp → path；其他非空拒绝 | B.native | vmess.go:181；vless.go:162,643；trojan.go:84 | 三协议 Upgrade / xhttpNestedOptionsPreserved |
| serviceName (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:49) | VMess/VLESS/Trojan grpc → grpc-service-name；其他非空拒绝 | B.native | vmess.go:610；vless.go:575；trojan.go:386 | grpcAllOptionsPreserved |
| tls (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:51) | VMess/VLESS TLS bool；Trojan 固定 TLS；HTTP/SOCKS 可选；QUIC/AnyTLS 内建 TLS；WG/SSR/mieru/Sudoku 无通用开关 | B.native/extended | vmess.go:347；vless.go:307；trojan.go:151-165；http.go:150；socks5.go:175 | allFourteenProtocolEndpointsMap / tlsFieldsHaveDistinctDestinations |
| sni (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:52) | VMess/VLESS → servername；Trojan/Hy1/Hy2/TUIC/HTTP/AnyTLS → sni；SOCKS 特殊差异见 G04；其他非空拒绝 | B.native/extended | 逐协议见第 4 节对应键的实际消费行 | tlsFieldsHaveDistinctDestinations / socksSniGapRejected |
| alpn (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:53) | 有条件消费；WS/gRPC/Reality 差异见 G02/G03；HTTP/SOCKS 经批准例外 | B.native/extended/checkGaps | HTTP/SOCKS：无（双核既有问题，见登记）；其他第4节与G02 | tlsFieldsHaveDistinctDestinations / 两 ALPN parity tests / fixedWsAlpnRejected |
| fingerprint (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:54) | client-fingerprint；native/AnyTLS；其他非空拒绝；WS 条件限制 G02 | B.native/extended/checkProfileFields | vmess.go:362；vless.go:322；trojan.go:164；anytls.go:161；WS分支见G02 | tlsFieldsHaveDistinctDestinations / anytlsAliasesAndNestedEchMap / wsFingerprintGapRejected |
| publicKey (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:55) | Reality → reality-opts.public-key；WG → public-key；其他非空拒绝 | B.native/extended | reality.go:26-32；wireguard.go:432-436 | realityKeysAndVisionPreserved / allFourteenProtocolEndpointsMap |
| shortId (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:56) | Reality → short-id；其他非空拒绝 | B.native/extended | adapter/outbound/reality.go:34-41 | realityKeysAndVisionPreserved |
| spiderX (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:57) | 所有协议先校验；空或精确 / 初始路径近似；其他拒绝 | B.spider | 无可配置入口；component/tls/reality.go:151 固定 GET；见第1节批准近似 | 四项 spider tests |
| allowInsecure (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:58) | 有 TLS 消费入口时 → skip-cert-verify；Reality 独立校验证据见G03；不适用true拒绝 | B.native/extended/checkGaps | 逐协议见第 4 节对应键的实际消费行 | tlsFieldsHaveDistinctDestinations |
| username (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:59) | HTTP/SOCKS/mieru → username；其他非空拒绝 | B.extended/checkProfileFields | 逐协议见第 4 节对应键的实际消费行 | allFourteenProtocolEndpointsMap |
| protocolOptions (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:61) | 扩展协议白名单/别名/冲突校验；native 非空拒绝；raw同时存在拒绝 | B.checkOptionKeys/extended | 第4节逐键；未读别名见G06 | hysteriaAliasesAndObfsConvert / anytlsAliasesAndNestedEchMap / unmappedProtocolOptionRejected |
| clashConfig (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:63) | 14 协议原始对象递归 schema；未知/无消费字段失败，不改原对象 | B.outbound + S.validate | 第4节逐键消费者与门控 | grpcAllOptionsPreserved / xhttpNestedOptionsPreserved / unknownTopOptionRejectedWithoutSecretLeak |
| subscriptionId (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:64) | 全部 app-owned：选 subscription 的前置/落地链 | B.chain | 无核心消费；ServerProfile.kt:74-85 | chainOrderAndDialersPreserved |
| groupId (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:66) | 全部 app-owned：本地分组的前置/落地链 | B.chain | 无核心消费；ServerProfile.kt:89-95 | chainOrderAndDialersPreserved |
| latencyMs (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:68) | 全部 app-owned 持久化/显示；mapper 不改 | B 无输出 | 无核心配置消费；ServerProfile.kt:103-109 | metadataDoesNotMutatePersistedProfile |
| uploadBytes (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:69) | 全部 app-owned 累计值；按节点来源统计待G11 | B 无输出 | 无核心配置消费；IceFlowVpnService.kt:571-581；tracker.go:27-31 | metadataDoesNotMutatePersistedProfile |
| downloadBytes (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:70) | 全部 app-owned；同 uploadBytes | B 无输出 | 无核心配置消费；IceFlowVpnService.kt:571-581 | metadataDoesNotMutatePersistedProfile |

## 3. 全部 AppSettings、规则字段与全局输出

| 字段 | 落点/归属 | mihomo 消费入口（文件:行） | 对应测试 |
| --- | --- | --- | --- |
| socksPort (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:193) | listeners.port | listener/inbound/base.go:92-99 | authAndUdpListenersIndependent |
| remoteDns (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:194) | dns.nameserver tcp://IP#proxy | config/config.go:1210-1295,1438 | dnsRealIpStillUsesProxyTcp |
| domesticDns (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:195) | direct/proxy-server nameserver + policy | config/config.go:1446-1464 | dnsDirectDomainPolicyConverted |
| bypassLan (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:196) | 过滤 builtin:private，CIDR表同dual | rules/parser.go:40-44,84-87 | lanToggleAndRuleOrderPreserved |
| fakeDns (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:198) | dns.enhanced-mode fake-ip/redir-host | config/config.go:1433,1508；constant/dns.go:39-48 | dnsFakeIpMode / dnsRealIpStillUsesProxyTcp |
| testUrl (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:199) | 外层保留；测速核心接口未实现 G12 | 无核心配置消费；app/src/main/java/com/iceflow/core/ConnectionProbe.kt:1；见现有测速路径 | 既有共享测速测试；本步不接线 |
| routingRules (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:200) | B.rules/rule/dns；顺序/组合/action | rules/parser.go:18-94；config/config.go:1339-1410 | ruleFieldsOrWithinAndAcross / geoAndDomainPrefixesMapped |
| routingMode (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:201) | RULE逐条；GLOBAL/DIRECT末规则 | rules/parser.go:93-94 | globalAndDirectModes |
| bypassApps (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:202) | 外层保留；VpnService包过滤 | 无核心消费；IceFlowVpnService.kt:395-407 | 既有 AppRoutingPolicy 单测；未改 |
| proxyAppsOnly (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:203) | 外层保留；旧分应用开关 | 无核心消费；IceFlowVpnService.kt:332,395 | 既有共享单测；未改 |
| ipv6Enabled (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:204) | global ipv6/dns.ipv6/TUN地址 | hub/executor/executor.go:249,396；config/config.go:1705 | ipv6SwitchControlsDnsAndTun |
| dnsServers (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:205) | 禁用DNS代理时直接nameserver；VpnService仍addDnsServer | config/config.go:1438；IceFlowVpnService.kt:391 | dnsDisabledUsesDirectDeviceServers |
| socksEnabled (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:206) | 公开listener启停；测量模式无listener | config/config.go:684-688,1000+；G10集成限制 | disabledPublicSocksKeepsPrivateListener / measurementOmitsListeners |
| socksUdp (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:207) | 公开listener.udp | listener/inbound/socks.go:14-22 | authAndUdpListenersIndependent |
| socksUsername (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:208) | 公开listener.users；与内部鉴权分离 | listener/inbound/auth.go:9-31 | authAndUdpListenersIndependent |
| socksPassword (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:209) | 同 username；与dual相同仅两者非空才启用 | listener/inbound/auth.go:9-31 | authAndUdpListenersIndependent |
| dnsProxyEnabled (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:210) | false直连指定DNS；true远程TCP经proxy | config/config.go:1210-1295,1438 | dnsDisabledUsesDirectDeviceServers |
| theme (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:211) | 外层UI，不进入核心 | 无核心消费；ServerProfile.kt:224-229 / ui/theme | 既有共享测试；未改 |
| nightMode (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:212) | 外层UI | 无核心消费；ServerProfile.kt:224-229 / ui/theme | 既有共享测试；未改 |
| textSize (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:213) | 外层UI | 无核心消费；ServerProfile.kt:164-168 / ui/theme | 既有共享测试；未改 |
| showTrafficStats (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:214) | 外层显示；统计集成见G11 | 无核心配置消费；TrafficSampler.kt | 既有 TrafficSamplerTest；未改 |
| showNodeLatency (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:216) | 外层显示 | 无核心配置消费；ServerProfile.kt:215-216 / ui | 既有共享测试；未改 |
| showPublicIp (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:218) | 外层显示，SOCKS集成G10/G12 | 无核心配置消费；ui/PublicIpLookup.kt | 既有 PublicIpLookupTest；未改 |
| logRetentionDays (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:219) | 外层日志保留 | 无核心消费；app/src/main/java/com/iceflow/service/AppLog.kt:1 | 既有共享测试；未改 |
| appRoutingEnabled (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:220) | 外层包过滤 | 无核心消费；IceFlowVpnService.kt:395-407 | 既有共享测试；未改 |
| appProxyMode (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:221) | 外层包过滤模式 | 无核心消费；IceFlowVpnService.kt:332,395-407 | 既有共享测试；未改 |
| language (app/src/main/java/com/iceflow/data/model/ServerProfile.kt:222) | 外层UI | 无核心消费；ServerProfile.kt:178-187 / AppStrings | 既有共享测试；未改 |

| RoutingRule 字段 | 落点 B.rule | mihomo 消费入口（文件:行） | T |
| --- | --- | --- | --- |
| id/name | app-owned，id过滤内置规则，name不输出 | 无核心消费；RoutingRule.kt:15-16；B.rules | lanToggleAndRuleOrderPreserved |
| enabled | 过滤禁用项 | 无核心消费；B.rules | lanToggleAndRuleOrderPreserved |
| action | proxy / DIRECT / REJECT | rules/parser.go:12-94 传 target | ruleFieldsOrWithinAndAcross |
| domains | full/domain/keyword/regexp/geosite → DOMAIN等；裸域/ext缺口G08 | rules/parser.go:18-29 | geoAndDomainPrefixesMapped / unprefixedDomainIsPending |
| ips | IP-CIDR/CIDR6/GEOIP/private固定表 | rules/parser.go:30-44 | privateCidrListExpanded / ruleFieldsOrWithinAndAcross |
| source | SRC-IP-CIDR/SRC-GEOIP | rules/parser.go:33-44 | ruleFieldsOrWithinAndAcross |
| port/sourcePort | DST-PORT/SRC-PORT，逗号列表OR | rules/parser.go:50-53 | ruleFieldsOrWithinAndAcross |
| network | NETWORK TCP/UDP，OR组合 | rules/parser.go:70-71 | ruleFieldsOrWithinAndAcross |
| protocols | 显式拒绝，未改bittorrent/destOverride现状 G09 | 无对应PROTOCOL规则；rules/parser.go:17-98 | protocolRuleIsRejectedRatherThanDropped |
| 多字段 | 字段内OR、跨字段AND | rules/parser.go:84-87；rules/logic/logic.go:50-66 | ruleFieldsOrWithinAndAcross |

| 全局输出/接缝输入 | mihomo 消费入口（文件:行） | T |
| --- | --- | --- |
| mode=rule | hub/executor/executor.go:394 | globalAndDirectModes |
| log-level=warning | hub/executor/executor.go:87 | explicitAndroidRouteOwnership |
| find-process-mode=off | hub/executor/executor.go:395 | explicitAndroidRouteOwnership |
| geodata-mode=true | hub/executor/executor.go:420 | geoAndDomainPrefixesMapped |
| tun.enable=false/stack=gvisor/mtu=1500 | config/config.go:1694-1701；wrapper启动才取fd | explicitAndroidRouteOwnership |
| tun.auto-route=false | config/config.go:1698 | explicitAndroidRouteOwnership |
| tun.auto-detect-interface=false | config/config.go:1699 | explicitAndroidRouteOwnership |
| tun.auto-redirect=false | config/config.go:1708 | explicitAndroidRouteOwnership |
| tun.dns-hijack=any:53 | config/config.go:1697 | dnsFakeIpMode |
| tun.inet6-address | config/config.go:1705 | ipv6SwitchControlsDnsAndTun |
| dns.enable=true/enhanced-mode | config/config.go:1416,1433 | dnsFakeIpMode / dnsRealIpStillUsesProxyTcp |
| dns.fake-ip-range/range6 | config/config.go:1488-1508 | ipv6SwitchControlsDnsAndTun |
| dns.nameserver-policy | config/config.go:1339-1410,1446 | dnsDirectDomainPolicyConverted |
| proxies/name/dialer-proxy | config/config.go:parseProxies；adapter/outbound/base.go:209-215 | chainOrderAndDialersPreserved |
| listeners.users/name/listen/port/udp | listener/inbound/base.go:92-99；auth.go:9-31；socks.go:14-22；wrapper拒绝见G10 | authAndUdpListenersIndependent |
| includeInbound/tunnelPort/tunnelAuth（接缝输入） | B.listeners；listener同上 | measurementOmitsListeners / authAndUdpListenersIndependent |

## 4. 原始 Clash / protocolOptions 完整字段消费表

下表为 pin 支持的14协议的全部顶层 option 与可达嵌套对象：每字段均给声明与实际读取。BasicOption在每协议继承；WireGuard继承peer；HTTP/SOCKS ALPN是唯一经批准的零消费例外。原始 options 不因存在声明就自动放行：B.checkGaps 和 S.validateObject 还检查条件门控。动态 map 仅 headers 等内容字典；插件仅已审定 SS v2ray-plugin websocket，其余插件进入G07。所有未知键在S拒绝，原始JSON不改。

### 4.1 vmess

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 B/S |
| --- | --- | --- | --- |
| name | adapter/outbound/vmess.go:55 | adapter/outbound/vmess.go:502 | 父选项启用时消费；具体分支见源码 |
| server | adapter/outbound/vmess.go:56 | adapter/outbound/vmess.go:503 | 父选项启用时消费；具体分支见源码 |
| port | adapter/outbound/vmess.go:57 | adapter/outbound/vmess.go:503 | 父选项启用时消费；具体分支见源码 |
| uuid | adapter/outbound/vmess.go:58 | adapter/outbound/vmess.go:485 | 父选项启用时消费；具体分支见源码 |
| alterId | adapter/outbound/vmess.go:59 | adapter/outbound/vmess.go:485 | 父选项启用时消费；具体分支见源码 |
| cipher | adapter/outbound/vmess.go:60 | adapter/outbound/vmess.go:476 | 父选项启用时消费；具体分支见源码 |
| udp | adapter/outbound/vmess.go:61 | adapter/outbound/vmess.go:506 | 父选项启用时消费；具体分支见源码 |
| network | adapter/outbound/vmess.go:62 | adapter/outbound/vmess.go:175; adapter/outbound/vmess.go:383; adapter/outbound/vmess.go:565; adapter/outbound/vmess.go:572 | 父选项启用时消费；具体分支见源码 |
| tls | adapter/outbound/vmess.go:63 | adapter/outbound/vmess.go:197; adapter/outbound/vmess.go:347; adapter/outbound/vmess.go:562; adapter/outbound/vmess.go:619 | 父选项启用时消费；具体分支见源码 |
| alpn | adapter/outbound/vmess.go:64 | adapter/outbound/vmess.go:363; adapter/outbound/vmess.go:578; adapter/outbound/vmess.go:579 | 依 TLS/network；G02/G03；checkGaps |
| skip-cert-verify | adapter/outbound/vmess.go:65 | adapter/outbound/vmess.go:208; adapter/outbound/vmess.go:232; adapter/outbound/vmess.go:352; adapter/outbound/vmess.go:622 | 依 TLS/network；G02/G03；checkGaps |
| name-cert-verify | adapter/outbound/vmess.go:66 | adapter/outbound/vmess.go:209; adapter/outbound/vmess.go:236; adapter/outbound/vmess.go:353; adapter/outbound/vmess.go:623 | 父选项启用时消费；具体分支见源码 |
| fingerprint | adapter/outbound/vmess.go:67 | adapter/outbound/vmess.go:210; adapter/outbound/vmess.go:235; adapter/outbound/vmess.go:354; adapter/outbound/vmess.go:624 | 父选项启用时消费；具体分支见源码 |
| certificate | adapter/outbound/vmess.go:68 | adapter/outbound/vmess.go:211; adapter/outbound/vmess.go:237; adapter/outbound/vmess.go:355; adapter/outbound/vmess.go:625 | 父选项启用时消费；具体分支见源码 |
| private-key | adapter/outbound/vmess.go:69 | adapter/outbound/vmess.go:212; adapter/outbound/vmess.go:238; adapter/outbound/vmess.go:356; adapter/outbound/vmess.go:626 | 父选项启用时消费；具体分支见源码 |
| servername | adapter/outbound/vmess.go:70 | adapter/outbound/vmess.go:199; adapter/outbound/vmess.go:200; adapter/outbound/vmess.go:372; adapter/outbound/vmess.go:373 | 父选项启用时消费；具体分支见源码 |
| ech-opts | adapter/outbound/vmess.go:71 | adapter/outbound/vmess.go:519 | 父选项启用时消费；具体分支见源码 |
| shadow-tls-opts | adapter/outbound/vmess.go:72 | adapter/outbound/vmess.go:523 | 父选项启用时消费；具体分支见源码 |
| restls-opts | adapter/outbound/vmess.go:73 | adapter/outbound/vmess.go:527 | 父选项启用时消费；具体分支见源码 |
| jls-opts | adapter/outbound/vmess.go:74 | adapter/outbound/vmess.go:531 | 父选项启用时消费；具体分支见源码 |
| reality-opts | adapter/outbound/vmess.go:75 | adapter/outbound/vmess.go:535 | 依 TLS/network；G02/G03；checkGaps |
| tlsmirror-opts | adapter/outbound/vmess.go:76 | adapter/outbound/vmess.go:222; adapter/outbound/vmess.go:364; adapter/outbound/vmess.go:552; adapter/outbound/vmess.go:634 | 父选项启用时消费；具体分支见源码 |
| mekya-opts | adapter/outbound/vmess.go:77 | adapter/outbound/vmess.go:581 | 父选项启用时消费；具体分支见源码 |
| mkcp-opts | adapter/outbound/vmess.go:78 | adapter/outbound/vmess.go:394 | 父选项启用时消费；具体分支见源码 |
| http-opts | adapter/outbound/vmess.go:79 | adapter/outbound/vmess.go:256; adapter/outbound/vmess.go:257; adapter/outbound/vmess.go:258 | 父选项启用时消费；具体分支见源码 |
| h2-opts | adapter/outbound/vmess.go:80 | adapter/outbound/vmess.go:269; adapter/outbound/vmess.go:270; adapter/outbound/vmess.go:574; adapter/outbound/vmess.go:575 | 父选项启用时消费；具体分支见源码 |
| grpc-opts | adapter/outbound/vmess.go:81 | adapter/outbound/vmess.go:610; adapter/outbound/vmess.go:611; adapter/outbound/vmess.go:613; adapter/outbound/vmess.go:647 | 父选项启用时消费；具体分支见源码 |
| ws-opts | adapter/outbound/vmess.go:82 | adapter/outbound/vmess.go:181; adapter/outbound/vmess.go:182; adapter/outbound/vmess.go:183; adapter/outbound/vmess.go:184 | 父选项启用时消费；具体分支见源码 |
| packet-addr | adapter/outbound/vmess.go:83 | adapter/outbound/vmess.go:313; adapter/outbound/vmess.go:492; adapter/outbound/vmess.go:497 | 父选项启用时消费；具体分支见源码 |
| xudp | adapter/outbound/vmess.go:84 | adapter/outbound/vmess.go:299; adapter/outbound/vmess.go:494; adapter/outbound/vmess.go:496; adapter/outbound/vmess.go:507 | 父选项启用时消费；具体分支见源码 |
| packet-encoding | adapter/outbound/vmess.go:85 | adapter/outbound/vmess.go:490 | 父选项启用时消费；具体分支见源码 |
| global-padding | adapter/outbound/vmess.go:86 | adapter/outbound/vmess.go:478 | 父选项启用时消费；具体分支见源码 |
| authenticated-length | adapter/outbound/vmess.go:87 | adapter/outbound/vmess.go:481 | 父选项启用时消费；具体分支见源码 |
| client-fingerprint | adapter/outbound/vmess.go:88 | adapter/outbound/vmess.go:186; adapter/outbound/vmess.go:213; adapter/outbound/vmess.go:357; adapter/outbound/vmess.go:527 | 依 TLS/network；G02/G03；checkGaps |
| tfo | adapter/outbound/base.go:194 | adapter/outbound/vmess.go:508 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| mptcp | adapter/outbound/base.go:195 | adapter/outbound/vmess.go:509 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| interface-name | adapter/outbound/base.go:196 | adapter/outbound/vmess.go:510 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| routing-mark | adapter/outbound/base.go:197 | adapter/outbound/vmess.go:511 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| ip-version | adapter/outbound/base.go:198 | adapter/outbound/vmess.go:512 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| dialer-proxy | adapter/outbound/base.go:199 | adapter/outbound/base.go:209-215；component/proxydialer/byname.go:22-45 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |

### 4.2 vless

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 B/S |
| --- | --- | --- | --- |
| name | adapter/outbound/vless.go:60 | adapter/outbound/vless.go:494 | 父选项启用时消费；具体分支见源码 |
| server | adapter/outbound/vless.go:61 | adapter/outbound/vless.go:495; adapter/outbound/vless.go:620; adapter/outbound/vless.go:725 | 父选项启用时消费；具体分支见源码 |
| port | adapter/outbound/vless.go:62 | adapter/outbound/vless.go:495; adapter/outbound/vless.go:726 | 父选项启用时消费；具体分支见源码 |
| uuid | adapter/outbound/vless.go:63 | adapter/outbound/vless.go:487 | 父选项启用时消费；具体分支见源码 |
| flow | adapter/outbound/vless.go:64 | adapter/outbound/vless.go:464; adapter/outbound/vless.go:465; adapter/outbound/vless.go:466; adapter/outbound/vless.go:467 | 父选项启用时消费；具体分支见源码 |
| tls | adapter/outbound/vless.go:65 | adapter/outbound/vless.go:177; adapter/outbound/vless.go:307; adapter/outbound/vless.go:556; adapter/outbound/vless.go:584 | 父选项启用时消费；具体分支见源码 |
| alpn | adapter/outbound/vless.go:66 | adapter/outbound/vless.go:323; adapter/outbound/vless.go:714; adapter/outbound/vless.go:728 | 依 TLS/network；G02/G03；checkGaps |
| udp | adapter/outbound/vless.go:67 | adapter/outbound/vless.go:498 | 父选项启用时消费；具体分支见源码 |
| packet-addr | adapter/outbound/vless.go:68 | adapter/outbound/vless.go:283; adapter/outbound/vless.go:397; adapter/outbound/vless.go:476; adapter/outbound/vless.go:479 | 父选项启用时消费；具体分支见源码 |
| xudp | adapter/outbound/vless.go:69 | adapter/outbound/vless.go:296; adapter/outbound/vless.go:387; adapter/outbound/vless.go:477; adapter/outbound/vless.go:480 | 父选项启用时消费；具体分支见源码 |
| packet-encoding | adapter/outbound/vless.go:70 | adapter/outbound/vless.go:474 | 父选项启用时消费；具体分支见源码 |
| encryption | adapter/outbound/vless.go:71 | adapter/outbound/vless.go:511 | 父选项启用时消费；具体分支见源码 |
| network | adapter/outbound/vless.go:72 | adapter/outbound/vless.go:156; adapter/outbound/vless.go:341; adapter/outbound/vless.go:560 | 父选项启用时消费；具体分支见源码 |
| ech-opts | adapter/outbound/vless.go:73 | adapter/outbound/vless.go:516 | 父选项启用时消费；具体分支见源码 |
| shadow-tls-opts | adapter/outbound/vless.go:74 | adapter/outbound/vless.go:520 | 父选项启用时消费；具体分支见源码 |
| restls-opts | adapter/outbound/vless.go:75 | adapter/outbound/vless.go:524 | 父选项启用时消费；具体分支见源码 |
| jls-opts | adapter/outbound/vless.go:76 | adapter/outbound/vless.go:528 | 父选项启用时消费；具体分支见源码 |
| reality-opts | adapter/outbound/vless.go:77 | adapter/outbound/vless.go:532 | 依 TLS/network；G02/G03；checkGaps |
| http-opts | adapter/outbound/vless.go:78 | adapter/outbound/vless.go:236; adapter/outbound/vless.go:237; adapter/outbound/vless.go:238 | 父选项启用时消费；具体分支见源码 |
| h2-opts | adapter/outbound/vless.go:79 | adapter/outbound/vless.go:249; adapter/outbound/vless.go:250; adapter/outbound/vless.go:562; adapter/outbound/vless.go:563 | 父选项启用时消费；具体分支见源码 |
| grpc-opts | adapter/outbound/vless.go:80 | adapter/outbound/vless.go:575; adapter/outbound/vless.go:576; adapter/outbound/vless.go:578; adapter/outbound/vless.go:610 | 父选项启用时消费；具体分支见源码 |
| ws-opts | adapter/outbound/vless.go:81 | adapter/outbound/vless.go:162; adapter/outbound/vless.go:163; adapter/outbound/vless.go:164; adapter/outbound/vless.go:165 | 父选项启用时消费；具体分支见源码 |
| xhttp-opts | adapter/outbound/vless.go:82 | adapter/outbound/vless.go:615; adapter/outbound/vless.go:630; adapter/outbound/vless.go:632; adapter/outbound/vless.go:633 | 父选项启用时消费；具体分支见源码 |
| ws-headers | adapter/outbound/vless.go:83 | adapter/outbound/vless.go:172-175 读取嵌套 ws-opts.headers；transport/vmess/websocket.go:396-400 消费 Host | VLESS + WS 且仅 Host 时转换到 ws-opts.headers.Host；其他自定义头显式拒绝；G05 |
| skip-cert-verify | adapter/outbound/vless.go:84 | adapter/outbound/vless.go:188; adapter/outbound/vless.go:207; adapter/outbound/vless.go:312; adapter/outbound/vless.go:587 | 依 TLS/network；G02/G03；checkGaps |
| name-cert-verify | adapter/outbound/vless.go:85 | adapter/outbound/vless.go:189; adapter/outbound/vless.go:211; adapter/outbound/vless.go:313; adapter/outbound/vless.go:588 | 父选项启用时消费；具体分支见源码 |
| fingerprint | adapter/outbound/vless.go:86 | adapter/outbound/vless.go:190; adapter/outbound/vless.go:210; adapter/outbound/vless.go:314; adapter/outbound/vless.go:589 | 父选项启用时消费；具体分支见源码 |
| certificate | adapter/outbound/vless.go:87 | adapter/outbound/vless.go:191; adapter/outbound/vless.go:212; adapter/outbound/vless.go:315; adapter/outbound/vless.go:590 | 父选项启用时消费；具体分支见源码 |
| private-key | adapter/outbound/vless.go:88 | adapter/outbound/vless.go:192; adapter/outbound/vless.go:213; adapter/outbound/vless.go:316; adapter/outbound/vless.go:591 | 父选项启用时消费；具体分支见源码 |
| servername | adapter/outbound/vless.go:89 | adapter/outbound/vless.go:179; adapter/outbound/vless.go:180; adapter/outbound/vless.go:330; adapter/outbound/vless.go:331 | 父选项启用时消费；具体分支见源码 |
| client-fingerprint | adapter/outbound/vless.go:90 | adapter/outbound/vless.go:167; adapter/outbound/vless.go:193; adapter/outbound/vless.go:317; adapter/outbound/vless.go:524 | 依 TLS/network；G02/G03；checkGaps |
| tfo | adapter/outbound/base.go:194 | adapter/outbound/vless.go:500 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| mptcp | adapter/outbound/base.go:195 | adapter/outbound/vless.go:501 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| interface-name | adapter/outbound/base.go:196 | adapter/outbound/vless.go:502 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| routing-mark | adapter/outbound/base.go:197 | adapter/outbound/vless.go:503 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| ip-version | adapter/outbound/base.go:198 | adapter/outbound/vless.go:504 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| dialer-proxy | adapter/outbound/base.go:199 | adapter/outbound/base.go:209-215；component/proxydialer/byname.go:22-45 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |

### 4.3 trojan

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 B/S |
| --- | --- | --- | --- |
| name | adapter/outbound/trojan.go:47 | adapter/outbound/trojan.go:292 | 父选项启用时消费；具体分支见源码 |
| server | adapter/outbound/trojan.go:48 | adapter/outbound/trojan.go:284; adapter/outbound/trojan.go:287 | 父选项启用时消费；具体分支见源码 |
| port | adapter/outbound/trojan.go:49 | adapter/outbound/trojan.go:284 | 父选项启用时消费；具体分支见源码 |
| password | adapter/outbound/trojan.go:50 | adapter/outbound/trojan.go:304 | 父选项启用时消费；具体分支见源码 |
| alpn | adapter/outbound/trojan.go:51 | adapter/outbound/trojan.go:107; adapter/outbound/trojan.go:108; adapter/outbound/trojan.go:154; adapter/outbound/trojan.go:155 | 依 TLS/network；G02/G03；checkGaps |
| sni | adapter/outbound/trojan.go:52 | adapter/outbound/trojan.go:96; adapter/outbound/trojan.go:97; adapter/outbound/trojan.go:113; adapter/outbound/trojan.go:135 | 父选项启用时消费；具体分支见源码 |
| skip-cert-verify | adapter/outbound/trojan.go:53 | adapter/outbound/trojan.go:114; adapter/outbound/trojan.go:134; adapter/outbound/trojan.go:159; adapter/outbound/trojan.go:371 | 依 TLS/network；G02/G03；checkGaps |
| name-cert-verify | adapter/outbound/trojan.go:54 | adapter/outbound/trojan.go:115; adapter/outbound/trojan.go:138; adapter/outbound/trojan.go:160; adapter/outbound/trojan.go:372 | 父选项启用时消费；具体分支见源码 |
| fingerprint | adapter/outbound/trojan.go:55 | adapter/outbound/trojan.go:116; adapter/outbound/trojan.go:137; adapter/outbound/trojan.go:161; adapter/outbound/trojan.go:373 | 父选项启用时消费；具体分支见源码 |
| certificate | adapter/outbound/trojan.go:56 | adapter/outbound/trojan.go:117; adapter/outbound/trojan.go:139; adapter/outbound/trojan.go:162; adapter/outbound/trojan.go:374 | 父选项启用时消费；具体分支见源码 |
| private-key | adapter/outbound/trojan.go:57 | adapter/outbound/trojan.go:118; adapter/outbound/trojan.go:140; adapter/outbound/trojan.go:163; adapter/outbound/trojan.go:375 | 父选项启用时消费；具体分支见源码 |
| udp | adapter/outbound/trojan.go:58 | adapter/outbound/trojan.go:296 | 父选项启用时消费；具体分支见源码 |
| network | adapter/outbound/trojan.go:59 | adapter/outbound/trojan.go:79; adapter/outbound/trojan.go:211; adapter/outbound/trojan.go:360 | 父选项启用时消费；具体分支见源码 |
| ech-opts | adapter/outbound/trojan.go:60 | adapter/outbound/trojan.go:309 | 父选项启用时消费；具体分支见源码 |
| shadow-tls-opts | adapter/outbound/trojan.go:61 | adapter/outbound/trojan.go:313 | 父选项启用时消费；具体分支见源码 |
| restls-opts | adapter/outbound/trojan.go:62 | adapter/outbound/trojan.go:317 | 父选项启用时消费；具体分支见源码 |
| jls-opts | adapter/outbound/trojan.go:63 | adapter/outbound/trojan.go:321 | 父选项启用时消费；具体分支见源码 |
| reality-opts | adapter/outbound/trojan.go:64 | adapter/outbound/trojan.go:325 | 依 TLS/network；G02/G03；checkGaps |
| grpc-opts | adapter/outbound/trojan.go:65 | adapter/outbound/trojan.go:386; adapter/outbound/trojan.go:387; adapter/outbound/trojan.go:389; adapter/outbound/trojan.go:396 | 父选项启用时消费；具体分支见源码 |
| ws-opts | adapter/outbound/trojan.go:66 | adapter/outbound/trojan.go:86; adapter/outbound/trojan.go:87; adapter/outbound/trojan.go:88; adapter/outbound/trojan.go:89 | 父选项启用时消费；具体分支见源码 |
| ss-opts | adapter/outbound/trojan.go:67 | adapter/outbound/trojan.go:346; adapter/outbound/trojan.go:347; adapter/outbound/trojan.go:350; adapter/outbound/trojan.go:351 | 父选项启用时消费；具体分支见源码 |
| client-fingerprint | adapter/outbound/trojan.go:68 | adapter/outbound/trojan.go:91; adapter/outbound/trojan.go:119; adapter/outbound/trojan.go:164; adapter/outbound/trojan.go:317 | 依 TLS/network；G02/G03；checkGaps |
| tfo | adapter/outbound/base.go:194 | adapter/outbound/trojan.go:297 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| mptcp | adapter/outbound/base.go:195 | adapter/outbound/trojan.go:298 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| interface-name | adapter/outbound/base.go:196 | adapter/outbound/trojan.go:299 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| routing-mark | adapter/outbound/base.go:197 | adapter/outbound/trojan.go:300 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| ip-version | adapter/outbound/base.go:198 | adapter/outbound/trojan.go:301 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| dialer-proxy | adapter/outbound/base.go:199 | adapter/outbound/base.go:209-215；component/proxydialer/byname.go:22-45 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |

### 4.4 ss

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 B/S |
| --- | --- | --- | --- |
| name | adapter/outbound/shadowsocks.go:45 | adapter/outbound/shadowsocks.go:500 | 父选项启用时消费；具体分支见源码 |
| server | adapter/outbound/shadowsocks.go:46 | adapter/outbound/shadowsocks.go:302 | 父选项启用时消费；具体分支见源码 |
| port | adapter/outbound/shadowsocks.go:47 | adapter/outbound/shadowsocks.go:302 | 父选项启用时消费；具体分支见源码 |
| password | adapter/outbound/shadowsocks.go:48 | adapter/outbound/shadowsocks.go:304 | 父选项启用时消费；具体分支见源码 |
| cipher | adapter/outbound/shadowsocks.go:49 | adapter/outbound/shadowsocks.go:303; adapter/outbound/shadowsocks.go:308 | 父选项启用时消费；具体分支见源码 |
| udp | adapter/outbound/shadowsocks.go:50 | adapter/outbound/shadowsocks.go:504 | 父选项启用时消费；具体分支见源码 |
| plugin | adapter/outbound/shadowsocks.go:51 | adapter/outbound/shadowsocks.go:321; adapter/outbound/shadowsocks.go:332; adapter/outbound/shadowsocks.go:365; adapter/outbound/shadowsocks.go:396 | 父选项启用时消费；具体分支见源码 |
| plugin-opts | adapter/outbound/shadowsocks.go:52 | adapter/outbound/shadowsocks.go:323; adapter/outbound/shadowsocks.go:334; adapter/outbound/shadowsocks.go:367; adapter/outbound/shadowsocks.go:401 | 父选项启用时消费；具体分支见源码 |
| udp-over-tcp | adapter/outbound/shadowsocks.go:53 | adapter/outbound/shadowsocks.go:199; adapter/outbound/shadowsocks.go:251; adapter/outbound/shadowsocks.go:291; adapter/outbound/shadowsocks.go:488 | 父选项启用时消费；具体分支见源码 |
| udp-over-tcp-version | adapter/outbound/shadowsocks.go:54 | adapter/outbound/shadowsocks.go:200; adapter/outbound/shadowsocks.go:264; adapter/outbound/shadowsocks.go:490; adapter/outbound/shadowsocks.go:493 | 父选项启用时消费；具体分支见源码 |
| client-fingerprint | adapter/outbound/shadowsocks.go:55 | adapter/outbound/shadowsocks.go:411; adapter/outbound/shadowsocks.go:429; adapter/outbound/shadowsocks.go:453 | 父选项启用时消费；具体分支见源码 |
| tfo | adapter/outbound/base.go:194 | adapter/outbound/shadowsocks.go:505 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| mptcp | adapter/outbound/base.go:195 | adapter/outbound/shadowsocks.go:506 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| interface-name | adapter/outbound/base.go:196 | adapter/outbound/shadowsocks.go:507 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| routing-mark | adapter/outbound/base.go:197 | adapter/outbound/shadowsocks.go:508 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| ip-version | adapter/outbound/base.go:198 | adapter/outbound/shadowsocks.go:509 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| dialer-proxy | adapter/outbound/base.go:199 | adapter/outbound/base.go:209-215；component/proxydialer/byname.go:22-45 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |

### 4.5 hysteria

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 B/S |
| --- | --- | --- | --- |
| name | adapter/outbound/hysteria.go:103 | adapter/outbound/hysteria.go:246 | 父选项启用时消费；具体分支见源码 |
| server | adapter/outbound/hysteria.go:104 | adapter/outbound/hysteria.go:148; adapter/outbound/hysteria.go:151 | 父选项启用时消费；具体分支见源码 |
| port | adapter/outbound/hysteria.go:105 | adapter/outbound/hysteria.go:148 | 父选项启用时消费；具体分支见源码 |
| ports | adapter/outbound/hysteria.go:106 | adapter/outbound/hysteria.go:149 | 父选项启用时消费；具体分支见源码 |
| protocol | adapter/outbound/hysteria.go:107 | adapter/outbound/hysteria.go:193; adapter/outbound/hysteria.go:195; adapter/outbound/hysteria.go:196; adapter/outbound/hysteria.go:237 | 父选项启用时消费；具体分支见源码 |
| obfs-protocol | adapter/outbound/hysteria.go:108 | adapter/outbound/hysteria.go:192; adapter/outbound/hysteria.go:193 | 父选项启用时消费；具体分支见源码 |
| up | adapter/outbound/hysteria.go:109 | adapter/outbound/hysteria.go:133; adapter/outbound/hysteria.go:135 | 父选项启用时消费；具体分支见源码 |
| up-speed | adapter/outbound/hysteria.go:110 | adapter/outbound/hysteria.go:230; adapter/outbound/hysteria.go:231 | 父选项启用时消费；具体分支见源码 |
| down | adapter/outbound/hysteria.go:111 | adapter/outbound/hysteria.go:138; adapter/outbound/hysteria.go:140 | 父选项启用时消费；具体分支见源码 |
| down-speed | adapter/outbound/hysteria.go:112 | adapter/outbound/hysteria.go:233; adapter/outbound/hysteria.go:234 | 父选项启用时消费；具体分支见源码 |
| auth | adapter/outbound/hysteria.go:113 | adapter/outbound/hysteria.go:215; adapter/outbound/hysteria.go:216 | 父选项启用时消费；具体分支见源码 |
| auth-str | adapter/outbound/hysteria.go:114 | adapter/outbound/hysteria.go:214 | 父选项启用时消费；具体分支见源码 |
| obfs | adapter/outbound/hysteria.go:115 | adapter/outbound/hysteria.go:222; adapter/outbound/hysteria.go:223 | 父选项启用时消费；具体分支见源码 |
| sni | adapter/outbound/hysteria.go:116 | adapter/outbound/hysteria.go:152; adapter/outbound/hysteria.go:153 | 父选项启用时消费；具体分支见源码 |
| ech-opts | adapter/outbound/hysteria.go:117 | adapter/outbound/hysteria.go:177 | 父选项启用时消费；具体分支见源码 |
| skip-cert-verify | adapter/outbound/hysteria.go:118 | adapter/outbound/hysteria.go:159 | 父选项启用时消费；具体分支见源码 |
| name-cert-verify | adapter/outbound/hysteria.go:119 | adapter/outbound/hysteria.go:163 | 父选项启用时消费；具体分支见源码 |
| fingerprint | adapter/outbound/hysteria.go:120 | adapter/outbound/hysteria.go:162 | 父选项启用时消费；具体分支见源码 |
| certificate | adapter/outbound/hysteria.go:121 | adapter/outbound/hysteria.go:164 | 父选项启用时消费；具体分支见源码 |
| private-key | adapter/outbound/hysteria.go:122 | adapter/outbound/hysteria.go:165 | 父选项启用时消费；具体分支见源码 |
| alpn | adapter/outbound/hysteria.go:123 | adapter/outbound/hysteria.go:171; adapter/outbound/hysteria.go:172 | 父选项启用时消费；具体分支见源码 |
| recv-window-conn | adapter/outbound/hysteria.go:124 | adapter/outbound/hysteria.go:184; adapter/outbound/hysteria.go:185 | 父选项启用时消费；具体分支见源码 |
| recv-window | adapter/outbound/hysteria.go:125 | adapter/outbound/hysteria.go:186; adapter/outbound/hysteria.go:187; adapter/outbound/hysteria.go:202; adapter/outbound/hysteria.go:206 | 父选项启用时消费；具体分支见源码 |
| disable-mtu-discovery | adapter/outbound/hysteria.go:126 | adapter/outbound/hysteria.go:189 | 父选项启用时消费；具体分支见源码 |
| fast-open | adapter/outbound/hysteria.go:127 | adapter/outbound/hysteria.go:239; adapter/outbound/hysteria.go:251 | 父选项启用时消费；具体分支见源码 |
| hop-interval | adapter/outbound/hysteria.go:128 | adapter/outbound/hysteria.go:198; adapter/outbound/hysteria.go:199; adapter/outbound/hysteria.go:201 | 父选项启用时消费；具体分支见源码 |
| tfo | adapter/outbound/base.go:194 | 无（G06：该协议未转发此BasicOption；非其他协议同名字段） | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| mptcp | adapter/outbound/base.go:195 | 无（G06：该协议未转发此BasicOption；非其他协议同名字段） | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| interface-name | adapter/outbound/base.go:196 | adapter/outbound/hysteria.go:252 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| routing-mark | adapter/outbound/base.go:197 | adapter/outbound/hysteria.go:253 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| ip-version | adapter/outbound/base.go:198 | adapter/outbound/hysteria.go:254 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| dialer-proxy | adapter/outbound/base.go:199 | adapter/outbound/base.go:209-215；component/proxydialer/byname.go:22-45 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |

### 4.6 hysteria2

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 B/S |
| --- | --- | --- | --- |
| name | adapter/outbound/hysteria2.go:41 | adapter/outbound/hysteria2.go:133 | 父选项启用时消费；具体分支见源码 |
| server | adapter/outbound/hysteria2.go:42 | adapter/outbound/hysteria2.go:130; adapter/outbound/hysteria2.go:165 | 父选项启用时消费；具体分支见源码 |
| port | adapter/outbound/hysteria2.go:43 | adapter/outbound/hysteria2.go:130; adapter/outbound/hysteria2.go:266 | 父选项启用时消费；具体分支见源码 |
| ports | adapter/outbound/hysteria2.go:44 | adapter/outbound/hysteria2.go:238; adapter/outbound/hysteria2.go:239 | 父选项启用时消费；具体分支见源码 |
| hop-interval | adapter/outbound/hysteria2.go:45 | adapter/outbound/hysteria2.go:248 | 父选项启用时消费；具体分支见源码 |
| up | adapter/outbound/hysteria2.go:46 | adapter/outbound/hysteria2.go:211 | 父选项启用时消费；具体分支见源码 |
| down | adapter/outbound/hysteria2.go:47 | adapter/outbound/hysteria2.go:212 | 父选项启用时消费；具体分支见源码 |
| password | adapter/outbound/hysteria2.go:48 | adapter/outbound/hysteria2.go:217 | 父选项启用时消费；具体分支见源码 |
| obfs | adapter/outbound/hysteria2.go:49 | adapter/outbound/hysteria2.go:149; adapter/outbound/hysteria2.go:153; adapter/outbound/hysteria2.go:161 | 父选项启用时消费；具体分支见源码 |
| obfs-password | adapter/outbound/hysteria2.go:50 | adapter/outbound/hysteria2.go:150; adapter/outbound/hysteria2.go:155; adapter/outbound/hysteria2.go:157 | 父选项启用时消费；具体分支见源码 |
| obfs-min-packet-size | adapter/outbound/hysteria2.go:51 | adapter/outbound/hysteria2.go:158 | 父选项启用时消费；具体分支见源码 |
| obfs-max-packet-size | adapter/outbound/hysteria2.go:52 | adapter/outbound/hysteria2.go:159 | 父选项启用时消费；具体分支见源码 |
| sni | adapter/outbound/hysteria2.go:53 | adapter/outbound/hysteria2.go:166; adapter/outbound/hysteria2.go:167 | 父选项启用时消费；具体分支见源码 |
| ech-opts | adapter/outbound/hysteria2.go:54 | adapter/outbound/hysteria2.go:190 | 父选项启用时消费；具体分支见源码 |
| skip-cert-verify | adapter/outbound/hysteria2.go:55 | adapter/outbound/hysteria2.go:173 | 父选项启用时消费；具体分支见源码 |
| name-cert-verify | adapter/outbound/hysteria2.go:56 | adapter/outbound/hysteria2.go:177 | 父选项启用时消费；具体分支见源码 |
| fingerprint | adapter/outbound/hysteria2.go:57 | adapter/outbound/hysteria2.go:176 | 父选项启用时消费；具体分支见源码 |
| certificate | adapter/outbound/hysteria2.go:58 | adapter/outbound/hysteria2.go:178 | 父选项启用时消费；具体分支见源码 |
| private-key | adapter/outbound/hysteria2.go:59 | adapter/outbound/hysteria2.go:179 | 父选项启用时消费；具体分支见源码 |
| alpn | adapter/outbound/hysteria2.go:60 | adapter/outbound/hysteria2.go:185; adapter/outbound/hysteria2.go:186 | 父选项启用时消费；具体分支见源码 |
| cwnd | adapter/outbound/hysteria2.go:61 | adapter/outbound/hysteria2.go:232 | 父选项启用时消费；具体分支见源码 |
| bbr-profile | adapter/outbound/hysteria2.go:62 | adapter/outbound/hysteria2.go:232 | 父选项启用时消费；具体分支见源码 |
| udp-mtu | adapter/outbound/hysteria2.go:63 | adapter/outbound/hysteria2.go:195; adapter/outbound/hysteria2.go:198; adapter/outbound/hysteria2.go:221 | 父选项启用时消费；具体分支见源码 |
| handshake-timeout | adapter/outbound/hysteria2.go:64 | adapter/outbound/hysteria2.go:234 | 父选项启用时消费；具体分支见源码 |
| realm-opts | adapter/outbound/hysteria2.go:66 | adapter/outbound/hysteria2.go:270; adapter/outbound/hysteria2.go:273; adapter/outbound/hysteria2.go:274; adapter/outbound/hysteria2.go:275 | 父选项启用时消费；具体分支见源码 |
| initial-stream-receive-window | adapter/outbound/hysteria2.go:69 | adapter/outbound/hysteria2.go:202 | 父选项启用时消费；具体分支见源码 |
| max-stream-receive-window | adapter/outbound/hysteria2.go:70 | adapter/outbound/hysteria2.go:203 | 父选项启用时消费；具体分支见源码 |
| initial-connection-receive-window | adapter/outbound/hysteria2.go:71 | adapter/outbound/hysteria2.go:204 | 父选项启用时消费；具体分支见源码 |
| max-connection-receive-window | adapter/outbound/hysteria2.go:72 | adapter/outbound/hysteria2.go:205 | 父选项启用时消费；具体分支见源码 |
| tfo | adapter/outbound/base.go:194 | 无（G06：该协议未转发此BasicOption；非其他协议同名字段） | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| mptcp | adapter/outbound/base.go:195 | 无（G06：该协议未转发此BasicOption；非其他协议同名字段） | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| interface-name | adapter/outbound/base.go:196 | adapter/outbound/hysteria2.go:138 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| routing-mark | adapter/outbound/base.go:197 | adapter/outbound/hysteria2.go:139 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| ip-version | adapter/outbound/base.go:198 | adapter/outbound/hysteria2.go:140 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| dialer-proxy | adapter/outbound/base.go:199 | adapter/outbound/base.go:209-215；component/proxydialer/byname.go:22-45 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |

### 4.7 tuic

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 B/S |
| --- | --- | --- | --- |
| name | adapter/outbound/tuic.go:36 | adapter/outbound/tuic.go:243 | 父选项启用时消费；具体分支见源码 |
| server | adapter/outbound/tuic.go:37 | adapter/outbound/tuic.go:130; adapter/outbound/tuic.go:131 | 父选项启用时消费；具体分支见源码 |
| port | adapter/outbound/tuic.go:38 | adapter/outbound/tuic.go:130; adapter/outbound/tuic.go:221 | 父选项启用时消费；具体分支见源码 |
| token | adapter/outbound/tuic.go:39 | adapter/outbound/tuic.go:183; adapter/outbound/tuic.go:271; adapter/outbound/tuic.go:272 | v4/v5分支；G06 |
| uuid | adapter/outbound/tuic.go:40 | adapter/outbound/tuic.go:289 | v4/v5分支；G06 |
| password | adapter/outbound/tuic.go:41 | adapter/outbound/tuic.go:290 | v4/v5分支；G06 |
| ip | adapter/outbound/tuic.go:42 | adapter/outbound/tuic.go:220; adapter/outbound/tuic.go:221 | 父选项启用时消费；具体分支见源码 |
| heartbeat-interval | adapter/outbound/tuic.go:43 | adapter/outbound/tuic.go:161; adapter/outbound/tuic.go:162; adapter/outbound/tuic.go:206 | 父选项启用时消费；具体分支见源码 |
| alpn | adapter/outbound/tuic.go:44 | adapter/outbound/tuic.go:151; adapter/outbound/tuic.go:152 | 父选项启用时消费；具体分支见源码 |
| reduce-rtt | adapter/outbound/tuic.go:45 | adapter/outbound/tuic.go:114 | 父选项启用时消费；具体分支见源码 |
| request-timeout | adapter/outbound/tuic.go:46 | adapter/outbound/tuic.go:157; adapter/outbound/tuic.go:158; adapter/outbound/tuic.go:276 | v4/v5分支；G06 |
| udp-relay-mode | adapter/outbound/tuic.go:47 | adapter/outbound/tuic.go:166 | 父选项启用时消费；具体分支见源码 |
| congestion-controller | adapter/outbound/tuic.go:48 | adapter/outbound/tuic.go:118 | 父选项启用时消费；具体分支见源码 |
| disable-sni | adapter/outbound/tuic.go:49 | adapter/outbound/tuic.go:223 | 父选项启用时消费；具体分支见源码 |
| max-udp-relay-packet-size | adapter/outbound/tuic.go:50 | adapter/outbound/tuic.go:170; adapter/outbound/tuic.go:171; adapter/outbound/tuic.go:188; adapter/outbound/tuic.go:194 | 父选项启用时消费；具体分支见源码 |
| fast-open | adapter/outbound/tuic.go:52 | adapter/outbound/tuic.go:248; adapter/outbound/tuic.go:278 | v4/v5分支；G06 |
| max-open-streams | adapter/outbound/tuic.go:53 | adapter/outbound/tuic.go:174; adapter/outbound/tuic.go:175; adapter/outbound/tuic.go:197; adapter/outbound/tuic.go:260 | 父选项启用时消费；具体分支见源码 |
| cwnd | adapter/outbound/tuic.go:54 | adapter/outbound/tuic.go:118; adapter/outbound/tuic.go:178; adapter/outbound/tuic.go:179 | 父选项启用时消费；具体分支见源码 |
| bbr-profile | adapter/outbound/tuic.go:55 | adapter/outbound/tuic.go:118 | 父选项启用时消费；具体分支见源码 |
| skip-cert-verify | adapter/outbound/tuic.go:56 | adapter/outbound/tuic.go:139 | 父选项启用时消费；具体分支见源码 |
| name-cert-verify | adapter/outbound/tuic.go:57 | adapter/outbound/tuic.go:143 | 父选项启用时消费；具体分支见源码 |
| fingerprint | adapter/outbound/tuic.go:58 | adapter/outbound/tuic.go:142 | 父选项启用时消费；具体分支见源码 |
| certificate | adapter/outbound/tuic.go:59 | adapter/outbound/tuic.go:144 | 父选项启用时消费；具体分支见源码 |
| private-key | adapter/outbound/tuic.go:60 | adapter/outbound/tuic.go:145 | 父选项启用时消费；具体分支见源码 |
| recv-window-conn | adapter/outbound/tuic.go:61 | adapter/outbound/tuic.go:200; adapter/outbound/tuic.go:201; adapter/outbound/tuic.go:211 | 父选项启用时消费；具体分支见源码 |
| recv-window | adapter/outbound/tuic.go:62 | adapter/outbound/tuic.go:202; adapter/outbound/tuic.go:203; adapter/outbound/tuic.go:215 | 父选项启用时消费；具体分支见源码 |
| disable-mtu-discovery | adapter/outbound/tuic.go:63 | adapter/outbound/tuic.go:207 | 父选项启用时消费；具体分支见源码 |
| max-datagram-frame-size | adapter/outbound/tuic.go:64 | adapter/outbound/tuic.go:187; adapter/outbound/tuic.go:188; adapter/outbound/tuic.go:191; adapter/outbound/tuic.go:192 | 父选项启用时消费；具体分支见源码 |
| sni | adapter/outbound/tuic.go:65 | adapter/outbound/tuic.go:132; adapter/outbound/tuic.go:133 | 父选项启用时消费；具体分支见源码 |
| ech-opts | adapter/outbound/tuic.go:66 | adapter/outbound/tuic.go:228 | 父选项启用时消费；具体分支见源码 |
| udp-over-stream | adapter/outbound/tuic.go:68 | adapter/outbound/tuic.go:87 | 父选项启用时消费；具体分支见源码 |
| udp-over-stream-version | adapter/outbound/tuic.go:69 | adapter/outbound/tuic.go:88; adapter/outbound/tuic.go:100; adapter/outbound/tuic.go:233; adapter/outbound/tuic.go:236 | 父选项启用时消费；具体分支见源码 |
| tfo | adapter/outbound/base.go:194 | 无（G06：该协议未转发此BasicOption；非其他协议同名字段） | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| mptcp | adapter/outbound/base.go:195 | 无（G06：该协议未转发此BasicOption；非其他协议同名字段） | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| interface-name | adapter/outbound/base.go:196 | adapter/outbound/tuic.go:249 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| routing-mark | adapter/outbound/base.go:197 | adapter/outbound/tuic.go:250 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| ip-version | adapter/outbound/base.go:198 | adapter/outbound/tuic.go:251 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| dialer-proxy | adapter/outbound/base.go:199 | adapter/outbound/base.go:209-215；component/proxydialer/byname.go:22-45 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |

### 4.8 wireguard

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 B/S |
| --- | --- | --- | --- |
| name | adapter/outbound/wireguard.go:72 | adapter/outbound/wireguard.go:354; adapter/outbound/wireguard.go:479; adapter/outbound/wireguard.go:482; adapter/outbound/wireguard.go:567 | peer数量与地址/allowedIPs条件；G05/G06 |
| ip | adapter/outbound/wireguard.go:73 | adapter/outbound/wireguard.go:325; adapter/outbound/wireguard.go:326; adapter/outbound/wireguard.go:327; adapter/outbound/wireguard.go:329 | peer数量与地址/allowedIPs条件；G05/G06 |
| ipv6 | adapter/outbound/wireguard.go:74 | adapter/outbound/wireguard.go:335; adapter/outbound/wireguard.go:336; adapter/outbound/wireguard.go:337; adapter/outbound/wireguard.go:339 | peer数量与地址/allowedIPs条件；G05/G06 |
| private-key | adapter/outbound/wireguard.go:75 | adapter/outbound/wireguard.go:396; adapter/outbound/wireguard.go:400; adapter/outbound/wireguard.go:608 | peer数量与地址/allowedIPs条件；G05/G06 |
| workers | adapter/outbound/wireguard.go:76 | adapter/outbound/wireguard.go:488; adapter/outbound/wireguard.go:490; adapter/outbound/wireguard.go:493 | peer数量与地址/allowedIPs条件；G05/G06 |
| mtu | adapter/outbound/wireguard.go:77 | adapter/outbound/wireguard.go:455 | peer数量与地址/allowedIPs条件；G05/G06 |
| udp | adapter/outbound/wireguard.go:78 | adapter/outbound/wireguard.go:358 | peer数量与地址/allowedIPs条件；G05/G06 |
| persistent-keepalive | adapter/outbound/wireguard.go:79 | adapter/outbound/wireguard.go:734; adapter/outbound/wireguard.go:735; adapter/outbound/wireguard.go:775; adapter/outbound/wireguard.go:776 | peer数量与地址/allowedIPs条件；G05/G06 |
| ip-stack | adapter/outbound/wireguard.go:81 | adapter/outbound/wireguard.go:459; adapter/outbound/wireguard.go:460; adapter/outbound/wireguard.go:467 | peer数量与地址/allowedIPs条件；G05/G06 |
| amnezia-wg-option | adapter/outbound/wireguard.go:83 | adapter/outbound/wireguard.go:446; adapter/outbound/wireguard.go:447; adapter/outbound/wireguard.go:451; adapter/outbound/wireguard.go:485 | peer数量与地址/allowedIPs条件；G05/G06 |
| peers | adapter/outbound/wireguard.go:85 | adapter/outbound/wireguard.go:375; adapter/outbound/wireguard.go:377; adapter/outbound/wireguard.go:378; adapter/outbound/wireguard.go:403 | peer数量与地址/allowedIPs条件；G05/G06 |
| remote-dns-resolve | adapter/outbound/wireguard.go:87 | adapter/outbound/wireguard.go:504 | peer数量与地址/allowedIPs条件；G05/G06 |
| dns | adapter/outbound/wireguard.go:88 | adapter/outbound/wireguard.go:504; adapter/outbound/wireguard.go:505 | peer数量与地址/allowedIPs条件；G05/G06 |
| refresh-server-ip-interval | adapter/outbound/wireguard.go:90 | adapter/outbound/wireguard.go:587 | peer数量与地址/allowedIPs条件；G05/G06 |
| tfo | adapter/outbound/base.go:194 | 无（G06：该协议未转发此BasicOption；非其他协议同名字段） | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| mptcp | adapter/outbound/base.go:195 | 无（G06：该协议未转发此BasicOption；非其他协议同名字段） | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| interface-name | adapter/outbound/base.go:196 | adapter/outbound/wireguard.go:359 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| routing-mark | adapter/outbound/base.go:197 | adapter/outbound/wireguard.go:360 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| ip-version | adapter/outbound/base.go:198 | adapter/outbound/wireguard.go:361 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| dialer-proxy | adapter/outbound/base.go:199 | adapter/outbound/base.go:209-215；component/proxydialer/byname.go:22-45 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| server | adapter/outbound/wireguard.go:94 | adapter/outbound/wireguard.go:320 | 顶层peer字段；allowed-ips只在peers对象消费，顶层拒绝；reserved真实消费于:367-383 |
| port | adapter/outbound/wireguard.go:95 | adapter/outbound/wireguard.go:320 | 顶层peer字段；allowed-ips只在peers对象消费，顶层拒绝；reserved真实消费于:367-383 |
| public-key | adapter/outbound/wireguard.go:96 | adapter/outbound/wireguard.go:406; adapter/outbound/wireguard.go:410; adapter/outbound/wireguard.go:715 | 顶层peer字段；allowed-ips只在peers对象消费，顶层拒绝；reserved真实消费于:367-383 |
| pre-shared-key | adapter/outbound/wireguard.go:97 | adapter/outbound/wireguard.go:412; adapter/outbound/wireguard.go:413; adapter/outbound/wireguard.go:417; adapter/outbound/wireguard.go:728 | 顶层peer字段；allowed-ips只在peers对象消费，顶层拒绝；reserved真实消费于:367-383 |
| reserved | adapter/outbound/wireguard.go:98 | adapter/outbound/wireguard.go:424; adapter/outbound/wireguard.go:425; adapter/outbound/wireguard.go:426; adapter/outbound/wireguard.go:720 | 顶层peer字段；allowed-ips只在peers对象消费，顶层拒绝；reserved真实消费于:367-383 |
| allowed-ips | adapter/outbound/wireguard.go:99 | adapter/outbound/wireguard.go:420; adapter/outbound/wireguard.go:731 | 顶层peer字段；allowed-ips只在peers对象消费，顶层拒绝；reserved真实消费于:367-383 |

### 4.9 ssr

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 B/S |
| --- | --- | --- | --- |
| name | adapter/outbound/shadowsocksr.go:30 | adapter/outbound/shadowsocksr.go:162 | 父选项启用时消费；具体分支见源码 |
| server | adapter/outbound/shadowsocksr.go:31 | adapter/outbound/shadowsocksr.go:116; adapter/outbound/shadowsocksr.go:141 | 父选项启用时消费；具体分支见源码 |
| port | adapter/outbound/shadowsocksr.go:32 | adapter/outbound/shadowsocksr.go:116; adapter/outbound/shadowsocksr.go:142 | 父选项启用时消费；具体分支见源码 |
| password | adapter/outbound/shadowsocksr.go:33 | adapter/outbound/shadowsocksr.go:118; adapter/outbound/shadowsocksr.go:130 | 父选项启用时消费；具体分支见源码 |
| cipher | adapter/outbound/shadowsocksr.go:34 | adapter/outbound/shadowsocksr.go:112; adapter/outbound/shadowsocksr.go:113; adapter/outbound/shadowsocksr.go:117; adapter/outbound/shadowsocksr.go:128 | 父选项启用时消费；具体分支见源码 |
| obfs | adapter/outbound/shadowsocksr.go:35 | adapter/outbound/shadowsocksr.go:140 | 父选项启用时消费；具体分支见源码 |
| obfs-param | adapter/outbound/shadowsocksr.go:36 | adapter/outbound/shadowsocksr.go:145 | 父选项启用时消费；具体分支见源码 |
| protocol | adapter/outbound/shadowsocksr.go:37 | adapter/outbound/shadowsocksr.go:151 | 父选项启用时消费；具体分支见源码 |
| protocol-param | adapter/outbound/shadowsocksr.go:38 | adapter/outbound/shadowsocksr.go:154 | 父选项启用时消费；具体分支见源码 |
| udp | adapter/outbound/shadowsocksr.go:39 | adapter/outbound/shadowsocksr.go:166 | 父选项启用时消费；具体分支见源码 |
| tfo | adapter/outbound/base.go:194 | adapter/outbound/shadowsocksr.go:167 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| mptcp | adapter/outbound/base.go:195 | adapter/outbound/shadowsocksr.go:168 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| interface-name | adapter/outbound/base.go:196 | adapter/outbound/shadowsocksr.go:169 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| routing-mark | adapter/outbound/base.go:197 | adapter/outbound/shadowsocksr.go:170 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| ip-version | adapter/outbound/base.go:198 | adapter/outbound/shadowsocksr.go:171 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| dialer-proxy | adapter/outbound/base.go:199 | adapter/outbound/base.go:209-215；component/proxydialer/byname.go:22-45 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |

### 4.10 http

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 B/S |
| --- | --- | --- | --- |
| name | adapter/outbound/http.go:30 | adapter/outbound/http.go:173 | 父选项启用时消费；具体分支见源码 |
| server | adapter/outbound/http.go:31 | adapter/outbound/http.go:151; adapter/outbound/http.go:174 | 父选项启用时消费；具体分支见源码 |
| port | adapter/outbound/http.go:32 | adapter/outbound/http.go:174 | 父选项启用时消费；具体分支见源码 |
| username | adapter/outbound/http.go:33 | adapter/outbound/http.go:183 | 父选项启用时消费；具体分支见源码 |
| password | adapter/outbound/http.go:34 | adapter/outbound/http.go:184 | 父选项启用时消费；具体分支见源码 |
| tls | adapter/outbound/http.go:35 | adapter/outbound/http.go:150 | 父选项启用时消费；具体分支见源码 |
| sni | adapter/outbound/http.go:36 | adapter/outbound/http.go:152; adapter/outbound/http.go:153 | 父选项启用时消费；具体分支见源码 |
| skip-cert-verify | adapter/outbound/http.go:37 | adapter/outbound/http.go:158 | 父选项启用时消费；具体分支见源码 |
| name-cert-verify | adapter/outbound/http.go:38 | adapter/outbound/http.go:162 | 父选项启用时消费；具体分支见源码 |
| fingerprint | adapter/outbound/http.go:39 | adapter/outbound/http.go:161 | 父选项启用时消费；具体分支见源码 |
| certificate | adapter/outbound/http.go:40 | adapter/outbound/http.go:163 | 父选项启用时消费；具体分支见源码 |
| private-key | adapter/outbound/http.go:41 | adapter/outbound/http.go:164 | 父选项启用时消费；具体分支见源码 |
| headers | adapter/outbound/http.go:42 | adapter/outbound/http.go:102 | 父选项启用时消费；具体分支见源码 |
| tfo | adapter/outbound/base.go:194 | adapter/outbound/http.go:177 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| mptcp | adapter/outbound/base.go:195 | adapter/outbound/http.go:178 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| interface-name | adapter/outbound/base.go:196 | adapter/outbound/http.go:179 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| routing-mark | adapter/outbound/base.go:197 | adapter/outbound/http.go:180 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| ip-version | adapter/outbound/base.go:198 | adapter/outbound/http.go:181 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| dialer-proxy | adapter/outbound/base.go:199 | adapter/outbound/base.go:209-215；component/proxydialer/byname.go:22-45 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| alpn | 无（模型与ExtendedConfigBuilder输出） | 无（双核既有问题，见登记） | 已批准输出；FEATURE_MAP 6.10 |

### 4.11 socks5

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 B/S |
| --- | --- | --- | --- |
| name | adapter/outbound/socks5.go:32 | adapter/outbound/socks5.go:194 | 父选项启用时消费；具体分支见源码 |
| server | adapter/outbound/socks5.go:33 | adapter/outbound/socks5.go:180; adapter/outbound/socks5.go:195 | 父选项启用时消费；具体分支见源码 |
| port | adapter/outbound/socks5.go:34 | adapter/outbound/socks5.go:195 | 父选项启用时消费；具体分支见源码 |
| username | adapter/outbound/socks5.go:35 | adapter/outbound/socks5.go:206 | 父选项启用时消费；具体分支见源码 |
| password | adapter/outbound/socks5.go:36 | adapter/outbound/socks5.go:207 | 父选项启用时消费；具体分支见源码 |
| tls | adapter/outbound/socks5.go:37 | adapter/outbound/socks5.go:175; adapter/outbound/socks5.go:208 | 父选项启用时消费；具体分支见源码 |
| udp | adapter/outbound/socks5.go:38 | adapter/outbound/socks5.go:198 | 父选项启用时消费；具体分支见源码 |
| skip-cert-verify | adapter/outbound/socks5.go:39 | adapter/outbound/socks5.go:179; adapter/outbound/socks5.go:209 | 父选项启用时消费；具体分支见源码 |
| name-cert-verify | adapter/outbound/socks5.go:40 | adapter/outbound/socks5.go:183 | 父选项启用时消费；具体分支见源码 |
| fingerprint | adapter/outbound/socks5.go:41 | adapter/outbound/socks5.go:182 | 父选项启用时消费；具体分支见源码 |
| certificate | adapter/outbound/socks5.go:42 | adapter/outbound/socks5.go:184 | 父选项启用时消费；具体分支见源码 |
| private-key | adapter/outbound/socks5.go:43 | adapter/outbound/socks5.go:185 | 父选项启用时消费；具体分支见源码 |
| tfo | adapter/outbound/base.go:194 | adapter/outbound/socks5.go:199 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| mptcp | adapter/outbound/base.go:195 | adapter/outbound/socks5.go:200 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| interface-name | adapter/outbound/base.go:196 | adapter/outbound/socks5.go:201 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| routing-mark | adapter/outbound/base.go:197 | adapter/outbound/socks5.go:202 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| ip-version | adapter/outbound/base.go:198 | adapter/outbound/socks5.go:203 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| dialer-proxy | adapter/outbound/base.go:199 | adapter/outbound/base.go:209-215；component/proxydialer/byname.go:22-45 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| alpn | 无（模型与ExtendedConfigBuilder输出） | 无（双核既有问题，见登记） | 已批准输出；FEATURE_MAP 6.10 |

### 4.12 mieru

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 B/S |
| --- | --- | --- | --- |
| name | adapter/outbound/mieru.go:32 | adapter/outbound/mieru.go:160; adapter/outbound/mieru.go:268; adapter/outbound/mieru.go:295 | 父选项启用时消费；具体分支见源码 |
| server | adapter/outbound/mieru.go:33 | adapter/outbound/mieru.go:153; adapter/outbound/mieru.go:156; adapter/outbound/mieru.go:219; adapter/outbound/mieru.go:223 | 父选项启用时消费；具体分支见源码 |
| port | adapter/outbound/mieru.go:34 | adapter/outbound/mieru.go:152; adapter/outbound/mieru.go:153; adapter/outbound/mieru.go:236; adapter/outbound/mieru.go:259 | 父选项启用时消费；具体分支见源码 |
| port-range | adapter/outbound/mieru.go:35 | adapter/outbound/mieru.go:155; adapter/outbound/mieru.go:221; adapter/outbound/mieru.go:226; adapter/outbound/mieru.go:244 | 父选项启用时消费；具体分支见源码 |
| transport | adapter/outbound/mieru.go:36 | adapter/outbound/mieru.go:212; adapter/outbound/mieru.go:326 | 父选项启用时消费；具体分支见源码 |
| udp | adapter/outbound/mieru.go:37 | adapter/outbound/mieru.go:164 | 父选项启用时消费；具体分支见源码 |
| username | adapter/outbound/mieru.go:38 | adapter/outbound/mieru.go:270; adapter/outbound/mieru.go:329 | 父选项启用时消费；具体分支见源码 |
| password | adapter/outbound/mieru.go:39 | adapter/outbound/mieru.go:271; adapter/outbound/mieru.go:332 | 父选项启用时消费；具体分支见源码 |
| multiplexing | adapter/outbound/mieru.go:40 | adapter/outbound/mieru.go:279; adapter/outbound/mieru.go:335; adapter/outbound/mieru.go:336; adapter/outbound/mieru.go:337 | 父选项启用时消费；具体分支见源码 |
| handshake-mode | adapter/outbound/mieru.go:41 | adapter/outbound/mieru.go:284; adapter/outbound/mieru.go:340; adapter/outbound/mieru.go:341; adapter/outbound/mieru.go:342 | 父选项启用时消费；具体分支见源码 |
| traffic-pattern | adapter/outbound/mieru.go:42 | adapter/outbound/mieru.go:287; adapter/outbound/mieru.go:288; adapter/outbound/mieru.go:345; adapter/outbound/mieru.go:346 | 父选项启用时消费；具体分支见源码 |
| tfo | adapter/outbound/base.go:194 | adapter/outbound/mieru.go:165 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| mptcp | adapter/outbound/base.go:195 | adapter/outbound/mieru.go:166 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| interface-name | adapter/outbound/base.go:196 | adapter/outbound/mieru.go:167 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| routing-mark | adapter/outbound/base.go:197 | adapter/outbound/mieru.go:168 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| ip-version | adapter/outbound/base.go:198 | adapter/outbound/mieru.go:169 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| dialer-proxy | adapter/outbound/base.go:199 | adapter/outbound/base.go:209-215；component/proxydialer/byname.go:22-45 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |

### 4.13 anytls

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 B/S |
| --- | --- | --- | --- |
| name | adapter/outbound/anytls.go:29 | adapter/outbound/anytls.go:98 | 父选项启用时消费；具体分支见源码 |
| server | adapter/outbound/anytls.go:30 | adapter/outbound/anytls.go:95; adapter/outbound/anytls.go:116; adapter/outbound/anytls.go:168 | 父选项启用时消费；具体分支见源码 |
| port | adapter/outbound/anytls.go:31 | adapter/outbound/anytls.go:95; adapter/outbound/anytls.go:116 | 父选项启用时消费；具体分支见源码 |
| password | adapter/outbound/anytls.go:32 | adapter/outbound/anytls.go:115 | 父选项启用时消费；具体分支见源码 |
| alpn | adapter/outbound/anytls.go:33 | adapter/outbound/anytls.go:157 | 父选项启用时消费；具体分支见源码 |
| sni | adapter/outbound/anytls.go:34 | adapter/outbound/anytls.go:132; adapter/outbound/anytls.go:154 | 父选项启用时消费；具体分支见源码 |
| ech-opts | adapter/outbound/anytls.go:35 | adapter/outbound/anytls.go:124 | 父选项启用时消费；具体分支见源码 |
| shadow-tls-opts | adapter/outbound/anytls.go:36 | adapter/outbound/anytls.go:128 | 父选项启用时消费；具体分支见源码 |
| restls-opts | adapter/outbound/anytls.go:37 | adapter/outbound/anytls.go:132 | 父选项启用时消费；具体分支见源码 |
| jls-opts | adapter/outbound/anytls.go:38 | adapter/outbound/anytls.go:136 | 父选项启用时消费；具体分支见源码 |
| client-fingerprint | adapter/outbound/anytls.go:39 | adapter/outbound/anytls.go:132; adapter/outbound/anytls.go:161 | 父选项启用时消费；具体分支见源码 |
| skip-cert-verify | adapter/outbound/anytls.go:40 | adapter/outbound/anytls.go:155 | 父选项启用时消费；具体分支见源码 |
| name-cert-verify | adapter/outbound/anytls.go:41 | adapter/outbound/anytls.go:156 | 父选项启用时消费；具体分支见源码 |
| fingerprint | adapter/outbound/anytls.go:42 | adapter/outbound/anytls.go:158 | 父选项启用时消费；具体分支见源码 |
| certificate | adapter/outbound/anytls.go:43 | adapter/outbound/anytls.go:159 | 父选项启用时消费；具体分支见源码 |
| private-key | adapter/outbound/anytls.go:44 | adapter/outbound/anytls.go:160 | 父选项启用时消费；具体分支见源码 |
| udp | adapter/outbound/anytls.go:45 | adapter/outbound/anytls.go:102 | 父选项启用时消费；具体分支见源码 |
| client-metadata | adapter/outbound/anytls.go:46 | adapter/outbound/anytls.go:118 | 父选项启用时消费；具体分支见源码 |
| idle-session-check-interval | adapter/outbound/anytls.go:47 | adapter/outbound/anytls.go:119 | 父选项启用时消费；具体分支见源码 |
| idle-session-timeout | adapter/outbound/anytls.go:48 | adapter/outbound/anytls.go:120 | 父选项启用时消费；具体分支见源码 |
| min-idle-session | adapter/outbound/anytls.go:49 | adapter/outbound/anytls.go:121 | 父选项启用时消费；具体分支见源码 |
| disable-reuse | adapter/outbound/anytls.go:50 | adapter/outbound/anytls.go:122 | 父选项启用时消费；具体分支见源码 |
| tfo | adapter/outbound/base.go:194 | adapter/outbound/anytls.go:103 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| mptcp | adapter/outbound/base.go:195 | adapter/outbound/anytls.go:104 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| interface-name | adapter/outbound/base.go:196 | adapter/outbound/anytls.go:105 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| routing-mark | adapter/outbound/base.go:197 | adapter/outbound/anytls.go:106 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| ip-version | adapter/outbound/base.go:198 | adapter/outbound/anytls.go:107 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| dialer-proxy | adapter/outbound/base.go:199 | adapter/outbound/base.go:209-215；component/proxydialer/byname.go:22-45 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |

### 4.14 sudoku

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 B/S |
| --- | --- | --- | --- |
| name | adapter/outbound/sudoku.go:31 | adapter/outbound/sudoku.go:232 | 父选项启用时消费；具体分支见源码 |
| server | adapter/outbound/sudoku.go:32 | adapter/outbound/sudoku.go:147; adapter/outbound/sudoku.go:200 | 父选项启用时消费；具体分支见源码 |
| port | adapter/outbound/sudoku.go:33 | adapter/outbound/sudoku.go:150; adapter/outbound/sudoku.go:151; adapter/outbound/sudoku.go:200 | 父选项启用时消费；具体分支见源码 |
| key | adapter/outbound/sudoku.go:34 | adapter/outbound/sudoku.go:153; adapter/outbound/sudoku.go:201; adapter/outbound/sudoku.go:214 | 父选项启用时消费；具体分支见源码 |
| aead-method | adapter/outbound/sudoku.go:35 | adapter/outbound/sudoku.go:223; adapter/outbound/sudoku.go:224 | 父选项启用时消费；具体分支见源码 |
| padding-min | adapter/outbound/sudoku.go:36 | adapter/outbound/sudoku.go:162 | 父选项启用时消费；具体分支见源码 |
| padding-max | adapter/outbound/sudoku.go:37 | adapter/outbound/sudoku.go:162 | 父选项启用时消费；具体分支见源码 |
| table-type | adapter/outbound/sudoku.go:38 | adapter/outbound/sudoku.go:158 | 父选项启用时消费；具体分支见源码 |
| enable-pure-downlink | adapter/outbound/sudoku.go:39 | adapter/outbound/sudoku.go:163 | 父选项启用时消费；具体分支见源码 |
| http-mask | adapter/outbound/sudoku.go:40 | adapter/outbound/sudoku.go:166; adapter/outbound/sudoku.go:167 | 父选项启用时消费；具体分支见源码 |
| http-mask-mode | adapter/outbound/sudoku.go:41 | adapter/outbound/sudoku.go:170; adapter/outbound/sudoku.go:171 | 父选项启用时消费；具体分支见源码 |
| http-mask-tls | adapter/outbound/sudoku.go:42 | adapter/outbound/sudoku.go:173 | 父选项启用时消费；具体分支见源码 |
| http-mask-host | adapter/outbound/sudoku.go:43 | adapter/outbound/sudoku.go:174 | 父选项启用时消费；具体分支见源码 |
| path-root | adapter/outbound/sudoku.go:44 | adapter/outbound/sudoku.go:175 | 父选项启用时消费；具体分支见源码 |
| multiplex | adapter/outbound/sudoku.go:45 | adapter/outbound/sudoku.go:177 | 父选项启用时消费；具体分支见源码 |
| http-mask-multiplex | adapter/outbound/sudoku.go:46 | adapter/outbound/sudoku.go:180 | 父选项启用时消费；具体分支见源码 |
| httpmask | adapter/outbound/sudoku.go:47 | adapter/outbound/sudoku.go:184 | 父选项启用时消费；具体分支见源码 |
| custom-table | adapter/outbound/sudoku.go:48 | adapter/outbound/sudoku.go:214 | 父选项启用时消费；具体分支见源码 |
| custom-tables | adapter/outbound/sudoku.go:49 | adapter/outbound/sudoku.go:214 | 父选项启用时消费；具体分支见源码 |
| tfo | adapter/outbound/base.go:194 | adapter/outbound/sudoku.go:237 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| mptcp | adapter/outbound/base.go:195 | adapter/outbound/sudoku.go:238 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| interface-name | adapter/outbound/base.go:196 | adapter/outbound/sudoku.go:239 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| routing-mark | adapter/outbound/base.go:197 | adapter/outbound/sudoku.go:240 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| ip-version | adapter/outbound/base.go:198 | adapter/outbound/sudoku.go:241 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |
| dialer-proxy | adapter/outbound/base.go:199 | adapter/outbound/base.go:209-215；component/proxydialer/byname.go:22-45 | BasicOption；IPVersion枚举校验；外部dialer引用拒绝 |

### 嵌套 V2rayPluginOptions

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| mode | adapter/outbound/shadowsocks.go:64 | adapter/outbound/shadowsocks.go:338; adapter/outbound/shadowsocks.go:339; adapter/outbound/shadowsocks.go:341 | 父选项启用时消费；具体分支见源码 |
| host | adapter/outbound/shadowsocks.go:65 | adapter/outbound/shadowsocks.go:343 | 父选项启用时消费；具体分支见源码 |
| path | adapter/outbound/shadowsocks.go:66 | adapter/outbound/shadowsocks.go:344 | 父选项启用时消费；具体分支见源码 |
| tls | adapter/outbound/shadowsocks.go:67 | adapter/outbound/shadowsocks.go:351 | 父选项启用时消费；具体分支见源码 |
| ech-opts | adapter/outbound/shadowsocks.go:68 | adapter/outbound/shadowsocks.go:359 | 父选项启用时消费；具体分支见源码 |
| fingerprint | adapter/outbound/shadowsocks.go:69 | adapter/outbound/shadowsocks.go:355 | 父选项启用时消费；具体分支见源码 |
| certificate | adapter/outbound/shadowsocks.go:70 | adapter/outbound/shadowsocks.go:356 | 父选项启用时消费；具体分支见源码 |
| private-key | adapter/outbound/shadowsocks.go:71 | adapter/outbound/shadowsocks.go:357 | 父选项启用时消费；具体分支见源码 |
| headers | adapter/outbound/shadowsocks.go:72 | adapter/outbound/shadowsocks.go:345 | 父选项启用时消费；具体分支见源码 |
| skip-cert-verify | adapter/outbound/shadowsocks.go:73 | adapter/outbound/shadowsocks.go:353 | 父选项启用时消费；具体分支见源码 |
| name-cert-verify | adapter/outbound/shadowsocks.go:74 | adapter/outbound/shadowsocks.go:354 | 父选项启用时消费；具体分支见源码 |
| mux | adapter/outbound/shadowsocks.go:75 | adapter/outbound/shadowsocks.go:346 | 父选项启用时消费；具体分支见源码 |
| v2ray-http-upgrade | adapter/outbound/shadowsocks.go:76 | adapter/outbound/shadowsocks.go:347 | 父选项启用时消费；具体分支见源码 |
| v2ray-http-upgrade-fast-open | adapter/outbound/shadowsocks.go:77 | adapter/outbound/shadowsocks.go:348 | 父选项启用时消费；具体分支见源码 |

### 嵌套 AmneziaWGOption

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| version | adapter/outbound/wireguard.go:103 | adapter/outbound/wireguard.go:487 | 父选项启用时消费；具体分支见源码 |
| jc | adapter/outbound/wireguard.go:105 | adapter/outbound/wireguard.go:610; adapter/outbound/wireguard.go:611 | 父选项启用时消费；具体分支见源码 |
| jmin | adapter/outbound/wireguard.go:106 | adapter/outbound/wireguard.go:613; adapter/outbound/wireguard.go:614 | 父选项启用时消费；具体分支见源码 |
| jmax | adapter/outbound/wireguard.go:107 | adapter/outbound/wireguard.go:616; adapter/outbound/wireguard.go:617 | 父选项启用时消费；具体分支见源码 |
| s1 | adapter/outbound/wireguard.go:108 | adapter/outbound/wireguard.go:619; adapter/outbound/wireguard.go:620 | 父选项启用时消费；具体分支见源码 |
| s2 | adapter/outbound/wireguard.go:109 | adapter/outbound/wireguard.go:622; adapter/outbound/wireguard.go:623 | 父选项启用时消费；具体分支见源码 |
| s3 | adapter/outbound/wireguard.go:110 | adapter/outbound/wireguard.go:625; adapter/outbound/wireguard.go:626 | 父选项启用时消费；具体分支见源码 |
| s4 | adapter/outbound/wireguard.go:111 | adapter/outbound/wireguard.go:628; adapter/outbound/wireguard.go:629 | 父选项启用时消费；具体分支见源码 |
| h1 | adapter/outbound/wireguard.go:115 | adapter/outbound/wireguard.go:631; adapter/outbound/wireguard.go:632 | 父选项启用时消费；具体分支见源码 |
| h2 | adapter/outbound/wireguard.go:116 | adapter/outbound/wireguard.go:634; adapter/outbound/wireguard.go:635 | 父选项启用时消费；具体分支见源码 |
| h3 | adapter/outbound/wireguard.go:117 | adapter/outbound/wireguard.go:637; adapter/outbound/wireguard.go:638 | 父选项启用时消费；具体分支见源码 |
| h4 | adapter/outbound/wireguard.go:118 | adapter/outbound/wireguard.go:640; adapter/outbound/wireguard.go:641 | 父选项启用时消费；具体分支见源码 |
| i1 | adapter/outbound/wireguard.go:120 | adapter/outbound/wireguard.go:643; adapter/outbound/wireguard.go:644 | 父选项启用时消费；具体分支见源码 |
| i2 | adapter/outbound/wireguard.go:121 | adapter/outbound/wireguard.go:646; adapter/outbound/wireguard.go:647 | 父选项启用时消费；具体分支见源码 |
| i3 | adapter/outbound/wireguard.go:122 | adapter/outbound/wireguard.go:649; adapter/outbound/wireguard.go:650 | 父选项启用时消费；具体分支见源码 |
| i4 | adapter/outbound/wireguard.go:123 | adapter/outbound/wireguard.go:652; adapter/outbound/wireguard.go:653 | 父选项启用时消费；具体分支见源码 |
| i5 | adapter/outbound/wireguard.go:124 | adapter/outbound/wireguard.go:655; adapter/outbound/wireguard.go:656 | 父选项启用时消费；具体分支见源码 |
| j1 | adapter/outbound/wireguard.go:126 | adapter/outbound/wireguard.go:658; adapter/outbound/wireguard.go:659 | 父选项启用时消费；具体分支见源码 |
| j2 | adapter/outbound/wireguard.go:127 | adapter/outbound/wireguard.go:661; adapter/outbound/wireguard.go:662 | 父选项启用时消费；具体分支见源码 |
| j3 | adapter/outbound/wireguard.go:128 | adapter/outbound/wireguard.go:664; adapter/outbound/wireguard.go:665 | 父选项启用时消费；具体分支见源码 |
| itime | adapter/outbound/wireguard.go:129 | adapter/outbound/wireguard.go:667; adapter/outbound/wireguard.go:668 | 父选项启用时消费；具体分支见源码 |
| header-protection-key | adapter/outbound/wireguard.go:132 | adapter/outbound/wireguard.go:446; adapter/outbound/wireguard.go:447; adapter/outbound/wireguard.go:451; adapter/outbound/wireguard.go:670 | 父选项启用时消费；具体分支见源码 |
| content-padding-addition | adapter/outbound/wireguard.go:133 | adapter/outbound/wireguard.go:673; adapter/outbound/wireguard.go:674 | 父选项启用时消费；具体分支见源码 |
| rekey-after-time | adapter/outbound/wireguard.go:134 | adapter/outbound/wireguard.go:676; adapter/outbound/wireguard.go:677 | 父选项启用时消费；具体分支见源码 |
| rekey-timeout | adapter/outbound/wireguard.go:135 | adapter/outbound/wireguard.go:679; adapter/outbound/wireguard.go:680 | 父选项启用时消费；具体分支见源码 |
| reject-after-time | adapter/outbound/wireguard.go:136 | adapter/outbound/wireguard.go:682; adapter/outbound/wireguard.go:683 | 父选项启用时消费；具体分支见源码 |
| keepalive-timeout | adapter/outbound/wireguard.go:137 | adapter/outbound/wireguard.go:685; adapter/outbound/wireguard.go:686 | 父选项启用时消费；具体分支见源码 |
| max-handshake-attempts | adapter/outbound/wireguard.go:138 | adapter/outbound/wireguard.go:688; adapter/outbound/wireguard.go:689 | 父选项启用时消费；具体分支见源码 |
| random-trailers | adapter/outbound/wireguard.go:139 | adapter/outbound/wireguard.go:691 | 父选项启用时消费；具体分支见源码 |
| disable-cookies | adapter/outbound/wireguard.go:140 | adapter/outbound/wireguard.go:694 | 父选项启用时消费；具体分支见源码 |

### 嵌套 ECHOptions

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| enable | adapter/outbound/ech.go:13 | adapter/outbound/ech.go:20 | enable/key门控；S.validateObject |
| config | adapter/outbound/ech.go:14 | adapter/outbound/ech.go:24; adapter/outbound/ech.go:25 | enable/key门控；S.validateObject |
| query-server-name | adapter/outbound/ech.go:16 | adapter/outbound/ech.go:34; adapter/outbound/ech.go:35 | enable/key门控；S.validateObject |

### 嵌套 GrpcOptions

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| grpc-service-name | adapter/outbound/vmess.go:157 | adapter/outbound/vmess.go:610 | 父选项启用时消费；具体分支见源码 |
| grpc-user-agent | adapter/outbound/vmess.go:158 | adapter/outbound/vmess.go:611 | 父选项启用时消费；具体分支见源码 |
| ping-interval | adapter/outbound/vmess.go:159 | adapter/outbound/vmess.go:613 | 父选项启用时消费；具体分支见源码 |
| max-connections | adapter/outbound/vmess.go:160 | adapter/outbound/vmess.go:647 | 父选项启用时消费；具体分支见源码 |
| min-streams | adapter/outbound/vmess.go:161 | adapter/outbound/vmess.go:648 | 父选项启用时消费；具体分支见源码 |
| max-streams | adapter/outbound/vmess.go:162 | adapter/outbound/vmess.go:649 | 父选项启用时消费；具体分支见源码 |

### 嵌套 HTTP2Options

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| host | adapter/outbound/vmess.go:152 | adapter/outbound/vmess.go:269; adapter/outbound/vmess.go:574; adapter/outbound/vmess.go:575 | 暂拒绝组合；G01/G07 |
| path | adapter/outbound/vmess.go:153 | adapter/outbound/vmess.go:270 | 暂拒绝组合；G01/G07 |

### 嵌套 HTTPOptions

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| method | adapter/outbound/vmess.go:146 | adapter/outbound/vmess.go:256 | 暂拒绝组合；G01/G07 |
| path | adapter/outbound/vmess.go:147 | adapter/outbound/vmess.go:257 | 暂拒绝组合；G01/G07 |
| headers | adapter/outbound/vmess.go:148 | adapter/outbound/vmess.go:258 | 暂拒绝组合；G01/G07 |

### 嵌套 Hysteria2RealmOption

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| enable | adapter/outbound/hysteria2.go:76 | adapter/outbound/hysteria2.go:270 | enable/key门控；S.validateObject |
| server-url | adapter/outbound/hysteria2.go:77 | adapter/outbound/hysteria2.go:286 | enable/key门控；S.validateObject |
| token | adapter/outbound/hysteria2.go:78 | adapter/outbound/hysteria2.go:287 | enable/key门控；S.validateObject |
| realm-id | adapter/outbound/hysteria2.go:79 | adapter/outbound/hysteria2.go:288 | enable/key门控；S.validateObject |
| stun-servers | adapter/outbound/hysteria2.go:80 | adapter/outbound/hysteria2.go:289 | enable/key门控；S.validateObject |
| sni | adapter/outbound/hysteria2.go:83 | adapter/outbound/hysteria2.go:273 | enable/key门控；S.validateObject |
| skip-cert-verify | adapter/outbound/hysteria2.go:84 | adapter/outbound/hysteria2.go:274 | enable/key门控；S.validateObject |
| name-cert-verify | adapter/outbound/hysteria2.go:85 | adapter/outbound/hysteria2.go:278 | enable/key门控；S.validateObject |
| fingerprint | adapter/outbound/hysteria2.go:86 | adapter/outbound/hysteria2.go:277 | enable/key门控；S.validateObject |
| certificate | adapter/outbound/hysteria2.go:87 | adapter/outbound/hysteria2.go:279 | enable/key门控；S.validateObject |
| private-key | adapter/outbound/hysteria2.go:88 | adapter/outbound/hysteria2.go:280 | enable/key门控；S.validateObject |
| alpn | adapter/outbound/hysteria2.go:89 | adapter/outbound/hysteria2.go:275 | enable/key门控；S.validateObject |

### 嵌套 IPStackOption

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| mode | adapter/outbound/wireguard.go:144 | adapter/outbound/wireguard.go:149; adapter/outbound/wireguard.go:150; adapter/outbound/wireguard.go:151; adapter/outbound/wireguard.go:157 | 父选项启用时消费；具体分支见源码 |
| congestion-controller | adapter/outbound/wireguard.go:145 | adapter/outbound/wireguard.go:153; adapter/outbound/wireguard.go:166; adapter/outbound/wireguard.go:170 | 父选项启用时消费；具体分支见源码 |

### 嵌套 JLSOptions

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| username | adapter/outbound/jls.go:6 | adapter/outbound/jls.go:11; adapter/outbound/jls.go:14 | 父选项启用时消费；具体分支见源码 |
| password | adapter/outbound/jls.go:7 | adapter/outbound/jls.go:11; adapter/outbound/jls.go:14 | 父选项启用时消费；具体分支见源码 |

### 嵌套 MKCPOptions

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| mtu | adapter/outbound/vmess.go:92 | adapter/outbound/vmess.go:105 | 暂拒绝组合；G01/G07 |
| tti | adapter/outbound/vmess.go:93 | adapter/outbound/vmess.go:106 | 暂拒绝组合；G01/G07 |
| uplink-capacity | adapter/outbound/vmess.go:94 | adapter/outbound/vmess.go:107 | 暂拒绝组合；G01/G07 |
| downlink-capacity | adapter/outbound/vmess.go:95 | adapter/outbound/vmess.go:108 | 暂拒绝组合；G01/G07 |
| congestion | adapter/outbound/vmess.go:96 | adapter/outbound/vmess.go:109 | 暂拒绝组合；G01/G07 |
| write-buffer | adapter/outbound/vmess.go:97 | adapter/outbound/vmess.go:110 | 暂拒绝组合；G01/G07 |
| read-buffer | adapter/outbound/vmess.go:98 | adapter/outbound/vmess.go:111 | 暂拒绝组合；G01/G07 |
| seed | adapter/outbound/vmess.go:99 | adapter/outbound/vmess.go:112 | 暂拒绝组合；G01/G07 |
| header | adapter/outbound/vmess.go:100 | adapter/outbound/vmess.go:113 | 暂拒绝组合；G01/G07 |

### 嵌套 MekyaOptions

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| url | adapter/outbound/vmess.go:118 | adapter/outbound/vmess.go:133 | 暂拒绝组合；G01/G07 |
| h2-pool-size | adapter/outbound/vmess.go:119 | adapter/outbound/vmess.go:134 | 暂拒绝组合；G01/G07 |
| max-write-delay | adapter/outbound/vmess.go:120 | adapter/outbound/vmess.go:135 | 暂拒绝组合；G01/G07 |
| max-request-size | adapter/outbound/vmess.go:121 | adapter/outbound/vmess.go:136 | 暂拒绝组合；G01/G07 |
| polling-interval-initial | adapter/outbound/vmess.go:122 | adapter/outbound/vmess.go:137 | 暂拒绝组合；G01/G07 |
| max-write-size | adapter/outbound/vmess.go:123 | adapter/outbound/vmess.go:138 | 暂拒绝组合；G01/G07 |
| max-write-duration-ms | adapter/outbound/vmess.go:124 | adapter/outbound/vmess.go:139 | 暂拒绝组合；G01/G07 |
| max-simultaneous-write-connection | adapter/outbound/vmess.go:125 | adapter/outbound/vmess.go:140 | 暂拒绝组合；G01/G07 |
| packet-writing-buffer | adapter/outbound/vmess.go:126 | adapter/outbound/vmess.go:141 | 暂拒绝组合；G01/G07 |
| kcp | adapter/outbound/vmess.go:127 | adapter/outbound/vmess.go:132 | 暂拒绝组合；G01/G07 |

### 嵌套 RealityOptions

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| public-key | adapter/outbound/reality.go:14 | adapter/outbound/reality.go:21; adapter/outbound/reality.go:26 | enable/key门控；S.validateObject |
| short-id | adapter/outbound/reality.go:15 | adapter/outbound/reality.go:35; adapter/outbound/reality.go:39 | enable/key门控；S.validateObject |
| support-x25519mlkem768 | adapter/outbound/reality.go:17 | adapter/outbound/reality.go:23 | enable/key门控；S.validateObject |

### 嵌套 RestlsOptions

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| password | adapter/outbound/restls.go:6 | adapter/outbound/restls.go:12; adapter/outbound/restls.go:15 | 父选项启用时消费；具体分支见源码 |
| version-hint | adapter/outbound/restls.go:7 | adapter/outbound/restls.go:12; adapter/outbound/restls.go:15 | 父选项启用时消费；具体分支见源码 |
| restls-script | adapter/outbound/restls.go:8 | adapter/outbound/restls.go:12; adapter/outbound/restls.go:15 | 父选项启用时消费；具体分支见源码 |

### 嵌套 ShadowTLSOptions

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| password | adapter/outbound/shadowtls.go:6 | adapter/outbound/shadowtls.go:11; adapter/outbound/shadowtls.go:14 | 父选项启用时消费；具体分支见源码 |
| version | adapter/outbound/shadowtls.go:7 | adapter/outbound/shadowtls.go:11; adapter/outbound/shadowtls.go:14 | 父选项启用时消费；具体分支见源码 |

### 嵌套 SudokuHTTPMaskOptions

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| disable | adapter/outbound/sudoku.go:53 | adapter/outbound/sudoku.go:185 | 父选项启用时消费；具体分支见源码 |
| mode | adapter/outbound/sudoku.go:54 | adapter/outbound/sudoku.go:186; adapter/outbound/sudoku.go:187 | 父选项启用时消费；具体分支见源码 |
| tls | adapter/outbound/sudoku.go:55 | adapter/outbound/sudoku.go:189 | 父选项启用时消费；具体分支见源码 |
| host | adapter/outbound/sudoku.go:56 | adapter/outbound/sudoku.go:190 | 父选项启用时消费；具体分支见源码 |
| path-root | adapter/outbound/sudoku.go:57 | adapter/outbound/sudoku.go:191 | 父选项启用时消费；具体分支见源码 |
| multiplex | adapter/outbound/sudoku.go:58 | adapter/outbound/sudoku.go:194 | 父选项启用时消费；具体分支见源码 |

### 嵌套 TLSMirrorConnectionEnrolment

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| primary-ingress-outbound | adapter/outbound/tlsmirror.go:16 | adapter/outbound/tlsmirror.go:25 | 父选项启用时消费；具体分支见源码 |
| primary-egress-outbound | adapter/outbound/tlsmirror.go:17 | adapter/outbound/tlsmirror.go:26 | 父选项启用时消费；具体分支见源码 |

### 嵌套 TLSMirrorOptions

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| primary-key | adapter/outbound/tlsmirror.go:6 | adapter/outbound/tlsmirror.go:130; adapter/outbound/tlsmirror.go:134 | enable/key门控；S.validateObject |
| explicit-nonce-ciphersuites | adapter/outbound/tlsmirror.go:7 | adapter/outbound/tlsmirror.go:135 | enable/key门控；S.validateObject |
| defer-instance-derived-write-time | adapter/outbound/tlsmirror.go:8 | adapter/outbound/tlsmirror.go:136 | enable/key门控；S.validateObject |
| transport-layer-padding | adapter/outbound/tlsmirror.go:9 | adapter/outbound/tlsmirror.go:137 | enable/key门控；S.validateObject |
| connection-enrolment | adapter/outbound/tlsmirror.go:10 | adapter/outbound/tlsmirror.go:138 | enable/key门控；S.validateObject |
| embedded-traffic-generator | adapter/outbound/tlsmirror.go:11 | adapter/outbound/tlsmirror.go:140 | enable/key门控；S.validateObject |
| sequence-watermarking-enabled | adapter/outbound/tlsmirror.go:12 | adapter/outbound/tlsmirror.go:139 | enable/key门控；S.validateObject |

### 嵌套 TLSMirrorTimeSpec

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| base-nanoseconds | adapter/outbound/tlsmirror.go:31 | adapter/outbound/tlsmirror.go:37 | 父选项启用时消费；具体分支见源码 |
| uniform-random-multiplier-nanoseconds | adapter/outbound/tlsmirror.go:32 | adapter/outbound/tlsmirror.go:38 | 父选项启用时消费；具体分支见源码 |

### 嵌套 TLSMirrorTrafficGenerator

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| steps | adapter/outbound/tlsmirror.go:53 | adapter/outbound/tlsmirror.go:57; adapter/outbound/tlsmirror.go:60; adapter/outbound/tlsmirror.go:61 | 父选项启用时消费；具体分支见源码 |

### 嵌套 TLSMirrorTrafficHeader

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| name | adapter/outbound/tlsmirror.go:104 | adapter/outbound/tlsmirror.go:111 | 父选项启用时消费；具体分支见源码 |
| value | adapter/outbound/tlsmirror.go:105 | adapter/outbound/tlsmirror.go:112 | 父选项启用时消费；具体分支见源码 |
| values | adapter/outbound/tlsmirror.go:106 | adapter/outbound/tlsmirror.go:113 | 父选项启用时消费；具体分支见源码 |

### 嵌套 TLSMirrorTrafficStep

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| name | adapter/outbound/tlsmirror.go:68 | adapter/outbound/tlsmirror.go:90 | 父选项启用时消费；具体分支见源码 |
| host | adapter/outbound/tlsmirror.go:69 | adapter/outbound/tlsmirror.go:91 | 父选项启用时消费；具体分支见源码 |
| path | adapter/outbound/tlsmirror.go:70 | adapter/outbound/tlsmirror.go:92 | 父选项启用时消费；具体分支见源码 |
| method | adapter/outbound/tlsmirror.go:71 | adapter/outbound/tlsmirror.go:93 | 父选项启用时消费；具体分支见源码 |
| headers | adapter/outbound/tlsmirror.go:72 | adapter/outbound/tlsmirror.go:81; adapter/outbound/tlsmirror.go:82 | 父选项启用时消费；具体分支见源码 |
| next-step | adapter/outbound/tlsmirror.go:73 | adapter/outbound/tlsmirror.go:85; adapter/outbound/tlsmirror.go:86 | 父选项启用时消费；具体分支见源码 |
| connection-ready | adapter/outbound/tlsmirror.go:74 | adapter/outbound/tlsmirror.go:96 | 父选项启用时消费；具体分支见源码 |
| connection-recall-exit | adapter/outbound/tlsmirror.go:75 | adapter/outbound/tlsmirror.go:97 | 父选项启用时消费；具体分支见源码 |
| wait-time | adapter/outbound/tlsmirror.go:76 | adapter/outbound/tlsmirror.go:98 | 父选项启用时消费；具体分支见源码 |
| h2-do-not-wait-for-download-finish | adapter/outbound/tlsmirror.go:77 | adapter/outbound/tlsmirror.go:99 | 父选项启用时消费；具体分支见源码 |

### 嵌套 TLSMirrorTrafficTransferCandidate

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| weight | adapter/outbound/tlsmirror.go:118 | adapter/outbound/tlsmirror.go:124 | 父选项启用时消费；具体分支见源码 |
| goto-location | adapter/outbound/tlsmirror.go:119 | adapter/outbound/tlsmirror.go:125 | 父选项启用时消费；具体分支见源码 |

### 嵌套 TLSMirrorTransportLayerPadding

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| enabled | adapter/outbound/tlsmirror.go:43 | adapter/outbound/tlsmirror.go:48 | 父选项启用时消费；具体分支见源码 |

### 嵌套 TrojanSSOption

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| enabled | adapter/outbound/trojan.go:73 | adapter/outbound/trojan.go:346 | enable/key门控；S.validateObject |
| method | adapter/outbound/trojan.go:74 | adapter/outbound/trojan.go:350; adapter/outbound/trojan.go:351; adapter/outbound/trojan.go:353 | enable/key门控；S.validateObject |
| password | adapter/outbound/trojan.go:75 | adapter/outbound/trojan.go:347; adapter/outbound/trojan.go:353 | enable/key门控；S.validateObject |

### 嵌套 WSOptions

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| path | adapter/outbound/vmess.go:166 | adapter/outbound/vmess.go:181 | 父选项启用时消费；具体分支见源码 |
| headers | adapter/outbound/vmess.go:167 | adapter/outbound/vmess.go:191; adapter/outbound/vmess.go:192 | 父选项启用时消费；具体分支见源码 |
| max-early-data | adapter/outbound/vmess.go:168 | adapter/outbound/vmess.go:182 | 父选项启用时消费；具体分支见源码 |
| early-data-header-name | adapter/outbound/vmess.go:169 | adapter/outbound/vmess.go:183 | 父选项启用时消费；具体分支见源码 |
| v2ray-http-upgrade | adapter/outbound/vmess.go:170 | adapter/outbound/vmess.go:184 | 父选项启用时消费；具体分支见源码 |
| v2ray-http-upgrade-fast-open | adapter/outbound/vmess.go:171 | adapter/outbound/vmess.go:185 | 父选项启用时消费；具体分支见源码 |

### 嵌套 WireGuardPeerOption

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| server | adapter/outbound/wireguard.go:94 | adapter/outbound/wireguard.go:320 | peer数量与地址/allowedIPs条件；G05/G06 |
| port | adapter/outbound/wireguard.go:95 | adapter/outbound/wireguard.go:320 | peer数量与地址/allowedIPs条件；G05/G06 |
| public-key | adapter/outbound/wireguard.go:96 | adapter/outbound/wireguard.go:406; adapter/outbound/wireguard.go:410; adapter/outbound/wireguard.go:715 | peer数量与地址/allowedIPs条件；G05/G06 |
| pre-shared-key | adapter/outbound/wireguard.go:97 | adapter/outbound/wireguard.go:412; adapter/outbound/wireguard.go:413; adapter/outbound/wireguard.go:417; adapter/outbound/wireguard.go:728 | peer数量与地址/allowedIPs条件；G05/G06 |
| reserved | adapter/outbound/wireguard.go:98 | adapter/outbound/wireguard.go:424; adapter/outbound/wireguard.go:425; adapter/outbound/wireguard.go:426; adapter/outbound/wireguard.go:720 | peer数量与地址/allowedIPs条件；G05/G06 |
| allowed-ips | adapter/outbound/wireguard.go:99 | adapter/outbound/wireguard.go:420; adapter/outbound/wireguard.go:731 | peer数量与地址/allowedIPs条件；G05/G06 |

### 嵌套 XHTTPDownloadSettings

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| path | adapter/outbound/vless.go:132 | adapter/outbound/vless.go:813 | 父选项启用时消费；具体分支见源码 |
| host | adapter/outbound/vless.go:133 | adapter/outbound/vless.go:797 | 父选项启用时消费；具体分支见源码 |
| headers | adapter/outbound/vless.go:134 | adapter/outbound/vless.go:814 | 父选项启用时消费；具体分支见源码 |
| reuse-settings | adapter/outbound/vless.go:135 | adapter/outbound/vless.go:816; adapter/outbound/vless.go:818; adapter/outbound/vless.go:819; adapter/outbound/vless.go:820 | 父选项启用时消费；具体分支见源码 |
| server | adapter/outbound/vless.go:137 | adapter/outbound/vless.go:725 | 父选项启用时消费；具体分支见源码 |
| port | adapter/outbound/vless.go:138 | adapter/outbound/vless.go:726 | 父选项启用时消费；具体分支见源码 |
| tls | adapter/outbound/vless.go:139 | adapter/outbound/vless.go:727 | 父选项启用时消费；具体分支见源码 |
| alpn | adapter/outbound/vless.go:140 | adapter/outbound/vless.go:728 | 父选项启用时消费；具体分支见源码 |
| ech-opts | adapter/outbound/vless.go:141 | adapter/outbound/vless.go:737; adapter/outbound/vless.go:738 | 父选项启用时消费；具体分支见源码 |
| shadow-tls-opts | adapter/outbound/vless.go:142 | adapter/outbound/vless.go:744; adapter/outbound/vless.go:745 | 父选项启用时消费；具体分支见源码 |
| restls-opts | adapter/outbound/vless.go:143 | adapter/outbound/vless.go:751; adapter/outbound/vless.go:752 | 父选项启用时消费；具体分支见源码 |
| jls-opts | adapter/outbound/vless.go:144 | adapter/outbound/vless.go:758; adapter/outbound/vless.go:759 | 父选项启用时消费；具体分支见源码 |
| reality-opts | adapter/outbound/vless.go:145 | adapter/outbound/vless.go:765; adapter/outbound/vless.go:766 | 父选项启用时消费；具体分支见源码 |
| skip-cert-verify | adapter/outbound/vless.go:146 | adapter/outbound/vless.go:729 | 父选项启用时消费；具体分支见源码 |
| name-cert-verify | adapter/outbound/vless.go:147 | adapter/outbound/vless.go:730 | 父选项启用时消费；具体分支见源码 |
| fingerprint | adapter/outbound/vless.go:148 | adapter/outbound/vless.go:731 | 父选项启用时消费；具体分支见源码 |
| certificate | adapter/outbound/vless.go:149 | adapter/outbound/vless.go:732 | 父选项启用时消费；具体分支见源码 |
| private-key | adapter/outbound/vless.go:150 | adapter/outbound/vless.go:733 | 父选项启用时消费；具体分支见源码 |
| servername | adapter/outbound/vless.go:151 | adapter/outbound/vless.go:734 | 父选项启用时消费；具体分支见源码 |
| client-fingerprint | adapter/outbound/vless.go:152 | adapter/outbound/vless.go:735 | 父选项启用时消费；具体分支见源码 |

### 嵌套 XHTTPOptions

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| path | adapter/outbound/vless.go:94 | adapter/outbound/vless.go:643; adapter/outbound/vless.go:813 | 父选项启用时消费；具体分支见源码 |
| host | adapter/outbound/vless.go:95 | adapter/outbound/vless.go:615; adapter/outbound/vless.go:797 | 父选项启用时消费；具体分支见源码 |
| mode | adapter/outbound/vless.go:96 | adapter/outbound/vless.go:644 | 父选项启用时消费；具体分支见源码 |
| headers | adapter/outbound/vless.go:97 | adapter/outbound/vless.go:645; adapter/outbound/vless.go:814 | 父选项启用时消费；具体分支见源码 |
| no-grpc-header | adapter/outbound/vless.go:98 | adapter/outbound/vless.go:646 | 父选项启用时消费；具体分支见源码 |
| x-padding-bytes | adapter/outbound/vless.go:99 | adapter/outbound/vless.go:647 | 父选项启用时消费；具体分支见源码 |
| x-padding-obfs-mode | adapter/outbound/vless.go:100 | adapter/outbound/vless.go:648 | 父选项启用时消费；具体分支见源码 |
| x-padding-key | adapter/outbound/vless.go:101 | adapter/outbound/vless.go:649 | 父选项启用时消费；具体分支见源码 |
| x-padding-header | adapter/outbound/vless.go:102 | adapter/outbound/vless.go:650 | 父选项启用时消费；具体分支见源码 |
| x-padding-placement | adapter/outbound/vless.go:103 | adapter/outbound/vless.go:651 | 父选项启用时消费；具体分支见源码 |
| x-padding-method | adapter/outbound/vless.go:104 | adapter/outbound/vless.go:652 | 父选项启用时消费；具体分支见源码 |
| uplink-http-method | adapter/outbound/vless.go:105 | adapter/outbound/vless.go:653 | 父选项启用时消费；具体分支见源码 |
| session-placement | adapter/outbound/vless.go:106 | adapter/outbound/vless.go:654 | 父选项启用时消费；具体分支见源码 |
| session-key | adapter/outbound/vless.go:107 | adapter/outbound/vless.go:655 | 父选项启用时消费；具体分支见源码 |
| session-table | adapter/outbound/vless.go:108 | adapter/outbound/vless.go:656 | 父选项启用时消费；具体分支见源码 |
| session-length | adapter/outbound/vless.go:109 | adapter/outbound/vless.go:657 | 父选项启用时消费；具体分支见源码 |
| seq-placement | adapter/outbound/vless.go:110 | adapter/outbound/vless.go:658 | 父选项启用时消费；具体分支见源码 |
| seq-key | adapter/outbound/vless.go:111 | adapter/outbound/vless.go:659 | 父选项启用时消费；具体分支见源码 |
| uplink-data-placement | adapter/outbound/vless.go:112 | adapter/outbound/vless.go:660 | 父选项启用时消费；具体分支见源码 |
| uplink-data-key | adapter/outbound/vless.go:113 | adapter/outbound/vless.go:661 | 父选项启用时消费；具体分支见源码 |
| uplink-chunk-size | adapter/outbound/vless.go:114 | adapter/outbound/vless.go:662 | 父选项启用时消费；具体分支见源码 |
| sc-max-each-post-bytes | adapter/outbound/vless.go:115 | adapter/outbound/vless.go:663 | 父选项启用时消费；具体分支见源码 |
| sc-min-posts-interval-ms | adapter/outbound/vless.go:116 | adapter/outbound/vless.go:664 | 父选项启用时消费；具体分支见源码 |
| reuse-settings | adapter/outbound/vless.go:117 | adapter/outbound/vless.go:630; adapter/outbound/vless.go:632; adapter/outbound/vless.go:633; adapter/outbound/vless.go:634 | 父选项启用时消费；具体分支见源码 |
| download-settings | adapter/outbound/vless.go:118 | adapter/outbound/vless.go:720 | 父选项启用时消费；具体分支见源码 |

### 嵌套 XHTTPReuseSettings

| 键 | 声明（文件:行） | mihomo 消费入口（文件:行） | 条件与实现落点 S |
| --- | --- | --- | --- |
| max-concurrency | adapter/outbound/vless.go:122 | adapter/outbound/vless.go:632; adapter/outbound/vless.go:818 | 父选项启用时消费；具体分支见源码 |
| max-connections | adapter/outbound/vless.go:123 | adapter/outbound/vless.go:633; adapter/outbound/vless.go:819 | 父选项启用时消费；具体分支见源码 |
| c-max-reuse-times | adapter/outbound/vless.go:124 | adapter/outbound/vless.go:634; adapter/outbound/vless.go:820 | 父选项启用时消费；具体分支见源码 |
| h-max-request-times | adapter/outbound/vless.go:125 | adapter/outbound/vless.go:635; adapter/outbound/vless.go:821 | 父选项启用时消费；具体分支见源码 |
| h-max-reusable-secs | adapter/outbound/vless.go:126 | adapter/outbound/vless.go:636; adapter/outbound/vless.go:822 | 父选项启用时消费；具体分支见源码 |
| h-keep-alive-period | adapter/outbound/vless.go:127 | adapter/outbound/vless.go:638; adapter/outbound/vless.go:824 | 父选项启用时消费；具体分支见源码 |

### protocolOptions 键与别名（不是任意透传）

ExtendedConfigBuilder 保持原状；single 只复用有已知消费入口的输出，并在输入侧拒绝未知键、冲突别名、未读取别名。以下每项的最终消费入口均是第 4 节同名输出键；嵌套 JSON 递归走 S，不以“已输出”冒充“已消费”。

- ExtendedConfigBuilder.kt:22：text("fingerprint", option("pinSHA256", "fingerprint"))；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:27：val transport = option("transport").ifEmpty { "TCP" }.uppercase()；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:30：val range = option("port-range")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:36：val multiplexing = option("multiplexing")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:39：val handshake = option("handshake-mode")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:41：text("handshake-mode", handshake); textOption("traffic-pattern")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:43：val mtu = option("mtu")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:51：val up = option("up", "upmbps"); val down = option("down", "downmbps")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:54：textOption("ports", "mport"); textOption("protocol"); number("hop-interval", "hopInterval")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:55：val obfs = option("obfs")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:56：if (obfs == "xplus") text("obfs", option("obfsParam", "obfs-password")) else text("obfs", obfs)；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:61：textOption("up", "upmbps"); textOption("down", "downmbps")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:62：textOption("ports", "mport"); textOption("hop-interval", "hopInterval")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:63：textOption("obfs"); textOption("obfs-password", "obfsParam")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:64：option("ech").takeIf { it.isNotEmpty() }?.let { putJsonObject("ech-opts") { put("enable", true); put("config", it) } }；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:69：else { val token = p.password.ifEmpty { option("token") }; require(token.isNotEmpty()) { "请输入 TUIC UUID/密码或 v4 Token" }; put("token", token) }；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:70：textOption("congestion-controller", "congestion_control", "congestion-controller")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:71：textOption("udp-relay-mode", "udp_relay_mode")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:72：boolean("disable-sni", "disable_sni"); boolean("reduce-rtt", "reduce_rtt"); boolean("fast-open", "fast_open")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:73：number("heartbeat-interval", "heartbeat"); number("request-timeout"); number("max-udp-relay-packet-size")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:77：val privateKey = option("private-key"); val publicKey = p.publicKey.ifEmpty { option("publickey", "public-key") }；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:80：val addresses = option("address", "local-address", "ip").split(',').map(String::trim).filter(String::isNotEmpty)；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:82：val ipv6 = addresses.firstOrNull { ':' in it }.orEmpty().ifEmpty { option("ipv6") }；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:85：textOption("pre-shared-key", "presharedkey", "preshared-key")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:86：number("mtu"); number("persistent-keepalive", "keepalive")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:87：val reserved = option("reserved").removeSurrounding("[", "]")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:92：list("allowed-ips", option("allowed-ips", "allowedips")); list("dns", option("dns"))；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:93：if (option("dns").isNotEmpty()) put("remote-dns-resolve", true)；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:98：put("protocol", option("protocol").ifEmpty { "origin" }); put("obfs", option("obfs").ifEmpty { "plain" })；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:99：textOption("protocol-param", "protocol-param", "protoparam"); textOption("obfs-param", "obfs-param", "obfsparam")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:104：text("client-fingerprint", p.fingerprint.ifEmpty { option("fp", "client-fingerprint", "clientFingerprint") })；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:105：textOption("client-metadata", "clientMetadata")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:106：number("idle-session-check-interval", "idleSessionCheckInterval")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:107：number("idle-session-timeout", "idleSessionTimeout")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:108：number("min-idle-session", "minIdleSession")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:109：boolean("disable-reuse", "disableReuse")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:111：option(key).takeIf(String::isNotEmpty)?.let { put(key, Json.parseToJsonElement(it)) }；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:117：textOption("aead-method", "aeadMethod")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:118：textOption("table-type", "tableType")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:119：textOption("multiplex")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:120：textOption("http-mask-mode", "httpMaskMode")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:121：textOption("http-mask-host", "httpMaskHost")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:122：textOption("path-root", "pathRoot")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:123：textOption("custom-table", "customTable")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:124：number("padding-min", "paddingMin")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:125：number("padding-max", "paddingMax")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:126：boolean("enable-pure-downlink", "enablePureDownlink")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:127：boolean("http-mask", "httpMask")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:128：boolean("http-mask-tls", "httpMaskTls")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:129：textOption("http-mask-multiplex", "httpMaskMultiplex")；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:130：list("custom-tables", option("custom-tables", "customTables"))；落点 B.extended/checkOptionKeys，输出消费见上表。
- ExtendedConfigBuilder.kt:131：option("httpmask").takeIf(String::isNotEmpty)?.let { put("httpmask", Json.parseToJsonElement(it)) }；落点 B.extended/checkOptionKeys，输出消费见上表。

## 5. 一次性缺口清单（第 2 步审计快照；第 3 步处置见 SINGLE_BRIDGE_STEP3.md）

每项保持 FEATURE_MAP 既有分类；“疑似/待等价证明”不是自动改判成无等价。当前 mapper 对未批准字段/组合先抛出 MihomoMappingException（CONFIGURATION_OR_SUBSCRIPTION），不返回含无消费字段的配置，不改 ServerProfile；错误接入UI属于第3步，本步未接 bridge，不能声称设备已显示这些错误。

| ID | 缺口与 pin 源码证据 | 当前行为 / 待处置方案 | 测试或状态 |
| --- | --- | --- | --- |
| G01 | TCP HTTP header：transport/vmess/http.go:54-96 以带payload的HTTP请求封装；Xray tcpSettings.header.request语义不能仅凭字段同名证明相同。VMess/VLESS有HTTPOpts，Trojan无通用入口 | headerType!=none、http-opts/h2-opts拒绝；按协议补等价夹具或单独批准限制。FEATURE_MAP6.3分类不变 | tcpHttpHeaderRejected |
| G02 | 条件 TLS：VMess/VLESS WS固定ALPN http/1.1（vmess.go:205-239、vless.go:185-220）；三协议gRPC固定h2（vmess.go:628、vless.go:593、trojan.go:377）；普通 TLS WS 客户端指纹在 transport/vmess/websocket.go:365-375 消费；HTTP/SOCKS5 ALPN 见既有同病登记 | 固定值逐值相同按等价放行，非空不同值显式拒绝；WS client-fingerprint 正常映射；dual 同样忽略的 no-TLS/Reality ALPN 与 allowInsecure 保持输出并登记，不声称消费 | trojanGrpcFixedAlpnIsEquivalent / trojanGrpcDifferentAlpnRejected / wsFingerprintIsConsumed / realityAlpnAndAllowInsecureKeepDualIgnoredBehavior |
| G03 | Reality：WS三协议普通路径无Reality传递；transport/vmess/tls.go:115-116 Reality分支不接收用户ALPN与skip-cert-verify；component/tls/reality.go:38-48独立校验 | WS+Reality、Reality+用户ALPN/allowInsecure=true拒绝；其余Reality字段有入口，spider按已批准近似 | wsRealityRejected / realityKeysAndVisionPreserved |
| G04 | SOCKS5通用sni：dual在ExtendedConfigBuilder.kt:136写name-cert-verify；socks5.go:180固定ServerName=server，:183只是证书名校验 | G04 撤销顶替：在调用共享 builder 前拒绝非空模型 sni，不生成 name-cert-verify 冒充 SNI；raw name-cert-verify 独立直映 | socksSniGapRejected |
| G05 | VLESS ws-headers 声明于 vless.go:83，WS 消费在 vless.go:172-175 与 websocket.go:396-400；WireGuard 顶层 allowed-ips 在隐含 peer 分支 wireguard.go:760-773 固定默认路由，显式 peer 的 allowed-ips 在:731-732 消费 | Host-only WS header 转换到嵌套 Host；自定义头拒绝。WG 非空顶层 allowed-ips 转为一个显式 peer，保留 endpoint/key/allowed-ips；空值仍走 dual 默认。reserved 仍按 :367-383 消费 | vlessHostWsHeadersConverted / vlessCustomWsHeadersRejected / wgAllowedIpsSinglePeerConverted / wgReservedHasConsumer |
| G06 | ExtendedConfigBuilder 的 option() 采用首个非空值；raw Clash 配置由 XrayConfigBuilder:64-69 直接沿 adapter.ParseProxy；TUIC token/v4-v5、WG 地址前缀、Hy1 覆盖和 BasicOption 未转发均有确定性 dual 语义（见对应 pin 行） | dual 可确定解析的别名/覆盖组合按同序输出，不再整包拒绝；raw 保持原配置优先；WG 地址前缀按 dual 去前缀；TUIC fast-open/request-timeout 按 dual 输出，v5 不声称 request-timeout 消费；未知键仍显式拒绝 | ignoredProtocolOptionMatchesDual / duplicateBandwidthAliasesFollowDualPrecedence / wgAddressPrefixMatchesDual / tuicFastOpenFollowsDualOutput / rawHy1AuthConflictFollowsDualPrecedence / rawHy2InactiveObfsSizeFollowsDualPrecedence / rawWgPeersAndTopKeyConflictFollowsDualPrecedence |
| G07 | raw Clash动态边界：其他SS插件obfs/gost/shadowtls/restls/jls/kcptun有各自动态map；原始network h2/mkcp/mekya存在上游实现，但不在当前通用传输等价审核；未知键与未知枚举无法证明消费。嵌套ECH关闭或固定config时query字段、Realm/TrojanSS关闭、TLSMirror无key时其余字段不消费 | 已确认v2ray-plugin websocket逐字段保留；其他插件/未审定transport/未知键拒绝，不把原始JSON抹掉。条件关掉却有payload则拒绝 | pluginUnknownOptionRejected / unknownNestedOptionRejected / echDisabledPayloadRejected / echFixedConfigAndQueryConflictRejected |
| G08 | 路由/DNS语义：裸域名、ext:外部geo资源、含逗号/括号的逻辑payload转义；Xray DNS域条件支持keyword/regexp，而nameserver-policy是domain trie/geosite（config.go:1339-1410） | 裸域/ext/未审定逻辑转义、无法表达DNS policy域条件拒绝；已有前缀与源/目标/端口组合继续实现。Xray regex→Go RE2差异尚需等价夹具 | unprefixedDomainIsPending / externalGeoRuleIsRejected / ruleFieldsOrWithinAndAcross |
| G09 | sniffed protocols规则无等价PROTOCOL解析分支（rules/parser.go:17-98）；bittorrent与destOverride旧不一致已在FEATURE_MAP6.1 | protocol条件显式拒绝；本步未修改嗅探与destOverride。后续shim/内核补丁单列 | protocolRuleIsRejectedRatherThanDropped |
| G10 | SOCKS入站独立鉴权有上游消费者，但当前mihomo-wrapper/core.go:95-100拒绝所有cfg.Listeners。单一全局socks-port+authentication不能表达公开/内部两个监听与独立凭据 | 生成器输出有上游消费的listeners草稿；wrapper尚不接受，禁止宣称可运行。需另批准wrapper最小放行与监听启动失败检测；本步不修改wrapper | authAndUdpListenersIndependent；仅配置单测，集成未验证 |
| G11 | 每节点流量：tunnel/statistic/tracker.go:27-31有Chain与累计数，但wrapper StatsJSON只有会话总量。mieru MTU不在MieruOption（mieru.go:132-145），dual专用shim才应用 | MTU字段显式拒绝；每节点聚合不能伪造为session总量。方案：wrapper按outbound聚合并保留结束连接累计，单独实现/测试。两项FEATURE_MAP“需内核补丁”不变 | mieruMtuRejected；聚合未实现，本步无stats输出 |
| G12 | forced dialer：base.go:209-215→byname.go:22-45→proxydialer.go:24-48,61-68直接调proxy，源码有强制前置路径；私网目标实际回归未做。测速/公网IP依赖可用SOCKS与实际HTTP请求；mieru protobuf Normalize接口尚未迁移 | dialer-proxy顺序已生成，不改“需补丁”分类也不宣布关闭；私网夹具/统计shim/测速/导入归后续批准工作。不能拿TCP握手冒充测速成功 | chainOrderAndDialersPreserved；运行时均未验证 |
| G13 | DNS/IPv6与geodata：有真实配置消费，但fake-IP回收、TUN两入口、Xray与mihomo geo资源同版本格式、DNS默认域策略差异尚无运行时证明；app包过滤继续由VpnService处理 | 配置与单测已落，设备验证按第4步；当前不能声称DNS/IPv6/分应用等价。DNS关闭仍输出redir-host + 指定直连nameserver，需对照双核 | 三DNS模式 / ipv6SwitchControlsDnsAndTun；设备未验证 |
| G14 | 其他嵌套组合语义：packet-encoding会覆盖xudp/packet-addr，Hy1 obfs-protocol覆盖protocol、auth覆盖auth-str、up/down-speed覆盖up/down；Hy2 gecko/salamander参数有分支；Sudoku httpmask嵌套覆盖扁平mask选项；TLSMirror/AnyTLS安全包装互斥及XHTTP下载TLS字段分支 | 消费入口不等于并存时都消费；冲突/覆盖组合应拒绝，完整键集继续保留审计，尚未批准的组合进入集中护栏，不推定等价 | 见S/B条件护栏；packetEncodingConflictRejected / rawHy1AuthConflictRejected / rawHy2InactiveObfsSizeRejected / sudokuNestedAndFlatMaskConflictRejected / rawWgPeersAndTopKeyConflictRejected / xhttpDisabledDownloadTlsFieldsRejected；仍不声称所有运行时组合已验 |

## 6. FEATURE_MAP 逐项落点对照

FEATURE_MAP 分类不变。协议14项全部有endpoint生成测试，raw字段按第4节；以下是其余每条“需转换”、补丁与外层项对照。运行时验证不在本步。

| FEATURE_MAP 条目 | 原类别/状态 | 落点 | mihomo 消费入口（文件:行） | 对应测试 |
| --- | --- | --- | --- | --- |
| VMess/VLESS/Trojan/SS + 其余10协议 | 原生支持；mieru原表需补丁 | B.native/extended；第4节 | 各协议option/transport，第4节 | allFourteenProtocolEndpointsMap |
| TCP/RAW无伪装 | 需转换 | B.native/normalizeTransport | vmess.go:175；vless.go:156；trojan.go:79 | rawTcpNormalized |
| TCP HTTP头 | 需转换/需补丁或无等价，未改判 | B拒绝；G01 | http.go:54-96仅候选 | tcpHttpHeaderRejected |
| WS | 需转换 | B.native + S.WSOptions | vmess.go:179-193；vless.go:160-174；trojan.go:82-102 | ordinaryWsHeadersPreserved |
| gRPC单流/multi | 需转换 | B.native + S.GrpcOptions | vmess.go:609-649；vless.go:574-612；trojan.go:385-398 | grpcAllOptionsPreserved |
| HTTP Upgrade/fast-open | 需转换（仅批准协议范围） | B.normalizeTransport；raw两flag保留 | vmess.go:184-185；websocket.go:446-458；SS:347-348 | vmessUpgradeConversion / vlessUpgradeConversion / trojanUpgradeConversion / earlyConnRequiresBothFlags / ssUpgradeOnlyThroughV2rayPlugin |
| VLESS xhttp/非VLESS xhttp | 原生需转换/无等价 | B.native + S递归；非VLESS拒绝 | vless.go:630-915 | xhttpNestedOptionsPreserved / nonVlessXhttpRejected |
| TLS/Reality/spiderX | 需转换；已批准近似边界 | B.native/spider/checkGaps | 第1/4节；G02/G03 | tlsFieldsHaveDistinctDestinations / realityKeysAndVisionPreserved / 四spider测试 |
| native分享链接 | 需转换 | 现有ShareLinkParser→ServerProfile→B | B.native→第4节 | 既有ShareLinkParserTest + allFourteenProtocolEndpointsMap |
| 扩展分享链接 | 需转换 | 现有ExtendedShareLinks→模型→B.extended | 第4节 | 既有ShareLinkParserTest/扩展协议用例 + hysteriaAliasesAndObfsConvert / anytlsAliasesAndNestedEchMap |
| mieru protobuf链接 | 需内核补丁 | 本步未实现归一化wrapper接口 G12 | 无single入口，dual有NormalizeMieruLink | 明确未验，不声称闭环 |
| Clash节点导入 | 需转换 | ClashYamlParser.config→B.outbound→S | 第4节逐字段 | grpcAllOptionsPreserved / xhttpNestedOptionsPreserved / unknownTopOptionRejectedWithoutSecretLeak |
| Clash规则导入 | 需转换 | 现有parseRules→RoutingRule→B.rule | rules/parser.go:18-94 | geoAndDomainPrefixesMapped / ruleFieldsOrWithinAndAcross |
| 节点编辑/校验 | 需转换，外层保留 | 现有NodeEditing；B纯函数不回写 | 无核心消费；模型投影见第2节 | 既有RoutingAndEditingTest / metadataDoesNotMutatePersistedProfile |
| RULE/GLOBAL/DIRECT | 需转换 | B.rules/rule | rules/parser.go:84-94 | globalAndDirectModes / lanToggleAndRuleOrderPreserved |
| domain/full/domain/keyword/regexp/geosite | 需转换；缺口G08 | B.rule | rules/parser.go:18-29 | geoAndDomainPrefixesMapped / unprefixedDomainIsPending |
| geoip/private/IP/CIDR/source | 需转换 | B.rule | rules/parser.go:30-44 | privateCidrListExpanded / customPrivateGeoRuleKeepsGeoSemantics / ruleFieldsOrWithinAndAcross |
| source/destination port + network + 多字段 | 需转换 | B.rule AND/OR | rules/parser.go:50-53,70-71,84-87 | ruleFieldsOrWithinAndAcross |
| sniffed protocol | 需补丁或无等价 | G09拒绝；不改旧嗅探 | 无PROTOCOL规则 | protocolRuleIsRejectedRatherThanDropped |
| direct DNS/fake-IP/DNS over proxy/geosite DNS | 需转换 | B.dns；资源与运行时G13 | config/config.go:1210-1295,1339-1410,1433-1508 | 三DNS模式 / dnsDirectDomainPolicyConverted / ipv6DnsAddressBracketed |
| 前置/当前/落地链 | 需转换 | B.chain + dialer-proxy | base.go:209-215；proxydialer/byname.go:22-45 | chainOrderAndDialersPreserved / missingChainNodeRejected / duplicateChainNodeRejected |
| 链内私网forced dialer | 需内核补丁（未自行关闭） | G12有源码路径，未runtime证明 | proxydialer.go:24-48,61-68 | 仅配置链测试；私网夹具未验 |
| 测速/公网IP | 需转换；本步仅配置 | B includeInbound/listeners，后续核心接口G10/G12 | SOCKS消费有，wrapper未放行 | measurementOmitsListeners / authAndUdpListenersIndependent；实际HTTP未验 |
| 每节点累计流量/mieru MTU | 需内核补丁 | G11；不伪造、不丢MTU | 统计tracker.go:27-31；MTU无普通option入口 | mieruMtuRejected；统计补丁未实现 |
| 分组排序/QR/订阅/备份/UI/日志/错误/状态机 | 外层原生保留 | main零diff，原有实现保留 | 无核心配置消费，见第2/3节归属 | 全部共享129项单测；错误生成器专用拒绝测试 |
| 关于/按flavor更新 | phase3需转换 | 未做，按原范围排除 | 不适用（外层） | 未验，本轮不进入phase3 |

## 7. 验证与未验证边界

- 本步仅生成与拒绝测试，不把生成成功当协议建连成功。HTTP Upgrade真实服务端、协议矩阵、DNS/IPv6/分应用、forced-private、流量聚合、mieru MTU/protobuf、双向覆盖与数据保留均未在本步设备验证；按原后续步骤执行。
- Main/dual/Go wrapper未改；不存在新增CORE_VARIANT分支；签名文件、密码与上游源码未触碰。
- 新鲜测试命令与分flavor/模块计数见 SINGLE_MAPPING_VALIDATION.md；报告与日志保存在 test-reports/phase2-step2-batch-20261028，不批量提交含设备历史的整个目录。

## 8. 第 3 步更新（不改冻结分类）

G02/G03/G05/G06 保持逐项显式拒绝，G04 已撤销通用 sni→证书名顶替；设计与命运表见 SINGLE_BRIDGE_STEP3.md。G10 只放行 App 精确双 SOCKS 入站形态，绑定失败向调用者传播。G11 新增 single shim：mieru mtu 在 mihomo-wrapper/libmihomo/mieru.go 的 configureMieruWithDialer→官方 appctl.URLToClientProfile→ClientConfig 消费；MihomoOptionSchema 中的 mtu 是 shim 扩展，非上游消费入口。MihomoConfigBuilder.outbound/checkGaps 对 0 或1280..1500 放行，非法值拒绝；测试 mieruMtuUsesOfficialClientShim/mieruInvalidMtuRejected 和 Go TestMieruMtuProfileAndCleanup/TestMieruMtuInvalidAndForcedDialerRetained。每节点流量见 traffic.go，按节点代理 I/O 累计，StatsJSON 选定显式 iceflow-selected-outbound 节点，QueryStats 输出各节点；不把链路标签相加，不计 DIRECT/REJECT。G12 forced-private 的回环代理夹具已进入 TestForcedDialerPrivateAddressFixture；该单测不替代第4步真实网络矩阵。
