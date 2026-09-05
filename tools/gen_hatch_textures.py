"""Generates the four symmetric bronze steam-hatch front overlays.

Palette and frame language match the existing steam exhaust hatch overlay
(assets/gregsteamexpansion/textures/block/machine/part/steam_exhaust_hatch.png):
a dark bronze edge, a light bronze inner frame and a near-black interior so the
overlays stay readable on both the bronze and steel steam hulls.

Every texture is symmetric about the vertical center axis by construction:
`Grid.put` mirrors each painted pixel, so no hand-drawn grid can ever drift
asymmetric. The script also asserts the mirror property before saving.
"""
import math
import os

from PIL import Image

# Shared palette (sampled from the exhaust hatch overlay).
DARK_BRONZE = (62, 42, 18, 255)
MID_BRONZE = (124, 86, 34, 255)
LIGHT_BRONZE = (166, 122, 56, 255)
HIGHLIGHT = (222, 184, 110, 255)
BLACK = (24, 24, 28, 255)
DARK_GRAY = (38, 38, 42, 255)
IRON = (128, 128, 136, 255)
SHADOW = (44, 30, 13, 255)

SYMBOLS = {
    '#': DARK_BRONZE,
    'b': MID_BRONZE,
    'B': LIGHT_BRONZE,
    'H': HIGHLIGHT,
    'k': BLACK,
    'd': DARK_GRAY,
    'g': IRON,
    's': SHADOW,
}


class Grid:
    """16x16 symbol grid; each painted pixel is mirrored onto the right half."""

    def __init__(self):
        self.cells = [['.'] * 16 for _ in range(16)]

    def put(self, x, y, sym):
        assert 0 <= x < 16 and 0 <= y < 16, (x, y)
        self.cells[y][x] = sym
        self.cells[y][15 - x] = sym

    def fill(self, x0, x1, y0, y1, sym):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, sym)

    def image(self):
        img = Image.new('RGBA', (16, 16))
        for y in range(16):
            for x in range(16):
                sym = self.cells[y][x]
                assert sym != '.', (x, y)  # every pixel must be painted
                img.putpixel((x, y), SYMBOLS[sym])
        return img

    def show(self):
        for row in self.cells:
            print(''.join(row))


def frame(g):
    """Dark bronze edge + light bronze inner frame, highlighted corners."""
    g.fill(0, 7, 0, 0, '#')
    g.fill(0, 7, 15, 15, '#')
    # Rows 1/14: '#H' corners then bronze; rows 2/13: '#b' corners then light.
    g.put(0, 1, '#')
    g.put(1, 1, 'H')
    g.fill(2, 7, 1, 1, 'b')
    g.put(0, 14, '#')
    g.put(1, 14, 'H')
    g.fill(2, 7, 14, 14, 'b')
    g.put(0, 2, '#')
    g.put(1, 2, 'b')
    g.fill(2, 7, 2, 2, 'B')
    g.put(0, 13, '#')
    g.put(1, 13, 'b')
    g.fill(2, 7, 13, 13, 'B')
    for y in range(3, 13):
        g.put(0, y, '#')
        g.put(1, y, 'b')
        g.put(2, y, 'B')


def interior(g, sym='k'):
    g.fill(3, 7, 3, 12, sym)


def flange_band(g, y0, y1, bolt_rows):
    """Full-width dark flange plate crossed by the pipe, iron bolt dots."""
    g.fill(3, 7, y0, y1, 'd')
    for y in bolt_rows:
        g.put(3, y, 'g')
        g.put(4, y, 'g')
        g.put(6, y, 'g')


def pipe_column(g, y0, y1, flow='H'):
    """Vertical pipe: mid-bronze walls, light bore, highlight flow ticks."""
    for y in range(y0, y1 + 1):
        g.put(6, y, 'b')
        g.put(7, y, 'B')
    if flow:
        for y in range(y0, y1 + 1):
            if y % 3 == y0 % 3:
                g.put(7, y, flow)


def chevron(g, tip_x, tip_y, dx, sym='H', arm='B'):
    """One arrow head: tip pixel plus two arms stepping back by dx."""
    g.put(tip_x, tip_y, sym)
    g.put(tip_x + dx, tip_y - 1, arm)
    g.put(tip_x + dx, tip_y + 1, arm)


def valve_wheel(g):
    """Crisp 4x4 handwheel box on the pipe: bronze rim, highlighted hub."""
    # Rim of the wheel (6x6 box outline reads better than a tiny circle).
    for x in range(5, 11):
        g.put(x, 5, 'b')
        g.put(x, 10, 'b')
    for y in range(6, 10):
        g.put(5, y, 'b')
        g.put(10, y, 'b')
    # Corner nuts and hub highlight.
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        g.put(x, y, 'B')
    g.put(7, 7, 'H')
    g.put(8, 7, 'B')
    g.put(7, 8, 'B')
    g.put(8, 8, 'H')
    # Spokes connect the rim to the hub.
    g.put(7, 6, 'b')
    g.put(8, 6, 'b')
    g.put(7, 9, 'b')
    g.put(8, 9, 'b')


# ---------------------------------------------------------------- texture 1
def supply_hatch():
    """蒸汽供给仓: flanged steam pipe with a valve handwheel, inward arrows."""
    g = Grid()
    frame(g)
    interior(g)

    pipe_column(g, 3, 12, flow=None)
    flange_band(g, 3, 3, (3,))
    flange_band(g, 12, 12, (12,))

    # Clear the pipe behind the wheel so rim and spokes stay readable.
    g.fill(5, 10, 5, 10, 'k')
    valve_wheel(g)

    # Solid inward triangles beside the wheel, pointing at it.
    for dx, sym in ((2, 'b'), (1, 'B')):
        g.put(dx, 7, sym)
        g.put(dx, 8, sym)
    g.put(3, 7, 'H')
    g.put(3, 8, 'H')
    return g


# ---------------------------------------------------------------- texture 2
def fluid_input_hatch():
    """蒸汽流体输入仓: pipe + double flange, chevrons converging inward."""
    g = Grid()
    frame(g)
    interior(g)

    pipe_column(g, 3, 12, flow='H')
    flange_band(g, 4, 5, (4, 5))
    flange_band(g, 10, 11, (10, 11))

    # Inward chevrons in the free middle band, tips toward the pipe center.
    g.put(4, 6, 'B')
    g.put(5, 7, 'H')
    g.put(5, 8, 'H')
    g.put(4, 9, 'B')
    return g


# ---------------------------------------------------------------- texture 3
def fluid_output_hatch():
    """蒸汽流体输出仓: pipe + single central flange, chevrons fanning outward."""
    g = Grid()
    frame(g)
    interior(g)

    pipe_column(g, 3, 12, flow='H')

    # One broad central flange pair (distinct silhouette vs the input double
    # plates): dark plate with bolt dots, the pipe bore stays visible.
    g.fill(3, 5, 7, 8, 'd')
    g.put(3, 7, 'g')
    g.put(3, 8, 'g')
    g.put(5, 7, 'g')
    g.put(5, 8, 'g')

    # Outward chevrons: tips at the frame edge, arms toward the pipe.
    chevron(g, 3, 4, +1, sym='B', arm='H')
    chevron(g, 3, 11, +1, sym='B', arm='H')
    g.put(3, 5, 'H')
    g.put(3, 10, 'H')
    return g


# ---------------------------------------------------------------- texture 4
def air_intake_hatch():
    """蒸汽进气室: louver grille bands above/below a central bronze impeller."""
    g = Grid()
    frame(g)
    interior(g)

    def louver(y, slat, rung):
        for x in range(3, 8):
            g.put(x, y, slat)
        g.put(4, y, rung)
        g.put(6, y, rung)

    # Large-area louver field: shadow/dark slat pairs with bronze rungs.
    louver(3, 's', 'b')
    louver(4, 'd', 'k')
    louver(5, 'k', 'b')
    louver(10, 'k', 'b')
    louver(11, 'd', 'k')
    louver(12, 's', 'b')
    # Vertical louver rungs on the side margins keep the grille readable.
    for y in range(5, 11):
        g.put(3, y, 'd' if y % 2 == 0 else 's')

    # Central impeller: a small retaining ring with an X-shaped bronze blade
    # cross and a highlighted hub — reads as a fan at 16 px.
    cx, cy = 7.5, 7.5
    for y in range(4, 12):
        for x in range(4, 12):
            dx, dy = x - cx, y - cy
            dist = math.hypot(dx, dy)
            if dist > 3.6:
                continue
            if 3.1 <= dist <= 3.6:
                g.put(x, y, 'B')
                continue
            if dist < 1.2:
                g.put(x, y, 'H')
                continue
            on_blade = abs(abs(dx) - abs(dy)) < 0.8
            g.put(x, y, 'b' if on_blade else 'k')

    # Small inward air chevrons beside the impeller ring.
    g.put(3, 7, 'H')
    g.put(3, 8, 'H')
    return g


def main():
    out_dir = os.path.normpath(os.path.join(
        os.path.dirname(__file__), '..',
        'src/main/resources/assets/gregsteamexpansion/textures/block/machine/part'))
    os.makedirs(out_dir, exist_ok=True)
    for name, builder in (
            ('steam_supply_hatch', supply_hatch),
            ('steam_fluid_input_hatch', fluid_input_hatch),
            ('steam_fluid_output_hatch', fluid_output_hatch),
            ('steam_air_intake_hatch', air_intake_hatch)):
        g = builder()
        for y in range(16):
            for x in range(8):
                assert g.cells[y][x] == g.cells[y][15 - x], (name, x, y)
        print(f'--- {name} ---')
        g.show()
        g.image().save(os.path.join(out_dir, name + '.png'))
    print('saved to', out_dir)


if __name__ == '__main__':
    main()
