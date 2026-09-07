"""Generates the Steam Centrifuge (small) and Large Steam Centrifuge
controller front overlays (GT decal style, same conventions as
tools/gen_mixer_textures.py: transparent background over the cased hull,
near-black outlines, shared bronze ramp, amber indicator).

Motif (steam-centrifuges.md 议题 9): a spinning centrifuge rotor — a dark
bowl with a three-swirl rotor disc rotating 90° per active frame, feed and
draw-off ports above. The LARGE controller adds two vertical bronze pipe
columns flanking the rotor (双管道柱语义) and a taller tower housing. Idle
keeps the rotor dark. Emissive masks cover only the lit rotor swirl tip and
the amber indicator — never the hull.
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


def ports(frame, active):
    """Feed port above the rotor and draw-off stubs at the rim."""
    frame.fill(7, 8, 1, 1, 's')
    frame.put(7, 2, 's' if active else 'k')
    frame.put(8, 2, 's' if active else 'k')


def rotor(frame, offset, active):
    """Three-swirl rotor disc: blades rotate 90° per frame; the leading
    blade tip carries the highlight."""
    cx, cy = 7, 9
    for i in range(3):
        quadrant = (i * 4 + offset * 2) % 8
        dx, dy = [(3, 0), (2, 2), (0, 3), (-2, 2),
                  (-3, 0), (-2, -2), (0, -3), (2, -2)][quadrant]
        for step in range(1, 4):
            x = cx + dx * step // 3
            y = cy + dy * step // 3
            lit = active and i == 0 and step == 3
            frame.put(x, y, 'H' if lit else ('B' if active else 'b'))
    frame.put(cx, cy, 'k')


def bowl(frame, active):
    """Centrifuge bowl: dark rim, bronze walls, dark interior."""
    frame.fill(1, 14, 2, 2, 'k')
    for y in range(3, 13):
        frame.put(1, y, 'k')
        frame.put(2, y, 'b' if active else '#')
        frame.put(13, y, 'b' if active else '#')
        frame.put(14, y, 'k')
    frame.fill(2, 13, 13, 13, 'k')
    frame.fill(3, 12, 3, 12, 'k')


def small_housing(frame, active):
    """Small controller: plain bowl on the cased hull."""
    bowl(frame, active)
    ports(frame, active)


def large_housing(frame, active):
    """Large controller: taller tower with two vertical pipe columns
    flanking the bowl (双管道柱语义)."""
    for y in range(0, 13):
        frame.put(0, y, 'k')
        frame.put(1, y, 'b' if active else '#')
        frame.put(14, y, 'b' if active else '#')
        frame.put(15, y, 'k')
    frame.put(3, 0, 's')
    frame.put(3, 1, 's')
    frame.put(12, 0, 's')
    frame.put(12, 1, 's')
    for y in range(2, 13):
        frame.put(3, y, 's' if active else 'k')
        frame.put(12, y, 's' if active else 'k')
    bowl(frame, active)
    ports(frame, active)


def indicator(frame, active):
    frame.put(7, 14, 'a' if active else 'd')
    frame.put(8, 14, 'a' if active else 'd')


def build(housing, rotor_offset, active):
    g = Frame()
    housing(g, active)
    rotor(g, rotor_offset, active)
    indicator(g, active)
    return g


def emissive(rotor_offset):
    g = Frame()
    cx, cy = 7, 9
    quadrant = (rotor_offset * 2) % 8
    dx, dy = [(3, 0), (2, 2), (0, 3), (-2, 2),
              (-3, 0), (-2, -2), (0, -3), (2, -2)][quadrant]
    g.put(cx + dx, cy + dy, 'H')
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


def emit(root, name, housing):
    out_dir = os.path.join(root, name)
    os.makedirs(out_dir, exist_ok=True)
    idle_img = build(housing, 0, active=False)
    print('--- %s idle ---' % name)
    idle_img.show()
    idle_img.image().save(os.path.join(out_dir, 'overlay_front.png'))
    frames = [build(housing, i, active=True) for i in range(4)]
    emFrames = [emissive(i) for i in range(4)]
    write_strip(os.path.join(out_dir, 'overlay_front_active.png'), frames)
    write_strip(os.path.join(out_dir, 'overlay_front_active_emissive.png'), emFrames)
    print('%s: idle + 4 active/emissive frames, frametime 2' % name)


def main():
    root = os.path.normpath(os.path.join(
        os.path.dirname(__file__), '..',
        'src/main/resources/assets/gregsteamexpansion/textures/block/multiblock'))
    emit(root, 'steam_centrifuge', small_housing)
    emit(root, 'large_steam_centrifuge', large_housing)
    print('saved under', root)


if __name__ == '__main__':
    main()
