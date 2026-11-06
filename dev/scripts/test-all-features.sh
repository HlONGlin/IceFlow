#!/bin/bash
# 测试 IceFlow 所有功能

export PATH="$PATH:$LOCALAPPDATA/Android/Sdk/platform-tools"
DEVICE="emulator-5556"

echo "=========================================="
echo "IceFlow 完整功能测试"
echo "=========================================="

echo ""
echo "1. 检查应用安装状态..."
adb -s $DEVICE shell pm list packages | grep iceflow
adb -s $DEVICE shell pm path com.iceflow

echo ""
echo "2. 检查原生库集成..."
adb -s $DEVICE shell "run-as com.iceflow ls -la /data/app/com.iceflow-*/lib/x86_64/ 2>/dev/null | grep -E '(libiceflow_core|libgojni)'" || echo "无法访问应用库目录（需要可调试应用）"

echo ""
echo "3. 清理日志并启动应用..."
adb -s $DEVICE logcat -c
adb -s $DEVICE shell am force-stop com.iceflow
adb -s $DEVICE shell am start -n com.iceflow/.MainActivity
sleep 3

echo ""
echo "4. 检查核心加载状态..."
adb -s $DEVICE logcat -d -s "XrayBridge:*" "RustBridge:*" | head -10

echo ""
echo "5. 检查启动日志（最近50行）..."
adb -s $DEVICE logcat -d | grep -iE "(iceflow|xray|rust)" | tail -50

echo ""
echo "=========================================="
echo "测试完成！"
echo "=========================================="
echo ""
echo "手动测试步骤："
echo "1. 在模拟器中打开 IceFlow 应用"
echo "2. 选择一个节点"
echo "3. 点击启动按钮（底部中间的圆形按钮）"
echo "4. 观察是否显示'已连接'提示"
echo "5. 测试拖动排序：点击节点菜单 -> 单独排序"
echo "6. 测试更新间隔：分组页 -> 编辑分组 -> 设置为0小时"
