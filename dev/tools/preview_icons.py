"""把 VectorDrawable 里的 path 渲染成 PNG，用于在没有 Android 设备时预览图标。

只支持本项目图标用到的指令子集：M L H V C Q Z 与相对版本，
以及 android:strokeColor / strokeWidth / strokeLineCap / fillColor。
"""
import math
import os
import re
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw

ANDROID = "{http://schemas.android.com/apk/res/android}"


def parse_color(value):
    """解析 Android 颜色字符串。Android 用 #AARRGGBB 顺序。"""
    value = value.strip()
    if value.startswith("#"):
        h = value[1:]
        if len(h) == 8:
            a, r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4, 6))
        elif len(h) == 6:
            a, r, g, b = 255, int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
        else:
            raise ValueError(value)
        return (r, g, b, a)
    raise ValueError(value)


def tokenize(d):
    return re.findall(r"[MmLlHhVvCcQqZz]|-?\d*\.?\d+(?:[eE][-+]?\d+)?", d)


def flatten(cmds, samples=24):
    """把路径指令展开为折线点列表 [(x,y), ...]。"""
    pts = []
    tok = tokenize(cmds)
    i = 0
    cur = (0.0, 0.0)
    start = (0.0, 0.0)
    cmd = None
    while i < len(tok):
        if re.match(r"[A-Za-z]", tok[i]):
            cmd = tok[i]
            i += 1
            if cmd in "Zz":
                cur = start
                pts.append(cur)
                continue
        nums = []
        need = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "Q": 4}[cmd.upper()]
        while i < len(tok) and len(nums) < need and not re.match(r"[A-Za-z]", tok[i]):
            nums.append(float(tok[i]))
            i += 1
        if len(nums) < need:
            break
        rel = cmd.islower()
        u = cmd.upper()
        if u == "M":
            x, y = nums
            if rel:
                x, y = cur[0] + x, cur[1] + y
            cur = (x, y)
            start = cur
            pts.append(cur)
            cmd = "l" if rel else "L"
        elif u == "L":
            x, y = nums
            if rel:
                x, y = cur[0] + x, cur[1] + y
            cur = (x, y)
            pts.append(cur)
        elif u == "H":
            x = nums[0]
            if rel:
                x = cur[0] + x
            cur = (x, cur[1])
            pts.append(cur)
        elif u == "V":
            y = nums[0]
            if rel:
                y = cur[1] + y
            cur = (cur[0], y)
            pts.append(cur)
        elif u == "C":
            x1, y1, x2, y2, x, y = nums
            if rel:
                x1, y1 = cur[0] + x1, cur[1] + y1
                x2, y2 = cur[0] + x2, cur[1] + y2
                x, y = cur[0] + x, cur[1] + y
            for k in range(1, samples + 1):
                t = k / samples
                mt = 1 - t
                px = mt**3 * cur[0] + 3 * mt**2 * t * x1 + 3 * mt * t**2 * x2 + t**3 * x
                py = mt**3 * cur[1] + 3 * mt**2 * t * y1 + 3 * mt * t**2 * y2 + t**3 * y
                pts.append((px, py))
            cur = (x, y)
        elif u == "Q":
            x1, y1, x, y = nums
            if rel:
                x1, y1 = cur[0] + x1, cur[1] + y1
                x, y = cur[0] + x, cur[1] + y
            for k in range(1, samples + 1):
                t = k / samples
                mt = 1 - t
                px = mt**2 * cur[0] + 2 * mt * t * x1 + t**2 * x
                py = mt**2 * cur[1] + 2 * mt * t * y1 + t**2 * y
                pts.append((px, py))
            cur = (x, y)
    return pts


def render(vector_path, out_png, size=256, bg=(10, 22, 38, 255), bg_alpha=255):
    tree = ET.parse(vector_path)
    root = tree.getroot()
    vw = float(root.get(ANDROID + "viewportWidth"))
    vh = float(root.get(ANDROID + "viewportHeight"))
    scale = size / max(vw, vh)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)

    def to_px(p):
        return (p[0] * scale, (vh - p[1]) * scale)

    # 递归遍历，继承 <group> 上的缩放/平移变换
    def walk(node, tf):
        for child in node:
            tag = child.tag
            if tag.endswith("group"):
                gtf = tf
                sx = float(child.get(ANDROID + "scaleX", "1"))
                sy = float(child.get(ANDROID + "scaleY", "1"))
                tx = float(child.get(ANDROID + "translateX", "0"))
                ty = float(child.get(ANDROID + "translateY", "0"))
                gtf = (gtf[0] * sx, gtf[1] * sy, gtf[2] + tx * gtf[0], gtf[3] + ty * gtf[1])
                walk(child, gtf)
            elif tag.endswith("path"):
                draw_path(child, tf)

    def draw_path(node, tf):
        d = node.get(ANDROID + "pathData")
        if not d:
            return
        sx, sy, tx, ty = tf
        pts = [to_px((x * sx + tx, y * sy + ty)) for x, y in flatten(d)]
        if len(pts) < 2:
            return
        fill = node.get(ANDROID + "fillColor")
        stroke = node.get(ANDROID + "strokeColor")
        width = float(node.get(ANDROID + "strokeWidth", "1"))
        if fill:
            color = parse_color(fill)
            draw.polygon(pts, fill=color)
        if stroke:
            color = parse_color(stroke)
            w = max(1, int(round(width * scale)))
            draw.line(pts, fill=color, width=w, joint="curve")
            r = w / 2
            for pt in (pts[0], pts[-1]):
                draw.ellipse([pt[0] - r, pt[1] - r, pt[0] + r, pt[1] + r], fill=color)
    walk(root, (1.0, 1.0, 0.0, 0.0))
    if bg_alpha:
        base = Image.new("RGBA", (size, size), bg[:3] + (bg_alpha,))
        base.alpha_composite(layer)
        img = base
    else:
        img = layer
    img.save(out_png)
    print("rendered", out_png)


if __name__ == "__main__":
    import sys
    res = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res", "drawable")
    out = os.path.join(os.path.dirname(__file__), "..", "..", "assets", "preview")
    os.makedirs(out, exist_ok=True)
    for name in ("ic_launcher_foreground", "ic_tile", "ic_tile_active"):
        render(os.path.join(res, name + ".xml"), os.path.join(out, name + ".png"))
