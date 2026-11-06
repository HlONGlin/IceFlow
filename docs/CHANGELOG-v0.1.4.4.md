# IceFlow v0.1.4.4

## 修复

- 修复 single 版本读取旧 AnyTLS 节点时，type、headerType、security、tls、insecure 等历史字段导致 mihomo 配置校验失败的问题。
- 启动时自动迁移旧 AnyTLS 节点数据，并保留原有的不校验证书设置。
- 增加 AnyTLS 历史字段和仓库迁移回归测试。

## 验证

- dual/single JVM 单元测试通过。
- single lint 通过。
- 正式签名 single x86_64 包在 emulator-5556 上可启动 mihomo 并进入连接阶段。
