#!/usr/bin/env python3
"""Generate coke oven hatch mode box overlay textures (coke-ovens.md 运行表现).

Three identically-shaped 16x16 box outlines differing only in color:
- item_input  green  (#3EE83E)
- item_output orange (#F09020)
- fluid_output blue  (#3AA0F0)

Output: src/main/resources/assets/gregsteamexpansion/textures/block/coke_oven_hatch/mode_<mode>.png
"""
from PIL import Image
import os

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                       "assets", "gregsteamexpansion", "textures", "block", "coke_oven_hatch")

MODES = {
    "item_input": (62, 232, 62),
    "item_output": (240, 144, 32),
    "fluid_output": (58, 160, 240),
}

# 方框几何: 3px 外边框、1px 间隙, 与上游 overlay hatch 的居中方框视觉一致。
BOX = (3, 3, 13, 13)  # inclusive x0, y0, x1, y1
BORDER = 1


def draw_box(size=16):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    return img, px


def render(color):
    img, px = draw_box()
    x0, y0, x1, y1 = BOX
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            on_border = (
                x <= x0 + BORDER - 1 or x >= x1 - BORDER + 1
                or y <= y0 + BORDER - 1 or y >= y1 - BORDER + 1
            )
            if on_border:
                px[x, y] = color + (255,)
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, color in MODES.items():
        path = os.path.join(OUT_DIR, f"mode_{name}.png")
        render(color).save(path)
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
