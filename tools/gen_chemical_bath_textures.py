"""Generates the Steam Chemical Bath controller front overlays
(GT decal style, same conventions as tools/gen_mixer_textures.py: transparent
background over the cased hull, near-black outlines, shared bronze ramp,
amber indicator).

Motif (large-steam-chemical-bath.md 议题 9): an immersion bath tank — dark
bronze rim walls, a bath liquid body with a shimmering fill line, glass
observation strips on the front (配方玻璃十字语义) and bubbles rising through
the liquid across 4 active frames (浸洗腔气泡语义). Idle keeps the liquid
dark and bubble-free. The emissive masks cover only the rising bubbles, the
surface shimmer and the amber indicator — never the hull.
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


def tank(frame, active):
    """Immersion bath tank: dark top rim, bronze walls, dark bottom band,
    bath liquid body with two glass observation strips on the front."""
    frame.fill(1, 14, 2, 2, 'k')            # top rim
    for y in range(3, 12):
        frame.put(1, y, 'k')
        frame.put(2, y, 'b' if active else '#')
        frame.put(13, y, 'b' if active else '#')
        frame.put(14, y, 'k')
    frame.fill(2, 13, 11, 11, 'k')          # bottom band
    frame.fill(3, 12, 4, 10, 'k')           # bath liquid body
    for y in range(4, 11):                  # glass observation strips
        frame.put(5, y, 's')
        frame.put(10, y, 's')


def surface(frame, active):
    """Bath liquid fill line with a shimmering highlight."""
    frame.fill(3, 12, 3, 3, 's')
    frame.put(4, 3, 'H' if active else 's')
    frame.put(11, 3, 'H' if active else 's')


def bubbles(frame, phase, active):
    """Three bubble columns rising through the bath (浸洗腔气泡)."""
    if not active:
        return
    for col, x in enumerate((6, 8, 11)):
        y = 9 - ((phase + col * 2) % 5)
        frame.put(x, y, 'H')


def indicator(frame, active):
    frame.put(7, 14, 'a' if active else 'd')
    frame.put(8, 14, 'a' if active else 'd')


def idle():
    g = Frame()
    tank(g, active=False)
    surface(g, active=False)
    bubbles(g, 0, active=False)
    indicator(g, active=False)
    return g


def active(frame_index):
    g = Frame()
    tank(g, active=True)
    surface(g, active=True)
    bubbles(g, frame_index, active=True)
    indicator(g, active=True)
    return g


def emissive(frame_index):
    g = Frame()
    if True:
        for col, x in enumerate((6, 8, 11)):
            y = 9 - ((frame_index + col * 2) % 5)
            g.put(x, y, 'H')
    g.put(4, 3, 'H')
    g.put(11, 3, 'H')
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
    out_dir = os.path.join(root, 'steam_chemical_bath')
    os.makedirs(out_dir, exist_ok=True)
    idle_img = idle()
    print('--- steam_chemical_bath idle ---')
    idle_img.show()
    idle_img.image().save(os.path.join(out_dir, 'overlay_front.png'))
    frames = [active(i) for i in range(4)]
    emFrames = [emissive(i) for i in range(4)]
    write_strip(os.path.join(out_dir, 'overlay_front_active.png'), frames)
    write_strip(os.path.join(out_dir, 'overlay_front_active_emissive.png'), emFrames)
    print('steam_chemical_bath: idle + 4 active/emissive frames, frametime 2')
    print('saved under', root)


if __name__ == '__main__':
    main()
