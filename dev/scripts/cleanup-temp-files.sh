#!/bin/bash
# 清理项目中的临时文件和测试数据

echo "🧹 清理临时文件..."

# 清理临时发布文件
rm -f QUICK_RELEASE.txt
rm -f RELEASE_GUIDE*.md
rm -f RELEASE_COMPLETE*.md
rm -f RELEASE_SUMMARY*.md
rm -f open-apk-folder.sh

# 清理测试数据
rm -f servers_backup.json
rm -f servers_now.json
rm -f node.json
rm -f build.log

echo "✅ 清理完成！"
