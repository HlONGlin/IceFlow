// Package libmihomo is the single flavor's gomobile surface. It never imports Xray or Rust.
package libmihomo

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"strings"
	"sync"

	"github.com/metacubex/mihomo/component/proxydialer"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/listener"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

const pinnedVersion = "v1.19.32"

// Version is the audited module version, not an inferred upstream build string.
func Version() string { return pinnedVersion }

type tunSession interface {
	io.Closer
	healthy() bool
}

type engine struct {
	mu                 sync.Mutex
	tun                tunSession
	cfg                *config.Config
	lastError          string
	listeners          []C.InboundListener
	book               trafficBook
	selectedTag        string
	baseUp, baseDown   int64
	finalUp, finalDown int64
	openTun            func(LC.Tun, int) (tunSession, error)
	apply              func(*config.Config) error
	shutdown           func() error
	totals             func() (int64, int64)
}

var current = &engine{openTun: openTun, apply: applyConfig, shutdown: shutdown}

func init() {
	current.totals = func() (int64, int64) { return current.book.selected(current.selectedTag) }
}

// SetHomeDir must run before any parse/start. Paths are not logged by the wrapper.
func SetHomeDir(dir string) error {
	current.mu.Lock()
	defer current.mu.Unlock()
	if current.cfg != nil {
		return errors.New("CORE_START_FAILED: stop before changing home directory")
	}
	if strings.TrimSpace(dir) == "" {
		return errors.New("CONFIGURATION_OR_SUBSCRIPTION: home directory is empty")
	}
	if err := os.MkdirAll(dir, 0700); err != nil {
		return fmt.Errorf("CONFIGURATION_OR_SUBSCRIPTION: prepare assets: %w", err)
	}
	C.SetHomeDir(dir)
	return nil
}

func parse(text string) (*config.Config, error) {
	// The app seam supplies JSON. Inspect explicit requests before applying mihomo defaults:
	// its desktop defaults enable auto-route/auto-detect, which Android must not inherit.
	var requested struct {
		Tun struct {
			FileDescriptor int  `json:"file-descriptor"`
			AutoRoute      bool `json:"auto-route"`
			AutoDetect     bool `json:"auto-detect-interface"`
			AutoRedirect   bool `json:"auto-redirect"`
		} `json:"tun"`
	}
	if err := json.Unmarshal([]byte(text), &requested); err != nil {
		return nil, fmt.Errorf("CONFIGURATION_OR_SUBSCRIPTION: expected app JSON: %w", err)
	}
	if requested.Tun.FileDescriptor != 0 || requested.Tun.AutoRoute || requested.Tun.AutoDetect || requested.Tun.AutoRedirect {
		return nil, errors.New("CONFIGURATION_OR_SUBSCRIPTION: TUN fd and routes belong to VpnService")
	}
	if err := validateAppListeners(text); err != nil {
		return nil, fmt.Errorf("CONFIGURATION_OR_SUBSCRIPTION: %w", err)
	}
	raw, err := config.UnmarshalRawConfig([]byte(text))
	if err != nil {
		return nil, fmt.Errorf("CONFIGURATION_OR_SUBSCRIPTION: %w", err)
	}
	// Override only absent desktop defaults after rejecting explicit ownership conflicts.
	raw.Tun.Enable = false
	raw.Tun.AutoRoute = false
	raw.Tun.AutoDetectInterface = false
	raw.Tun.AutoRedirect = false
	if raw.ExternalController != "" || raw.ExternalControllerTLS != "" || raw.ExternalControllerUnix != "" || raw.ExternalControllerPipe != "" {
		return nil, errors.New("CONFIGURATION_OR_SUBSCRIPTION: external controllers are unsupported")
	}
	cfg, err := config.ParseRawConfig(raw)
	if err != nil {
		return nil, fmt.Errorf("CONFIGURATION_OR_SUBSCRIPTION: %w", err)
	}
	if len(cfg.Providers) != 1 || len(cfg.RuleProviders) > 0 || len(cfg.Tunnels) > 0 || cfg.General.Port != 0 || cfg.General.MixedPort != 0 || cfg.General.RedirPort != 0 || cfg.General.TProxyPort != 0 || cfg.General.ShadowSocksConfig != "" || cfg.General.VmessConfig != "" || cfg.General.TuicServer.Enable {
		return nil, errors.New("CONFIGURATION_OR_SUBSCRIPTION: unsupported provider or listener")
	}
	// MTU is an app shim extension; never claim upstream MieruOption consumes it.
	for _, options := range raw.Proxy {
		if options["type"] != "mieru" {
			continue
		}
		name, _ := options["name"].(string)
		var dialer C.Dialer
		if upstream, ok := options["dialer-proxy"].(string); ok && upstream != "" {
			dialer = proxydialer.NewByName(upstream, tunnel.Tunnel)
		}
		patched, patchErr := configureMieruWithDialer(cfg.Proxies[name], options, dialer)
		if patchErr != nil {
			for _, p := range cfg.Proxies {
				_ = p.Close()
			}
			return nil, fmt.Errorf("CONFIGURATION_OR_SUBSCRIPTION: mieru mtu: %w", patchErr)
		}
		cfg.Proxies[name] = patched
	}
	return cfg, nil
}

// Validate does not start listeners or take ownership of any descriptor.
func Validate(text string) error {
	cfg, err := parse(text)
	if cfg != nil {
		for _, p := range cfg.Proxies {
			_ = p.Close()
		}
	}
	return err
}

// Prepare is the service pre-TUN phase. No descriptor is borrowed until AttachTun.
func Prepare(text string) error {
	current.mu.Lock()
	defer current.mu.Unlock()
	return current.prepare(text)
}
func (e *engine) prepare(text string) error {
	if e.cfg != nil {
		return errors.New("CORE_START_FAILED: core is already running")
	}
	cfg, err := parse(text)
	if err != nil {
		return e.failed(err, nil)
	}
	e.book = trafficBook{}
	var metadata struct {
		Selected string `json:"iceflow-selected-outbound"`
	}
	_ = json.Unmarshal([]byte(text), &metadata)
	e.selectedTag = metadata.Selected
	if e.selectedTag == "" {
		e.selectedTag = "proxy"
	}
	for name, p := range cfg.Proxies {
		if p.Type() <= C.Dns || (name != "proxy" && !strings.HasPrefix(name, "chain-")) {
			continue
		}
		counter := &nodeTraffic{}
		e.book[name] = counter
		cfg.Proxies[name] = countedProxy(p, counter)
	}
	if metadata.Selected != "" && e.book[e.selectedTag] == nil {
		for _, p := range cfg.Proxies {
			_ = p.Close()
		}
		return e.failed(errors.New("CONFIGURATION_OR_SUBSCRIPTION: invalid selected outbound"), nil)
	}
	e.baseUp, e.baseDown = e.totals()
	e.finalUp, e.finalDown = 0, 0
	// Upstream PatchInboundListeners swallows bind errors. Own these validated listeners.
	listeners := cfg.Listeners
	cfg.Listeners = nil
	if err = e.apply(cfg); err != nil {
		cleanup := e.shutdown()
		for _, p := range cfg.Proxies {
			cleanup = errors.Join(cleanup, p.Close())
		}
		return e.failed(fmt.Errorf("CORE_START_FAILED: apply config: %w", err), cleanup)
	}
	e.cfg = cfg
	for _, in := range listeners {
		e.listeners = append(e.listeners, in) // include partially opened TCP/UDP listener in cleanup
		if err = in.Listen(tunnel.Tunnel); err != nil {
			primary := fmt.Errorf("CORE_START_FAILED: app SOCKS listener: %w", err)
			cleanup := e.stopLocked()
			return e.failed(primary, cleanup)
		}
	}
	e.lastError = ""
	return nil
}

// AttachTun borrows original; openTun duplicates it and closes only the duplicate.
func AttachTun(fd, mtu int) error {
	current.mu.Lock()
	defer current.mu.Unlock()
	return current.attach(fd, mtu)
}
func (e *engine) attach(fd, mtu int) error {
	if e.cfg == nil {
		return e.failed(errors.New("CORE_START_FAILED: prepare core before TUN handoff"), nil)
	}
	if e.tun != nil {
		return e.failed(errors.New("CORE_START_FAILED: TUN already attached"), nil)
	}
	if fd <= 0 {
		return e.failed(errors.New("CORE_START_FAILED: invalid borrowed TUN fd"), nil)
	}
	options := e.cfg.General.Tun
	if mtu != int(options.MTU) && options.MTU != 0 {
		return e.failed(errors.New("CONFIGURATION_OR_SUBSCRIPTION: service TUN MTU mismatch"), nil)
	}
	options.MTU = uint32(mtu)
	session, err := e.openTun(options, fd)
	if err != nil {
		primary := fmt.Errorf("CORE_START_FAILED: start TUN: %w", err)
		cleanup := e.stopLocked()
		return e.failed(primary, cleanup)
	}
	e.tun = session
	// The service resets its persisted deltas after each successful handoff, including reuse.
	e.baseUp, e.baseDown = e.totals()
	e.finalUp, e.finalDown = 0, 0
	e.lastError = ""
	return nil
}

// StopTun leaves configuration/listeners alive for the existing service fast reconnect.
func StopTun() error { current.mu.Lock(); defer current.mu.Unlock(); return current.stopTun() }
func (e *engine) stopTun() error {
	if e.tun == nil {
		return nil
	}
	err := e.tun.Close()
	e.tun = nil
	if err != nil {
		return e.failed(fmt.Errorf("RESOURCE_CLEANUP_FAILED: stop TUN: %w", err), nil)
	}
	return nil
}
func CoreRunning() bool {
	current.mu.Lock()
	defer current.mu.Unlock()
	return current.cfg != nil && tunnel.Status() == tunnel.Running
}
func QueryStats() string { current.mu.Lock(); defer current.mu.Unlock(); return current.book.text() }

// Start borrows the service's original fd. openTun duplicates it and owns ONLY that duplicate.
// Parse/apply/TUN failures propagate with their primary classification, then cleanup is appended.
// The original fd remains the service's responsibility on every path, including failed start.
func Start(text string, fd int) error { return current.start(text, fd) }
func (e *engine) start(text string, fd int) error {
	e.mu.Lock()
	defer e.mu.Unlock()
	if fd <= 0 {
		return e.failed(errors.New("CORE_START_FAILED: invalid borrowed TUN fd"), nil)
	}
	if err := e.prepare(text); err != nil {
		return err
	}
	return e.attach(fd, 1500)
}
func (e *engine) failed(primary, cleanup error) error {
	if cleanup != nil {
		primary = fmt.Errorf("%w; cleanup: %s", primary, strings.TrimPrefix(cleanup.Error(), "RESOURCE_CLEANUP_FAILED: "))
	}
	e.lastError = primary.Error()
	return primary
}

// Stop closes the duplicate TUN and listeners, retaining the final byte counters for persistence.
func Stop() error { return current.stop() }
func (e *engine) stop() error {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.stopLocked()
}
func (e *engine) stopLocked() error {
	if e.cfg == nil && e.tun == nil {
		return nil
	}
	up, down := e.totals()
	e.finalUp = max(0, up-e.baseUp)
	e.finalDown = max(0, down-e.baseDown)
	var errs []error
	if e.tun != nil {
		errs = append(errs, e.tun.Close())
		e.tun = nil
	}
	for _, in := range e.listeners {
		errs = append(errs, in.Close())
	}
	e.listeners = nil
	errs = append(errs, e.shutdown())
	e.cfg = nil
	err := errors.Join(errs...)
	if err != nil {
		e.lastError = "RESOURCE_CLEANUP_FAILED: " + err.Error()
		return errors.New(e.lastError)
	}
	return nil
}

func IsRunning() bool {
	current.mu.Lock()
	defer current.mu.Unlock()
	return current.cfg != nil && current.tun != nil && current.tun.healthy() && tunnel.Status() == tunnel.Running
}
func LastError() string { current.mu.Lock(); defer current.mu.Unlock(); return current.lastError }

// StatsJSON returns cumulative session byte counts, never one-second rates or reset-on-read.
func StatsJSON() string {
	current.mu.Lock()
	defer current.mu.Unlock()
	up, down := current.finalUp, current.finalDown
	if current.cfg != nil {
		u, d := current.totals()
		up = max(0, u-current.baseUp)
		down = max(0, d-current.baseDown)
	}
	b, _ := json.Marshal(map[string]int64{"upload": up, "download": down})
	return string(b)
}

func applyConfig(cfg *config.Config) error {
	executor.ApplyConfig(cfg, true)
	if cfg.General.SocksPort != 0 && listener.GetPorts().SocksPort != cfg.General.SocksPort {
		return errors.New("local SOCKS listener did not start")
	}
	return nil
}
func shutdown() error {
	// executor.Shutdown only closes the singleton TUN; explicitly remove listeners and sessions too.
	tunnel.OnSuspend()
	var errs []error
	statistic.DefaultManager.Range(func(c statistic.Tracker) bool { errs = append(errs, c.Close()); return true })
	listener.ReCreateSocks(0, tunnel.Tunnel)
	listener.PatchInboundListeners(nil, tunnel.Tunnel, true)
	executor.Shutdown()
	for _, p := range tunnel.Proxies() {
		errs = append(errs, p.Close())
	}
	return errors.Join(errs...)
}
