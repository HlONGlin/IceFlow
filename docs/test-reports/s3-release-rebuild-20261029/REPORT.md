# S3: 8 release APK rebuild and signature verification

Date: 2026-10-29  
Branch: `feat/core-variants`  
Scope: x86_64 emulator scope only; ARM real-device validation was not performed.

## Build entry

Git Bash entry used: `E:/bulid/iceflow-release-packaging/build-release.sh`.
Commands executed: `assembleSingleRelease` and `assembleDualRelease`.
The script processes were given the Android Studio JBR and local Android SDK paths
because the default terminal Java was Java 8; no signing configuration values were
read or printed, and no `.jks` or signing files were changed.

Both entry-point runs completed with `BUILD SUCCESSFUL` and the script's native
library and signature checks completed.

## Per-APK apksigner results

Baseline certificate SHA-256:
`762ca8438b9a449784d09b5d503baf781d85b40b039f57ab9a6e382ea0fdc025`

| APK | apksigner verify | Certificate SHA-256 | Result |
|---|---:|---|---|
| app-dual-arm64-v8a-release.apk | exit 0 | 762ca8438b9a449784d09b5d503baf781d85b40b039f57ab9a6e382ea0fdc025 | PASS |
| app-dual-armeabi-v7a-release.apk | exit 0 | 762ca8438b9a449784d09b5d503baf781d85b40b039f57ab9a6e382ea0fdc025 | PASS |
| app-dual-universal-release.apk | exit 0 | 762ca8438b9a449784d09b5d503baf781d85b40b039f57ab9a6e382ea0fdc025 | PASS |
| app-dual-x86_64-release.apk | exit 0 | 762ca8438b9a449784d09b5d503baf781d85b40b039f57ab9a6e382ea0fdc025 | PASS |
| app-single-arm64-v8a-release.apk | exit 0 | 762ca8438b9a449784d09b5d503baf781d85b40b039f57ab9a6e382ea0fdc025 | PASS |
| app-single-armeabi-v7a-release.apk | exit 0 | 762ca8438b9a449784d09b5d503baf781d85b40b039f57ab9a6e382ea0fdc025 | PASS |
| app-single-universal-release.apk | exit 0 | 762ca8438b9a449784d09b5d503baf781d85b40b039f57ab9a6e382ea0fdc025 | PASS |
| app-single-x86_64-release.apk | exit 0 | 762ca8438b9a449784d09b5d503baf781d85b40b039f57ab9a6e382ea0fdc025 | PASS |

## Native library results by ABI

APK `libgojni.so` entries were SHA-256 compared with the corresponding
`jni/<ABI>/libgojni.so` entry in the AAR.

| APK | ABI | `libgojni.so` | `libiceflow_core.so` | Result |
|---|---|---|---|---|
| app-dual-arm64-v8a-release.apk | arm64-v8a | equals Xray AAR | present | PASS |
| app-dual-armeabi-v7a-release.apk | armeabi-v7a | equals Xray AAR | present | PASS |
| app-dual-universal-release.apk | arm64-v8a | equals Xray AAR | present | PASS |
| app-dual-universal-release.apk | armeabi-v7a | equals Xray AAR | present | PASS |
| app-dual-universal-release.apk | x86_64 | equals Xray AAR | present | PASS |
| app-dual-x86_64-release.apk | x86_64 | equals Xray AAR | present | PASS |
| app-single-arm64-v8a-release.apk | arm64-v8a | equals mihomo AAR and differs from Xray AAR | absent | PASS |
| app-single-armeabi-v7a-release.apk | armeabi-v7a | equals mihomo AAR and differs from Xray AAR | absent | PASS |
| app-single-universal-release.apk | arm64-v8a | equals mihomo AAR and differs from Xray AAR | absent | PASS |
| app-single-universal-release.apk | armeabi-v7a | equals mihomo AAR and differs from Xray AAR | absent | PASS |
| app-single-universal-release.apk | x86_64 | equals mihomo AAR and differs from Xray AAR | absent | PASS |
| app-single-x86_64-release.apk | x86_64 | equals mihomo AAR and differs from Xray AAR | absent | PASS |

Conclusion: all 8 APKs passed independent `apksigner` verification and matched the
baseline fingerprint; all dual/single native-library requirements passed for every
ABI present in the packages.
