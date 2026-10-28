package com.iceflow.data

import kotlinx.serialization.json.*

/** Pinned v1.19.32 proxy option schema; see docs/core-migration/SINGLE_MAPPING_AUDIT.md. */
internal object MihomoOptionSchema {
    private val protocolTypes = mapOf(
        "vmess" to "VmessOption",
        "vless" to "VlessOption",
        "trojan" to "TrojanOption",
        "ss" to "ShadowSocksOption",
        "hysteria" to "HysteriaOption",
        "hysteria2" to "Hysteria2Option",
        "tuic" to "TuicOption",
        "wireguard" to "WireGuardOption",
        "ssr" to "ShadowSocksROption",
        "http" to "HttpOption",
        "socks5" to "Socks5Option",
        "mieru" to "MieruOption",
        "anytls" to "AnyTLSOption",
        "sudoku" to "SudokuOption",
    )
    private val fields: Map<String, Map<String, String>> = mapOf(
        "V2rayPluginOptions" to mapOf(
            "mode" to "string", "host" to "string", "path" to "string", "tls" to "bool",
            "ech-opts" to "ECHOptions", "fingerprint" to "string", "certificate" to "string", "private-key" to "string",
            "headers" to "map[string]string", "skip-cert-verify" to "bool", "name-cert-verify" to "string",
            "mux" to "bool", "v2ray-http-upgrade" to "bool", "v2ray-http-upgrade-fast-open" to "bool",
        ), // adapter/outbound/shadowsocks.go:63-77,333-363
        "AmneziaWGOption" to mapOf(
            "version" to "int", // adapter/outbound/wireguard.go:103
            "jc" to "int", // adapter/outbound/wireguard.go:105
            "jmin" to "int", // adapter/outbound/wireguard.go:106
            "jmax" to "int", // adapter/outbound/wireguard.go:107
            "s1" to "int", // adapter/outbound/wireguard.go:108
            "s2" to "int", // adapter/outbound/wireguard.go:109
            "s3" to "int", // adapter/outbound/wireguard.go:110
            "s4" to "int", // adapter/outbound/wireguard.go:111
            "h1" to "string", // adapter/outbound/wireguard.go:115
            "h2" to "string", // adapter/outbound/wireguard.go:116
            "h3" to "string", // adapter/outbound/wireguard.go:117
            "h4" to "string", // adapter/outbound/wireguard.go:118
            "i1" to "string", // adapter/outbound/wireguard.go:120
            "i2" to "string", // adapter/outbound/wireguard.go:121
            "i3" to "string", // adapter/outbound/wireguard.go:122
            "i4" to "string", // adapter/outbound/wireguard.go:123
            "i5" to "string", // adapter/outbound/wireguard.go:124
            "j1" to "string", // adapter/outbound/wireguard.go:126
            "j2" to "string", // adapter/outbound/wireguard.go:127
            "j3" to "string", // adapter/outbound/wireguard.go:128
            "itime" to "int64", // adapter/outbound/wireguard.go:129
            "header-protection-key" to "string", // adapter/outbound/wireguard.go:132
            "content-padding-addition" to "string", // adapter/outbound/wireguard.go:133
            "rekey-after-time" to "string", // adapter/outbound/wireguard.go:134
            "rekey-timeout" to "string", // adapter/outbound/wireguard.go:135
            "reject-after-time" to "string", // adapter/outbound/wireguard.go:136
            "keepalive-timeout" to "string", // adapter/outbound/wireguard.go:137
            "max-handshake-attempts" to "string", // adapter/outbound/wireguard.go:138
            "random-trailers" to "bool", // adapter/outbound/wireguard.go:139
            "disable-cookies" to "bool", // adapter/outbound/wireguard.go:140
        ),
        "AnyTLSOption" to mapOf(
            "name" to "string", // adapter/outbound/anytls.go:29
            "server" to "string", // adapter/outbound/anytls.go:30
            "port" to "int", // adapter/outbound/anytls.go:31
            "password" to "string", // adapter/outbound/anytls.go:32
            "alpn" to "[]string", // adapter/outbound/anytls.go:33
            "sni" to "string", // adapter/outbound/anytls.go:34
            "ech-opts" to "ECHOptions", // adapter/outbound/anytls.go:35
            "shadow-tls-opts" to "ShadowTLSOptions", // adapter/outbound/anytls.go:36
            "restls-opts" to "RestlsOptions", // adapter/outbound/anytls.go:37
            "jls-opts" to "JLSOptions", // adapter/outbound/anytls.go:38
            "client-fingerprint" to "string", // adapter/outbound/anytls.go:39
            "skip-cert-verify" to "bool", // adapter/outbound/anytls.go:40
            "name-cert-verify" to "string", // adapter/outbound/anytls.go:41
            "fingerprint" to "string", // adapter/outbound/anytls.go:42
            "certificate" to "string", // adapter/outbound/anytls.go:43
            "private-key" to "string", // adapter/outbound/anytls.go:44
            "udp" to "bool", // adapter/outbound/anytls.go:45
            "client-metadata" to "string", // adapter/outbound/anytls.go:46
            "idle-session-check-interval" to "int", // adapter/outbound/anytls.go:47
            "idle-session-timeout" to "int", // adapter/outbound/anytls.go:48
            "min-idle-session" to "int", // adapter/outbound/anytls.go:49
            "disable-reuse" to "bool", // adapter/outbound/anytls.go:50
            "tfo" to "bool", // adapter/outbound/base.go:194
            "mptcp" to "bool", // adapter/outbound/base.go:195
            "interface-name" to "string", // adapter/outbound/base.go:196
            "routing-mark" to "int", // adapter/outbound/base.go:197
            "ip-version" to "C.DNSPrefer", // adapter/outbound/base.go:198
            "dialer-proxy" to "string", // adapter/outbound/base.go:199
        ),
        "ECHOptions" to mapOf(
            "enable" to "bool", // adapter/outbound/ech.go:13
            "config" to "string", // adapter/outbound/ech.go:14
            "query-server-name" to "string", // adapter/outbound/ech.go:16
        ),
        "GrpcOptions" to mapOf(
            "grpc-service-name" to "string", // adapter/outbound/vmess.go:157
            "grpc-user-agent" to "string", // adapter/outbound/vmess.go:158
            "ping-interval" to "int", // adapter/outbound/vmess.go:159
            "max-connections" to "int", // adapter/outbound/vmess.go:160
            "min-streams" to "int", // adapter/outbound/vmess.go:161
            "max-streams" to "int", // adapter/outbound/vmess.go:162
        ),
        "HTTP2Options" to mapOf(
            "host" to "[]string", // adapter/outbound/vmess.go:152
            "path" to "string", // adapter/outbound/vmess.go:153
        ),
        "HTTPOptions" to mapOf(
            "method" to "string", // adapter/outbound/vmess.go:146
            "path" to "[]string", // adapter/outbound/vmess.go:147
            "headers" to "map[string][]string", // adapter/outbound/vmess.go:148
        ),
        "HttpOption" to mapOf(
            "name" to "string", // adapter/outbound/http.go:30
            "server" to "string", // adapter/outbound/http.go:31
            "port" to "int", // adapter/outbound/http.go:32
            "username" to "string", // adapter/outbound/http.go:33
            "password" to "string", // adapter/outbound/http.go:34
            "tls" to "bool", // adapter/outbound/http.go:35
            "sni" to "string", // adapter/outbound/http.go:36
            "skip-cert-verify" to "bool", // adapter/outbound/http.go:37
            "name-cert-verify" to "string", // adapter/outbound/http.go:38
            "fingerprint" to "string", // adapter/outbound/http.go:39
            "certificate" to "string", // adapter/outbound/http.go:40
            "private-key" to "string", // adapter/outbound/http.go:41
            "headers" to "map[string]string", // adapter/outbound/http.go:42
            "tfo" to "bool", // adapter/outbound/base.go:194
            "mptcp" to "bool", // adapter/outbound/base.go:195
            "interface-name" to "string", // adapter/outbound/base.go:196
            "routing-mark" to "int", // adapter/outbound/base.go:197
            "ip-version" to "C.DNSPrefer", // adapter/outbound/base.go:198
            "dialer-proxy" to "string", // adapter/outbound/base.go:199
        ),
        "Hysteria2Option" to mapOf(
            "name" to "string", // adapter/outbound/hysteria2.go:41
            "server" to "string", // adapter/outbound/hysteria2.go:42
            "port" to "int", // adapter/outbound/hysteria2.go:43
            "ports" to "string", // adapter/outbound/hysteria2.go:44
            "hop-interval" to "string", // adapter/outbound/hysteria2.go:45
            "up" to "string", // adapter/outbound/hysteria2.go:46
            "down" to "string", // adapter/outbound/hysteria2.go:47
            "password" to "string", // adapter/outbound/hysteria2.go:48
            "obfs" to "string", // adapter/outbound/hysteria2.go:49
            "obfs-password" to "string", // adapter/outbound/hysteria2.go:50
            "obfs-min-packet-size" to "int", // adapter/outbound/hysteria2.go:51
            "obfs-max-packet-size" to "int", // adapter/outbound/hysteria2.go:52
            "sni" to "string", // adapter/outbound/hysteria2.go:53
            "ech-opts" to "ECHOptions", // adapter/outbound/hysteria2.go:54
            "skip-cert-verify" to "bool", // adapter/outbound/hysteria2.go:55
            "name-cert-verify" to "string", // adapter/outbound/hysteria2.go:56
            "fingerprint" to "string", // adapter/outbound/hysteria2.go:57
            "certificate" to "string", // adapter/outbound/hysteria2.go:58
            "private-key" to "string", // adapter/outbound/hysteria2.go:59
            "alpn" to "[]string", // adapter/outbound/hysteria2.go:60
            "cwnd" to "int", // adapter/outbound/hysteria2.go:61
            "bbr-profile" to "string", // adapter/outbound/hysteria2.go:62
            "udp-mtu" to "int", // adapter/outbound/hysteria2.go:63
            "handshake-timeout" to "int", // adapter/outbound/hysteria2.go:64
            "realm-opts" to "Hysteria2RealmOption", // adapter/outbound/hysteria2.go:66
            "initial-stream-receive-window" to "uint64", // adapter/outbound/hysteria2.go:69
            "max-stream-receive-window" to "uint64", // adapter/outbound/hysteria2.go:70
            "initial-connection-receive-window" to "uint64", // adapter/outbound/hysteria2.go:71
            "max-connection-receive-window" to "uint64", // adapter/outbound/hysteria2.go:72
            "tfo" to "bool", // adapter/outbound/base.go:194
            "mptcp" to "bool", // adapter/outbound/base.go:195
            "interface-name" to "string", // adapter/outbound/base.go:196
            "routing-mark" to "int", // adapter/outbound/base.go:197
            "ip-version" to "C.DNSPrefer", // adapter/outbound/base.go:198
            "dialer-proxy" to "string", // adapter/outbound/base.go:199
        ),
        "Hysteria2RealmOption" to mapOf(
            "enable" to "bool", // adapter/outbound/hysteria2.go:76
            "server-url" to "string", // adapter/outbound/hysteria2.go:77
            "token" to "string", // adapter/outbound/hysteria2.go:78
            "realm-id" to "string", // adapter/outbound/hysteria2.go:79
            "stun-servers" to "[]string", // adapter/outbound/hysteria2.go:80
            "sni" to "string", // adapter/outbound/hysteria2.go:83
            "skip-cert-verify" to "bool", // adapter/outbound/hysteria2.go:84
            "name-cert-verify" to "string", // adapter/outbound/hysteria2.go:85
            "fingerprint" to "string", // adapter/outbound/hysteria2.go:86
            "certificate" to "string", // adapter/outbound/hysteria2.go:87
            "private-key" to "string", // adapter/outbound/hysteria2.go:88
            "alpn" to "[]string", // adapter/outbound/hysteria2.go:89
        ),
        "HysteriaOption" to mapOf(
            "name" to "string", // adapter/outbound/hysteria.go:103
            "server" to "string", // adapter/outbound/hysteria.go:104
            "port" to "int", // adapter/outbound/hysteria.go:105
            "ports" to "string", // adapter/outbound/hysteria.go:106
            "protocol" to "string", // adapter/outbound/hysteria.go:107
            "obfs-protocol" to "string", // adapter/outbound/hysteria.go:108
            "up" to "string", // adapter/outbound/hysteria.go:109
            "up-speed" to "int", // adapter/outbound/hysteria.go:110
            "down" to "string", // adapter/outbound/hysteria.go:111
            "down-speed" to "int", // adapter/outbound/hysteria.go:112
            "auth" to "string", // adapter/outbound/hysteria.go:113
            "auth-str" to "string", // adapter/outbound/hysteria.go:114
            "obfs" to "string", // adapter/outbound/hysteria.go:115
            "sni" to "string", // adapter/outbound/hysteria.go:116
            "ech-opts" to "ECHOptions", // adapter/outbound/hysteria.go:117
            "skip-cert-verify" to "bool", // adapter/outbound/hysteria.go:118
            "name-cert-verify" to "string", // adapter/outbound/hysteria.go:119
            "fingerprint" to "string", // adapter/outbound/hysteria.go:120
            "certificate" to "string", // adapter/outbound/hysteria.go:121
            "private-key" to "string", // adapter/outbound/hysteria.go:122
            "alpn" to "[]string", // adapter/outbound/hysteria.go:123
            "recv-window-conn" to "int", // adapter/outbound/hysteria.go:124
            "recv-window" to "int", // adapter/outbound/hysteria.go:125
            "disable-mtu-discovery" to "bool", // adapter/outbound/hysteria.go:126
            "fast-open" to "bool", // adapter/outbound/hysteria.go:127
            "hop-interval" to "int", // adapter/outbound/hysteria.go:128
            "tfo" to "bool", // adapter/outbound/base.go:194
            "mptcp" to "bool", // adapter/outbound/base.go:195
            "interface-name" to "string", // adapter/outbound/base.go:196
            "routing-mark" to "int", // adapter/outbound/base.go:197
            "ip-version" to "C.DNSPrefer", // adapter/outbound/base.go:198
            "dialer-proxy" to "string", // adapter/outbound/base.go:199
        ),
        "IPStackOption" to mapOf(
            "mode" to "string", // adapter/outbound/wireguard.go:144
            "congestion-controller" to "string", // adapter/outbound/wireguard.go:145
        ),
        "JLSOptions" to mapOf(
            "username" to "string", // adapter/outbound/jls.go:6
            "password" to "string", // adapter/outbound/jls.go:7
        ),
        "MKCPOptions" to mapOf(
            "mtu" to "uint32", // adapter/outbound/vmess.go:92
            "tti" to "uint32", // adapter/outbound/vmess.go:93
            "uplink-capacity" to "uint32", // adapter/outbound/vmess.go:94
            "downlink-capacity" to "uint32", // adapter/outbound/vmess.go:95
            "congestion" to "bool", // adapter/outbound/vmess.go:96
            "write-buffer" to "uint32", // adapter/outbound/vmess.go:97
            "read-buffer" to "uint32", // adapter/outbound/vmess.go:98
            "seed" to "string", // adapter/outbound/vmess.go:99
            "header" to "string", // adapter/outbound/vmess.go:100
        ),
        "MekyaOptions" to mapOf(
            "url" to "string", // adapter/outbound/vmess.go:118
            "h2-pool-size" to "int", // adapter/outbound/vmess.go:119
            "max-write-delay" to "int", // adapter/outbound/vmess.go:120
            "max-request-size" to "int", // adapter/outbound/vmess.go:121
            "polling-interval-initial" to "int", // adapter/outbound/vmess.go:122
            "max-write-size" to "int", // adapter/outbound/vmess.go:123
            "max-write-duration-ms" to "int", // adapter/outbound/vmess.go:124
            "max-simultaneous-write-connection" to "int", // adapter/outbound/vmess.go:125
            "packet-writing-buffer" to "int", // adapter/outbound/vmess.go:126
            "kcp" to "MKCPOptions", // adapter/outbound/vmess.go:127
        ),
        "MieruOption" to mapOf(
            "name" to "string", // adapter/outbound/mieru.go:32
            "server" to "string", // adapter/outbound/mieru.go:33
            "port" to "int", // adapter/outbound/mieru.go:34
            "port-range" to "string", // adapter/outbound/mieru.go:35
            "transport" to "string", // adapter/outbound/mieru.go:36
            "udp" to "bool", // adapter/outbound/mieru.go:37
            "username" to "string", // adapter/outbound/mieru.go:38
            "password" to "string", // adapter/outbound/mieru.go:39
            "multiplexing" to "string", // adapter/outbound/mieru.go:40
            "handshake-mode" to "string", // adapter/outbound/mieru.go:41
            "traffic-pattern" to "string", // adapter/outbound/mieru.go:42
            "tfo" to "bool", // adapter/outbound/base.go:194
            "mptcp" to "bool", // adapter/outbound/base.go:195
            "interface-name" to "string", // adapter/outbound/base.go:196
            "routing-mark" to "int", // adapter/outbound/base.go:197
            "ip-version" to "C.DNSPrefer", // adapter/outbound/base.go:198
            "dialer-proxy" to "string", // adapter/outbound/base.go:199
        ),
        "RealityOptions" to mapOf(
            "public-key" to "string", // adapter/outbound/reality.go:14
            "short-id" to "string", // adapter/outbound/reality.go:15
            "support-x25519mlkem768" to "bool", // adapter/outbound/reality.go:17
        ),
        "RestlsOptions" to mapOf(
            "password" to "string", // adapter/outbound/restls.go:6
            "version-hint" to "string", // adapter/outbound/restls.go:7
            "restls-script" to "string", // adapter/outbound/restls.go:8
        ),
        "ShadowSocksOption" to mapOf(
            "name" to "string", // adapter/outbound/shadowsocks.go:45
            "server" to "string", // adapter/outbound/shadowsocks.go:46
            "port" to "int", // adapter/outbound/shadowsocks.go:47
            "password" to "string", // adapter/outbound/shadowsocks.go:48
            "cipher" to "string", // adapter/outbound/shadowsocks.go:49
            "udp" to "bool", // adapter/outbound/shadowsocks.go:50
            "plugin" to "string", // adapter/outbound/shadowsocks.go:51
            "plugin-opts" to "map[string]any", // adapter/outbound/shadowsocks.go:52
            "udp-over-tcp" to "bool", // adapter/outbound/shadowsocks.go:53
            "udp-over-tcp-version" to "int", // adapter/outbound/shadowsocks.go:54
            "client-fingerprint" to "string", // adapter/outbound/shadowsocks.go:55
            "tfo" to "bool", // adapter/outbound/base.go:194
            "mptcp" to "bool", // adapter/outbound/base.go:195
            "interface-name" to "string", // adapter/outbound/base.go:196
            "routing-mark" to "int", // adapter/outbound/base.go:197
            "ip-version" to "C.DNSPrefer", // adapter/outbound/base.go:198
            "dialer-proxy" to "string", // adapter/outbound/base.go:199
        ),
        "ShadowSocksROption" to mapOf(
            "name" to "string", // adapter/outbound/shadowsocksr.go:30
            "server" to "string", // adapter/outbound/shadowsocksr.go:31
            "port" to "int", // adapter/outbound/shadowsocksr.go:32
            "password" to "string", // adapter/outbound/shadowsocksr.go:33
            "cipher" to "string", // adapter/outbound/shadowsocksr.go:34
            "obfs" to "string", // adapter/outbound/shadowsocksr.go:35
            "obfs-param" to "string", // adapter/outbound/shadowsocksr.go:36
            "protocol" to "string", // adapter/outbound/shadowsocksr.go:37
            "protocol-param" to "string", // adapter/outbound/shadowsocksr.go:38
            "udp" to "bool", // adapter/outbound/shadowsocksr.go:39
            "tfo" to "bool", // adapter/outbound/base.go:194
            "mptcp" to "bool", // adapter/outbound/base.go:195
            "interface-name" to "string", // adapter/outbound/base.go:196
            "routing-mark" to "int", // adapter/outbound/base.go:197
            "ip-version" to "C.DNSPrefer", // adapter/outbound/base.go:198
            "dialer-proxy" to "string", // adapter/outbound/base.go:199
        ),
        "ShadowTLSOptions" to mapOf(
            "password" to "string", // adapter/outbound/shadowtls.go:6
            "version" to "int", // adapter/outbound/shadowtls.go:7
        ),
        "Socks5Option" to mapOf(
            "name" to "string", // adapter/outbound/socks5.go:32
            "server" to "string", // adapter/outbound/socks5.go:33
            "port" to "int", // adapter/outbound/socks5.go:34
            "username" to "string", // adapter/outbound/socks5.go:35
            "password" to "string", // adapter/outbound/socks5.go:36
            "tls" to "bool", // adapter/outbound/socks5.go:37
            "udp" to "bool", // adapter/outbound/socks5.go:38
            "skip-cert-verify" to "bool", // adapter/outbound/socks5.go:39
            "name-cert-verify" to "string", // adapter/outbound/socks5.go:40
            "fingerprint" to "string", // adapter/outbound/socks5.go:41
            "certificate" to "string", // adapter/outbound/socks5.go:42
            "private-key" to "string", // adapter/outbound/socks5.go:43
            "tfo" to "bool", // adapter/outbound/base.go:194
            "mptcp" to "bool", // adapter/outbound/base.go:195
            "interface-name" to "string", // adapter/outbound/base.go:196
            "routing-mark" to "int", // adapter/outbound/base.go:197
            "ip-version" to "C.DNSPrefer", // adapter/outbound/base.go:198
            "dialer-proxy" to "string", // adapter/outbound/base.go:199
        ),
        "SudokuHTTPMaskOptions" to mapOf(
            "disable" to "bool", // adapter/outbound/sudoku.go:53
            "mode" to "string", // adapter/outbound/sudoku.go:54
            "tls" to "bool", // adapter/outbound/sudoku.go:55
            "host" to "string", // adapter/outbound/sudoku.go:56
            "path-root" to "string", // adapter/outbound/sudoku.go:57
            "multiplex" to "string", // adapter/outbound/sudoku.go:58
        ),
        "SudokuOption" to mapOf(
            "name" to "string", // adapter/outbound/sudoku.go:31
            "server" to "string", // adapter/outbound/sudoku.go:32
            "port" to "int", // adapter/outbound/sudoku.go:33
            "key" to "string", // adapter/outbound/sudoku.go:34
            "aead-method" to "string", // adapter/outbound/sudoku.go:35
            "padding-min" to "*int", // adapter/outbound/sudoku.go:36
            "padding-max" to "*int", // adapter/outbound/sudoku.go:37
            "table-type" to "string", // adapter/outbound/sudoku.go:38
            "enable-pure-downlink" to "*bool", // adapter/outbound/sudoku.go:39
            "http-mask" to "*bool", // adapter/outbound/sudoku.go:40
            "http-mask-mode" to "string", // adapter/outbound/sudoku.go:41
            "http-mask-tls" to "bool", // adapter/outbound/sudoku.go:42
            "http-mask-host" to "string", // adapter/outbound/sudoku.go:43
            "path-root" to "string", // adapter/outbound/sudoku.go:44
            "multiplex" to "string", // adapter/outbound/sudoku.go:45
            "http-mask-multiplex" to "string", // adapter/outbound/sudoku.go:46
            "httpmask" to "*SudokuHTTPMaskOptions", // adapter/outbound/sudoku.go:47
            "custom-table" to "string", // adapter/outbound/sudoku.go:48
            "custom-tables" to "[]string", // adapter/outbound/sudoku.go:49
            "tfo" to "bool", // adapter/outbound/base.go:194
            "mptcp" to "bool", // adapter/outbound/base.go:195
            "interface-name" to "string", // adapter/outbound/base.go:196
            "routing-mark" to "int", // adapter/outbound/base.go:197
            "ip-version" to "C.DNSPrefer", // adapter/outbound/base.go:198
            "dialer-proxy" to "string", // adapter/outbound/base.go:199
        ),
        "TLSMirrorConnectionEnrolment" to mapOf(
            "primary-ingress-outbound" to "string", // adapter/outbound/tlsmirror.go:16
            "primary-egress-outbound" to "string", // adapter/outbound/tlsmirror.go:17
        ),
        "TLSMirrorOptions" to mapOf(
            "primary-key" to "string", // adapter/outbound/tlsmirror.go:6
            "explicit-nonce-ciphersuites" to "[]uint16", // adapter/outbound/tlsmirror.go:7
            "defer-instance-derived-write-time" to "TLSMirrorTimeSpec", // adapter/outbound/tlsmirror.go:8
            "transport-layer-padding" to "TLSMirrorTransportLayerPadding", // adapter/outbound/tlsmirror.go:9
            "connection-enrolment" to "*TLSMirrorConnectionEnrolment", // adapter/outbound/tlsmirror.go:10
            "embedded-traffic-generator" to "TLSMirrorTrafficGenerator", // adapter/outbound/tlsmirror.go:11
            "sequence-watermarking-enabled" to "bool", // adapter/outbound/tlsmirror.go:12
        ),
        "TLSMirrorTimeSpec" to mapOf(
            "base-nanoseconds" to "uint64", // adapter/outbound/tlsmirror.go:31
            "uniform-random-multiplier-nanoseconds" to "uint64", // adapter/outbound/tlsmirror.go:32
        ),
        "TLSMirrorTrafficGenerator" to mapOf(
            "steps" to "[]TLSMirrorTrafficStep", // adapter/outbound/tlsmirror.go:53
        ),
        "TLSMirrorTrafficHeader" to mapOf(
            "name" to "string", // adapter/outbound/tlsmirror.go:104
            "value" to "string", // adapter/outbound/tlsmirror.go:105
            "values" to "[]string", // adapter/outbound/tlsmirror.go:106
        ),
        "TLSMirrorTrafficStep" to mapOf(
            "name" to "string", // adapter/outbound/tlsmirror.go:68
            "host" to "string", // adapter/outbound/tlsmirror.go:69
            "path" to "string", // adapter/outbound/tlsmirror.go:70
            "method" to "string", // adapter/outbound/tlsmirror.go:71
            "headers" to "[]TLSMirrorTrafficHeader", // adapter/outbound/tlsmirror.go:72
            "next-step" to "[]TLSMirrorTrafficTransferCandidate", // adapter/outbound/tlsmirror.go:73
            "connection-ready" to "bool", // adapter/outbound/tlsmirror.go:74
            "connection-recall-exit" to "bool", // adapter/outbound/tlsmirror.go:75
            "wait-time" to "TLSMirrorTimeSpec", // adapter/outbound/tlsmirror.go:76
            "h2-do-not-wait-for-download-finish" to "bool", // adapter/outbound/tlsmirror.go:77
        ),
        "TLSMirrorTrafficTransferCandidate" to mapOf(
            "weight" to "int32", // adapter/outbound/tlsmirror.go:118
            "goto-location" to "int", // adapter/outbound/tlsmirror.go:119
        ),
        "TLSMirrorTransportLayerPadding" to mapOf(
            "enabled" to "bool", // adapter/outbound/tlsmirror.go:43
        ),
        "TrojanOption" to mapOf(
            "name" to "string", // adapter/outbound/trojan.go:47
            "server" to "string", // adapter/outbound/trojan.go:48
            "port" to "int", // adapter/outbound/trojan.go:49
            "password" to "string", // adapter/outbound/trojan.go:50
            "alpn" to "[]string", // adapter/outbound/trojan.go:51
            "sni" to "string", // adapter/outbound/trojan.go:52
            "skip-cert-verify" to "bool", // adapter/outbound/trojan.go:53
            "name-cert-verify" to "string", // adapter/outbound/trojan.go:54
            "fingerprint" to "string", // adapter/outbound/trojan.go:55
            "certificate" to "string", // adapter/outbound/trojan.go:56
            "private-key" to "string", // adapter/outbound/trojan.go:57
            "udp" to "bool", // adapter/outbound/trojan.go:58
            "network" to "string", // adapter/outbound/trojan.go:59
            "ech-opts" to "ECHOptions", // adapter/outbound/trojan.go:60
            "shadow-tls-opts" to "ShadowTLSOptions", // adapter/outbound/trojan.go:61
            "restls-opts" to "RestlsOptions", // adapter/outbound/trojan.go:62
            "jls-opts" to "JLSOptions", // adapter/outbound/trojan.go:63
            "reality-opts" to "RealityOptions", // adapter/outbound/trojan.go:64
            "grpc-opts" to "GrpcOptions", // adapter/outbound/trojan.go:65
            "ws-opts" to "WSOptions", // adapter/outbound/trojan.go:66
            "ss-opts" to "TrojanSSOption", // adapter/outbound/trojan.go:67
            "client-fingerprint" to "string", // adapter/outbound/trojan.go:68
            "tfo" to "bool", // adapter/outbound/base.go:194
            "mptcp" to "bool", // adapter/outbound/base.go:195
            "interface-name" to "string", // adapter/outbound/base.go:196
            "routing-mark" to "int", // adapter/outbound/base.go:197
            "ip-version" to "C.DNSPrefer", // adapter/outbound/base.go:198
            "dialer-proxy" to "string", // adapter/outbound/base.go:199
        ),
        "TrojanSSOption" to mapOf(
            "enabled" to "bool", // adapter/outbound/trojan.go:73
            "method" to "string", // adapter/outbound/trojan.go:74
            "password" to "string", // adapter/outbound/trojan.go:75
        ),
        "TuicOption" to mapOf(
            "name" to "string", // adapter/outbound/tuic.go:36
            "server" to "string", // adapter/outbound/tuic.go:37
            "port" to "int", // adapter/outbound/tuic.go:38
            "token" to "string", // adapter/outbound/tuic.go:39
            "uuid" to "string", // adapter/outbound/tuic.go:40
            "password" to "string", // adapter/outbound/tuic.go:41
            "ip" to "string", // adapter/outbound/tuic.go:42
            "heartbeat-interval" to "int", // adapter/outbound/tuic.go:43
            "alpn" to "[]string", // adapter/outbound/tuic.go:44
            "reduce-rtt" to "bool", // adapter/outbound/tuic.go:45
            "request-timeout" to "int", // adapter/outbound/tuic.go:46
            "udp-relay-mode" to "string", // adapter/outbound/tuic.go:47
            "congestion-controller" to "string", // adapter/outbound/tuic.go:48
            "disable-sni" to "bool", // adapter/outbound/tuic.go:49
            "max-udp-relay-packet-size" to "int", // adapter/outbound/tuic.go:50
            "fast-open" to "bool", // adapter/outbound/tuic.go:52
            "max-open-streams" to "int", // adapter/outbound/tuic.go:53
            "cwnd" to "int", // adapter/outbound/tuic.go:54
            "bbr-profile" to "string", // adapter/outbound/tuic.go:55
            "skip-cert-verify" to "bool", // adapter/outbound/tuic.go:56
            "name-cert-verify" to "string", // adapter/outbound/tuic.go:57
            "fingerprint" to "string", // adapter/outbound/tuic.go:58
            "certificate" to "string", // adapter/outbound/tuic.go:59
            "private-key" to "string", // adapter/outbound/tuic.go:60
            "recv-window-conn" to "int", // adapter/outbound/tuic.go:61
            "recv-window" to "int", // adapter/outbound/tuic.go:62
            "disable-mtu-discovery" to "bool", // adapter/outbound/tuic.go:63
            "max-datagram-frame-size" to "int", // adapter/outbound/tuic.go:64
            "sni" to "string", // adapter/outbound/tuic.go:65
            "ech-opts" to "ECHOptions", // adapter/outbound/tuic.go:66
            "udp-over-stream" to "bool", // adapter/outbound/tuic.go:68
            "udp-over-stream-version" to "int", // adapter/outbound/tuic.go:69
            "tfo" to "bool", // adapter/outbound/base.go:194
            "mptcp" to "bool", // adapter/outbound/base.go:195
            "interface-name" to "string", // adapter/outbound/base.go:196
            "routing-mark" to "int", // adapter/outbound/base.go:197
            "ip-version" to "C.DNSPrefer", // adapter/outbound/base.go:198
            "dialer-proxy" to "string", // adapter/outbound/base.go:199
        ),
        "VlessOption" to mapOf(
            "name" to "string", // adapter/outbound/vless.go:60
            "server" to "string", // adapter/outbound/vless.go:61
            "port" to "int", // adapter/outbound/vless.go:62
            "uuid" to "string", // adapter/outbound/vless.go:63
            "flow" to "string", // adapter/outbound/vless.go:64
            "tls" to "bool", // adapter/outbound/vless.go:65
            "alpn" to "[]string", // adapter/outbound/vless.go:66
            "udp" to "bool", // adapter/outbound/vless.go:67
            "packet-addr" to "bool", // adapter/outbound/vless.go:68
            "xudp" to "bool", // adapter/outbound/vless.go:69
            "packet-encoding" to "string", // adapter/outbound/vless.go:70
            "encryption" to "string", // adapter/outbound/vless.go:71
            "network" to "string", // adapter/outbound/vless.go:72
            "ech-opts" to "ECHOptions", // adapter/outbound/vless.go:73
            "shadow-tls-opts" to "ShadowTLSOptions", // adapter/outbound/vless.go:74
            "restls-opts" to "RestlsOptions", // adapter/outbound/vless.go:75
            "jls-opts" to "JLSOptions", // adapter/outbound/vless.go:76
            "reality-opts" to "RealityOptions", // adapter/outbound/vless.go:77
            "http-opts" to "HTTPOptions", // adapter/outbound/vless.go:78
            "h2-opts" to "HTTP2Options", // adapter/outbound/vless.go:79
            "grpc-opts" to "GrpcOptions", // adapter/outbound/vless.go:80
            "ws-opts" to "WSOptions", // adapter/outbound/vless.go:81
            "xhttp-opts" to "XHTTPOptions", // adapter/outbound/vless.go:82
            "ws-headers" to "map[string]string", // adapter/outbound/vless.go:83
            "skip-cert-verify" to "bool", // adapter/outbound/vless.go:84
            "name-cert-verify" to "string", // adapter/outbound/vless.go:85
            "fingerprint" to "string", // adapter/outbound/vless.go:86
            "certificate" to "string", // adapter/outbound/vless.go:87
            "private-key" to "string", // adapter/outbound/vless.go:88
            "servername" to "string", // adapter/outbound/vless.go:89
            "client-fingerprint" to "string", // adapter/outbound/vless.go:90
            "tfo" to "bool", // adapter/outbound/base.go:194
            "mptcp" to "bool", // adapter/outbound/base.go:195
            "interface-name" to "string", // adapter/outbound/base.go:196
            "routing-mark" to "int", // adapter/outbound/base.go:197
            "ip-version" to "C.DNSPrefer", // adapter/outbound/base.go:198
            "dialer-proxy" to "string", // adapter/outbound/base.go:199
        ),
        "VmessOption" to mapOf(
            "name" to "string", // adapter/outbound/vmess.go:55
            "server" to "string", // adapter/outbound/vmess.go:56
            "port" to "int", // adapter/outbound/vmess.go:57
            "uuid" to "string", // adapter/outbound/vmess.go:58
            "alterId" to "int", // adapter/outbound/vmess.go:59
            "cipher" to "string", // adapter/outbound/vmess.go:60
            "udp" to "bool", // adapter/outbound/vmess.go:61
            "network" to "string", // adapter/outbound/vmess.go:62
            "tls" to "bool", // adapter/outbound/vmess.go:63
            "alpn" to "[]string", // adapter/outbound/vmess.go:64
            "skip-cert-verify" to "bool", // adapter/outbound/vmess.go:65
            "name-cert-verify" to "string", // adapter/outbound/vmess.go:66
            "fingerprint" to "string", // adapter/outbound/vmess.go:67
            "certificate" to "string", // adapter/outbound/vmess.go:68
            "private-key" to "string", // adapter/outbound/vmess.go:69
            "servername" to "string", // adapter/outbound/vmess.go:70
            "ech-opts" to "ECHOptions", // adapter/outbound/vmess.go:71
            "shadow-tls-opts" to "ShadowTLSOptions", // adapter/outbound/vmess.go:72
            "restls-opts" to "RestlsOptions", // adapter/outbound/vmess.go:73
            "jls-opts" to "JLSOptions", // adapter/outbound/vmess.go:74
            "reality-opts" to "RealityOptions", // adapter/outbound/vmess.go:75
            "tlsmirror-opts" to "TLSMirrorOptions", // adapter/outbound/vmess.go:76
            "mekya-opts" to "MekyaOptions", // adapter/outbound/vmess.go:77
            "mkcp-opts" to "MKCPOptions", // adapter/outbound/vmess.go:78
            "http-opts" to "HTTPOptions", // adapter/outbound/vmess.go:79
            "h2-opts" to "HTTP2Options", // adapter/outbound/vmess.go:80
            "grpc-opts" to "GrpcOptions", // adapter/outbound/vmess.go:81
            "ws-opts" to "WSOptions", // adapter/outbound/vmess.go:82
            "packet-addr" to "bool", // adapter/outbound/vmess.go:83
            "xudp" to "bool", // adapter/outbound/vmess.go:84
            "packet-encoding" to "string", // adapter/outbound/vmess.go:85
            "global-padding" to "bool", // adapter/outbound/vmess.go:86
            "authenticated-length" to "bool", // adapter/outbound/vmess.go:87
            "client-fingerprint" to "string", // adapter/outbound/vmess.go:88
            "tfo" to "bool", // adapter/outbound/base.go:194
            "mptcp" to "bool", // adapter/outbound/base.go:195
            "interface-name" to "string", // adapter/outbound/base.go:196
            "routing-mark" to "int", // adapter/outbound/base.go:197
            "ip-version" to "C.DNSPrefer", // adapter/outbound/base.go:198
            "dialer-proxy" to "string", // adapter/outbound/base.go:199
        ),
        "WSOptions" to mapOf(
            "path" to "string", // adapter/outbound/vmess.go:166
            "headers" to "map[string]string", // adapter/outbound/vmess.go:167
            "max-early-data" to "int", // adapter/outbound/vmess.go:168
            "early-data-header-name" to "string", // adapter/outbound/vmess.go:169
            "v2ray-http-upgrade" to "bool", // adapter/outbound/vmess.go:170
            "v2ray-http-upgrade-fast-open" to "bool", // adapter/outbound/vmess.go:171
        ),
        "WireGuardOption" to mapOf(
            "name" to "string", // adapter/outbound/wireguard.go:72
            "ip" to "string", // adapter/outbound/wireguard.go:73
            "ipv6" to "string", // adapter/outbound/wireguard.go:74
            "private-key" to "string", // adapter/outbound/wireguard.go:75
            "workers" to "int", // adapter/outbound/wireguard.go:76
            "mtu" to "int", // adapter/outbound/wireguard.go:77
            "udp" to "bool", // adapter/outbound/wireguard.go:78
            "persistent-keepalive" to "int", // adapter/outbound/wireguard.go:79
            "ip-stack" to "IPStackOption", // adapter/outbound/wireguard.go:81
            "amnezia-wg-option" to "*AmneziaWGOption", // adapter/outbound/wireguard.go:83
            "peers" to "[]WireGuardPeerOption", // adapter/outbound/wireguard.go:85
            "remote-dns-resolve" to "bool", // adapter/outbound/wireguard.go:87
            "dns" to "[]string", // adapter/outbound/wireguard.go:88
            "refresh-server-ip-interval" to "int", // adapter/outbound/wireguard.go:90
            "tfo" to "bool", // adapter/outbound/base.go:194
            "mptcp" to "bool", // adapter/outbound/base.go:195
            "interface-name" to "string", // adapter/outbound/base.go:196
            "routing-mark" to "int", // adapter/outbound/base.go:197
            "ip-version" to "C.DNSPrefer", // adapter/outbound/base.go:198
            "dialer-proxy" to "string", // adapter/outbound/base.go:199
            "server" to "string", // adapter/outbound/wireguard.go:94
            "port" to "int", // adapter/outbound/wireguard.go:95
            "public-key" to "string", // adapter/outbound/wireguard.go:96
            "pre-shared-key" to "string", // adapter/outbound/wireguard.go:97
            "reserved" to "[]uint8", // adapter/outbound/wireguard.go:98
            "allowed-ips" to "[]string", // adapter/outbound/wireguard.go:99
        ),
        "WireGuardPeerOption" to mapOf(
            "server" to "string", // adapter/outbound/wireguard.go:94
            "port" to "int", // adapter/outbound/wireguard.go:95
            "public-key" to "string", // adapter/outbound/wireguard.go:96
            "pre-shared-key" to "string", // adapter/outbound/wireguard.go:97
            "reserved" to "[]uint8", // adapter/outbound/wireguard.go:98
            "allowed-ips" to "[]string", // adapter/outbound/wireguard.go:99
        ),
        "XHTTPDownloadSettings" to mapOf(
            "path" to "*string", // adapter/outbound/vless.go:132
            "host" to "*string", // adapter/outbound/vless.go:133
            "headers" to "*map[string]string", // adapter/outbound/vless.go:134
            "reuse-settings" to "*XHTTPReuseSettings", // adapter/outbound/vless.go:135
            "server" to "*string", // adapter/outbound/vless.go:137
            "port" to "*int", // adapter/outbound/vless.go:138
            "tls" to "*bool", // adapter/outbound/vless.go:139
            "alpn" to "*[]string", // adapter/outbound/vless.go:140
            "ech-opts" to "*ECHOptions", // adapter/outbound/vless.go:141
            "shadow-tls-opts" to "*ShadowTLSOptions", // adapter/outbound/vless.go:142
            "restls-opts" to "*RestlsOptions", // adapter/outbound/vless.go:143
            "jls-opts" to "*JLSOptions", // adapter/outbound/vless.go:144
            "reality-opts" to "*RealityOptions", // adapter/outbound/vless.go:145
            "skip-cert-verify" to "*bool", // adapter/outbound/vless.go:146
            "name-cert-verify" to "*string", // adapter/outbound/vless.go:147
            "fingerprint" to "*string", // adapter/outbound/vless.go:148
            "certificate" to "*string", // adapter/outbound/vless.go:149
            "private-key" to "*string", // adapter/outbound/vless.go:150
            "servername" to "*string", // adapter/outbound/vless.go:151
            "client-fingerprint" to "*string", // adapter/outbound/vless.go:152
        ),
        "XHTTPOptions" to mapOf(
            "path" to "string", // adapter/outbound/vless.go:94
            "host" to "string", // adapter/outbound/vless.go:95
            "mode" to "string", // adapter/outbound/vless.go:96
            "headers" to "map[string]string", // adapter/outbound/vless.go:97
            "no-grpc-header" to "bool", // adapter/outbound/vless.go:98
            "x-padding-bytes" to "string", // adapter/outbound/vless.go:99
            "x-padding-obfs-mode" to "bool", // adapter/outbound/vless.go:100
            "x-padding-key" to "string", // adapter/outbound/vless.go:101
            "x-padding-header" to "string", // adapter/outbound/vless.go:102
            "x-padding-placement" to "string", // adapter/outbound/vless.go:103
            "x-padding-method" to "string", // adapter/outbound/vless.go:104
            "uplink-http-method" to "string", // adapter/outbound/vless.go:105
            "session-placement" to "string", // adapter/outbound/vless.go:106
            "session-key" to "string", // adapter/outbound/vless.go:107
            "session-table" to "string", // adapter/outbound/vless.go:108
            "session-length" to "string", // adapter/outbound/vless.go:109
            "seq-placement" to "string", // adapter/outbound/vless.go:110
            "seq-key" to "string", // adapter/outbound/vless.go:111
            "uplink-data-placement" to "string", // adapter/outbound/vless.go:112
            "uplink-data-key" to "string", // adapter/outbound/vless.go:113
            "uplink-chunk-size" to "string", // adapter/outbound/vless.go:114
            "sc-max-each-post-bytes" to "string", // adapter/outbound/vless.go:115
            "sc-min-posts-interval-ms" to "string", // adapter/outbound/vless.go:116
            "reuse-settings" to "*XHTTPReuseSettings", // adapter/outbound/vless.go:117
            "download-settings" to "*XHTTPDownloadSettings", // adapter/outbound/vless.go:118
        ),
        "XHTTPReuseSettings" to mapOf(
            "max-concurrency" to "string", // adapter/outbound/vless.go:122
            "max-connections" to "string", // adapter/outbound/vless.go:123
            "c-max-reuse-times" to "string", // adapter/outbound/vless.go:124
            "h-max-request-times" to "string", // adapter/outbound/vless.go:125
            "h-max-reusable-secs" to "string", // adapter/outbound/vless.go:126
            "h-keep-alive-period" to "int", // adapter/outbound/vless.go:127
        ),
    )

    fun validatePlugin(options: JsonObject) = validateObject(options, "V2rayPluginOptions", "plugin-opts")

    fun validate(node: JsonObject) {
        val type = node["type"]?.jsonPrimitive?.content ?: throw MihomoMappingException("type", "缺少协议类型")
        val schema = protocolTypes[type] ?: throw MihomoMappingException("type", "未知协议类型")
        validateObject(JsonObject(node - "type"), schema, type)
    }

    private fun validateObject(obj: JsonObject, type: String, path: String) {
        val schema = fields.getValue(type)
        if (type == "ECHOptions") {
            if (obj["enable"] != JsonPrimitive(true) && (obj["config"] != null || obj["query-server-name"] != null))
                throw MihomoMappingException(path, "ECH 关闭时不消费 config/query-server-name")
            if (!obj["config"]?.jsonPrimitive?.content.isNullOrEmpty() && obj["query-server-name"] != null)
                throw MihomoMappingException(path, "ECH 固定 config 分支不消费 query-server-name")
        }
        if (type == "RealityOptions" && obj["public-key"]?.jsonPrimitive?.content.isNullOrEmpty() && obj.keys.any { it != "public-key" })
            throw MihomoMappingException(path, "Reality public-key 为空时不消费其余选项")
        if (type == "TrojanSSOption" && obj["enabled"] != JsonPrimitive(true) && obj.keys.any { it != "enabled" })
            throw MihomoMappingException(path, "Trojan SS 关闭时不消费 method/password")
        if (type == "Hysteria2RealmOption" && obj["enable"] != JsonPrimitive(true) && obj.keys.any { it != "enable" })
            throw MihomoMappingException(path, "Realm 关闭时不消费其余选项")
        if (type == "TLSMirrorOptions" && obj["primary-key"]?.jsonPrimitive?.content.isNullOrEmpty() && obj.keys.any { it != "primary-key" })
            throw MihomoMappingException(path, "TLSMirror 缺少 primary-key 时不消费其余选项")
        obj.forEach { (key, value) ->
            val kind = schema[key]
            // Approved exception: both flavors emit HTTP/SOCKS ALPN without an upstream consumer.
            if (key == "alpn" && type in setOf("HttpOption", "Socks5Option")) {
                validateValue(value, "[]string", "$path.$key")
                return@forEach
            }
            if (kind == null) throw MihomoMappingException("$path.$key", "消费入口待核定，见批量缺口清单")
            validateValue(value, kind, "$path.$key")
        }
    }

    private fun validateValue(value: JsonElement, kind: String, path: String) {
        val base = kind.removePrefix("*")
        when {
            base.startsWith("[]") -> {
                val array = value as? JsonArray ?: throw MihomoMappingException(path, "应为数组")
                array.forEach { validateValue(it, base.removePrefix("[]"), path) }
            }
            base in fields -> validateObject(value as? JsonObject ?: throw MihomoMappingException(path, "应为对象"), base, path)
            base.startsWith("map[") -> {
                val obj = value as? JsonObject ?: throw MihomoMappingException(path, "应为对象")
                if (base == "map[string]string") obj.forEach { (_, v) -> validateValue(v, "string", path) }
                if (base == "map[string][]string") obj.forEach { (_, v) -> validateValue(v, "[]string", path) }
                // Dynamic plugin maps are validated by the builder before this schema pass.
            }
            base == "C.DNSPrefer" -> if ((value as? JsonPrimitive)?.content !in setOf("dual", "ipv4", "ipv6", "ipv4-prefer", "ipv6-prefer")) throw MihomoMappingException(path, "未知 IP 优先级；拒绝上游静默默认值")
            base == "bool" -> if ((value as? JsonPrimitive)?.booleanOrNull == null) throw MihomoMappingException(path, "应为布尔值")
            base == "string" -> if (value !is JsonPrimitive || !value.isString) throw MihomoMappingException(path, "应为字符串")
            base in setOf("int", "int32", "int64", "uint", "uint8", "uint16", "uint32", "uint64") -> if ((value as? JsonPrimitive)?.longOrNull == null) throw MihomoMappingException(path, "应为整数")
            else -> throw MihomoMappingException(path, "字段类型消费入口待核定")
        }
    }
}
