package libxray

import (
	"context"
	"fmt"

	statsService "github.com/xtls/xray-core/app/stats/command"
)

// TrafficStats holds uplink and downlink bytes for a tag.
type TrafficStats struct {
	Tag      string
	Uplink   int64
	Downlink int64
}

// QueryStats returns traffic statistics for all outbounds and inbounds.
// Returns comma-separated lines: "outbound>>>proxy,12345,67890" format.
func QueryStats() (string, error) {
	mu.Lock()
	inst := instance
	mu.Unlock()

	if inst == nil {
		return "", fmt.Errorf("xray not running")
	}

	statsManager := inst.GetFeature((*statsService.StatsServiceServer)(nil))
	if statsManager == nil {
		return "", fmt.Errorf("stats feature not available")
	}

	server, ok := statsManager.(statsService.StatsServiceServer)
	if !ok {
		return "", fmt.Errorf("stats service type mismatch")
	}

	resp, err := server.QueryStats(context.Background(), &statsService.QueryStatsRequest{
		Pattern: "",
		Reset_:  false,
	})
	if err != nil {
		return "", fmt.Errorf("query stats: %w", err)
	}

	var result []string
	for _, stat := range resp.Stat {
		result = append(result, fmt.Sprintf("%s,%d", stat.Name, stat.Value))
	}

	if len(result) == 0 {
		return "", nil
	}

	return join(result, "\n"), nil
}

func join(parts []string, sep string) string {
	if len(parts) == 0 {
		return ""
	}
	result := parts[0]
	for i := 1; i < len(parts); i++ {
		result += sep + parts[i]
	}
	return result
}
