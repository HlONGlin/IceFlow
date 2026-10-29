# IceFlow v0.1.4.1

## 用户可见修复

- 修复 single 版本底部连接栏测速不可用的问题，改为通过实际 mihomo 代理链路测速。
- 节点和批量测速支持真实 SOCKS5/HTTP 代理请求及认证，移除只测试 TCP 端口的假测速。
- 修复同一节点修改 SOCKS 端口或认证后仍复用旧核心配置的问题。
- 设置页关于区域按构建版本显示实际核心；single 显示 mihomo 版本，dual 保留 Xray/Rust 信息。
- 修复连接按钮、通知、磁贴、流量和错误恢复等表面状态与诊断文案。
- 公网 IP 查询失败时明确提示 VPN 仍保持连接。

## 发布包

- dual：arm64-v8a、armeabi-v7a、x86_64、universal
- single：arm64-v8a、armeabi-v7a、x86_64、universal

本版本已通过 dual/single JVM 测试、AndroidTest APK 构建、single 表面回归和 mihomo Go 测试。
