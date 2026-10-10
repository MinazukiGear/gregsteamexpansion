#!/usr/bin/env python3
"""Export the default steam economy from local Java and generated recipes.

This is a planning model, not a runtime benchmark. Formula guards deliberately
fail when an implementation changes: review the model before regenerating.
Only the Python standard library is required (Python 3.10+).
"""

import argparse
from collections import Counter
from fractions import Fraction
import hashlib
import json
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
JAVA = "src/main/java/com/hoshino/gregsteamexpansion/"
MULTI = JAVA + "machine/multiblock/"
RECIPES = "src/generated/resources/data/gregsteamexpansion/recipes/shaped/"
OUTPUT = ROOT / "docs/balance"
TIERS = ("OFF", "EASY", "NORMAL", "EXPERT")
LABELS = {"OFF": "关闭难度", "EASY": "Easy", "NORMAL": "Normal", "EXPERT": "Expert"}
READS: dict[str, str] = {}
PROCESSORS = {
    "SteamCompressorMachine": "蒸汽压缩机", "SteamExtractorMachine": "蒸汽提取机",
    "SteamForgeMachine": "蒸汽锻压机", "SteamChemicalBathMachine": "蒸汽化学浸洗机",
    "SteamCentrifugeMachine": "蒸汽离心机", "LargeSteamCentrifugeMachine": "大型蒸汽离心机",
    "LargeSteamThermalCentrifugeMachine": "大型蒸汽热力离心机", "LargeSteamOreWasherMachine": "大型蒸汽洗矿厂",
    "LargeSteamMaceratorMachine": "大型蒸汽研磨厂", "LargeSteamMixerMachine": "大型蒸汽搅拌机",
}
ACQUISITIONS = (
    "lp_steam_mixed_fuel_boiler", "hp_steam_mixed_fuel_boiler", "steam_crusher", "large_steam_crusher",
    "large_steam_assembler", "large_steam_circuit_assembler", "large_steam_blast_furnace",
    "large_heat_storage_steam_furnace", "boiler_room_bronze", "boiler_room_steel",
    "boiler_room_titanium", "boiler_room_tungstensteel", "large_steam_ore_plant", "large_steam_fluid_drill",
    "large_steam_tank", "steam_supply_hatch", "large_steam_supply_hatch", "advanced_steam_exhaust_hatch",
    "steam_compressor", "steam_extractor", "steam_forge", "steam_chemical_bath", "steam_centrifuge",
    "large_steam_centrifuge", "large_steam_thermal_centrifuge", "large_steam_ore_washer",
    "large_steam_macerator", "large_steam_mixer",
)


def read(relative):
    text = (ROOT / relative).read_text(encoding="utf-8")
    READS[relative] = text
    return text


def match(relative, pattern):
    found = re.findall(pattern, read(relative), re.DOTALL)
    if len(found) != 1:
        raise ValueError(f"{relative}: expected one match for {pattern!r}, got {len(found)}")
    return found[0]


def number(relative, name):
    value = match(relative, rf"\b{re.escape(name)}\s*=\s*([\d_.]+)[LlFfDd]?\s*;")
    return Fraction(value.replace("_", ""))


def array(relative, name):
    body = match(relative, rf"\b{re.escape(name)}\s*=\s*\{{([^}}]+)\}};")
    return [int(value.strip().replace("_", "")) for value in body.split(",")]


def guard(relative, snippet):
    if re.sub(r"\s+", "", snippet) not in re.sub(r"\s+", "", read(relative)):
        raise ValueError(f"{relative}: formula changed; review model: {snippet}")


def ceil(value):
    value = Fraction(value)
    return -(-value.numerator // value.denominator)


def fmt(value):
    value = Fraction(value)
    if value.denominator == 1:
        return f"{value.numerator:,}"
    return f"{float(value):,.4f}".rstrip("0").rstrip(".")


def profiles():
    relative = JAVA + "difficulty/GSEDifficultyProfile.java"
    body = match(relative, r"public record GSEDifficultyProfile\((.*?)\)\s*\{")
    fields = re.findall(r"\b(?:int|double|boolean)\s+(\w+)", body)
    result = {}
    for tier in TIERS[1:]:
        body = match(relative, rf"case {tier} -> new GSEDifficultyProfile\((.*?)\);")
        tokens = [token.strip() for token in body.split(",")]
        if len(fields) != len(tokens):
            raise ValueError(f"{tier}: record fields and constructor arguments differ")
        result[tier] = {
            name: token == "true" if token in ("true", "false") else Fraction(token)
            for name, token in zip(fields, tokens)
        }
    result["OFF"] = result["NORMAL"].copy()
    # Disabled integration preserves GTCEu's setting, independently of GSE's
    # Normal profile. This report uses the pinned upstream default (2); packs
    # with a changed gtceu.yaml must recalculate their structure quantities.
    if match("gradle.properties", r"(?m)^gtceu_version=([^\r\n]+)\r?$").strip() != "7.5.3":
        raise ValueError("Review disabled-difficulty casing defaults for the new GTCEu version")
    result["OFF"]["gtceuCasingsPerCraft"] = Fraction(2)
    for name in ("steamOutputMultiplier", "singleblockSteamCacheMultiplier",
                 "oreCrushingMultiplier", "boilerRoomSteamOutputMultiplier",
                 "assemblerOutputMultiplier", "voidProducerOutputMultiplier"):
        result["OFF"][name] = Fraction(1)
    result["OFF"].update(preheatCostPercent=Fraction(100),
                         preheatIntervalTicks=Fraction(5), processingSteamPercent=Fraction(100))
    state = JAVA + "difficulty/GSEDifficultyState.java"
    guard(state, "startupProfile = enabled ? profile : GSEDifficultyProfile.defaults(Difficulty.NORMAL);")
    guard(state, "ConfigHolder.init(); return ConfigHolder.INSTANCE.recipes.casingsPerCraft;")
    for name in ("steamOutputMultiplier", "boilerRoomSteamOutputMultiplier", "assemblerOutputMultiplier"):
        guard(state, f"return isEnabled(remote) ? (float) currentProfile(remote).{name}() : 1.0F;")
    guard(state, "return isEnabled(remote) ? currentProfile(remote).voidProducerOutputMultiplier() : 1;")
    guard(state, "return isEnabled(remote) ? currentProfile(remote).preheatCostPercent() : 100;")
    guard(state, "return isEnabled(remote) ? currentProfile(remote).preheatIntervalTicks() : 5;")
    guard(state, "return isEnabled(remote) ? currentProfile(remote).processingSteamPercent() : 100;")
    return result


def ladder(name):
    relative = MULTI + "processor/AbstractSteamAssemblerMachine.java"
    body = match(relative, rf"private static double {name}\(int parallel\)\s*\{{(.*?)\}}")
    steps = [(int(p), Fraction(v)) for p, v in re.findall(r"if \(parallel >= (\d+)\) return ([\d.]+);", body)]
    fallback = re.findall(r"return ([\d.]+);", body)[-1]
    if len(steps) != 4:
        raise ValueError(f"{name}: unexpected assembler ladder")
    return lambda p: next((v for threshold, v in steps if p >= threshold), Fraction(fallback))


def recipe_costs(profile):
    names = ACQUISITIONS
    prefixes = ("bronze_component", "industrial_steam_casing", "steam_grinding_block",
                "steam_assembly_block", "steam_circuit_assembly_block", "steam_mixing_block")
    selected = {}
    for path in sorted((ROOT / RECIPES).glob("*.json")):
        if path.stem not in names and not path.stem.startswith(prefixes):
            continue
        data = json.loads(read(path.relative_to(ROOT).as_posix()))
        active = True
        for condition in data.get("conditions", []):
            if condition["type"] != "gregsteamexpansion:difficulty_recipe_config":
                raise ValueError(f"{path.name}: unsupported recipe condition")
            active &= profile[condition["key"]] == condition["value"]
        if not active:
            continue
        materials, tools = Counter(), Counter()
        counts = Counter("".join(data["pattern"]))
        for symbol, ingredient in data["key"].items():
            if len(ingredient) != 1 or next(iter(ingredient)) not in ("item", "tag"):
                raise ValueError(f"{path.name}: unsupported ingredient")
            kind, resource = next(iter(ingredient.items()))
            target = tools if resource.startswith("gtceu:tools/") else materials
            target[f"{kind}:{resource}"] += counts[symbol]
        item = data["result"]["item"]
        if item in selected:
            raise ValueError(f"multiple active acquisition recipes for {item}")
        selected[item] = {"recipe": path.stem, "output": data["result"].get("count", 1),
                          "materials": dict(sorted(materials.items())), "tools": dict(sorted(tools.items()))}
    if len(selected) != len(names) + len(prefixes):
        raise ValueError(f"incomplete material inventory: {len(selected)} items")
    return selected


def build_model():
    p = profiles()
    assembler = MULTI + "processor/AbstractSteamAssemblerMachine.java"
    processor = MULTI + "processor/AbstractSteamProcessorMachine.java"
    circuit = MULTI + "processor/LargeSteamCircuitAssemblerMachine.java"
    blast = MULTI + "processor/LargeSteamBlastFurnaceMachine.java"
    drill = MULTI + "voidproducer/LargeSteamFluidDrillMachine.java"
    thermal = MULTI + "furnace/FurnaceThermalLogic.java"
    furnace = MULTI + "LargeHeatStorageSteamFurnaceMachine.java"
    boiler = MULTI + "BoilerRoomMachine.java"
    boiler_thermal = MULTI + "BoilerRoomThermalLogic.java"
    overclock = MULTI + "LargeSteamOverclock.java"
    advanced = MULTI + "part/AdvancedSteamExhaustHatchMachine.java"
    guards = {
        assembler: ["Math.ceil(recipe.duration * DURATION_MULTIPLIER * timeScale(parallel))",
                    "Math.ceil(eu * (double) STEAM_PER_EU_MB * steamScale(parallel))"],
        circuit: ["Math.ceil(duration * SPECIALIZATION_COST_MULTIPLIER)",
                  "* parallel * batchSpecializationMultiplier;"],
        blast: ["((long) recipe.duration * percent + 99L) / 100L",
                "(product + NORMAL_MAX_PARALLEL - 1) / NORMAL_MAX_PARALLEL",
                "hotBlastHeat = Math.max(0, boundedHotBlastHeat() - heat);",
                "hotBlastHeat = Math.min(HOT_BLAST_HEAT_CAPACITY, boundedHotBlastHeat() + heat);"],
        boiler_thermal: ["temperature * (double) throttle / 20.0 * BoilerRoomMachine.CO_FIRING_MULTIPLIER * difficultyMultiplier",
                         "cycleTicks * load / (failureHours * TICKS_PER_HOUR)"],
        thermal: ["((long) width * width - 4) * height", "formedVolume(width, height) * 2 * preheatCostPercent",
                  "maxTemperature(width) * 3 / 5", "1 / (1 + 3 * volume * volume)",
                  "1 + (2 + 22 * volume * volume * volume) * heat * heat"],
        furnace: ["Math.ceil(baseEnergy * 2.0 * parallel * discount * processingMultiplier)",
                  "Math.ceil(recipe.duration / speed)", "Math.ceil((double) totalSteam / normalDuration)"],
    }
    for relative, snippets in guards.items():
        for snippet in snippets:
            guard(relative, snippet)
    duration_factor = number(processor, "DURATION_MULTIPLIER")
    steam_per_eu = number(processor, "STEAM_PER_EU_MB")
    spec_cost = number(circuit, "SPECIALIZATION_COST_MULTIPLIER")
    steam_scale, time_scale = ladder("steamScale"), ladder("timeScale")
    oc_steam = number(overclock, "STEAM_PER_TICK_MULTIPLIER")
    oc_divisor = number(overclock, "DURATION_DIVISOR")
    exhaust = number(advanced, "STEAM_PERCENT") / 100
    model = {"profiles": p, "materials": {}, "assembler": [], "circuit": [],
             "drill": [], "blast": [], "boilers": [], "furnace": [],
             "processors": [], "mixed_boilers": [], "ore_plant": [], "crushers": []}
    guard(processor, "Math.ceil(recipe.duration * DURATION_MULTIPLIER)")
    guard(processor, "return eu * STEAM_PER_EU_MB * parallel;")
    for machine, label in PROCESSORS.items():
        relative = MULTI + f"processor/{machine}.java"
        parallel = int(match(relative, r"int maximumParallel\(\)\s*\{(?:\s|//[^\n]*\n)*return (\d+);"))
        if any(name in read(relative) for name in ("batchDurationTicks(", "batchSteamPerTickMb(")):
            raise ValueError(f"{machine}: economy override requires a separate model")
        duration = ceil(100 * duration_factor)
        rate = 32 * steam_per_eu * parallel
        model["processors"].append(dict(machine=label, parallel=parallel, duration=duration,
            steam_per_tick=rate, output_per_tick=Fraction(parallel, duration), steam_per_item=rate * duration / parallel))
    mixed = JAVA + "machine/steam/MixedFuelBoilerMachine.java"
    guard(mixed, "/ 2 * difficultyMultiplier)")
    guard(mixed, "return getTotalSteamOutput() / 10.0;")
    ore_plant = MULTI + "voidproducer/LargeSteamOrePlantMachine.java"
    ore_stations = int(match(ore_plant, r"int stationCount\(\)\s*\{\s*return (\d+);"))
    ore_cycle = int(match(ore_plant, r"int cycleTicks\(\)\s*\{\s*return (\d+);"))
    ore_steam = int(match(ore_plant, r"long steamPerStationTick\(\)\s*\{\s*return (\d+);"))
    crusher = MULTI + "crusher/AbstractSteamCrusherMachine.java"
    guard(crusher, "partCollector.modifySteamConsumption((long) STEAM_PER_TICK_PER_PARALLEL * parallel)")
    migration = JAVA + "migration/OreCrushingMigration.java"
    guard(migration, "Math.round(mainStack.getCount() * multiplier)")
    for tier in TIERS:
        profile = p[tier]
        for pressure in ("LP", "HP"):
            for co_firing in (False, True):
                constant = f'{"CO_FIRING" if co_firing else "LIQUID"}_OUTPUT_{pressure}'
                rate = number(mixed, constant) / 20 * profile["steamOutputMultiplier"]
                model["mixed_boilers"].append(dict(tier=tier, pressure=pressure, co_firing=co_firing, steam_per_tick=rate))
        ore_output = ore_stations * number(ore_plant, "RAW_ORE_PER_DRAW") * profile["voidProducerOutputMultiplier"]
        model["ore_plant"].append(dict(tier=tier, steam_per_tick=ore_stations * ore_steam,
            raw_ore_per_tick=ore_output / ore_cycle, steam_per_raw_ore=ore_stations * ore_steam * ore_cycle / ore_output))
        # Representative raw-ore recipe with three guaranteed crushed outputs.
        crusher_items = (3 * profile["oreCrushingMultiplier"] * 2 + 1) // 2
        for machine, label in (("SteamCrusherMachine", "蒸汽粉碎机"), ("LargeSteamCrusherMachine", "大型蒸汽粉碎机")):
            relative = MULTI + f"crusher/{machine}.java"
            parallel = int(match(relative, r"int maximumParallel\(\)\s*\{\s*return (\d+);"))
            duration = number(crusher, "DURATION_TICKS")
            rate = number(crusher, "STEAM_PER_TICK_PER_PARALLEL") * parallel
            output = parallel * crusher_items
            model["crushers"].append(dict(tier=tier, machine=label, parallel=parallel,
                steam_per_tick=rate, output_per_tick=output / duration, steam_per_item=rate * duration / output))
        model["materials"][tier] = recipe_costs(profile)
        for parallel in (1, 2, 4, 8, 16):
            duration = ceil(100 * duration_factor * time_scale(parallel))
            rate = ceil(32 * steam_per_eu * steam_scale(parallel))
            output = parallel * profile["assemblerOutputMultiplier"]
            model["assembler"].append(dict(tier=tier, parallel=parallel, duration=duration,
                steam_per_tick=rate, output=output, steam_per_item=Fraction(rate * duration, output)))
        expected = 1 + profile["circuitAssemblerBonusChancePercent"] / 100 * profile["circuitAssemblerBonusMultiplier"]
        duration = ceil(ceil(100 * duration_factor * time_scale(16)) * spec_cost)
        rate = ceil(32 * steam_per_eu * steam_scale(16))
        output = 16 * profile["assemblerOutputMultiplier"] * expected
        model["circuit"].append(dict(tier=tier, expected_multiplier=expected,
            throughput_ratio=expected / spec_cost, unit_steam_ratio=spec_cost / expected,
            expected_output=output, duration=duration, steam_per_item=rate * duration / output))
        stations = int(match(drill, r"int stationCount\(\)\s*\{\s*return (\d+);"))
        cycle = int(match(drill, r"int cycleTicks\(\)\s*\{\s*return (\d+);"))
        station_steam = int(match(drill, r"long steamPerStationTick\(\)\s*\{\s*return (\d+);"))
        output = stations * number(drill, "FLUID_PER_DRAW_MB") * profile["voidProducerOutputMultiplier"]
        for efficient in (False, True):
            for oc in (False, True):
                duration = ceil(cycle / oc_divisor) if oc else cycle
                rate = ceil(stations * station_steam * (exhaust if efficient else 1)) * (oc_steam if oc else 1)
                model["drill"].append(dict(tier=tier, advanced_exhaust=efficient, overclock=oc,
                    duration=duration, steam_per_tick=rate, output_per_tick=output / duration,
                    output_per_hour=output / duration * 72_000, steam_per_mb=rate * duration / output))
        normal_p = int(number(blast, "NORMAL_MAX_PARALLEL"))
        high_p = int(number(blast, "HIGH_CHARGE_MAX_PARALLEL"))
        duration = ceil(100 * profile["blastFurnaceMasteredDurationPercent"] / 100)
        reset = ceil(Fraction(duration * (high_p - normal_p), normal_p)) * 2
        cycle = duration * 2 + reset
        model["blast"].append(dict(tier=tier, mastered_duration=duration,
            normal_output_per_tick=Fraction(normal_p, duration), high_cycle_ticks=cycle,
            high_output_per_tick=Fraction(high_p * 2, cycle),
            high_steam_per_operation=number(blast, "HIGH_CHARGE_STEAM_PER_TICK_MB") * cycle / (high_p * 2),
            normal_steam_per_operation=number(blast, "STEAM_PER_TICK_PER_PARALLEL_MB") * duration))
        for i, temperature in enumerate(array(boiler, "MAX_TEMPERATURES")):
            rate = temperature * Fraction(100, 20) * number(boiler, "CO_FIRING_MULTIPLIER") * profile["boilerRoomSteamOutputMultiplier"]
            loss = profile["boilerRoomScaleLossStage3Percent"]
            model["boilers"].append(dict(tier=tier, metal=("青铜", "钢", "钛", "钨钢")[i],
                clean_steam_per_tick=rate, stage3_steam_per_tick=rate * (100 - loss) / 100,
                ideal_heat_seconds=Fraction(temperature * array(boiler, "HEAT_INTERVALS")[i], 20)))
        max_temps = {int(w): int(t) for w, t in re.findall(r"case (\d+) -> (\d+);", read(thermal))}
        default_temp = int(match(thermal, r"default -> (\d+);"))
        cold = int(number(thermal, "COLD_TEMPERATURE"))
        for width, height in ((7, 6), (11, 12), (15, 18)):
            volume = (width * width - 4) * height
            maximum = max_temps.get(width, default_temp)
            degrees = maximum * 3 // 5 - cold
            progress = max(Fraction(0), min(Fraction(1), Fraction(volume - 270, 3978 - 270)))
            speed = 3 + 22 * progress ** 3
            discount = 1 / (1 + 3 * progress ** 2)
            batch_duration = ceil(100 / speed)
            nominal_total = ceil(100 * 32 * 2 * discount * profile["processingSteamPercent"] / 100)
            actual_total = ceil(Fraction(nominal_total, batch_duration)) * batch_duration
            model["furnace"].append(dict(tier=tier, width=width, height=height,
                ideal_start_steam=ceil(volume * 2 * profile["preheatCostPercent"] / 100) * degrees,
                ideal_start_seconds=degrees * profile["preheatIntervalTicks"] / 20,
                full_heat_speed=speed, nominal_steam_factor=discount * profile["processingSteamPercent"] / 100,
                example_batch_ticks=batch_duration, example_steam_per_item=actual_total))
    # These sources define integration boundaries described in the report.
    for relative in ("SteamThrottle.java", "SteamPartCollector.java", "BoilerRoomFuelSync.java",
                     "voidproducer/AbstractSteamVoidMachine.java", "part/SteamSupplyHatchPartMachine.java",
                     "part/LargeSteamSupplyHatchPartMachine.java"):
        read(MULTI + relative)
    read(JAVA + "difficulty/GSEDifficultyConfig.java")
    read(JAVA + "data/GSERecipes.java")
    model["source_fingerprints"] = {name: hashlib.sha256(text.encode()).hexdigest()
                                    for name, text in sorted(READS.items())}
    return model


def table(headers, rows):
    return "\n".join(["| " + " | ".join(headers) + " |", "| " + " | ".join(["---"] * len(headers)) + " |"]
                     + ["| " + " | ".join(map(str, row)) + " |" for row in rows])


def render(m):
    parts = ["# 蒸汽经济基线（自动生成）\n",
        "由 `python tools/export_steam_economy.py` 从本仓库 Java 默认档位、常量与已生成获取配方导出。"
        "核对命令为 `python tools/export_steam_economy.py --check`。修改公式后须先复核模型再重新生成。\n",
        "## 比较口径\n",
        "以下为默认配置的理论批次结算，假设 20 TPS、供汽/辅料充足、输入连续且输出畅通。"
        "不包含管网限流、搜索/提交批次的调度间隔、缺料、卸载或人为停机；实际持续产能通常更低。"
        "随机增产列为长期期望，不能保证单批结果。关闭难度另列：倍率回到 1，但电路专精、高炉熟练度与水垢仍用 Normal 基线。"
        "整合包自定义参数、权重和数据包配方需另算；关闭难度时的 GTCEu 外壳产量沿用上游配置。\n",
        "## 三档参数\n"]
    keys = [("GTCEu 外壳产量/次", "gtceuCasingsPerCraft"), ("上游/单方块产汽倍率", "steamOutputMultiplier"),
            ("锅炉房产汽倍率", "boilerRoomSteamOutputMultiplier"), ("组装产出倍率", "assemblerOutputMultiplier"),
            ("虚空产出倍率", "voidProducerOutputMultiplier"), ("熔炉预热成本 %", "preheatCostPercent"),
            ("熔炉每升一度 tick", "preheatIntervalTicks"), ("熔炉加工蒸汽 %", "processingSteamPercent"),
            ("粉碎产出倍率", "oreCrushingMultiplier"), ("满负载水垢报废小时", "boilerRoomScaleFailureHours")]
    parts.append(table(["参数", "Easy", "Normal", "Expert"],
                       [[label] + [fmt(m["profiles"][t][key]) for t in TIERS[1:]] for label, key in keys]))
    parts += ["\n## 获取成本与阶段\n",
        "表中材料为一次手工合成的直接输入；工具单列在 JSON 中，只占槽并产生耐久损耗，不作为消耗材料计数。"
        "控制器产 1，结构外壳、接口、升级槽中的电力机器和工具损耗另计，不能把控制器价格当整机造价。"
        "核心部件的条件配方已按四种模式选择；关闭难度使用 GSE Normal 的默认配方开关，"
        "外壳产量按 GTCEu 7.5.3 默认的 2 件计算，服务器修改上游配置后须另算。"
        "原料使用注册名，避免翻译歧义。\n"]
    def materials(cost):
        return "、".join(f"{count}×`{key.split(':', 1)[1]}`" for key, count in cost["materials"].items())
    parts.append(table(["档位", "核心部件", "每次产量", "直接材料"], [
        [LABELS[t], item.split(":", 1)[1], c["output"], materials(c)]
        for t in TIERS for item, c in m["materials"][t].items()
        if c["recipe"].startswith(("bronze_component", "industrial_steam_casing", "steam_grinding_block",
                                    "steam_assembly_block", "steam_circuit_assembly_block", "steam_mixing_block"))]))
    parts.append("")
    parts.append(table(["控制器/接口（四种模式相同）", "直接材料"], [
        [item.split(":", 1)[1], materials(c)] for item, c in m["materials"]["NORMAL"].items()
        if c["recipe"] in ACQUISITIONS]))
    parts += ["\n## 基础产汽、通用处理与采矿\n",
        "混合锅炉满温、连续燃料和水供给，按实际 10 tick 产汽周期折算。协同模式还消耗粉料。\n"]
    parts.append(table(["档位", "压力", "协同", "蒸汽 mB/t"], [
        [LABELS[r["tier"]], r["pressure"], "开" if r["co_firing"] else "关", fmt(r["steam_per_tick"])]
        for r in m["mixed_boilers"]]))
    parts += ["\n以下通用处理机用 100 tick、32 EU/t、1 个保证产物的归一化配方比较满并行；"
        "仅在对应类型确有可接受配方时应用，配方流体和副产物另计。普通排气、不超频、100% 节流，"
        "三档没有该家族独立产出倍率；并行增加的是产能，单位蒸汽不随并行降低。\n"]
    parts.append(table(["机器", "并行", "耗时 tick", "蒸汽 mB/t", "产物/t", "mB 汽/件"], [
        [r["machine"]] + [fmt(r[key]) for key in ("parallel", "duration", "steam_per_tick", "output_per_tick", "steam_per_item")]
        for r in m["processors"]]))
    parts += ["\n粉碎机另用固定 600 tick、每并行 200 mB/t。示例原配方保证产出 3 个同材质粉碎矿石，"
        "主产物乘难度倍率后按 Java 四舍五入，不能把 Normal 的 1.5 倍直接当 4.5 件。"
        "副产物不计入下表；大型机扩建并行不减少该配方的单位蒸汽。\n"]
    parts.append(table(["档位", "机器", "并行", "蒸汽 mB/t", "主产物/t", "mB 汽/主产物"], [
        [LABELS[r["tier"]], r["machine"]] + [fmt(r[key]) for key in ("parallel", "steam_per_tick", "output_per_tick", "steam_per_item")]
        for r in m["crushers"]]))
    parts += ["\n虚空采矿厂默认 4 工位、200 tick 周期、每工位每刻 3,000 mB 蒸汽、每抽 8 件粗矿。"
        "产物随机，以下按全部粗矿合计，普通排气、不超频；具体目标矿种产量还需乘其权重份额。\n"]
    parts.append(table(["档位", "蒸汽 mB/t", "粗矿/t", "mB 汽/粗矿"], [
        [LABELS[r["tier"]]] + [fmt(r[key]) for key in ("steam_per_tick", "raw_ore_per_tick", "steam_per_raw_ore")]
        for r in m["ore_plant"]]))
    parts += ["\n科技顺序与依赖见[玩家成长指南](../guides/steam-progression.md)。"
              "大型供汽仓与高效排气仓均需要 HV 电路，属于后期回访升级。\n",
        "## 组装并行与电路专精\n",
        "示例配方：100 tick、32 EU/t、1 个保证产物；非专精、普通排气、不超频、100% 节流。"
        "空槽仅 ULV；单槽堆叠 1–4 台同种 LV–EV 电力组装机允许 2/4/8/16 并行，配方等级由该机种决定。"
        "表中 32 EU/t 示例需要至少 LV 槽位；1 并行行用于少量输入时的结算比较。"
        "实际不足满并行时按实际并行落入阶梯，不能套用满槽成本。\n"]
    parts.append(table(["档位", "并行", "耗时 tick", "蒸汽 mB/t", "批次产物", "mB 汽/件"], [
        [LABELS[r["tier"]], r["parallel"], r["duration"], r["steam_per_tick"], fmt(r["output"]), fmt(r["steam_per_item"])]
        for r in m["assembler"]]))
    parts += ["\n同档位从 1 到 16 满并行，理论产能为 `16/3 = 5.3333×`，单位蒸汽为 `2×3/16 = 0.375×`。"
              "升级槽内机器还影响电压门槛，无法仅以并行倍率评估投资。\n",
        "专精仅对与样品物品及 NBT 完全相同的保证输出生效。样品不消耗；输入物品、焊料按普通批次扣取。"
        "每批一次随机，期望产出倍率为 `1 + 概率×额外倍率`，耗时向上取 `1.5×`，每刻耗汽不变。"
        "预留输出容量按最大增产计算。下表沿用上述配方，16 并行；单位比率与同档、同并行的非专精批次相比。\n"]
    parts.append(table(["档位", "期望产出倍率", "期望产能比", "单位蒸汽比", "期望批产物", "示例 mB 汽/件"], [
        [LABELS[r["tier"]]] + [fmt(r[key]) for key in ("expected_multiplier", "throughput_ratio", "unit_steam_ratio",
                                                      "expected_output", "steam_per_item")]
        for r in m["circuit"]]))
    parts += ["\nExpert 专精的期望产出仅 1.25×，耗时 1.5×：产能为 0.8333×，单位蒸汽为 1.2×。"
              "它节省目标电路的原料和焊料，适用于材料受限场景；蒸汽或时间受限时应慎用。"
              "Easy/Normal 专精可同时改善期望产能和单位蒸汽，但仍需覆盖最高输出容量。\n",
        "## 钻井与燃料闭环\n",
        "默认 2 泵位、每泵位每 200 tick 抽 2,000 mB、每泵位 3,000 mB/t。普通满速总需求 6,000 mB/t。"
        "高效排气先将每刻蒸汽向上取整到 67%；超频再将耗时向上取半、每刻耗汽乘 3。\n"]
    parts.append(table(["档位", "高效排气", "超频", "蒸汽 mB/t", "流体 mB/t", "流体 mB/小时", "mB 汽/mB 流体"], [
        [LABELS[r["tier"]], "有" if r["advanced_exhaust"] else "无", "开" if r["overclock"] else "关"]
        + [fmt(r[key]) for key in ("steam_per_tick", "output_per_tick", "output_per_hour", "steam_per_mb")]
        for r in m["drill"]]))
    parts += ["\n产物是全部油气合计，默认权重轻质原油/原油/重质原油/天然气为 25/20/15/15，"
        "目标流体期望份额为对应权重除以 75；短期可能连续抽到同一种。天然气在当前蒸汽阶段用于积累。\n",
        "不能把全部产出直接折算为返供燃料。混合锅炉与锅炉房的白名单来自运行时 `steam_boiler` 流体配方，"
        "锅炉房复制其配方并将燃烧时长向下取整除以 4；上游大型锅炉、燃油发电机的油类配方并不会因此自动解锁。"
        "核验闭环时逐种检查配方查看器接受性、炼化损耗及粉料/水/鼓风，再计算 "
        "`净蒸汽 = Σ(产物量×可返供蒸汽/mB) − 钻井蒸汽 − 炼化与辅助成本`。"
        "未接受或尚不可加工的油气当期返供量计 0。本文不宣称默认钻井能够自持。\n",
        "## 高炉熟练度、热风与高装料\n"]
    parts.append(table(["档位", "新手/熟悉/熟练/精通耗时 %", "熟悉/熟练/精通累计作业数"], [
        [LABELS[t], "/".join(fmt(m["profiles"][t][f"blastFurnace{x}DurationPercent"]) for x in ("Novice", "Familiar", "Skilled", "Mastered")),
         "/".join(fmt(m["profiles"][t][f"blastFurnace{x}Operations"]) for x in ("Familiar", "Skilled", "Mastered"))]
        for t in TIERS]))
    parts += ["\n累计作业数按完成并行累加；换配方 ID 后连贯熟练度重新起算。原始高炉配方仍消耗自己的固体燃料，"
        "大型机额外使用蒸汽与鼓风。普通满载 96 并行：19,200 mB/t 蒸汽、384 mB/t 鼓风。\n",
        "高装料连续两批后复位，每批增加债务 `ceil(D×max(P−96,0)/96)`。两批和复位都消耗固定 "
        "19,200 mB/t 蒸汽、384 mB/t 鼓风；强制 100% 节流，禁用超频及热风节汽。"
        "示例原配方 100 tick、已精通、两批都满载 192 并行、无高效排气：\n"]
    parts.append(table(["档位", "普通批耗时 tick", "普通作业/t", "高装料含复位 tick", "高装料作业/t", "普通 mB 汽/作业", "高装料 mB 汽/作业"], [
        [LABELS[r["tier"]]] + [fmt(r[key]) for key in ("mastered_duration", "normal_output_per_tick", "high_cycle_ticks",
            "high_output_per_tick", "normal_steam_per_operation", "high_steam_per_operation")]
        for r in m["blast"]]))
    parts += ["\n满装料 192 并行时，`两批生产 2D + 复位 2D = 4D`，输出 `384` 次作业，"
        "理论持续产能与普通 96 并行相同。它提供集中出货能力；第二批输入不足或配方不匹配会扩大等待成本。"
        "高效排气仍作用于生产批次；复位通过直接资源扣取，不享受该折扣，不能把 67% 套到整个周期。\n",
        "热风模块普通批次回收最终普通蒸汽的 15% 为储热；储热足以覆盖整批后，该批节汽 15% 并消耗等量储热。"
        "相同配方/并行长期交替蓄热、放热且无容量溢出时，平均节汽约 7.5%，不是持续 15%。"
        "单批热需求超过储热上限 100,000,000 时无法启动整批优惠。拆坏模块清空储热；高装料期间储热冻结。\n",
        "## 锅炉房预热与维护\n",
        "满温、100% 节流、有效协同粉料、无产汽外挂模块。末档水垢仅表示 75%–100% 的减产阶段，"
        "到 100% 报废时不能继续产汽。满温理想升温时间从内部 0°C 算起，假设液体燃料、粉料与鼓风不断；"
        "实际升温过程的产汽随温度增长，不能全程按满温产量结算。\n"]
    parts.append(table(["档位", "材料级别", "干净蒸汽 mB/t", "末档水垢蒸汽 mB/t", "理想升温秒"], [
        [LABELS[r["tier"]], r["metal"]] + [fmt(r[key]) for key in ("clean_steam_per_tick", "stage3_steam_per_tick", "ideal_heat_seconds")]
        for r in m["boilers"]]))
    parts += ["\n水垢按干净产量相对该机器最大干净产量的负载积分累计；默认四段边界为 25%/50%/75%/100%。"
        "Easy/Normal/Expert 在理想满负载下每 180/6/2 小时增加 25 个百分点。"
        "默认每次冷酸洗消耗 8,000 mB 稀盐酸、耗时 3,200 tick（160 秒），清除 25 个百分点。"
        "仅清洗占时的理想损失为 `160/(工作秒+160)`，还需加上停炉冷却和重新升温；"
        "冷酸洗要求内部温度 ≤26°C，不能把 160 秒当整次维护总停机时间。"
        "低负载与软化模块能延后水垢，但模块辅料必须计入。\n",
        "## 蓄热熔炉启动成本与摊销\n",
        "尺寸按宽×高，选最小、中等、最大三种样本。计价体积为 `(宽²−4)×高`。"
        "理想首次启炉从 20°C 到最高温的 60%，每度蒸汽向上取整；下表排除冷却、补热、限流和停机。"
        "加工列是另一个满温锁定批次样例（100 tick、32 EU/t、1 并行、普通排气、不超频），"
        "按实际每刻取整后的批次总汽计价，并非把首次启炉温度当满温。\n"]
    parts.append(table(["档位", "尺寸", "理想启动汽 mB", "理想启动秒", "满温速度倍率", "示例批次 tick", "示例 mB 汽/件"], [
        [LABELS[r["tier"]], f'{r["width"]}×{r["height"]}'] + [fmt(r[key]) for key in ("ideal_start_steam", "ideal_start_seconds",
            "full_heat_speed", "example_batch_ticks", "example_steam_per_item")]
        for r in m["furnace"]]))
    parts += ["\n规模越大，启动投资越高，满温速度和加工节汽越好。实际单位成本应按 "
        "`(首次预热汽+所有补热汽+加工汽)/累计有效输出` 计算，不能只取满温折扣。"
        "若把升级材料折算为蒸汽等价成本 `I`、额外启动成本 `H`，单位节省 `ΔS>0`，"
        "静态回本数量为 `ceil((I+H)/ΔS)`；若 `ΔS≤0` 则无蒸汽回本点。"
        "材料没有统一售价，本文不给虚构的固定回本分钟。\n",
        "## 共用升级顺序\n",
        "高效排气：名义每刻耗汽向上取整至 67%，短批次/小需求按实际取整。"
        "大型供汽仓超频：每刻×3、耗时向上取半；偶数耗时的产能约×2、单位蒸汽×1.5。"
        "同时使用高效排气与超频时名义单位蒸汽约 `0.67×1.5=1.005`，不等于节省一半。"
        "普通节流 25%/50%/75%/100% 保留批次总蒸汽，只把每刻需求转为更长耗时；"
        "它适合供汽瓶颈，不能当节汽升级。高装料复位是独立扣资源阶段，见上面的例外。\n",
        "## 数据来源\n",
        "数值来源文件及 SHA-256 记录在同目录 `steam-economy.json`；以下是本次模型读取的文件。\n"]
    parts += [f"- [`{name}`](../../{name})" for name in m["source_fingerprints"]]
    return "\n".join(parts) + "\n"


def json_value(value):
    if isinstance(value, Fraction):
        return value.numerator if value.denominator == 1 else {"numerator": value.numerator, "denominator": value.denominator}
    raise TypeError(type(value).__name__)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify committed reports without writing")
    args = parser.parse_args()
    try:
        model = build_model()
        outputs = {"steam-economy.md": render(model),
                   "steam-economy.json": json.dumps(model, ensure_ascii=False, indent=2, default=json_value) + "\n"}
        stale = []
        for name, text in outputs.items():
            path = OUTPUT / name
            if args.check:
                if not path.exists() or path.read_text(encoding="utf-8") != text:
                    stale.append(path.relative_to(ROOT).as_posix())
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8", newline="\n")
        if stale:
            raise ValueError("stale economy reports: " + ", ".join(stale) + "; run tools/export_steam_economy.py")
        print(f"Steam economy {'verified' if args.check else 'exported'}: 4 modes, 4 recipe profiles, "
              f"{len(model['materials']['NORMAL'])} acquisition items/profile.")
        return 0
    except (ValueError, KeyError, OSError) as error:
        print(f"Steam economy error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
