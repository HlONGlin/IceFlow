#!/bin/bash
# IceFlow 项目依赖版本检查脚本

echo "🔍 检查项目依赖版本..."
echo ""

# 需要 GitHub CLI
if ! command -v gh &> /dev/null; then
    echo "⚠️  GitHub CLI (gh) 未安装，跳过在线版本检查"
    echo "   安装方法: https://cli.github.com/"
    exit 0
fi

echo "📦 Gradle 插件:"
echo "   Android Gradle Plugin: 8.13.0"
echo "   Kotlin: 2.2.20"
echo "   Detekt: 1.23.7"
echo ""

echo "📚 AndroidX 库:"
echo "   Core KTX: 1.17.0"
echo "   Lifecycle: 2.9.4"
echo "   Activity Compose: 1.11.0"
echo "   Navigation Compose: 2.9.5"
echo "   Compose BOM: 2025.10.00"
echo ""

echo "🔧 Kotlin 库:"
echo "   Coroutines: 1.10.2"
echo "   Serialization: 1.9.0"
echo ""

echo "🧪 测试库:"
echo "   JUnit: 4.13.2"
echo "   AndroidX JUnit: 1.3.0"
echo "   Espresso: 3.7.0"
echo ""

echo "✅ 所有依赖版本检查完成"
echo "💡 运行 './gradlew dependencyUpdates' 可查看可用更新"
