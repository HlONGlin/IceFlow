# R1 retry WireGuard real fixture validation

- Date: 2026-10-29 (Asia/Shanghai)
- Device: `emulator-5556`, `x86_64` only (`device-abi.txt`)
- APK: rebuilt `single` x86_64 debug package; SHA-256 in `apk-sha256.txt`
- Fixture endpoint: `54.255.193.12:51820`
- Server public key: `iPDzOJilNJ84GQdfPyNzPKqXvisRRsOXHySJahRgWwM=`
- Client address: `10.66.66.2/32`
- Client private key: injected only into the device test file; redacted from evidence

## Matrix

| Mode | Connect | `generate_204` | Disconnect | Result | Evidence |
|---|---|---|---|---|---|
| TUN | Passed; app logged `VPN started: R1-WireGuard-real-fixture` and `rust.startTun` | Passed; device `curl https://www.gstatic.com/generate_204` returned `HTTP/1.1 204 No Content` | Passed; `Mihomo shutting down`, `tun0` link down, VPN state disconnected | PASS | `connect-summary.txt`, `tun-state-before-204.txt`, `routes-before-204.txt`, `generate204-tun.txt`, `disconnect-summary.txt`, `disconnected.xml` |
| SOCKS | Same active VPN session | Passed through `127.0.0.1:10808` with `--socks5-hostname`; returned `HTTP/1.1 204 No Content` | Same disconnect | PASS | `generate204-socks-10808.txt`, `connect-summary.txt`, `disconnect-summary.txt` |

The old batch port `32123` was probed once and refused; it was not used for the SOCKS result. The active application listener was `10808`, confirmed by the successful request and the app's connected state log.

## Mapping and cleanup

The injected single node used the R1 template values with top-level `allowed-ips` set to `0.0.0.0/0`; the redacted node evidence is in `client-node-redacted.json`. The single builder accepted the non-empty value and the real server handshake/request path completed, covering the explicit peer mapping path described by G05.

After testing, the VPN was disconnected, the pre-existing `servers.json` and backup were restored (`json_equal=True`; canonical hashes are in `restore-compare.txt`), the app was force-stopped, `adb reverse --remove-all` was run, and no `tun0` remained. The remote fixture was not started or controlled by this worker; the coordinator confirmed the user owns its cleanup.

ARM physical devices were not tested.

## Evidence index

- `install-x86_64.txt`, `apk-sha256.txt`, `device-abi.txt`: package/device proof.
- `single-client-node-redacted.json`: template substitution; `client-node-redacted.json` is the injected store snapshot with private key redacted.
- `home2.xml`, `after-connect-tap.xml`, `disconnected.xml`: UI state proof.
- `generate204-tun.txt`, `generate204-socks-10808.txt`: request results.
- `connect-summary.txt`, `logcat-after-connect-tap.txt`: connection/core evidence.
- `disconnect-summary.txt`, `logcat-disconnect.txt`: disconnect/cleanup evidence.
- `adb-reverse-final.txt`, `cleanup-final.txt`, `restored-servers.json`: final cleanup proof.


