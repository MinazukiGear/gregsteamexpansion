"""Generates placeholder 16x16 textures for the plain steam structure blocks
and the bronze component (docs/design/items-and-blocks.md 模型与纹理方向).

The output follows each entry's documented art direction (bronze plate shell,
dark riveted corner frames, cyan grinding ring, meshed gear pair, wire slots,
twin rotor silhouettes) so gameplay can ship before hand-drawn art replaces
these files 1:1. Pure stdlib PNG writer, deterministic output.

Usage: python tools/gen_structure_textures.py
"""

import math
import os
import random
import struct
import zlib

SIZE = 16

# Bronze family + support colors (GT-style bronze plating).
BRONZE_EDGE = (62, 42, 18)
BRONZE_DARK = (124, 86, 34)
BRONZE_BASE = (166, 122, 56)
BRONZE_LIGHT = (198, 154, 82)
BRONZE_RIVET = (222, 184, 110)
DARK_METAL = (52, 52, 56)
DARKER_METAL = (32, 32, 36)
STEEL_MID = (96, 96, 104)
CYAN_LIGHT = (196, 244, 240)
CYAN_BASE = (126, 214, 208)
CYAN_DARK = (66, 158, 154)
RUBBER_DARK = (44, 44, 50)
GRATE_DARK = (38, 38, 42)
GRATE_BAR = (128, 128, 136)
GRATE_SHADOW = (24, 24, 28)
RUBBER_BASE = (60, 60, 68)


def new_canvas():
    return [[BRONZE_BASE for _ in range(SIZE)] for _ in range(SIZE)]


def shade(color, delta):
    return tuple(max(0, min(255, c + delta)) for c in color)


def px(canvas, x, y, color):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        canvas[y][x] = color


def rect(canvas, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(canvas, x, y, color)


def hline(canvas, x0, x1, y, color):
    rect(canvas, x0, y, x1, y, color)


def vline(canvas, x, y0, y1, color):
    rect(canvas, x, y0, x, y1, color)


def riveted_frame(canvas):
    """Dark outer edge plus riveted corner plates shared by the block series."""
    hline(canvas, 0, SIZE - 1, 0, BRONZE_EDGE)
    hline(canvas, 0, SIZE - 1, SIZE - 1, BRONZE_EDGE)
    vline(canvas, 0, 0, SIZE - 1, BRONZE_EDGE)
    vline(canvas, SIZE - 1, 0, SIZE - 1, BRONZE_EDGE)
    for cx, cy in ((1, 1), (SIZE - 2, 1), (1, SIZE - 2), (SIZE - 2, SIZE - 2)):
        rect(canvas, cx - 1, cy - 1, cx + 1, cy + 1, BRONZE_DARK)
        px(canvas, cx, cy, BRONZE_RIVET)


def sym_noise(x, y, salt):
    """Deterministic noise mirrored on both axes: folded coordinates make the
    value identical for (x,y), (15-x,y), (x,15-y) and (15-x,15-y)."""
    mx, my = min(x, SIZE - 1 - x), min(y, SIZE - 1 - y)
    h = ((mx + 1) * 73856093) ^ ((my + 1) * 19349663) ^ ((salt + 1) * 83492791)
    return (h & 0xFFFF)


def plate_noise(canvas, rng, strength=14, salt=1):
    del rng  # kept for signature compatibility; noise is now deterministic
    for y in range(SIZE):
        for x in range(SIZE):
            n = sym_noise(x, y, salt)
            if n % 5 < 2:
                delta = (n % (strength + 1)) - strength // 2
                px(canvas, x, y, shade(canvas[y][x], delta))


def beveled_plates(canvas, rng):
    """Fill with large riveted plate sections like GT casings."""
    plate_noise(canvas, rng)
    for y in (4, 11):
        hline(canvas, 1, SIZE - 2, y, BRONZE_DARK)
        hline(canvas, 1, SIZE - 2, y + 1, BRONZE_LIGHT)
    riveted_frame(canvas)


def gear(canvas, cx, cy, body, tooth, hub):
    """Compact 4-tooth gear silhouette readable at 16x16."""
    rect(canvas, cx - 3, cy - 3, cx + 3, cy + 3, body)
    for dx, dy in ((-4, -4), (4, -4), (-4, 4), (4, 4)):
        rect(canvas, cx + dx - 1, cy + dy - 1, cx + dx + 1, cy + dy + 1, tooth)
    rect(canvas, cx - 1, cy - 1, cx + 1, cy + 1, hub)


def meshed_gear_pair(canvas, rng):
    """Two meshing bronze gears over a dark recess (assembly block side)."""
    rect(canvas, 2, 4, SIZE - 3, SIZE - 3, BRONZE_DARK)
    gear(canvas, 6, 8, BRONZE_BASE, BRONZE_LIGHT, STEEL_MID)
    gear(canvas, 10, 8, BRONZE_DARK, BRONZE_BASE, STEEL_MID)
    # Mesh point: teeth of both gears meet on the shared center line.
    rect(canvas, 7, 7, 8, 9, BRONZE_DARK)
    riveted_frame(canvas)


def grinding_ring(canvas, rng):
    """Cyan segmented grinding ring inside a dark bearing ring (grinding side)."""
    rect(canvas, 2, 2, SIZE - 3, SIZE - 3, BRONZE_DARK)
    rect(canvas, 4, 4, SIZE - 5, SIZE - 5, CYAN_BASE)
    # Segment cuts keep the ring from reading as a solid diamond block.
    # Cut rows are mirror-symmetric about y = 7.5 (4/11 and 7/8).
    for y in (4, 7, 8, 11):
        hline(canvas, 4, SIZE - 5, y, CYAN_DARK)
    rect(canvas, 7, 7, 8, 8, DARKER_METAL)
    # Mirrored accent columns on both sides of the hub.
    rect(canvas, 6, 6, 6, 9, CYAN_LIGHT)
    rect(canvas, 9, 6, 9, 9, CYAN_LIGHT)
    riveted_frame(canvas)


def rotor(canvas, cx, cy):
    """Four-blade bronze rotor silhouette around a dark shaft hub."""
    rect(canvas, cx - 2, cy - 2, cx + 2, cy + 2, DARKER_METAL)
    rect(canvas, cx - 1, cy - 1, cx + 1, cy + 1, DARK_METAL)
    vline(canvas, cx, cy - 5, cy - 3, BRONZE_LIGHT)
    vline(canvas, cx, cy + 3, cy + 5, BRONZE_LIGHT)
    hline(canvas, cx - 5, cx - 3, cy, BRONZE_BASE)
    hline(canvas, cx + 3, cx + 5, cy, BRONZE_BASE)
    px(canvas, cx - 1, cy - 4, BRONZE_DARK)
    px(canvas, cx + 1, cy - 4, BRONZE_DARK)
    px(canvas, cx - 1, cy + 4, BRONZE_DARK)
    px(canvas, cx + 1, cy + 4, BRONZE_DARK)


def twin_rotors(canvas, rng):
    """Vertical shaft with two rotor stages over a dark bearing housing."""
    rect(canvas, 3, 2, SIZE - 4, SIZE - 3, BRONZE_DARK)
    vline(canvas, 7, 3, SIZE - 4, BRONZE_DARK)
    vline(canvas, 8, 3, SIZE - 4, BRONZE_DARK)
    rotor(canvas, 8, 5)
    rotor(canvas, 8, 10)
    riveted_frame(canvas)


def mixer_top(canvas, rng):
    """Drive gear inside a bearing collar on a reinforced mount."""
    beveled_plates(canvas, rng)
    rect(canvas, 2, 2, SIZE - 3, SIZE - 3, BRONZE_DARK)
    # Bearing collar.
    rect(canvas, 4, 4, SIZE - 5, SIZE - 5, STEEL_MID)
    rect(canvas, 5, 5, SIZE - 6, SIZE - 6, DARKER_METAL)
    gear(canvas, 8, 8, BRONZE_BASE, BRONZE_LIGHT, STEEL_MID)
    riveted_frame(canvas)


def wire_slots(canvas, rng):
    """Fine positioning holes and parallel wire slots (circuit assembly side)."""
    rect(canvas, 2, 2, SIZE - 3, SIZE - 3, BRONZE_DARK)
    # Crossed conductor slots.
    hline(canvas, 3, SIZE - 4, 8, RUBBER_BASE)
    vline(canvas, 8, 3, SIZE - 4, RUBBER_BASE)
    # Fine positioning pin rows.
    for y in (5, 10):
        for x in (4, 7, 8, 11):
            px(canvas, x, y, BRONZE_LIGHT)
            px(canvas, x, y + 1, DARK_METAL)
    riveted_frame(canvas)


def assembly_top(canvas, rng):
    """Segmented assembly table with locating holes and corner clamps."""
    beveled_plates(canvas, rng)
    rect(canvas, 3, 3, SIZE - 4, SIZE - 4, BRONZE_DARK)
    rect(canvas, 4, 4, SIZE - 5, SIZE - 5, BRONZE_DARK)
    for cx, cy in ((5, 5), (10, 5), (5, 10), (10, 10)):
        px(canvas, cx, cy, DARKER_METAL)
        px(canvas, cx, cy, STEEL_MID if cy == 5 else BRONZE_RIVET)
    hline(canvas, 4, SIZE - 5, 7, DARK_METAL)
    hline(canvas, 4, SIZE - 5, 8, BRONZE_BASE)
    riveted_frame(canvas)


def reinforced_bottom(canvas, rng):
    """Bearing-frame base with fixed bolts along the edges."""
    plate_noise(canvas, rng)
    rect(canvas, 2, 2, SIZE - 3, SIZE - 3, BRONZE_DARK)
    rect(canvas, 4, 4, SIZE - 5, SIZE - 5, BRONZE_BASE)
    for cx, cy in ((3, 3), (SIZE - 4, 3), (3, SIZE - 4), (SIZE - 4, SIZE - 4)):
        px(canvas, cx, cy, BRONZE_RIVET)
        # shadow cast toward the block centre keeps the mirror intact
        px(canvas, cx + (1 if cx < 7 else -1), cy, BRONZE_DARK)
        px(canvas, cx, cy + (1 if cy < 7 else -1), BRONZE_DARK)
    for i in (5, 7, 8, 10):
        px(canvas, i, 2, STEEL_MID)
        px(canvas, i, SIZE - 3, STEEL_MID)
    riveted_frame(canvas)




def _panel_field(canvas, rim=BRONZE_DARK):
    """Bright symmetric bronze field used by all top/side feature faces."""
    for y in range(SIZE):
        for x in range(SIZE):
            band = max(abs(x - 7.5), abs(y - 7.5))
            delta = 10 if band < 4 else (4 if band < 5.5 else -6)
            px(canvas, x, y, shade(BRONZE_BASE, delta))
    for i in range(SIZE):
        px(canvas, i, 0, BRONZE_EDGE)
        px(canvas, i, SIZE - 1, BRONZE_EDGE)
        px(canvas, 0, i, BRONZE_EDGE)
        px(canvas, SIZE - 1, i, BRONZE_EDGE)
    for i in range(1, SIZE - 1):
        px(canvas, i, 1, rim)
        px(canvas, i, SIZE - 2, rim)
        px(canvas, 1, i, rim)
        px(canvas, SIZE - 2, i, rim)
    for cx, cy in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(canvas, cx, cy, BRONZE_RIVET)


def grind_face(canvas, rng):
    """Diamond grinding head: dark seat, cyan rhombus, bright core."""
    _panel_field(canvas)
    for y in range(3, 13):
        for x in range(3, 13):
            d = abs(x - 7.5) + abs(y - 7.5)
            if 3.5 <= d <= 4.5:
                px(canvas, x, y, GRATE_SHADOW)
            elif 1.5 <= d <= 3.5:
                px(canvas, x, y, CYAN_BASE)
    for x, y in ((7, 3), (8, 3), (7, 12), (8, 12), (3, 7), (3, 8), (12, 7), (12, 8)):
        px(canvas, x, y, GRATE_SHADOW)
    for y in (7, 8):
        for x in (7, 8):
            px(canvas, x, y, CYAN_LIGHT)


def gear_face(canvas, rng):
    """Assembly gear: dark ring with eight teeth around a bronze hub."""
    _panel_field(canvas)
    for y in range(3, 13):
        for x in range(3, 13):
            dist = math.hypot(x - 7.5, y - 7.5)
            if 3.0 <= dist <= 3.6:
                px(canvas, x, y, GRATE_SHADOW)
            elif 1.2 <= dist <= 2.4:
                px(canvas, x, y, BRONZE_DARK)
            elif dist < 1.2:
                px(canvas, x, y, BRONZE_RIVET)
    for tooth in range(8):
        a = math.radians(tooth * 45 + 22.5)
        x = int(round(7.5 + 3.3 * math.cos(a)))
        y = int(round(7.5 + 3.3 * math.sin(a)))
        px(canvas, x, y, GRATE_SHADOW)


def circuit_face(canvas, rng):
    """Circuit assembly: central chip with symmetric four-way traces."""
    _panel_field(canvas)
    for y in range(6, 10):
        for x in range(6, 10):
            px(canvas, x, y, GRATE_SHADOW)
    for y in (7, 8):
        for x in (7, 8):
            px(canvas, x, y, CYAN_LIGHT)
    for i in (5, 10):
        for j in (6, 7, 8, 9):
            px(canvas, i, j, BRONZE_DARK)
            px(canvas, j, i, BRONZE_DARK)
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        px(canvas, x, y, GRATE_BAR)


def rotor_face(canvas, rng):
    """Mixing rotors: X-shaped twin blades around a dark hub."""
    _panel_field(canvas)
    for y in range(3, 13):
        for x in range(3, 13):
            dist = math.hypot(x - 7.5, y - 7.5)
            if 3.0 <= dist <= 3.6:
                px(canvas, x, y, GRATE_SHADOW)
    for d in range(3, 13):
        c = BRONZE_RIVET if d in (3, 12) else BRONZE_DARK
        px(canvas, d, d, c)
        px(canvas, d, 15 - d, c)
    for y in (7, 8):
        for x in (7, 8):
            px(canvas, x, y, GRATE_SHADOW)


def gear_train_top(canvas, rng):
    """Drive gear pair with a bearing collar for the grinding block top."""
    beveled_plates(canvas, rng)
    rect(canvas, 2, 2, SIZE - 3, SIZE - 3, BRONZE_DARK)
    gear(canvas, 5, 8, BRONZE_DARK, BRONZE_BASE, STEEL_MID)
    gear(canvas, 11, 8, BRONZE_LIGHT, BRONZE_BASE, STEEL_MID)
    rect(canvas, 7, 6, 8, 10, DARK_METAL)
    riveted_frame(canvas)


def circuit_top(canvas, rng):
    """Precision circuit assembly table: pins, slots, small corner clamps."""
    plate_noise(canvas, rng)
    rect(canvas, 2, 2, SIZE - 3, SIZE - 3, BRONZE_DARK)
    rect(canvas, 3, 3, SIZE - 4, SIZE - 4, STEEL_MID)
    for x in range(4, SIZE - 3, 2):
        px(canvas, x, 5, DARKER_METAL)
        px(canvas, x, 10, DARKER_METAL)
    hline(canvas, 4, SIZE - 5, 7, DARK_METAL)
    hline(canvas, 4, SIZE - 5, 8, CYAN_DARK)
    for cx, cy in ((2, 2), (SIZE - 3, 2), (2, SIZE - 3), (SIZE - 3, SIZE - 3)):
        px(canvas, cx, cy, BRONZE_DARK)
        px(canvas, cx + 1, cy + 1, BRONZE_RIVET)
    riveted_frame(canvas)


def bronze_component_item(rng):
    """Symmetric I-fitting: top/bottom flanges, a central column with rivets
    and side ribs connecting them — transparent corners frame the item."""
    canvas = new_canvas()
    plate_noise(canvas, rng, 10)
    # plate body
    rect(canvas, 3, 3, SIZE - 4, SIZE - 4, BRONZE_BASE)
    # top & bottom flanges
    rect(canvas, 2, 2, SIZE - 3, 5, BRONZE_DARK)
    rect(canvas, 2, 10, SIZE - 3, SIZE - 3, BRONZE_DARK)
    rect(canvas, 3, 3, SIZE - 4, 4, BRONZE_LIGHT)
    rect(canvas, 3, 11, SIZE - 4, SIZE - 4, BRONZE_LIGHT)
    # central column with riveted bands
    rect(canvas, 6, 5, SIZE - 7, 10, BRONZE_DARK)
    rect(canvas, 7, 5, 8, 10, BRONZE_BASE)
    for y in (6, 9):
        hline(canvas, 7, 8, y, BRONZE_RIVET)
    # side ribs connecting the flanges to the column
    hline(canvas, 3, 5, 7, BRONZE_DARK)
    hline(canvas, 3, 5, 8, BRONZE_DARK)
    hline(canvas, SIZE - 6, SIZE - 4, 7, BRONZE_DARK)
    hline(canvas, SIZE - 6, SIZE - 4, 8, BRONZE_DARK)
    # corner rivets on the plate
    for cx, cy in ((3, 3), (SIZE - 4, 3), (3, SIZE - 4), (SIZE - 4, SIZE - 4)):
        px(canvas, cx, cy, BRONZE_RIVET)
    return canvas


def crafting_station_top(canvas, rng):
    """Synthetic-station top: symmetric 3x3 crafting grid on a bench plate."""
    plate_noise(canvas, rng, 10)
    rect(canvas, 1, 1, SIZE - 2, SIZE - 2, BRONZE_BASE)
    # grid trenches at 2, 6, 9, 13 (mirror-symmetric about x/y = 7.5)
    for t in (2, 6, 9, 13):
        vline(canvas, t, 2, SIZE - 3, BRONZE_EDGE)
        hline(canvas, 2, SIZE - 3, t, BRONZE_EDGE)
    # symmetric highlights inside the cells (centre cell fully filled)
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        px(canvas, x, y, BRONZE_RIVET)
    for y in (7, 8):
        for x in (7, 8):
            px(canvas, x, y, BRONZE_RIVET)
    riveted_frame(canvas)


def crafting_station_bottom(canvas, rng):
    """Synthetic-station bottom: plain symmetric plate with a cross brace."""
    plate_noise(canvas, rng, 10)
    rect(canvas, 2, 2, SIZE - 3, SIZE - 3, BRONZE_DARK)
    rect(canvas, 3, 3, SIZE - 4, SIZE - 4, BRONZE_BASE)
    vline(canvas, 7, 3, SIZE - 4, BRONZE_DARK)
    vline(canvas, 8, 3, SIZE - 4, BRONZE_DARK)
    hline(canvas, 3, SIZE - 4, 7, BRONZE_DARK)
    hline(canvas, 3, SIZE - 4, 8, BRONZE_DARK)
    for cx, cy in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px(canvas, cx, cy, BRONZE_RIVET)
    riveted_frame(canvas)


def steam_exhaust_hatch_front():
    """Recessed square steel grille on a bronze shell (exhaust hatch front,
    large-heat-storage-steam-furnace.md 美术方向). Every pixel is written to
    all four mirrored positions, so the face is symmetric about both axes."""
    canvas = new_canvas()
    # Symmetric plate shading: brightness varies with distance from the
    # vertical centre line only.
    for y in range(SIZE):
        for x in range(SIZE):
            band = abs(x - (SIZE - 1) / 2.0)
            delta = 6 if band < 4 else (0 if band < 6.5 else -6)
            px(canvas, x, y, shade(BRONZE_BASE, delta))

    def sym(x, y, color):
        px(canvas, x, y, color)
        px(canvas, SIZE - 1 - x, y, color)
        px(canvas, x, SIZE - 1 - y, color)
        px(canvas, SIZE - 1 - x, SIZE - 1 - y, color)

    # Outer dark border and a uniform mid-tone rim with corner rivets.
    for i in range(SIZE):
        sym(i, 0, BRONZE_EDGE)
        sym(0, i, BRONZE_EDGE)
    for i in range(1, SIZE - 1):
        sym(i, 1, BRONZE_DARK)
        sym(1, i, BRONZE_DARK)
    for cx, cy in ((1, 1), (SIZE - 2, 1), (1, SIZE - 2), (SIZE - 2, SIZE - 2)):
        sym(cx, cy, BRONZE_RIVET)

    # Recessed grille area 3..12 with a uniform shadow ring.
    for y in range(3, SIZE - 3):
        for x in range(3, SIZE - 3):
            px(canvas, x, y, GRATE_DARK)
    for i in range(3, SIZE - 3):
        sym(i, 3, GRATE_SHADOW)
        sym(3, i, GRATE_SHADOW)

    # Coarse 2px steel bars mirrored about the centre (5-6 / 9-10).
    for t in (5, 6, 9, 10):
        for y in range(3, SIZE - 3):
            sym(t, y, GRATE_BAR)
        for x in range(3, SIZE - 3):
            sym(x, t, GRATE_BAR)
    # Rivet dots where the bars meet the recess frame.
    for a in (5, 6, 9, 10):
        sym(a, 4, GRATE_SHADOW)
        sym(4, a, GRATE_SHADOW)
    return canvas



FURN_DOOR_FRAME = BRONZE_DARK
FURN_DOOR_PANEL = shade(BRONZE_BASE, -18)
FURN_RIB = BRONZE_DARK
FURN_HINGE = BRONZE_RIVET
FURN_WINDOW = (104, 62, 22)
FURN_WINDOW_FRAME = BRONZE_DARK
FURN_SLIT = shade(BRONZE_BASE, -30)
FURN_NEEDLE_IDLE = (70, 40, 14)
FIRE_BASE = (180, 84, 20)
FIRE_MID = (232, 140, 42)
FIRE_CORE = (255, 200, 100)


def furnace_front(lit):
    """Large Heat-Storage Steam Furnace controller front overlay: bronze is
    the dominant material per the steam-era positioning — steel appears only
    as thin accents. Large furnace door with ribs, hinges, observation
    window and vent slits; centred temperature gauge. Mirror-symmetric about
    the vertical centre axis; lit swaps in the orange firing glow
    (large-heat-storage-steam-furnace.md 美术方向, 经用户要求以亮青铜为主体)."""
    canvas = new_canvas()
    # Bright symmetric bronze field.
    for y in range(SIZE):
        for x in range(SIZE):
            band = abs(x - (SIZE - 1) / 2.0)
            delta = 10 if band < 4.5 else (2 if band < 6.5 else -8)
            px(canvas, x, y, shade(BRONZE_BASE, delta))

    def m(x, y, color):
        px(canvas, x, y, color)
        px(canvas, SIZE - 1 - x, y, color)

    # Thin bronze edge with a light inner rim and corner rivets.
    for i in range(SIZE):
        m(i, 0, BRONZE_EDGE)
        m(0, i, BRONZE_EDGE)
    for i in range(1, SIZE - 1):
        m(i, 1, BRONZE_LIGHT)
        m(1, i, BRONZE_LIGHT)
    for cx, cy in ((1, 1), (SIZE - 2, 1), (1, SIZE - 2), (SIZE - 2, SIZE - 2)):
        m(cx, cy, BRONZE_RIVET)

    # Centred temperature gauge (rows 2..5, cols 6..9): bronze rim, light face.
    for y in range(2, 6):
        for x in range(6, 10):
            px(canvas, x, y, BRONZE_DARK)
    for y in range(3, 5):
        for x in range(7, 9):
            px(canvas, x, y, BRONZE_LIGHT)
    needle = (255, 130, 70) if lit else FURN_NEEDLE_IDLE
    px(canvas, 7, 2, needle)
    px(canvas, 8, 2, needle)

    # Large furnace door (cols 4..11, rows 6..13): bronze frame, warm panel.
    for y in range(6, 14):
        for x in range(4, 12):
            px(canvas, x, y, FURN_DOOR_FRAME)
    for y in range(7, 13):
        for x in range(5, 11):
            px(canvas, x, y, FURN_DOOR_PANEL)
    # Cross ribs (X shape, symmetric).
    for i in range(6):
        m(5 + i, 7 + i, FURN_RIB)
        m(10 - i, 7 + i, FURN_RIB)
    # Hinges mid-height on both door sides.
    m(4, 9, FURN_HINGE)
    m(4, 11, FURN_HINGE)
    # Observation window (cols 6..9, rows 7..9): deep amber when unlit.
    for y in range(7, 10):
        for x in range(6, 10):
            px(canvas, x, y, FURN_WINDOW_FRAME)
    for y in range(8, 9):
        for x in range(6, 10):
            px(canvas, x, y, FURN_WINDOW)
    # Vent slits near the door bottom (symmetric pairs).
    for x in (5, 6, 9, 10):
        px(canvas, x, 12, FURN_SLIT)

    if lit:
        # Firing glow: window lights up, fire fills the door bottom.
        for x in range(6, 10):
            px(canvas, x, 8, FIRE_MID)
        px(canvas, 7, 8, FIRE_CORE)
        px(canvas, 8, 8, FIRE_CORE)
        for y in range(10, 13):
            for x in range(5, 11):
                px(canvas, x, y, FIRE_BASE)
        for y in range(11, 13):
            for x in range(6, 10):
                px(canvas, x, y, FIRE_MID)
        for x in range(7, 9):
            px(canvas, x, 12, FIRE_CORE)
    return canvas


def furnace_front_emissive(lit):
    """Emissive mask with a TRANSPARENT background: only the flame, the
    observation window and the gauge needle carry alpha; the shell must stay
    see-through or the full-bright layer covers the whole front face
    (matches the working mixed-fuel-boiler masks)."""
    canvas = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]
    if not lit:
        return canvas
    for y in range(8, 9):
        for x in range(6, 10):
            canvas[y][x] = (255, 200, 120, 255)
    for y in range(10, 13):
        for x in range(5, 11):
            canvas[y][x] = (255, 255, 255, 255)
    canvas[2][7] = (255, 255, 255, 255)
    canvas[2][8] = (255, 255, 255, 255)
    return canvas


def write_png(path, canvas):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = b""
    for row in canvas:
        raw += b"\x00" + b"".join(
            struct.pack("4B", *px_[:3], px_[3] if len(px_) > 3 else 255) for px_ in row)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    ihdr = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
    with open(path, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n")
        fh.write(chunk(b"IHDR", ihdr))
        fh.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        fh.write(chunk(b"IEND", b""))


def main():
    root = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                        "assets", "gregsteamexpansion", "textures")
    blocks = {
        "steam_grinding_block": {
            "side": grind_face,
            "top": grind_face,
            "bottom": reinforced_bottom,
        },
        "steam_assembly_block": {
            "side": gear_face,
            "top": gear_face,
            "bottom": reinforced_bottom,
        },
        "steam_circuit_assembly_block": {
            "side": circuit_face,
            "top": circuit_face,
            "bottom": reinforced_bottom,
        },
        "steam_mixing_block": {
            "side": rotor_face,
            "top": rotor_face,
            "bottom": reinforced_bottom,
        },
    }
    blocks["crafting_station"] = {
        "top": crafting_station_top,
        "bottom": crafting_station_bottom,
    }
    for name, faces in blocks.items():
        for face, painter in faces.items():
            rng = random.Random(f"{name}/{face}")
            canvas = new_canvas()
            painter(canvas, rng)
            write_png(os.path.join(root, "block", name, f"{face}.png"), canvas)
            print(f"wrote block/{name}/{face}.png")

    # NOTE: the furnace controller overlays and the exhaust hatch front are
    # owned by tools/gen_hatch_textures.py now (GT decal style); this script
    # deliberately does NOT write them.

    component = bronze_component_item(random.Random("bronze_component"))
    write_png(os.path.join(root, "item", "bronze_component.png"), component)
    print("wrote item/bronze_component.png")

    # Symmetry gate: every texture written by this script must be
    # mirror-symmetric about both axes.
    import glob
    from PIL import Image as _Image
    written = (
        [os.path.join(root, "block", name, f"{face}.png")
         for name, faces in blocks.items() for face in faces]
        + [os.path.join(root, "item", "bronze_component.png"),
           os.path.join(root, "block", "crafting_station", "top.png"),
           os.path.join(root, "block", "crafting_station", "bottom.png")])
    for path in written:
        img = _Image.open(path).convert("RGBA")
        px = img.load()
        w, h = img.size
        for y in range(h):
            for x in range(w):
                assert px[x, y] == px[w - 1 - x, y], (path, "h", x, y)
                assert px[x, y] == px[x, h - 1 - y], (path, "v", x, y)
    print("symmetry check passed for", len(written), "textures")


if __name__ == "__main__":
    main()
