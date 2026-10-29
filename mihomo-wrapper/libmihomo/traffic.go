package libmihomo

import (
	"context"
	"fmt"
	"io"
	"net"
	"sort"
	"strings"
	"sync/atomic"

	"github.com/metacubex/mihomo/common/buf"
	N "github.com/metacubex/mihomo/common/net"
	C "github.com/metacubex/mihomo/constant"
)

// One counter pair per configured node, shared by all its live and closed connections.
// No registry polling, close-time re-addition or sum of nested chain counters.
type nodeTraffic struct{ upload, download atomic.Int64 }
type trafficBook map[string]*nodeTraffic

func (b trafficBook) selected(tag string) (int64, int64) {
	if c := b[tag]; c != nil {
		return c.upload.Load(), c.download.Load()
	}
	return 0, 0
}
func (b trafficBook) text() string {
	keys := make([]string, 0, len(b))
	for k := range b {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	var out strings.Builder
	for _, k := range keys {
		fmt.Fprintf(&out, "outbound>>>%s>>>traffic>>>uplink,%d\noutbound>>>%s>>>traffic>>>downlink,%d\n", k, b[k].upload.Load(), k, b[k].download.Load())
	}
	return out.String()
}
func countedProxy(p C.Proxy, c *nodeTraffic) C.Proxy {
	if p.Type() <= C.Dns {
		return p
	}
	return &trafficProxy{Proxy: p, counter: c}
}

type trafficProxy struct {
	C.Proxy
	counter *nodeTraffic
}

func (p *trafficProxy) DialContext(ctx context.Context, m *C.Metadata) (C.Conn, error) {
	c, err := p.Proxy.DialContext(ctx, m)
	if err != nil {
		return nil, err
	}
	return &trafficConn{Conn: c, counter: p.counter}, nil
}
func (p *trafficProxy) ListenPacketContext(ctx context.Context, m *C.Metadata) (C.PacketConn, error) {
	c, err := p.Proxy.ListenPacketContext(ctx, m)
	if err != nil {
		return nil, err
	}
	return &trafficPacket{PacketConn: c, counter: p.counter}, nil
}

type trafficConn struct {
	C.Conn
	counter *nodeTraffic
}

func (c *trafficConn) Read(b []byte) (int, error) {
	n, e := c.Conn.Read(b)
	c.counter.download.Add(int64(n))
	return n, e
}
func (c *trafficConn) Write(b []byte) (int, error) {
	n, e := c.Conn.Write(b)
	c.counter.upload.Add(int64(n))
	return n, e
}
func (c *trafficConn) ReadBuffer(b *buf.Buffer) error {
	before := b.Len()
	e := c.Conn.ReadBuffer(b)
	c.counter.download.Add(int64(max(0, b.Len()-before)))
	return e
}
func (c *trafficConn) WriteBuffer(b *buf.Buffer) error {
	n := b.Len()
	e := c.Conn.WriteBuffer(b)
	if e == nil {
		c.counter.upload.Add(int64(n))
	}
	return e
}

// Optimized copies unwrap once and invoke these counters instead of Read/Write.
func (c *trafficConn) UnwrapReader() (io.Reader, []N.CountFunc) {
	return c.Conn, []N.CountFunc{func(n int64) { c.counter.download.Add(n) }}
}
func (c *trafficConn) UnwrapWriter() (io.Writer, []N.CountFunc) {
	return c.Conn, []N.CountFunc{func(n int64) { c.counter.upload.Add(n) }}
}
func (c *trafficConn) ReaderReplaceable() bool { return false }
func (c *trafficConn) WriterReplaceable() bool { return false }

type trafficPacket struct {
	C.PacketConn
	counter *nodeTraffic
}

func (c *trafficPacket) ReadFrom(b []byte) (int, net.Addr, error) {
	n, a, e := c.PacketConn.ReadFrom(b)
	c.counter.download.Add(int64(n))
	return n, a, e
}
func (c *trafficPacket) WriteTo(b []byte, a net.Addr) (int, error) {
	n, e := c.PacketConn.WriteTo(b, a)
	c.counter.upload.Add(int64(n))
	return n, e
}
func (c *trafficPacket) WaitReadFrom() ([]byte, func(), net.Addr, error) {
	b, p, a, e := c.PacketConn.WaitReadFrom()
	c.counter.download.Add(int64(len(b)))
	return b, p, a, e
}
func (c *trafficPacket) ReaderReplaceable() bool { return false }
func (c *trafficPacket) WriterReplaceable() bool { return false }
