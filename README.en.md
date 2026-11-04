<div align="center">

# ❄️ IceFlow

**A modern Android proxy client · dual and single core variants**

<p>
  <a href="https://github.com/HlONGlin/IceFlow/releases/latest"><img src="https://img.shields.io/github/v/release/HlONGlin/IceFlow?display_name=tag&style=flat-square&color=1677FF" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
</p>

[⬇️ Download v0.1.5.1](https://github.com/HlONGlin/IceFlow/releases/latest) · [简体中文](README.md)

</div>

---

IceFlow is an Android proxy client built with Jetpack Compose. It combines node and subscription management, rule-based routing, and automatic recovery after network interruptions. The dual and single variants share the same UI, subscription and routing features, with APKs for multiple device architectures.

**Current release** v0.1.5.1 · **versionCode** 113 · **Android** 8.0+

## Real Device Preview

These screenshots were captured on `emulator-5566`: Pixel Tablet, Android 15 (API 35). They show an active connection, the offline waiting state, and automatic reconnection after the network returns.

<table>
  <tr>
    <td align="center" width="33%">
      <img src="screenshots/emulator/v0.1.5.1/connected.png" width="260" alt="Connected state">
      <br><sub><b>Connected</b><br>Connection and traffic status are visible</sub>
    </td>
    <td align="center" width="33%">
      <img src="screenshots/emulator/v0.1.5.1/network-waiting.png" width="260" alt="Network waiting state">
      <br><sub><b>Network lost</b><br>A clear waiting state appears during the outage</sub>
    </td>
    <td align="center" width="33%">
      <img src="screenshots/emulator/v0.1.5.1/restored.png" width="260" alt="Restored connection state">
      <br><sub><b>Network restored</b><br>The app reconnects automatically</sub>
    </td>
  </tr>
</table>

> The screenshots document UI states and recovery behavior. Testing used a `127.0.0.1:1080` SOCKS5 profile without an available upstream, so they do not represent real proxy throughput.

## What's New in v0.1.5.1

- A dedicated waiting state appears when the network drops, hiding stale traffic and speed data.
- IceFlow reconnects automatically when the network returns; users can still disconnect while waiting.
- Both dual and single variants include the recovery behavior and localized strings.

See the [full change log](CHANGELOG-v0.1.5.1.md).

## Downloads

Get signed APKs from [GitHub Release v0.1.5.1](https://github.com/HlONGlin/IceFlow/releases/tag/v0.1.5.1). The existing production signing key is retained, so the APK can update an existing installation while preserving app data.

| Variant | Core | Recommended download | Description |
| --- | --- | --- | --- |
| **single** | mihomo | [Universal APK](https://github.com/HlONGlin/IceFlow/releases/download/v0.1.5.1/IceFlow-0.1.5.1-single-universal-release.apk) | Simple installation for most devices |
| **dual** | xray + mihomo + Rust TUN | [Universal APK](https://github.com/HlONGlin/IceFlow/releases/download/v0.1.5.1/IceFlow-0.1.5.1-dual-universal-release.apk) | For devices that need dual-core features |

`arm64-v8a`, `armeabi-v7a` and `x86_64` packages are also available. Checksums: [SHA256SUMS.txt](https://github.com/HlONGlin/IceFlow/releases/download/v0.1.5.1/SHA256SUMS.txt) · [Build manifest](https://github.com/HlONGlin/IceFlow/releases/download/v0.1.5.1/ARTIFACT_MANIFEST.json)

## Features

| Area | Capabilities |
| --- | --- |
| Protocols | VMess, VLESS with Reality, Trojan, Shadowsocks, Mieru, Hysteria/Hysteria2, TUIC, WireGuard, SSR, HTTP, SOCKS, AnyTLS and Sudoku |
| Import | Clash YAML, QR codes, HTTPS subscriptions, node links, deep links and config files |
| Routing | Rule-based routing, per-app proxy, DNS routing, IPv6, proxy chains and rule-specific outbound selection |
| Management | Node groups, subscription updates, batch import, latency tests, traffic statistics, backup and restore |
| Experience | Material Design 3, dark mode, themes, responsive layout and automatic network recovery |

## Core Variants

- **dual** packages xray, mihomo and the Rust TUN layer.
- **single** packages mihomo only.

Both variants share the app UI, node management, subscriptions and routing. The app clearly reports capabilities that depend on a particular core.

## Connection Flow

~~~mermaid
flowchart TB
    UI["Jetpack Compose UI"] --> VPN["Android VpnService / TUN"]
    VPN --> DUAL["dual · xray + mihomo + Rust TUN"]
    VPN --> SINGLE["single · mihomo"]
    DUAL --> ROUTE["Protocol handling and routing"]
    SINGLE --> ROUTE
    ROUTE --> SERVER["Remote proxy server"]
~~~

## Quick Start

1. Download the APK for your device from [Releases](https://github.com/HlONGlin/IceFlow/releases/latest).
2. Add a node, import an HTTPS subscription, or share a node to IceFlow from another app.
3. Select a node and tap connect. Approve Android's VPN permission on the first connection.
4. If the network drops while connected, wait for it to return; IceFlow reconnects automatically.

## Deep Links and Imports

External imports show a confirmation dialog first. Nothing is written and no connection starts before confirmation.

| Entry point | Example |
| --- | --- |
| Clash subscription | `clash://install-config?url=https%3A%2F%2Fexample.com%2Fsub` |
| IceFlow subscription | `iceflow://install-config?url=https%3A%2F%2Fexample.com%2Fsub&name=My%20Sub` |
| v2rayN subscription | `v2rayn://install-sub?url=https%3A%2F%2Fexample.com%2Fsub` |
| sing-box profile | `sing-box://import-remote-profile?url=https%3A%2F%2Fexample.com%2Fsub` |
| Node links | `vmess://`, `vless://`, `trojan://`, `ss://`, `ssr://`, `hysteria2://`, `hy2://`, `tuic://`, `wireguard://`, `anytls://`, `mierus://` |
| Config files | Open `.yaml`, `.yml`, `.json` or `.txt` from a file manager (4 MiB limit) |

Subscriptions require HTTPS. Duplicate URLs are not added twice. Full `mieru://` Protobuf links require the dual variant.

<details>
<summary><b>Build from Source</b></summary>

### Requirements

- JDK 17
- Android SDK 36 and NDK
- Rust with `cargo-ndk`
- Go 1.26+

### Project Structure

| Directory | Purpose |
| --- | --- |
| `app/` | Android app, Compose UI and VPN service |
| `rust-core/` | Rust TUN forwarding layer used by dual |
| `xray-wrapper/` | xray gomobile wrapper used by dual |
| `mihomo-wrapper/` | mihomo gomobile wrapper used by single |
| `docs/` | Architecture, quick start and design notes |

### Release Signing

For a signed release build, set all four environment variables locally or leave all four unset. Never commit signing keys or passwords.

~~~powershell
$env:ICEFLOW_KEYSTORE_PATH="path/to/keystore.jks"
$env:ICEFLOW_KEYSTORE_PASSWORD="keystore_password"
$env:ICEFLOW_KEY_ALIAS="key_alias"
$env:ICEFLOW_KEY_PASSWORD="key_password"
~~~

### Windows

~~~powershell
# Rust TUN layer, dual only
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
.\rust-core\build-android.ps1

# xray AAR, dual only
.\xray-wrapper\build-aar.ps1

# mihomo AAR, single
.\mihomo-wrapper\build-aar.ps1

# Unit tests and debug APKs
.\gradlew.bat testDualDebugUnitTest testSingleDebugUnitTest assembleDualDebug assembleSingleDebug
~~~

### Linux / macOS

~~~bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
./rust-core/build-android.sh
./xray-wrapper/build-aar.sh
./mihomo-wrapper/build-aar.sh
./gradlew testDualDebugUnitTest testSingleDebugUnitTest assembleDualDebug assembleSingleDebug
~~~

> On Windows, an ASCII-only project path is recommended. Non-ASCII paths may cause Gradle tests to fail with `ClassNotFoundException`.

</details>

<details>
<summary><b>Testing and Validation</b></summary>

- pp/src/test and pp/src/testSingle: JVM unit tests for dual and single.
- pp/src/androidTest: Android instrumentation tests.
- ust-core: Rust unit tests (cargo test).
- xray-wrapper: Go unit tests (go test ./...).

~~~powershell
.\gradlew.bat testDualDebugUnitTest testSingleDebugUnitTest
.\gradlew.bat connectedDualDebugAndroidTest connectedSingleDebugAndroidTest
~~~

Version 0.1.5.1 was validated on `emulator-5566` (Pixel Tablet, Android 15, API 35, x86_64) for dual/single offline startup, single network-loss waiting and automatic recovery, and the dual disconnect action.

</details>

## Updates

IceFlow can quietly check GitHub Releases after startup and notify you when an update is available. You can also check manually under **Settings → About → IceFlow** or turn off automatic reminders.

## Documentation

- [Quick Start](docs/IceFlow-QuickStart.md)
- [Architecture](docs/IceFlow-Architecture.md)
- [Network Recovery](RECONNECTION_OPTIMIZATION.md)
- [Test Reports](test-reports/README.md)

## Known Limitations

- Subscription URLs must use HTTPS. Subscription content is limited to 4 MiB and the routing database to 64 MiB.
- Android's Always-on VPN and Block connections without VPN settings are required for system-level blocking.
- Full `mieru://` Protobuf links require the dual variant.

<div align="center">

**If IceFlow helps you, consider giving it a Star ⭐**

</div>
