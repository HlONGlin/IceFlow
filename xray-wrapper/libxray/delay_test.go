package libxray

import (
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

func unusedProbePort(t *testing.T) int64 {
	t.Helper()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	port := int64(listener.Addr().(*net.TCPAddr).Port)
	_ = listener.Close()
	return port
}

func TestNodeAndRunningProbesShareRequestSemantics(t *testing.T) {
	port := unusedProbePort(t)
	config := fmt.Sprintf(`{"log":{"loglevel":"none"},"inbounds":[{"listen":"127.0.0.1","port":%d,"protocol":"socks","settings":{"auth":"noauth"}}],"outbounds":[{"tag":"proxy","protocol":"freedom"}]}`, port)
	if err := Start(config); err != nil {
		t.Fatal(err)
	}
	defer Stop()
	running := instance
	var hits atomic.Int32
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("existing") != "kept" || r.URL.Query().Get("_t") == "" || r.Header.Get("Cache-Control") != "no-cache" {
			t.Error("probe lost the query or omitted cache prevention")
		}
		hits.Add(1)
		w.WriteHeader(http.StatusNoContent)
	}))
	defer target.Close()
	testURL := target.URL + "/generate_204?existing=kept#fragment"
	for _, probe := range []struct {
		name    string
		measure func() (int64, error)
	}{
		{"node", func() (int64, error) {
			return MeasureDelay(`{"outbounds":[{"tag":"proxy","protocol":"freedom"}]}`, testURL, 5000)
		}},
		{"running", func() (int64, error) { return MeasureSocksDelay(port, testURL, 5000) }},
	} {
		t.Run(probe.name, func(t *testing.T) {
			for i := 0; i < 2; i++ {
				if elapsed, err := probe.measure(); err != nil || elapsed < 0 {
					t.Fatalf("probe failed: elapsed=%d err=%v", elapsed, err)
				}
			}
		})
	}
	if hits.Load() != 4 {
		t.Fatalf("expected four uncached requests, got %d", hits.Load())
	}
	if !IsRunning() || instance != running {
		t.Fatal("measurement replaced or stopped the running core")
	}
	before := hits.Load()
	if _, err := MeasureSocksDelay(unusedProbePort(t), testURL, 500); err == nil {
		t.Fatal("unavailable SOCKS listener must fail")
	}
	if hits.Load() != before {
		t.Fatal("probe bypassed SOCKS and fell back to direct access")
	}
}

func TestRunningProbeRejectsTlsErrorsBadResponsesAndTimeouts(t *testing.T) {
	port := unusedProbePort(t)
	server, err := newInstance(fmt.Sprintf(`{"log":{"loglevel":"none"},"inbounds":[{"listen":"127.0.0.1","port":%d,"protocol":"socks","settings":{"auth":"noauth"}}],"outbounds":[{"protocol":"freedom"}]}`, port))
	if err != nil {
		t.Fatal(err)
	}
	defer server.Close()
	if err := server.Start(); err != nil {
		t.Fatal(err)
	}
	for _, failure := range []struct {
		name    string
		handler http.HandlerFunc
	}{
		{"status", func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(http.StatusServiceUnavailable) }},
		{"truncated", func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Length", "10")
			_, _ = w.Write([]byte("short"))
		}},
		{"timeout", func(w http.ResponseWriter, r *http.Request) { <-r.Context().Done() }},
	} {
		t.Run(failure.name, func(t *testing.T) {
			target := httptest.NewServer(failure.handler)
			defer target.Close()
			start := time.Now()
			if _, err := MeasureSocksDelay(port, target.URL, 200); err == nil {
				t.Fatal("expected measurement failure")
			}
			if time.Since(start) > 2*time.Second {
				t.Fatal("probe exceeded its timeout")
			}
		})
	}
	target := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(http.StatusNoContent) }))
	defer target.Close()
	if _, err := MeasureSocksDelay(port, target.URL, 1000); err == nil {
		t.Fatal("probe accepted an untrusted TLS certificate")
	}
	for _, args := range []struct {
		port    int64
		url     string
		timeout int64
	}{
		{0, "http://localhost/", 100}, {65536, "http://localhost/", 100},
		{port, "http://localhost/", 0}, {port, "file:///tmp/probe", 100},
	} {
		if _, err := MeasureSocksDelay(args.port, args.url, args.timeout); err == nil {
			t.Fatal("probe accepted invalid arguments")
		}
	}
}
