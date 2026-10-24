#!/bin/bash
# 完整的测速功能诊断脚本

export PATH="$PATH:$LOCALAPPDATA/Android/Sdk/platform-tools"
DEVICE="emulator-5556"

echo "=========================================="
echo "IceFlow 测速功能完整诊断"
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
echo "=========================================="
echo "请在模拟器中执行以下操作："
echo ""
echo "步骤 1: 连接 VPN"
echo "  - 选择一个节点"
echo "  - 点击底部中间的启动按钮"
echo "  - 等待连接成功（按钮变蓝色）"
echo ""
echo "步骤 2: 触发测速"
echo "  - 点击连接面板右侧区域（显示时间的地方）"
echo "  - 应该会显示 '测速中…'"
echo "  - 等待结果显示"
echo ""
echo "完成后按 Enter 键查看日志..."
echo "=========================================="
read -r

echo ""
echo "=========================================="
echo "诊断结果"
echo "=========================================="
echo ""

echo "--- VPN 连接状态 ---"
adb -s $DEVICE logcat -d | grep -E "VPN 已连接|VPN 失败|socksPort" | tail -5

echo ""
echo "--- 测速触发日志 ---"
adb -s $DEVICE logcat -d -s "MainViewModel:*" | grep -i "测速"

echo ""
echo "--- ConnectionProbe 详细日志 ---"
adb -s $DEVICE logcat -d -s "ConnectionProbe:*"

echo ""
echo "--- 应用日志写入 ---"
adb -s $DEVICE logcat -d | grep "当前连接测速"

echo ""
echo "=========================================="
echo "分析结果："
echo ""

# 检查是否有测速日志
if adb -s $DEVICE logcat -d -s "ConnectionProbe:*" | grep -q "开始测速"; then
    echo "✓ 测速功能已触发"

    if adb -s $DEVICE logcat -d -s "ConnectionProbe:*" | grep -q "测速完成"; then
        LATENCY=$(adb -s $DEVICE logcat -d -s "ConnectionProbe:*" | grep "测速完成" | tail -1 | grep -oP '\d+ms' | head -1)
        echo "✓ 测速成功完成: $LATENCY"

        if [ "$LATENCY" == "200ms" ] || adb -s $DEVICE logcat -d -s "ConnectionProbe:*" | grep -q "200ms"; then
            echo "⚠ 警告: 延迟固定为 200ms，可能存在问题"
        fi
    else
        echo "✗ 测速未完成或超时"
    fi
else
    echo "✗ 测速功能未被触发"
    echo ""
    echo "可能的原因："
    echo "1. 点击位置不正确（应该点击连接面板右侧区域）"
    echo "2. VPN 未连接"
    echo "3. UI 事件未正确绑定"
fi

echo ""
echo "=========================================="
echo "完整日志已保存，请检查上述输出"
echo "=========================================="
