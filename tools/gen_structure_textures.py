"""Generates placeholder 16x16 textures for the plain steam structure blocks
and the bronze component (docs/design/items-and-blocks.md 模型与纹理方向).

The output follows each entry's documented art direction (bronze plate shell,
dark riveted corner frames, cyan grinding ring, meshed gear pair, wire slots,
twin rotor silhouettes) so gameplay can ship before hand-drawn art replaces
these files 1:1. Pure stdlib PNG writer, deterministic output.

Usage: python tools/gen_structure_textures.py
"""

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


def plate_noise(canvas, rng, strength=14):
    for y in range(SIZE):
        for x in range(SIZE):
            if rng.random() < 0.45:
                px(canvas, x, y, shade(canvas[y][x], rng.randint(-strength, strength // 2)))


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
    rect(canvas, 2, 4, SIZE - 3, SIZE - 3, DARKER_METAL)
    gear(canvas, 6, 8, BRONZE_BASE, BRONZE_LIGHT, DARK_METAL)
    gear(canvas, 10, 8, BRONZE_DARK, BRONZE_BASE, DARK_METAL)
    # Mesh point: teeth of both gears meet on the shared center line.
    rect(canvas, 8, 7, 9, 9, BRONZE_DARK)
    riveted_frame(canvas)


def grinding_ring(canvas, rng):
    """Cyan segmented grinding ring inside a dark bearing ring (grinding side)."""
    rect(canvas, 2, 2, SIZE - 3, SIZE - 3, DARK_METAL)
    rect(canvas, 4, 4, SIZE - 5, SIZE - 5, CYAN_BASE)
    # Segment cuts keep the ring from reading as a solid diamond block.
    for y in range(4, SIZE - 4, 3):
        hline(canvas, 4, SIZE - 5, y, CYAN_DARK)
    rect(canvas, 7, 7, 8, 8, DARKER_METAL)
    rect(canvas, 6, 6, 6, 9, CYAN_LIGHT)
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
    rect(canvas, 3, 2, SIZE - 4, SIZE - 3, DARK_METAL)
    vline(canvas, 7, 3, SIZE - 4, BRONZE_DARK)
    vline(canvas, 8, 3, SIZE - 4, BRONZE_DARK)
    rotor(canvas, 8, 5)
    rotor(canvas, 8, 11)
    riveted_frame(canvas)


def mixer_top(canvas, rng):
    """Drive gear inside a bearing collar on a reinforced mount."""
    beveled_plates(canvas, rng)
    rect(canvas, 2, 2, SIZE - 3, SIZE - 3, DARK_METAL)
    # Bearing collar.
    rect(canvas, 4, 4, SIZE - 5, SIZE - 5, STEEL_MID)
    rect(canvas, 5, 5, SIZE - 6, SIZE - 6, DARKER_METAL)
    gear(canvas, 8, 8, BRONZE_BASE, BRONZE_LIGHT, DARK_METAL)
    riveted_frame(canvas)


def wire_slots(canvas, rng):
    """Fine positioning holes and parallel wire slots (circuit assembly side)."""
    rect(canvas, 2, 2, SIZE - 3, SIZE - 3, DARKER_METAL)
    # Crossed conductor slots.
    hline(canvas, 3, SIZE - 4, 8, RUBBER_BASE)
    vline(canvas, 8, 3, SIZE - 4, RUBBER_BASE)
    # Fine positioning pin rows.
    for y in (5, 11):
        for x in range(4, SIZE - 3, 3):
            px(canvas, x, y, BRONZE_LIGHT)
            px(canvas, x, y + 1, DARK_METAL)
    riveted_frame(canvas)


def assembly_top(canvas, rng):
    """Segmented assembly table with locating holes and corner clamps."""
    beveled_plates(canvas, rng)
    rect(canvas, 3, 3, SIZE - 4, SIZE - 4, DARK_METAL)
    rect(canvas, 4, 4, SIZE - 5, SIZE - 5, BRONZE_DARK)
    for cx, cy in ((5, 5), (10, 5), (5, 10), (10, 10)):
        px(canvas, cx, cy, DARKER_METAL)
        px(canvas, cx, cy, BRONZE_RIVET if (cx + cy) % 2 else STEEL_MID)
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
        px(canvas, cx + 1, cy, BRONZE_DARK)
    for i in range(5, SIZE - 4, 3):
        px(canvas, i, 2, STEEL_MID)
        px(canvas, i, SIZE - 3, STEEL_MID)
    riveted_frame(canvas)


def gear_train_top(canvas, rng):
    """Drive gear pair with a bearing collar for the grinding block top."""
    beveled_plates(canvas, rng)
    rect(canvas, 2, 2, SIZE - 3, SIZE - 3, DARK_METAL)
    gear(canvas, 5, 8, BRONZE_DARK, BRONZE_BASE, DARKER_METAL)
    gear(canvas, 11, 8, BRONZE_LIGHT, BRONZE_BASE, DARKER_METAL)
    rect(canvas, 7, 6, 8, 10, DARK_METAL)
    riveted_frame(canvas)


def circuit_top(canvas, rng):
    """Precision circuit assembly table: pins, slots, small corner clamps."""
    plate_noise(canvas, rng)
    rect(canvas, 2, 2, SIZE - 3, SIZE - 3, DARK_METAL)
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
    """Compact square bronze frame: spring coils left/right, plated corners."""
    canvas = new_canvas()
    plate_noise(canvas, rng, 10)
    rect(canvas, 3, 3, SIZE - 4, SIZE - 4, BRONZE_BASE)
    # Hollow dark center keeps it distinct from a full block.
    rect(canvas, 6, 6, SIZE - 7, SIZE - 7, DARKER_METAL)
    # Corner plating over the frame.
    for cx, cy in ((3, 3), (SIZE - 4, 3), (3, SIZE - 4), (SIZE - 4, SIZE - 4)):
        rect(canvas, cx - 1, cy - 1, cx + 1, cy + 1, BRONZE_DARK)
        px(canvas, cx, cy, BRONZE_RIVET)
    # Outer silhouette.
    for i in range(2, SIZE - 2):
        px(canvas, i, 2, BRONZE_EDGE)
        px(canvas, i, SIZE - 3, BRONZE_EDGE)
        px(canvas, 2, i, BRONZE_EDGE)
        px(canvas, SIZE - 3, i, BRONZE_EDGE)
    # Twin spring coils inside the frame.
    for x in (6, 9):
        for y in range(6, SIZE - 5):
            px(canvas, x, y, BRONZE_LIGHT if y % 2 else BRONZE_DARK)
    # Segmented reinforcing plates between the coils.
    hline(canvas, 7, 8, 7, BRONZE_BASE)
    hline(canvas, 7, 8, 8, BRONZE_DARK)
    hline(canvas, 7, 8, 11, BRONZE_BASE)
    hline(canvas, 7, 8, 12, BRONZE_DARK)
    return canvas


def write_png(path, canvas):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = b""
    for row in canvas:
        raw += b"\x00" + b"".join(struct.pack("4B", *px_[:3], 255) for px_ in row)

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
            "side": grinding_ring,
            "top": gear_train_top,
            "bottom": reinforced_bottom,
        },
        "steam_assembly_block": {
            "side": meshed_gear_pair,
            "top": assembly_top,
            "bottom": reinforced_bottom,
        },
        "steam_circuit_assembly_block": {
            "side": wire_slots,
            "top": circuit_top,
            "bottom": reinforced_bottom,
        },
        "steam_mixing_block": {
            "side": twin_rotors,
            "top": mixer_top,
            "bottom": reinforced_bottom,
        },
    }
    for name, faces in blocks.items():
        for face, painter in faces.items():
            rng = random.Random(f"{name}/{face}")
            canvas = new_canvas()
            painter(canvas, rng)
            write_png(os.path.join(root, "block", name, f"{face}.png"), canvas)
            print(f"wrote block/{name}/{face}.png")

    write_png(os.path.join(root, "item", "bronze_component.png"), bronze_component_item(random.Random("bronze_component")))
    print("wrote item/bronze_component.png")


if __name__ == "__main__":
    main()
