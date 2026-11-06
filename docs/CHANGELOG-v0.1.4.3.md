# IceFlow v0.1.4.3

## 修复

- 修复 single 单核版本在已连接状态下无法测试当前节点延迟的问题。
- 连接状态下测试当前节点时复用正在运行的 SOCKS 会话，避免重复启动内核。
- 对 single 当前节点以外的节点测速和混合批量测速给出明确提示，避免显示误导性的 `-1ms`。
- 保持 AnyTLS 分享链接的 TLS 参数转换兼容 mihomo。

## 发布包

- dual：arm64-v8a、armeabi-v7a、x86_64、universal
- single：arm64-v8a、armeabi-v7a、x86_64、universal

本版本已通过 dual/single JVM 测试、lint、release 构建和原生库打包校验。
