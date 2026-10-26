"""生成 IceFlow 的图标资源。

设计语言：白 + 浅蓝，母题是「被水流环绕的雪花」。
- 启动图标：浅蓝渐变背景 → 白色雪花 + 蓝色水流弧。
- 磁贴图标：默认与应用图标同款；点击后进入「连接中」时切换为旋转水流弧的动画，
  形成流动感。
- 通知图标：只保留雪花剪影，状态栏只取 alpha 通道，不能有镂空和描边。

几何全部由函数生成而非手写 pathData，减少错位风险。
"""
import math
import os

OUT = os.path.normpath(
    os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable")
)

# ---- 配色：白底 + 深蓝主色 + 橙色点缀 ----
WHITE = "#FFFFFFFF"          # 纯白
DEEP_BLUE = "#FF1E3A8A"      # 深蓝（主色，圆环）
LIGHT_BLUE = "#FF3B82F6"     # 亮蓝（流线）
ORANGE = "#FFFF6B35"         # 活力橙（高光点、强调）
BG_WHITE = "#FFFFFFFF"       # 白色背景
TILE_ON = "#FFFFFFFF"        # 磁贴·已连接（实心白）

# 磁贴水流动画的帧数（等分一周）
TILE_FLOW_FRAMES = 12

# 雪花几何：Material ac_unit 的六臂轮廓，24×24 视口，跨度 2..22
SNOW_RAW = ("M11,2h2v4.2l2.1,-2.1 1.4,1.4L13,9v2h2l3.5,-3.5 1.4,1.4L17.8,11H22v2h-4.2l2.1,2.1 "
            "-1.4,1.4L15,13h-2v2l3.5,3.5 -1.4,1.4L13,17.8V22h-2v-4.2l-2.1,2.1 -1.4,-1.4L11,15v-2H9l-3.5,3.5 "
            "-1.4,-1.4L6.2,13H2v-2h4.2L4.1,8.9l1.4,-1.4L9,11h2V9L7.5,5.5l1.4,-1.4L11,6.2z")


def write(path, content):
    full = os.path.join(OUT, path)
    with open(full, "w", encoding="utf-8") as fh:
        fh.write(content)
    print("wrote", os.path.basename(full))


# ---------------------------------------------------------------- 几何工具
def arc_points(cx, cy, r, a0, a1, steps=30):
    """圆弧折线点，角度制（屏幕坐标 y 向下，顺时针为正）。"""
    pts = []
    for i in range(steps + 1):
        a = math.radians(a0 + (a1 - a0) * i / steps)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def polyline_path(pts):
    if not pts:
        return ""
    return "M%.2f,%.2f" % pts[0] + "".join(" L%.2f,%.2f" % p for p in pts[1:])


def flow_arcs(cx=12.0, cy=12.0):
    """水流弧：长短不一的断弧，同向绕行，暗示流向而不是静态圆环。"""
    return [
        polyline_path(arc_points(cx, cy, 11.2, -122, 58)),  # 外圈长弧
        polyline_path(arc_points(cx, cy, 11.2, 80, 104)),    # 外圈短促引导段
        polyline_path(arc_points(cx, cy, 8.1, 158, 296)),    # 内圈反向错开
    ]


def strokes_xml(segments, color, width, indent="    ", cap="round"):
    if not segments:
        return ""
    out = []
    for seg in segments:
        out.append(
            f'{indent}<path\n'
            f'{indent}    android:strokeColor="{color}"\n'
            f'{indent}    android:strokeWidth="{width}"\n'
            f'{indent}    android:strokeLineCap="{cap}"\n'
            f'{indent}    android:strokeLineJoin="round"\n'
            f'{indent}    android:pathData="{seg}" />'
        )
    return "\n".join(out)


def snow_group(scale, indent="    ", name=None):
    """把 24 视口里的雪花缩放居中，返回 <group> 开头标签。"""
    tx = 12.0 * (1 - scale)
    ty = 12.0 * (1 - scale)
    name_attr = f'\n{indent}    android:name="{name}"' if name else ""
    return (
        f'{indent}<group{name_attr}\n'
        f'{indent}    android:scaleX="{scale}"\n'
        f'{indent}    android:scaleY="{scale}"\n'
        f'{indent}    android:translateX="{tx:.3f}"\n'
        f'{indent}    android:translateY="{ty:.3f}">'
    )


def snow_fill_path(color, indent="        "):
    return (
        f'{indent}<path\n'
        f'{indent}    android:fillColor="{color}"\n'
        f'{indent}    android:pathData="{SNOW_RAW}" />'
    )


def snow_grad_path(indent="        "):
    """雪花用白 → 浅蓝的线性渐变填充。"""
    return f'''{indent}<path
{indent}    android:pathData="{SNOW_RAW}">
{indent}    <aapt:attr xmlns:aapt="http://schemas.android.com/aapt" name="android:fillColor">
{indent}        <gradient
{indent}            android:type="linear"
{indent}            android:startX="12" android:startY="2"
{indent}            android:endX="12" android:endY="22">
{indent}            <item android:offset="0" android:color="{SNOW_LO}" />
{indent}            <item android:offset="1" android:color="{SNOW_HI}" />
{indent}        </gradient>
{indent}    </aapt:attr>
{indent}</path>'''


def launcher_background():
    """启动图标背景：纯白色。"""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 启动图标背景：纯白色 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="{BG_WHITE}"
        android:pathData="M0,0h108v108h-108z" />
</vector>
'''


def launcher_foreground():
    """启动图标前景：字母 F 艺术设计 - 深蓝主体 + 对称亮蓝流线 + 橙色右向箭头。"""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 启动图标前景：字母 F 艺术设计 - 深蓝主体 + 对称亮蓝流线 + 橙色右向箭头 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:scaleX="2.6"
        android:scaleY="2.6"
        android:translateX="22.8"
        android:translateY="22.8">
        <!-- 深蓝 F 字母主体 -->
        <path
            android:strokeColor="{DEEP_BLUE}"
            android:strokeWidth="2.6"
            android:strokeLineCap="round"
            android:strokeLineJoin="round"
            android:fillColor="#00000000"
            android:pathData="M 6,4 L 6,20 M 6,4 L 17,4 M 6,11.5 L 14,11.5" />
        <!-- 亮蓝流线（上下对称，保持平衡） -->
        <path
            android:strokeColor="{LIGHT_BLUE}"
            android:strokeWidth="1.6"
            android:strokeLineCap="round"
            android:fillColor="#00000000"
            android:alpha="0.85"
            android:pathData="M 8.5,6 L 15,6 M 8.5,13.5 L 12,13.5" />
        <!-- 橙色三角箭头：位于 F 左上角顶点，指向右 -->
        <path
            android:fillColor="{ORANGE}"
            android:pathData="M 6,3 L 9,4.5 L 6,6 Z" />
    </group>
</vector>
'''



def launcher_monochrome():
    """Android 13+ 主题图标（单色层）：雪花 + 水流弧，随系统取色。"""
    arcs = strokes_xml(flow_arcs(), "#FF000000", 1.5, "        ")
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Android 13+ 主题图标（单色层），跟随系统取色 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:scaleX="2.6"
        android:scaleY="2.6"
        android:translateX="22.8"
        android:translateY="22.8">
{arcs}
{snow_group(0.95, "        ")}
{snow_fill_path("#FF000000", "            ")}
        </group>
    </group>
</vector>
'''


# ---------------------------------------------------------------- 磁贴图标
def _mark(arc_color, flake_fill, flake_stroke=None, indent="        "):
    """磁贴标记：水流弧 + 雪花。"""
    arcs = strokes_xml(flow_arcs(), arc_color, 1.5, indent)
    if flake_stroke:
        flake = (
            f'{indent}<path\n'
            f'{indent}    android:fillColor="{flake_fill}"\n'
            f'{indent}    android:strokeColor="{flake_stroke}"\n'
            f'{indent}    android:strokeWidth="0.45"\n'
            f'{indent}    android:strokeLineJoin="round"\n'
            f'{indent}    android:pathData="{SNOW_RAW}" />'
        )
    else:
        flake = snow_fill_path(flake_fill, indent)
    return arcs + "\n" + flake


def tile_default():
    """磁贴·默认：深蓝 F 字母 + 亮蓝流线。"""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 磁贴·默认：深蓝 F 字母 + 亮蓝流线 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <!-- 深蓝 F 主体 -->
    <path
        android:strokeColor="{DEEP_BLUE}"
        android:strokeWidth="2.6"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:fillColor="#00000000"
        android:pathData="M 6,4 L 6,20 M 6,4 L 17,4 M 6,11.5 L 14,11.5" />
    <!-- 亮蓝流线 -->
    <path
        android:strokeColor="{LIGHT_BLUE}"
        android:strokeWidth="1.6"
        android:strokeLineCap="round"
        android:fillColor="#00000000"
        android:alpha="0.85"
        android:pathData="M 8.5,6 L 15,6 M 8.5,13.5 L 12,13.5" />
</vector>
'''



def tile_active():
    """磁贴·已连接：白色 F 字母 + 流线。"""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 磁贴·已连接：白色 F 字母 + 流线 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <!-- 白色 F 主体 -->
    <path
        android:strokeColor="{TILE_ON}"
        android:strokeWidth="2.6"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:fillColor="#00000000"
        android:pathData="M 6,4 L 6,20 M 6,4 L 17,4 M 6,11.5 L 14,11.5" />
    <!-- 白色流线 -->
    <path
        android:strokeColor="{TILE_ON}"
        android:strokeWidth="1.6"
        android:strokeLineCap="round"
        android:fillColor="#00000000"
        android:alpha="0.75"
        android:pathData="M 8.5,6 L 15,6 M 8.5,13.5 L 12,13.5" />
</vector>
'''


def tile_flow_frame(frame_index):
    """磁贴·水流动画的一帧：橙色三角箭头沿 F 字母笔画运动。

    动画路径（12 帧）：从 F 的左上角顶点出发，沿笔画顺序运动：
    - 起点：F 左上角 (6,4)
    - 上横笔：从左向右 (6,4) → (17,4)
    - 右端向下：转向左竖笔
    - 左竖笔：向下 (6,4) → (6,20)
    - 中横笔：从左向右 (6,11.5) → (14,11.5)
    箭头的朝向跟随当前运动方向（右、下）。
    """
    # F 的笔画路径关键点：
    # 左竖笔：(6, 4) → (6, 20)
    # 上横笔：(6, 4) → (17, 4)
    # 中横笔：(6, 11.5) → (14, 11.5)

    # 每一帧的箭头路径（三角形）+ 注释
    # 箭头格式：尖端在前，底边在后
    arrows = [
        # 帧 0: 起点 - F 左上角，箭头向右
        "M 6,3 L 9,4.5 L 6,6 Z",
        # 帧 1-4: 上横笔，箭头向右移动
        "M 8,3 L 11,4.5 L 8,6 Z",
        "M 10.5,3 L 13.5,4.5 L 10.5,6 Z",
        "M 13,3 L 16,4.5 L 13,6 Z",
        "M 15.5,3 L 18.5,4.5 L 15.5,6 Z",
        # 帧 5: 上横笔末端，准备转向
        "M 17,4.5 L 18.5,7.5 L 15.5,7.5 Z",
        # 帧 6-8: 左竖笔上段，箭头向下
        "M 4.5,7 L 6,10 L 7.5,7 Z",
        "M 4.5,10 L 6,13 L 7.5,10 Z",
        "M 4.5,13 L 6,16 L 7.5,13 Z",
        # 帧 9: 中横笔起点，箭头转向右
        "M 6,10.5 L 9,12 L 6,13.5 Z",
        # 帧 10-11: 中横笔，箭头向右
        "M 9,10.5 L 12,12 L 9,13.5 Z",
        "M 12,10.5 L 15,12 L 12,13.5 Z",
    ]

    arrow_path = arrows[frame_index % len(arrows)]

    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 磁贴·水流帧 {frame_index:02d}：橙色箭头沿 F 笔画运动 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <!-- 深蓝 F 主体 -->
    <path
        android:strokeColor="{DEEP_BLUE}"
        android:strokeWidth="2.6"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:fillColor="#00000000"
        android:pathData="M 6,4 L 6,20 M 6,4 L 17,4 M 6,11.5 L 14,11.5" />
    <!-- 亮蓝流线（上下对称） -->
    <path
        android:strokeColor="{LIGHT_BLUE}"
        android:strokeWidth="1.6"
        android:strokeLineCap="round"
        android:fillColor="#00000000"
        android:alpha="0.85"
        android:pathData="M 8.5,6 L 15,6 M 8.5,13.5 L 12,13.5" />
    <!-- 橙色三角箭头 -->
    <path
        android:fillColor="{ORANGE}"
        android:pathData="{arrow_path}" />
</vector>
'''


# ---------------------------------------------------------------- 通知图标
def notification():
    """通知栏小图标：只取 alpha 通道，必须是实心剪影，不能有镂空/描边。"""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 通知图标：实心雪花剪影（状态栏只取 alpha 通道） -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
{snow_fill_path(WHITE, "    ")}
</vector>
'''


if __name__ == "__main__":
    write("ic_launcher_background.xml", launcher_background())
    write("ic_launcher_foreground.xml", launcher_foreground())
    write("ic_launcher_monochrome.xml", launcher_monochrome())
    write("ic_notification.xml", notification())
    write("ic_tile.xml", tile_default())
    write("ic_tile_active.xml", tile_active())
    # 水流动画：12 帧沿 F 字母路径滑动
    for i in range(TILE_FLOW_FRAMES):
        write(f"ic_tile_flow_{i:02d}.xml", tile_flow_frame(i))
