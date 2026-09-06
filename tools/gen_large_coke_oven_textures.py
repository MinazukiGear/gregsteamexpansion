#!/usr/bin/env python3
"""Generate large coke oven presentation textures (coke-ovens.md 表现方案).

- furnace_door.png      熄灭炉门: 深色格栅 (16x16, 每格炉室墙面重复 1x3)
- furnace_door_lit.png  点燃炉门: 橙红火焰格栅 (与熄灭版同形, 仅颜色不同)
- status_flame.png      工作状态符号: 火焰形轮廓
- status_blocked.png    堵塞/等待输出状态符号: 带感叹号的箱形轮廓
- status_invalid.png    结构无效状态符号: 断裂轮廓

轮廓符号按设计要求以不同轮廓区分含义, 颜色仅作辅助。
"""
from PIL import Image
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "gregsteamexpansion", "textures", "block", "large_coke_oven")


def new_img():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    return img, img.load()


def save(img, name):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    img.save(path)
    print("wrote", path)


def furnace_door(lit):
    img, px = new_img()
    frame = (52, 48, 46, 255) if not lit else (72, 56, 44, 255)
    inner_off = (24, 20, 18, 255) if not lit else (140, 40, 10, 255)
    inner_on = (92, 34, 12, 255) if not lit else (232, 120, 24, 255)
    core = (56, 16, 6, 255) if not lit else (250, 190, 60, 255)
    # 外框
    for i in range(16):
        px[i, 0] = frame; px[i, 15] = frame
        px[0, i] = frame; px[15, i] = frame
    # 内部格栅: 竖条交替明暗, 点燃版在中央模拟火焰
    for x in range(2, 14):
        for y in range(2, 14):
            if lit and 5 <= x <= 10 and 6 + (x % 3) <= y <= 12 - (x % 2):
                px[x, y] = core if 6 <= x <= 9 and 8 <= y <= 11 else inner_on
            else:
                px[x, y] = inner_on if x % 2 == 0 else inner_off
    return img


def status_flame():
    img, px = new_img()
    c = (250, 140, 30, 255)
    outline = (120, 52, 8, 255)
    # 火焰轮廓 (上尖下圆)
    flame = {
        (8, 2), (7, 3), (8, 3), (9, 3),
        (6, 4), (7, 4), (8, 4), (9, 4), (10, 4),
        (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (11, 5),
        (5, 6), (4, 7), (5, 7), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (11, 6), (12, 7),
        (4, 8), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (11, 8), (12, 8),
        (4, 9), (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (11, 9), (12, 9),
        (5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10), (11, 10),
        (6, 11), (7, 11), (8, 11), (9, 11), (10, 11),
        (7, 12), (8, 12), (9, 12),
    }
    for (x, y) in flame:
        px[x, y] = c
    # 内焰
    for x in range(7, 10):
        for y in range(9, 12):
            px[x, y] = outline
    px[8, 10] = c
    return img


def status_blocked():
    img, px = new_img()
    c = (222, 168, 40, 255)
    # 箱形轮廓
    for i in range(3, 13):
        px[i, 3] = c; px[i, 12] = c
        px[i, 4] = c; px[i, 11] = c
    for j in range(3, 13):
        px[3, j] = c; px[12, j] = c
        px[4, j] = c; px[11, j] = c
    # 感叹号
    for j in range(6, 10):
        px[8, j] = c
    px[8, 10] = c
    return img


def status_invalid():
    img, px = new_img()
    c = (196, 60, 48, 255)
    # 断裂轮廓: 左上/右下两块错位碎片 + 中间裂纹
    for i in range(3, 8):
        px[i, 5] = c; px[i, 6] = c
    for i in range(9, 14):
        px[i, 9] = c; px[i, 10] = c
    for j in range(5, 9):
        px[3, j] = c; px[7, j] = c
    for j in range(8, 12):
        px[9, j] = c; px[13, j] = c
    # 裂纹斜线
    px[8, 4] = c; px[8, 5] = c; px[8, 6] = c
    px[8, 9] = c; px[8, 10] = c; px[8, 11] = c
    return img


def main():
    save(furnace_door(False), "furnace_door.png")
    save(furnace_door(True), "furnace_door_lit.png")
    save(status_flame(), "status_flame.png")
    save(status_blocked(), "status_blocked.png")
    save(status_invalid(), "status_invalid.png")


if __name__ == "__main__":
    main()
