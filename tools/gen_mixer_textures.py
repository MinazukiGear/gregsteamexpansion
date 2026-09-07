"""Generates the Large Steam Mixer controller front overlays
(GT decal style, same conventions as tools/gen_thermal_centrifuge_textures.py:
transparent background over the cased hull, near-black outlines, shared
bronze ramp, amber indicator).

Motif (large-steam-mixer.md 议题 9): an open mixing vat — dark rim walls, a
central stirring shaft and a two-blade impeller that visibly rotates across 4
active frames, with twinkle foam specks on the liquid surface (中轴立柱 + 叶轮
十字语义). Idle keeps the impeller dark and the foam dim. The emissive masks
cover only the lit impeller arm, the foam specks and the amber indicator —
never the hull.
"""
import os

from PIL import Image

DARK_BRONZE = (62, 42, 18, 255)
MID_BRONZE = (124, 86, 34, 255)
LIGHT_BRONZE = (166, 122, 56, 255)
HIGHLIGHT = (222, 184, 110, 255)
BLACK = (24, 24, 28, 255)
DARK_STEEL = (56, 56, 60, 255)
AMBER = (255, 170, 0, 255)
AMBER_DIM = (170, 112, 0, 255)
TRANSPARENT = (0, 0, 0, 0)

SYMBOLS = {
    '#': DARK_BRONZE,
    'b': MID_BRONZE,
    'B': LIGHT_BRONZE,
    'H': HIGHLIGHT,
    'k': BLACK,
    's': DARK_STEEL,
    'a': AMBER,
    'd': AMBER_DIM,
    '.': TRANSPARENT,
}


class Frame:
    def __init__(self):
        self.cells = [['.'] * 16 for _ in range(16)]

    def put(self, x, y, sym):
        assert 0 <= x < 16 and 0 <= y < 16, (x, y)
        self.cells[y][x] = sym

    def fill(self, x0, x1, y0, y1, sym):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, sym)

    def image(self):
        img = Image.new('RGBA', (16, 16))
        for y in range(16):
            for x in range(16):
                img.putpixel((x, y), SYMBOLS[self.cells[y][x]])
        return img

    def show(self):
        for row in self.cells:
            print(''.join(row))


def vat(frame, active):
    """Open mixing vat: dark rim walls, bronze inner shell, liquid surface
    line with a highlight, dark bottom band."""
    frame.fill(1, 14, 2, 2, 'k')            # top rim
    for y in range(3, 12):
        frame.put(1, y, 'k')
        frame.put(2, y, 'b' if active else '#')
        frame.put(13, y, 'b' if active else '#')
        frame.put(14, y, 'k')
    frame.fill(2, 13, 11, 11, 'k')          # bottom band
    frame.fill(3, 12, 3, 3, 's')            # liquid surface
    frame.put(4, 3, 'H' if active else 's')  # surface highlight
    frame.put(11, 3, 'H' if active else 's')
    for x in range(3, 13):
        for y in range(4, 11):
            frame.put(x, y, 's' if (x * 3 + y) % 7 == 0 else 'k')


def shaft(frame, active):
    """Central stirring shaft from the rim down to the impeller (中轴立柱)."""
    for y in range(2, 8):
        frame.put(7, y, 'k')
        frame.put(8, y, 's' if active else 'k')


def impeller(frame, offset, active):
    """Two-blade impeller on the shaft: the blades rotate 90° per frame; the
    leading blade carries the highlight."""
    cx = 7
    cy = 8
    for i in range(2):
        quadrant = (i * 2 + offset) % 4
        for step in range(1, 4):
            if quadrant % 2 == 0:   # horizontal arms
                y = cy
                x = cx + (step if quadrant == 2 else -step)
            else:                   # vertical arms
                x = cx
                y = cy + (step if quadrant == 1 else -step)
            lit = active and i == 0 and step == 3
            if lit:
                frame.put(x, y, 'H')
            else:
                frame.put(x, y, 'B' if active else 'b')


def foam(frame, active, phase):
    """Twinkling foam specks on the liquid surface."""
    for x in (4, 6, 10, 12):
        lit = active and ((x + phase) // 2) % 2 == 0
        frame.put(x, 3, 'H' if lit else 'd')


def indicator(frame, active):
    frame.put(7, 14, 'a' if active else 'd')
    frame.put(8, 14, 'a' if active else 'd')


def idle():
    g = Frame()
    vat(g, active=False)
    shaft(g, active=False)
    impeller(g, 0, active=False)
    foam(g, active=False, phase=0)
    indicator(g, active=False)
    return g


def active(frame_index):
    g = Frame()
    vat(g, active=True)
    shaft(g, active=True)
    impeller(g, frame_index % 4, active=True)
    foam(g, active=True, phase=frame_index)
    indicator(g, active=True)
    return g


def emissive(frame_index):
    g = Frame()
    cx, cy = 7, 8
    quadrant = frame_index % 4
    if quadrant % 2 == 0:
        y = cy
        x = cx + (3 if quadrant == 2 else -3)
    else:
        x = cx
        y = cy + (3 if quadrant == 1 else -3)
    g.put(x, y, 'H')
    for x in (4, 6, 10, 12):
        if ((x + frame_index) // 2) % 2 == 0:
            g.put(x, 3, 'H')
    g.put(7, 14, 'a')
    g.put(8, 14, 'a')
    return g


def write_strip(path, frames):
    strip = Image.new('RGBA', (16, 16 * len(frames)))
    for i, frame in enumerate(frames):
        strip.paste(frame.image(), (0, i * 16))
    strip.save(path)
    with open(path + '.mcmeta', 'w', encoding='utf-8') as f:
        f.write('{\n  "animation": {\n    "frametime": 2\n  }\n}\n')


def main():
    root = os.path.normpath(os.path.join(
        os.path.dirname(__file__), '..',
        'src/main/resources/assets/gregsteamexpansion/textures/block/multiblock'))
    out_dir = os.path.join(root, 'large_steam_mixer')
    os.makedirs(out_dir, exist_ok=True)
    idle_img = idle()
    print('--- large_steam_mixer idle ---')
    idle_img.show()
    idle_img.image().save(os.path.join(out_dir, 'overlay_front.png'))
    frames = [active(i) for i in range(4)]
    emFrames = [emissive(i) for i in range(4)]
    write_strip(os.path.join(out_dir, 'overlay_front_active.png'), frames)
    write_strip(os.path.join(out_dir, 'overlay_front_active_emissive.png'), emFrames)
    print('large_steam_mixer: idle + 4 active/emissive frames, frametime 2')
    print('saved under', root)


if __name__ == '__main__':
    main()
