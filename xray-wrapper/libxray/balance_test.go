package libxray

import (
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

func TestBalancedStartupAndForcedProbe(t *testing.T) {
	var visits [2]atomic.Int32
	var outbounds []any
	var adapters []any
	for i := range visits {
		index := i
		proxy := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
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
		defer proxy.Close()
		tag := fmt.Sprintf("lb-%d", i)
		outbounds = append(outbounds, map[string]any{"tag": tag, "protocol": "blackhole"})
		adapters = append(adapters, map[string]any{"tag": tag, "options": map[string]any{"name": tag, "type": "http", "server": "127.0.0.1", "port": proxy.Listener.Addr().(*net.TCPAddr).Port}})
	}
	outbounds = append([]any{map[string]any{"tag": "proxy", "protocol": "loopback", "settings": map[string]any{"inboundTag": "balance-in"}}}, outbounds...)
	for _, strategy := range []string{"random", "roundRobin", "leastPing", "leastLoad"} {
		t.Run(strategy, func(t *testing.T) {
			for i := range visits {
				visits[i].Store(0)
			}
			cfg := map[string]any{"log": map[string]any{"loglevel": "none"}, "outbounds": outbounds, "iceflowAdapters": adapters,
				"observatory": map[string]any{"subjectSelector": []string{"lb-"}, "probeURL": "http://balance.invalid/probe", "probeInterval": "1s"},
				"routing":     map[string]any{"rules": []any{map[string]any{"type": "field", "inboundTag": []string{"balance-in"}, "balancerTag": "proxy"}}, "balancers": []any{map[string]any{"tag": "proxy", "selector": []string{"lb-"}, "fallbackTag": "lb-0", "strategy": map[string]any{"type": strategy}}}}}
			encoded, _ := json.Marshal(cfg)
			inst, err := newInstance(string(encoded))
			if err != nil {
				t.Fatal(err)
			}
			defer inst.Close()
			if err = inst.Start(); err != nil {
				t.Fatal(err)
			}
			transport := &http.Transport{DialContext: (&taggedDialer{instance: inst, tag: "proxy"}).DialContext, DisableKeepAlives: true}
			defer transport.CloseIdleConnections()
			client := &http.Client{Transport: transport, Timeout: 3 * time.Second}
			for range 8 {
				response, err := client.Get("http://balance.invalid/request")
				if err != nil {
					t.Fatal(err)
				}
				response.Body.Close()
				if response.StatusCode != 204 {
					t.Fatal(response.Status)
				}
				if strategy == "roundRobin" && visits[0].Load() > 0 && visits[1].Load() > 0 {
					break
				}
				time.Sleep(100 * time.Millisecond)
			}
			if strategy == "roundRobin" && (visits[0].Load() == 0 || visits[1].Load() == 0) {
				t.Fatalf("balancer bypassed: %d %d", visits[0].Load(), visits[1].Load())
			}
			// This is the actual old startup failure, not just a JSON-shape assertion.
			if strategy == "random" || strategy == "roundRobin" {
				delete(cfg, "observatory")
				invalid, _ := json.Marshal(cfg)
				bad, err := newInstance(string(invalid))
				if bad != nil {
					bad.Close()
				}
				if err == nil {
					t.Fatal("missing required observatory accepted")
				}
			}
		})
	}
}
