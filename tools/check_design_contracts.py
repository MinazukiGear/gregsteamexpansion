#!/usr/bin/env python3
"""Check representative numeric contracts shared by design docs and Java code."""

import json
import re
import sys
from collections import Counter
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


def game_test_counts() -> dict[str, int]:
    test_root = ROOT / "src/main/java/com/hoshino/gregsteamexpansion/gametest"
    counts = {}
    for path in sorted(test_root.glob("*.java")):
        count = len(re.findall(r"^\s*@GameTest\s*\(", path.read_text("utf-8"), re.MULTILINE))
        if count:
            counts[path.stem] = count
    if not counts:
        raise ContractError(f"{test_root}: no @GameTest methods were found")
    return counts


def check_game_test_inventory() -> None:
    counts = game_test_counts()
    total = sum(counts.values())
    documented_totals = {
        "README development command": captured_integers(
            "README.md",
            r"运行全部 GameTest（(\d+) 个，见下）",
            "README GameTest command count",
        )[0],
        "design index": captured_integers(
            "docs/design/README.md",
            r"全仓库现有 (\d+) 个 GameTest",
            "design index GameTest count",
        )[0],
        "automation interface status": captured_integers(
            "docs/design/automation-interfaces.md",
            r"已实现 (\d+) 项 GameTest",
            "automation interface GameTest count",
        )[0],
    }
    for label, documented in documented_totals.items():
        if documented != total:
            raise ContractError(
                f"{label}: documents {documented} GameTests, source has {total}"
            )

    engine_count = counts.get("GSESteamEngineTests", 0)
    documented_engine = captured_integers(
        "README.md",
        r"`GSESteamEngineTests`.*?（(\d+) 个）",
        "README steam-engine GameTest count",
    )[0]
    if documented_engine != engine_count:
        raise ContractError(
            "README steam-engine inventory: documents "
            f"{documented_engine} tests, source has {engine_count}"
        )
    print(
        f"ok: GameTest inventory = {total} total / "
        f"{engine_count} steam-engine tests"
    )


def crafting_ingredient_counts(relative: str) -> dict[str, int]:
    data = json.loads(source(relative))
    key = data.get("key")
    pattern = data.get("pattern")
    if not isinstance(key, dict) or not isinstance(pattern, list):
        raise ContractError(f"{relative}: expected a shaped crafting recipe")

    counts: dict[str, int] = {}
    for row in pattern:
        for symbol in row:
            if symbol == " ":
                continue
            ingredient = key.get(symbol)
            if not isinstance(ingredient, dict):
                raise ContractError(f"{relative}: pattern symbol {symbol!r} has no ingredient")
            if "item" in ingredient:
                spec = f'item:{ingredient["item"]}'
            elif "tag" in ingredient:
                spec = f'tag:{ingredient["tag"]}'
            else:
                raise ContractError(f"{relative}: unsupported ingredient for symbol {symbol!r}")
            counts[spec] = counts.get(spec, 0) + 1
    return counts


def recipe_profile(data: dict, relative: str) -> dict[str, object]:
    conditions = [
        condition
        for condition in data.get("conditions", [])
        if condition.get("type") == "gregsteamexpansion:difficulty_recipe_config"
    ]
    profile: dict[str, object] = {}
    for condition in conditions:
        key = condition.get("key")
        value = condition.get("value")
        if not isinstance(key, str) or key in profile or not isinstance(value, (bool, int)):
            raise ContractError(f"{relative}: malformed difficulty recipe config condition")
        profile[key] = value
    return profile


def canonical_ingredient(spec: str) -> str:
    if spec.startswith("tag:forge:"):
        path = spec.removeprefix("tag:forge:")
        forge_forms = {
            "double_plates": "double_plate",
            "plates": "plate",
            "small_gears": "small_gear",
            "gears": "gear",
            "rotors": "rotor",
            "springs": "spring",
            "frames": "frame",
            "normal_fluid_pipes": "normal_fluid_pipe",
        }
        for tag, form in forge_forms.items():
            prefix = f"{tag}/"
            if path.startswith(prefix):
                return f"material:{form}/{path.removeprefix(prefix)}"

    if spec.startswith("item:gtceu:"):
        path = spec.removeprefix("item:gtceu:")
        item_forms = (
            (r"double_(.+)_plate", "double_plate"),
            (r"small_(.+)_gear", "small_gear"),
            (r"(.+)_normal_fluid_pipe", "normal_fluid_pipe"),
            (r"(.+)_plate", "plate"),
            (r"(.+)_gear", "gear"),
            (r"(.+)_rotor", "rotor"),
            (r"(.+)_spring", "spring"),
            (r"(.+)_frame", "frame"),
        )
        for pattern, form in item_forms:
            match = re.fullmatch(pattern, path)
            if match:
                return f"material:{form}/{match.group(1)}"
    return spec


def crafting_route(relative: str) -> tuple[str, int, dict[str, object], Counter[str]]:
    data = json.loads(source(relative))
    result = data.get("result", {})
    item = result.get("item")
    count = result.get("count", 1)
    if not isinstance(item, str) or not isinstance(count, int) or count < 1:
        raise ContractError(f"{relative}: malformed crafting output")
    materials = Counter()
    for spec, amount in crafting_ingredient_counts(relative).items():
        if spec.startswith("tag:gtceu:tools/crafting_"):
            continue
        materials[canonical_ingredient(spec)] += amount
    return item, count, recipe_profile(data, relative), materials


def assembler_route(
    relative: str,
) -> tuple[str, int, dict[str, object], Counter[str], int, int, int, dict[str, int]]:
    data = json.loads(source(relative))
    outputs = data.get("outputs", {}).get("item", [])
    if len(outputs) != 1:
        raise ContractError(f"{relative}: expected exactly one item output")
    output = outputs[0].get("content", {})
    ingredient = output.get("ingredient", {})
    item = ingredient.get("item")
    count = output.get("count", 1)
    if output.get("type") != "gtceu:sized" or not isinstance(item, str) or not isinstance(count, int):
        raise ContractError(f"{relative}: malformed assembler output")

    materials = Counter()
    circuit_configs = []
    for entry in data.get("inputs", {}).get("item", []):
        content = entry.get("content", {})
        if content.get("type") == "gtceu:circuit":
            circuit_configs.append(content.get("configuration"))
            if entry.get("chance") != 0:
                raise ContractError(f"{relative}: programming circuit must be non-consumable")
            continue
        if content.get("type") != "gtceu:sized":
            raise ContractError(f"{relative}: unsupported assembler item input {content}")
        sized_ingredient = content.get("ingredient", {})
        if "item" in sized_ingredient:
            spec = f'item:{sized_ingredient["item"]}'
        elif "tag" in sized_ingredient:
            spec = f'tag:{sized_ingredient["tag"]}'
        else:
            raise ContractError(f"{relative}: malformed assembler item ingredient")
        materials[canonical_ingredient(spec)] += content.get("count", 1)
    if len(circuit_configs) != 1 or not isinstance(circuit_configs[0], int):
        raise ContractError(f"{relative}: expected exactly one programming circuit")

    fluids: dict[str, int] = {}
    for entry in data.get("inputs", {}).get("fluid", []):
        content = entry.get("content", {})
        amount = content.get("amount")
        values = content.get("value", [])
        if not isinstance(amount, int) or len(values) != 1 or set(values[0]) != {"tag"}:
            raise ContractError(f"{relative}: unsupported assembler fluid input {content}")
        tag = values[0]["tag"]
        fluids[tag] = fluids.get(tag, 0) + amount

    eu_inputs = data.get("tickInputs", {}).get("eu", [])
    if len(eu_inputs) != 1 or not isinstance(eu_inputs[0].get("content"), int):
        raise ContractError(f"{relative}: expected exactly one EU/t input")
    duration = data.get("duration")
    if not isinstance(duration, int):
        raise ContractError(f"{relative}: malformed duration")
    return (
        item,
        count,
        recipe_profile(data, relative),
        materials,
        circuit_configs[0],
        duration,
        eu_inputs[0]["content"],
        fluids,
    )


def check_acquisition_route_parity() -> None:
    recipe_root = "src/generated/resources/data/gregsteamexpansion/recipes"
    pairs: dict[str, tuple[int, int, int, Counter[str], dict[str, int], dict[str, object]]] = {}

    def add(
        name: str,
        circuit: int,
        duration: int,
        eut: int,
        hand_excess: dict[str, int] | None = None,
        fluids: dict[str, int] | None = None,
        profile: dict[str, object] | None = None,
    ) -> None:
        pairs[name] = (circuit, duration, eut, Counter(hand_excess or {}), fluids or {}, profile or {})

    for tier in ("bronze", "steel", "titanium", "tungstensteel"):
        add(f"boiler_room_{tier}", 7, 400, 16)
    add("large_steam_supply_hatch", 1, 200, 480)
    add("advanced_steam_exhaust_hatch", 2, 200, 480)
    electric_tiers = ("mv", "hv", "ev", "iv", "luv", "zpm", "uv")
    for index, tier in enumerate(electric_tiers, start=2):
        add(f"electric_ore_crusher_{tier}", index, 100, 16 * (1 << index))
    add("steam_fluid_input_hatch", 1, 100, 16)
    add("steam_fluid_output_hatch", 2, 100, 16)
    add("steam_air_intake_hatch", 3, 100, 16)

    for count in (1, 2, 3):
        count_profile = {"gtceuCasingsPerCraft": count}
        add(f"industrial_steam_casing_count_{count}", 6, 50, 16, profile=count_profile)

    for hard in (False, True):
        variant = "hard" if hard else "standard"
        plate = "double_plate" if hard else "plate"
        add(
            f"bronze_component_{variant}",
            6,
            50,
            16,
            {f"material:{plate}/bronze": 1, "material:spring/copper": 1},
            profile={"hardBronzeComponentRecipes": hard},
        )

        for count in (1, 2, 3):
            count_profile = {"gtceuCasingsPerCraft": count}
            grinding_profile = {"harderSteamGrindingBlockRecipes": hard, **count_profile}
            grinding_gear = "gear" if hard else "small_gear"
            add(
                f"steam_grinding_block_{variant}_count_{count}",
                4,
                100,
                16,
                {"material:plate/bronze": 1, f"material:{grinding_gear}/bronze": 1},
                profile=grinding_profile,
            )
            assembly_excess = (
                {"material:plate/bronze": 1, "material:double_plate/bronze": 1}
                if hard
                else {"material:plate/bronze": 2}
            )
            add(
                f"steam_assembly_block_{variant}_count_{count}", 4, 100, 16, assembly_excess,
                profile={"hardSteamAssemblyBlockRecipes": hard, **count_profile},
            )
            add(
                f"steam_circuit_assembly_block_{variant}_count_{count}",
                5,
                100,
                16,
                fluids={"forge:rubber": 288},
                profile={"hardSteamCircuitAssemblyBlockRecipes": hard, **count_profile},
            )
            add(
                f"steam_mixing_block_{variant}_count_{count}", 6, 100, 16, assembly_excess,
                profile={"hardSteamMixingBlockRecipes": hard, **count_profile},
            )

    assembler_root = ROOT / recipe_root / "assembler"
    actual_names = {path.stem for path in assembler_root.glob("*.json")}
    if actual_names != set(pairs):
        raise ContractError(
            "assembler acquisition route inventory differs from audited pairs: "
            f"missing={sorted(set(pairs) - actual_names)}, extra={sorted(actual_names - set(pairs))}"
        )

    for name, (circuit, duration, eut, hand_excess, expected_fluids, expected_profile) in pairs.items():
        shaped_path = f"{recipe_root}/shaped/{name}.json"
        assembler_path = f"{recipe_root}/assembler/{name}.json"
        hand_item, hand_count, hand_difficulty, hand_materials = crafting_route(shaped_path)
        (
            assembler_item,
            assembler_count,
            assembler_difficulty,
            assembler_materials,
            assembler_circuit,
            assembler_duration,
            assembler_eut,
            assembler_fluids,
        ) = assembler_route(assembler_path)

        if (hand_item, hand_count, hand_difficulty) != (
            assembler_item,
            assembler_count,
            assembler_difficulty,
        ) or hand_difficulty != expected_profile:
            raise ContractError(
                f"{name}: hand/assembler output or recipe profile differs: "
                f"hand={(hand_item, hand_count, hand_difficulty)}, "
                f"assembler={(assembler_item, assembler_count, assembler_difficulty)}"
            )
        configured_count = expected_profile.get("gtceuCasingsPerCraft")
        if configured_count is not None and hand_count != configured_count:
            raise ContractError(
                f"{name}: output count {hand_count} differs from gtceuCasingsPerCraft {configured_count}"
            )

        comparable_assembler = assembler_materials.copy()
        if expected_fluids == {"forge:rubber": 288}:
            comparable_assembler["material:plate/rubber"] += 2
        if hand_materials != comparable_assembler + hand_excess:
            raise ContractError(
                f"{name}: material relationship differs: hand={dict(hand_materials)}, "
                f"assembler={dict(assembler_materials)}, expected hand excess={dict(hand_excess)}"
            )
        if assembler_fluids != expected_fluids:
            raise ContractError(
                f"{name}: assembler fluids are {assembler_fluids}, expected {expected_fluids}"
            )
        actual_parameters = (assembler_circuit, assembler_duration, assembler_eut)
        expected_parameters = (circuit, duration, eut)
        if actual_parameters != expected_parameters:
            raise ContractError(
                f"{name}: assembler circuit/duration/EU/t is {actual_parameters}, "
                f"expected {expected_parameters}"
            )
    print(f"ok: acquisition route parity = {len(pairs)} hand/assembler pairs")


def check_acquisition_tier_boundaries() -> None:
    recipe_root = "src/generated/resources/data/gregsteamexpansion/recipes"
    large_hatch = f"{recipe_root}/shaped/large_steam_supply_hatch.json"
    advanced_exhaust_hatch = f"{recipe_root}/shaped/advanced_steam_exhaust_hatch.json"
    expected_large_hatch = {
        "item:gtceu:bronze_drum": 4,
        "tag:gtceu:circuits/hv": 4,
        "item:gregsteamexpansion:steam_supply_hatch": 1,
    }
    actual_large_hatch = crafting_ingredient_counts(large_hatch)
    if actual_large_hatch != expected_large_hatch:
        raise ContractError(
            f"large steam supply hatch tier boundary: {actual_large_hatch}, "
            f"expected {expected_large_hatch}"
        )

    expected_advanced_exhaust_hatch = {
        "item:gtceu:bronze_drum": 4,
        "tag:gtceu:circuits/hv": 4,
        "item:gregsteamexpansion:steam_exhaust_hatch": 1,
    }
    actual_advanced_exhaust_hatch = crafting_ingredient_counts(advanced_exhaust_hatch)
    if actual_advanced_exhaust_hatch != expected_advanced_exhaust_hatch:
        raise ContractError(
            f"advanced steam exhaust hatch tier boundary: {actual_advanced_exhaust_hatch}, "
            f"expected {expected_advanced_exhaust_hatch}"
        )

    electric_tiers = {
        "mv": ("aluminium_plate", "aluminium_frame"),
        "hv": ("stainless_steel_plate", "stainless_steel_frame"),
        "ev": ("titanium_plate", "titanium_frame"),
        "iv": ("tungsten_steel_plate", "tungsten_steel_frame"),
        "luv": ("rhodium_plated_palladium_plate", "ruridit_frame"),
        "zpm": ("naquadah_alloy_plate", "iridium_frame"),
        "uv": ("darmstadtium_plate", "naquadah_alloy_frame"),
    }
    electric_paths = set()
    for tier, (plate, frame) in electric_tiers.items():
        relative = f"{recipe_root}/shaped/electric_ore_crusher_{tier}.json"
        electric_paths.add(relative)
        expected = {
            f"item:gtceu:{plate}": 6,
            f"tag:gtceu:circuits/{tier}": 2,
            f"item:gtceu:{frame}": 1,
        }
        actual = crafting_ingredient_counts(relative)
        if actual != expected:
            raise ContractError(
                f"{tier.upper()} electric ore crusher tier boundary: {actual}, expected {expected}"
            )

    forbidden_markers = (
        "circuits/",
        "_circuit",
        "electric_",
        "battery",
        "vacuum_tube",
        "aluminium",
        "stainless_steel",
        "titanium",
        "tungsten",
        "chrome",
        "rhodium",
        "ruridit",
        "iridium",
        "naquadah",
        "darmstadtium",
        "osmium",
        "europium",
        "neutronium",
        "tritanium",
    )
    intentional_late_routes = electric_paths | {
        large_hatch,
        advanced_exhaust_hatch,
        f"{recipe_root}/shaped/boiler_room_titanium.json",
        f"{recipe_root}/shaped/boiler_room_tungstensteel.json",
    }
    shaped_root = ROOT / recipe_root / "shaped"
    audited = 0
    audit_paths = list(shaped_root.glob("*.json")) + [
        ROOT / recipe_root / "large_coke_oven.json",
        ROOT / recipe_root / "large_coke_oven_hatch.json",
    ]
    for path in sorted(audit_paths):
        relative = path.relative_to(ROOT).as_posix()
        if relative in intentional_late_routes:
            continue
        for ingredient in crafting_ingredient_counts(relative):
            if ingredient.startswith("item:gregsteamexpansion:"):
                continue
            if any(marker in ingredient for marker in forbidden_markers) or re.search(
                r":(?:ulv|lv|mv|hv|ev|iv|luv|zpm|uv|uhv|uev|uiv|uxv|opv|max)_",
                ingredient,
            ):
                raise ContractError(
                    f"{relative}: early steam/steel acquisition route uses late-tier ingredient {ingredient}"
                )
        audited += 1
    print(
        "ok: acquisition tier boundaries = "
        f"{audited} steam/steel recipes + 2 HV hatches + {len(electric_tiers)} electric tiers"
    )


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
    try:
        check_game_test_inventory()
    except ContractError as error:
        errors.append(str(error))
    try:
        check_acquisition_tier_boundaries()
    except ContractError as error:
        errors.append(str(error))
    try:
        check_acquisition_route_parity()
    except ContractError as error:
        errors.append(str(error))
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
    print(
        f"Design contracts aligned: {len(checks)} representative numeric checks "
        "plus acquisition tier and route-parity audits."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ContractError as error:
        print(f"Design contract verification failed: {error}", file=sys.stderr)
        raise SystemExit(1) from error
