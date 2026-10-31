# v0.1.4.6 (111) validation — 2026-11-01

## Changes

- Manual additions reuse the selected existing local or subscription group, falling back to the first existing group. No extra group is created. Manual subscription members retain their identity across edits and subscription refreshes.
- Dual load balancing always emits the observatory required by its fallback strategy, and exposes a logical proxy loopback for forced latency probes.
- Single load balancing selects the logical proxy group for session traffic accounting. The native wrapper accepts internal compatible providers created by inline groups while continuing to reject explicit external providers.
- Updates select single/dual assets according to the installed flavor, prefer a matching universal APK, then match supported device ABIs. Missing or unsigned assets do not switch core variants.

## Validation

- Unit tests: dual 160, single 257; all 417 passed.
- Both debug lint tasks passed with zero errors; each reports 36 existing warnings.
- Full native Go package tests passed for libxray and libmihomo. New regressions start all supported balance strategies, send actual HTTP requests, check round-robin member visits and single group traffic counters, and reproduce the old invalid configurations.
- Rebuilt the single Mihomo AAR for arm64-v8a, armeabi-v7a and x86_64 using the pinned gomobile tool and with_gvisor tag. Release native verification confirms every single APK matches that AAR and contains no Rust library.
- Signed both release variants using E:/bulid/iceflow-release-packaging/build-release.sh. All eight APKs passed package/version, ABI and formal signing-certificate checks.
- On emulator-5556, manual HTTP nodes and a standalone balance configuration were saved inside an existing subscription group without creating an ungrouped tab.
- Installed the exact staged x86_64 single and dual APKs in turn; installed package SHA-256 and versionName/versionCode match the staged artifacts.
- Both variants established VPN and returned HTTP 204 through both configured local proxy members. The test restores the fixture port mappings after VPN creation because this emulator reconnects ADB at that point.
- Removed test configurations and mappings, restored the original selected node, and left the verified dual release installed.

## Artifacts

Eight signed APKs and SHA256SUMS.txt are staged in E:/bulid/iceflow-release-packaging/release-upload-v0.1.4.6-20261101. Local manifests and device verification JSON remain beside those artifacts. Source changes exclude the unrelated existing documentation/report cleanup.

The updated package selector takes effect after installing this version; older clients require selecting the matching single/dual asset for their first upgrade. GitHub CI status is separate from these local build and device results.
