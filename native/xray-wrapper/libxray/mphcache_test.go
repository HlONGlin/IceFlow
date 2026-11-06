package libxray

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/features/routing"
	"github.com/xtls/xray-core/infra/conf/serial"
)

// routingTestConfig mirrors the shape of the config the app generates:
// domain rules carrying a ruleTag, a DNS server with a tag, and geosite data.
const routingTestConfig = `{
  "log": {"loglevel": "none"},
  "dns": {
    "servers": [
      {"address": "223.5.5.5", "domains": ["geosite:cn"], "tag": "domestic"},
      {"address": "1.1.1.1", "tag": "remote"}
    ],
    "queryStrategy": "UseIPv4"
  },
  "routing": {
    "domainStrategy": "AsIs",
    "rules": [
      {"type": "field", "ruleTag": "rule-cn-domain", "domain": ["geosite:cn"], "outboundTag": "direct"},
      {"type": "field", "ruleTag": "rule-example", "domain": ["domain:example.com"], "outboundTag": "block"},
      {"type": "field", "ruleTag": "rule-ads", "domain": ["full:ads.example.net"], "outboundTag": "block"}
    ]
  },
  "outbounds": [
    {"tag": "direct", "protocol": "freedom"},
    {"tag": "block", "protocol": "blackhole"}
  ]
}`

// routeContext is a minimal routing.Context for route picking.
type routeContext struct {
	domain string
}

func (c routeContext) GetInboundTag() string            { return "in" }
func (c routeContext) GetSourceIPs() []net.IP           { return nil }
func (c routeContext) GetSourcePort() net.Port          { return 0 }
func (c routeContext) GetTargetIPs() []net.IP           { return nil }
func (c routeContext) GetTargetPort() net.Port          { return 443 }
func (c routeContext) GetLocalIPs() []net.IP            { return nil }
func (c routeContext) GetLocalPort() net.Port           { return 0 }
func (c routeContext) GetTargetDomain() string          { return c.domain }
func (c routeContext) GetNetwork() net.Network          { return net.Network_TCP }
func (c routeContext) GetProtocol() string              { return "tls" }
func (c routeContext) GetUser() string                  { return "" }
func (c routeContext) GetVlessRoute() net.Port          { return 0 }
func (c routeContext) GetAttributes() map[string]string { return nil }
func (c routeContext) GetSkipDNSResolve() bool          { return true }

// pickRoutes returns the outbound tag chosen for every probe domain.
func pickRoutes(t *testing.T, configJSON string) map[string]string {
	t.Helper()
	config, err := serial.LoadJSONConfig(strings.NewReader(configJSON))
	if err != nil {
		t.Fatalf("parse config: %v", err)
	}
	inst, err := core.New(config)
	if err != nil {
		t.Fatalf("create instance: %v", err)
	}
	defer inst.Close()
	if err := inst.Start(); err != nil {
		t.Fatalf("start: %v", err)
	}
	router := inst.GetFeature(routing.RouterType()).(routing.Router)

	probes := []string{
		"www.baidu.com",
		"taobao.com",
		"example.com",
		"ads.example.net",
		"ads.example.net.",
		"www.google.com",
	}
	result := make(map[string]string, len(probes))
	for _, probe := range probes {
		route, err := router.PickRoute(routeContext{domain: probe})
		if err != nil {
			result[probe] = "error: " + err.Error()
			continue
		}
		result[probe] = route.GetOutboundTag()
	}
	return result
}

func TestMphCacheRoutingEquivalence(t *testing.T) {
	assetDir := "../../../app/src/main/assets/routing"
	if _, err := os.Stat(filepath.Join(assetDir, "geosite.dat")); err != nil {
		t.Skip("geosite.dat not available")
	}
	os.Setenv("xray.location.asset", assetDir)

	// Baseline: no cache at all.
	os.Unsetenv(mphEnvName())
	baseline := pickRoutes(t, routingTestConfig)

	// With the cache built and enabled.
	cacheDir := t.TempDir()
	for _, name := range []string{"geosite.dat", "geoip.dat"} {
		data, err := os.ReadFile(filepath.Join(assetDir, name))
		if err != nil {
			t.Skipf("%s unreadable", name)
		}
		if err := os.WriteFile(filepath.Join(cacheDir, name), data, 0o600); err != nil {
			t.Fatal(err)
		}
	}
	os.Setenv("xray.location.asset", assetDir)
	SetMphCacheDir(cacheDir)
	defer SetMphCacheDir("")

	disable := prepareMphCache(routingTestConfig)
	cached := pickRoutes(t, routingTestConfig)
	disable()

	if len(baseline) == 0 {
		t.Fatal("no routes picked")
	}
	for domain, want := range baseline {
		got, ok := cached[domain]
		if !ok {
			t.Fatalf("domain %q missing from cached result", domain)
		}
		if got != want {
			t.Errorf("domain %q: baseline routed to %q, cached routed to %q", domain, want, got)
		}
	}
	fmt.Printf("ROUTES baseline=%v\n", baseline)
	fmt.Printf("ROUTES cached  =%v\n", cached)
}
