package libmihomo

import (
	"errors"
	"reflect"
	"strings"
	"testing"

	"github.com/metacubex/mihomo/config"
	LC "github.com/metacubex/mihomo/listener/config"
)

const minimalConfig = `{"mode":"rule","rules":["MATCH,DIRECT"],"find-process-mode":"off","tun":{"stack":"gvisor","mtu":1500}}`

type fakeTun struct {
	closed    bool
	failClose bool
}

func (t *fakeTun) Close() error {
	t.closed = true
	if t.failClose {
		return errors.New("tun cleanup failure")
	}
	return nil
}
func (t *fakeTun) healthy() bool { return !t.closed }
func fakeEngine(events *[]string, device *fakeTun) *engine {
	return &engine{
		openTun: func(_ LC.Tun, fd int) (tunSession, error) {
			*events = append(*events, "tun")
			if fd != 42 {
				panic("borrowed fd changed")
			}
			return device, nil
		},
		apply:    func(*config.Config) error { *events = append(*events, "apply"); return nil },
		shutdown: func() error { *events = append(*events, "shutdown"); return nil },
		totals:   func() (int64, int64) { return 50, 80 },
	}
}
func TestPinnedVersion(t *testing.T) {
	if Version() != "v1.19.32" {
		t.Fatal(Version())
	}
}
func TestConfigValidation(t *testing.T) {
	if err := Validate(minimalConfig); err != nil {
		t.Fatal(err)
	}
}
func TestRejectsOwnedRoutingAndEmbeddedFd(t *testing.T) {
	for _, field := range []string{`"file-descriptor":42`, `"auto-route":true`, `"auto-detect-interface":true`, `"auto-redirect":true`} {
		cfg := `{"tun":{` + field + "}}"
		if err := Validate(cfg); err == nil || !strings.HasPrefix(err.Error(), "CONFIGURATION_OR_SUBSCRIPTION:") {
			t.Fatalf("%s: %v", field, err)
		}
	}
}
func TestInvalidConfigFailsBeforeApplyOrFdHandoff(t *testing.T) {
	var events []string
	e := fakeEngine(&events, &fakeTun{})
	err := e.start("{", 42)
	if err == nil || len(events) != 0 {
		t.Fatalf("%v %v", err, events)
	}
}
func TestStartupOrderAndStopIdempotence(t *testing.T) {
	var events []string
	device := &fakeTun{}
	e := fakeEngine(&events, device)
	if err := e.start(minimalConfig, 42); err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(events, []string{"apply", "tun"}) {
		t.Fatal(events)
	}
	if err := e.stop(); err != nil {
		t.Fatal(err)
	}
	if !device.closed {
		t.Fatal("duplicate not closed")
	}
	if err := e.stop(); err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(events, []string{"apply", "tun", "shutdown"}) {
		t.Fatal(events)
	}
}
func TestPrimaryStartFailureSurvivesCleanupFailure(t *testing.T) {
	var events []string
	e := fakeEngine(&events, &fakeTun{})
	e.openTun = func(LC.Tun, int) (tunSession, error) { return nil, errors.New("primary TUN startup failure") }
	e.shutdown = func() error { return errors.New("secondary cleanup failure") }
	err := e.start(minimalConfig, 42)
	if err == nil || !strings.HasPrefix(err.Error(), "CORE_START_FAILED: start TUN: primary TUN startup failure") || !strings.Contains(err.Error(), "cleanup: secondary cleanup failure") {
		t.Fatal(err)
	}
	if e.cfg != nil || e.tun != nil {
		t.Fatal("failed start advertised running")
	}
}
func TestStopAttemptsCoreCleanupAfterTunFailure(t *testing.T) {
	var events []string
	e := fakeEngine(&events, &fakeTun{failClose: true})
	if err := e.start(minimalConfig, 42); err != nil {
		t.Fatal(err)
	}
	err := e.stop()
	if err == nil || !strings.HasPrefix(err.Error(), "RESOURCE_CLEANUP_FAILED:") || events[len(events)-1] != "shutdown" {
		t.Fatalf("%v %v", err, events)
	}
}
func TestStatsAreCumulativeAndRetainedAfterStop(t *testing.T) {
	old := current
	defer func() { current = old }()
	var events []string
	current = fakeEngine(&events, &fakeTun{})
	if err := current.start(minimalConfig, 42); err != nil {
		t.Fatal(err)
	}
	current.totals = func() (int64, int64) { return 110, 200 }
	first := StatsJSON()
	second := StatsJSON()
	if first != `{"download":120,"upload":60}` || first != second {
		t.Fatal(first, second)
	}
	if err := Stop(); err != nil {
		t.Fatal(err)
	}
	if StatsJSON() != first {
		t.Fatal("stop lost final byte counters")
	}
}
func TestInvalidFdDoesNotTakeOwnership(t *testing.T) {
	var events []string
	e := fakeEngine(&events, &fakeTun{})
	if e.start(minimalConfig, -1) == nil || len(events) != 0 {
		t.Fatal(events)
	}
}

func TestAndroidDefaultsLeaveRoutingWithService(t *testing.T) {
	cfg, err := parse(minimalConfig)
	if err != nil {
		t.Fatal(err)
	}
	if cfg.General.Tun.Enable || cfg.General.Tun.AutoRoute || cfg.General.Tun.AutoDetectInterface || cfg.General.Tun.AutoRedirect || cfg.General.Tun.FileDescriptor != 0 {
		t.Fatal("desktop route ownership leaked into Android config")
	}
}
func TestExplicitDisabledRoutingRemainsValid(t *testing.T) {
	if err := Validate(`{"tun":{"auto-route":false,"auto-detect-interface":false,"auto-redirect":false,"file-descriptor":0}}`); err != nil {
		t.Fatal(err)
	}
}
