package com.hoshino.gregsteamexpansion.data;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GCYMBlocks;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyRecipes;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;

import net.minecraft.data.recipes.FinishedRecipe;
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
