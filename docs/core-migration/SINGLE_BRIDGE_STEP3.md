# Phase 2 step 3: single runtime integration

## Step 2 disposition, before runtime changes

- G04: withdraw the SNI/name-cert-verify substitution. Model SOCKS sni is explicitly rejected before calling ExtendedConfigBuilder; single must not advertise certificate-name verification as SNI preservation. Raw name-cert-verify retains its independently audited consumer. No dual change.
- G02: fixed WS/gRPC ALPN and ordinary WS fingerprint gaps remain explicit CONFIGURATION_OR_SUBSCRIPTION rejections. Other audited TLS branches retain mapping. No automatic replacement with fixed values.
- G03: WS Reality, Reality user ALPN and Reality allowInsecure=true remain explicit configuration errors; approved spiderX initial-root approximation remains limited to the documented initial path.
- G05: unconsumed VLESS ws-headers and WireGuard top-level allowed-ips remain rejected; reserved retains its proven consumer. No unapproved peer rewrite.
- G06: unread aliases, conflicting sources, WG multi-address/prefix loss and TUIC version ambiguities remain explicit configuration errors. BasicOption tfo/mptcp without forwarding remain rejected. No field is silently discarded.

These are explicit restrictions, not claims of feature equivalence or changes to FEATURE_MAP categories. The detailed pinned source evidence remains in SINGLE_MAPPING_AUDIT section 5.

## G10 exact app inbound contract

Only two optional SOCKS listeners are generated: tun-in and socks-in. Both bind 127.0.0.1; port is an integer 1..65535; udp is a Boolean; users is an optional array of username/password objects. tun-in uses the service-generated private credentials and UDP=true. socks-in uses AppSettings.socksUdp and optional paired public credentials. Unknown names, fields, non-loopback binds, duplicate names/ports and malformed credentials must fail validation. Wrapper starts listeners itself and propagates bind failures, rather than relying on upstream PatchInboundListeners which logs failures.

## G11 implementation design

Counters are owned by a per-engine accounting shim around configured node proxy connections, not sampled from the live tracker registry (short-lived closed trackers disappear). Each node tag has cumulative upload/download counters retained after connection close. Direct/reject proxies are never wrapped. Reads are snapshots and never add a previously observed value. TCP and UDP interception must preserve metadata while preventing optimized unwrap/copy paths from bypassing accounting. Nested dialer-proxy traffic belongs to its own node tag; the app session totals use the explicit iceflow-selected-outbound tag only (which may be chain-1 when a landing node exists), never sum all chain tags (which would count the same payload repeatedly). queryStats exposes individual node tags. The existing service persists the selected node only; it is not changed to persist every chain member.

Mieru explicit MTU uses a single-only adaptation of the existing official-client shim: create an official mieru ClientProfile with mtu, preserve node name and forced upstream dialer, replace that parsed proxy before application, and close client plus original proxy independently. Upstream sources and dual shim remain unchanged. Tests cover MTU bounds/profile propagation and chained dialer behavior.

## Lifecycle and ownership

Start parses/applies configuration and starts validated listeners before service TUN establishment. AttachTun duplicates the borrowed original descriptor and owns only that duplicate. StopTun closes the duplicate/stack but preserves the core for the existing fast-reconnect path; Stop also closes listeners, sessions and proxies. The service alone establishes/closes its original fd on every path. Primary startup errors precede appended cleanup diagnostics. Readiness requires the pinned wrapper API, native loading and supported lifecycle surface; running/healthy additionally require successful config/listener/TUN startup.

## Scope and verification gates

No main or dual source changes are planned. Config, wrapper, single bridge and their tests are separate changes. Fresh dual/single Kotlin, wrapper Go and Xray Go counts precede signing/device work. G12 private-address forced-dialer fixture regression is mandatory in the wrapper test list. Device scope is basic single x86_64 only; real HTTP Upgrade server and full DNS/IPv6/protocol matrices remain step 4.

G11 ownership detail: the session baseline resets only after a successful TUN handoff, matching the unchanged service persistedUp/persistedDown reset on fast reconnect. queryStats remains cumulative for the prepared core lifetime. The wrapper-only iceflow-selected-outbound metadata is validated against configured node tags and has no upstream consumer claim.
