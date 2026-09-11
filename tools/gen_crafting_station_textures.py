#!/usr/bin/env python3
"""Generate crafting-station block and GUI textures."""

import math
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
BLOCK_OUTPUT = ROOT / "src/main/resources/assets/gregsteamexpansion/textures/block/crafting_station"
GUI_OUTPUT = ROOT / "src/main/resources/assets/gregsteamexpansion/textures/gui"

COLORS = {
    "wood": (197, 157, 99, 255),
    "wood_light": (216, 178, 118, 255),
    "wood_dark": (165, 133, 79, 255),
    "wood_deep": (122, 98, 56, 255),
    "wood_shadow": (90, 72, 40, 255),
    "grid_line": (91, 70, 35, 255),
    "grid_surface": (217, 184, 116, 255),
    "bronze": (176, 109, 36, 255),
    "bronze_dark": (104, 66, 28, 255),
    "bronze_light": (218, 151, 65, 255),
    "dark": (74, 58, 32, 255),
    "panel": (198, 198, 198, 255),
    "slot_fill": (139, 139, 139, 255),
    "slot_dark": (55, 55, 55, 255),
    "slot_light": (255, 255, 255, 255),
    "border_light": (255, 255, 255, 255),
    "border_dark": (85, 85, 85, 255),
    "arrow_dark": (85, 85, 85, 255),
    "gear_body": (97, 97, 97, 255),
    "gear_hole": (58, 58, 58, 255),
}


def new_sprite(width: int, height: int) -> Image.Image:
    return Image.new("RGBA", (width, height), (0, 0, 0, 0))


def set_pixel(image: Image.Image, x: int, y: int, color: str) -> None:
    if 0 <= x < image.width and 0 <= y < image.height:
        image.putpixel((x, y), COLORS[color])


def fill_rect(image: Image.Image, x: int, y: int, width: int, height: int, color: str) -> None:
    for py in range(y, y + height):
        for px in range(x, x + width):
            set_pixel(image, px, py, color)


def save_sprite(image: Image.Image, directory: Path, name: str) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / f"{name}.png"
    image.save(path)
    print(f"wrote {path.relative_to(ROOT)}")


def draw_planks(image: Image.Image, base_color: str, seam_color: str) -> None:
    for py in range(16):
        tone = "wood_light" if py % 4 == 0 else base_color
        fill_rect(image, 0, py, 16, 1, tone)
    for py in range(3, 16, 4):
        fill_rect(image, 0, py, 16, 1, seam_color)
    fill_rect(image, 4, 0, 1, 3, "wood_shadow")
    fill_rect(image, 11, 4, 1, 3, "wood_shadow")
    fill_rect(image, 6, 8, 1, 3, "wood_shadow")
    fill_rect(image, 13, 12, 1, 3, "wood_shadow")


def draw_top_texture() -> None:
    sprite = new_sprite(16, 16)
    draw_planks(sprite, "wood", "wood_deep")
    fill_rect(sprite, 2, 2, 12, 12, "grid_surface")
    for position in range(2, 15, 4):
        fill_rect(sprite, position, 2, 1, 12, "grid_line")
        fill_rect(sprite, 2, position, 12, 1, "grid_line")
    for x, y in ((3, 3), (11, 3), (3, 11), (11, 11)):
        fill_rect(sprite, x, y, 3, 3, "wood_light")
    for x, y in ((0, 0), (13, 0), (0, 13), (13, 13)):
        fill_rect(sprite, x, y, 3, 3, "bronze")
        set_pixel(sprite, x + 1, y + 1, "bronze_dark")
        set_pixel(sprite, x, y, "bronze_light")
    save_sprite(sprite, BLOCK_OUTPUT, "top")


def draw_side_texture() -> None:
    sprite = new_sprite(16, 16)
    draw_planks(sprite, "wood_dark", "wood_deep")
    fill_rect(sprite, 0, 0, 16, 3, "bronze")
    fill_rect(sprite, 0, 0, 16, 1, "bronze_light")
    fill_rect(sprite, 0, 3, 16, 1, "bronze_dark")
    fill_rect(sprite, 3, 6, 10, 8, "dark")
    fill_rect(sprite, 4, 7, 8, 6, "wood_deep")
    fill_rect(sprite, 4, 7, 8, 1, "wood")
    fill_rect(sprite, 4, 12, 8, 1, "wood_shadow")
    save_sprite(sprite, BLOCK_OUTPUT, "side")


def draw_bottom_texture() -> None:
    sprite = new_sprite(16, 16)
    draw_planks(sprite, "wood_deep", "wood_shadow")
    fill_rect(sprite, 0, 0, 16, 1, "bronze_dark")
    fill_rect(sprite, 0, 15, 16, 1, "bronze_dark")
    fill_rect(sprite, 0, 0, 1, 16, "bronze_dark")
    fill_rect(sprite, 15, 0, 1, 16, "bronze_dark")
    save_sprite(sprite, BLOCK_OUTPUT, "bottom")


def draw_gear_icon(image: Image.Image, offset_x: int, offset_y: int) -> None:
    for py in range(16):
        for px in range(16):
            dx, dy = px - 7.5, py - 7.5
            radius = math.sqrt(dx * dx + dy * dy)
            if radius > 7.0:
                continue
            angle = math.atan2(dy, dx) + math.pi
            tooth = math.floor(angle / (math.pi / 4)) % 2 == 0
            if radius <= 2.0:
                set_pixel(image, offset_x + px, offset_y + py, "gear_hole")
            elif radius <= 5.0 or tooth:
                set_pixel(image, offset_x + px, offset_y + py, "gear_body")


def draw_slot(image: Image.Image, x: int, y: int) -> None:
    fill_rect(image, x, y, 16, 16, "slot_fill")
    fill_rect(image, x - 1, y - 1, 18, 1, "slot_dark")
    fill_rect(image, x - 1, y, 1, 18, "slot_dark")
    fill_rect(image, x, y + 16, 18, 1, "slot_light")
    fill_rect(image, x + 16, y, 1, 17, "slot_light")


def draw_gui_background() -> None:
    width, height = 184, 190
    gui = new_sprite(width, height)
    fill_rect(gui, 0, 0, width, height, "panel")
    fill_rect(gui, 0, 0, width, 1, "border_light")
    fill_rect(gui, 0, 0, 1, height, "border_light")
    fill_rect(gui, 0, height - 1, width, 1, "border_dark")
    fill_rect(gui, width - 1, 0, 1, height, "border_dark")
    for row in range(3):
        for column in range(3):
            draw_slot(gui, 30 + column * 18, 17 + row * 18)
    draw_slot(gui, 114, 35)
    for index in range(9):
        draw_slot(gui, 8 + index * 18, 80)
        draw_gear_icon(gui, 8 + index * 18, 80)
    for row in range(3):
        for column in range(9):
            draw_slot(gui, 8 + column * 18, 106 + row * 18)
    for column in range(9):
        draw_slot(gui, 8 + column * 18, 168)
    fill_rect(gui, 89, 42, 13, 4, "arrow_dark")
    fill_rect(gui, 89, 42, 13, 1, "slot_light")
    fill_rect(gui, 101, 38, 2, 12, "arrow_dark")
    fill_rect(gui, 103, 40, 2, 8, "arrow_dark")
    fill_rect(gui, 105, 42, 2, 4, "arrow_dark")
    save_sprite(gui, GUI_OUTPUT, "crafting_station")


def draw_terminal_background() -> None:
    width, height = 140, 190
    gui = new_sprite(width, height)
    fill_rect(gui, 0, 0, width, height, "panel")
    fill_rect(gui, 0, 0, width, 1, "border_light")
    fill_rect(gui, 0, 0, 1, height, "border_light")
    fill_rect(gui, 0, height - 1, width, 1, "border_dark")
    fill_rect(gui, width - 1, 0, 1, height, "border_dark")
    fill_rect(gui, 120, 12, 6, 160, "slot_fill")
    fill_rect(gui, 120, 12, 6, 1, "slot_dark")
    fill_rect(gui, 120, 12, 1, 160, "slot_dark")
    fill_rect(gui, 120, 171, 6, 1, "slot_light")
    fill_rect(gui, 125, 12, 1, 160, "slot_light")
    for row in range(9):
        for column in range(6):
            draw_slot(gui, 8 + column * 18, 12 + row * 18)
    save_sprite(gui, GUI_OUTPUT, "crafting_station_terminal")


def main() -> None:
    draw_top_texture()
    draw_side_texture()
    draw_bottom_texture()
    draw_gui_background()
    draw_terminal_background()


if __name__ == "__main__":
    main()
