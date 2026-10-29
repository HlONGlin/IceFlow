# Step 4 gate corrections

Date: 2026-10-29
Worktree: E:/IceFlow-core-variants
Branch: feat/core-variants

This document is the current gate-0 correction ledger. It supersedes the older
conservative wording that described G02, G05, or G06 as whole-package rejection.

## Three-part rule

1. dual consumes and single has a consumer: map normally.
2. dual consumes and single has no consumer: reject only a non-empty,
   non-default value, using CONFIGURATION_OR_SUBSCRIPTION and naming the field;
   allow the default.
3. dual also does not consume: preserve dual behavior, register the gap in
   FEATURE_MAP, do not reject, and do not claim semantic preservation.

For values fixed by the core, an input equal to the fixed value is equivalent
and is allowed; a differing non-empty value follows rule 2.

## G02

The pinned mihomo consumers are:

- VMess/VLESS/Trojan gRPC force ALPN h2 in
  adapter/outbound/vmess.go:628, adapter/outbound/vless.go:593, and
  adapter/outbound/trojan.go:377.
- VMess/VLESS ordinary TLS WebSocket force ALPN http/1.1 in
  adapter/outbound/vmess.go:205-239 and adapter/outbound/vless.go:185-220.
- VMess/VLESS/Trojan WebSocket client fingerprints reach
  transport/vmess/websocket.go:365-375 through the adapter options.

The mapper therefore allows empty ALPN or the exact fixed list, rejects a
different non-empty list, and maps ordinary TLS WebSocket client fingerprints.
For Reality and no-TLS branches where dual emits fields that the pinned
adapter also ignores, single keeps the same output and makes no consumption
claim. HTTP/SOCKS5 ALPN remains the registered dual-side existing gap.

## G05

The pinned VLESS adapter declares ws-headers at vless.go:83, but its consumer
reads nested ws-opts headers at vless.go:172-175; the shared WebSocket transport
uses Host at transport/vmess/websocket.go:396-400. A VLESS WebSocket object
containing only Host is converted to ws-opts.headers.Host. Other custom headers,
invalid values, or conflicting nested Host values are explicit configuration
errors.

WireGuard's implicit peer path uses fixed default allowed routes in
adapter/outbound/wireguard.go:760-773, while explicit peer allowed-ips are
consumed at :731-732. A non-empty top-level allowed-ips list is therefore
converted to one explicit peer carrying the endpoint, public key, PSK, and
allowed routes. An empty list retains the dual implicit default.

## G06

ExtendedConfigBuilder.option() uses the first non-empty key. The raw Clash path
also has deterministic dual precedence: XrayConfigBuilder.kt:64-69 passes the
raw node through the adapter parser. The mapper now preserves those outcomes
for aliases, Hysteria override fields, TUIC version selection and flags,
WireGuard address prefix removal, raw nested conflicts, and BasicOption fields
that both adapters ignore. Unknown option keys still fail explicitly.

R3 TUIC clarification: the pinned adapter's TuicOption
(adapter/outbound/tuic.go:36-69) declares no `version` field; v4-vs-v5
selection is derived from token presence (tuic.go:183, tuic.go:271), so no
`tuic.version` input exists and TUIC is never rejected for it. A raw Clash
TUIC node carrying a `version` key fails the pinned-option schema as
`tuic.version` under CONFIGURATION_OR_SUBSCRIPTION (rule 2 with an explicit
non-default value), while nodes without the key map both flavors normally.

## Tests

The fresh full Kotlin run used:

    ./gradlew :app:testDualDebugUnitTest :app:testSingleDebugUnitTest --rerun-tasks --no-daemon --console=plain

Results: dual 129/129, single 221/221. The added tests cover fixed-ALPN
equivalence and rejection, WebSocket fingerprint consumption, Host-only header
conversion and custom-header rejection, WireGuard peer conversion, dual alias
precedence, TUIC output, raw Clash precedence, and shared ignored TLS fields.
