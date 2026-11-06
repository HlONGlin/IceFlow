package libxray

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf/serial"
)

// benchConfig mirrors the config the app generates for the VPN.
const benchConfig = `{
  "log": {"loglevel": "none"},
  "dns": {
    "servers": [
      {"address": "223.5.5.5", "domains": ["geosite:cn"], "tag": "dns-domestic"},
      {"address": "1.1.1.1", "tag": "dns-remote"}
    ],
    "queryStrategy": "UseIPv4"
  },
  "routing": {
    "domainStrategy": "IPIfNonMatch",
    "rules": [
      {"type": "field", "ruleTag": "rule-cn-domain", "domain": ["geosite:cn"], "outboundTag": "direct"},
      {"type": "field", "ruleTag": "rule-cn-ip", "ip": ["geoip:cn"], "outboundTag": "direct"},
      {"type": "field", "ruleTag": "rule-private", "ip": ["geoip:private"], "outboundTag": "direct"}
    ]
  },
  "outbounds": [
    {"tag": "proxy", "protocol": "freedom"},
    {"tag": "direct", "protocol": "freedom"},
    {"tag": "block", "protocol": "blackhole"}
  ]
}`

func benchStart(t *testing.T, configJSON string) time.Duration {
	t.Helper()
	start := time.Now()
	config, err := serial.LoadJSONConfig(strings.NewReader(configJSON))
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	inst, err := core.New(config)
	if err != nil {
		t.Fatalf("new: %v", err)
	}
	defer inst.Close()
	if err := inst.Start(); err != nil {
		t.Fatalf("start: %v", err)
	}
	return time.Since(start)
}

func TestMphCacheSpeedup(t *testing.T) {
	assetDir := "../../../app/src/main/assets/routing"
	if _, err := os.Stat(filepath.Join(assetDir, "geosite.dat")); err != nil {
		t.Skip("geosite.dat not available")
	}
	os.Setenv("xray.location.asset", assetDir)

	// Without the cache.
	os.Unsetenv(mphEnvName())
	var uncached time.Duration
	for i := 0; i < 3; i++ {
		uncached = benchStart(t, benchConfig)
	}
	fmt.Printf("BENCH without cache: %v\n", uncached)

	// With the cache: the first start builds it, the later ones reuse it.
	cacheDir := t.TempDir()
	os.Setenv("xray.location.asset", assetDir)
	SetMphCacheDir(cacheDir)
	defer SetMphCacheDir("")

	disable := prepareMphCache(benchConfig)
	first := benchStart(t, benchConfig)
	disable()

	var cached time.Duration
	for i := 0; i < 3; i++ {
		disable = prepareMphCache(benchConfig)
		cached = benchStart(t, benchConfig)
		disable()
	}
	fmt.Printf("BENCH first start (builds cache): %v\n", first)
	fmt.Printf("BENCH with cache: %v\n", cached)
	fmt.Printf("BENCH speedup: %.1fx\n", float64(uncached)/float64(cached))
}
