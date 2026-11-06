#!/bin/bash
# 实时监控测速功能

export PATH="$PATH:$LOCALAPPDATA/Android/Sdk/platform-tools"
DEVICE="emulator-5556"

echo "=========================================="
echo "测速功能实时监控"
echo "=========================================="
echo ""
echo "清空日志..."
adb -s $DEVICE logcat -c

echo "启动应用..."
adb -s $DEVICE shell am start -n com.iceflow/.MainActivity

echo ""
echo "=========================================="
echo "现在请在模拟器中："
echo "1. 连接 VPN（点击启动按钮）"
echo "2. 点击连接面板右侧区域触发测速"
echo ""
echo "正在监控日志（按 Ctrl+C 停止）..."
echo "=========================================="
echo ""

# 实时监控测速相关日志
adb -s $DEVICE logcat -s "ConnectionProbe:*" "MainViewModel:*" "IceFlowVpnService:*" | grep --line-buffered -E "(测速|measure|latency|Connected|VPN)"
