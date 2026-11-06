package libxray

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"
	"time"

	C "github.com/metacubex/mihomo/constant"
	XN "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/core"
)

func TestAdapterMetadataPreservesIPv6AndAddressFamilies(t *testing.T) {
	for _, network := range []string{"tcp", "udp"} {
		for _, host := range []string{"198.51.100.1", "2001:db8::1", "proxy.invalid"} {
			t.Run(network+"/"+host, func(t *testing.T) {
				dest, err := XN.ParseDestination(network + ":" + net.JoinHostPort(host, "80"))
				if err != nil {
					t.Fatal(err)
				}
				metadata, err := metadataFor(dest)
				if err != nil {
					t.Fatal(err)
				}
				if metadata.DstPort != 80 {
					t.Fatalf("unexpected port: %d", metadata.DstPort)
				}
				if ip := net.ParseIP(host); ip != nil {
					if metadata.DstIP.String() != host || metadata.Host != "" {
						t.Fatalf("unexpected IP metadata: %+v", metadata)
					}
				} else if metadata.Host != host {
					t.Fatalf("unexpected domain metadata: %+v", metadata)
				}
				want := C.TCP
				if network == "udp" {
					want = C.UDP
				}
				if metadata.NetWork != want {
					t.Fatalf("unexpected network: %v", metadata.NetWork)
				}
			})
		}
	}
}

func adapterConfig(options map[string]any) string {
	options["name"] = "test-proxy"
	if options["server"] == nil {
		options["server"] = "127.0.0.1"
	}
	if options["port"] == nil {
		options["port"] = 443
	}
	options["udp"] = true
	config := map[string]any{
		"log":            map[string]any{"loglevel": "none"},
		"outbounds":      []any{map[string]any{"tag": "proxy", "protocol": "blackhole"}, map[string]any{"tag": "direct", "protocol": "freedom"}},
		"iceflowAdapter": options,
	}
	encoded, _ := json.Marshal(config)
	return string(encoded)
}

func TestExtendedAdapterLifecycle(t *testing.T) {
	key := "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
	cases := []map[string]any{
		{"type": "vmess", "uuid": "00000000-0000-4000-8000-000000000001", "alterId": 0, "cipher": "auto", "network": "ws", "ws-opts": map[string]any{"path": "/ws", "headers": map[string]any{"Host": "cdn.example.com"}}},
		{"type": "vless", "uuid": "00000000-0000-4000-8000-000000000001", "tls": true, "network": "grpc", "grpc-opts": map[string]any{"grpc-service-name": "svc"}},
		{"type": "trojan", "password": "secret", "sni": "example.com"},
		{"type": "ss", "password": "secret", "cipher": "aes-128-gcm"},
		{"type": "hysteria", "auth-str": "password", "up": "10", "down": "100"},
		{"type": "hysteria", "auth-str": "password", "up": 10, "down": 100},
		{"type": "hysteria2", "password": "password", "obfs": "salamander", "obfs-password": "secret"},
		{"type": "tuic", "uuid": "00000000-0000-4000-8000-000000000001", "password": "secret", "alpn": []string{"h3"}},
		{"type": "tuic", "token": "legacy-token", "alpn": []string{"h3"}},
		{"type": "wireguard", "private-key": key, "public-key": key, "ip": "10.0.0.2"},
		{"type": "ssr", "cipher": "aes-128-cfb", "password": "secret", "protocol": "auth_aes128_md5", "obfs": "tls1.2_ticket_auth"},
		{"type": "http", "username": "user", "password": "secret"},
		{"type": "socks5", "username": "user", "password": "secret"},
		{"type": "anytls", "password": "secret", "sni": "example.com"},
		{"type": "sudoku", "key": "00000000-0000-4000-8000-000000000001"},
	}
	for _, options := range cases {
		t.Run(options["type"].(string), func(t *testing.T) {
			if err := Start(adapterConfig(options)); err != nil {
				t.Fatal(err)
			}
			if !IsRunning() {
				t.Fatal("adapter instance not running")
			}
			if err := Stop(); err != nil {
				t.Fatal(err)
			}
		})
	}
}

func TestHTTPAdapterAuthenticatedForwarding(t *testing.T) {
	requests := make(chan string, 1)
	proxy := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodConnect || r.Header.Get("Proxy-Authorization") != "Basic dXNlcjpzZWNyZXQ=" {
			w.WriteHeader(http.StatusProxyAuthRequired)
			return
		}
		conn, rw, err := w.(http.Hijacker).Hijack()
		if err != nil {
			return
		}
		defer conn.Close()
		_ = conn.SetDeadline(time.Now().Add(5 * time.Second))
		_, _ = rw.WriteString("HTTP/1.1 200 Connection Established\r\n\r\n")
		_ = rw.Flush()
		request, err := http.ReadRequest(rw.Reader)
		if err != nil {
			return
		}
		requests <- request.Host
		_, _ = rw.WriteString("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n")
		_ = rw.Flush()
	}))
	defer proxy.Close()
	port := proxy.Listener.Addr().(*net.TCPAddr).Port
	elapsed, err := MeasureDelay(adapterConfig(map[string]any{"type": "http", "port": port, "username": "user", "password": "secret"}), "http://iceflow.invalid/generate_204", 5000)
	if err != nil || elapsed < 0 {
		t.Fatalf("HTTP CONNECT measurement: elapsed=%d err=%v", elapsed, err)
	}
	select {
	case host := <-requests:
		if host != "iceflow.invalid" {
			t.Fatalf("unexpected target: %s", host)
		}
	case <-time.After(time.Second):
		t.Fatal("request bypassed HTTP proxy")
	}
}

func TestSOCKSAdapterAuthenticatedTCPAndUDP(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	port := listener.Addr().(*net.TCPAddr).Port
	_ = listener.Close()
	serverJSON := fmt.Sprintf(`{"log":{"loglevel":"none"},"inbounds":[{"listen":"127.0.0.1","port":%d,"protocol":"socks","settings":{"auth":"password","accounts":[{"user":"user","pass":"secret"}],"udp":true}}],"outbounds":[{"protocol":"freedom"}]}`, port)
	server, err := newInstance(serverJSON)
	if err != nil {
		t.Fatal(err)
	}
	defer server.Close()
	if err := server.Start(); err != nil {
		t.Fatal(err)
	}
	config := adapterConfig(map[string]any{"type": "socks5", "port": port, "username": "user", "password": "secret"})
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(http.StatusNoContent) }))
	defer target.Close()
	if _, err := MeasureDelay(config, target.URL, 5000); err != nil {
		t.Fatalf("SOCKS TCP forwarding: %v", err)
	}

	echo, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer echo.Close()
	go func() {
		packet := make([]byte, 65535)
		for {
			n, source, err := echo.ReadFrom(packet)
			if err != nil {
				return
			}
			_, _ = echo.WriteTo(packet[:n], source)
		}
	}()
	client, err := newInstance(config)
	if err != nil {
		t.Fatal(err)
	}
	defer client.Close()
	if err := client.Start(); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	dest, err := XN.ParseDestination("udp:" + echo.LocalAddr().String())
	if err != nil {
		t.Fatal(err)
	}
	conn, err := core.Dial(ctx, client, dest)
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(5 * time.Second))
	// Exercise multiple datagrams and packets larger than Xray's normal 2KiB buffer.
	for _, size := range []int{17, 4096, 17} {
		payload := []byte(strconv.Itoa(size) + "-")
		for len(payload) < size {
			payload = append(payload, byte(len(payload)%251))
		}
		if _, err := conn.Write(payload); err != nil {
			t.Fatal(err)
		}
		got := make([]byte, 65535)
		n, err := conn.Read(got)
		if err != nil {
			t.Fatalf("UDP %d: %v", size, err)
		}
		if string(got[:n]) != string(payload) {
			t.Fatalf("UDP payload changed: wrote %d read %d", size, n)
		}
	}
}
