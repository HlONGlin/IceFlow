package libxray

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"

	XN "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/core"
)

func TestGroupChainForwardsThroughEveryHopAndFailsClosed(t *testing.T) {
	var lock sync.Mutex
	var visits []string
	proxies := make([]*httptest.Server, 3)
	for i := range proxies {
		index := i
		proxies[i] = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.Method != http.MethodConnect {
				w.WriteHeader(405)
				return
			}
			lock.Lock()
			visits = append(visits, fmt.Sprintf("%d:%s", index, r.Host))
			lock.Unlock()
			conn, rw, err := w.(http.Hijacker).Hijack()
			if err != nil {
				return
			}
			defer conn.Close()
			_ = conn.SetDeadline(time.Now().Add(5 * time.Second))
			if index == 2 {
				_, _ = rw.WriteString("HTTP/1.1 200 Connection Established\r\n\r\n")
				_ = rw.Flush()
				request, err := http.ReadRequest(rw.Reader)
				if err != nil || request.Host != "chain.invalid" {
					return
				}
				_, _ = rw.WriteString("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n")
				_ = rw.Flush()
				return
			}
			target, err := net.DialTimeout("tcp", r.Host, time.Second)
			if err != nil {
				return
			}
			defer target.Close()
			_, _ = rw.WriteString("HTTP/1.1 200 Connection Established\r\n\r\n")
			_ = rw.Flush()
			go func() { _, _ = io.Copy(target, rw); _ = target.Close() }()
			_, _ = io.Copy(conn, target)
		}))
		defer proxies[i].Close()
	}
	tags := []string{"chain-0", "chain-1", "proxy"}
	var adapters []any
	for i, proxy := range proxies {
		options := map[string]any{"name": tags[i], "type": "http", "server": "127.0.0.1", "port": proxy.Listener.Addr().(*net.TCPAddr).Port}
		item := map[string]any{"tag": tags[i], "options": options}
		if i > 0 {
			item["upstream"] = tags[i-1]
		}
		adapters = append(adapters, item)
	}
	encoded, _ := json.Marshal(map[string]any{"log": map[string]any{"loglevel": "none"}, "iceflowAdapters": adapters,
		"outbounds": []any{map[string]any{"tag": "proxy", "protocol": "blackhole"}, map[string]any{"tag": "chain-1", "protocol": "blackhole"}, map[string]any{"tag": "chain-0", "protocol": "blackhole"}, map[string]any{"tag": "direct", "protocol": "freedom"}},
		"routing":   map[string]any{"rules": []any{map[string]any{"type": "field", "ip": []string{"127.0.0.0/8"}, "outboundTag": "direct"}}}})
	if _, err := MeasureDelay(string(encoded), "http://chain.invalid/generate_204", 5000); err != nil {
		t.Fatal(err)
	}
	lock.Lock()
	got := append([]string(nil), visits...)
	lock.Unlock()
	want := []string{"0:" + proxies[1].Listener.Addr().String(), "1:" + proxies[2].Listener.Addr().String(), "2:chain.invalid:80"}
	if fmt.Sprint(got) != fmt.Sprint(want) {
		t.Fatalf("chain bypassed or reordered: got %v want %v", got, want)
	}
	proxies[0].Close()
	if _, err := MeasureDelay(string(encoded), "http://chain.invalid/generate_204", 500); err == nil {
		t.Fatal("unavailable first hop must not fall back directly")
	}
}

func TestTaggedDialerRoutesUDPThroughNativeOutbound(t *testing.T) {
	echo, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer echo.Close()
	go func() {
		data := make([]byte, 65535)
		for {
			n, addr, err := echo.ReadFrom(data)
			if err != nil {
				return
			}
			_, _ = echo.WriteTo(data[:n], addr)
		}
	}()
	inst, err := newInstance(`{"log":{"loglevel":"none"},"outbounds":[{"tag":"proxy","protocol":"blackhole"},{"tag":"upstream","protocol":"freedom"}]}`)
	if err != nil {
		t.Fatal(err)
	}
	defer inst.Close()
	if err := inst.Start(); err != nil {
		t.Fatal(err)
	}
	dialer := &taggedDialer{instance: inst, tag: "upstream"}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	dest, _ := XN.ParseDestination("udp:" + echo.LocalAddr().String())
	conn, err := core.Dial(dialer.context(ctx), inst, dest)
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(3 * time.Second))
	for _, size := range []int{17, 4096} {
		data := make([]byte, size)
		if _, err := conn.Write(data); err != nil {
			t.Fatal(err)
		}
		output := make([]byte, 65535)
		n, err := conn.Read(output)
		if err != nil || n != size {
			t.Fatalf("UDP %d got %d: %v", size, n, err)
		}
	}
}
