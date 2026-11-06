package libxray

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/netip"
	"strings"
	"time"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/common/net/deadline"
	C "github.com/metacubex/mihomo/constant"
	"github.com/xtls/xray-core/common"
	"github.com/xtls/xray-core/common/buf"
	XN "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/common/serial"
	"github.com/xtls/xray-core/common/session"
	"github.com/xtls/xray-core/common/task"
	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/features/outbound"
	"github.com/xtls/xray-core/transport"
)

// installExtended replaces only the proxy outbound; Xray retains SOCKS, DNS and routing.
func installExtended(inst *core.Instance, configJSON string) error {
	var config struct {
		Extended map[string]any `json:"iceflowAdapter"`
		Adapters []struct {
			Tag      string         `json:"tag"`
			Upstream string         `json:"upstream"`
			Options  map[string]any `json:"options"`
		} `json:"iceflowAdapters"`
	}
	decoder := json.NewDecoder(strings.NewReader(configJSON))
	decoder.UseNumber()
	if err := decoder.Decode(&config); err != nil {
		return err
	}
	if config.Extended != nil {
		if len(config.Adapters) != 0 {
			return fmt.Errorf("ambiguous adapter configuration")
		}
		return installAdapter(inst, "proxy", "", config.Extended)
	}
	seen := map[string]bool{}
	for _, item := range config.Adapters {
		if item.Tag == "" || seen[item.Tag] || item.Tag == item.Upstream {
			return fmt.Errorf("invalid adapter chain tag")
		}
		seen[item.Tag] = true
		if err := installAdapter(inst, item.Tag, item.Upstream, item.Options); err != nil {
			return err
		}
	}
	return nil
}

func installAdapter(inst *core.Instance, tag, upstream string, options map[string]any) error {
	options = adapterNumbers(options).(map[string]any)
	kind, _ := options["type"].(string)
	switch kind {
	case "vmess", "vless", "trojan", "ss", "hysteria", "hysteria2", "tuic", "wireguard", "ssr", "http", "socks5", "mieru", "anytls", "sudoku":
	default:
		return fmt.Errorf("unsupported IceFlow adapter: %s", kind)
	}
	var parseOptions []adapter.ProxyOption
	var dialer C.Dialer
	if upstream != "" {
		manager := inst.GetFeature(outbound.ManagerType()).(outbound.Manager)
		if manager.GetHandler(upstream) == nil {
			return fmt.Errorf("missing upstream outbound %s", upstream)
		}
		dialer = &taggedDialer{instance: inst, tag: upstream}
		parseOptions = append(parseOptions, adapter.WithDialerForAPI(dialer))
	}
	proxy, err := adapter.ParseProxy(options, parseOptions...)
	if err != nil {
		return fmt.Errorf("create %s adapter: %w", kind, err)
	}
	if kind == "mieru" {
		proxy, err = configureMieruWithDialer(proxy, options, dialer)
		if err != nil {
			return err
		}
	}
	manager := inst.GetFeature(outbound.ManagerType()).(outbound.Manager)
	old := manager.GetHandler(tag)
	if old == nil {
		_ = proxy.Close()
		return fmt.Errorf("missing proxy outbound")
	}
	if err := manager.RemoveHandler(context.Background(), tag); err != nil {
		_ = proxy.Close()
		return err
	}
	_ = old.Close()
	if err := manager.AddHandler(context.Background(), &extendedHandler{proxy: proxy, tag: tag}); err != nil {
		_ = proxy.Close()
		return err
	}
	return nil
}

// YAML integers must remain integers: Mihomo string options (e.g. bandwidth)
// otherwise receive scientific notation when JSON float64 values are cast.
func adapterNumbers(value any) any {
	switch v := value.(type) {
	case json.Number:
		if integer, err := v.Int64(); err == nil {
			return integer
		}
		if number, err := v.Float64(); err == nil {
			return number
		}
	case map[string]any:
		for key, item := range v {
			v[key] = adapterNumbers(item)
		}
	case []any:
		for index, item := range v {
			v[index] = adapterNumbers(item)
		}
	}
	return value
}

type extendedHandler struct {
	proxy C.Proxy
	tag   string
}

func (h *extendedHandler) Tag() string                          { return h.tag }
func (h *extendedHandler) Start() error                         { return nil }
func (h *extendedHandler) Close() error                         { return h.proxy.Close() }
func (h *extendedHandler) SenderSettings() *serial.TypedMessage { return nil }
func (h *extendedHandler) ProxySettings() *serial.TypedMessage  { return nil }

// Force the previous hop even for private endpoints; bypass-LAN rules apply only
// to the original destination, never to a proxy server inside the chain.
type taggedDialer struct {
	instance *core.Instance
	tag      string
}

func (d *taggedDialer) context(ctx context.Context) context.Context {
	ctx = session.ContextWithContent(ctx, &session.Content{SkipDNSResolve: true})
	return session.SetForcedOutboundTagToContext(ctx, d.tag)
}
func (d *taggedDialer) DialContext(ctx context.Context, network, address string) (net.Conn, error) {
	dest, err := XN.ParseDestination(network + ":" + address)
	if err != nil {
		return nil, err
	}
	return core.Dial(d.context(ctx), d.instance, dest)
}
func (d *taggedDialer) ListenPacket(ctx context.Context, network, address string, remote netip.AddrPort) (net.PacketConn, error) {
	pc, err := core.DialUDP(d.context(ctx), d.instance)
	if err != nil {
		return nil, err
	}
	return deadline.NewNetPacketConn(pc), nil
}

func metadataFor(dest XN.Destination) (*C.Metadata, error) {
	m := &C.Metadata{NetWork: C.TCP}
	if dest.Network == XN.Network_UDP {
		m.NetWork = C.UDP
	}
	if err := m.SetRemoteAddress(dest.NetAddr()); err != nil {
		return nil, err
	}
	return m, nil
}

func (h *extendedHandler) Dispatch(ctx context.Context, link *transport.Link) {
	defer common.Close(link.Writer)
	defer common.Interrupt(link.Reader)
	outs := session.OutboundsFromContext(ctx)
	if len(outs) == 0 {
		return
	}
	dest := outs[len(outs)-1].Target
	metadata, err := metadataFor(dest)
	if err != nil {
		return
	}
	dialCtx, cancel := context.WithTimeout(ctx, 15*time.Second)
	defer cancel()
	if dest.Network == XN.Network_TCP {
		conn, err := h.proxy.DialContext(dialCtx, metadata)
		if err != nil {
			return
		}
		defer conn.Close()
		stop := context.AfterFunc(ctx, func() { _ = conn.Close(); common.Interrupt(link.Reader) })
		defer stop()
		_ = task.Run(ctx,
			func() error {
				err := buf.Copy(link.Reader, buf.NewWriter(conn))
				if cw, ok := any(conn).(interface{ CloseWrite() error }); ok && err == nil {
					_ = cw.CloseWrite()
				} else {
					_ = conn.Close()
				}
				return err
			},
			func() error { defer common.Interrupt(link.Reader); return buf.Copy(buf.NewReader(conn), link.Writer) },
		)
		return
	}
	if !h.proxy.SupportUDP() {
		return
	}
	pc, err := h.proxy.ListenPacketContext(dialCtx, metadata)
	if err != nil {
		return
	}
	defer pc.Close()
	stop := context.AfterFunc(ctx, func() { _ = pc.Close(); common.Interrupt(link.Reader) })
	defer stop()
	_ = task.Run(ctx,
		func() error {
			defer pc.Close()
			for {
				mb, readErr := link.Reader.ReadMultiBuffer()
				var writeErr error
				for _, b := range mb {
					target := dest
					if b.UDP != nil {
						target = *b.UDP
					}
					m, err := metadataFor(target)
					if err == nil {
						err = pc.ResolveUDP(ctx, m)
					}
					if err == nil {
						_, err = pc.WriteTo(b.Bytes(), m.UDPAddr())
					}
					if err != nil {
						writeErr = err
						break
					}
				}
				buf.ReleaseMulti(mb)
				if writeErr != nil {
					return writeErr
				}
				if readErr != nil {
					return readErr
				}
			}
		},
		func() error {
			defer common.Interrupt(link.Reader)
			// Preserve one datagram per buffer, including its source address.
			packet := make([]byte, 65535)
			for {
				_ = pc.SetReadDeadline(time.Now().Add(2 * time.Minute))
				n, addr, err := pc.ReadFrom(packet)
				if err != nil {
					return err
				}
				b := buf.NewWithSize(int32(n))
				_, _ = b.Write(packet[:n])
				source := XN.DestinationFromAddr(addr)
				b.UDP = &source
				if err := link.Writer.WriteMultiBuffer(buf.MultiBuffer{b}); err != nil {
					return err
				}
			}
		},
	)
}
