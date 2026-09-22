#!/usr/bin/env python3
"""Verify startup-only configuration across independent GameTest server processes."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = ROOT / "run" / "config" / "gregsteamexpansion-common.toml"
PROFILE_DEFAULTS = {
    "EASY": (
        (2, 5.0, 2, 40, 2, 50, 2.0, 3.0, 0.0, 0, 0, 0, 2.0, 4, 100, 7,
         75, 65, 55, 45, 144, 576, 1440),
        (False, False, False, False, False),
        (False,) * 19,
    ),
    "NORMAL": (
        (1, 5.0, 1, 100, 5, 100, 1.5, 2.0, 24.0, 10, 25, 50, 1.0, 2, 50, 3,
         80, 70, 60, 50, 192, 768, 1920),
        (False, False, False, False, False),
        (True, True, False, False, False, True, False, False, False, True,
         True, True, False, True, True, False, True, False, False),
    ),
    "EXPERT": (
        (1, 2.0, 1, 220, 10, 100, 1.0, 1.5, 8.0, 15, 40, 75, 1.0, 1, 25, 1,
         90, 80, 70, 60, 288, 1152, 2880),
        (True, True, True, True, True),
        (True,) * 19,
    ),
}
PROFILE_NUMBER_KEYS = (
    "gtceuCasingsPerCraft",
    "steamOutputMultiplier",
    "singleblockSteamCacheMultiplier",
    "preheatCostPercent",
    "preheatIntervalTicks",
    "processingSteamPercent",
    "oreCrushingMultiplier",
    "boilerRoomSteamOutputMultiplier",
    "boilerRoomScaleFailureHours",
    "boilerRoomScaleLossStage1Percent",
    "boilerRoomScaleLossStage2Percent",
    "boilerRoomScaleLossStage3Percent",
    "assemblerOutputMultiplier",
    "voidProducerOutputMultiplier",
    "circuitAssemblerBonusChancePercent",
    "circuitAssemblerBonusMultiplier",
    "blastFurnaceNoviceDurationPercent",
    "blastFurnaceFamiliarDurationPercent",
    "blastFurnaceSkilledDurationPercent",
    "blastFurnaceMasteredDurationPercent",
    "blastFurnaceFamiliarOperations",
    "blastFurnaceSkilledOperations",
    "blastFurnaceMasteredOperations",
)
GSE_RECIPE_KEYS = (
    "hardBronzeComponentRecipes",
    "harderSteamGrindingBlockRecipes",
    "hardSteamAssemblyBlockRecipes",
    "hardSteamCircuitAssemblyBlockRecipes",
    "hardSteamMixingBlockRecipes",
)
GTCEU_RECIPE_KEYS = (
    "disableManualCompression",
    "harderRods",
    "harderBrickRecipes",
    "nerfWoodCrafting",
    "hardWoodRecipes",
    "hardIronRecipes",
    "hardRedstoneRecipes",
    "hardToolArmorRecipes",
    "hardMiscRecipes",
    "hardGlassRecipes",
    "nerfPaperCrafting",
    "hardAdvancedIronRecipes",
    "hardDyeRecipes",
    "harderCharcoalRecipe",
    "flintAndSteelRequireSteel",
    "removeVanillaBlockRecipes",
    "removeVanillaTNTRecipe",
    "harderCircuitRecipes",
    "hardMultiRecipes",
)
PHASES = (
    {
        "name": "easy",
        "difficulty": "EASY",
        "ore_enabled": False,
        "external_modules_enabled": False,
        "fluid_enabled": True,
        "ore_weights": ("minecraft:iron_ore|7|2",),
        "fluid_weights": ("minecraft:water|9",),
        "profile_casings": 3,
        "profile_steam_output": 6.25,
        "profile_scale_hours": 0.0,
        "profile_void_output": 5,
        "profile_circuit_bonus_chance": 100,
        "profile_circuit_bonus_multiplier": 7,
        "profile_blast_values": (76, 66, 56, 46, 145, 577, 1441),
        "profile_harder_rods": True,
        "profile_hard_bronze_component": True,
    },
    {
        "name": "normal",
        "difficulty": "NORMAL",
        "ore_enabled": True,
        "external_modules_enabled": True,
        "fluid_enabled": True,
        "ore_weights": (),
        "fluid_weights": (),
        "profile_casings": 2,
        "profile_steam_output": 4.25,
        "profile_scale_hours": 30.0,
        "profile_void_output": 3,
        "profile_circuit_bonus_chance": 50,
        "profile_circuit_bonus_multiplier": 3,
        "profile_blast_values": (81, 71, 61, 51, 193, 769, 1921),
        "profile_harder_rods": False,
        "profile_hard_bronze_component": True,
    },
    {
        "name": "expert",
        "difficulty": "EXPERT",
        "ore_enabled": True,
        "external_modules_enabled": True,
        "fluid_enabled": False,
        "ore_weights": ("minecraft:gold_ore|3|8",),
        "fluid_weights": ("minecraft:lava|5",),
        "profile_casings": 3,
        "profile_steam_output": 1.75,
        "profile_scale_hours": 9.0,
        "profile_void_output": 2,
        "profile_circuit_bonus_chance": 25,
        "profile_circuit_bonus_multiplier": 1,
        "profile_blast_values": (91, 81, 71, 61, 289, 1153, 2881),
        "profile_harder_rods": False,
        "profile_hard_bronze_component": False,
    },
)


def gradle_command(offline: bool) -> list[str]:
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    command = [str(wrapper), "--no-daemon", "runGameTestServer"]
    if offline:
        command.append("--offline")
    command.append("--stacktrace")
    return command


def toml_list(values: tuple[str, ...]) -> str:
    return "[" + ", ".join(f'"{value}"' for value in values) + "]"


def toml_value(value: object) -> str:
    return str(value).lower() if isinstance(value, bool) else str(value)


def profile_lines(phase: dict[str, object], difficulty: str) -> list[str]:
    numbers, gse_recipes, gtceu_recipes = PROFILE_DEFAULTS[difficulty]
    number_values = dict(zip(PROFILE_NUMBER_KEYS, numbers, strict=True))
    gse_values = dict(zip(GSE_RECIPE_KEYS, gse_recipes, strict=True))
    gtceu_values = dict(zip(GTCEU_RECIPE_KEYS, gtceu_recipes, strict=True))
    if difficulty == phase["difficulty"]:
        number_values["gtceuCasingsPerCraft"] = phase["profile_casings"]
        number_values["steamOutputMultiplier"] = phase["profile_steam_output"]
        number_values["boilerRoomScaleFailureHours"] = phase["profile_scale_hours"]
        number_values["voidProducerOutputMultiplier"] = phase["profile_void_output"]
        number_values["circuitAssemblerBonusChancePercent"] = phase["profile_circuit_bonus_chance"]
        number_values["circuitAssemblerBonusMultiplier"] = phase["profile_circuit_bonus_multiplier"]
        for key, value in zip(PROFILE_NUMBER_KEYS[16:23], phase["profile_blast_values"], strict=True):
            number_values[key] = value
        gse_values["hardBronzeComponentRecipes"] = phase["profile_hard_bronze_component"]
        gtceu_values["harderRods"] = phase["profile_harder_rods"]

    name = difficulty.lower()
    lines = [f"[difficultyProfiles.{name}]"]
    lines.extend(f"{key} = {toml_value(value)}" for key, value in number_values.items())
    lines.extend(("", f"[difficultyProfiles.{name}.gseRecipeOptions]"))
    lines.extend(f"{key} = {toml_value(value)}" for key, value in gse_values.items())
    lines.extend(("", f"[difficultyProfiles.{name}.gtceuRecipeOptions]"))
    lines.extend(f"{key} = {toml_value(value)}" for key, value in gtceu_values.items())
    lines.append("")
    return lines


def write_config(phase: dict[str, object]) -> None:
    CONFIG_PATH.parent.mkdir(parents=True, exist_ok=True)
    lines = (
        [
            f'difficulty = "{phase["difficulty"]}"',
            "difficultyEnabled = true",
            "difficultySetupCompleted = true",
            f'externalModulesEnabled = {str(phase["external_modules_enabled"]).lower()}',
            "",
            "[machines]",
            "",
            "[machines.large_steam_ore_plant]",
            f'enabled = {str(phase["ore_enabled"]).lower()}',
            f'weights = {toml_list(phase["ore_weights"])}',
            "",
            "[machines.large_steam_fluid_drill]",
            f'enabled = {str(phase["fluid_enabled"]).lower()}',
            f'weights = {toml_list(phase["fluid_weights"])}',
            "",
            "[machines.boiler_room]",
            "",
            "[machines.boiler_room.waterScale]",
            "enabled = true",
            "descalingAcidMb = 8000",
            "descalingDurationTicks = 3200",
            "",
            "[difficultyProfiles]",
            "",
        ]
        + profile_lines(phase, "EASY")
        + profile_lines(phase, "NORMAL")
        + profile_lines(phase, "EXPERT")
    )
    CONFIG_PATH.write_text(
        "\n".join(lines),
        encoding="utf-8",
        newline="\n",
    )


def run_phase(phase: dict[str, object], command: list[str]) -> None:
    write_config(phase)
    environment = os.environ.copy()
    environment["GSE_EXPECTED_DIFFICULTY"] = str(phase["difficulty"])
    environment["GSE_EXPECTED_ORE_PLANT_ENABLED"] = str(phase["ore_enabled"]).lower()
    environment["GSE_EXPECTED_EXTERNAL_MODULES_ENABLED"] = str(
        phase["external_modules_enabled"]
    ).lower()
    environment["GSE_EXPECTED_FLUID_DRILL_ENABLED"] = str(phase["fluid_enabled"]).lower()
    environment["GSE_EXPECTED_ORE_PLANT_WEIGHTS"] = ";".join(phase["ore_weights"])
    environment["GSE_EXPECTED_FLUID_DRILL_WEIGHTS"] = ";".join(phase["fluid_weights"])
    environment["GSE_EXPECTED_PROFILE_CASINGS"] = str(phase["profile_casings"])
    environment["GSE_EXPECTED_PROFILE_STEAM_OUTPUT"] = str(phase["profile_steam_output"])
    environment["GSE_EXPECTED_PROFILE_SCALE_HOURS"] = str(phase["profile_scale_hours"])
    environment["GSE_EXPECTED_PROFILE_VOID_OUTPUT"] = str(phase["profile_void_output"])
    environment["GSE_EXPECTED_PROFILE_CIRCUIT_BONUS_CHANCE"] = str(
        phase["profile_circuit_bonus_chance"]
    )
    environment["GSE_EXPECTED_PROFILE_CIRCUIT_BONUS_MULTIPLIER"] = str(
        phase["profile_circuit_bonus_multiplier"]
    )
    for key, value in zip(PROFILE_NUMBER_KEYS[16:23], phase["profile_blast_values"], strict=True):
        environment[f"GSE_EXPECTED_PROFILE_{key.upper()}"] = str(value)
    environment["GSE_EXPECTED_PROFILE_HARDER_RODS"] = str(phase["profile_harder_rods"]).lower()
    environment["GSE_EXPECTED_PROFILE_HARD_BRONZE_COMPONENT"] = str(
        phase["profile_hard_bronze_component"]
    ).lower()
    print(
        f'\n=== Configuration restart: {phase["name"]} phase '
        f'({phase["difficulty"]}) ===',
        flush=True,
    )
    subprocess.run(command, cwd=ROOT, env=environment, check=True)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Run GameTest servers with all three startup-only difficulty profiles."
    )
    parser.add_argument(
        "--offline",
        action="store_true",
        help="pass --offline to Gradle (dependencies must already be cached)",
    )
    args = parser.parse_args()
    command = gradle_command(args.offline)
    original = CONFIG_PATH.read_bytes() if CONFIG_PATH.exists() else None

    try:
        for phase in PHASES:
            run_phase(phase, command)
    except subprocess.CalledProcessError as error:
        print(
            f"Configuration restart verification failed during a Gradle run "
            f"(exit code {error.returncode}).",
            file=sys.stderr,
        )
        return error.returncode or 1
    finally:
        if original is None:
            CONFIG_PATH.unlink(missing_ok=True)
        else:
            CONFIG_PATH.write_bytes(original)

    print("\nConfiguration restart verification passed; original config restored.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
