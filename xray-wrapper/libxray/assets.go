package libxray

import (
	"fmt"
	"github.com/xtls/xray-core/app/router"
	"google.golang.org/protobuf/proto"
	"os"
	"strings"
)

// ValidateRoutingAsset validates a database before atomically replacing the active file.
func ValidateRoutingAsset(path, kind string) error {
	info, err := os.Stat(path)
	if err != nil {
		return err
	}
	if info.Size() <= 0 || info.Size() > 64*1024*1024 {
		return fmt.Errorf("invalid routing resource size")
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	switch kind {
	case "geosite.dat":
		list := new(router.GeoSiteList)
		if err := proto.Unmarshal(data, list); err != nil {
			return err
		}
		for _, entry := range list.Entry {
			if strings.EqualFold(entry.CountryCode, "cn") && len(entry.Domain) > 0 {
				return nil
			}
		}
	case "geoip.dat":
		list := new(router.GeoIPList)
		if err := proto.Unmarshal(data, list); err != nil {
			return err
		}
		for _, entry := range list.Entry {
			if strings.EqualFold(entry.CountryCode, "cn") && len(entry.Cidr) > 0 {
				return nil
			}
		}
	default:
		return fmt.Errorf("unsupported routing resource")
	}
	return fmt.Errorf("routing resource does not contain CN data")
}
