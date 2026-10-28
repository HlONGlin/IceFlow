package libmihomo

import (
	"encoding/json"
	"errors"
	"fmt"
)

// Validate the exact closed app-generated shape before the permissive upstream decoder.
func validateAppListeners(text string) error {
	var root struct {
		Listeners []map[string]json.RawMessage `json:"listeners"`
	}
	if err := json.Unmarshal([]byte(text), &root); err != nil {
		return err
	}
	names, ports := map[string]bool{}, map[int]bool{}
	for _, item := range root.Listeners {
		if len(item) != 6 {
			return errors.New("listeners: expected exact app SOCKS shape")
		}
		for key := range item {
			switch key {
			case "type", "name", "listen", "port", "udp", "users":
			default:
				return fmt.Errorf("listeners: unsupported field %s", key)
			}
		}
		var kind, name, host string
		var port int
		var udp bool
		if json.Unmarshal(item["type"], &kind) != nil || kind != "socks" ||
			json.Unmarshal(item["name"], &name) != nil || (name != "tun-in" && name != "socks-in") ||
			json.Unmarshal(item["listen"], &host) != nil || host != "127.0.0.1" ||
			json.Unmarshal(item["port"], &port) != nil || port < 1 || port > 65535 ||
			json.Unmarshal(item["udp"], &udp) != nil || string(item["udp"]) == "null" || (name == "tun-in" && !udp) {
			return errors.New("listeners: invalid app SOCKS options")
		}
		if names[name] || ports[port] {
			return errors.New("listeners: duplicate name or port")
		}
		names[name], ports[port] = true, true
		var users []map[string]string
		if json.Unmarshal(item["users"], &users) != nil || len(users) > 1 || string(item["users"]) == "null" {
			return errors.New("listeners: expected zero or one app credential pair")
		}
		for _, user := range users {
			if len(user) != 2 || user["username"] == "" || user["password"] == "" || len(user["username"]) > 255 || len(user["password"]) > 255 {
				return errors.New("listeners: invalid app credential pair")
			}
		}
	}
	return nil
}
