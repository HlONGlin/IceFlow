"""生成 IceFlow 的图标资源。

- 启动图标：品牌雪花（沿用 Material ac_unit 的经典六臂几何，已验证在小尺寸下清晰）
- 磁贴图标：盾牌 + 六芒星，与启动图标明确区分，避免「多个启动按钮」时难以分辨

几何全部由函数生成而非手写 pathData，减少错位风险。
"""
import math
import os

OUT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable"))

CYAN = "#FF00D4FF"
DEEP = "#FF071A2B"   # 深海军蓝，用作盾牌内的镂空
DIM = "#FF93A9BF"    # 未连接时的冷灰蓝


def write(path, content):
    full = os.path.join(OUT, path)
    with open(full, "w", encoding="utf-8") as fh:
        fh.write(content)
    print("wrote", os.path.basename(full))


def star_spines(cx, cy, tip, arms=6, phase=90.0, inner=0.0):
    """各臂中心→尖端的线段。inner>0 时留出中心空隙。"""
    segs = []
    for i in range(arms):
        ang = math.radians(phase + i * 360.0 / arms)
        dx, dy = math.cos(ang), math.sin(ang)
        segs.append(
            f"M{cx + dx * inner:.2f},{cy + dy * inner:.2f} "
            f"L{cx + dx * tip:.2f},{cy + dy * tip:.2f}"
        )
    return segs


def strokes_xml(segments, color, width, indent="    ", cap="round"):
    if not segments:
        return ""
    return (
        f'{indent}<path\n'
        f'{indent}    android:strokeColor="{color}"\n'
        f'{indent}    android:strokeWidth="{width}"\n'
        f'{indent}    android:strokeLineCap="{cap}"\n'
        f'{indent}    android:strokeLineJoin="round"\n'
        f'{indent}    android:pathData="{" ".join(segments)}" />'
    )


def hex_path(cx, cy, r, phase=90.0):
    pts = []
    for i in range(6):
        a = math.radians(phase + i * 60)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return "M%.2f,%.2f" % pts[0] + "".join(" L%.2f,%.2f" % p for p in pts[1:]) + " Z"


def shield_path(cx=12.0, top=1.7, bottom=22.3, half_w=8.1, shoulder=4.55):
    """盾牌轮廓：顶部两角，下方收拢成弧形尖端。"""
    l, r = cx - half_w, cx + half_w
    return (
        f"M{cx},{top} L{l},{shoulder} L{l},11.3 "
        f"C{l},16.9 {cx - 4.4},20.6 {cx},{bottom} "
        f"C{cx + 4.4},20.6 {r},16.9 {r},11.3 "
        f"L{r},{shoulder} Z"
    )


def snowflake_solid(color, size=24.0, cx=12.0, cy=12.0):
    """实心雪花：沿用 Material ac_unit 的六臂几何，按目标尺寸缩放。"""
    s = size / 24.0
    raw = ("M11,2h2v4.2l2.1,-2.1 1.4,1.4L13,9v2h2l3.5,-3.5 1.4,1.4L17.8,11H22v2h-4.2l2.1,2.1 "
           "-1.4,1.4L15,13h-2v2l3.5,3.5 -1.4,1.4L13,17.8V22h-2v-4.2l-2.1,2.1 -1.4,-1.4L11,15v-2H9l-3.5,3.5 "
           "-1.4,-1.4L6.2,13H2v-2h4.2L4.1,8.9l1.4,-1.4L9,11h2V9L7.5,5.5l1.4,-1.4L11,6.2z")
    return raw


def launcher_foreground():
    """启动图标前景：白色实心雪花（Material ac_unit 几何），108 视口放大 3 倍后居中。"""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 启动图标前景：实心雪花，品牌主视觉 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:scaleX="2.6"
        android:scaleY="2.6"
        android:translateX="25.2"
        android:translateY="25.2">
        <path
            android:fillColor="#FFFFFFFF"
            android:pathData="{snowflake_solid('#FFFFFFFF')}" />
    </group>
</vector>
'''


def launcher_monochrome():
    """Android 13+ 主题图标（单色层）。"""
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
        android:translateX="25.2"
        android:translateY="25.2">
        <path
            android:fillColor="#FF000000"
            android:pathData="{snowflake_solid('#FF000000')}" />
    </group>
</vector>
'''


def tile_active():
    """磁贴·已连接：实心青色盾牌 + 深色六芒星。"""
    star = strokes_xml(star_spines(12.0, 12.3, 5.7, 6), DEEP, 1.55, "        ")
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 磁贴：已连接。实心盾牌 + 镂空六芒星 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="{CYAN}"
        android:pathData="{shield_path()}" />
{star}
</vector>
'''


def tile_off():
    """磁贴·未连接：同款盾牌描边版，形态统一只是明暗不同。"""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 磁贴：未连接。同一款盾牌描边版，保持视觉一致 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:strokeColor="{DIM}"
        android:strokeWidth="1.7"
        android:strokeLineJoin="round"
        android:pathData="{shield_path()}" />
</vector>
'''


if __name__ == "__main__":
    write("ic_launcher_foreground.xml", launcher_foreground())
    write("ic_launcher_monochrome.xml", launcher_monochrome())
    write("ic_tile.xml", tile_active())
    write("ic_tile_off.xml", tile_off())


def _flatten_shield_samples():
    """把盾牌轮廓采样成折线点，用于生成虚线段。"""
    from preview_icons import flatten
    pts = flatten(shield_path())
    return [(x, y) for x, y in pts]


def dashed_shield_path(dash=2.4, gap=1.7):
    """沿盾牌轮廓生成虚线路径（VectorDrawable 不支持 strokeDashWidth，只能手写）。"""
    pts = _flatten_shield_samples()
    # 累计弧长，按 dash/gap 交替输出子路径
    segs = []
    cur = None
    acc = 0.0
    period = dash + gap
    for i, p in enumerate(pts):
        if i > 0:
            dx, dy = p[0] - pts[i - 1][0], p[1] - pts[i - 1][1]
            acc += math.hypot(dx, dy)
        phase = acc % period
        on = phase < dash
        if on and cur is None:
            cur = [p]
        elif on:
            cur.append(p)
        elif cur is not None:
            segs.append(cur)
            cur = None
    if cur:
        segs.append(cur)
    out = []
    for seg in segs:
        if len(seg) < 2:
            continue
        out.append("M%.2f,%.2f" % seg[0] + "".join(" L%.2f,%.2f" % q for q in seg[1:]))
    return " ".join(out)


def tile_connecting():
    """磁贴·连接中：虚线盾牌 + 青色六芒星。"""
    star = strokes_xml(star_spines(12.0, 12.3, 5.7, 6), CYAN, 1.35, "        ")
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 磁贴：连接中。虚线盾牌 + 青色六芒星 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:strokeColor="{DIM}"
        android:strokeWidth="1.7"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="{dashed_shield_path()}" />
{star}
</vector>
'''
