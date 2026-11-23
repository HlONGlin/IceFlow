package libxray

import (
	"context"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

const minimalConfig = `{
  "log": {"loglevel": "none"},
  "outbounds": [{"tag": "direct", "protocol": "freedom"}]
}`

func TestStartStop(t *testing.T) {
	if err := Start(minimalConfig); err != nil {
		t.Fatalf("Start: %v", err)
	}
	if !IsRunning() {
		t.Fatal("expected running")
	}
	if err := Stop(); err != nil {
		t.Fatalf("Stop: %v", err)
	}
	if IsRunning() {
		t.Fatal("expected stopped")
	}
	// 重复停止安全
	if err := Stop(); err != nil {
		t.Fatalf("second Stop: %v", err)
	}
}

func TestInvalidConfig(t *testing.T) {
	if err := Start("{not json"); err == nil {
		t.Fatal("expected error for invalid config")
	}
	if IsRunning() {
		t.Fatal("must not be running after failed start")
	}
}

func TestMeasureDelayRejectsBadTimeout(t *testing.T) {
	if _, err := MeasureDelay(minimalConfig, "https://example.com", 0); err == nil {
		t.Fatal("expected error")
	}
}

func TestVersion(t *testing.T) {
	if Version() == "" {
		t.Fatal("empty version")
	}
}

func TestMeasureHTTPDelayReportsTimeoutPhase(t *testing.T) {
	assertTimeout := func(t *testing.T, transport *http.Transport, url, code string) {
		t.Helper()
		if ms, err := measureHTTPDelay(transport, url, 200); ms != -1 || err == nil || !strings.HasPrefix(err.Error(), code+": ") {
			t.Fatalf("want %s, got %d ms, %v", code, ms, err)
		}
	}
	t.Run("dial", func(t *testing.T) {
		transport := &http.Transport{DialContext: func(context.Context, string, string) (net.Conn, error) {
			return nil, context.DeadlineExceeded
		}}
		assertTimeout(t, transport, "https://probe.invalid", "iceflow_probe_dial_timeout")
	})
	t.Run("TLS handshake", func(t *testing.T) {
		client, peer := net.Pipe()
		defer peer.Close()
		transport := &http.Transport{DialContext: func(context.Context, string, string) (net.Conn, error) {
			return client, nil
		}}
		assertTimeout(t, transport, "https://probe.invalid", "iceflow_probe_tls_timeout")
	})
	t.Run("response headers", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			<-r.Context().Done()
		}))
		defer server.Close()
		assertTimeout(t, &http.Transport{}, server.URL, "iceflow_probe_response_timeout")
	})
	t.Run("response body", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(http.StatusOK)
			w.(http.Flusher).Flush()
			<-r.Context().Done()
		}))
		defer server.Close()
		assertTimeout(t, &http.Transport{}, server.URL, "iceflow_probe_response_timeout")
	})
}

func TestMeasureHTTPDelayPreservesSuccessAndHTTPError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/error" {
			w.WriteHeader(http.StatusServiceUnavailable)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()
	if ms, err := measureHTTPDelay(&http.Transport{}, server.URL, 1000); ms < 0 || err != nil {
		t.Fatalf("want successful probe, got %d ms, %v", ms, err)
	}
	if ms, err := measureHTTPDelay(&http.Transport{}, server.URL+"/error", 1000); ms != -1 || err == nil || err.Error() != "HTTP 503" {
		t.Fatalf("want HTTP status error, got %d ms, %v", ms, err)
	}
}
