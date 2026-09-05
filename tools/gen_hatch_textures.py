"""Generates the four bronze steam-hatch front overlays in GTCEu decal style.

GTCEu part fronts are PARTIAL TRANSPARENT DECALS composited over the hull side
texture (the `sided/sided` machine template renders `overlay_front` on a cube
slightly larger than the hull): the upstream steam input hatch front is the
pump cover decal (39% opaque), the muffler hatch a circular fan decal, the
electric fluid hatches tiny 9% arrow decals. Full-face opaque fronts would hide
the bronze/steel hull and break the machine's look, so every texture here keeps
the background fully transparent and paints only the identifying motif:

- 蒸汽供给仓: vertical steam pipe with flanges, central valve handwheel,
  inward chevrons (向内汇聚).
- 蒸汽流体输入仓: pipe with two flange plates, inward chevrons.
- 蒸汽流体输出仓: pipe with one central flange pair, outward chevrons.
- 蒸汽进气室: large louver grille panel with central impeller, inward chevrons.

Dark near-black outlines guarantee contrast on both the bronze and the steel
hull; all textures are mirror-symmetric about the vertical axis by
construction (`Grid.put` mirrors every pixel) and the script asserts it.
"""
import math
import os

from PIL import Image

# Decal palette: hull-agnostic outlines plus the shared bronze ramp.
DARK_BRONZE = (62, 42, 18, 255)
MID_BRONZE = (124, 86, 34, 255)
LIGHT_BRONZE = (166, 122, 56, 255)
HIGHLIGHT = (222, 184, 110, 255)
BLACK = (24, 24, 28, 255)
IRON = (128, 128, 136, 255)
TRANSPARENT = (0, 0, 0, 0)

SYMBOLS = {
    '#': DARK_BRONZE,
    'b': MID_BRONZE,
    'B': LIGHT_BRONZE,
    'H': HIGHLIGHT,
    'k': BLACK,
    'g': IRON,
    '.': TRANSPARENT,
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
                img.putpixel((x, y), SYMBOLS[self.cells[y][x]])
        return img

    def show(self):
        for row in self.cells:
            print(''.join(row))


def pipe(g, y0, y1, flow=True):
    """Vertical steam pipe decal: dark walls, bronze bore, highlight ticks."""
    for y in range(y0, y1 + 1):
        g.put(6, y, 'k')
        g.put(7, y, 'b')
    if flow:
        for y in range(y0, y1 + 1):
            if y % 3 == y0 % 3:
                g.put(7, y, 'H')


def flange(g, y0, y1, bolts=True):
    """Flange plate crossing the pipe: dark plate, bronze edge, iron bolts."""
    g.fill(5, 7, y0, y1, 'k')
    g.fill(5, 7, y0 + 1, y1 - 1, 'b') if y1 > y0 + 1 else None
    for y in range(y0, y1 + 1):
        g.put(5, y, 'B')
    if bolts:
        for y in range(y0, y1 + 1):
            g.put(6, y, 'g')


def chevron(g, tip_x, tip_y, direction, sym='H', arm='b'):
    """One arrow head pointing in `direction` (+1 = toward +x / inward-left)."""
    g.put(tip_x, tip_y, sym)
    g.put(tip_x + direction, tip_y - 1, arm)
    g.put(tip_x + direction, tip_y + 1, arm)


def inward_chevrons(g):
    """»  « pair on both flanks: tips toward the center of the block."""
    # Left side: tip at x=2 pointing right; arms behind at x=1.
    g.put(2, 7, 'H')
    g.put(2, 8, 'H')
    g.put(1, 6, 'b')
    g.put(1, 9, 'b')


def outward_chevrons(g):
    """«  » pair on both flanks: tips at the frame, fanning outward."""
    g.put(1, 7, 'H')
    g.put(1, 8, 'H')
    g.put(2, 6, 'b')
    g.put(2, 9, 'b')


# ---------------------------------------------------------------- texture 1
def supply_hatch():
    """蒸汽供给仓: flanged steam pipe with a central valve handwheel."""
    g = Grid()
    pipe(g, 3, 12, flow=False)
    flange(g, 3, 3, bolts=True)
    flange(g, 12, 12, bolts=True)

    # Valve handwheel: dark rim circle, bronze ring, cross spokes, hub.
    cx, cy = 7.5, 7.5
    for y in range(4, 12):
        for x in range(4, 12):
            dx, dy = x - cx, y - cy
            dist = math.hypot(dx, dy)
            if 3.2 <= dist <= 3.8:
                g.put(x, y, 'k')
            elif 2.2 <= dist <= 3.0:
                g.put(x, y, 'b')
            elif dist < 1.2:
                g.put(x, y, 'H')
            elif (abs(dx) < 0.5 or abs(dy) < 0.5) and dist < 2.2:
                g.put(x, y, 'b')

    inward_chevrons(g)
    return g


# ---------------------------------------------------------------- texture 2
def fluid_input_hatch():
    """蒸汽流体输入仓: pipe + double flange plates, chevrons converging in."""
    g = Grid()
    pipe(g, 3, 12, flow=True)
    flange(g, 4, 5, bolts=True)
    flange(g, 10, 11, bolts=True)
    inward_chevrons(g)
    return g


# ---------------------------------------------------------------- texture 3
def fluid_output_hatch():
    """蒸汽流体输出仓: pipe + single central flange, chevrons fanning out."""
    g = Grid()
    pipe(g, 3, 12, flow=True)
    flange(g, 7, 8, bolts=True)
    outward_chevrons(g)
    return g


# ---------------------------------------------------------------- texture 4
def air_intake_hatch():
    """蒸汽进气室: large louver grille panel with a central impeller."""
    g = Grid()

    # Grille panel frame (10x10): dark outline with bronze corner bolts.
    g.fill(3, 7, 3, 3, 'k')
    g.fill(3, 7, 12, 12, 'k')
    for y in range(3, 13):
        g.put(3, y, 'k')
    g.put(3, 3, 'B')
    g.put(3, 12, 'B')

    # Louver slats: bronze body rows with dark shadow gaps.
    for y in range(4, 12):
        if y in (4, 7, 10):
            band = 'k'
        elif y in (5, 8):
            band = 'b'
        else:
            band = 'B'
        for x in range(4, 8):
            g.put(x, y, band)

    # Central impeller over the louvers: sits on a dark disc so the four
    # bronze blades and highlighted hub pop out of the grille.
    cx, cy = 7.5, 7.5
    for y in range(4, 12):
        for x in range(4, 12):
            dx, dy = x - cx, y - cy
            dist = math.hypot(dx, dy)
            if dist > 3.6:
                continue
            if 3.0 <= dist <= 3.6:
                g.put(x, y, 'k')
            elif dist < 3.0:
                g.put(x, y, 'k')
    for y in range(4, 12):
        for x in range(4, 12):
            dx, dy = x - cx, y - cy
            dist = math.hypot(dx, dy)
            if dist >= 3.0:
                continue
            if dist < 1.2:
                g.put(x, y, 'H')
                continue
            angle = math.degrees(math.atan2(dy, dx)) % 360
            blade = any(a - 34 <= angle <= a + 34 for a in (45, 135, 225, 315))
            g.put(x, y, 'b' if blade else '#')

    # Small inward air chevrons between the panel and the frame.
    inward_chevrons(g)
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
        img = g.image()
        transparent = sum(1 for y in range(16) for x in range(16)
                          if img.getpixel((x, y))[3] == 0)
        print(f'--- {name} ({100 * transparent // 256}% transparent) ---')
        g.show()
        img.save(os.path.join(out_dir, name + '.png'))
    print('saved to', out_dir)


if __name__ == '__main__':
    main()
