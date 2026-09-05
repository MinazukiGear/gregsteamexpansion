"""Generates the steam crusher controller front overlays (GT decal style).

Following the shared GT conventions established in tools/gen_hatch_textures.py:
transparent backgrounds over the bricked bronze hull, near-black outlines and
the shared bronze ramp. Both controllers are workable machines driven by the
RECIPE_LOGIC_STATUS render property, so the active texture is an animated
vertical strip with an .mcmeta clock (steam-crushers.md 控制器模型):

- 蒸汽粉碎机: two counter-rotating coarse gear wheels with a narrow feed gap
  and a small amber indicator. 4 frames × 2 ticks.
- 大型蒸汽粉碎机: dark steel reinforcement frame, heavy central drill shaft
  and a pressure gauge. 8 frames × 2 ticks (slower rhythm).

The emissive masks cover only the amber indicator, the feed-gap / shaft heat
highlights and the gauge needle — never the hull or the frames.
"""
import math
import os

from PIL import Image

DARK_BRONZE = (62, 42, 18, 255)
MID_BRONZE = (124, 86, 34, 255)
LIGHT_BRONZE = (166, 122, 56, 255)
HIGHLIGHT = (222, 184, 110, 255)
BLACK = (24, 24, 28, 255)
DARK_STEEL = (56, 56, 60, 255)
STEEL = (96, 96, 102, 255)
IRON = (128, 128, 136, 255)
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
    'S': STEEL,
    'g': IRON,
    'a': AMBER,
    'd': AMBER_DIM,
    '.': TRANSPARENT,
}


class Frame:
    """16x16 frame with free (non-mirrored) painting; hatches bake the wheel
    animation, so vertical-axis symmetry is not asserted here."""

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


def gear(frame, cx, cy, offset, active, r_out=2.9, r_body=2.2, teeth=4):
    """A coarse grinding wheel: dark rim, bronze body with dark spoke gaps,
    dark hub, and protruding teeth that interlock across the feed gap.

    `offset` (teeth steps per frame) rotates the highlight; the two wheels get
    opposite offsets so they counter-rotate (反向咬合). Idle keeps the teeth and
    body dark; active frames light one body quadrant plus its tooth."""
    for y in range(int(cy - r_out) - 1, int(cy + r_out) + 2):
        for x in range(int(cx - r_out) - 1, int(cx + r_out) + 2):
            dx, dy = x - cx, y - cy
            dist = math.hypot(dx, dy)
            if dist > r_out:
                continue
            angle = math.degrees(math.atan2(dy, dx)) % 360
            if dist > r_body:
                frame.put(x, y, 'k')  # rim band
                continue
            if dist <= 0.8:
                frame.put(x, y, 'k')  # hub
                continue
            on_gap = min(angle % 90, 90 - angle % 90) < 13
            frame.put(x, y, 'k' if on_gap else 'b')  # spoke gaps between quadrants
    # protruding teeth on the rim at N/E/S/W (they mesh across the feed gap)
    for tooth in range(teeth):
        ang = math.radians(tooth * (360 // teeth) - 90)
        for radius in (r_out + 0.2, r_out + 0.7):
            x = int(round(cx + radius * math.cos(ang)))
            y = int(round(cy + radius * math.sin(ang)))
            if 0 <= x < 16 and 0 <= y < 16:
                lit = active and tooth == offset % teeth
                frame.put(x, y, 'B' if lit else 'k')
    if active:
        # lit body quadrant rotating with the teeth
        quadrant = offset % teeth
        for y in range(int(cy - r_body) + 1, int(cy + r_body) + 1):
            for x in range(int(cx - r_body) + 1, int(cx + r_body) + 1):
                dx, dy = x - cx, y - cy
                if math.hypot(dx, dy) > r_body:
                    continue
                a = math.degrees(math.atan2(dy, dx)) % 360
                if a // 90 == quadrant:
                    frame.put(x, y, 'B')


def indicator(frame, active):
    """Small amber run indicator below the wheels/gauge (琥珀色运行指示灯)."""
    frame.put(7, 13, 'a' if active else 'd')
    frame.put(8, 13, 'a' if active else 'd')


# ---------------------------------------------------------------- small
def small_idle():
    g = Frame()
    gear(g, 4.5, 7.5, 0, active=False)
    gear(g, 10.5, 7.5, 0, active=False)
    indicator(g, active=False)
    return g


def small_active(frame_index):
    g = Frame()
    gear(g, 4.5, 7.5, frame_index % 4, active=True)
    gear(g, 10.5, 7.5, (-frame_index) % 4, active=True)
    # narrow feed gap heat highlight between the wheels
    g.put(7, 7, 'H')
    g.put(8, 8, 'H')
    indicator(g, active=True)
    return g


# ---------------------------------------------------------------- large
def large_frame_base(g):
    """Dark steel reinforcement frame with corner bolts, drill shaft, gauge."""
    # rectangular reinforcement frame (深色钢制矩形加固框)
    g.fill(2, 13, 2, 2, 's')
    g.fill(2, 13, 13, 13, 's')
    for y in range(2, 14):
        g.put(2, y, 's')
        g.put(13, y, 's')
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        g.put(x, y, 'g')
    # second frame line for the reinforced look
    for x in range(3, 13):
        g.put(x, 3, 'k')
        g.put(x, 12, 'k')
    for y in range(3, 13):
        g.put(3, y, 'k')
        g.put(12, y, 'k')
    # heavy central drill shaft (中央粗重竖直钻轴)
    for y in range(4, 12):
        g.put(7, y, 's')
        g.put(8, y, 'S')
        # segment notches
        if y % 3 == 1:
            g.put(7, y, 'k')
            g.put(8, y, 'k')
    # drill bit tip
    g.put(7, 12, 'b')
    g.put(8, 12, 'b')
    # pressure gauge (压力表), top-left inside the frame
    for x, y in ((4, 4), (5, 4), (6, 4), (4, 5), (6, 5), (4, 6), (5, 6), (6, 6)):
        g.put(x, y, 'k')
    g.put(5, 5, 's')


def large_idle():
    g = Frame()
    large_frame_base(g)
    indicator(g, active=False)
    return g


def large_active(frame_index):
    g = Frame()
    large_frame_base(g)
    # drive highlight travelling down the shaft (one segment per frame)
    positions = [(4, 5), (5, 6), (6, 7), (7, 8), (8, 9), (9, 10), (10, 11), (11, 12)]
    hy = positions[frame_index % len(positions)]
    g.put(7, hy[0], 'B')
    g.put(8, hy[0], 'H')
    if hy[1] <= 11:
        g.put(7, hy[1], 'b')
        g.put(8, hy[1], 'B')
    # gauge needle swings slowly with the cycle (needle sits on the ring)
    needle = [(5, 4), (6, 4), (6, 5), (6, 6), (5, 6), (4, 6), (4, 5), (4, 5)][frame_index % 8]
    g.put(needle[0], needle[1], 'H')
    g.put(5, 5, 'B')
    indicator(g, active=True)
    return g


def small_emissive(frame_index):
    """Amber indicator + the narrow feed-gap heat highlight only."""
    g = Frame()
    g.put(7, 13, 'a')
    g.put(8, 13, 'a')
    g.put(7, 7, 'H')
    g.put(8, 8, 'H')
    return g


def large_emissive(frame_index):
    """Amber indicator, gauge needle and a few shaft drive highlights."""
    g = Frame()
    g.put(7, 13, 'a')
    g.put(8, 13, 'a')
    needle = [(5, 4), (6, 4), (6, 5), (6, 6), (5, 6), (4, 6), (4, 5), (4, 5)][frame_index % 8]
    g.put(needle[0], needle[1], 'H')
    positions = [(4, 5), (5, 6), (6, 7), (7, 8), (8, 9), (9, 10), (10, 11), (11, 12)]
    hy = positions[frame_index % len(positions)]
    if frame_index % 2 == 0:
        g.put(8, hy[0], 'H')
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
    for name, idle, active, emissive, frame_count in (
            ('steam_crusher', small_idle, small_active, small_emissive, 4),
            ('large_steam_crusher', large_idle, large_active, large_emissive, 8)):
        out_dir = os.path.join(root, name)
        os.makedirs(out_dir, exist_ok=True)
        idle_img = idle()
        print(f'--- {name} idle ---')
        idle_img.show()
        idle_img.image().save(os.path.join(out_dir, 'overlay_front.png'))
        frames = [active(i) for i in range(frame_count)]
        emFrames = [emissive(i) for i in range(frame_count)]
        write_strip(os.path.join(out_dir, 'overlay_front_active.png'), frames)
        write_strip(os.path.join(out_dir, 'overlay_front_active_emissive.png'), emFrames)
        print(f'{name}: idle + {frame_count} active/emissive frames, frametime 2')
    print('saved under', root)


if __name__ == '__main__':
    main()
