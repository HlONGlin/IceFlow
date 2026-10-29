# IceFlow v0.1.4.2

## 修复

- 修复 single 版本 AnyTLS 分享链接携带 security=tls、tls、allowInsecure 或 skip-cert-verify 时被 mihomo 严格配置校验拒绝的问题。
- 兼容已保存的 AnyTLS 旧参数，并统一转换为 mihomo 的 skip-cert-verify 配置。
- 保留 AnyTLS 的 peer 到 SNI 映射，避免分享链接重建时丢失连接目标信息。

## 发布包

- dual：arm64-v8a、armeabi-v7a、x86_64、universal
- single：arm64-v8a、armeabi-v7a、x86_64、universal

本版本已通过 dual/single JVM 测试及 dual/single release 构建校验。
