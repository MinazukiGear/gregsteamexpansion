#!/usr/bin/env python3
"""Generate mixed-fuel boiler overlays and status icons."""

from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
BLOCK_OUTPUT = ROOT / "src/main/resources/assets/gregsteamexpansion/textures/block/generators/boiler/mixed_fuel"
ICON_OUTPUT = ROOT / "src/main/resources/assets/gregsteamexpansion/textures/gui/icon/mixed_fuel_boiler"

COLORS = {
    "outline": (24, 24, 28, 255),
    "bronze_mid": (124, 86, 34, 255),
    "bronze_edge": (166, 122, 56, 255),
    "iron": (128, 128, 136, 255),
    "fire_deep": (255, 106, 0, 255),
    "fire_mid": (255, 136, 0, 255),
    "fire_bright": (255, 170, 0, 255),
    "dark": (25, 25, 25, 255),
    "steel": (178, 178, 178, 255),
    "bronze": (176, 109, 36, 255),
    "bronze_dark": (104, 66, 28, 255),
    "amber": (195, 93, 27, 255),
    "orange": (255, 143, 0, 255),
    "yellow": (255, 216, 0, 255),
    "white": (255, 255, 255, 255),
    "blue_dark": (25, 55, 150, 255),
    "blue": (45, 95, 225, 255),
    "blue_light": (95, 165, 255, 255),
    "gray_light": (170, 170, 170, 255),
    "red_dark": (125, 20, 20, 255),
    "red": (225, 45, 35, 255),
}


def new_sprite() -> Image.Image:
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def set_pixel(image: Image.Image, x: int, y: int, color: str) -> None:
    if 0 <= x < 16 and 0 <= y < 16:
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


def draw_boiler_frame(image: Image.Image) -> None:
    fill_rect(image, 6, 3, 4, 1, "outline")
    set_pixel(image, 5, 4, "outline")
    fill_rect(image, 6, 4, 4, 1, "bronze_mid")
    set_pixel(image, 10, 4, "outline")
    fill_rect(image, 3, 5, 10, 1, "outline")
    fill_rect(image, 3, 13, 10, 1, "outline")
    for py in range(6, 13):
        set_pixel(image, 3, py, "outline")
        set_pixel(image, 12, py, "outline")
    fill_rect(image, 4, 6, 8, 1, "bronze_edge")
    fill_rect(image, 4, 12, 8, 1, "bronze_edge")
    for py in range(7, 12):
        set_pixel(image, 4, py, "bronze_edge")
        set_pixel(image, 11, py, "bronze_edge")
    fill_rect(image, 5, 7, 6, 4, "outline")
    set_pixel(image, 7, 7, "iron")
    set_pixel(image, 8, 7, "iron")
    set_pixel(image, 5, 11, "outline")
    set_pixel(image, 10, 11, "outline")
    fill_rect(image, 6, 11, 4, 1, "bronze_dark")
    for py in range(8, 10):
        set_pixel(image, 1, py, "outline")
        set_pixel(image, 2, py, "bronze_mid")


def add_active_flame(image: Image.Image) -> None:
    fill_rect(image, 6, 7, 4, 1, "fire_deep")
    set_pixel(image, 5, 8, "fire_deep")
    fill_rect(image, 6, 8, 4, 1, "fire_mid")
    set_pixel(image, 10, 8, "fire_deep")
    set_pixel(image, 5, 9, "fire_deep")
    set_pixel(image, 6, 9, "fire_mid")
    set_pixel(image, 7, 9, "fire_bright")
    set_pixel(image, 8, 9, "fire_bright")
    set_pixel(image, 9, 9, "fire_mid")
    set_pixel(image, 10, 9, "fire_deep")
    set_pixel(image, 5, 10, "fire_deep")
    fill_rect(image, 6, 10, 4, 1, "fire_mid")
    set_pixel(image, 10, 10, "fire_deep")


def draw_drop(image: Image.Image, offset_x: int, offset_y: int,
              dark: str = "blue_dark", mid: str = "blue", highlight: str = "blue_light") -> None:
    set_pixel(image, offset_x + 4, offset_y, highlight)
    fill_rect(image, offset_x + 3, offset_y + 1, 3, 2, mid)
    fill_rect(image, offset_x + 2, offset_y + 3, 5, 2, mid)
    fill_rect(image, offset_x + 1, offset_y + 5, 7, 3, dark)
    fill_rect(image, offset_x + 2, offset_y + 5, 5, 3, mid)
    fill_rect(image, offset_x + 3, offset_y + 8, 3, 1, dark)
    set_pixel(image, offset_x + 3, offset_y + 4, highlight)
    set_pixel(image, offset_x + 3, offset_y + 5, highlight)


def draw_slash(image: Image.Image) -> None:
    for index in range(10):
        set_pixel(image, 12 - index, 3 + index, "red_dark")
        if 0 < index < 9:
            set_pixel(image, 13 - index, 3 + index, "red")


def main() -> None:
    idle = new_sprite()
    draw_boiler_frame(idle)
    save_sprite(idle, BLOCK_OUTPUT, "overlay_front")

    save_sprite(new_sprite(), BLOCK_OUTPUT, "overlay_front_emissive")

    active = new_sprite()
    draw_boiler_frame(active)
    add_active_flame(active)
    save_sprite(active, BLOCK_OUTPUT, "overlay_front_active")

    active_emissive = new_sprite()
    add_active_flame(active_emissive)
    save_sprite(active_emissive, BLOCK_OUTPUT, "overlay_front_active_emissive")

    mode_liquid = new_sprite()
    draw_drop(mode_liquid, 3, 3)
    save_sprite(mode_liquid, ICON_OUTPUT, "mode_liquid")

    mode_co_firing = new_sprite()
    draw_drop(mode_co_firing, 0, 4)
    fill_rect(mode_co_firing, 9, 9, 6, 2, "bronze")
    fill_rect(mode_co_firing, 10, 7, 4, 2, "amber")
    set_pixel(mode_co_firing, 11, 5, "yellow")
    set_pixel(mode_co_firing, 13, 6, "orange")
    set_pixel(mode_co_firing, 9, 7, "bronze")
    save_sprite(mode_co_firing, ICON_OUTPUT, "mode_co_firing")

    dry = new_sprite()
    fill_rect(dry, 7, 2, 2, 7, "steel")
    fill_rect(dry, 8, 3, 1, 6, "red")
    fill_rect(dry, 6, 9, 4, 4, "red_dark")
    fill_rect(dry, 7, 10, 2, 2, "red")
    set_pixel(dry, 5, 14, "amber")
    set_pixel(dry, 7, 13, "orange")
    set_pixel(dry, 9, 14, "yellow")
    set_pixel(dry, 11, 13, "orange")
    save_sprite(dry, ICON_OUTPUT, "status_dry_boiler")

    missing_water = new_sprite()
    draw_drop(missing_water, 3, 3)
    draw_slash(missing_water)
    save_sprite(missing_water, ICON_OUTPUT, "status_missing_water")

    missing_liquid = new_sprite()
    draw_drop(missing_liquid, 3, 3, "red_dark", "amber", "orange")
    draw_slash(missing_liquid)
    save_sprite(missing_liquid, ICON_OUTPUT, "status_missing_liquid_fuel")

    missing_powder = new_sprite()
    fill_rect(missing_powder, 3, 11, 10, 2, "bronze")
    fill_rect(missing_powder, 5, 9, 6, 2, "amber")
    fill_rect(missing_powder, 7, 7, 2, 2, "orange")
    set_pixel(missing_powder, 4, 8, "bronze")
    set_pixel(missing_powder, 11, 8, "amber")
    draw_slash(missing_powder)
    save_sprite(missing_powder, ICON_OUTPUT, "status_missing_co_firing_fuel")

    steam_blocked = new_sprite()
    fill_rect(steam_blocked, 2, 9, 10, 4, "white")
    fill_rect(steam_blocked, 4, 7, 3, 2, "gray_light")
    fill_rect(steam_blocked, 8, 6, 3, 3, "white")
    set_pixel(steam_blocked, 5, 4, "gray_light")
    set_pixel(steam_blocked, 6, 3, "white")
    set_pixel(steam_blocked, 9, 3, "gray_light")
    set_pixel(steam_blocked, 10, 2, "white")
    fill_rect(steam_blocked, 11, 8, 4, 6, "red_dark")
    fill_rect(steam_blocked, 12, 9, 3, 4, "red")
    fill_rect(steam_blocked, 12, 10, 3, 2, "dark")
    save_sprite(steam_blocked, ICON_OUTPUT, "status_steam_output_blocked")


if __name__ == "__main__":
    main()
