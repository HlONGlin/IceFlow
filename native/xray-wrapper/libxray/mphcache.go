package libxray

import (
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/xtls/xray-core/common/platform"
	"github.com/xtls/xray-core/infra/conf/serial"
)

// Domain matcher cache (MPH cache).
//
// xray-core expands "geosite:cn" into a domain matcher every time a config is
// parsed. Every start - and every node switch - therefore re-reads and
// re-parses the ~11 MB geosite database, which dominates the connect latency.
//
// xray-core can instead load a pre-built matcher from a file, selected through
// the XRAY_MPH_CACHE environment variable. The cache is keyed by "ruleTag" for
// routing rules and by "tag" for DNS servers, so every domain rule that should
// be served from the cache needs a stable, unique tag.
//
// The cache is valid only for one specific set of domain rules: changing the
// routing rules, the DNS servers or the geosite database invalidates it. The
// fingerprint below detects exactly those changes. One cache file is kept per
// fingerprint, so the VPN config and the per node test configs can each keep
// their own matcher.

const (
	mphCachePrefix = "matcher-"
	// mphCacheKeep is the number of cache files kept on disk. The app uses one
	// config for the running VPN and one per node test, so a few files are kept
	// to avoid rebuilding the matcher on every config switch.
	mphCacheKeep = 4
)

var (
	// mphMu guards the cache directory.
	mphMu sync.Mutex
	// mphDir holds the cache files.
	mphDir string
	// mphAssetDir holds the geo databases; it is used to detect updates.
	mphAssetDir string
	// instanceMu serializes instance creation: the cache is selected through a
	// process wide environment variable, so it must not change while another
	// instance is being built.
	instanceMu sync.Mutex
)

// mphDomainRef is a domain bearing rule or DNS server.
type mphDomainRef struct {
	Tag     string   `json:"tag"`
	Domains []string `json:"domains"`
}

// mphSummary is the part of the config the matcher cache depends on.
type mphSummary struct {
	Rules []mphDomainRef             `json:"rules"`
	DNS   []mphDomainRef             `json:"dns"`
	Hosts map[string]json.RawMessage `json:"hosts,omitempty"`
}

// SetMphCacheDir sets the directory that holds the domain matcher cache. The
// directory must be writable and must contain geoip.dat / geosite.dat.
// Passing an empty string disables the cache.
func SetMphCacheDir(dir string) {
	mphMu.Lock()
	defer mphMu.Unlock()
	mphDir = dir
	// The geo databases live next to the assets; look for them there.
	mphAssetDir = os.Getenv("xray.location.asset")
	if mphAssetDir == "" {
		mphAssetDir = dir
	}
}

// MphCacheEnabled reports whether a domain matcher cache directory is configured.
func MphCacheEnabled() bool {
	mphMu.Lock()
	defer mphMu.Unlock()
	return mphDir != ""
}

// summarizeMphConfig extracts the domain bearing rules and DNS servers.
func summarizeMphConfig(configJSON string) (*mphSummary, error) {
	var doc struct {
		DNS struct {
			Servers []struct {
				Domains []string `json:"domains"`
				Tag     string   `json:"tag"`
			} `json:"servers"`
			Hosts map[string]json.RawMessage `json:"hosts"`
		} `json:"dns"`
		Routing struct {
			Rules []json.RawMessage `json:"rules"`
		} `json:"routing"`
	}
	if err := json.Unmarshal([]byte(configJSON), &doc); err != nil {
		return nil, fmt.Errorf("parse config: %w", err)
	}

	summary := &mphSummary{Hosts: doc.DNS.Hosts}
	for _, raw := range doc.Routing.Rules {
		var rule struct {
			RuleTag string   `json:"ruleTag"`
			Domain  []string `json:"domain"`
			Domains []string `json:"domains"`
		}
		if err := json.Unmarshal(raw, &rule); err != nil {
			continue
		}
		domains := append(append([]string{}, rule.Domain...), rule.Domains...)
		if len(domains) == 0 {
			continue
		}
		summary.Rules = append(summary.Rules, mphDomainRef{Tag: rule.RuleTag, Domains: domains})
	}
	for _, server := range doc.DNS.Servers {
		if len(server.Domains) == 0 {
			continue
		}
		summary.DNS = append(summary.DNS, mphDomainRef{Tag: server.Tag, Domains: server.Domains})
	}
	return summary, nil
}

// cacheable reports whether every domain rule carries a tag that is unique
// across rules and DNS servers.
//
// xray-core looks the matcher up by tag and fails hard when the tag is
// missing, so the cache is only used when the config is fully tagged.
func (s *mphSummary) cacheable() bool {
	if len(s.Rules) == 0 && len(s.DNS) == 0 {
		return false
	}
	seen := make(map[string]bool, len(s.Rules)+len(s.DNS))
	for _, ref := range s.Rules {
		if ref.Tag == "" || seen[ref.Tag] {
			return false
		}
		seen[ref.Tag] = true
	}
	for _, ref := range s.DNS {
		if ref.Tag == "" || seen[ref.Tag] {
			return false
		}
		seen[ref.Tag] = true
	}
	return true
}

// fingerprint hashes the domain related config and the state of the geosite
// database. Rules are sorted by tag so that reordering rules does not
// invalidate the cache.
func (s *mphSummary) fingerprint(geositeStamp string) string {
	rules := append([]mphDomainRef{}, s.Rules...)
	dns := append([]mphDomainRef{}, s.DNS...)
	sort.Slice(rules, func(i, j int) bool { return rules[i].Tag < rules[j].Tag })
	sort.Slice(dns, func(i, j int) bool { return dns[i].Tag < dns[j].Tag })
	payload := struct {
		Rules   []mphDomainRef             `json:"rules"`
		DNS     []mphDomainRef             `json:"dns"`
		Hosts   map[string]json.RawMessage `json:"hosts,omitempty"`
		Geosite string                     `json:"geosite"`
	}{Rules: rules, DNS: dns, Hosts: s.Hosts, Geosite: geositeStamp}
	encoded, err := json.Marshal(payload)
	if err != nil {
		return ""
	}
	sum := sha256.Sum256(encoded)
	return fmt.Sprintf("%x", sum[:])
}

// geoAssetStamp returns the size and modification time of a geo database.
func geoAssetStamp(dir string, name string) string {
	info, err := os.Stat(filepath.Join(dir, name))
	if err != nil {
		return ""
	}
	return fmt.Sprintf("%d:%d", info.Size(), info.ModTime().UnixNano())
}

// buildMphCacheFile asks xray-core to build the domain matcher cache.
//
// The environment variable is cleared while building: xray-core skips the
// geosite expansion when a cache is active, and the builder needs the real
// geosite database.
func buildMphCacheFile(configJSON string, cachePath string) error {
	previous, had := os.LookupEnv(mphEnvName())
	_ = os.Unsetenv(mphEnvName())
	defer func() {
		if had {
			_ = os.Setenv(mphEnvName(), previous)
		}
	}()

	config, err := serial.DecodeJSONConfig(strings.NewReader(configJSON))
	if err != nil {
		return fmt.Errorf("parse config: %w", err)
	}
	if err := config.BuildMPHCache(&cachePath); err != nil {
		return fmt.Errorf("build mph cache: %w", err)
	}
	return nil
}

// mphEnvName returns the environment variable xray-core reads the cache path from.
func mphEnvName() string {
	return platform.NewEnvFlag(platform.MphCachePath).AltName
}

// prepareMphCache makes sure a matching domain matcher cache exists and enables
// it for the current process. The returned function disables it again.
//
// A cache miss is not an error: the caller simply starts xray without a cache.
func prepareMphCache(configJSON string) func() {
	noop := func() {}
	mphMu.Lock()
	dir := mphDir
	assetDir := mphAssetDir
	mphMu.Unlock()
	if dir == "" {
		return noop
	}

	summary, err := summarizeMphConfig(configJSON)
	if err != nil || !summary.cacheable() {
		return noop
	}

	fingerprint := summary.fingerprint(geoAssetStamp(assetDir, "geosite.dat"))
	if fingerprint == "" {
		return noop
	}
	cachePath := filepath.Join(dir, mphCachePrefix+fingerprint+".cache")

	if info, err := os.Stat(cachePath); err != nil || info.Size() == 0 {
		tmp := cachePath + ".tmp"
		if err := buildMphCacheFile(configJSON, tmp); err != nil {
			_ = os.Remove(tmp)
			return noop
		}
		if err := os.Rename(tmp, cachePath); err != nil {
			_ = os.Remove(tmp)
			return noop
		}
		pruneMphCacheFiles(dir, cachePath)
	}

	_ = os.Setenv(mphEnvName(), cachePath)
	return func() { _ = os.Unsetenv(mphEnvName()) }
}

// pruneMphCacheFiles removes stale cache files, keeping the most recent ones.
//
// The app uses more than one config (the running VPN config and the per node
// test configs). Each has its own fingerprint, so several cache files coexist.
// Keeping only the active one would rebuild the matcher on every config switch,
// which is exactly what the cache is meant to avoid.
func pruneMphCacheFiles(dir string, keep string) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return
	}
	type cacheFile struct {
		path string
		mod  time.Time
	}
	var files []cacheFile
	for _, entry := range entries {
		name := entry.Name()
		if !strings.HasPrefix(name, mphCachePrefix) || !strings.HasSuffix(name, ".cache") {
			continue
		}
		info, err := entry.Info()
		if err != nil {
			continue
		}
		files = append(files, cacheFile{path: filepath.Join(dir, name), mod: info.ModTime()})
	}
	if len(files) <= mphCacheKeep {
		return
	}
	sort.Slice(files, func(i, j int) bool { return files[i].mod.After(files[j].mod) })
	for _, file := range files[mphCacheKeep:] {
		if file.path == keep {
			continue
		}
		_ = os.Remove(file.path)
	}
}
