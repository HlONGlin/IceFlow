# IceFlow v0.1.1 更新日志

## 🚀 性能优化

### 重连速度大幅提升
- **典型重连时间从 1.8秒 降至 0.4秒**（提升 78%）
- **网络切换响应从 500ms 降至 100ms**（提升 80%）
- **失败重试总时间从 15秒 降至 6.7秒**（减少 55%）

**技术实现：**
- 智能核心复用：同节点重连时保留 xray-core 进程
- 激进重试策略：200ms → 500ms → 1s → 2s → 3s
- 快速网络响应：网络事件延迟降至 100ms
- 配置快速切换：debounce 优化至 150ms
- 配置缓存复用：缓存 tunnel 配置避免重复生成

## 🐛 Bug 修复

### 节点名称显示优化
- **修复从 v2rayN 复制的节点名称显示问题**
  - 之前：无名称节点显示为 "127.0.0.1:8080"
  - 现在：智能生成友好名称 "Proxy 127.0.0.1"、"Node 192.168.1.1" 等
  - 支持 vmess、vless、ss、trojan 等所有协议类型

### UI 对齐优化
- **优化底部标签栏对齐**
  - 统一图标和文字的垂直对齐方式
  - 优化视觉平衡，提升整体观感
  - 标签间距更加协调

## 📖 完整技术文档

详细的重连优化说明请参阅：
- `docs/RECONNECTION_OPTIMIZATION_TECHNICAL.md` - 完整技术文档
- `优化说明.md` - 中文快速总结
- `重连优化说明.txt` - 用户友好说明

---

## Changelog (English)

## 🚀 Performance Improvements

### Dramatically Faster Reconnection
- **Typical reconnection time reduced from 1.8s to 0.4s** (78% improvement)
- **Network switch response from 500ms to 100ms** (80% improvement)
- **Total retry time from 15s to 6.7s** (55% reduction)

**Technical Implementation:**
- Smart core reuse: preserve xray-core process on same-node reconnection
- Aggressive retry strategy: 200ms → 500ms → 1s → 2s → 3s
- Fast network response: network event delay reduced to 100ms
- Quick configuration switching: debounce optimized to 150ms
- Configuration cache reuse: cache tunnel configurations

## 🐛 Bug Fixes

### Node Name Display Enhancement
- **Fixed node name display from v2rayN clipboard import**
  - Before: unnamed nodes showed "127.0.0.1:8080"
  - Now: generates friendly names like "Proxy 127.0.0.1", "Node 192.168.1.1"
  - Supports all protocols: vmess, vless, ss, trojan, etc.

### UI Alignment Improvements
- **Optimized bottom navigation bar alignment**
  - Unified vertical alignment for icons and labels
  - Improved visual balance and overall appearance
  - More harmonious tab spacing

## 📖 Full Technical Documentation

For detailed reconnection optimization:
- `docs/RECONNECTION_OPTIMIZATION_TECHNICAL.md` - Full technical documentation
- `优化说明.md` - Quick summary (Chinese)
- `重连优化说明.txt` - User-friendly explanation (Chinese)
