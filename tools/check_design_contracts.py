#!/usr/bin/env python3
"""Check representative numeric contracts shared by design docs and Java code."""

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class ContractError(RuntimeError):
    pass


def source(relative: str) -> str:
    return (ROOT / relative).read_text("utf-8")


def captured_integers(relative: str, pattern: str, label: str) -> tuple[int, ...]:
    matches = list(re.finditer(pattern, source(relative), re.DOTALL))
    if len(matches) != 1:
        raise ContractError(
            f"{label}: expected one match in {relative}, found {len(matches)}"
        )
    return tuple(int(value) for value in matches[0].groups())


def java_string_array(relative: str, name: str) -> list[str]:
    body = re.search(
        rf"\b{re.escape(name)}\s*=\s*\{{(?P<body>.*?)\n\s*\}};",
        source(relative),
        re.DOTALL,
    )
    if body is None:
        raise ContractError(f"{relative}: Java string array {name} was not found")
    values = re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', body.group("body"))
    if not values:
        raise ContractError(f"{relative}: Java string array {name} is empty")
    return values


def rectangular_dimensions(layers: list[list[str]], label: str) -> tuple[int, int, int]:
    if not layers or not layers[0] or not layers[0][0]:
        raise ContractError(f"{label}: layout is empty")
    height = len(layers)
    depth = len(layers[0])
    width = len(layers[0][0])
    if any(len(layer) != depth for layer in layers):
        raise ContractError(f"{label}: layout layers have different depths")
    if any(len(row) != width for layer in layers for row in layer):
        raise ContractError(f"{label}: layout rows have different widths")
    return width, depth, height


def java_cube_dimensions(relative: str, method_name: str) -> tuple[int, int, int]:
    initializer = re.search(
        rf"private static String\[\]\[\]\s+{re.escape(method_name)}\(\)\s*\{{"
        rf".*?return\s+new\s+String\[\]\[\]\s*\{{(?P<body>.*?)\n\s*\}};",
        source(relative),
        re.DOTALL,
    )
    if initializer is None:
        raise ContractError(f"{relative}: layout initializer in {method_name} was not found")
    layers = []
    for layer_body in re.findall(r"\{([^{}]+)\}", initializer.group("body")):
        layers.append(re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', layer_body))
    return rectangular_dimensions(layers, f"{relative}#{method_name}")


def coke_oven_dimensions() -> tuple[int, int, int]:
    relative = "src/main/java/com/hoshino/gregsteamexpansion/cokeoven/LargeCokeOvenStructures.java"
    array = re.search(
        r"LAYER_BLOCKS\s*=\s*\{(?P<body>.*?)\n\s*\};",
        source(relative),
        re.DOTALL,
    )
    if array is None:
        raise ContractError(f"{relative}: LAYER_BLOCKS was not found")
    layers = []
    for block in re.findall(r'"""(.*?)"""', array.group("body"), re.DOTALL):
        layers.append([line.strip() for line in block.strip().splitlines()])
    width, depth, height = rectangular_dimensions(layers, f"{relative}#LAYER_BLOCKS")
    # The coke-oven design writes its bounding box as width × height × depth.
    return width, height, depth


def boiler_room_dimensions() -> tuple[int, int, int]:
    relative = "src/main/java/com/hoshino/gregsteamexpansion/registry/GSEBoilerPatterns.java"
    bottom = java_string_array(relative, "BOTTOM_LAYER")
    text = source(relative)
    method = re.search(
        r"public static BlockPattern createPattern\(.*?(?P<body>return .*?\.build\(\);)",
        text,
        re.DOTALL,
    )
    if method is None:
        raise ContractError(f"{relative}: createPattern body was not found")
    height = len(re.findall(r"\.aisle\(", method.group("body")))
    return rectangular_dimensions([bottom] * height, f"{relative}#createPattern")


def blast_furnace_dimensions() -> tuple[int, int, int]:
    relative = "src/main/java/com/hoshino/gregsteamexpansion/registry/GSEProcessorPatterns.java"
    hearth = java_string_array(relative, "BLAST_HEARTH_LAYER")
    height = captured_integers(
        relative,
        r"String\[\]\[\]\s+layers\s*=\s*new String\[(\d+)\]\[\];",
        "blast furnace layer count",
    )[0]
    return rectangular_dimensions([hearth] * height, f"{relative}#blastFurnaceLayers")


def heat_storage_furnace_dimensions() -> tuple[int, ...]:
    relative = "src/main/java/com/hoshino/gregsteamexpansion/registry/GSEFurnacePatterns.java"
    text = source(relative)
    widths_match = re.search(r"WIDTHS\s*=\s*\{([^}]+)\}", text)
    if widths_match is None:
        raise ContractError(f"{relative}: WIDTHS was not found")
    widths = tuple(int(value) for value in re.findall(r"\d+", widths_match.group(1)))
    repeats = captured_integers(
        relative,
        r"MIN_MIDDLE_REPEATS\s*=\s*(\d+);.*?MAX_MIDDLE_REPEATS\s*=\s*(\d+);",
        "heat-storage furnace repeat range",
    )
    # bottom + repeatable middle layers + exhaust layer + top
    return widths + (repeats[0] + 3, repeats[1] + 3)


def integer_return(relative: str, method_name: str) -> int:
    return captured_integers(
        relative,
        rf"public int {re.escape(method_name)}\(\)\s*\{{.*?return\s+(\d+)\s*;",
        f"{relative}#{method_name}",
    )[0]


def assembler_parallel_mapping() -> tuple[int, ...]:
    relative = (
        "src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/"
        "AbstractSteamAssemblerMachine.java"
    )
    method = re.search(
        r"public int maximumParallel\(\)\s*\{(?P<body>.*?)\n\s*\}",
        source(relative),
        re.DOTALL,
    )
    if method is None:
        raise ContractError(f"{relative}: maximumParallel was not found")
    cases = {
        int(key): int(value)
        for key, value in re.findall(r"case\s+(\d+)\s*->\s*(\d+)", method.group("body"))
    }
    default = re.search(r"default\s*->\s*(\d+)", method.group("body"))
    if sorted(cases) != [1, 2, 3, 4] or default is None:
        raise ContractError(f"{relative}: assembler parallel switch is incomplete")
    return (int(default.group(1)), *(cases[index] for index in range(1, 5)))


def check_contract(
    name: str,
    documented: tuple[int, ...],
    implemented: tuple[int, ...],
    expected: tuple[int, ...],
) -> None:
    if documented != expected:
        raise ContractError(f"{name}: design has {documented}, expected {expected}")
    if implemented != expected:
        raise ContractError(f"{name}: code has {implemented}, expected {expected}")


def main() -> int:
    crusher_doc = "docs/design/steam-crushers.md"
    crusher_code = "src/main/java/com/hoshino/gregsteamexpansion/registry/GSECrusherPatterns.java"
    checks = [
        (
            "boiler-room structure",
            captured_integers(
                "docs/design/boiler-room.md",
                r"结构尺寸（用户拍板）.*?长\s*(\d+)\s*×\s*宽\s*(\d+)\s*×\s*高\s*(\d+)",
                "boiler-room structure design",
            ),
            boiler_room_dimensions(),
            (7, 11, 7),
        ),
        (
            "steam-crusher structures",
            captured_integers(
                crusher_doc,
                r"蒸汽粉碎机采用固定 `(\d+)×(\d+)×(\d+)` 外接尺寸.*?"
                r"大型蒸汽粉碎机采用固定 `(\d+)×(\d+)×(\d+)` 外接尺寸",
                "steam-crusher structure designs",
            ),
            java_cube_dimensions(crusher_code, "smallLayers")
            + java_cube_dimensions(crusher_code, "largeLayers"),
            (3, 3, 3, 7, 7, 9),
        ),
        (
            "large coke-oven structure",
            captured_integers(
                "docs/design/coke-ovens.md",
                r"固定结构以三个横向炉室、`(\d+)×(\d+)×(\d+)` 包围范围",
                "large coke-oven structure design",
            ),
            coke_oven_dimensions(),
            (7, 7, 5),
        ),
        (
            "large steam blast-furnace structure",
            captured_integers(
                "docs/design/large-steam-blast-furnace.md",
                r"结构 `(\d+)×(\d+)×(\d+)` 三段收分巨塔",
                "large steam blast-furnace structure design",
            ),
            blast_furnace_dimensions(),
            (13, 13, 15),
        ),
        (
            "heat-storage furnace size range",
            captured_integers(
                "docs/design/large-heat-storage-steam-furnace.md",
                r"外接宽度只能为 `(\d+)×\d+`、`(\d+)×\d+` 或 `(\d+)×\d+`，"
                r"总高度允许 `(\d+)–(\d+)`",
                "heat-storage furnace size design",
            ),
            heat_storage_furnace_dimensions(),
            (7, 11, 15, 6, 18),
        ),
        (
            "steam-crusher parallel caps",
            captured_integers(
                crusher_doc,
                r"蒸汽粉碎机显示 `(\d+)`，大型蒸汽粉碎机显示 `(\d+)`",
                "steam-crusher parallel design",
            ),
            (
                integer_return(
                    "src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/"
                    "crusher/SteamCrusherMachine.java",
                    "maximumParallel",
                ),
                integer_return(
                    "src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/"
                    "crusher/LargeSteamCrusherMachine.java",
                    "maximumParallel",
                ),
            ),
            (8, 64),
        ),
        (
            "large coke-oven parallel cap",
            captured_integers(
                "docs/design/coke-ovens.md",
                r"大型焦炉的最大并行固定为 `(\d+)`，Easy",
                "large coke-oven parallel design",
            ),
            captured_integers(
                "src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/"
                "largecokeoven/LargeCokeOvenRecipeLogic.java",
                r"MAX_PARALLEL\s*=\s*(\d+);",
                "large coke-oven parallel code",
            ),
            (6,),
        ),
        (
            "large steam blast-furnace parallel cap",
            captured_integers(
                "docs/design/large-steam-blast-furnace.md",
                r"最大并行固定 `(\d+)`",
                "large steam blast-furnace parallel design",
            ),
            (
                integer_return(
                    "src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/"
                    "processor/LargeSteamBlastFurnaceMachine.java",
                    "maximumParallel",
                ),
            ),
            (96,),
        ),
        (
            "steam assembler parallel mapping",
            captured_integers(
                "docs/design/large-steam-assembler.md",
                r"最大并行由槽内组装机堆叠数量决定：空槽 `(\d+)`；"
                r"堆叠 `1 / 2 / 3 / 4` 台同等级组装机时为 "
                r"`(\d+) / (\d+) / (\d+) / (\d+)`",
                "steam assembler parallel design",
            ),
            assembler_parallel_mapping(),
            (1, 2, 4, 8, 16),
        ),
    ]

    errors = []
    for name, documented, implemented, expected in checks:
        try:
            check_contract(name, documented, implemented, expected)
        except ContractError as error:
            errors.append(str(error))
        else:
            print(f"ok: {name} = {expected}")

    if errors:
        print("Design contract verification failed:", file=sys.stderr)
        for error in errors:
            print(f"  {error}", file=sys.stderr)
        return 1
    print(f"Design contracts aligned: {len(checks)} representative checks.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ContractError as error:
        print(f"Design contract verification failed: {error}", file=sys.stderr)
        raise SystemExit(1) from error
