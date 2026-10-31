package libmihomo

import (
	"context"
	"encoding/json"
	C "github.com/metacubex/mihomo/constant"
	"net"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

func TestBalancedGroupPrepareAndTraffic(t *testing.T) {
	for _, strategy := range []string{"round-robin", "consistent-hashing", "sticky-sessions"} {
		t.Run(strategy, func(t *testing.T) {
			var visits [2]atomic.Int32
			var proxies []any
			for i := range visits {
				index := i
				server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
					c, rw, err := w.(http.Hijacker).Hijack()
					if err != nil {
						return
					}
					defer c.Close()
					_ = c.SetDeadline(time.Now().Add(3 * time.Second))
					_, _ = rw.WriteString("HTTP/1.1 200 Connection Established\r\n\r\n")
					_ = rw.Flush()
					req, err := http.ReadRequest(rw.Reader)
					if err != nil {
						return
					}
					if req.URL.Path == "/request" {
						visits[index].Add(1)
					}
					_, _ = rw.WriteString("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n")
					_ = rw.Flush()
				}))
				defer server.Close()
				name := []string{"lb-0", "lb-1"}[i]
				proxies = append(proxies, map[string]any{"name": name, "type": "http", "server": "127.0.0.1", "port": server.Listener.Addr().(*net.TCPAddr).Port})
			}
			cfg := map[string]any{"mode": "rule", "rules": []string{"MATCH,proxy"}, "find-process-mode": "off", "iceflow-selected-outbound": "proxy", "proxies": proxies,
				"proxy-groups": []any{map[string]any{"name": "proxy", "type": "load-balance", "strategy": strategy, "proxies": []string{"lb-0", "lb-1"}, "url": "http://balance.invalid/probe", "interval": 300}}}
			encoded, _ := json.Marshal(cfg)
			var events []string
			e := fakeEngine(&events, &fakeTun{})
			e.totals = func() (int64, int64) { return e.book.selected(e.selectedTag) }
			if err := e.prepare(string(encoded)); err != nil {
				t.Fatal(err)
			}
			defer e.stop()
			if e.selectedTag != "proxy" || e.book["proxy"] == nil || e.book["lb-0"] != nil {
				t.Fatal("wrong selected traffic group")
			}
			if err := e.attach(42, 1500); err != nil {
				t.Fatal(err)
			}
			transport := &http.Transport{DisableKeepAlives: true, DialContext: func(ctx context.Context, network, address string) (net.Conn, error) {
				return e.cfg.Proxies["proxy"].DialContext(ctx, &C.Metadata{NetWork: C.TCP, Host: "balance.invalid", DstPort: 80})
			}}
			defer transport.CloseIdleConnections()
			client := &http.Client{Transport: transport, Timeout: 3 * time.Second}
			for range 4 {
				resp, err := client.Get("http://balance.invalid/request")
				if err != nil {
					t.Fatal(err)
				}
				resp.Body.Close()
				if resp.StatusCode != 204 {
					t.Fatal(resp.Status)
				}
			}
			if strategy == "round-robin" && (visits[0].Load() == 0 || visits[1].Load() == 0) {
				t.Fatal("load balancing bypassed")
			}
			up, down := e.book.selected("proxy")
			if up == 0 || down == 0 {
				t.Fatalf("missing balanced traffic: %d %d", up, down)
			}
			cfg["iceflow-selected-outbound"] = "lb-0"
			invalid, _ := json.Marshal(cfg)
			bad := fakeEngine(&events, &fakeTun{})
			if err := bad.prepare(string(invalid)); err == nil {
				bad.stop()
				t.Fatal("old invalid metadata accepted")
			}
		})
	}
}

func TestExplicitProvidersRemainRejected(t *testing.T) {
	for _, field := range []string{"proxy-providers", "rule-providers"} {
		cfg, _ := json.Marshal(map[string]any{field: map[string]any{"remote": map[string]any{"type": "http", "url": "http://127.0.0.1:1/provider", "path": "provider.yaml"}}})
		if err := Validate(string(cfg)); err == nil {
			t.Fatalf("accepted explicit %s", field)
		}
	}
}
