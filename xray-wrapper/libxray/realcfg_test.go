package libxray

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/features/routing"
	"github.com/xtls/xray-core/infra/conf/serial"
)

// realAppConfig mirrors the config the app generates (see the Kotlin
// XrayConfigBuilder), with trivial outbounds so that no real key is needed.
const realAppConfig = `{
  "log": {"loglevel": "none"},
  "dns": {
    "servers": [
      {"address": "223.5.5.5", "tag": "dns-domestic", "domains": ["geosite:cn", "geosite:cn"]},
      {"address": "1.1.1.1", "tag": "dns-remote"}
    ],
    "queryStrategy": "UseIPv4"
  },
  "routing": {
    "domainStrategy": "IPIfNonMatch",
    "rules": [
      {"type": "field", "outboundTag": "direct", "ruleTag": "rule-builtin-cn-domain", "domain": ["geosite:cn"]},
      {"type": "field", "outboundTag": "direct", "ip": ["geoip:cn"]},
      {"type": "field", "outboundTag": "direct", "ip": ["10.0.0.0/8", "127.0.0.0/8"]}
    ]
  },
  "outbounds": [
    {"tag": "proxy", "protocol": "freedom"},
    {"tag": "direct", "protocol": "freedom"},
    {"tag": "block", "protocol": "blackhole"}
  ]
}`

func TestRealAppConfigWithCache(t *testing.T) {
	assetDir := "../../app/src/main/assets/routing"
	if _, err := os.Stat(filepath.Join(assetDir, "geosite.dat")); err != nil {
		t.Skip("no geosite")
	}
	os.Setenv("xray.location.asset", assetDir)

	probe := func() (time.Duration, string) {
		start := time.Now()
		cfg, err := serial.LoadJSONConfig(strings.NewReader(realAppConfig))
		if err != nil {
			t.Fatalf("parse: %v", err)
		}
		inst, err := core.New(cfg)
		if err != nil {
			t.Fatalf("new: %v", err)
		}
		defer inst.Close()
		if err := inst.Start(); err != nil {
			t.Fatalf("start: %v", err)
		}
		r := inst.GetFeature(routing.RouterType()).(routing.Router)
		route, err := r.PickRoute(routeContext{domain: "www.baidu.com"})
		if err != nil {
			t.Fatalf("pick: %v", err)
		}
		return time.Since(start), route.GetOutboundTag()
	}

	os.Unsetenv(mphEnvName())
	noCache, tag1 := probe()

	cacheDir := t.TempDir()
	SetMphCacheDir(cacheDir)
	defer SetMphCacheDir("")

	disable := prepareMphCache(realAppConfig)
	_, _ = probe() // first run builds the cache
	disable()

	var withCache time.Duration
	var tag2 string
	for i := 0; i < 3; i++ {
		disable = prepareMphCache(realAppConfig)
		withCache, tag2 = probe()
		disable()
	}

	fmt.Printf("REAL no-cache=%v with-cache=%v speedup=%.1fx\n", noCache, withCache, float64(noCache)/float64(withCache))
	fmt.Printf("REAL route no-cache=%s with-cache=%s\n", tag1, tag2)
	if tag1 != tag2 {
		t.Errorf("routing differs: %q vs %q", tag1, tag2)
	}
}
