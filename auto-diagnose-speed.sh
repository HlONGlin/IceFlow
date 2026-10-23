#!/bin/bash
# 完整的测速问题诊断 - 实时版本

export PATH="$PATH:$LOCALAPPDATA/Android/Sdk/platform-tools"
DEVICE="emulator-5556"

echo "=========================================="
echo "测速功能实时诊断（自动化版本）"
echo "=========================================="
echo ""

# 清空日志
echo "1. 清空日志..."
adb -s $DEVICE logcat -c

# 启动应用
echo "2. 启动应用..."
adb -s $DEVICE shell am force-stop com.iceflow
sleep 1
adb -s $DEVICE shell am start -n com.iceflow/.MainActivity

echo ""
echo "等待5秒让应用启动..."
sleep 5

echo ""
echo "=========================================="
echo "开始实时监控，请在模拟器中："
echo "1. 如果未连接，点击启动按钮连接 VPN"
echo "2. 点击连接面板（任意位置）触发测速"
echo ""
echo "日志将实时显示在下方（30秒后自动停止）"
echo "按 Ctrl+C 可提前停止"
echo "=========================================="
echo ""

# 实时监控所有相关日志
timeout 30 adb -s $DEVICE logcat | grep --line-buffered -iE "(ConnectionPanel|ConnectionProbe|MainViewModel.*测速|testRunningConnection)" || true

echo ""
echo "=========================================="
echo "监控结束，分析结果..."
echo "=========================================="
echo ""

# 分析日志
LOGS=$(adb -s $DEVICE logcat -d)

if echo "$LOGS" | grep -q "ConnectionPanel.*点击测速"; then
    echo "✓ 检测到点击事件"
    echo "$LOGS" | grep "ConnectionPanel.*点击测速" | tail -5
else
    echo "✗ 未检测到点击事件"
    echo "  可能原因："
    echo "  - VPN 未连接（connected=false）"
    echo "  - 正在测速中（testing=true）"
    echo "  - 点击位置不正确"
fi

echo ""

if echo "$LOGS" | grep -q "MainViewModel.*开始测速"; then
    echo "✓ testRunningConnection 被调用"
    echo "$LOGS" | grep "MainViewModel.*开始测速" | tail -3
else
    echo "✗ testRunningConnection 未被调用"
fi

echo ""

if echo "$LOGS" | grep -q "ConnectionProbe.*开始测速"; then
    echo "✓ ConnectionProbe.measure 被调用"
    echo ""
    echo "详细测速过程："
    echo "$LOGS" | grep "ConnectionProbe" | tail -10

    if echo "$LOGS" | grep -q "ConnectionProbe.*测速完成"; then
        RESULT=$(echo "$LOGS" | grep "ConnectionProbe.*测速完成" | tail -1)
        echo ""
        echo "测速结果: $RESULT"
    fi
else
    echo "✗ ConnectionProbe.measure 未被调用"
fi

echo ""
echo "=========================================="
echo "完整诊断报告完成"
echo "=========================================="
