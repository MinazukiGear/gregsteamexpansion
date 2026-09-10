package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.recipe.BoilerFuelCache;
import com.hoshino.gregsteamexpansion.recipe.RecipeCacheLifecycle;
import com.hoshino.gregsteamexpansion.recipe.SteamRecipeCache;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.AbstractSteamProcessorMachine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSERecipeOptimizationTests {
    private GSERecipeOptimizationTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fuelCacheTracksManagerAndReload(GameTestHelper helper) {
        var cache = new BoilerFuelCache();
        var type = GTRecipeTypes.STEAM_BOILER_RECIPES;
        var fluid = GTMaterials.Creosote.getFluid();
        var recipe = type.recipeBuilder(GregSteamExpansion.id("cache_test"))
                .inputFluids(GTMaterials.Creosote.getFluid(1)).duration(20).buildRawRecipe();
        var emptyManager = new RecipeManager();
        var manager = new RecipeManager();
        manager.replaceRecipes(List.of(recipe));
        helper.assertTrue(!cache.accepts(emptyManager, type, fluid), "Empty world accepted fuel");
        helper.assertTrue(cache.accepts(manager, type, fluid), "Negative cache leaked across worlds");
        helper.assertTrue(!cache.accepts(emptyManager, type, fluid), "Positive cache leaked across worlds");
        emptyManager.replaceRecipes(List.of(recipe));
        RecipeCacheLifecycle.invalidate();
        helper.assertTrue(cache.accepts(emptyManager, type, fluid), "Reload did not invalidate negative cache");
        emptyManager.replaceRecipes(List.of());
        RecipeCacheLifecycle.invalidate();
        helper.assertTrue(!cache.accepts(emptyManager, type, fluid), "Reload did not invalidate positive cache");
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void idleProcessorWakesOnInput(GameTestHelper helper) {
        var definition = GSEMachines.STEAM_COMPRESSOR;
        var machine = (AbstractSteamProcessorMachine) GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing compressor controller");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(), "Compressor did not form"))
                .thenIdle(23)
                .thenExecute(() -> {
                    helper.assertTrue(machine.getBatchParallel() == 0, "Empty compressor started a batch");
                    var input = machine.getParts().stream()
                            .filter(part -> part instanceof ItemBusPartMachine bus
                                    && bus.getInventory().getHandlerIO() == IO.IN)
                            .map(part -> (ItemBusPartMachine) part).findFirst().orElseThrow();
                    var remainder = input.getInventory().insertItemInternal(0, new ItemStack(Items.IRON_INGOT, 9), false);
                    helper.assertTrue(remainder.isEmpty(), "Input bus rejected iron");
                })
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(machine.getBatchParallel() > 0,
                        "Input change did not wake idle recipe search within two ticks"))
                .thenSucceed();
    }

    /**
     * P0-2 regression: the content index must be able to reach
     * {@code air_separation}, whose air arrives through the steam air intake
     * hatch rather than a fluid input hatch.
     *
     * <p>This is the sharpest failure mode of the candidate index. Air-fed
     * recipes declare their air as an ordinary fluid input, so the index is keyed
     * on {@code GTMaterials.Air} — but the fluid physically sits in the intake's
     * tank, which is not an {@code FluidHatchPartMachine} and does not appear in
     * the fluid-input-hatch list. If candidate collection only reads that list,
     * the centrifuge sits idle forever with a full intake tank. The assertion is
     * therefore on the index itself: an air key must resolve to the air
     * separation recipe.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void airRecipesAreReachableThroughTheContentIndex(GameTestHelper helper) {
        var entry = SteamRecipeCache.get(GTRecipeTypes.CENTRIFUGE_RECIPES);
        var fluidBuckets = entry.byContent().get(FluidRecipeCapability.CAP);
        helper.assertTrue(fluidBuckets != null, "Centrifuge cache has no fluid index");

        var airBucket = fluidBuckets.get(GTMaterials.Air.getFluid());
        helper.assertTrue(airBucket != null && !airBucket.isEmpty(),
                "No centrifuge recipe is indexed under air; the air intake cannot reach its recipes");

        // Upstream GTCEu recipe; datapack ids carry the type path as a prefix
        // (gtceu:centrifuge/air_separation), not the bare builder name.
        var airSeparation = entry.byId(new ResourceLocation("gtceu", "centrifuge/air_separation"));
        helper.assertTrue(airSeparation != null, "air_separation is not present in the centrifuge cache");
        helper.assertTrue(airBucket.contains(airSeparation),
                "air_separation is not reachable through the air index bucket");

        // The intake's own fluid must be the very key the index is built on,
        // otherwise the two halves agree only by coincidence.
        helper.assertTrue(GTMaterials.Air.getFluid().is(GTMaterials.Air.getFluidTag()),
                "GTCEu air does not satisfy its own fluid tag; the intake filter and the index disagree");
        helper.succeed();
    }

    /**
     * P0-2 integration guard for the air path: a formed centrifuge fed only by
     * a self-filling steam air intake hatch must actually start the air
     * separation batch and keep the intake's cache as a usable recipe input.
     *
     * <p>This exercises the whole air chain end to end — the intake's own
     * 80-tick environment collection, the cache accumulating across cycles
     * (a rewrite bug once capped it at one cycle and made every air recipe
     * unreachable), the controller seeing that air as a fluid input, the
     * worst-case output precheck accepting a two-fluid product, and the batch
     * finally starting. Every one of those links has failed at least once, so
     * the assertion is deliberately on the observable end state rather than on
     * any single internal step.</p>
     *
     * <p><b>Scope, measured rather than assumed.</b> Sabotaging
     * {@code inputFluidContents()} so the intake no longer contributes its air
     * key does <em>not</em> fail this test: an empty indexable-input set makes
     * {@code candidateRecipes()} fall back to the type's full recipe list, so
     * {@code air_separation} still reaches {@code tryStartRecipe} and still
     * matches against the real tank. The test therefore guards behaviour, not
     * the indexing shortcut — the content index is a performance path whose
     * correctness is backstopped by that fallback.</p>
     *
     * <p>The registered preview shape is deliberately <em>not</em> the air-path
     * configuration: {@code air_separation} emits two distinct fluids
     * (3,900 mB nitrogen + 1,000 mB oxygen) and a single-slot hatch can hold
     * only one of them, so the design (steam-centrifuges.md 议题 12 并行表)
     * requires one dedicated single-slot hatch per product. The shape therefore
     * trades its spare casing cells for extra {@code FLUID_EXPORT_HATCH[1]}
     * parts and tops out at exactly the 8-interface budget:
     * {@code 1 import bus + 1 export bus + 1 supply hatch + 1 fluid input hatch
     * + 1 air intake + 3 fluid output hatches}.</p>
     */
    @GameTest(template = "empty_32x32x32", timeoutTicks = 400)
    public static void airIntakeAloneStartsAirBatch(GameTestHelper helper) {
        var definition = GSEMachines.STEAM_CENTRIFUGE;
        var machine = (AbstractSteamProcessorMachine) GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing centrifuge controller");

        // The centrifuge legitimately requires a fluid input hatch
        // (requiresFluidInput() is hard true — the type's one fluid input slot)
        // and, optionally, one air intake. The registered preview shape carries
        // the former plus a single fluid output hatch; the air path needs three
        // output slots total (two products + spare), so two more casing cells
        // are replaced. Both count against the 8-interface budget.
        var intakeDefinition = GSEMachines.STEAM_AIR_INTAKE_HATCH;
        BlockState intakeState = intakeDefinition.getBlock().defaultBlockState();
        RotationState rotation = intakeDefinition.getRotationState();
        // The intake only collects when the block in front of its facing is air
        // (`isFrontClear`). A north-facing hatch on an interior wall looks
        // straight at the machine's own casing and reports `intake_blocked`, so
        // the hatch must face OUT of the structure.
        if (rotation != RotationState.NONE && intakeState.hasProperty(rotation.property)) {
            intakeState = intakeState.setValue(rotation.property, Direction.EAST);
        }
        // The centrifuge shape is 3 wide (x) x 3 tall (y) x 4 deep (z), with the
        // controller anchored at 16,16,16 and sitting at the front wall's
        // bottom centre (shape x=1, y=0, z=0). Every other cell of the shape is
        // the 25-casing block, so an east side-wall casing cell is a legal
        // part position: shape x=2, y=0, z=1 -> world 16+1, 16+0, 16+1.
        // Facing EAST puts the checked cell at world x=16+2, which is outside
        // the 3-wide structure, so it reads as air.
        BlockPos relativeHatch = new BlockPos(16 + 1, 16 + 0, 16 + 1);
        helper.setBlock(relativeHatch, intakeState);

        // Two more output slots: the east side wall's upper cells (shape x=2
        // at y=1/z=1 and y=2/z=1). Direction.NORTH keeps them flush with the
        // wall like the preview shape's own hatches.
        BlockState extraOutput = GTMachines.FLUID_EXPORT_HATCH[1].getBlock().defaultBlockState();
        RotationState outputRotation = GTMachines.FLUID_EXPORT_HATCH[1].getRotationState();
        if (outputRotation != RotationState.NONE && extraOutput.hasProperty(outputRotation.property)) {
            extraOutput = extraOutput.setValue(outputRotation.property, Direction.NORTH);
        }
        BlockPos secondOutput = new BlockPos(16 + 1, 16 + 1, 16 + 1);
        BlockPos thirdOutput = new BlockPos(16 + 1, 16 + 2, 16 + 1);
        helper.setBlock(secondOutput, extraOutput);
        helper.setBlock(thirdOutput, extraOutput);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Centrifuge did not form with an air intake in place; cell="
                                + helper.getLevel().getBlockState(helper.absolutePos(relativeHatch))
                                + " error=" + (machine.getMultiblockState() == null
                                        || machine.getMultiblockState().error == null ? "none"
                                        : machine.getMultiblockState().error.getClass().getSimpleName())))
                .thenExecute(() -> {
                    var intake = machine.getParts().stream()
                            .filter(part -> part instanceof SteamAirIntakeHatchPartMachine)
                            .map(part -> (SteamAirIntakeHatchPartMachine) part)
                            .findFirst().orElse(null);
                    helper.assertTrue(intake != null, "Swapped cell did not become an air intake hatch at "
                            + helper.absolutePos(relativeHatch));
                    // Air is deliberately NOT primed by hand: the whole point is
                    // that the hatch's own environment collection fills the
                    // cache the machine reads. A full air_separation dose is
                    // 10,000 mB and the hatch banks 4,000 mB per 80-tick cycle,
                    // so three cycles are needed — this also guards the cache
                    // accumulating instead of overwriting itself every cycle.
                    helper.assertTrue(intake.getIntakeStatus()
                            == SteamAirIntakeHatchPartMachine.IntakeStatus.COLLECTING,
                            "Intake is not collecting; status=" + intake.getIntakeStatus().getId());

                    // A batch can only leave the idle state once the per-tick
                    // steam draw succeeds, so the supply hatch has to be primed.
                    var supply = machine.getParts().stream()
                            .filter(part -> part instanceof FluidHatchPartMachine)
                            .map(part -> (FluidHatchPartMachine) part)
                            .findFirst().orElse(null);
                    helper.assertTrue(supply != null, "Centrifuge has no fluid input hatch to prime steam");
                    supply.tank.getStorages()[0].setFluid(
                            new FluidStack(GTMaterials.Steam.getFluid(), 32_000));
                })
                // 3 collection cycles (240 ticks) to bank 10,000 mB, plus the
                // 20-tick idle retry window for the machine to notice it.
                .thenIdle(320)
                .thenExecute(() -> helper.assertTrue(machine.getBatchParallel() > 0,
                        "Centrifuge with a primed air intake never started air_separation"
                                + " | formed=" + machine.isFormed()
                                + " hasAirIntake=" + machine.hasAirIntake()
                                + " airMb=" + machine.getAirIntakeStored()
                                + " intakeStatus=" + machine.getAirIntakeStatusId()
                                + " batchId=" + machine.getBatchRecipeId()
                                + " consumingSteam=" + machine.isConsumingSteam()
                                + " progress=" + machine.getBatchProgress()
                                + " parts=" + machine.getParts().stream()
                                        .map(part -> part.getClass().getSimpleName())
                                        .collect(java.util.stream.Collectors.joining(","))))
                .thenSucceed();
    }

    /**
     * Regression guard for the fluid-output delivery path: fluid must reach an
     * output hatch through the hook the machine actually calls.
     *
     * <p>A {@code FluidHatchPartMachine} with {@code handlerIO = IO.OUT} sets
     * {@code capabilityIO} so that {@code IO.IN} is not supported, and
     * {@code NotifiableFluidTank.fill} begins with
     * {@code if (!canCapInput()) return 0;}. The batch precheck and
     * {@code deliverPendingFluids} both used to call that capability-facing
     * {@code fill}, so a fluid-producing recipe failed its worst-case output
     * precheck with {@code insufficient_out} and could never start — every
     * steam processor's fluid output was dead. The machine now calls
     * {@code fillInternal}, the same hook the coke oven's own export path uses.
     * This test builds a real formed centrifuge, reads one of its output
     * hatches and pins the difference between the two entry points.</p>
     */
    @GameTest(template = "empty_32x32x32", timeoutTicks = 100)
    public static void fluidOutputHatchAcceptsInternalFill(GameTestHelper helper) {
        var definition = GSEMachines.STEAM_CENTRIFUGE;
        var machine = (AbstractSteamProcessorMachine) GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing centrifuge controller");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(),
                        "Centrifuge did not form for the fluid-output probe"))
                .thenExecute(() -> {
                    FluidHatchPartMachine output = machine.getParts().stream()
                            .filter(part -> part instanceof FluidHatchPartMachine)
                            .map(part -> (FluidHatchPartMachine) part)
                            .filter(part -> part.tank.getHandlerIO() == IO.OUT)
                            .findFirst().orElse(null);
                    helper.assertTrue(output != null,
                            "Centrifuge preview shape exposes no IO.OUT fluid hatch");

                    FluidStack water =
                            new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1_000);
                    int viaCapability = output.tank.fill(water,
                            net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
                    helper.assertTrue(viaCapability == 0,
                            "capability-facing fill unexpectedly accepted fluid on an output hatch"
                                    + " (accepted=" + viaCapability + "); the delivery path's"
                                    + " fillInternal requirement may no longer hold upstream");

                    int viaInternal = output.tank.fillInternal(water,
                            net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    helper.assertTrue(viaInternal == 1_000,
                            "fillInternal rejected fluid on an output hatch; accepted=" + viaInternal
                                    + " handlerIO=" + output.tank.getHandlerIO());
                    helper.assertTrue(output.tank.getFluidInTank(0).getAmount() == 1_000,
                            "Output hatch did not retain the internally filled fluid; stored="
                                    + output.tank.getFluidInTank(0).getAmount());
                })
                .thenSucceed();
    }

    /**
     * P0-2: the recipe tables must be shared, not copied per machine. Two
     * machines of the same type have to see the very same {@code List} and index
     * instances, otherwise every formed machine is still paying for its own
     * full-type snapshot on each reload.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recipeCacheIsSharedAcrossMachines(GameTestHelper helper) {
        var type = GTRecipeTypes.COMPRESSOR_RECIPES;
        SteamRecipeCache.Entry first = SteamRecipeCache.get(type);
        var otherType = GTRecipeTypes.CENTRIFUGE_RECIPES;
        SteamRecipeCache.Entry other = SteamRecipeCache.get(otherType);
        SteamRecipeCache.Entry second = SteamRecipeCache.get(type);
        helper.assertTrue(first == second, "Two lookups of the same type returned different cache entries");
        helper.assertTrue(first.recipes() == second.recipes(), "Recipe list was not shared between lookups");
        helper.assertTrue(first.byId() == second.byId(), "Recipe id index was not shared between lookups");
        helper.assertTrue(first.byContent() == second.byContent(), "Content index was not shared between lookups");
        helper.assertTrue(SteamRecipeCache.get(otherType) == other,
                "Alternating recipe types evicted the centrifuge cache");
        helper.assertTrue(first != other, "Different recipe types shared one entry");

        // The entry must be the type's live recipe set, so a machine can still
        // resolve by id through it.
        helper.assertTrue(!first.recipes().isEmpty(), "Compressor recipe cache is unexpectedly empty");
        var sample = first.recipes().get(0);
        helper.assertTrue(first.byId(sample.getId()) == sample,
                "Recipe id index does not resolve a recipe it was built from");

        // A reload must publish a new entry rather than mutating the old one.
        RecipeCacheLifecycle.invalidate();
        SteamRecipeCache.Entry afterReload = SteamRecipeCache.get(type);
        helper.assertTrue(afterReload != first, "Recipe cache survived a revision change unchanged");
        SteamRecipeCache.Entry otherAfterReload = SteamRecipeCache.get(otherType);
        helper.assertTrue(otherAfterReload != other, "Second recipe type survived a revision change unchanged");
        helper.assertTrue(SteamRecipeCache.get(type) == afterReload,
                "Alternating types after reload rebuilt the compressor cache");
        helper.assertTrue(SteamRecipeCache.get(otherType) == otherAfterReload,
                "Alternating types after reload rebuilt the centrifuge cache");
        helper.assertTrue(first.byId(sample.getId()) == sample, "Reload mutated the retained old entry");
        helper.succeed();
    }

    /**
     * P0-2: an idle machine with empty input buses must not start anything.
     *
     * <p>Also inspect candidate selection: removing the empty-input short circuit
     * must fail even if the machine still cannot start a batch.</p>
     */
    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void emptyInputsSkipRecipeSearch(GameTestHelper helper) {
        var definition = GSEMachines.STEAM_COMPRESSOR;
        var machine = (AbstractSteamProcessorMachine) GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(machine != null, "Missing compressor controller");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed(), "Compressor did not form"))
                // Well past several 20-tick idle retries with empty buses.
                .thenIdle(70)
                .thenExecute(() -> {
                    helper.assertTrue(machine.getBatchParallel() == 0,
                            "Empty-input compressor started a batch");
                    helper.assertTrue(!SteamRecipeCache.get(GTRecipeTypes.COMPRESSOR_RECIPES).recipes().isEmpty(),
                            "Empty recipe cache would make the candidate assertion vacuous");
                    try {
                        var selectCandidates = AbstractSteamProcessorMachine.class.getDeclaredMethod("candidateRecipes");
                        selectCandidates.setAccessible(true);
                        var candidates = (List<?>) selectCandidates.invoke(machine);
                        helper.assertTrue(candidates.isEmpty(), "Empty inputs reached recipe candidate traversal");
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError("Could not inspect recipe candidates", e);
                    }
                })
                .thenSucceed();
    }
}
