package com.hoshino.gregsteamexpansion.data;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GCYMBlocks;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyRecipes;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.registry.GSERecipeSerializers;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;

import net.minecraft.data.recipes.FinishedRecipe;

import org.jetbrains.annotations.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class GSERecipes {
    private GSERecipes() {}

    public static void init(Consumer<FinishedRecipe> provider) {
        addCraftingStationRecipes(provider);

        VanillaRecipeHelper.addShapedRecipe(
                provider,
                GregSteamExpansion.id("lp_steam_mixed_fuel_boiler"),
                GSEMachines.MIXED_FUEL_BOILER.left().asStack(),
                "DQD",
                "SwL",
                "DQD",
                'D', ChemicalHelper.get(TagPrefix.plateDouble, GTMaterials.Bronze),
                'Q', ChemicalHelper.get(TagPrefix.pipeQuadrupleFluid, GTMaterials.Bronze),
                'S', GTMachines.STEAM_SOLID_BOILER.left().asStack(),
                'L', GTMachines.STEAM_LIQUID_BOILER.left().asStack());

        VanillaRecipeHelper.addShapedRecipe(
                provider,
                GregSteamExpansion.id("hp_steam_mixed_fuel_boiler"),
                GSEMachines.MIXED_FUEL_BOILER.right().asStack(),
                "DQD",
                "SwL",
                "DQD",
                'D', ChemicalHelper.get(TagPrefix.plateDouble, GTMaterials.Steel),
                'Q', ChemicalHelper.get(TagPrefix.pipeQuadrupleFluid, GTMaterials.Steel),
                'S', GTMachines.STEAM_SOLID_BOILER.right().asStack(),
                'L', GTMachines.STEAM_LIQUID_BOILER.right().asStack());

        addIndustrialSteamCasingRecipes(provider);
        addBronzeComponentRecipes(provider);
        addSteamGrindingBlockRecipes(provider);
        addSteamAssemblyBlockRecipes(provider);
        addSteamCircuitAssemblyBlockRecipes(provider);
        addSteamMixingBlockRecipes(provider);
        addSteamExhaustHatchRecipe(provider);
        addSteamHatchRecipes(provider);
        addSteamCrusherRecipes(provider);
        addSteamCompressorRecipe(provider);
        addSteamExtractorRecipe(provider);
        addSteamForgeRecipe(provider);
        addLargeSteamOreWasherRecipe(provider);
        addLargeSteamThermalCentrifugeRecipe(provider);
        addLargeSteamMaceratorRecipe(provider);
        addLargeSteamMixerRecipe(provider);
        addSteamChemicalBathRecipe(provider);
        addSteamCentrifugeRecipes(provider);
        addLargeSteamAssemblerRecipe(provider);
        addLargeSteamCircuitAssemblerRecipe(provider);
        addFurnaceControllerRecipe(provider);
        addCokeOvenRecipes(provider);
        addLargeCokeOvenRecipes(provider);
        addBoilerRoomRecipes(provider);
        addLargeSteamOrePlantRecipe(provider);
        addLargeSteamFluidDrillRecipe(provider);
        addElectricOreCrusherRecipes(provider);
    }

    // ------------------------------------------------------------------
    // 电力粉碎机 (ore-crushing.md 电力消费机器): LV–UV 分级单方块,
    // 每档 = 该档板材 x6 + 该档电路 x2 + 该档框架 x1, 工作台与组装机双路线,
    // 产出恒 1。材质/电路/框架逐档取 GTCEu 标准进阶表。
    // ------------------------------------------------------------------

    private static void addElectricOreCrusherRecipes(Consumer<FinishedRecipe> provider) {
        var tiers = new Object[][]{
                {GTValues.LV, GTMaterials.Steel, com.gregtechceu.gtceu.data.recipe.CustomTags.LV_CIRCUITS},
                {GTValues.MV, GTMaterials.Aluminium, com.gregtechceu.gtceu.data.recipe.CustomTags.MV_CIRCUITS},
                {GTValues.HV, GTMaterials.StainlessSteel, com.gregtechceu.gtceu.data.recipe.CustomTags.HV_CIRCUITS},
                {GTValues.EV, GTMaterials.Titanium, com.gregtechceu.gtceu.data.recipe.CustomTags.EV_CIRCUITS},
                {GTValues.IV, GTMaterials.TungstenSteel, com.gregtechceu.gtceu.data.recipe.CustomTags.IV_CIRCUITS},
                {GTValues.LuV, GTMaterials.RhodiumPlatedPalladium, com.gregtechceu.gtceu.data.recipe.CustomTags.LuV_CIRCUITS},
                {GTValues.ZPM, GTMaterials.NaquadahAlloy, com.gregtechceu.gtceu.data.recipe.CustomTags.ZPM_CIRCUITS},
                {GTValues.UV, GTMaterials.Darmstadtium, com.gregtechceu.gtceu.data.recipe.CustomTags.UV_CIRCUITS},
        };
        var frames = new Object[][]{
                {GTValues.LV, GTMaterials.Steel},
                {GTValues.MV, GTMaterials.Aluminium},
                {GTValues.HV, GTMaterials.StainlessSteel},
                {GTValues.EV, GTMaterials.Titanium},
                {GTValues.IV, GTMaterials.TungstenSteel},
                {GTValues.LuV, GTMaterials.Ruridit},
                {GTValues.ZPM, GTMaterials.Iridium},
                {GTValues.UV, GTMaterials.NaquadahAlloy},
        };
        var tierNames = new String[]{"lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv"};

        for (int i = 0; i < tiers.length; i++) {
            int tier = (Integer) tiers[i][0];
            var plateMaterial = (com.gregtechceu.gtceu.api.data.chemical.material.Material) tiers[i][1];
            var circuitTag = (net.minecraft.tags.TagKey<net.minecraft.world.item.Item>) tiers[i][2];
            var frameMaterial = (com.gregtechceu.gtceu.api.data.chemical.material.Material) frames[i][1];
            var definition = GSEMachines.ELECTRIC_ORE_CRUSHERS[tier];

            // 工作台: 六板 + 双电路 + 中心框架。
            VanillaRecipeHelper.addShapedRecipe(
                    provider,
                    GregSteamExpansion.id("electric_ore_crusher_" + tierNames[i]),
                    definition.asStack(),
                    "PCP",
                    "PFP",
                    "PCP",
                    'P', ChemicalHelper.get(TagPrefix.plate, plateMaterial),
                    'C', circuitTag,
                    'F', ChemicalHelper.get(TagPrefix.frameGt, frameMaterial));

            // 组装机: 材料一致, 电路配置 = 档位。
            GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder(
                            GregSteamExpansion.id("electric_ore_crusher_" + tierNames[i]))
                    .inputItems(TagPrefix.plate, plateMaterial, 6)
                    .inputItems(TagPrefix.frameGt, frameMaterial)
                    .inputItems(circuitTag, 2)
                    .circuitMeta(tier)
                    .outputItems(definition.asStack())
                    .duration(100)
                    .EUt(16 * (1 << Math.min(tier, 8)))
                    .save(provider);
        }
    }

    // ------------------------------------------------------------------
    // 大型蒸汽采矿厂 / 大型蒸汽流体钻井 (large-steam-ore-plant.md 获取配方
    // (议题 10) / large-steam-fluid-drill.md 获取配方（议题 10)): 旗舰加重
    // 版家族骨架——钢框架 x2 + 钢双层板 x4 + 青铜构件 x2 + 主题核心方块
    // (F1 蒸汽研磨方块 / F2 钢管道方块), 折算约 20-24 钢板当量。恒产 1,
    // 三档相同, 不含仓室。图案水平/垂直双对称, 原版 shaped 即可。
    // ------------------------------------------------------------------

    private static void addLargeSteamOrePlantRecipe(Consumer<FinishedRecipe> provider) {
        provider.accept(upstreamShaped(
                GregSteamExpansion.id("shaped/large_steam_ore_plant"),
                GSEMachines.LARGE_STEAM_ORE_PLANT.asStack(),
                new String[]{"DFD", "CMC", "DFD"},
                new Object[]{
                        'D', ChemicalHelper.get(TagPrefix.plateDouble, GTMaterials.Steel),
                        'F', ChemicalHelper.get(TagPrefix.frameGt, GTMaterials.Steel),
                        'C', new ItemStack(GSEBlocks.BRONZE_COMPONENT.get()),
                        'M', new ItemStack(GSEBlocks.STEAM_GRINDING_BLOCK.get())}));
    }

    private static void addLargeSteamFluidDrillRecipe(Consumer<FinishedRecipe> provider) {
        provider.accept(upstreamShaped(
                GregSteamExpansion.id("shaped/large_steam_fluid_drill"),
                GSEMachines.LARGE_STEAM_FLUID_DRILL.asStack(),
                new String[]{"DFD", "CMC", "DFD"},
                new Object[]{
                        'D', ChemicalHelper.get(TagPrefix.plateDouble, GTMaterials.Steel),
                        'F', ChemicalHelper.get(TagPrefix.frameGt, GTMaterials.Steel),
                        'C', new ItemStack(GSEBlocks.BRONZE_COMPONENT.get()),
                        'M', GTBlocks.CASING_STEEL_PIPE.asStack()}));
    }

    // ------------------------------------------------------------------
    // 大型蒸汽组装机 / 大型蒸汽电路组装机 (large-steam-assembler.md 获取
    // 配方（议题 10） / large-steam-circuit-assembler.md 获取配方（议题 10）):
    // 家族骨架九宫格 (核心方块居中 + 上下功能件 + 左右构件 + 四角板), 每次
    // 恒产 1 个控制器, 三档相同; 不含任何仓室。B1 以青铜齿轮传动, B2 以
    // 橡胶片绝缘——两图案在配方查看器中一眼可辨。
    // ------------------------------------------------------------------

    private static void addLargeSteamAssemblerRecipe(Consumer<FinishedRecipe> provider) {
        provider.accept(upstreamShaped(
                GregSteamExpansion.id("shaped/large_steam_assembler"),
                GSEMachines.LARGE_STEAM_ASSEMBLER.asStack(),
                new String[]{"PGP", "CAC", "PGP"},
                new Object[]{
                        'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze),
                        'G', ChemicalHelper.get(TagPrefix.gear, GTMaterials.Bronze),
                        'C', new ItemStack(GSEBlocks.BRONZE_COMPONENT.get()),
                        'A', new ItemStack(GSEBlocks.STEAM_ASSEMBLY_BLOCK.get())}));
    }

    private static void addLargeSteamCircuitAssemblerRecipe(Consumer<FinishedRecipe> provider) {
        provider.accept(upstreamShaped(
                GregSteamExpansion.id("shaped/large_steam_circuit_assembler"),
                GSEMachines.LARGE_STEAM_CIRCUIT_ASSEMBLER.asStack(),
                new String[]{"PRP", "CAC", "PRP"},
                new Object[]{
                        'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze),
                        'R', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Rubber),
                        'C', new ItemStack(GSEBlocks.BRONZE_COMPONENT.get()),
                        'A', new ItemStack(GSEBlocks.STEAM_CIRCUIT_ASSEMBLY_BLOCK.get())}));
    }

    // ------------------------------------------------------------------
    // 锅炉房四档控制器 (boiler-room.md P3#14 材料定稿): 对应档 GTCEu 大型锅炉
    // 控制器 ×1 + 本模组高压混合燃料锅炉 ×1 + 对应档板 ×7 → 锅炉房控制器 ×1。
    // 工作台与组装机两条路线材料一致、产出相同 (恒产 1, 三档相同), 使「完全
    // 取代」在获取路径上成立。
    // ------------------------------------------------------------------

    private static void addBoilerRoomRecipes(Consumer<FinishedRecipe> provider) {
        addBoilerRoomRecipe(provider, "bronze",
                GTMultiMachines.LARGE_BOILER_BRONZE, TagPrefix.plate, GTMaterials.Bronze,
                GSEMachines.BOILER_ROOM_BRONZE);
        addBoilerRoomRecipe(provider, "steel",
                GTMultiMachines.LARGE_BOILER_STEEL, TagPrefix.plate, GTMaterials.Steel,
                GSEMachines.BOILER_ROOM_STEEL);
        addBoilerRoomRecipe(provider, "titanium",
                GTMultiMachines.LARGE_BOILER_TITANIUM, TagPrefix.plate, GTMaterials.Titanium,
                GSEMachines.BOILER_ROOM_TITANIUM);
        addBoilerRoomRecipe(provider, "tungstensteel",
                GTMultiMachines.LARGE_BOILER_TUNGSTENSTEEL, TagPrefix.plate, GTMaterials.TungstenSteel,
                GSEMachines.BOILER_ROOM_TUNGSTENSTEEL);
    }

    private static void addBoilerRoomRecipe(Consumer<FinishedRecipe> provider, String tier,
                                            com.gregtechceu.gtceu.api.machine.MachineDefinition largeBoiler,
                                            TagPrefix platePrefix, com.gregtechceu.gtceu.api.data.chemical.material.Material plateMaterial,
                                            com.gregtechceu.gtceu.api.machine.MachineDefinition boilerRoom) {
        // 工作台: 七板包裹大型锅炉 (上) 与高压混合燃料锅炉 (核心右侧)。
        VanillaRecipeHelper.addShapedRecipe(
                provider,
                GregSteamExpansion.id("boiler_room_" + tier),
                boilerRoom.asStack(),
                "PPP",
                "PSM",
                "PPP",
                'P', ChemicalHelper.get(platePrefix, plateMaterial),
                'S', largeBoiler.asStack(),
                'M', GSEMachines.MIXED_FUEL_BOILER.right().asStack());

        // 组装机: 材料一致, 电路 7 用于区分四个档位。
        GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder(GregSteamExpansion.id("boiler_room_" + tier))
                .inputItems(ChemicalHelper.get(platePrefix, plateMaterial, 7))
                .inputItems(largeBoiler.asStack())
                .inputItems(GSEMachines.MIXED_FUEL_BOILER.right().asStack())
                .circuitMeta(7)
                .outputItems(boilerRoom.asStack())
                .duration(400)
                .EUt(16)
                .save(provider);
    }

    // ------------------------------------------------------------------
    // 大型焦炉控制器 / 大型焦炉仓 (coke-ovens.md 已确认大型焦炉控制器配方 /
    // 已确认大型焦炉仓配方): 各只有一条有序工作台配方, 资源 ID 固定为
    // gregsteamexpansion:large_coke_oven(_hatch), 每次固定产出 1 个, 不读取
    // casingsPerCraft 或通用方块产量, 三档永久相同。两图案左右完全对称 (原版
    // shaped 的水平镜像不产生另一种排列), 上下不可颠倒 (原版不做垂直镜像)。
    // ------------------------------------------------------------------

    private static void addLargeCokeOvenRecipes(Consumer<FinishedRecipe> provider) {
        ItemStack bricksBlock = GTBlocks.CASING_COKE_BRICKS.asStack();
        ItemStack steelDoublePlate = ChemicalHelper.get(TagPrefix.plateDouble, GTMaterials.Steel);
        ItemStack steelPlate = ChemicalHelper.get(TagPrefix.plate, GTMaterials.Steel);

        // 控制器: 四角钢双层板、四边中点焦炉砖块、正中普通焦炉控制器 (升级核心,
        // 仅消耗物品形态, 不转移任何世界状态)。
        provider.accept(upstreamShaped(
                GregSteamExpansion.id("large_coke_oven"),
                GSEMachines.LARGE_COKE_OVEN.asStack(),
                new String[]{"SBS", "BCB", "SBS"},
                new Object[]{
                        'S', steelDoublePlate,
                        'B', bricksBlock,
                        'C', GTMultiMachines.COKE_OVEN.asStack()}));

        // 大型焦炉仓: 四角钢板、中央左右焦炉砖块、正中普通焦炉仓 (升级核心),
        // 上方中央木箱标签 (物品语义), 下方中央 GTCEu 木桶 (流体语义)。
        provider.accept(upstreamShaped(
                GregSteamExpansion.id("large_coke_oven_hatch"),
                GSEMachines.LARGE_COKE_OVEN_HATCH.asStack(),
                new String[]{"SXS", "BCB", "SPS"},
                new Object[]{
                        'S', steelPlate,
                        'X', net.minecraftforge.common.Tags.Items.CHESTS_WOODEN,
                        'B', bricksBlock,
                        'C', GTMachines.COKE_OVEN_HATCH.asStack(),
                        'P', GTMachines.WOODEN_DRUM.asStack()}));
    }

    // ------------------------------------------------------------------
    // 普通焦炉控制器 / 可配置焦炉仓 (coke-ovens.md 获取配方): 精确覆盖上游
    // gtceu:shaped/coke_oven 与 gtceu:shaped/coke_oven_hatch (上游经
    // VanillaRecipeHelper 注册时的真实资源 ID), 旧图案不再存在。使用原版
    // shaped 序列化器, 产出原有注册对象, 每次固定 1 个, 三档完全相同。
    // ------------------------------------------------------------------

    /** Shared by data generation and the addon's dynamic-pack replacements. */
    public static void addCokeOvenRecipes(Consumer<FinishedRecipe> provider) {
        ItemStack brickItem = GTItems.COKE_OVEN_BRICK.get().getDefaultInstance();
        ItemStack bricksBlock = GTBlocks.CASING_COKE_BRICKS.asStack();
        ItemStack bronzeFluidPipe = ChemicalHelper.get(TagPrefix.pipeNormalFluid, GTMaterials.Bronze);

        // 控制器: 8 焦炉砖物品 + 1 熔炉居中, 不再使用焦炉砖块/铁板/扳手位。
        provider.accept(upstreamShaped(
                GregSteamExpansion.gtceuId("shaped/coke_oven"),
                GTMultiMachines.COKE_OVEN.asStack(),
                new String[]{"BBB", "BFB", "BBB"},
                new Object[]{
                        'B', brickItem,
                        'F', new ItemStack(Items.FURNACE)}));

        // 焦炉仓: 四角焦炉砖物品、中央左右焦炉砖块、上方漏斗、正中木箱标签、
        // 下方普通青铜流体管道; 上下不可颠倒, 左右对称。
        provider.accept(upstreamShaped(
                GregSteamExpansion.gtceuId("shaped/coke_oven_hatch"),
                GTMachines.COKE_OVEN_HATCH.asStack(),
                new String[]{"BHB", "CXC", "BPB"},
                new Object[]{
                        'B', brickItem,
                        'H', new ItemStack(Items.HOPPER),
                        'C', bricksBlock,
                        'X', net.minecraftforge.common.Tags.Items.CHESTS_WOODEN,
                        'P', bronzeFluidPipe}));
    }

    /**
     * 产出一份标准原版 shaped 配方 JSON, 资源 ID 与内容完全由调用方指定 (允许
     * 覆盖 gtceu 命名空间的上游配方 ID)。
     */
    private static FinishedRecipe upstreamShaped(ResourceLocation id, ItemStack result, String[] pattern,
                                                 Object... keys) {
        return new FinishedRecipe() {
            @Override
            public void serializeRecipeData(com.google.gson.JsonObject json) {
                json.addProperty("category", "misc");
                com.google.gson.JsonArray patternJson = new com.google.gson.JsonArray();
                for (String row : pattern) {
                    patternJson.add(row);
                }
                json.add("pattern", patternJson);
                com.google.gson.JsonObject keyJson = new com.google.gson.JsonObject();
                for (int i = 0; i + 1 < keys.length; i += 2) {
                    char symbol = (Character) keys[i];
                    Object value = keys[i + 1];
                    com.google.gson.JsonObject ingredient = new com.google.gson.JsonObject();
                    if (value instanceof ItemStack stack) {
                        ingredient.addProperty("item", net.minecraftforge.registries.ForgeRegistries.ITEMS
                                .getKey(stack.getItem()).toString());
                    } else if (value instanceof net.minecraft.tags.TagKey<?> tag) {
                        @SuppressWarnings("unchecked")
                        net.minecraft.tags.TagKey<net.minecraft.world.item.Item> itemTag =
                                (net.minecraft.tags.TagKey<net.minecraft.world.item.Item>) tag;
                        ingredient.addProperty("tag", itemTag.location().toString());
                    } else {
                        throw new IllegalArgumentException("Unsupported coke oven recipe key: " + value);
                    }
                    keyJson.add(String.valueOf(symbol), ingredient);
                }
                json.add("key", keyJson);
                com.google.gson.JsonObject resultJson = new com.google.gson.JsonObject();
                resultJson.addProperty("item", net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getKey(result.getItem()).toString());
                resultJson.addProperty("count", result.getCount());
                json.add("result", resultJson);
            }

            @Override
            public ResourceLocation getId() {
                return id;
            }

            @Override
            public net.minecraft.world.item.crafting.RecipeSerializer<?> getType() {
                return net.minecraft.world.item.crafting.RecipeSerializer.SHAPED_RECIPE;
            }

            @Override
            @Nullable
            public com.google.gson.JsonObject serializeAdvancement() {
                return null;
            }

            @Override
            @Nullable
            public ResourceLocation getAdvancementId() {
                return null;
            }
        };
    }

    // ------------------------------------------------------------------
    // Steam compressor (steam-compressor.md 议题 10 已定案配方): vanilla
    // piston core + bronze small gear drive + seven bronze plates; crafts
    // exactly one controller per tier. The pattern is left/right symmetric
    // (plain horizontal mirroring keeps it unchanged), so the vanilla shaped
    // serializer suffices; the resource ID uses the shaped/ prefix because the
    // machine definition ID is already taken by the EMI multiblock info page.
    // ------------------------------------------------------------------

    private static void addSteamCompressorRecipe(Consumer<FinishedRecipe> provider) {
        provider.accept(upstreamShaped(
                GregSteamExpansion.id("shaped/steam_compressor"),
                GSEMachines.STEAM_COMPRESSOR.asStack(),
                new String[]{"PPP", "PXP", "PGP"},
                new Object[]{
                        'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze),
                        'X', new ItemStack(Items.PISTON),
                        'G', ChemicalHelper.get(TagPrefix.gearSmall, GTMaterials.Bronze)}));
    }

    // ------------------------------------------------------------------
    // Large Steam Ore Washer controller
    // (large-steam-ore-washer.md 获取配方（议题 10）): 3×3 fully filled —
    // mixing block centre (washing core), bronze rotors top/bottom (stirring
    // drive shafts), bronze components left/right (load frame), bronze plates
    // at the corners (shell). Four-way symmetric, crafting-table only.
    // ------------------------------------------------------------------
    private static void addLargeSteamOreWasherRecipe(Consumer<FinishedRecipe> provider) {
        provider.accept(upstreamShaped(
                GregSteamExpansion.id("shaped/large_steam_ore_washer"),
                GSEMachines.LARGE_STEAM_ORE_WASHER.asStack(),
                new String[]{"PRP", "CSC", "PRP"},
                new Object[]{
                        'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze),
                        'R', ChemicalHelper.get(TagPrefix.rotor, GTMaterials.Bronze),
                        'C', new ItemStack(GSEBlocks.BRONZE_COMPONENT.get()),
                        'S', new ItemStack(GSEBlocks.STEAM_MIXING_BLOCK.get())}));
    }

    // ------------------------------------------------------------------
    // Large Steam Thermal Centrifuge controller
    // (large-steam-thermal-centrifuge.md 获取配方（议题 10）): 3×3 fully
    // filled — steam mixing block centre (rotor core), bronze firebox casings
    // left/right (heat sources, 2026-09-07 用户修订由齿轮改为燃烧室), bronze
    // components top/bottom centre (load frame), bronze plates at the corners
    // (shell). Four-way symmetric, crafting-table only.
    // ------------------------------------------------------------------
    private static void addLargeSteamThermalCentrifugeRecipe(Consumer<FinishedRecipe> provider) {
        provider.accept(upstreamShaped(
                GregSteamExpansion.id("shaped/large_steam_thermal_centrifuge"),
                GSEMachines.LARGE_STEAM_THERMAL_CENTRIFUGE.asStack(),
                new String[]{"PCP", "FMF", "PCP"},
                new Object[]{
                        'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze),
                        'C', new ItemStack(GSEBlocks.BRONZE_COMPONENT.get()),
                        'F', GTBlocks.FIREBOX_BRONZE.asStack(),
                        'M', new ItemStack(GSEBlocks.STEAM_MIXING_BLOCK.get())}));
    }

    // ------------------------------------------------------------------
    // Large Steam Macerator controller
    // (large-steam-macerator.md 获取配方（议题 10）): 3×3 ordered crafting
    // recipe, fixed output 1, identical across all three difficulty tiers.
    // Pattern P G P / C H C / P M P: bronze plates at the four corners
    // (shell), bronze gear above the core (drive input), bronze components
    // flanking the core (load-bearing), the HP steel steam macerator as the
    // upgrade core (gtceu:hp_steam_macerator = GTMachines.STEAM_MACERATOR
    // second entry, doc-verified ID) and the Steam Grinding Block below the
    // core (grinding chamber). Left-right mirror symmetric (vanilla shaped
    // mirroring does not produce another arrangement), crafting-table only.
    // ------------------------------------------------------------------
    private static void addLargeSteamMaceratorRecipe(Consumer<FinishedRecipe> provider) {
        provider.accept(upstreamShaped(
                GregSteamExpansion.id("shaped/large_steam_macerator"),
                GSEMachines.LARGE_STEAM_MACERATOR.asStack(),
                new String[]{"PGP", "CHC", "PMP"},
                new Object[]{
                        'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze),
                        'G', ChemicalHelper.get(TagPrefix.gear, GTMaterials.Bronze),
                        'C', new ItemStack(GSEBlocks.BRONZE_COMPONENT.get()),
                        'H', GTMachines.STEAM_MACERATOR.second().asStack(),
                        'M', new ItemStack(GSEBlocks.STEAM_GRINDING_BLOCK.get())}));
    }

    // ------------------------------------------------------------------
    // Large Steam Mixer controller
    // (large-steam-mixer.md 获取配方（议题 10）): 3×3 ordered crafting
    // recipe, fixed output 1, identical across all three difficulty tiers.
    // Pattern C R C / P M P / C R C: bronze components at the four corners
    // (load-bearing), bronze rotors above/below the core (stirring shafts),
    // bronze plates left/right (shell) and the Steam Mixing Block as the
    // mixing core (S1 precedent: the mixing block legally has multiple
    // consumers). The corners were deliberately switched from gears to
    // components (用户 2026-09-07 裁定) so this grid differs cell-by-cell
    // from the S1 ore washer controller recipe (P R P / C S C / P R P) —
    // vanilla crafting cannot disambiguate two identical input grids.
    // Four-way symmetric, crafting-table only.
    // ------------------------------------------------------------------
    private static void addLargeSteamMixerRecipe(Consumer<FinishedRecipe> provider) {
        provider.accept(upstreamShaped(
                GregSteamExpansion.id("shaped/large_steam_mixer"),
                GSEMachines.LARGE_STEAM_MIXER.asStack(),
                new String[]{"CRC", "PMP", "CRC"},
                new Object[]{
                        'C', new ItemStack(GSEBlocks.BRONZE_COMPONENT.get()),
                        'R', ChemicalHelper.get(TagPrefix.rotor, GTMaterials.Bronze),
                        'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze),
                        'M', new ItemStack(GSEBlocks.STEAM_MIXING_BLOCK.get())}));
    }

    // ------------------------------------------------------------------
    // Steam Chemical Bath controller
    // (large-steam-chemical-bath.md 获取配方（议题 10）): bronze plates x4 on
    // the corners, a glass cross (c:glass item tag, 浸洗观察腔语义) and one
    // bronze component as the load-bearing core. No hatches in the recipe -
    // pure steam-era materials. Horizontally AND vertically symmetric, so
    // the vanilla shaped serializer suffices; always yields 1 controller.
    // ------------------------------------------------------------------

    private static void addSteamChemicalBathRecipe(Consumer<FinishedRecipe> provider) {
        provider.accept(upstreamShaped(
                GregSteamExpansion.id("shaped/steam_chemical_bath"),
                GSEMachines.STEAM_CHEMICAL_BATH.asStack(),
                new String[]{"PGP", "GCG", "PGP"},
                new Object[]{
                        'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze),
                        'G', net.minecraft.tags.TagKey.create(
                                net.minecraft.core.registries.Registries.ITEM,
                                new ResourceLocation("c", "glass")),
                        'C', new ItemStack(GSEBlocks.BRONZE_COMPONENT.get())}));
    }

    // ------------------------------------------------------------------
    // Steam Centrifuge + Large Steam Centrifuge controllers
    // (steam-centrifuges.md 获取配方（议题 10）, 轻量化修订版): both recipes
    // are bronze-plate dominated, NO steam machine / industrial casings as
    // ingredients, always yield 1 controller. The LARGE recipe consumes one
    // Steam Centrifuge controller as the upgrade core (大型粉碎机先例, 用户
    // 2026-09-07 更正口径). Both patterns are horizontally symmetric - the
    // vanilla shaped serializer suffices.
    // ------------------------------------------------------------------

    private static void addSteamCentrifugeRecipes(Consumer<FinishedRecipe> provider) {
        provider.accept(upstreamShaped(
                GregSteamExpansion.id("shaped/steam_centrifuge"),
                GSEMachines.STEAM_CENTRIFUGE.asStack(),
                new String[]{"PPP", "PMP", "PGP"},
                new Object[]{
                        'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze),
                        'M', new ItemStack(GSEBlocks.STEAM_MIXING_BLOCK.get()),
                        'G', ChemicalHelper.get(TagPrefix.gear, GTMaterials.Bronze)}));
        provider.accept(upstreamShaped(
                GregSteamExpansion.id("shaped/large_steam_centrifuge"),
                GSEMachines.LARGE_STEAM_CENTRIFUGE.asStack(),
                new String[]{"GSG", "CMC", "PPP"},
                new Object[]{
                        'G', ChemicalHelper.get(TagPrefix.gear, GTMaterials.Bronze),
                        'S', GSEMachines.STEAM_CENTRIFUGE.asStack(),
                        'C', new ItemStack(GSEBlocks.BRONZE_COMPONENT.get()),
                        'M', new ItemStack(GSEBlocks.STEAM_MIXING_BLOCK.get()),
                        'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze)}));
    }

    // ------------------------------------------------------------------
    // Steam Extractor controller
    // (steam-extractor.md 获取配方（议题 10）): bronze plate ×7 wrapping a
    // bronze pipe casing core (fluid extraction semantics, centred) and a
    // small bronze gear drive at the bottom centre. Horizontally symmetric,
    // so the vanilla shaped serializer suffices.
    // ------------------------------------------------------------------

    private static void addSteamExtractorRecipe(Consumer<FinishedRecipe> provider) {
        provider.accept(upstreamShaped(
                GregSteamExpansion.id("shaped/steam_extractor"),
                GSEMachines.STEAM_EXTRACTOR.asStack(),
                new String[]{"PPP", "PTP", "PGP"},
                new Object[]{
                        'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze),
                        'T', GTBlocks.CASING_BRONZE_PIPE.asStack(),
                        'G', ChemicalHelper.get(TagPrefix.gearSmall, GTMaterials.Bronze)}));
    }

    // ------------------------------------------------------------------
    // Steam Forge controller
    // (steam-forge.md 获取配方（议题 10）): bronze plate ×7 wrapping a vanilla
    // piston core (forge semantics, centred) and a small bronze gear drive at
    // the TOP centre — gear position moved up versus the compressor recipe so
    // the two share no identical 3×3 input (配方重合修正). Horizontally
    // symmetric, so the vanilla shaped serializer suffices.
    // ------------------------------------------------------------------

    private static void addSteamForgeRecipe(Consumer<FinishedRecipe> provider) {
        provider.accept(upstreamShaped(
                GregSteamExpansion.id("shaped/steam_forge"),
                GSEMachines.STEAM_FORGE.asStack(),
                new String[]{"PGP", "PXP", "PPP"},
                new Object[]{
                        'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze),
                        'X', new ItemStack(Items.PISTON),
                        'G', ChemicalHelper.get(TagPrefix.gearSmall, GTMaterials.Bronze)}));
    }

    // ------------------------------------------------------------------
    // Large Heat-Storage Steam Furnace controller
    // (large-heat-storage-steam-furnace.md 控制器合成配方)
    // ------------------------------------------------------------------

    private static void addFurnaceControllerRecipe(Consumer<FinishedRecipe> provider) {
        VanillaRecipeHelper.addShapedRecipe(provider,
                GregSteamExpansion.id("large_heat_storage_steam_furnace"),
                GSEMachines.LARGE_HEAT_STORAGE_STEAM_FURNACE.asStack(),
                "DPD",
                "FOF",
                "DPD",
                'D', ChemicalHelper.get(TagPrefix.plateDouble, GTMaterials.Steel),
                'P', ChemicalHelper.get(TagPrefix.pipeHugeFluid, GTMaterials.Steel),
                'F', GTMachines.STEAM_FURNACE.right().asStack(),
                'O', GTMultiMachines.STEAM_OVEN.asStack());
    }

    private static void addCraftingStationRecipes(Consumer<FinishedRecipe> provider) {
        VanillaRecipeHelper.addShapedRecipe(
                provider,
                GregSteamExpansion.id("crafting_station"),
                new ItemStack(GSEBlocks.CRAFTING_STATION_ITEM.get()),
                "RBR",
                " W ",
                "R R",
                'B', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze),
                'R', ChemicalHelper.get(TagPrefix.rod, GTMaterials.Bronze),
                'W', new ItemStack(Items.CRAFTING_TABLE));

        // 's' matches GTCEu's saw tool symbol: the saw only takes durability
        // instead of being consumed. The station uses uppercase 'C' because
        // lowercase 'c' is GTCEu's crowbar tool symbol (crafting-station.md 7.1).
        VanillaRecipeHelper.addShapedRecipe(
                provider,
                GregSteamExpansion.id("crafting_station_slab"),
                new ItemStack(GSEBlocks.CRAFTING_STATION_SLAB_ITEM.get()),
                "s",
                "C",
                's', CustomTags.CRAFTING_SAWS,
                'C', new ItemStack(GSEBlocks.CRAFTING_STATION_ITEM.get()));
    }

    // ------------------------------------------------------------------
    // Difficulty tiering (difficulty.md 配方与数据重载机制)
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface TieredRecipe {
        void build(Consumer<FinishedRecipe> provider, ResourceLocation id, Difficulty difficulty);
    }

    /**
     * Emits one recipe variant per difficulty tier. Every variant carries a
     * {@code gregsteamexpansion:difficulty} condition and its own suffixed
     * resource ID ({@code <base>_easy/_normal/_expert}) so exactly one loads
     * per save. GTCEu's helpers already prepend the {@code shaped/} and
     * {@code assembler/} folders, so {@code gregsteamexpansion:shaped/<base>_easy}
     * matches the resource IDs in items-and-blocks.md.
     */
    private static void tiered(Consumer<FinishedRecipe> provider, String basePath, TieredRecipe recipe) {
        for (Difficulty difficulty : Difficulty.values()) {
            ResourceLocation id = GregSteamExpansion.id(basePath + "_" + difficulty.getSerializedName());
            recipe.build(GSEDifficultyRecipes.atDifficulty(provider, difficulty), id, difficulty);
        }
    }

    /**
     * items-and-blocks.md 通用方块产量: recipes whose primary output is a plain
     * block registered by this mod produce the same effective value as GTCEu's
     * {@code recipes.casingsPerCraft} (Easy 2, Normal 1, Expert 1).
     */
    private static int blocksPerCraft(Difficulty difficulty) {
        return difficulty.getCasingsPerCraft();
    }

    private static ItemStack blockOutput(RegistryObject<Item> item, Difficulty difficulty) {
        return new ItemStack(item.get(), blocksPerCraft(difficulty));
    }

    /** Expert upgrades regular plates to double plates in equal slot counts. */
    private static TagPrefix platePrefix(Difficulty difficulty) {
        return difficulty == Difficulty.EXPERT ? TagPrefix.plateDouble : TagPrefix.plate;
    }

    /** Steam grinding block: Expert swaps small gears for full gears. */
    private static TagPrefix gearPrefix(Difficulty difficulty) {
        return difficulty == Difficulty.EXPERT ? TagPrefix.gear : TagPrefix.gearSmall;
    }

    // ------------------------------------------------------------------
    // Industrial Steam Casing (upstream block, this mod only adds recipes)
    // ------------------------------------------------------------------

    private static void addIndustrialSteamCasingRecipes(Consumer<FinishedRecipe> provider) {
        // Historical Gregicality Multiblocks steam casing recipe restored for
        // gtceu:industrial_steam_casing; this mod never re-registers the block.
        // Output follows the GTCEu casingsPerCraft parameter, which the global
        // difficulty forces to 2 / 1 / 1 (difficulty.md 上游覆盖白名单).
        // GTCEu 7.5.3 gives Brass no GENERATE_FRAME flag, so the frame slot
        // uses the bronze frame instead of the nonexistent brass frame.
        ItemStack brassPlate = ChemicalHelper.get(TagPrefix.plate, GTMaterials.Brass);
        ItemStack bronzeFrame = ChemicalHelper.get(TagPrefix.frameGt, GTMaterials.Bronze);

        tiered(provider, "industrial_steam_casing", (tierProvider, id, difficulty) ->
                VanillaRecipeHelper.addShapedRecipe(tierProvider, id,
                        GCYMBlocks.CASING_INDUSTRIAL_STEAM.asStack(difficulty.getCasingsPerCraft()),
                        "PhP",
                        "PFP",
                        "PwP",
                        'P', brassPlate,
                        'F', bronzeFrame,
                        'h', CustomTags.CRAFTING_HAMMERS,
                        'w', CustomTags.CRAFTING_WRENCHES));

        tiered(provider, "industrial_steam_casing", (tierProvider, id, difficulty) ->
                GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder(id)
                        .inputItems(TagPrefix.plate, GTMaterials.Brass, 6)
                        .inputItems(TagPrefix.frameGt, GTMaterials.Bronze)
                        .circuitMeta(6)
                        .outputItems(GCYMBlocks.CASING_INDUSTRIAL_STEAM.asStack(difficulty.getCasingsPerCraft()))
                        .duration(50)
                        .EUt(16)
                        .save(tierProvider));
    }

    // ------------------------------------------------------------------
    // Bronze Component (always 1 per craft, never the generic block output)
    // ------------------------------------------------------------------

    private static void addBronzeComponentRecipes(Consumer<FinishedRecipe> provider) {
        // GTCEu 7.5.3 gives Bronze no GENERATE_SPRING flag, so the two spring
        // slots use copper springs — the steam-era spring material upstream
        // actually generates items for.
        ItemStack copperSpring = ChemicalHelper.get(TagPrefix.spring, GTMaterials.Copper);

        tiered(provider, "bronze_component", (tierProvider, id, difficulty) ->
                VanillaRecipeHelper.addShapedRecipe(tierProvider, id,
                        new ItemStack(GSEBlocks.BRONZE_COMPONENT.get()),
                        "PhP",
                        "SFS",
                        "PwP",
                        'P', ChemicalHelper.get(platePrefix(difficulty), GTMaterials.Bronze),
                        'S', copperSpring,
                        'F', ChemicalHelper.get(TagPrefix.frameGt, GTMaterials.Bronze),
                        'h', CustomTags.CRAFTING_HAMMERS,
                        'w', CustomTags.CRAFTING_WRENCHES));

        tiered(provider, "bronze_component", (tierProvider, id, difficulty) ->
                GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder(id)
                        .inputItems(platePrefix(difficulty), GTMaterials.Bronze, 3)
                        .inputItems(TagPrefix.spring, GTMaterials.Copper)
                        .inputItems(TagPrefix.frameGt, GTMaterials.Bronze)
                        .circuitMeta(6)
                        .outputItems(GSEBlocks.BRONZE_COMPONENT.get(), 1)
                        .duration(50)
                        .EUt(16)
                        .save(tierProvider));
    }

    // ------------------------------------------------------------------
    // Steam Grinding Block (diamond grinding head is mandatory in every tier)
    // ------------------------------------------------------------------

    private static void addSteamGrindingBlockRecipes(Consumer<FinishedRecipe> provider) {
        tiered(provider, "steam_grinding_block", (tierProvider, id, difficulty) ->
                VanillaRecipeHelper.addShapedRecipe(tierProvider, id,
                        blockOutput(GSEBlocks.STEAM_GRINDING_BLOCK_ITEM, difficulty),
                        "PGP",
                        "hDw",
                        "PGP",
                        'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze),
                        'G', ChemicalHelper.get(gearPrefix(difficulty), GTMaterials.Bronze),
                        'D', GTItems.COMPONENT_GRINDER_DIAMOND.get(),
                        'h', CustomTags.CRAFTING_HAMMERS,
                        'w', CustomTags.CRAFTING_WRENCHES));

        tiered(provider, "steam_grinding_block", (tierProvider, id, difficulty) ->
                GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder(id)
                        .inputItems(TagPrefix.plate, GTMaterials.Bronze, 3)
                        .inputItems(gearPrefix(difficulty), GTMaterials.Bronze, 1)
                        .inputItems(GTItems.COMPONENT_GRINDER_DIAMOND.get())
                        .circuitMeta(4)
                        .outputItems(GSEBlocks.STEAM_GRINDING_BLOCK_ITEM.get(), blocksPerCraft(difficulty))
                        .duration(100)
                        .EUt(16)
                        .save(tierProvider));
    }

    // ------------------------------------------------------------------
    // Steam Assembly Block (bronze component core + sync gear pair)
    // ------------------------------------------------------------------

    private static void addSteamAssemblyBlockRecipes(Consumer<FinishedRecipe> provider) {
        ItemStack bronzePlate = ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze);
        ItemStack bronzeDoublePlate = ChemicalHelper.get(TagPrefix.plateDouble, GTMaterials.Bronze);
        ItemStack bronzeGear = ChemicalHelper.get(TagPrefix.gear, GTMaterials.Bronze);
        ItemStack bronzeComponent = new ItemStack(GSEBlocks.BRONZE_COMPONENT.get());

        tiered(provider, "steam_assembly_block", (tierProvider, id, difficulty) -> {
            boolean expert = difficulty == Difficulty.EXPERT;
            // 'D' is only defined when the pattern's top row references it;
            // the Forge-patched serializer rejects unused key symbols.
            List<Object> args = new ArrayList<>(List.of(
                    expert ? "DGD" : "PGP",
                    "hCw",
                    "PGP"));
            if (expert) {
                args.addAll(List.of('D', bronzeDoublePlate));
            }
            args.addAll(List.of(
                    'P', bronzePlate,
                    'G', bronzeGear,
                    'C', bronzeComponent,
                    'h', CustomTags.CRAFTING_HAMMERS,
                    'w', CustomTags.CRAFTING_WRENCHES));
            VanillaRecipeHelper.addShapedRecipe(tierProvider, id,
                    blockOutput(GSEBlocks.STEAM_ASSEMBLY_BLOCK_ITEM, difficulty), args.toArray());
        });

        tiered(provider, "steam_assembly_block", (tierProvider, id, difficulty) -> {
            var builder = GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder(id)
                    .inputItems(TagPrefix.gear, GTMaterials.Bronze, 2)
                    .inputItems(GSEBlocks.BRONZE_COMPONENT.get())
                    .circuitMeta(4);
            if (difficulty == Difficulty.EXPERT) {
                builder.inputItems(TagPrefix.plate, GTMaterials.Bronze, 1)
                        .inputItems(TagPrefix.plateDouble, GTMaterials.Bronze, 1);
            } else {
                builder.inputItems(TagPrefix.plate, GTMaterials.Bronze, 2);
            }
            builder.outputItems(GSEBlocks.STEAM_ASSEMBLY_BLOCK_ITEM.get(), blocksPerCraft(difficulty))
                    .duration(100)
                    .EUt(16)
                    .save(tierProvider);
        });
    }

    // ------------------------------------------------------------------
    // Steam Circuit Assembly Block (rubber insulation, wire cutter tooling)
    // ------------------------------------------------------------------

    private static void addSteamCircuitAssemblyBlockRecipes(Consumer<FinishedRecipe> provider) {
        ItemStack bronzePlate = ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze);
        ItemStack bronzeDoublePlate = ChemicalHelper.get(TagPrefix.plateDouble, GTMaterials.Bronze);
        ItemStack bronzeGear = ChemicalHelper.get(TagPrefix.gear, GTMaterials.Bronze);
        ItemStack rubberPlate = ChemicalHelper.get(TagPrefix.plate, GTMaterials.Rubber);
        ItemStack bronzeComponent = new ItemStack(GSEBlocks.BRONZE_COMPONENT.get());

        tiered(provider, "steam_circuit_assembly_block", (tierProvider, id, difficulty) -> {
            boolean expert = difficulty == Difficulty.EXPERT;
            List<Object> args = new ArrayList<>(List.of(
                    expert ? "DGD" : "PGP",
                    "xCw",
                    "RGR"));
            if (expert) {
                args.addAll(List.of('D', bronzeDoublePlate));
            } else {
                args.addAll(List.of('P', bronzePlate));
            }
            args.addAll(List.of(
                    'R', rubberPlate,
                    'G', bronzeGear,
                    'C', bronzeComponent,
                    'x', CustomTags.CRAFTING_WIRE_CUTTERS,
                    'w', CustomTags.CRAFTING_WRENCHES));
            VanillaRecipeHelper.addShapedRecipe(tierProvider, id,
                    blockOutput(GSEBlocks.STEAM_CIRCUIT_ASSEMBLY_BLOCK_ITEM, difficulty), args.toArray());
        });

        // The assembler replaces the two rubber sheets with an equal material
        // amount of liquid rubber (2 x 144 mB); circuit config 5 keeps it
        // distinct from the regular assembly block's config 4.
        tiered(provider, "steam_circuit_assembly_block", (tierProvider, id, difficulty) -> {
            var builder = GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder(id)
                    .inputItems(TagPrefix.gear, GTMaterials.Bronze, 2)
                    .inputItems(GSEBlocks.BRONZE_COMPONENT.get())
                    .inputFluids(GTMaterials.Rubber, 288)
                    .circuitMeta(5);
            if (difficulty == Difficulty.EXPERT) {
                builder.inputItems(TagPrefix.plateDouble, GTMaterials.Bronze, 2);
            } else {
                builder.inputItems(TagPrefix.plate, GTMaterials.Bronze, 2);
            }
            builder.outputItems(GSEBlocks.STEAM_CIRCUIT_ASSEMBLY_BLOCK_ITEM.get(), blocksPerCraft(difficulty))
                    .duration(100)
                    .EUt(16)
                    .save(tierProvider);
        });
    }

    // ------------------------------------------------------------------
    // Steam Exhaust Hatch (large-heat-storage-steam-furnace.md): the GTCEu
    // steam hatch shell with the center bronze drum replaced by a bronze gear.
    // Hatches always craft exactly one with identical materials in every tier
    // (difficulty.md 仓室通则), so no difficulty conditions are needed.
    // ------------------------------------------------------------------

    private static void addSteamExhaustHatchRecipe(Consumer<FinishedRecipe> provider) {
        VanillaRecipeHelper.addShapedRecipe(provider,
                GregSteamExpansion.id("steam_exhaust_hatch"),
                GSEMachines.STEAM_EXHAUST_HATCH.asStack(),
                "BPB",
                "BGB",
                "BPB",
                'B', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze),
                'P', ChemicalHelper.get(TagPrefix.pipeNormalFluid, GTMaterials.Bronze),
                'G', ChemicalHelper.get(TagPrefix.gear, GTMaterials.Bronze));
    }

    // ------------------------------------------------------------------
    // Steam-era hatches (machines-and-hatches.md 获取方式): the four defined
    // hatches always craft exactly one with identical bronze materials in
    // every tier (difficulty.md 仓室通则), so no difficulty conditions are
    // needed. GTCEu's helpers prepend the shaped/ and assembler/ folders,
    // producing the resource IDs named by the design doc.
    // ------------------------------------------------------------------

    private static void addSteamHatchRecipes(Consumer<FinishedRecipe> provider) {
        ItemStack bronzePlate = ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze);
        ItemStack bronzePipe = ChemicalHelper.get(TagPrefix.pipeNormalFluid, GTMaterials.Bronze);
        ItemStack bronzeDrum = GTMachines.BRONZE_DRUM.asStack();
        ItemStack bronzeRotor = ChemicalHelper.get(TagPrefix.rotor, GTMaterials.Bronze);
        ItemStack bronzeComponent = new ItemStack(GSEBlocks.BRONZE_COMPONENT.get());

        // 蒸汽供给仓: bronze plates form the hull, a normal bronze fluid pipe
        // pair the steam channel, and the bronze drum the inner container.
        VanillaRecipeHelper.addShapedRecipe(provider,
                GregSteamExpansion.id("steam_supply_hatch"),
                GSEMachines.STEAM_SUPPLY_HATCH.asStack(),
                "BPB",
                "BTB",
                "BPB",
                'B', bronzePlate,
                'P', bronzePipe,
                'T', bronzeDrum);

        // 蒸汽流体输入仓: single vertical pipe on top (no vertical mirroring;
        // plain horizontal mirroring keeps the pattern unchanged).
        VanillaRecipeHelper.addShapedRecipe(provider,
                GregSteamExpansion.id("steam_fluid_input_hatch"),
                GSEMachines.STEAM_FLUID_IMPORT_HATCH.asStack(),
                "PTP",
                "TDT",
                "PRP",
                'P', bronzePlate,
                'T', bronzePipe,
                'D', bronzeDrum,
                'R', bronzeRotor);

        // 蒸汽流体输出仓: the vertical pipe and rotor swap places vertically.
        VanillaRecipeHelper.addShapedRecipe(provider,
                GregSteamExpansion.id("steam_fluid_output_hatch"),
                GSEMachines.STEAM_FLUID_EXPORT_HATCH.asStack(),
                "PRP",
                "TDT",
                "PTP",
                'P', bronzePlate,
                'T', bronzePipe,
                'D', bronzeDrum,
                'R', bronzeRotor);

        // 蒸汽进气室: the bronze component fixes rotor and air ducts in place.
        VanillaRecipeHelper.addShapedRecipe(provider,
                GregSteamExpansion.id("steam_air_intake_hatch"),
                GSEMachines.STEAM_AIR_INTAKE_HATCH.asStack(),
                "PRP",
                "TDT",
                "PCP",
                'P', bronzePlate,
                'T', bronzePipe,
                'D', bronzeDrum,
                'R', bronzeRotor,
                'C', bronzeComponent);

        // Assembler routes (LV-era automation) use identical material costs,
        // distinguished only by the programming circuit configuration, which
        // is consumed neither here nor anywhere else.
        GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder(GregSteamExpansion.id("steam_fluid_input_hatch"))
                .inputItems(TagPrefix.plate, GTMaterials.Bronze, 4)
                .inputItems(TagPrefix.pipeNormalFluid, GTMaterials.Bronze, 3)
                .inputItems(GTMachines.BRONZE_DRUM.asStack())
                .inputItems(TagPrefix.rotor, GTMaterials.Bronze, 1)
                .circuitMeta(1)
                .outputItems(GSEMachines.STEAM_FLUID_IMPORT_HATCH.asStack())
                .duration(100)
                .EUt(16)
                .save(provider);

        GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder(GregSteamExpansion.id("steam_fluid_output_hatch"))
                .inputItems(TagPrefix.plate, GTMaterials.Bronze, 4)
                .inputItems(TagPrefix.pipeNormalFluid, GTMaterials.Bronze, 3)
                .inputItems(GTMachines.BRONZE_DRUM.asStack())
                .inputItems(TagPrefix.rotor, GTMaterials.Bronze, 1)
                .circuitMeta(2)
                .outputItems(GSEMachines.STEAM_FLUID_EXPORT_HATCH.asStack())
                .duration(100)
                .EUt(16)
                .save(provider);

        GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder(GregSteamExpansion.id("steam_air_intake_hatch"))
                .inputItems(TagPrefix.plate, GTMaterials.Bronze, 4)
                .inputItems(TagPrefix.pipeNormalFluid, GTMaterials.Bronze, 2)
                .inputItems(GTMachines.BRONZE_DRUM.asStack())
                .inputItems(TagPrefix.rotor, GTMaterials.Bronze, 1)
                .inputItems(GSEBlocks.BRONZE_COMPONENT.get())
                .circuitMeta(3)
                .outputItems(GSEMachines.STEAM_AIR_INTAKE_HATCH.asStack())
                .duration(100)
                .EUt(16)
                .save(provider);
    }

    // ------------------------------------------------------------------
    // Steam crushers (steam-crushers.md 控制器配方): both controllers craft
    // exactly one per tier. The small crusher's pattern is left/right
    // symmetric, so vanilla mirroring is indistinguishable; the large
    // crusher's rotor/saw-blade row must NOT mirror, which needs the
    // exact-direction serializer.
    // ------------------------------------------------------------------

    private static void addSteamCrusherRecipes(Consumer<FinishedRecipe> provider) {
        // 蒸汽粉碎机: the HP steam macerator is permanently installed as the
        // control & drive core; the bottom-centre slot stays empty.
        VanillaRecipeHelper.addShapedRecipe(provider,
                GregSteamExpansion.id("steam_crusher"),
                GSEMachines.STEAM_CRUSHER.asStack(),
                "PDP",
                "RMR",
                "P P",
                'P', ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze),
                'D', GTItems.COMPONENT_GRINDER_DIAMOND.get(),
                'R', ChemicalHelper.get(TagPrefix.rotor, GTMaterials.Bronze),
                'M', GTMachines.STEAM_MACERATOR.right().asStack());

        // 大型蒸汽粉碎机: the small crusher controller is permanently installed
        // as the upgrade core; rotor left, buzz saw blade right, no mirroring.
        // GTCEu 7.5.3 gives Brass no GENERATE_GEAR/GENERATE_ROTOR flags, so the
        // brass gear and rotor use the steel equivalents — the large machine's
        // steel reinforcement material (same substitution precedent as the
        // industrial steam casing's bronze frame).
        provider.accept(exactDirectionShaped(
                // shaped/ 前缀 is mandatory here: the recipe ID must differ from
                // the machine definition ID, which GTCEu's multiblock info page
                // (MultiblockInfoEmiRecipe) already uses as its viewer recipe ID
                // (steam-crushers.md: 配方资源 ID 为 shaped/large_steam_crusher).
                GregSteamExpansion.id("shaped/large_steam_crusher"),
                GSEMachines.LARGE_STEAM_CRUSHER.asStack(),
                new String[]{"DGD", "RSB", "DGD"},
                'D', ChemicalHelper.get(TagPrefix.plateDouble, GTMaterials.Steel),
                'G', ChemicalHelper.get(TagPrefix.gear, GTMaterials.Steel),
                'R', ChemicalHelper.get(TagPrefix.rotor, GTMaterials.Steel),
                'S', GSEMachines.STEAM_CRUSHER.asStack(),
                'B', ChemicalHelper.get(TagPrefix.toolHeadBuzzSaw, GTMaterials.Steel)));
    }

    /**
     * Emits a shaped recipe JSON for the exact-direction serializer
     * ({@code gregsteamexpansion:exact_direction_shaped}); keys are single-item
     * ingredients, which is all the crusher recipes use.
     */
    private static FinishedRecipe exactDirectionShaped(ResourceLocation id, ItemStack result,
                                                       String[] pattern, Object... keys) {
        return new FinishedRecipe() {
            @Override
            public void serializeRecipeData(com.google.gson.JsonObject json) {
                json.addProperty("category", "misc");
                com.google.gson.JsonArray patternJson = new com.google.gson.JsonArray();
                for (String row : pattern) {
                    patternJson.add(row);
                }
                json.add("pattern", patternJson);
                com.google.gson.JsonObject keyJson = new com.google.gson.JsonObject();
                for (int i = 0; i + 1 < keys.length; i += 2) {
                    char symbol = (Character) keys[i];
                    ItemStack stack = (ItemStack) keys[i + 1];
                    ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS
                            .getKey(stack.getItem());
                    com.google.gson.JsonObject ingredient = new com.google.gson.JsonObject();
                    ingredient.addProperty("item", itemId.toString());
                    keyJson.add(String.valueOf(symbol), ingredient);
                }
                json.add("key", keyJson);
                com.google.gson.JsonObject resultJson = new com.google.gson.JsonObject();
                resultJson.addProperty("item", net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getKey(result.getItem()).toString());
                resultJson.addProperty("count", result.getCount());
                json.add("result", resultJson);
            }

            @Override
            public ResourceLocation getId() {
                return id;
            }

            @Override
            public net.minecraft.world.item.crafting.RecipeSerializer<?> getType() {
                return GSERecipeSerializers.EXACT_DIRECTION_SHAPED.get();
            }

            @Override
            @Nullable
            public com.google.gson.JsonObject serializeAdvancement() {
                return null;
            }

            @Override
            @Nullable
            public ResourceLocation getAdvancementId() {
                return null;
            }
        };
    }


    // ------------------------------------------------------------------
    // Steam Mixing Block (twin bronze rotor shaft, no bronze component)
    // ------------------------------------------------------------------

    private static void addSteamMixingBlockRecipes(Consumer<FinishedRecipe> provider) {
        ItemStack bronzePlate = ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze);
        ItemStack bronzeDoublePlate = ChemicalHelper.get(TagPrefix.plateDouble, GTMaterials.Bronze);
        ItemStack bronzeGear = ChemicalHelper.get(TagPrefix.gear, GTMaterials.Bronze);
        ItemStack bronzeRotor = ChemicalHelper.get(TagPrefix.rotor, GTMaterials.Bronze);

        tiered(provider, "steam_mixing_block", (tierProvider, id, difficulty) -> {
            boolean expert = difficulty == Difficulty.EXPERT;
            List<Object> args = new ArrayList<>(List.of(
                    expert ? "DGD" : "PGP",
                    "hRw",
                    "PRP"));
            if (expert) {
                args.addAll(List.of('D', bronzeDoublePlate));
            }
            args.addAll(List.of(
                    'P', bronzePlate,
                    'G', bronzeGear,
                    'R', bronzeRotor,
                    'h', CustomTags.CRAFTING_HAMMERS,
                    'w', CustomTags.CRAFTING_WRENCHES));
            VanillaRecipeHelper.addShapedRecipe(tierProvider, id,
                    blockOutput(GSEBlocks.STEAM_MIXING_BLOCK_ITEM, difficulty), args.toArray());
        });

        tiered(provider, "steam_mixing_block", (tierProvider, id, difficulty) -> {
            var builder = GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder(id)
                    .inputItems(TagPrefix.gear, GTMaterials.Bronze, 1)
                    .inputItems(TagPrefix.rotor, GTMaterials.Bronze, 2)
                    .circuitMeta(6);
            if (difficulty == Difficulty.EXPERT) {
                builder.inputItems(TagPrefix.plate, GTMaterials.Bronze, 1)
                        .inputItems(TagPrefix.plateDouble, GTMaterials.Bronze, 1);
            } else {
                builder.inputItems(TagPrefix.plate, GTMaterials.Bronze, 2);
            }
            builder.outputItems(GSEBlocks.STEAM_MIXING_BLOCK_ITEM.get(), blocksPerCraft(difficulty))
                    .duration(100)
                    .EUt(16)
                    .save(tierProvider);
        });
    }
}
