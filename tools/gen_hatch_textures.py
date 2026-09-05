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

# GT fire ramp, sampled from the upstream steam furnace front overlay.
FIRE_DEEP = (255, 106, 0, 255)
FIRE_MID = (255, 136, 0, 255)
FIRE_BRIGHT = (255, 170, 0, 255)

SYMBOLS = {
    '#': DARK_BRONZE,
    'b': MID_BRONZE,
    'B': LIGHT_BRONZE,
    'H': HIGHLIGHT,
    'k': BLACK,
    'g': IRON,
    'o': FIRE_DEEP,
    'f': FIRE_MID,
    'y': FIRE_BRIGHT,
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


# ---------------------------------------------------------------- texture 5
def exhaust_hatch():
    """蒸汽排气仓: static hot-steam vent grille in the GT steam-vent language,
    with outward chevrons marking the exhaust direction."""
    g = Grid()

    # Vent panel (8x8): dark frame, bronze cross bars, dark slits.
    g.fill(4, 7, 4, 11, '.')
    for x, y, sym in (
            (4, 4, 'k'), (5, 4, 'k'), (6, 4, '#'), (7, 4, '#'),
            (4, 5, 'k'), (5, 5, 'k'), (6, 5, 'b'), (7, 5, 'k'),
            (4, 6, '#'), (5, 6, 'b'), (6, 6, 'b'), (7, 6, 'B'),
            (4, 7, '#'), (5, 7, 'k'), (6, 7, 'B'), (7, 7, 'k'),
            (4, 8, '#'), (5, 8, 'k'), (6, 8, 'B'), (7, 8, 'k'),
            (4, 9, '#'), (5, 9, 'b'), (6, 9, 'b'), (7, 9, 'b'),
            (4, 10, 'k'), (5, 10, 'k'), (6, 10, 'b'), (7, 10, 'k'),
            (4, 11, 'k'), (5, 11, 'k'), (6, 11, '#'), (7, 11, '#')):
        g.put(x, y, sym)

    outward_chevrons(g)
    return g


# ------------------------------------------------- furnace controller set
def furnace_front(active):
    """大型蓄热蒸汽熔炉 controller front, following the upstream steam furnace
    language: a dark smokebox on top and a barred furnace door below, with the
    hull side showing between and around them. When active, the GT fire ramp
    (255,106/136/170,0) glows inside both."""
    g = Grid()

    # Smokebox (rows 3-6).
    g.put(4, 3, '#')
    g.fill(5, 7, 3, 3, 'k')
    for y in (4, 5, 6):
        g.put(3, y, 'k')
        g.fill(4, 7, y, y, 'k')
    if active:
        # embers light up inside the smokebox
        g.put(6, 5, 'o')
        g.put(5, 6, 'b')
        g.put(6, 6, 'f')
        g.put(7, 6, 'b')

    # Furnace door (rows 9-13): dark frame, dark-bronze lintel, bars.
    g.put(3, 9, 'k')
    g.fill(4, 7, 9, 9, '#')
    for y in (10, 11, 12):
        if not active:
            g.put(4, y, 'k')
            g.put(5, y, 'b')
            g.put(6, y, 'k')
            g.put(7, y, 'b')
        else:
            g.put(4, y, 'b')
            g.put(5, y, {10: 'f', 11: 'o', 12: 'y'}[y])
            g.put(6, y, 'k')
            g.put(7, y, {10: 'o', 11: 'f', 12: 'y'}[y])
    g.put(3, 13, 'k')
    g.fill(4, 7, 13, 13, '#')
    return g


def furnace_front_emissive():
    """Idle emissive mask: fully transparent (no glow while idle)."""
    return Grid()


def furnace_front_active_emissive():
    """Active emissive mask: exactly the fire pixels of the active front —
    smokebox embers and the flames between the door bars — so only they
    render fullbright."""
    g = Grid()
    g.put(6, 5, 'o')
    g.put(6, 6, 'f')
    for y in (10, 11, 12):
        g.put(5, y, {10: 'f', 11: 'o', 12: 'y'}[y])
        g.put(7, y, {10: 'o', 11: 'f', 12: 'y'}[y])
    return g


OUTPUTS = (
    ('machine/part/steam_supply_hatch.png', supply_hatch),
    ('machine/part/steam_fluid_input_hatch.png', fluid_input_hatch),
    ('machine/part/steam_fluid_output_hatch.png', fluid_output_hatch),
    ('machine/part/steam_air_intake_hatch.png', air_intake_hatch),
    ('machine/part/steam_exhaust_hatch.png', exhaust_hatch),
    ('machine/large_heat_storage_steam_furnace/overlay_front.png',
     lambda: furnace_front(False)),
    ('machine/large_heat_storage_steam_furnace/overlay_front_active.png',
     lambda: furnace_front(True)),
    ('machine/large_heat_storage_steam_furnace/overlay_front_emissive.png',
     furnace_front_emissive),
    ('machine/large_heat_storage_steam_furnace/overlay_front_active_emissive.png',
     furnace_front_active_emissive),
)


def main():
    root = os.path.normpath(os.path.join(
        os.path.dirname(__file__), '..',
        'src/main/resources/assets/gregsteamexpansion/textures/block'))
    for rel, builder in OUTPUTS:
        g = builder()
        for y in range(16):
            for x in range(8):
                assert g.cells[y][x] == g.cells[y][15 - x], (rel, x, y)
        img = g.image()
        transparent = sum(1 for y in range(16) for x in range(16)
                          if img.getpixel((x, y))[3] == 0)
        print(f'--- {rel} ({100 * transparent // 256}% transparent) ---')
        g.show()
        out_path = os.path.join(root, rel)
        os.makedirs(os.path.dirname(out_path), exist_ok=True)
        img.save(out_path)
    print('saved under', root)


if __name__ == '__main__':
    main()
