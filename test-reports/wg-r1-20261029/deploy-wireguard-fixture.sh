#!/usr/bin/env bash
set -euo pipefail

# R1 remote fixture: Debian/Ubuntu VPS, run as root.
# It creates a private WireGuard server on UDP 51820 and a single peer at
# 10.66.66.2/32, forwarding IPv4 traffic for generate_204 checks.

WG_IFACE="${WG_IFACE:-wg-r1}"
WG_PORT="${WG_PORT:-51820}"
WG_NET="${WG_NET:-10.66.66.0/24}"
SERVER_ADDR="${SERVER_ADDR:-10.66.66.1/24}"
CLIENT_ADDR="${CLIENT_ADDR:-10.66.66.2/32}"
OUT_DIR="${OUT_DIR:-$PWD/wg-r1-output}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "run as root" >&2
  exit 2
fi

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y wireguard wireguard-tools iptables curl
install -d -m 700 "$OUT_DIR"

WAN_IFACE="${WAN_IFACE:-$(ip -o -4 route show to default | awk '{print $5; exit}')}"
if [[ -z "$WAN_IFACE" ]]; then
  echo "cannot determine default-route interface; set WAN_IFACE" >&2
  exit 3
fi

umask 077
SERVER_PRIVATE_KEY="$(wg genkey)"
SERVER_PUBLIC_KEY="$(printf '%s' "$SERVER_PRIVATE_KEY" | wg pubkey)"
CLIENT_PRIVATE_KEY="$(wg genkey)"
CLIENT_PUBLIC_KEY="$(printf '%s' "$CLIENT_PRIVATE_KEY" | wg pubkey)"

sysctl -w net.ipv4.ip_forward=1 >/dev/null
cat >/etc/sysctl.d/99-iceflow-wg-r1.conf <<EOF
net.ipv4.ip_forward=1
EOF

cat >"/etc/wireguard/${WG_IFACE}.conf" <<EOF
[Interface]
Address = ${SERVER_ADDR}
ListenPort = ${WG_PORT}
PrivateKey = ${SERVER_PRIVATE_KEY}
PostUp = iptables -A FORWARD -i %i -j ACCEPT; iptables -A FORWARD -o %i -j ACCEPT; iptables -t nat -A POSTROUTING -s ${WG_NET} -o ${WAN_IFACE} -j MASQUERADE
PostDown = iptables -D FORWARD -i %i -j ACCEPT; iptables -D FORWARD -o %i -j ACCEPT; iptables -t nat -D POSTROUTING -s ${WG_NET} -o ${WAN_IFACE} -j MASQUERADE

[Peer]
PublicKey = ${CLIENT_PUBLIC_KEY}
AllowedIPs = ${CLIENT_ADDR}
EOF
chmod 600 "/etc/wireguard/${WG_IFACE}.conf"
systemctl enable --now "wg-quick@${WG_IFACE}"

PUBLIC_ENDPOINT="${PUBLIC_ENDPOINT:-$(curl -4fsS --max-time 10 https://api.ipify.org)}"
cat >"${OUT_DIR}/server-summary.env" <<EOF
WG_IFACE=${WG_IFACE}
WG_PORT=${WG_PORT}
WG_NET=${WG_NET}
SERVER_ADDR=${SERVER_ADDR}
SERVER_PUBLIC_KEY=${SERVER_PUBLIC_KEY}
CLIENT_ADDR=${CLIENT_ADDR}
CLIENT_PUBLIC_KEY=${CLIENT_PUBLIC_KEY}
PUBLIC_ENDPOINT=${PUBLIC_ENDPOINT}
EOF

cat >"${OUT_DIR}/client.conf" <<EOF
[Interface]
PrivateKey = ${CLIENT_PRIVATE_KEY}
Address = ${CLIENT_ADDR}
DNS = 1.1.1.1

[Peer]
PublicKey = ${SERVER_PUBLIC_KEY}
Endpoint = ${PUBLIC_ENDPOINT}:${WG_PORT}
AllowedIPs = 0.0.0.0/0
PersistentKeepalive = 25
EOF
chmod 600 "${OUT_DIR}/client.conf" "${OUT_DIR}/server-summary.env"

echo "fixture ready"
echo "server public key: ${SERVER_PUBLIC_KEY}"
echo "endpoint: ${PUBLIC_ENDPOINT}:${WG_PORT}"
echo "client config: ${OUT_DIR}/client.conf"
echo "client peer allowed-ips: 0.0.0.0/0 (G05 explicit peer landing)"
