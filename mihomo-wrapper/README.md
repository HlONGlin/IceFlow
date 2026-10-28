# Single mihomo core wrapper (phase 2, step 1B)

Pinned upstream: github.com/metacubex/mihomo v1.19.32. Independent Go module;
no Xray imports or Rust dependency. Build with ./build-aar.ps1 from the ASCII
worktree using the pinned gomobile tool. The output app/libs/single/mihomo.aar
is singleImplementation only; the existing dualImplementation AAR and Rust inputs
are untouched. The AAR is a build artifact, not checked into Git.

## Minimal gomobile surface

- SetHomeDir: initialize app-private core assets before parsing or starting.
- Validate: validate app-generated JSON without starting listeners.
- Start(config JSON, original TUN fd): apply config and start an Android gVisor
  TUN; throws a classified Go error if any phase fails. It does not publish UI state.
- Stop: attempt TUN and core cleanup independently; report cleanup errors.
- IsRunning / LastError: core state and diagnostic error outlet.
- StatsJSON: cumulative session upload/download bytes, never a rate or read-and-reset.
  Step 2 must supply per-outbound aggregation and single bridge normalization;
  the raw core totals are not claimed to satisfy per-node attribution yet.

## TUN and failure contract

IceFlowVpnService establishes and owns the original ParcelFileDescriptor. This
wrapper borrows that fd only and calls dup before creating any native TUN object.
Mihomo/sing-tun owns that duplicate; the wrapper closes it on all successful-start,
failed-start and stop paths. It never closes, detaches or adopts the original fd.
The service retains the original until its existing replacement/terminal cleanup.
Startup errors remain primary if cleanup also fails; cleanup detail is appended.

Android owns authorization and OS routing. Explicit nonzero file-descriptor,
auto-route/auto-detect-interface/auto-redirect=true requests are rejected. Absent
upstream desktop defaults are explicitly disabled, not mistaken for user requests.
The app seam accepts JSON, not arbitrary upstream YAML files. External controller,
remote providers and privileged/general listeners are outside this wrapper contract.
The core is not connected to the single bridge yet; readiness stays false until step 3.

## Native artifact acceptance

Both Xray and mihomo gomobile AARs contain libgojni.so. A basename-only check is
insufficient. For every ABI, single APK libgojni.so must match the SHA-256 of
jni/<ABI>/libgojni.so from app/libs/single/mihomo.aar, differ from the Xray AAR's
same ABI entry, and contain no libiceflow_core.so. Dual checks remain unchanged.
Three split ABIs: arm64-v8a, armeabi-v7a, x86_64; universal contains all three.
