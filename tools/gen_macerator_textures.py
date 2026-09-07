"""Generates the Large Steam Macerator controller front overlays
(GT decal style, same conventions as tools/gen_thermal_centrifuge_textures.py:
transparent background over the cased hull, near-black outlines, shared
bronze ramp, amber indicator).

Motif (large-steam-macerator.md 议题 9): a circular grinding chamber whose
toothed disc rotor visibly spins across 4 active frames, above a grind-dust
tray with twinkling dust specks (the sphere's six-armed grinding cross).
Idle keeps the disc dark and the tray dim. The emissive masks cover only the
lit rotor spoke, the dust specks and the amber indicator — never the hull.
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


def chamber(frame, active):
    """Grinding chamber: dark rim, bronze body ring with a toothed inner
    edge, dark hub — reads as a closed disc grinder."""
    cx, cy = 7.5, 6.5
    for y in range(0, 13):
        for x in range(16):
            dx, dy = x - cx, y - cy
            dist = (dx * dx + dy * dy) ** 0.5
            if 5.6 <= dist <= 6.2:
                frame.put(x, y, 'k')          # rim band
            elif 4.6 <= dist < 5.6:
                frame.put(x, y, 's' if (int(dist * 3) + x) % 4 == 0
                          else ('b' if active else '#'))
    for y in range(5, 9):
        for x in range(6, 10):
            dx, dy = x - cx, y - cy
            if dx * dx + dy * dy <= 2.4:
                frame.put(x, y, 'k')          # hub


def disc(frame, offset, active):
    """Toothed disc rotor: four radial spokes; the lit spoke advances one
    quadrant per active frame (rotating grind read)."""
    cx, cy = 7.5, 6.5
    for quadrant in range(4):
        for step in range(2, 5):
            if quadrant % 2 == 0:   # up / down spokes
                x = int(cx)
                y = int(cy + (step if quadrant == 0 else -step))
            else:                   # left / right spokes
                y = int(cy)
                x = int(cx + (step if quadrant == 2 else -step))
            lit = active and quadrant == offset % 4
            if lit:
                frame.put(x, y, 'H')
            elif step == 2:
                frame.put(x, y, 'B' if active else 'b')
            else:
                frame.put(x, y, 'b' if active else 'k')


def tray(frame, active, phase):
    """Grind-dust tray across the lower decal with twinkling specks
    (六向碾磨十字的粉尘语义)."""
    frame.fill(2, 13, 11, 11, 'k')
    for x in range(2, 14):
        frame.put(x, 12, 's')
    # dust specks on the tray rim: two phases alternate
    for x in range(3, 13, 2):
        lit = active and ((x + phase) // 2) % 2 == 0
        frame.put(x, 10, 'H' if lit else 'd')
    # tray posts
    frame.put(1, 11, 'k')
    frame.put(14, 11, 'k')
    frame.put(1, 12, 'k')
    frame.put(14, 12, 'k')


def indicator(frame, active):
    frame.put(7, 14, 'a' if active else 'd')
    frame.put(8, 14, 'a' if active else 'd')


def idle():
    g = Frame()
    chamber(g, active=False)
    disc(g, 0, active=False)
    tray(g, active=False, phase=0)
    indicator(g, active=False)
    return g


def active(frame_index):
    g = Frame()
    chamber(g, active=True)
    disc(g, frame_index % 4, active=True)
    tray(g, active=True, phase=frame_index)
    indicator(g, active=True)
    return g


def emissive(frame_index):
    g = Frame()
    cx, cy = 7.5, 6.5
    quadrant = frame_index % 4
    if quadrant % 2 == 0:
        x = int(cx)
        y = int(cy + (4 if quadrant == 0 else -4))
    else:
        y = int(cy)
        x = int(cx + (4 if quadrant == 2 else -4))
    g.put(x, y, 'H')
    for x in range(3, 13, 2):
        if ((x + frame_index) // 2) % 2 == 0:
            g.put(x, 10, 'H')
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
    out_dir = os.path.join(root, 'large_steam_macerator')
    os.makedirs(out_dir, exist_ok=True)
    idle_img = idle()
    print('--- large_steam_macerator idle ---')
    idle_img.show()
    idle_img.image().save(os.path.join(out_dir, 'overlay_front.png'))
    frames = [active(i) for i in range(4)]
    emFrames = [emissive(i) for i in range(4)]
    write_strip(os.path.join(out_dir, 'overlay_front_active.png'), frames)
    write_strip(os.path.join(out_dir, 'overlay_front_active_emissive.png'), emFrames)
    print('large_steam_macerator: idle + 4 active/emissive frames, frametime 2')
    print('saved under', root)


if __name__ == '__main__':
    main()
