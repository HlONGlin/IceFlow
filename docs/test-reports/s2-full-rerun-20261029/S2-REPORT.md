# S2：全量测试强制重跑

日期：2026-10-29。工作树 E:/IceFlow-core-variants，分支 feat/core-variants，起点 7a6892d（R3 已完结）。

## 结果对比

| 组 | 基线 | 本次新跑 | 差异 | 判定 |
| --- | ---: | ---: | --- | --- |
| Kotlin dual | 129 | 129 | 0 | 无倒退 |
| Kotlin single | 221 | 223 | +2（R3 提交 7a6892d 新增 raw version reject + default pass 两个 TUIC 测试） | 无倒退（只增不减） |
| mihomo Go | 36 | 36 | 0 | 无倒退 |
| Xray Go | 57 | 57 | 0 | 无倒退 |
| Rust | 7 | 7 | 0 | 无倒退 |

失败 / error / skipped：五组全部为 0。验收标准（与基线对比无倒退）满足。

## 命令

- Kotlin：`.\gradlew.bat :app:testDualDebugUnitTest :app:testSingleDebugUnitTest --rerun-tasks --no-daemon --console=plain`（52 actionable tasks 全部 executed，BUILD SUCCESSFUL）
- mihomo Go：`go test -count=1 -json ./...`（run=36 pass=36 fail=0）
- Xray Go：`go test -count=1 -json ./...`（run=57 pass=57 fail=0）
- Rust：`cargo test --all-targets`（7 passed; 0 failed; 0 ignored）
- Go 计数按 jsonl 事件去重统计 run/pass，不引用缓存结果。

## 环境备注

- 需显式设置 `JAVA_HOME`（Android Studio JBR，JDK 17）与 `ANDROID_HOME`（C:\Users\honglin\AppData\Local\Android\Sdk），仓库无 local.properties。
- 仅 x86_64 模拟器环境口径；ARM 真机未验证。

## 证据位置

test-reports/s2-full-rerun-20261029/：kotlin-final.log、mihomo-go.jsonl、xray-go.jsonl、rust.log。JUnit XML 位于 app/build/test-results/testDualDebugUnitTest/ 与 testSingleDebugUnitTest/。
