//go:build android

package libmihomo

import (
	"context"
	"errors"
	"fmt"
	"net/netip"
	"sync/atomic"
	"time"

	"github.com/metacubex/gvisor/pkg/tcpip/stack"
	"github.com/metacubex/mihomo/adapter/inbound"
	C "github.com/metacubex/mihomo/constant"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/listener/sing"
	singtun "github.com/metacubex/mihomo/listener/sing_tun"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
	tun "github.com/metacubex/sing-tun"
	"golang.org/x/sys/unix"
)

type ownedTun struct {
	tun.LinuxTUN
	gvisor  tun.GVisorTun
	stack   tun.Stack
	failed  atomic.Bool
	closing atomic.Bool
}

// Keep the extra gVisor methods: embedding only LinuxTUN hides NativeTun.NewEndpoint.
var _ tun.GVisorTun = (*ownedTun)(nil)

func (t *ownedTun) NewEndpoint() (stack.LinkEndpoint, stack.NICOptions, error) {
	return t.gvisor.NewEndpoint()
}
func (t *ownedTun) WritePacket(p *stack.PacketBuffer) (int, error) {
	n, err := t.gvisor.WritePacket(p)
	if err != nil && !t.closing.Load() {
		t.failed.Store(true)
	}
	return n, err
}

func (t *ownedTun) healthy() bool { return !t.failed.Load() && !t.closing.Load() }
func (t *ownedTun) Read(p []byte) (int, error) {
	n, e := t.LinuxTUN.Read(p)
	if e != nil && !t.closing.Load() {
		t.failed.Store(true)
	}
	return n, e
}
func (t *ownedTun) BatchRead(p [][]byte, o int, n []int) (int, error) {
	v, e := t.LinuxTUN.BatchRead(p, o, n)
	if e != nil && !t.closing.Load() {
		t.failed.Store(true)
	}
	return v, e
}
func (t *ownedTun) Close() error {
	if t.closing.Swap(true) {
		return nil
	}
	fdErr := t.LinuxTUN.Close()
	var stackErr error
	if t.stack != nil {
		stackErr = t.stack.Close()
	}
	return errors.Join(fdErr, stackErr)
}
func openTun(options LC.Tun, original int) (tunSession, error) {
	fd, err := unix.Dup(original)
	if err != nil {
		return nil, fmt.Errorf("duplicate borrowed fd: %w", err)
	}
	unix.CloseOnExec(fd)
	opts := tun.Options{Name: "iceflow", MTU: options.MTU, FileDescriptor: fd, Inet4Address: options.Inet4Address, Inet6Address: options.Inet6Address}
	if opts.MTU == 0 {
		opts.MTU = 1500
	}
	if len(opts.Inet4Address) == 0 {
		opts.Inet4Address = []netip.Prefix{netip.MustParsePrefix("172.19.0.1/30")}
	}
	device, err := tun.New(opts)
	if err != nil {
		unix.Close(fd)
		return nil, err
	}
	linux, ok := device.(tun.LinuxTUN)
	if !ok {
		device.Close()
		return nil, errors.New("Android TUN is not LinuxTUN")
	}
	gvisor, ok := device.(tun.GVisorTun)
	if !ok {
		device.Close()
		return nil, errors.New("Android TUN lacks gVisor endpoint methods")
	}
	owned := &ownedTun{LinuxTUN: linux, gvisor: gvisor}
	base, err := sing.NewListenerHandler(sing.ListenerConfig{Tunnel: tunnel.Tunnel, Type: C.TUN, Additions: []inbound.Addition{inbound.WithInName("tun-in")}})
	if err != nil {
		owned.Close()
		return nil, err
	}
	var hijack []netip.AddrPort
	for _, value := range options.DNSHijack {
		if value == "any:53" {
			value = "0.0.0.0:53"
		}
		addr, e := netip.ParseAddrPort(value)
		if e != nil {
			owned.Close()
			return nil, fmt.Errorf("invalid DNS hijack: %w", e)
		}
		hijack = append(hijack, addr)
	}
	handler := &singtun.ListenerHandler{ListenerHandler: base, DnsAddrPorts: hijack, Inet4Address: opts.Inet4Address, Inet6Address: opts.Inet6Address, DisableICMPForwarding: true}
	stack, err := tun.NewStack("gvisor", tun.StackOptions{Context: context.Background(), Tun: owned, TunOptions: opts, Handler: handler, Logger: log.SingLogger, UDPTimeout: time.Minute, ICMPTimeout: 10 * time.Second})
	if err != nil {
		owned.Close()
		return nil, err
	}
	owned.stack = stack
	if err = stack.Start(); err != nil {
		owned.Close()
		return nil, err
	}
	return owned, nil
}
