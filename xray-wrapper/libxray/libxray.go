// Package libxray wraps xray-core for gomobile bind.
//
// The generated Java class is libxray.Libxray, called via reflection from
// com.iceflow.core.XrayBridge. Exported functions use only gomobile-supported
// types (string / int64 / bool / error).
package libxray

// 注意：导出符号与包的文档注释会被 gomobile 复制进生成的 Java 源码，
// 而 Windows 中文系统上 javac 默认按 GBK 读取，因此这些注释必须使用 ASCII。

import (
	"context"
	"errors"
	"fmt"
	"io"
	gonet "net"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf/serial"

	// 注册全部协议、传输与特性
	_ "github.com/xtls/xray-core/main/distro/all"
)

var (
	mu       sync.Mutex
	instance *core.Instance
)

// SetAssetDir sets the directory containing geoip.dat / geosite.dat.
func SetAssetDir(dir string) {
	_ = os.Setenv("xray.location.asset", dir)
}

// Version returns the xray-core version.
func Version() string {
	return core.Version()
}

// Start launches xray-core with a JSON config, stopping any running instance first.
func Start(configJSON string) error {
	mu.Lock()
	defer mu.Unlock()

	if instance != nil {
		_ = instance.Close()
		instance = nil
	}
	inst, err := newInstance(configJSON)
	if err != nil {
		return err
	}
	if err := inst.Start(); err != nil {
		_ = inst.Close()
		return fmt.Errorf("start xray: %w", err)
	}
	instance = inst
	return nil
}

// Stop stops xray-core. Safe to call repeatedly.
func Stop() error {
	mu.Lock()
	defer mu.Unlock()

	if instance == nil {
		return nil
	}
	err := instance.Close()
	instance = nil
	return err
}

// IsRunning reports whether an instance is running.
func IsRunning() bool {
	mu.Lock()
	defer mu.Unlock()
	return instance != nil
}

// MeasureDelay starts a temporary instance (config should have no inbounds),
// requests testURL through the proxy and returns the elapsed time in milliseconds.
func MeasureDelay(configJSON string, testURL string, timeoutMs int64) (int64, error) {
	if timeoutMs <= 0 {
		return -1, errors.New("timeout must be positive")
	}
	inst, err := newInstance(configJSON)
	if err != nil {
		return -1, err
	}
	if err := inst.Start(); err != nil {
		_ = inst.Close()
		return -1, fmt.Errorf("start xray: %w", err)
	}
	defer inst.Close()

	transport := &http.Transport{
		DisableKeepAlives: true,
		DialContext: func(ctx context.Context, network, addr string) (gonet.Conn, error) {
			dest, err := net.ParseDestination(network + ":" + addr)
			if err != nil {
				return nil, err
			}
			return core.Dial((&taggedDialer{instance: inst, tag: "proxy"}).context(ctx), inst, dest)
		},
	}
	client := &http.Client{Transport: transport, Timeout: time.Duration(timeoutMs) * time.Millisecond}

	start := time.Now()
	resp, err := client.Get(testURL)
	if err != nil {
		return -1, err
	}
	_, _ = io.Copy(io.Discard, resp.Body)
	_ = resp.Body.Close()
	if resp.StatusCode >= 400 {
		return -1, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	return time.Since(start).Milliseconds(), nil
}

func newInstance(configJSON string) (*core.Instance, error) {
	config, err := serial.LoadJSONConfig(strings.NewReader(configJSON))
	if err != nil {
		return nil, fmt.Errorf("parse config: %w", err)
	}
	inst, err := core.New(config)
	if err != nil {
		return nil, fmt.Errorf("create xray: %w", err)
	}
	if err := installExtended(inst, configJSON); err != nil {
		_ = inst.Close()
		return nil, err
	}
	return inst, nil
}
