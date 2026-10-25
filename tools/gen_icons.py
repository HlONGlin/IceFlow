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

# ---- 配色：白 + 浅蓝 ----
WHITE = "#FFFFFFFF"          # 雪花高光 / 纯白
SNOW_LO = "#FFFFFFFF"        # 雪花渐变起点（白）
SNOW_HI = "#FFCDEBFF"        # 雪花渐变终点（浅蓝）
ICE = "#FF4FB2EE"            # 水流弧主色（蓝，在浅蓝底上保证对比度）
BG_LO = "#FFEAF7FF"          # 背景渐变起点（极浅蓝）
BG_HI = "#FF7EC8F3"          # 背景渐变终点（浅蓝）
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
    """启动图标背景：白 → 浅蓝的对角渐变。"""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 启动图标背景：白 → 浅蓝渐变 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr xmlns:aapt="http://schemas.android.com/aapt" name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108">
                <item android:offset="0" android:color="{BG_LO}" />
                <item android:offset="0.5" android:color="#FFBCE4FA" />
                <item android:offset="1" android:color="{BG_HI}" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''


def launcher_foreground():
    """启动图标前景：白色雪花 + 蓝色水流弧（浅蓝底上的流动）。"""
    arcs = strokes_xml(flow_arcs(), ICE, 1.5, "        ")
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 启动图标前景：白色雪花 + 蓝色水流弧 -->
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
{snow_grad_path("            ")}
        </group>
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
    """磁贴·默认：与应用图标同款标记（雪花 + 水流弧）。"""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 磁贴·默认：与应用图标同款，雪花 + 水流弧 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
{_mark(ICE, WHITE, ICE)}
</vector>
'''



def tile_active():
    """磁贴·已连接：实心白色，状态明确。"""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 磁贴·已连接：实心雪花 + 白色水流弧 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
{_mark(TILE_ON, TILE_ON)}
</vector>
'''


def tile_flow_frame(angle):
    """磁贴·水流动画的一帧：高光珠沿水流弧滑动到 angle 度。

    水流弧本身是三等分对称的，整体旋转看不出来，所以让不对称的高光珠
    绕圆心旋转，才能明确表达「在流动」。磁贴图标由 SystemUI 绘制，
    animated-vector 在那边不会真的跑动画，所以改成切帧 + updateTile()。
    """
    arcs = strokes_xml(flow_arcs(), ICE, 1.5, "        ")
    comet = f"""    <group
        android:name="comet"
        android:pivotX="12"
        android:pivotY="12"
        android:rotation="{angle}">
        <path
            android:fillColor="#CCFFFFFF"
            android:pathData="M10.55,1.28 L11.85,0.62 L12.62,1.72 Z" />
        <path
            android:fillColor="#FFFFFFFF"
            android:pathData="M12.0,0.15 a0.95,0.95 0 1,0 0.01,0 z" />
    </group>
"""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 磁贴·水流帧：高光珠沿水流弧滑动 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <group>
{arcs}
    </group>
{comet}{snow_group(0.44, "    ")}
{snow_fill_path(WHITE)}
    </group>
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
    # 水流动画：12 帧等分一周，由磁贴服务按帧率切换
    for i in range(TILE_FLOW_FRAMES):
        write(f"ic_tile_flow_{i:02d}.xml", tile_flow_frame(i * 360 // TILE_FLOW_FRAMES))
