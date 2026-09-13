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
PHASES = (
    {
        "name": "easy",
        "difficulty": "EASY",
        "ore_enabled": False,
        "fluid_enabled": True,
        "ore_weights": ("minecraft:iron_ore|7|2",),
        "fluid_weights": ("minecraft:water|9",),
    },
    {
        "name": "expert",
        "difficulty": "EXPERT",
        "ore_enabled": True,
        "fluid_enabled": False,
        "ore_weights": ("minecraft:gold_ore|3|8",),
        "fluid_weights": ("minecraft:lava|5",),
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


def write_config(phase: dict[str, object]) -> None:
    CONFIG_PATH.parent.mkdir(parents=True, exist_ok=True)
    CONFIG_PATH.write_text(
        "\n".join(
            (
                f'difficulty = "{phase["difficulty"]}"',
                "",
                "[machines.large_steam_ore_plant]",
                f'enabled = {str(phase["ore_enabled"]).lower()}',
                f'weights = {toml_list(phase["ore_weights"])}',
                "",
                "[machines.large_steam_fluid_drill]",
                f'enabled = {str(phase["fluid_enabled"]).lower()}',
                f'weights = {toml_list(phase["fluid_weights"])}',
                "",
            )
        ),
        encoding="utf-8",
        newline="\n",
    )


def run_phase(phase: dict[str, object], command: list[str]) -> None:
    write_config(phase)
    environment = os.environ.copy()
    environment["GSE_EXPECTED_DIFFICULTY"] = str(phase["difficulty"])
    environment["GSE_EXPECTED_ORE_PLANT_ENABLED"] = str(phase["ore_enabled"]).lower()
    environment["GSE_EXPECTED_FLUID_DRILL_ENABLED"] = str(phase["fluid_enabled"]).lower()
    environment["GSE_EXPECTED_ORE_PLANT_WEIGHTS"] = ";".join(phase["ore_weights"])
    environment["GSE_EXPECTED_FLUID_DRILL_WEIGHTS"] = ";".join(phase["fluid_weights"])
    print(
        f'\n=== Configuration restart: {phase["name"]} phase '
        f'({phase["difficulty"]}) ===',
        flush=True,
    )
    subprocess.run(command, cwd=ROOT, env=environment, check=True)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Run GameTest servers with two startup-only configuration profiles."
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
