package libmihomo

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/netip"
	"strings"
	"testing"
	"time"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/adapter/outbound"
	"github.com/metacubex/mihomo/common/buf"
	"github.com/metacubex/mihomo/component/proxydialer"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/sing/common/bufio"
)

func appListener(name string, port int, udp bool) map[string]any {
	return map[string]any{"type": "socks", "name": name, "listen": "127.0.0.1", "port": port, "udp": udp, "users": []map[string]string{{"username": "u", "password": "p"}}}
}
func listenerJSON(items ...map[string]any) string {
	b, _ := json.Marshal(map[string]any{"listeners": items})
	return string(b)
}
func TestAppListenersExactGeneratedShape(t *testing.T) {
	text := listenerJSON(appListener("tun-in", 30001, true), appListener("socks-in", 30002, false))
	if err := Validate(text); err != nil {
		t.Fatal(err)
	}
}
func TestAppListenersRejectOutsideAppShape(t *testing.T) {
	cases := []struct {
		name   string
		change func(map[string]any)
	}{
		{"unknown-name", func(m map[string]any) { m["name"] = "other" }},
		{"public-bind", func(m map[string]any) { m["listen"] = "0.0.0.0" }},
		{"TLS-extra", func(m map[string]any) { m["certificate"] = "secret" }},
		{"string-port", func(m map[string]any) { m["port"] = "1080" }},
		{"tun-udp-disabled", func(m map[string]any) { m["udp"] = false }},
		{"missing-udp", func(m map[string]any) { delete(m, "udp") }},
		{"bad-credentials", func(m map[string]any) { m["users"] = []map[string]string{{"username": "u"}} }},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			m := appListener("tun-in", 30001, true)
			tc.change(m)
			if Validate(listenerJSON(m)) == nil {
				t.Fatal("accepted outside app shape")
			}
		})
	}
}
func TestAppListenerDuplicateNamesAndPorts(t *testing.T) {
	for _, second := range []map[string]any{appListener("tun-in", 30002, true), appListener("socks-in", 30001, false)} {
		if Validate(listenerJSON(appListener("tun-in", 30001, true), second)) == nil {
			t.Fatal("duplicate accepted")
		}
	}
}
func TestListenerBindFailurePropagatesAndCleans(t *testing.T) {
	held, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer held.Close()
	var events []string
	e := fakeEngine(&events, &fakeTun{})
	err = e.prepare(listenerJSON(appListener("tun-in", held.Addr().(*net.TCPAddr).Port, true)))
	if err == nil || !strings.HasPrefix(err.Error(), "CORE_START_FAILED: app SOCKS listener:") || e.cfg != nil || len(e.listeners) != 0 {
		t.Fatalf("%v %+v", err, events)
	}
	if events[len(events)-1] != "shutdown" {
		t.Fatal(events)
	}
}
func TestTwoPhaseHandoffAndFastReconnect(t *testing.T) {
	var events []string
	e := fakeEngine(&events, &fakeTun{})
	if err := e.prepare(minimalConfig); err != nil {
		t.Fatal(err)
	}
	if len(events) != 1 || events[0] != "apply" || e.tun != nil {
		t.Fatal(events)
	}
	if err := e.attach(42, 1500); err != nil {
		t.Fatal(err)
	}
	if err := e.stopTun(); err != nil {
		t.Fatal(err)
	}
	if e.cfg == nil || e.tun != nil {
		t.Fatal("stopTun lost reusable core")
	}
	if err := e.attach(42, 1500); err != nil {
		t.Fatal(err)
	}
	if len(events) != 3 || events[2] != "tun" {
		t.Fatal(events)
	}
	if err := e.stop(); err != nil {
		t.Fatal(err)
	}
}

// Fixture proxy uses a real loopback echo server; forced dialer must call it even for
// a private target and with a DIRECT fallback rule. No upstream source is modified.
type fixtureProxy struct {
	C.Proxy
	target string
	seen   []*C.Metadata
}

func (p *fixtureProxy) DialContext(ctx context.Context, m *C.Metadata) (C.Conn, error) {
	p.seen = append(p.seen, m)
	c, e := (&net.Dialer{}).DialContext(ctx, "tcp", p.target)
	if e != nil {
		return nil, e
	}
	return outbound.NewConn(c, p), nil
}
func echoFixture(t *testing.T) (string, func()) {
	t.Helper()
	l, e := net.Listen("tcp", "127.0.0.1:0")
	if e != nil {
		t.Fatal(e)
	}
	go func() {
		for {
			c, e := l.Accept()
			if e != nil {
				return
			}
			go func() { defer c.Close(); _, _ = io.Copy(c, c) }()
		}
	}()
	return l.Addr().String(), func() { _ = l.Close() }
}
func TestForcedDialerPrivateAddressFixture(t *testing.T) {
	address, close := echoFixture(t)
	defer close()
	base, e := adapter.ParseProxy(map[string]any{"type": "socks5", "name": "front", "server": "127.0.0.1", "port": 1080})
	if e != nil {
		t.Fatal(e)
	}
	p := &fixtureProxy{Proxy: base, target: address}
	previous := tunnel.Proxies()
	defer tunnel.UpdateProxies(previous, nil)
	tunnel.UpdateProxies(map[string]C.Proxy{"front": p}, nil)
	c, e := proxydialer.NewByName("front", tunnel.Tunnel).DialContext(context.Background(), "tcp", "192.168.77.9:443")
	if e != nil {
		t.Fatal(e)
	}
	defer c.Close()
	_ = c.SetDeadline(time.Now().Add(2 * time.Second))
	_, e = c.Write([]byte("forced-private"))
	if e != nil {
		t.Fatal(e)
	}
	b := make([]byte, 14)
	if _, e = io.ReadFull(c, b); e != nil {
		t.Fatal(e)
	}
	if string(b) != "forced-private" || len(p.seen) != 1 || p.seen[0].DstIP != netip.MustParseAddr("192.168.77.9") {
		t.Fatal(p.seen)
	}
}
func TestTrafficSnapshotsClosedConnectionsAndChainNoDoubleCount(t *testing.T) {
	address, close := echoFixture(t)
	defer close()
	p, e := adapter.ParseProxy(map[string]any{"type": "socks5", "name": "proxy", "server": "127.0.0.1", "port": 1080})
	if e != nil {
		t.Fatal(e)
	}
	book := trafficBook{"proxy": {}, "chain-0": {}}
	proxy := countedProxy(&fixtureProxy{Proxy: p, target: address}, book["proxy"])
	for i := 0; i < 2; i++ {
		c, e := proxy.DialContext(context.Background(), &C.Metadata{})
		if e != nil {
			t.Fatal(e)
		}
		_ = c.SetDeadline(time.Now().Add(2 * time.Second))
		_, _ = c.Write([]byte("abc"))
		b := make([]byte, 3)
		_, e = io.ReadFull(c, b)
		if e != nil {
			t.Fatal(e)
		}
		_ = c.Close()
	}
	book["chain-0"].upload.Add(60)
	book["chain-0"].download.Add(70)
	for i := 0; i < 3; i++ {
		up, down := book.selected("proxy")
		if up != 6 || down != 6 {
			t.Fatal(up, down)
		}
	}
	if strings.Count(book.text(), "outbound>>>") != 4 {
		t.Fatal(book.text())
	}
}
func TestTrafficOptimizedCopyCountsExactlyOnce(t *testing.T) {
	address, close := echoFixture(t)
	defer close()
	p, _ := adapter.ParseProxy(map[string]any{"type": "socks5", "name": "proxy", "server": "127.0.0.1", "port": 1080})
	counter := &nodeTraffic{}
	c, e := countedProxy(&fixtureProxy{Proxy: p, target: address}, counter).DialContext(context.Background(), &C.Metadata{})
	if e != nil {
		t.Fatal(e)
	}
	defer c.Close()
	_ = c.SetDeadline(time.Now().Add(2 * time.Second))
	if _, e = bufio.Copy(c, strings.NewReader("optimized")); e != nil {
		t.Fatal(e)
	}
	b := make([]byte, 9)
	_, e = io.ReadFull(c, b)
	if e != nil {
		t.Fatal(e)
	}
	if counter.upload.Load() != 9 || counter.download.Load() != 9 {
		t.Fatal(counter.upload.Load(), counter.download.Load())
	}
}

func TestTrafficBufferAndUdpPathsRetainCounts(t *testing.T) {
	address, close := echoFixture(t)
	defer close()
	p, _ := adapter.ParseProxy(map[string]any{"type": "socks5", "name": "proxy", "server": "127.0.0.1", "port": 1080})
	counter := &nodeTraffic{}
	c, e := countedProxy(&fixtureProxy{Proxy: p, target: address}, counter).DialContext(context.Background(), &C.Metadata{})
	if e != nil {
		t.Fatal(e)
	}
	defer c.Close()
	_ = c.SetDeadline(time.Now().Add(2 * time.Second))
	if e = c.WriteBuffer(buf.As([]byte("buffer"))); e != nil {
		t.Fatal(e)
	}
	b := buf.NewSize(32)
	defer b.Release()
	if e = c.ReadBuffer(b); e != nil {
		t.Fatal(e)
	}
	if counter.upload.Load() != 6 || counter.download.Load() != 6 {
		t.Fatal(counter)
	}
	server, e := net.ListenPacket("udp", "127.0.0.1:0")
	if e != nil {
		t.Fatal(e)
	}
	defer server.Close()
	go func() {
		b := make([]byte, 64)
		for {
			n, a, e := server.ReadFrom(b)
			if e != nil {
				return
			}
			_, _ = server.WriteTo(b[:n], a)
		}
	}()
	client, e := net.ListenPacket("udp", "127.0.0.1:0")
	if e != nil {
		t.Fatal(e)
	}
	pc := &trafficPacket{PacketConn: outbound.NewPacketConn(client, outbound.NewBase(outbound.BaseOption{Name: "proxy", Type: C.Socks5, UDP: true})), counter: counter}
	defer pc.Close()
	_ = pc.SetDeadline(time.Now().Add(2 * time.Second))
	_, _ = pc.WriteTo([]byte("udp"), server.LocalAddr())
	v, put, _, e := pc.WaitReadFrom()
	if e != nil {
		t.Fatal(e)
	}
	if put != nil {
		put()
	}
	if len(v) != 3 {
		t.Fatal(len(v))
	}
	_, _ = pc.WriteTo([]byte("read"), server.LocalAddr())
	n, _, e := pc.ReadFrom(make([]byte, 32))
	if e != nil || n != 4 {
		t.Fatal(n, e)
	}
	if counter.upload.Load() != 13 || counter.download.Load() != 13 {
		t.Fatal(counter.upload.Load(), counter.download.Load())
	}
}

func TestSelectedMiddleNodeAndReattachSessionBaseline(t *testing.T) {
	old := current
	defer func() { current = old }()
	var events []string
	current = fakeEngine(&events, &fakeTun{})
	current.totals = func() (int64, int64) { return current.book.selected(current.selectedTag) }
	if e := current.prepare("{\"iceflow-selected-outbound\":\"chain-0\",\"proxies\":[{\"name\":\"chain-0\",\"type\":\"socks5\",\"server\":\"127.0.0.1\",\"port\":1080},{\"name\":\"proxy\",\"type\":\"socks5\",\"server\":\"127.0.0.1\",\"port\":1081}]}"); e != nil {
		t.Fatal(e)
	}
	if e := current.attach(42, 1500); e != nil {
		t.Fatal(e)
	}
	current.book["chain-0"].upload.Add(12)
	current.book["proxy"].upload.Add(90)
	if StatsJSON() != "{\"download\":0,\"upload\":12}" {
		t.Fatal(StatsJSON())
	}
	if e := StopTun(); e != nil {
		t.Fatal(e)
	}
	current.openTun = func(LC.Tun, int) (tunSession, error) { return &fakeTun{}, nil }
	if e := current.attach(42, 1500); e != nil {
		t.Fatal(e)
	}
	if StatsJSON() != "{\"download\":0,\"upload\":0}" {
		t.Fatal(StatsJSON())
	}
	current.book["chain-0"].upload.Add(3)
	if StatsJSON() != "{\"download\":0,\"upload\":3}" {
		t.Fatal(StatsJSON())
	}
	if !strings.Contains(QueryStats(), "uplink,15") {
		t.Fatal(QueryStats())
	}
	if e := Stop(); e != nil {
		t.Fatal(e)
	}
}

func TestRealExecutorAppListenersLifecycle(t *testing.T) {
	cfg, err := config.Parse([]byte(minimalConfig))
	if err != nil {
		t.Fatal(err)
	}
	_ = cfg
	e := &engine{openTun: openTun, apply: applyConfig, shutdown: shutdown}
	e.totals = func() (int64, int64) { return e.book.selected(e.selectedTag) }
	free, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	port := free.Addr().(*net.TCPAddr).Port
	_ = free.Close()
	if err = e.prepare(listenerJSON(appListener("socks-in", port, false))); err != nil {
		t.Fatal(err)
	}
	c, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), time.Second)
	if err != nil {
		t.Fatal(err)
	}
	_ = c.SetDeadline(time.Now().Add(time.Second))
	_, _ = c.Write([]byte{5, 1, 2})
	reply := make([]byte, 2)
	_, err = io.ReadFull(c, reply)
	_ = c.Close()
	if err != nil || reply[0] != 5 || reply[1] != 2 {
		t.Fatal(reply, err)
	}
	if err = e.stop(); err != nil {
		t.Fatal(err)
	}
	if c, err = net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), time.Second); err == nil {
		c.Close()
		t.Fatal("listener survived stop")
	}
}
func TestTrafficDirectBlockExcluded(t *testing.T) {
	cfg, e := parse(minimalConfig)
	if e != nil {
		t.Fatal(e)
	}
	for _, name := range []string{"DIRECT", "REJECT"} {
		p := cfg.Proxies[name]
		if countedProxy(p, &nodeTraffic{}) != p {
			t.Fatal(name)
		}
	}
}
func TestMieruMtuProfileAndCleanup(t *testing.T) {
	for _, mtu := range []int{0, 1280, 1400, 1500} {
		t.Run(fmt.Sprint(mtu), func(t *testing.T) {
			cfg, e := parse(fmt.Sprintf("{\"proxies\":[{\"name\":\"proxy\",\"type\":\"mieru\",\"server\":\"127.0.0.1\",\"port\":7777,\"username\":\"u\",\"password\":\"p\",\"transport\":\"TCP\",\"mtu\":%d}]}", mtu))
			if e != nil {
				t.Fatal(e)
			}
			p, ok := cfg.Proxies["proxy"].(*mieruMTUProxy)
			if !ok {
				t.Fatal("shim absent")
			}
			loaded, e := p.client.Load()
			if e != nil || int(loaded.Profile.GetMtu()) != mtu || p.Name() != "proxy" {
				t.Fatal(loaded, e)
			}
			if e = p.Close(); e != nil {
				t.Fatal(e)
			}
		})
	}
}
func TestMieruMtuInvalidAndForcedDialerRetained(t *testing.T) {
	p, _ := adapter.ParseProxy(map[string]any{"name": "proxy", "type": "mieru", "server": "127.0.0.1", "port": 7777, "username": "u", "password": "p", "transport": "TCP"})
	if _, e := configureMieru(p, map[string]any{"mtu": 1279}); e == nil {
		t.Fatal("invalid MTU accepted")
	}
	cfg, e := parse("{\"proxies\":[{\"name\":\"front\",\"type\":\"socks5\",\"server\":\"127.0.0.1\",\"port\":1080},{\"name\":\"proxy\",\"type\":\"mieru\",\"server\":\"127.0.0.1\",\"port\":7777,\"username\":\"u\",\"password\":\"p\",\"transport\":\"TCP\",\"mtu\":1400,\"dialer-proxy\":\"front\"}]}")
	if e != nil {
		t.Fatal(e)
	}
	shim := cfg.Proxies["proxy"].(*mieruMTUProxy)
	defer shim.Close()
	loaded, e := shim.client.Load()
	if e != nil || loaded.Dialer == nil || loaded.PacketDialer == nil || loaded.DNSConfig.BypassDialerDNS {
		t.Fatal("chain bypass", e)
	}
}
