#!/bin/bash
# 简化版测速测试 - 请手动操作

export PATH="$PATH:$LOCALAPPDATA/Android/Sdk/platform-tools"
DEVICE="emulator-5556"

echo "=========================================="
echo "简化版测速测试"
echo "=========================================="
echo ""
echo "步骤 1: 清空日志"
adb -s $DEVICE logcat -c

echo "步骤 2: 启动日志监控（后台）"
adb -s $DEVICE logcat > /tmp/speed-test.log 2>&1 &
LOGCAT_PID=$!

echo "步骤 3: 等待你在模拟器中操作..."
echo ""
echo "=========================================="
echo "请在模拟器中完成以下操作："
echo ""
echo "1. 打开 IceFlow 应用"
echo "2. 如果未连接，点击启动按钮连接 VPN"
echo "3. 连接成功后，点击连接面板（蓝色区域）任意位置"
echo "4. 观察是否显示 '测速中…'"
echo "5. 记录显示的延迟值（例如：200 ms）"
echo ""
echo "完成后按 Enter 键继续..."
echo "=========================================="
read -r

echo ""
echo "步骤 4: 停止日志监控"
kill $LOGCAT_PID 2>/dev/null

echo "步骤 5: 分析日志..."
echo ""
echo "=========================================="

# 检查点击事件
if grep -q "ConnectionPanel.*点击测速" /tmp/speed-test.log; then
    echo "✓ 检测到点击事件"
    grep "ConnectionPanel.*点击测速" /tmp/speed-test.log | tail -1
else
    echo "✗ 未检测到点击事件"
fi

echo ""

# 检查测速调用
if grep -q "MainViewModel.*开始测速" /tmp/speed-test.log; then
    echo "✓ testRunningConnection 被调用"
    grep "MainViewModel.*开始测速" /tmp/speed-test.log | tail -1
else
    echo "✗ testRunningConnection 未被调用"
fi

echo ""

# 检查 ConnectionProbe
if grep -q "ConnectionProbe" /tmp/speed-test.log; then
    echo "✓ ConnectionProbe 日志:"
    grep "ConnectionProbe" /tmp/speed-test.log
else
    echo "✗ 没有 ConnectionProbe 日志"
fi

echo ""
echo "=========================================="
echo "请告诉我："
echo "1. 你看到的延迟值是多少？（例如：200 ms）"
echo "2. 每次点击测速延迟值是否相同？"
echo "3. 是否看到上面的日志输出？"
echo "=========================================="

rm -f /tmp/speed-test.log
