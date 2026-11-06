package libxray

import "testing"

const minimalConfig = `{
  "log": {"loglevel": "none"},
  "outbounds": [{"tag": "direct", "protocol": "freedom"}]
}`

func TestStartStop(t *testing.T) {
	if err := Start(minimalConfig); err != nil {
		t.Fatalf("Start: %v", err)
	}
	if !IsRunning() {
		t.Fatal("expected running")
	}
	if err := Stop(); err != nil {
		t.Fatalf("Stop: %v", err)
	}
	if IsRunning() {
		t.Fatal("expected stopped")
	}
	// 重复停止安全
	if err := Stop(); err != nil {
		t.Fatalf("second Stop: %v", err)
	}
}

func TestInvalidConfig(t *testing.T) {
	if err := Start("{not json"); err == nil {
		t.Fatal("expected error for invalid config")
	}
	if IsRunning() {
		t.Fatal("must not be running after failed start")
	}
}

func TestMeasureDelayRejectsBadTimeout(t *testing.T) {
	if _, err := MeasureDelay(minimalConfig, "https://example.com", 0); err == nil {
		t.Fatal("expected error")
	}
}

func TestVersion(t *testing.T) {
	if Version() == "" {
		t.Fatal("empty version")
	}
}
