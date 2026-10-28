//go:build !android

package libmihomo

import (
	"errors"
	LC "github.com/metacubex/mihomo/listener/config"
)

func openTun(options LC.Tun, fd int) (tunSession, error) {
	return nil, errors.New("Android VpnService TUN is required")
}
