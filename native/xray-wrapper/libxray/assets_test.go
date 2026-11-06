package libxray

import (
	"os"
	"path/filepath"
	"testing"
)

func TestBundledRoutingResourcesValidateAndRejectMalformedData(t *testing.T) {
	for _, name := range []string{"geosite.dat", "geoip.dat"} {
		if err := ValidateRoutingAsset(filepath.Join("..", "..", "..", "app", "src", "main", "assets", "routing", name), name); err != nil {
			t.Fatalf("%s: %v", name, err)
		}
		malformed := filepath.Join(t.TempDir(), name)
		if err := os.WriteFile(malformed, []byte("<html>not a database</html>"), 0600); err != nil {
			t.Fatal(err)
		}
		if err := ValidateRoutingAsset(malformed, name); err == nil {
			t.Fatalf("accepted malformed %s", name)
		}
	}
}
