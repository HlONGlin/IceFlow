package libxray

import (
	"os"
	"strings"
	"testing"
)

// untaggedConfig has a domain rule without a ruleTag. xray-core looks the
// matcher up by tag and fails hard when the tag is missing, so the cache must
// not be used for such a config.
const untaggedConfig = `{
  "log": {"loglevel": "none"},
  "routing": {
    "rules": [
      {"type": "field", "domain": ["domain:example.com"], "outboundTag": "direct"}
    ]
  },
  "outbounds": [{"tag": "direct", "protocol": "freedom"}]
}`

const taggedConfig = `{
  "log": {"loglevel": "none"},
  "routing": {
    "rules": [
      {"type": "field", "ruleTag": "rule-example", "domain": ["domain:example.com"], "outboundTag": "direct"}
    ]
  },
  "outbounds": [{"tag": "direct", "protocol": "freedom"}]
}`

func TestMphSummaryCacheable(t *testing.T) {
	cases := []struct {
		name   string
		config string
		want   bool
	}{
		{"untagged rule", untaggedConfig, false},
		{"tagged rule", taggedConfig, true},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			summary, err := summarizeMphConfig(tc.config)
			if err != nil {
				t.Fatalf("summarize: %v", err)
			}
			if got := summary.cacheable(); got != tc.want {
				t.Errorf("cacheable() = %v, want %v", got, tc.want)
			}
		})
	}
}

func TestMphSummaryDetectsMissingTag(t *testing.T) {
	summary, err := summarizeMphConfig(untaggedConfig)
	if err != nil {
		t.Fatal(err)
	}
	if len(summary.Rules) != 1 {
		t.Fatalf("expected 1 domain rule, got %d", len(summary.Rules))
	}
	if summary.Rules[0].Tag != "" {
		t.Errorf("expected empty tag, got %q", summary.Rules[0].Tag)
	}
}

func TestMphFingerprintChangesWithRules(t *testing.T) {
	a, err := summarizeMphConfig(taggedConfig)
	if err != nil {
		t.Fatal(err)
	}
	b, err := summarizeMphConfig(untaggedConfig)
	if err != nil {
		t.Fatal(err)
	}
	if a.fingerprint("stamp") == b.fingerprint("stamp") {
		t.Error("fingerprints must differ when the domain rules differ")
	}
}

func TestMphFingerprintChangesWithGeosite(t *testing.T) {
	summary, err := summarizeMphConfig(taggedConfig)
	if err != nil {
		t.Fatal(err)
	}
	if summary.fingerprint("a") == summary.fingerprint("b") {
		t.Error("fingerprints must differ when the geosite database changes")
	}
}

func TestMphFingerprintStableAcrossRuleOrder(t *testing.T) {
	first, err := summarizeMphConfig(`{"routing":{"rules":[
		{"type":"field","ruleTag":"r1","domain":["domain:a.com"]},
		{"type":"field","ruleTag":"r2","domain":["domain:b.com"]}]}}`)
	if err != nil {
		t.Fatal(err)
	}
	second, err := summarizeMphConfig(`{"routing":{"rules":[
		{"type":"field","ruleTag":"r2","domain":["domain:b.com"]},
		{"type":"field","ruleTag":"r1","domain":["domain:a.com"]}]}}`)
	if err != nil {
		t.Fatal(err)
	}
	if first.fingerprint("s") != second.fingerprint("s") {
		t.Error("reordering rules must not change the fingerprint")
	}
}

func TestMphCacheDisabledWithoutDirectory(t *testing.T) {
	SetMphCacheDir("")
	if MphCacheEnabled() {
		t.Error("cache must be disabled when no directory is set")
	}
	disable := prepareMphCache(taggedConfig)
	if disable == nil {
		t.Fatal("expected a disable function")
	}
	disable()
	if os.Getenv(mphEnvName()) != "" {
		t.Error("cache environment variable must not be set")
	}
}

func TestMphCacheNotUsedForUntaggedConfig(t *testing.T) {
	dir := t.TempDir()
	SetMphCacheDir(dir)
	defer SetMphCacheDir("")

	disable := prepareMphCache(untaggedConfig)
	defer disable()
	if os.Getenv(mphEnvName()) != "" {
		t.Error("cache must not be enabled for a config without rule tags")
	}
	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range entries {
		if strings.HasPrefix(entry.Name(), mphCachePrefix) {
			t.Errorf("unexpected cache file %q", entry.Name())
		}
	}
}
