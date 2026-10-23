package libxray

import (
	"context"
	"fmt"
	"net"
	"net/netip"
	"net/url"
	"strconv"
	"strings"

	mieruclient "github.com/enfein/mieru/v3/apis/client"
	mierucommon "github.com/enfein/mieru/v3/apis/common"
	mierumodel "github.com/enfein/mieru/v3/apis/model"
	"github.com/enfein/mieru/v3/pkg/appctl"
	"github.com/metacubex/mihomo/adapter/outbound"
	N "github.com/metacubex/mihomo/common/net"
	C "github.com/metacubex/mihomo/constant"
)

// NormalizeMieruLink converts an official protobuf config URL into simple profile URLs.
// Keep the upstream protobuf schema and codec in Go rather than duplicating them in Android.
func NormalizeMieruLink(link string) (string, error) {
	if len(link) > 4*1024*1024 {
		return "", fmt.Errorf("mieru link exceeds 4MiB")
	}
	config, err := appctl.URLToClientConfig(link)
	if err != nil {
		return "", fmt.Errorf("decode mieru link: %w", err)
	}
	if len(config.GetProfiles()) == 0 || len(config.GetProfiles()) > 1024 {
		return "", fmt.Errorf("mieru configuration must contain 1-1024 profiles")
	}
	var links []string
	for _, profile := range config.GetProfiles() {
		urls, err := appctl.ClientProfileToMultiURLs(profile)
		if err != nil {
			return "", fmt.Errorf("convert mieru profile: %w", err)
		}
		links = append(links, urls...)
		if len(links) > 4096 {
			return "", fmt.Errorf("too many mieru servers")
		}
	}
	return strings.Join(links, "\n"), nil
}

// Mihomo's options omit MTU. Use the official client API when it is explicitly set,
// retaining the validated adapter's metadata and the surrounding Xray routing.
func configureMieru(proxy C.Proxy, options map[string]any) (C.Proxy, error) {
	return configureMieruWithDialer(proxy, options, nil)
}
func configureMieruWithDialer(proxy C.Proxy, options map[string]any, dialer C.Dialer) (C.Proxy, error) {
	rawMTU, exists := options["mtu"]
	if !exists {
		return proxy, nil
	}
	fail := func(err error) (C.Proxy, error) { _ = proxy.Close(); return nil, err }
	mtu, err := strconv.Atoi(fmt.Sprint(rawMTU))
	if err != nil || (mtu != 0 && (mtu < 1280 || mtu > 1500)) {
		return fail(fmt.Errorf("invalid mieru MTU"))
	}
	q := url.Values{"profile": {"iceflow-proxy"}, "mtu": {strconv.Itoa(mtu)}}
	for _, key := range []string{"multiplexing", "handshake-mode", "traffic-pattern"} {
		if value, ok := options[key].(string); ok && value != "" {
			q.Set(key, value)
		}
	}
	q.Set("protocol", fmt.Sprint(options["transport"]))
	if ports, ok := options["port-range"].(string); ok && ports != "" {
		q.Set("port", ports)
	} else {
		q.Set("port", fmt.Sprint(options["port"]))
	}
	host := fmt.Sprint(options["server"])
	if strings.Contains(host, ":") {
		host = "[" + host + "]"
	}
	u := &url.URL{Scheme: "mierus", Host: host, User: url.UserPassword(fmt.Sprint(options["username"]), fmt.Sprint(options["password"])), RawQuery: q.Encode()}
	profile, err := appctl.URLToClientProfile(u.String())
	if err != nil {
		return fail(err)
	}
	client := mieruclient.NewClient()
	config := &mieruclient.ClientConfig{Profile: profile, Resolver: net.DefaultResolver, DNSConfig: &mierucommon.ClientDNSConfig{BypassDialerDNS: true}}
	if dialer != nil {
		config.Dialer = dialer
		config.PacketDialer = mieruChainPacketDialer{dialer}
		config.DNSConfig.BypassDialerDNS = false
	}
	if err := client.Store(config); err != nil {
		return fail(err)
	}
	if err := client.Start(); err != nil {
		return fail(err)
	}
	base := outbound.NewBase(outbound.BaseOption{Name: "iceflow-proxy", Type: C.Mieru, UDP: true})
	return &mieruMTUProxy{Proxy: proxy, client: client, base: base}, nil
}

type mieruChainPacketDialer struct{ C.Dialer }

func (d mieruChainPacketDialer) ListenPacket(ctx context.Context, network, local, remote string) (net.PacketConn, error) {
	// The upstream packet tunnel supports arbitrary destinations through WriteTo.
	return d.Dialer.ListenPacket(ctx, network, local, netip.AddrPort{})
}

type mieruMTUProxy struct {
	C.Proxy
	client mieruclient.Client
	base   *outbound.Base
}

func (p *mieruMTUProxy) DialContext(ctx context.Context, metadata *C.Metadata) (C.Conn, error) {
	addr := mierumodel.NetAddrSpec{Net: "tcp", AddrSpec: mierumodel.AddrSpec{Port: int(metadata.DstPort)}}
	if metadata.Host != "" {
		addr.FQDN = metadata.Host
	} else {
		addr.IP = metadata.DstIP.AsSlice()
	}
	conn, err := p.client.DialContext(ctx, addr)
	if err != nil {
		return nil, err
	}
	return outbound.NewConn(conn, p), nil
}

func (p *mieruMTUProxy) ListenPacketContext(ctx context.Context, metadata *C.Metadata) (C.PacketConn, error) {
	if err := p.base.ResolveUDP(ctx, metadata); err != nil {
		return nil, err
	}
	conn, err := p.client.DialContext(ctx, metadata.UDPAddr())
	if err != nil {
		return nil, err
	}
	return outbound.NewPacketConn(N.NewThreadSafePacketConn(mierucommon.NewUDPAssociateWrapper(mierucommon.NewPacketOverStreamTunnel(conn))), p.base), nil
}

func (p *mieruMTUProxy) Close() error {
	clientErr := p.client.Stop()
	proxyErr := p.Proxy.Close()
	if clientErr != nil {
		return clientErr
	}
	return proxyErr
}
