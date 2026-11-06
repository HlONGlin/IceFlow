#!/bin/bash
# 测试连接测速功能

export PATH="$PATH:$LOCALAPPDATA/Android/Sdk/platform-tools"
DEVICE="emulator-5556"

echo "=========================================="
echo "测速功能诊断"
echo "=========================================="

echo ""
echo "1. 清空日志..."
adb -s $DEVICE logcat -c

echo ""
echo "2. 等待5秒收集日志..."
sleep 5

echo ""
echo "3. 检查 VPN 连接状态..."
adb -s $DEVICE logcat -d | grep -iE "(VPN 已连接|VPN 失败|Connected|socksPort)" | tail -10

echo ""
echo "4. 检查测速相关日志..."
adb -s $DEVICE logcat -d | grep -iE "(ConnectionProbe|测速|measure|latency)" | tail -10

echo ""
echo "5. 检查应用日志..."
adb -s $DEVICE logcat -d | grep -E "IceFlow|MainViewModel" | tail -20

echo ""
echo "=========================================="
echo "请在模拟器中："
echo "1. 确保已连接 VPN（启动按钮显示蓝色）"
echo "2. 点击连接面板右侧区域触发测速"
echo "3. 观察是否显示 '测速中…'"
echo "4. 等待结果显示"
echo "=========================================="
