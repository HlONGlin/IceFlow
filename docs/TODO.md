# 项目优化待办清单

## 🚨 立即处理（今天）

- [ ] **升级 JDK 到 17**
  ```powershell
  # 下载 JDK 17: https://adoptium.net/
  # 配置环境变量 JAVA_HOME
  java -version  # 验证
  ```

- [ ] **从仓库移除敏感文件**
  ```bash
  # 警告：这需要强制推送，会改写历史
  git rm --cached iceflow-release.jks
  git commit -m "chore: remove keystore from repository"

  # 将密钥移到安全位置
  mv iceflow-release.jks ~/secure/
  ```

- [ ] **提交优化改动**
  ```bash
  git add .
  git commit -m "chore: project cleanup and quality tools setup

  - Add EditorConfig for consistent code formatting
  - Add Detekt for static code analysis
  - Improve .gitignore rules
  - Add development scripts (cleanup, dependency check)
  - Move screenshots to assets/screenshots/ directory
  - Move Chinese docs to docs/ directory
  - Remove temporary build and test files

  Co-Authored-By: Claude <noreply@anthropic.com>"
  ```

## 📋 本周完成

- [ ] **验证 Detekt 配置**
  ```bash
  ./gradlew detekt
  # 检查报告: build/reports/detekt/detekt.html
  ```

- [ ] **验证依赖版本**
  - [ ] 确认 Compose BOM 2025.10.00 是否正确
  - [ ] 如需要，更新为 2024.10.00

- [ ] **改进 CI/CD**
  - [ ] 在 GitHub Actions 中添加 Detekt 检查
  - [ ] 在 GitHub Actions 中添加单元测试

- [ ] **文档改进**
  - [ ] 重命名中文文档为英文/拼音
  - [ ] 统一 CHANGELOG 管理策略

## 🔮 未来改进

### 代码质量
- [ ] 配置 JaCoCo 代码覆盖率
- [ ] 设置覆盖率目标（>60%）
- [ ] 添加 pre-commit hooks

### 监控与诊断
- [ ] 集成崩溃报告工具（Sentry/Crashlytics）
- [ ] 添加性能监控
- [ ] 改进日志收集

### 开发体验
- [ ] 添加依赖更新检查（Gradle Versions Plugin）
- [ ] 创建开发环境快速设置脚本
- [ ] 添加常用 Gradle 任务别名

### 国际化
- [ ] 完善多语言支持
- [ ] 添加更多语言翻译
- [ ] 检查文本硬编码

## 📝 笔记

### 当前问题
1. Java 8 环境无法运行新的 Gradle 任务
2. 签名密钥已暴露在仓库中
3. 部分文件命名不规范（中文）

### 项目优势
1. 代码质量高，无遗留 TODO
2. 测试覆盖完整（单元测试 + UI测试）
3. 文档齐全
4. 架构清晰（MVVM + Repository）

---

最后更新：2024-10-26
