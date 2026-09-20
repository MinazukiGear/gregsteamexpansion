package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import com.hoshino.gregsteamexpansion.machine.multiblock.SteamProcessorUI;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamExhaustHatchMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.BlastFurnaceHotBlastModule;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.BlastFurnaceHotBlastWorldData;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.LargeSteamBlastFurnaceMachine;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.recipe.SteamRecipeCache;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import static com.hoshino.gregsteamexpansion.gametest.GSESteamEngineTestSupport.*;

/** Runtime contracts for the large steam blast furnace. */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSEBlastFurnaceTests {
    private GSEBlastFurnaceTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 400)
    public static void hotBlastModuleCyclesHeatAndFallsBackOnDamage(GameTestHelper h) {
        var definition = GSEMachines.LARGE_STEAM_BLAST_FURNACE;
        var controller = GSEStructureTestUtils.placeShape(h, definition,
                definition.getMatchingShapes().get(0), new BlockPos(16, 16, 8), Direction.NORTH);
        h.assertTrue(controller instanceof LargeSteamBlastFurnaceMachine,
                "Missing hot-blast furnace fixture controller");
        if (!(controller instanceof LargeSteamBlastFurnaceMachine machine)) {
            return;
        }

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(machine.isFormed(),
                        "Hot-blast furnace fixture did not form"))
                .thenExecute(() -> {
                    BlastFurnaceHotBlastModule.place(h.getLevel(), machine.getPos(), machine.getFrontFacing());
                    set(machine, "lastHotBlastValidationTick", Long.MIN_VALUE);
                    h.assertTrue((boolean) call(machine, "refreshHotBlastModule", true),
                            "Complete twin-tower module did not validate");
                    h.assertTrue(machine.getHotBlastModuleStatusId().equals("ready"),
                            "Complete module exposed the wrong status");

                    var claims = BlastFurnaceHotBlastWorldData.getOrCreate(h.getLevel());
                    var overlap = claims.claim(BlastFurnaceHotBlastWorldData.claimFor(
                            machine.getPos().east(), machine.getFrontFacing()));
                    h.assertTrue(!overlap.success(),
                            "A second controller claimed an overlapping hot-blast module");

                    GTRecipe recipe = GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES
                            .recipeBuilder(GregSteamExpansion.id("hot_blast_cycle_probe"))
                            .inputItems(new ItemStack(Items.COBBLESTONE))
                            .outputItems(new ItemStack(Items.IRON_INGOT))
                            .duration(100).buildRawRecipe();
                    ItemBusPartMachine input = inputBus(machine);
                    fillOutputs(machine, false);
                    fillSteam(machine, 32_000);
                    for (SteamAirIntakeHatchPartMachine intake :
                            GSESteamEngineTestSupport.<SteamAirIntakeHatchPartMachine>list(
                                    machine, "airIntakeHatches")) {
                        intake.tank.getStorages()[0].setFluid(GTMaterials.Air.getFluid(64_000));
                    }

                    set(machine, "hotBlastHeat", 0L);
                    input.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
                    h.assertTrue((boolean) call(machine, "tryStartRecipe", recipe),
                            "Cold module batch did not start");
                    h.assertTrue(!machine.isCurrentBatchHotBlast(),
                            "Empty module incorrectly started in hot-blast mode");
                    eq(h, machine.getBatchSteamPerTick(), 200,
                            "Cold module batch changed normal steam demand");
                    call(machine, "runBatchTick");
                    eq(h, machine.getHotBlastHeat(), 30,
                            "Cold module tick did not recover exactly 15% heat");

                    clearActiveProcessorBatch(machine);
                    call(machine, "onBatchCleared");
                    set(machine, "hotBlastHeat", LargeSteamBlastFurnaceMachine.HOT_BLAST_HEAT_CAPACITY);
                    fillOutputs(machine, false);
                    input.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
                    h.assertTrue((boolean) call(machine, "tryStartRecipe", recipe),
                            "Charged module batch did not start");
                    h.assertTrue(machine.isCurrentBatchHotBlast(),
                            "Charged module did not lock hot-blast mode");
                    eq(h, machine.getBatchSteamPerTick(), 170,
                            "Hot-blast module did not reduce finalized steam demand by 15%");

                    set(machine, "batchProgress", 7);
                    BlockPos broken = BlastFurnaceHotBlastModule.anchor(
                            machine.getPos(), machine.getFrontFacing());
                    h.getLevel().setBlockAndUpdate(broken, Blocks.AIR.defaultBlockState());
                    set(machine, "lastHotBlastValidationTick", Long.MIN_VALUE);
                    long steamBefore = steam(machine);
                    tick(machine);
                    eq(h, machine.getHotBlastHeat(), 0,
                            "Damaged module retained stored heat");
                    eq(h, machine.getBatchProgress(), 1,
                            "Damaged hot-blast batch did not roll back to one tick");
                    eq(h, machine.getBatchSteamPerTick(), 200,
                            "Damaged hot-blast batch did not restore normal steam demand");
                    eq(h, steam(machine), steamBefore,
                            "Module failure consumed steam on the fallback tick");
                    h.assertTrue(!machine.isCurrentBatchHotBlast(),
                            "Damaged module left the current batch hot");

                    BlastFurnaceHotBlastModule.place(h.getLevel(), machine.getPos(), machine.getFrontFacing());
                    set(machine, "lastHotBlastValidationTick", Long.MIN_VALUE);
                    call(machine, "refreshHotBlastModule", true);
                    h.assertTrue(!machine.isCurrentBatchHotBlast(),
                            "Rebuilt module re-enabled hot mode for an already downgraded batch");

                    set(machine, "hotBlastHeat", 12_345L);
                    CompoundTag saved = saveState(h, machine);
                    set(machine, "hotBlastHeat", 0L);
                    loadState(h, machine, saved);
                    eq(h, machine.getHotBlastHeat(), 12_345,
                            "Complete module heat did not survive controller NBT reload");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void blastAirAndSteamAreAtomic(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, m -> {
            LargeSteamBlastFurnaceMachine machine = (LargeSteamBlastFurnaceMachine) m;
            List<SteamAirIntakeHatchPartMachine> intakes = list(m, "airIntakeHatches");
            h.assertTrue(!intakes.isEmpty(), "Fixture lacks blast air intake");
            for (var intake : intakes) intake.tank.getStorages()[0].setFluid(FluidStack.EMPTY);
            seedBatch(m, itemRecipe(), 2);
            set(m, "batchProgress", 7);
            fillSteam(m, 32_000);
            long before = steam(m);
            call(m, "runBatchTick");
            eq(h, progress(m), 1, "Air shortage did not roll back");
            eq(h, steam(m), before, "Air shortage wasted steam");
            h.assertTrue(machine.getStatusId().equals("auxiliary_shortfall"),
                    "Air shortage exposed the wrong controller status: " + machine.getStatusId());
            h.assertTrue(machine.getStatusText().getContents() instanceof TranslatableContents text
                            && text.getKey().equals(
                            "gregsteamexpansion.machine.large_steam_blast_furnace.low_blast"),
                    "Air shortage did not expose the dedicated blast-air text");
            h.assertTrue(machine.getStatusColor() == ChatFormatting.YELLOW,
                    "Air shortage did not use the warning status color");
            intakes.get(0).tank.getStorages()[0].setFluid(GTMaterials.Air.getFluid(8));
            fillSteam(m, 0);
            call(m, "runBatchTick");
            eq(h, intakes.get(0).tank.getFluidInTank(0).getAmount(), 8, "Steam shortage wasted blast air");
            fillSteam(m, 32_000);
            before = steam(m);
            call(m, "runBatchTick");
            eq(h, progress(m), 2, "Recovered blast inputs did not resume batch");
            eq(h, before - steam(m), 200, "Blast tick ignored locked steam demand");
            eq(h, intakes.get(0).tank.getFluidInTank(0).getAmount(), 0, "Blast tick did not consume 4 mB per parallel");
            h.assertTrue(machine.getStatusId().equals("working"),
                    "Recovered blast inputs did not restore the working status");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void advancedExhaustReducesNewBlastBatchSteamDemand(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, controller -> {
            LargeSteamBlastFurnaceMachine machine = (LargeSteamBlastFurnaceMachine) controller;
            replaceSteamExhaustHatch(h, machine);
            machine.setSteamThrottlePercent(50);
            ItemBusPartMachine input = inputBus(machine);
            fillOutputs(machine, false);
            GTRecipe recipe = recipeEndingWith(
                    GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES,
                    "wrought_iron_from_dust_coke_dust");
            input.getInventory().setStackInSlot(0,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Iron, 4));
            input.getInventory().setStackInSlot(1,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coke, 4));
            h.assertTrue((boolean) call(machine, "tryStartRecipe", recipe),
                    "Blast furnace did not start with an advanced exhaust hatch");
            eq(h, machine.getBatchParallel(), 4,
                    "Advanced exhaust hatch changed the selected parallel");
            eq(h, machine.getBatchSteamPerTick(), 268,
                    "50% throttle did not reduce the advanced exhaust demand from 536 to 268 mB/t");
            int unthrottledDuration = noviceDuration(recipe);
            eq(h, machine.getBatchDuration(), unthrottledDuration * 2,
                    "50% throttle did not double the advanced exhaust batch duration");
            eq(h, number(machine, "batchTotalSteamMb"), 536L * unthrottledDuration,
                    "Throttle changed the advanced exhaust batch's total steam consumption");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void blastFurnaceGuiAndJadeExposeAirShortfall(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, controller -> {
            LargeSteamBlastFurnaceMachine machine = (LargeSteamBlastFurnaceMachine) controller;
            ItemBusPartMachine input = inputBus(machine);
            List<SteamAirIntakeHatchPartMachine> intakes = list(machine, "airIntakeHatches");
            h.assertTrue(!intakes.isEmpty(), "Fixture lacks blast air intake");
            for (var intake : intakes) intake.tank.getStorages()[0].setFluid(FluidStack.EMPTY);
            fillOutputs(machine, false);
            fillSteam(machine, 32_000);

            GTRecipe recipe = recipeEndingWith(
                    GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES,
                    "wrought_iron_from_dust_coke_dust");
            input.getInventory().setStackInSlot(0,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Iron, 4));
            input.getInventory().setStackInSlot(1,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coke, 4));
            h.assertTrue((boolean) call(machine, "tryStartRecipe", recipe),
                    "Blast furnace did not start its real GUI/Jade recipe");
            set(machine, "batchProgress", 7);
            call(machine, "runBatchTick");
            h.assertTrue(machine.getStatusId().equals("auxiliary_shortfall"),
                    "Air-starved GUI/Jade fixture exposed the wrong controller state");

            var ui = machine.createUI(FakePlayerFactory.getMinecraft(h.getLevel()));
            h.assertTrue(labelText(ui.getFlatWidgetCollection(), 104, 2)
                            .equals(machine.getStatusText().getString()),
                    "Controller GUI status row diverged from the dedicated blast-air text");
            h.assertTrue(labelText(ui.getFlatWidgetCollection(), 104, 32).equals("4 / 96"),
                    "Controller GUI did not expose locked parallel as 4 / 96");
            h.assertTrue(!labelText(ui.getFlatWidgetCollection(), 104, 42).isBlank(),
                    "Controller GUI omitted the proficiency row");
            String intakeText = labelText(ui.getFlatWidgetCollection(), 104, 92);
            h.assertTrue(intakeText.equals(call(machine, "intakeText")),
                    "Controller GUI intake row diverged from the intake snapshot");
            h.assertTrue(!intakeText.equals("—"), "Controller GUI omitted its required intake row");

            CompoundTag serverData = new CompoundTag();
            Object provider = jadeProvider("ProcessorProvider");
            BlockAccessor accessor = jadeAccessor(machine, serverData);
            @SuppressWarnings("unchecked")
            IServerDataProvider<BlockAccessor> serverProvider =
                    (IServerDataProvider<BlockAccessor>) provider;
            serverProvider.appendServerData(serverData, accessor);
            h.assertTrue(serverData.contains("GregSteamExpansionProcessor", Tag.TAG_COMPOUND),
                    "Jade server provider omitted the processor snapshot");
            CompoundTag data = serverData.getCompound("GregSteamExpansionProcessor");
            h.assertTrue(data.getString("statusId").equals("auxiliary_shortfall"),
                    "Jade snapshot changed the controller status id");
            h.assertTrue(data.getString("statusKey").equals(
                            "gregsteamexpansion.machine.large_steam_blast_furnace.low_blast"),
                    "Jade snapshot lost the dedicated blast-air translation key");
            eq(h, data.getInt("parallel"), 4, "Jade snapshot changed locked parallel");
            eq(h, data.getInt("parallelCap"), 96, "Jade snapshot changed the parallel cap");
            eq(h, data.getLong("steamInputLimit"), machine.getSteamInputLimitPerTick(),
                    "Jade snapshot changed the steam input limit used by the demand bar");
            h.assertTrue(data.getBoolean("hasIntake"), "Jade snapshot omitted the intake state");
            h.assertTrue(data.getString("intakeStatusId").equals(machine.getAirIntakeStatusId()),
                    "Jade snapshot changed the intake status id");
            eq(h, data.getLong("intakeStored"), machine.getAirIntakeStored(),
                    "Jade snapshot changed stored blast air");
            eq(h, data.getLong("intakeCapacity"), machine.getAirIntakeCapacity(),
                    "Jade snapshot changed intake capacity");

            List<Component> tooltipLines = new ArrayList<>();
            ITooltip tooltip = jadeTooltip(tooltipLines);
            ((IBlockComponentProvider) provider).appendTooltip(tooltip, accessor, null);
            h.assertTrue(tooltipContains(tooltipLines, Component.translatable(
                            "gregsteamexpansion.jade.steam_processor.status",
                            Component.translatable(
                                    "gregsteamexpansion.machine.large_steam_blast_furnace.low_blast"))),
                    "Jade client tooltip did not render the dedicated blast-air status");
            h.assertTrue(tooltipContains(tooltipLines, Component.translatable(
                            "gregsteamexpansion.jade.bar.parallel", "4", "96")),
                    "Jade client tooltip did not render parallel as 4 / 96");
            h.assertTrue(tooltipLines.stream().anyMatch(line ->
                            line.getContents() instanceof TranslatableContents text
                                    && text.getKey().equals(
                                    "gregsteamexpansion.jade.steam_processor.intake")),
                    "Jade client tooltip omitted the intake status line");
            h.assertTrue(tooltipTranslationKeys(tooltipLines).equals(List.of(
                            "gregsteamexpansion.jade.steam_processor.status",
                            "gregsteamexpansion.jade.steam_processor.recipe",
                            "gtceu.jade.progress_sec",
                            "gregsteamexpansion.jade.bar.parallel",
                            "gregsteamexpansion.jade.bar.fluid_stored",
                            "gtceu.jade.fluid_use",
                            "gregsteamexpansion.jade.steam_processor.intake",
                            "gregsteamexpansion.jade.bar.fluid_stored")),
                    "Processor Jade tooltip stopped using GTCEu-style bar text or changed row order");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void blastFurnaceDistinguishesBlockedExhaustAndSteamShortage(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, controller -> {
            LargeSteamBlastFurnaceMachine machine = (LargeSteamBlastFurnaceMachine) controller;
            ItemBusPartMachine input = inputBus(machine);
            List<SteamAirIntakeHatchPartMachine> intakes = list(machine, "airIntakeHatches");
            h.assertTrue(!intakes.isEmpty(), "Fixture lacks blast air intake");
            for (var intake : intakes) intake.tank.getStorages()[0].setFluid(FluidStack.EMPTY);
            intakes.get(0).tank.getStorages()[0].setFluid(GTMaterials.Air.getFluid(32_000));
            fillOutputs(machine, false);

            GTRecipe recipe = recipeEndingWith(
                    GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES,
                    "wrought_iron_from_dust_coke_dust");
            input.getInventory().setStackInSlot(0,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Iron, 4));
            input.getInventory().setStackInSlot(1,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coke, 4));
            h.assertTrue((boolean) call(machine, "tryStartRecipe", recipe),
                    "Blast furnace did not start its real four-parallel recipe");
            eq(h, machine.getBatchParallel(), 4, "Real blast recipe locked the wrong parallel");
            eq(h, machine.getBatchSteamPerTick(), 800, "Real blast recipe locked the wrong steam demand");
            set(machine, "batchProgress", 7);

            SteamExhaustHatchMachine exhaust = machine.getParts().stream()
                    .filter(SteamExhaustHatchMachine.class::isInstance)
                    .map(SteamExhaustHatchMachine.class::cast).findFirst().orElseThrow();
            BlockPos front = exhaust.getPos().relative(exhaust.getFrontFacing());
            h.assertTrue(!exhaust.isExhaustBlocked(), "Fixture exhaust channel is blocked");
            fillSteam(machine, 32_000);
            long steamBefore = steam(machine);
            long airBefore = air(machine);

            h.getLevel().setBlockAndUpdate(front, Blocks.STONE.defaultBlockState());
            tick(machine);
            eq(h, progress(machine), 7, "Blocked exhaust changed batch progress");
            eq(h, steam(machine), steamBefore, "Blocked exhaust consumed steam");
            eq(h, air(machine), airBefore, "Blocked exhaust consumed blast air");
            eq(h, demand(machine), 0, "Blocked exhaust exposed a current steam demand");
            h.assertTrue(!machine.isConsumingSteam(), "Blocked exhaust reported active steam consumption");
            h.assertTrue(machine.getStatusId().equals("exhaust_obstructed"),
                    "Blocked exhaust exposed the wrong status: " + machine.getStatusId());

            h.getLevel().setBlockAndUpdate(front, Blocks.AIR.defaultBlockState());
            fillSteam(machine, 0);
            airBefore = air(machine);
            tick(machine);
            eq(h, progress(machine), 1, "Steam shortage did not roll batch progress back");
            eq(h, steam(machine), 0, "Steam shortage changed empty steam storage");
            eq(h, air(machine), airBefore, "Steam shortage consumed blast air");
            eq(h, demand(machine), 800, "Steam shortage hid the resumable steam demand");
            h.assertTrue(!machine.isConsumingSteam(), "Steam shortage reported active steam consumption");
            h.assertTrue(machine.getStatusId().equals("low_steam"),
                    "Steam shortage exposed the wrong status: " + machine.getStatusId());

            fillSteam(machine, 32_000);
            steamBefore = steam(machine);
            airBefore = air(machine);
            tick(machine);
            eq(h, progress(machine), 2, "Refilled blast furnace did not resume from rollback progress");
            eq(h, steamBefore - steam(machine), 800, "Resumed blast tick consumed the wrong steam amount");
            eq(h, airBefore - air(machine), 16, "Resumed blast tick consumed the wrong air amount");
            h.assertTrue(machine.isConsumingSteam(), "Resumed blast tick did not report steam consumption");
            h.assertTrue(machine.getStatusId().equals("working"),
                    "Refilled blast furnace did not restore the working status");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void blastFurnacePersistsAndAtomicallyDeliversRealPendingOutput(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, controller -> {
            LargeSteamBlastFurnaceMachine machine = (LargeSteamBlastFurnaceMachine) controller;
            ItemBusPartMachine input = inputBus(machine);
            List<SteamAirIntakeHatchPartMachine> intakes = list(machine, "airIntakeHatches");
            h.assertTrue(!intakes.isEmpty(), "Fixture lacks blast air intake");
            for (var intake : intakes) intake.tank.getStorages()[0].setFluid(FluidStack.EMPTY);
            intakes.get(0).tank.getStorages()[0].setFluid(GTMaterials.Air.getFluid(32_000));
            fillOutputs(machine, false);
            fillSteam(machine, 32_000);

            GTRecipe recipe = recipeEndingWith(
                    GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES,
                    "wrought_iron_from_dust_coke_dust");
            Item wroughtIron = ChemicalHelper.get(TagPrefix.ingot, GTMaterials.WroughtIron).getItem();
            input.getInventory().setStackInSlot(0,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Iron, 4));
            input.getInventory().setStackInSlot(1,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coke, 4));
            h.assertTrue((boolean) call(machine, "tryStartRecipe", recipe),
                    "Blast furnace did not start its real pending-output recipe");
            eq(h, machine.getBatchParallel(), 4, "Pending-output recipe locked the wrong parallel");
            eq(h, inputItemCount(input, ChemicalHelper.get(TagPrefix.dust, GTMaterials.Iron).getItem()), 0,
                    "Controller retained iron input after starting the batch");
            eq(h, inputItemCount(input, ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coke).getItem()), 0,
                    "Controller retained coke input after starting the batch");
            eq(h, machine.getPendingTotalCount(), 0, "Live batch appeared in the pending-output cache");
            set(machine, "batchProgress", 7);

            var ui = machine.createUI(FakePlayerFactory.getMinecraft(h.getLevel()));
            List<ToggleButtonWidget> powerButtons = ui.getFlatWidgetCollection().stream()
                    .filter(ToggleButtonWidget.class::isInstance)
                    .map(ToggleButtonWidget.class::cast)
                    .filter(widget -> widget.getSelfPositionX() == 6)
                    .toList();
            h.assertTrue(powerButtons.size() == 1, "Controller UI did not expose exactly one power button");
            ToggleButtonWidget powerButton = powerButtons.get(0);
            powerButton.detectAndSendChanges();
            h.assertTrue(powerButton.isPressed(), "Controller UI power button did not reflect enabled state");
            List<ToggleButtonWidget> overclockButtons = ui.getFlatWidgetCollection().stream()
                    .filter(ToggleButtonWidget.class::isInstance)
                    .map(ToggleButtonWidget.class::cast)
                    .filter(widget -> widget.getSelfPositionX() == SteamProcessorUI.WIDTH - 24)
                    .toList();
            h.assertTrue(overclockButtons.size() == 1,
                    "Controller UI did not build its synchronized large-steam overclock button");
            ToggleButtonWidget overclockButton = overclockButtons.get(0);
            overclockButton.detectAndSendChanges();
            h.assertTrue(!overclockButton.isVisible() && !overclockButton.isActive(),
                    "Large-steam overclock button was available without a large steam supply hatch");
            guiToggle(powerButton, false);
            h.assertTrue(!machine.isWorkingEnabled(), "GUI power button did not disable the blast furnace");
            long steamBefore = steam(machine);
            long airBefore = air(machine);
            tick(machine);
            eq(h, progress(machine), 7, "GUI-disabled blast furnace changed batch progress");
            eq(h, steam(machine), steamBefore, "GUI-disabled blast furnace consumed steam");
            eq(h, air(machine), airBefore, "GUI-disabled blast furnace consumed blast air");
            h.assertTrue(machine.getStatusId().equals("working_disabled"),
                    "GUI-disabled blast furnace exposed the wrong status");

            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(machine.getPos()),
                    machine.getFrontFacing(), machine.getPos(), false);
            InteractionResult malletResult = (InteractionResult) call(machine, "onSoftMalletClick",
                    FakePlayerFactory.getMinecraft(h.getLevel()), InteractionHand.MAIN_HAND,
                    machine.getFrontFacing(), hit);
            h.assertTrue(malletResult.consumesAction(), "Soft mallet did not handle the controller click");
            h.assertTrue(machine.isWorkingEnabled(), "Soft mallet did not re-enable the blast furnace");
            tick(machine);
            eq(h, progress(machine), 8, "Soft-mallet-enabled blast furnace did not resume");
            eq(h, steamBefore - steam(machine), 800,
                    "Soft-mallet-enabled blast furnace consumed the wrong steam amount");
            eq(h, airBefore - air(machine), 16,
                    "Soft-mallet-enabled blast furnace consumed the wrong air amount");

            malletResult = (InteractionResult) call(machine, "onSoftMalletClick",
                    FakePlayerFactory.getMinecraft(h.getLevel()), InteractionHand.MAIN_HAND,
                    machine.getFrontFacing(), hit);
            h.assertTrue(malletResult.consumesAction() && !machine.isWorkingEnabled(),
                    "Second soft-mallet click did not disable the blast furnace");
            guiToggle(powerButton, true);
            h.assertTrue(machine.isWorkingEnabled(), "GUI power button did not re-enable the blast furnace");

            fillOutputs(machine, true);
            set(machine, "batchProgress", machine.getBatchDuration() - 1);
            tick(machine);
            h.assertTrue(!(boolean) get(machine, "hasBatch"), "Completed blast batch remained active");
            eq(h, count(pending(machine), wroughtIron), 4,
                    "Blocked real recipe did not create four pending wrought-iron ingots");
            long pendingTotal = machine.getPendingTotalCount();
            h.assertTrue(pendingTotal >= 4,
                    "Blocked real recipe lost its guaranteed output when rolling chance products");
            h.assertTrue(machine.getStatusId().equals("insufficient_outputs"),
                    "Blocked real output did not expose the output-full status");

            CompoundTag saved = saveState(h, machine);
            clearProcessorState(machine);
            loadState(h, machine, saved);
            eq(h, count(pending(machine), wroughtIron), 4,
                    "Blast-furnace pending output did not survive controller NBT reload");
            eq(h, machine.getPendingTotalCount(), pendingTotal,
                    "Blast-furnace chance-output snapshot changed during controller NBT reload");
            h.assertTrue((boolean) call(get(machine, "pendingBuffer"), "hasAny"),
                    "Reloaded pending buffer detached from the restored output list");

            ItemBusPartMachine output = outputs(machine).get(0);
            output.getInventory().setStackInSlot(0, new ItemStack(wroughtIron, 62));
            h.assertTrue(!(boolean) call(machine, "deliverPendingOutputs"),
                    "Insufficient output space accepted the whole pending batch");
            eq(h, outputCount(machine, wroughtIron), 62,
                    "Failed pending-output delivery partially mutated the output bus");
            eq(h, count(pending(machine), wroughtIron), 4,
                    "Failed pending-output delivery partially consumed the controller cache");
            eq(h, machine.getPendingTotalCount(), pendingTotal,
                    "Failed pending-output delivery changed the rolled chance products");

            for (int slot = 1; slot < output.getInventory().getSlots(); slot++) {
                output.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
            }
            h.assertTrue((boolean) call(machine, "deliverPendingOutputs"),
                    "Sufficient output space rejected the restored pending batch");
            eq(h, outputCount(machine, wroughtIron), 66,
                    "Restored pending output was duplicated or lost during whole-batch delivery");
            eq(h, machine.getPendingTotalCount(), 0, "Successful delivery left pending controller output");
            h.assertTrue(!(boolean) call(get(machine, "pendingBuffer"), "hasAny"),
                    "Successful delivery did not clear the pending buffer");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void blastFurnaceControllerRemovalDropsOnlyPendingItems(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, controller -> {
            LargeSteamBlastFurnaceMachine machine = (LargeSteamBlastFurnaceMachine) controller;
            ItemBusPartMachine input = inputBus(machine);
            ItemBusPartMachine output = outputs(machine).get(0);
            List<FluidHatchPartMachine> supplyHatches = List.copyOf(supplies(machine));
            SteamAirIntakeHatchPartMachine intake = GSESteamEngineTestSupport
                    .<SteamAirIntakeHatchPartMachine>list(machine, "airIntakeHatches").get(0);
            Item wroughtIron = ChemicalHelper.get(TagPrefix.ingot, GTMaterials.WroughtIron).getItem();

            pending(machine).add(new ItemStack(wroughtIron, 64));
            pending(machine).add(new ItemStack(wroughtIron, 6));
            GSESteamEngineTestSupport.<FluidStack>list(machine, "pendingFluids")
                    .add(GTMaterials.Water.getFluid(750));
            set(machine, "hasBatch", true);
            set(machine, "batchInputDisplay", new ItemStack(Items.EMERALD, 3));
            input.getInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
            output.getInventory().setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 7));
            fillSteam(machine, 12_345);
            intake.tank.getStorages()[0].setFluid(GTMaterials.Air.getFluid(23_456));
            long steamBefore = steam(machine);

            BlockPos controllerPos = machine.getPos();
            AABB dropArea = new AABB(controllerPos).inflate(2.0);
            List<ItemEntity> existingDrops = h.getLevel().getEntitiesOfClass(ItemEntity.class, dropArea);
            h.assertTrue(h.getLevel().destroyBlock(controllerPos, true),
                    "Physical controller removal was rejected");
            h.assertTrue(h.getLevel().getBlockState(controllerPos).isAir(),
                    "Destroyed blast-furnace controller block remained in the world");

            List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, dropArea).stream()
                    .filter(entity -> !existingDrops.contains(entity))
                    .toList();
            Item controllerItem = GSEMachines.LARGE_STEAM_BLAST_FURNACE.asStack().getItem();
            int pendingDrops = drops.stream().filter(entity -> entity.getItem().is(wroughtIron))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
            int controllerDrops = drops.stream().filter(entity -> entity.getItem().is(controllerItem))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
            eq(h, pendingDrops, 70, "Controller removal lost or duplicated pending item outputs");
            eq(h, controllerDrops, 1, "Controller removal produced the wrong controller-item count");
            h.assertTrue(drops.stream().allMatch(entity ->
                            entity.getItem().is(wroughtIron) || entity.getItem().is(controllerItem)),
                    "Controller removal dropped an item other than the controller and pending outputs");
            eq(h, drops.stream().filter(entity -> entity.getItem().is(Items.EMERALD))
                    .mapToInt(entity -> entity.getItem().getCount()).sum(), 0,
                    "Controller removal dropped the live batch input display");
            eq(h, drops.stream().filter(entity -> entity.getItem().is(Items.DIAMOND))
                    .mapToInt(entity -> entity.getItem().getCount()).sum(), 0,
                    "Controller removal copied the input-bus inventory");
            eq(h, drops.stream().filter(entity -> entity.getItem().is(Items.GOLD_INGOT))
                    .mapToInt(entity -> entity.getItem().getCount()).sum(), 0,
                    "Controller removal copied the output-bus inventory");

            eq(h, inputItemCount(input, Items.DIAMOND), 5,
                    "Controller removal changed the surviving input-bus inventory");
            eq(h, output.getInventory().getStackInSlot(0).getCount(), 7,
                    "Controller removal changed the surviving output-bus inventory");
            eq(h, supplyHatches.stream().mapToLong(hatch ->
                            hatch.tank.getFluidInTank(0).getAmount()).sum(), steamBefore,
                    "Controller removal changed steam stored in surviving supply hatches");
            eq(h, intake.tank.getFluidInTank(0).getAmount(), 23_456,
                    "Controller removal changed air stored in the surviving intake hatch");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void blastFurnaceParallelUsesTightestLimit(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, controller -> {
            LargeSteamBlastFurnaceMachine machine = (LargeSteamBlastFurnaceMachine) controller;
            ItemBusPartMachine input = inputBus(machine);
            GTRecipe recipe = GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES
                    .recipeBuilder(GregSteamExpansion.id("blast_parallel_limit_probe"))
                    .inputItems(new ItemStack(Items.COBBLESTONE))
                    .outputItems(new ItemStack(Items.IRON_INGOT))
                    .duration(100).buildRawRecipe();

            // Ten inputs are available, but only six ingots fit in the outputs.
            fillOutputs(machine, true);
            ItemBusPartMachine output = outputs(machine).get(0);
            output.getInventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 58));
            input.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 10));
            h.assertTrue((boolean) call(machine, "tryStartRecipe", recipe),
                    "Blast furnace rejected the output-limited probe recipe");
            eq(h, machine.getBatchParallel(), 6,
                    "Blast furnace did not let output capacity limit parallel to six");
            eq(h, inputItemCount(input, Items.COBBLESTONE), 4,
                    "Output-limited batch consumed the wrong input quantity");

            // With empty outputs, eleven inputs become the tightest limit.
            clearActiveProcessorBatch(machine);
            fillOutputs(machine, false);
            clearInventory(input);
            input.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 11));
            h.assertTrue((boolean) call(machine, "tryStartRecipe", recipe),
                    "Blast furnace rejected the input-limited probe recipe");
            eq(h, machine.getBatchParallel(), 11,
                    "Blast furnace did not let available inputs limit parallel to eleven");
            eq(h, inputItemCount(input, Items.COBBLESTONE), 0,
                    "Input-limited batch left a consumed input behind");

            // Inputs and outputs now allow more than the machine's fixed cap,
            // but the fixture's 9,600 mB/t supply can sustain only 48-way
            // parallel at this recipe's locked 200 mB/t per operation.
            clearActiveProcessorBatch(machine);
            fillOutputs(machine, false);
            clearInventory(input);
            h.assertTrue(input.getInventory().getSlots() >= 2,
                    "Blast-furnace input bus lacks two slots for the cap probe");
            input.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
            input.getInventory().setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
            h.assertTrue((boolean) call(machine, "tryStartRecipe", recipe),
                    "Blast furnace rejected the machine-capped probe recipe");
            eq(h, machine.getBatchParallel(), 48,
                    "Blast furnace did not lower parallel to its sustainable supply limit");
            eq(h, inputItemCount(input, Items.COBBLESTONE), 80,
                    "Steam-limited batch consumed inputs above its locked parallel");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void blastFurnaceProficiencyLocksNextBatchAndResetsOnRecipeChange(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, controller -> {
            LargeSteamBlastFurnaceMachine machine = (LargeSteamBlastFurnaceMachine) controller;
            ItemBusPartMachine input = inputBus(machine);
            GTRecipe first = GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES
                    .recipeBuilder(GregSteamExpansion.id("blast_proficiency_first"))
                    .inputItems(new ItemStack(Items.COBBLESTONE))
                    .outputItems(new ItemStack(Items.IRON_INGOT))
                    .duration(100).buildRawRecipe();
            GTRecipe second = GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES
                    .recipeBuilder(GregSteamExpansion.id("blast_proficiency_second"))
                    .inputItems(new ItemStack(Items.DIRT))
                    .outputItems(new ItemStack(Items.GOLD_INGOT))
                    .duration(100).buildRawRecipe();

            int familiar = GSEDifficultyState.blastFurnaceRequiredOperations(false, 1);
            set(machine, "proficiencyRecipeId", first.getId().toString());
            set(machine, "proficiencyOperations", familiar - 1);
            fillOutputs(machine, false);
            fillSteam(machine, 32_000);
            GSESteamEngineTestSupport.<SteamAirIntakeHatchPartMachine>list(machine, "airIntakeHatches").get(0)
                    .tank.getStorages()[0].setFluid(GTMaterials.Air.getFluid(64_000));
            input.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
            h.assertTrue((boolean) call(machine, "tryStartRecipe", first),
                    "Blast furnace rejected the threshold-crossing proficiency batch");
            eq(h, machine.getBatchDuration(), durationAt(first, 0),
                    "Threshold-crossing batch did not retain its novice duration");
            set(machine, "batchProgress", machine.getBatchDuration() - 1);
            call(machine, "runBatchTick");
            eq(h, machine.getProficiencyOperations(), familiar,
                    "Completed parallel operation did not cross the familiar threshold");
            eq(h, machine.getProficiencyLevel(), 1,
                    "Completed batch did not advance proficiency for the next batch");

            fillOutputs(machine, false);
            input.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
            h.assertTrue((boolean) call(machine, "tryStartRecipe", first),
                    "Blast furnace rejected the familiar proficiency batch");
            eq(h, machine.getBatchDuration(), durationAt(first, 1),
                    "Next same-recipe batch did not use familiar duration");
            clearActiveProcessorBatch(machine);

            set(machine, "proficiencyOperations",
                    GSEDifficultyState.blastFurnaceRequiredOperations(false, 3));
            fillOutputs(machine, false);
            clearInventory(input);
            input.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
            h.assertTrue((boolean) call(machine, "tryStartRecipe", first),
                    "Blast furnace rejected the mastered proficiency batch");
            eq(h, machine.getBatchDuration(), durationAt(first, 3),
                    "Mastered same-recipe batch used the wrong duration");
            clearActiveProcessorBatch(machine);

            fillOutputs(machine, false);
            clearInventory(input);
            input.getInventory().setStackInSlot(0, new ItemStack(Items.DIRT));
            h.assertTrue((boolean) call(machine, "tryStartRecipe", second),
                    "Blast furnace rejected a recipe-switch batch");
            eq(h, machine.getBatchDuration(), durationAt(second, 0),
                    "Different recipe did not restart at novice duration");
            eq(h, machine.getProficiencyOperations(), 0,
                    "Different recipe did not clear completed proficiency operations");
            h.assertTrue(machine.getProficiencyRecipeId().equals(second.getId().toString()),
                    "Different recipe did not replace the tracked proficiency recipe id");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void blastFurnaceProficiencyPersistsWithoutChangingJade(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, controller -> {
            LargeSteamBlastFurnaceMachine machine = (LargeSteamBlastFurnaceMachine) controller;
            String recipeId = GregSteamExpansion.id("persisted_proficiency").toString();
            set(machine, "proficiencyRecipeId", recipeId);
            set(machine, "proficiencyOperations",
                    GSEDifficultyState.blastFurnaceRequiredOperations(false, 2));
            CompoundTag saved = saveState(h, machine);
            set(machine, "proficiencyRecipeId", "");
            set(machine, "proficiencyOperations", 0);
            loadState(h, machine, saved);
            h.assertTrue(machine.getProficiencyRecipeId().equals(recipeId),
                    "Blast-furnace proficiency recipe did not survive NBT reload");
            eq(h, machine.getProficiencyLevel(), 2,
                    "Blast-furnace proficiency level did not survive NBT reload");
            h.assertTrue(!(machine instanceof IDropSaveMachine),
                    "Blast-furnace controller unexpectedly saves proficiency into its dropped item");

            var ui = machine.createUI(FakePlayerFactory.getMinecraft(h.getLevel()));
            h.assertTrue(labelText(ui.getFlatWidgetCollection(), 104, 42).contains("("),
                    "Controller GUI did not add the proficiency row after parallel");

            CompoundTag serverData = new CompoundTag();
            BlockAccessor accessor = jadeAccessor(machine, serverData);
            @SuppressWarnings("unchecked")
            IServerDataProvider<BlockAccessor> provider =
                    (IServerDataProvider<BlockAccessor>) jadeProvider("ProcessorProvider");
            provider.appendServerData(serverData, accessor);
            CompoundTag data = serverData.getCompound("GregSteamExpansionProcessor");
            h.assertTrue(!data.contains("proficiencyRecipeId") && !data.contains("proficiencyOperations"),
                    "Processor Jade protocol unexpectedly exposed blast-furnace proficiency");

            machine.onMachineRemoved();
            h.assertTrue(machine.getProficiencyRecipeId().isEmpty()
                            && machine.getProficiencyOperations() == 0,
                    "Controller removal did not reset blast-furnace proficiency");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void legacyBlastBatchKeepsLockedDurationAndSeedsProficiency(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, controller -> {
            LargeSteamBlastFurnaceMachine machine = (LargeSteamBlastFurnaceMachine) controller;
            GTRecipe legacyRecipe = GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES
                    .recipeBuilder(GregSteamExpansion.id("legacy_blast_proficiency_seed"))
                    .outputItems(new ItemStack(Items.IRON_INGOT))
                    .duration(500).buildRawRecipe();

            // A pre-migration active batch has its old locked duration but no
            // proficiency fields. Reloading must not recalculate that duration.
            seedBatch(machine, legacyRecipe, 2);
            set(machine, "batchDurationTicks", 200);
            set(machine, "proficiencyRecipeId", "");
            set(machine, "proficiencyOperations", 0);
            CompoundTag saved = saveState(h, machine);
            clearProcessorState(machine);
            loadState(h, machine, saved);
            eq(h, machine.getBatchDuration(), 200,
                    "Legacy active batch did not preserve its locked pre-migration duration");

            call(machine, "completeBatch");
            h.assertTrue(machine.getProficiencyRecipeId().equals(legacyRecipe.getId().toString()),
                    "Legacy batch completion did not seed the tracked recipe id");
            eq(h, machine.getProficiencyOperations(), 2,
                    "Legacy batch completion did not credit its completed parallel operations");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void blastFurnacePrefersLastRecipeAfterReload(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, controller -> {
            LargeSteamBlastFurnaceMachine machine = (LargeSteamBlastFurnaceMachine) controller;
            ItemBusPartMachine input = inputBus(machine);
            GTRecipe fallback = GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES
                    .recipeBuilder(GregSteamExpansion.id("blast_preference_fallback"))
                    .inputItems(new ItemStack(Items.COBBLESTONE))
                    .outputItems(new ItemStack(Items.STONE))
                    .duration(100).buildRawRecipe();
            GTRecipe preferred = GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES
                    .recipeBuilder(GregSteamExpansion.id("blast_preference_selected"))
                    .inputItems(new ItemStack(Items.COBBLESTONE))
                    .outputItems(new ItemStack(Items.IRON_INGOT))
                    .duration(100).buildRawRecipe();
            List<GTRecipe> registrationOrder = List.of(fallback, preferred);
            Map<RecipeCapability<?>, Map<Object, List<GTRecipe>>> byContent = new HashMap<>();
            byContent.put(ItemRecipeCapability.CAP,
                    Map.of(Items.COBBLESTONE, registrationOrder));
            SteamRecipeCache.Entry cache = new SteamRecipeCache.Entry(
                    registrationOrder,
                    Map.of(fallback.getId(), fallback, preferred.getId(), preferred),
                    Map.copyOf(byContent));
            set(machine, "recipeCache", cache);

            // A successful preferred batch writes the persisted preference.
            fillOutputs(machine, false);
            input.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
            h.assertTrue((boolean) call(machine, "tryStartRecipe", preferred),
                    "Blast furnace could not establish the preferred recipe");
            h.assertTrue(get(machine, "preferredRecipeId").equals(preferred.getId().toString()),
                    "Successful batch did not record its recipe preference");
            clearActiveProcessorBatch(machine);

            CompoundTag saved = saveState(h, machine);
            set(machine, "preferredRecipeId", "");
            loadState(h, machine, saved);
            set(machine, "recipeCache", cache);
            h.assertTrue(get(machine, "preferredRecipeId").equals(preferred.getId().toString()),
                    "Blast-furnace recipe preference did not survive NBT reload");

            // Both recipes match; the preferred recipe is second in registration
            // order, so selecting it proves preference is evaluated first.
            clearInventory(input);
            input.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
            call(machine, "tryStartBatch");
            h.assertTrue(get(machine, "batchRecipeId").equals(preferred.getId().toString()),
                    "Reloaded blast furnace chose registration order before its preferred recipe");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void blastFurnaceClosesWroughtIronAndSteelChain(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, controller -> {
            LargeSteamBlastFurnaceMachine machine = (LargeSteamBlastFurnaceMachine) controller;
            eq(h, replaceSteamSupplyHatches(h, machine, 1), 1,
                    "Blast-furnace fixture did not install exactly one large steam supply hatch");
            ItemBusPartMachine input = machine.getParts().stream()
                    .filter(ItemBusPartMachine.class::isInstance)
                    .map(ItemBusPartMachine.class::cast)
                    .filter(bus -> bus.getInventory().getHandlerIO() == IO.IN)
                    .findFirst().orElseThrow();
            List<SteamAirIntakeHatchPartMachine> intakes = list(machine, "airIntakeHatches");
            h.assertTrue(!intakes.isEmpty(), "Blast-furnace fixture lacks an air intake hatch");
            intakes.get(0).tank.getStorages()[0].setFluid(GTMaterials.Air.getFluid(64_000));
            fillOutputs(machine, false);

            List<GTRecipe> upstreamSteel = GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES
                    .getRecipesInCategory(GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES.getCategory()).stream()
                    .filter(recipe -> recipe.getId().getNamespace().equals("gtceu"))
                    .filter(recipe -> recipe.getId().getPath().contains("steel_from_"))
                    .toList();
            eq(h, upstreamSteel.size(), 18,
                    "Primitive blast furnace upstream steel recipe inventory changed");
            for (GTRecipe recipe : upstreamSteel) {
                h.assertTrue((boolean) call(machine, "acceptsRecipe", recipe),
                        "Large steam blast furnace rejected upstream recipe " + recipe.getId());
                h.assertTrue((boolean) call(machine, "passesVoltageGate", recipe),
                        "Large steam blast furnace voltage-gated EU-less recipe " + recipe.getId());
            }

            GTRecipe wroughtRecipe = recipeEndingWith(
                    GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES,
                    "wrought_iron_from_dust_coke_dust");
            input.getInventory().setStackInSlot(0,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Iron, 4));
            input.getInventory().setStackInSlot(1,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coke, 4));
            runBlastRecipe(h, machine, wroughtRecipe, input,
                    ChemicalHelper.get(TagPrefix.ingot, GTMaterials.WroughtIron).getItem());

            fillOutputs(machine, false);
            GTRecipe steelRecipe = recipeEndingWith(
                    GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES,
                    "steel_from_coke_dust_wrought");
            input.getInventory().setStackInSlot(0,
                    ChemicalHelper.get(TagPrefix.ingot, GTMaterials.WroughtIron, 4));
            input.getInventory().setStackInSlot(1,
                    ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coke, 4));
            runBlastRecipe(h, machine, steelRecipe,
                    input, ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Steel).getItem());
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void blastFurnaceFullLoadUsesFourLargeSuppliesAndEightIntakes(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, controller -> {
            LargeSteamBlastFurnaceMachine machine = (LargeSteamBlastFurnaceMachine) controller;
            configureBlastFurnaceFullLoadHatches(h, machine);

            List<FluidHatchPartMachine> supplies = supplies(machine);
            long largeSupplies = supplies.stream()
                    .filter(hatch -> hatch.self().getDefinition() == GSEMachines.LARGE_STEAM_SUPPLY_HATCH)
                    .count();
            eq(h, largeSupplies, 4, "Full-load fixture did not collect four large steam supply hatches");
            List<SteamAirIntakeHatchPartMachine> intakes = list(machine, "airIntakeHatches");
            eq(h, intakes.size(), 8, "Full-load fixture did not collect eight air intake hatches");
            startFullLoadBlastRecipe(h, machine);

            for (FluidHatchPartMachine hatch : supplies) {
                boolean large = hatch.self().getDefinition() == GSEMachines.LARGE_STEAM_SUPPLY_HATCH;
                hatch.tank.getStorages()[0].setFluid(
                        large ? GTMaterials.Steam.getFluid(4_800) : FluidStack.EMPTY);
            }
            for (SteamAirIntakeHatchPartMachine intake : intakes) {
                intake.tank.getStorages()[0].setFluid(GTMaterials.Air.getFluid(48));
            }
            eq(h, steam(machine), 19_200, "Full-load fixture did not stage exactly one steam tick");
            eq(h, air(machine), 384, "Full-load fixture did not stage exactly one blast-air tick");

            call(machine, "runBatchTick");
            eq(h, machine.getBatchProgress(), 1, "Full-load batch did not advance exactly one tick");
            eq(h, steam(machine), 0, "Four large supply hatches did not atomically provide 19,200 mB");
            eq(h, air(machine), 0, "Eight air intakes did not atomically provide 384 mB");
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void blastFurnaceEightIntakesSustainFullLoad(GameTestHelper h) {
        formed(h, GSEMachines.LARGE_STEAM_BLAST_FURNACE, controller -> {
            LargeSteamBlastFurnaceMachine machine = (LargeSteamBlastFurnaceMachine) controller;
            configureBlastFurnaceFullLoadHatches(h, machine);
            startFullLoadBlastRecipe(h, machine);

            List<FluidHatchPartMachine> supplies = supplies(machine);
            List<SteamAirIntakeHatchPartMachine> intakes = list(machine, "airIntakeHatches");
            eq(h, intakes.size(), 8, "Sustained-load fixture did not collect eight air intakes");
            for (SteamAirIntakeHatchPartMachine intake : intakes) {
                h.assertTrue(h.getLevel().getBlockState(intake.getPos().relative(intake.getFrontFacing())).isAir(),
                        "Sustained-load intake does not face a clear air block at " + intake.getPos());
                intake.tank.getStorages()[0].setFluid(GTMaterials.Air.getFluid(4_000));
                set(intake, "cycleTimer", 0);
                set(intake, "syncedCycleTimer", 0);
            }

            int sustainedTicks = SteamAirIntakeHatchPartMachine.COLLECT_CYCLE_TICKS * 4;
            for (int tick = 1; tick <= sustainedTicks; tick++) {
                if ((tick - 1) % 40 == 0) refillFullLoadSteam(h, supplies);
                for (SteamAirIntakeHatchPartMachine intake : intakes) {
                    call(intake, "updateIntake");
                }
                long steamBefore = steam(machine);
                call(machine, "runBatchTick");
                eq(h, machine.getBatchProgress(), tick,
                        "Full-load batch stalled during passive air collection at tick " + tick);
                eq(h, steamBefore - steam(machine), 19_200,
                        "Full-load tick consumed the wrong steam budget at tick " + tick);
            }

            eq(h, air(machine), 34_560,
                    "Four passive collection cycles did not leave the expected growing air reserve");
            for (SteamAirIntakeHatchPartMachine intake : intakes) {
                h.assertTrue(intake.getIntakeStatus()
                                == SteamAirIntakeHatchPartMachine.IntakeStatus.COLLECTING,
                        "Full-load intake stopped collecting: " + intake.getIntakeStatus().getId());
            }
        });
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void blastFurnaceAcceptsCapabilityInputPart(GameTestHelper h) {
        var definition = GSEMachines.LARGE_STEAM_BLAST_FURNACE;
        var m = GSEStructureTestUtils.placeShape(h, definition, definition.getMatchingShapes().get(0));
        h.assertTrue(m != null, "Missing blast-furnace fixture controller");

        var creativeInput = ForgeRegistries.BLOCKS.getValue(
                ResourceLocation.fromNamespaceAndPath("gtmthings", "creative_item_input_bus"));
        h.assertTrue(creativeInput != null && creativeInput != Blocks.AIR,
                "Development GTM Things creative item input bus is unavailable");

        int replaced = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                m.getPos().offset(-15, 0, -15), m.getPos().offset(15, 15, 15))) {
            if (h.getLevel().getBlockState(pos).is(GTMachines.STEAM_IMPORT_BUS.getBlock())) {
                h.getLevel().setBlockAndUpdate(pos, creativeInput.defaultBlockState());
                replaced++;
            }
        }
        eq(h, replaced, 1, "Expected exactly one representative steam input bus");

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(m.isFormed(),
                        "Capability-compatible creative item input bus did not form the blast furnace"))
                .thenExecute(() -> h.assertTrue(m.getParts().stream()
                                .anyMatch(part -> part.self().getDefinition().getId().toString()
                                        .equals("gtmthings:creative_item_input_bus")),
                        "Formed structure did not retain the creative item input bus"))
                .thenSucceed();
    }

    private static void configureBlastFurnaceFullLoadHatches(GameTestHelper h,
                                                              LargeSteamBlastFurnaceMachine machine) {
        if (machine.isFormed()) machine.onStructureInvalid();
        List<BlockPos> ordinarySupplies = new java.util.ArrayList<>();
        int existingIntakes = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                machine.getPos().offset(-15, -15, -15), machine.getPos().offset(15, 15, 15))) {
            if (h.getLevel().getBlockState(pos).is(GSEMachines.STEAM_SUPPLY_HATCH.getBlock())) {
                ordinarySupplies.add(pos.immutable());
            } else if (h.getLevel().getBlockState(pos).is(GSEMachines.STEAM_AIR_INTAKE_HATCH.getBlock())) {
                existingIntakes++;
            }
        }
        h.assertTrue(ordinarySupplies.size() >= 4,
                "Blast-furnace preview lacks four supply hatches to upgrade");
        for (int i = 0; i < 4; i++) {
            h.getLevel().setBlockAndUpdate(ordinarySupplies.get(i),
                    GSEMachines.LARGE_STEAM_SUPPLY_HATCH.getBlock().defaultBlockState());
        }

        int requiredIntakes = 8 - existingIntakes;
        h.assertTrue(requiredIntakes >= 0, "Blast-furnace preview already exceeds eight air intakes");
        int installedIntakes = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                machine.getPos().offset(-15, -15, -15), machine.getPos().offset(15, 15, 15))) {
            if (h.getLevel().getBlockState(pos).is(GTBlocks.CASING_PRIMITIVE_BRICKS.get())) {
                h.getLevel().setBlockAndUpdate(pos,
                        GSEMachines.STEAM_AIR_INTAKE_HATCH.getBlock().defaultBlockState());
                if (++installedIntakes == requiredIntakes) break;
            }
        }
        eq(h, installedIntakes, requiredIntakes,
                "Could not install all full-load air intake hatches in legal wall positions");
        orientBlastFurnaceIntakesTowardAir(h, machine);
        h.assertTrue(machine.checkPattern(),
                "Four large supplies and eight air intakes did not reform the blast furnace");
        machine.onStructureFormed();
        h.assertTrue(machine.isFormed(), "Full-load blast-furnace fixture remained invalid");
    }

    private static void orientBlastFurnaceIntakesTowardAir(GameTestHelper h,
                                                            LargeSteamBlastFurnaceMachine machine) {
        RotationState rotation = GSEMachines.STEAM_AIR_INTAKE_HATCH.getRotationState();
        h.assertTrue(rotation != RotationState.NONE,
                "Steam air intake hatch has no configurable facing");
        for (BlockPos pos : BlockPos.betweenClosed(
                machine.getPos().offset(-15, -15, -15), machine.getPos().offset(15, 15, 15))) {
            BlockState state = h.getLevel().getBlockState(pos);
            if (!state.is(GSEMachines.STEAM_AIR_INTAKE_HATCH.getBlock())) continue;
            Direction clearFacing = null;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (h.getLevel().getBlockState(pos.relative(direction)).isAir()) {
                    clearFacing = direction;
                    break;
                }
            }
            h.assertTrue(clearFacing != null,
                    "Steam air intake has no clear horizontal face at " + pos);
            if (clearFacing != null && state.hasProperty(rotation.property)) {
                h.getLevel().setBlockAndUpdate(pos, state.setValue(rotation.property, clearFacing));
            }
        }
    }

    private static void startFullLoadBlastRecipe(GameTestHelper h,
                                                  LargeSteamBlastFurnaceMachine machine) {
        ItemBusPartMachine input = inputBus(machine);
        h.assertTrue(input.getInventory().getSlots() >= 4,
                "Full-load fixture input bus lacks four slots for split stacks");
        fillOutputs(machine, false);
        input.getInventory().setStackInSlot(0,
                ChemicalHelper.get(TagPrefix.dust, GTMaterials.Iron, 64));
        input.getInventory().setStackInSlot(1,
                ChemicalHelper.get(TagPrefix.dust, GTMaterials.Iron, 32));
        input.getInventory().setStackInSlot(2,
                ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coke, 64));
        input.getInventory().setStackInSlot(3,
                ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coke, 32));

        GTRecipe recipe = recipeEndingWith(
                GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES,
                "wrought_iron_from_dust_coke_dust");
        h.assertTrue((boolean) call(machine, "tryStartRecipe", recipe),
                "Large steam blast furnace did not start its 96-parallel real recipe");
        eq(h, machine.getBatchParallel(), 96, "Blast furnace did not lock its maximum parallel");
        eq(h, machine.getBatchDuration(), noviceDuration(recipe),
                "Full-load recipe did not apply novice proficiency duration");
        eq(h, machine.getBatchSteamPerTick(), 19_200,
                "Full-load recipe locked the wrong steam demand");
        for (int slot = 0; slot < 4; slot++) {
            h.assertTrue(input.getInventory().getStackInSlot(slot).isEmpty(),
                    "Full-load recipe left input in split stack slot " + slot);
        }
    }

    private static void refillFullLoadSteam(GameTestHelper h, List<FluidHatchPartMachine> supplies) {
        for (FluidHatchPartMachine hatch : supplies) {
            boolean large = hatch.self().getDefinition() == GSEMachines.LARGE_STEAM_SUPPLY_HATCH;
            if (!large) continue;
            int missing = hatch.tank.getTankCapacity(0) - hatch.tank.getFluidInTank(0).getAmount();
            int filled = hatch.tank.fillInternal(
                    GTMaterials.Steam.getFluid(missing), IFluidHandler.FluidAction.EXECUTE);
            eq(h, filled, missing, "Large supply hatch refused its periodic full-load refill");
        }
    }

    private static GTRecipe recipeEndingWith(GTRecipeType type, String suffix) {
        List<GTRecipe> matches = type.getRecipesInCategory(type.getCategory()).stream()
                .filter(recipe -> recipe.getId().getPath().endsWith(suffix))
                .toList();
        if (matches.size() != 1) {
            throw new AssertionError("Expected one recipe ending with " + suffix + ", found " + matches.size());
        }
        return matches.get(0);
    }

    private static void runBlastRecipe(GameTestHelper h, LargeSteamBlastFurnaceMachine machine,
                                       GTRecipe recipe, ItemBusPartMachine input, Item expectedOutput) {
        fillSteam(machine, 256_000);
        long steamBefore = steam(machine);
        long airBefore = air(machine);
        h.assertTrue((boolean) call(machine, "tryStartRecipe", recipe),
                "Large steam blast furnace did not start " + recipe.getId());
        eq(h, machine.getBatchParallel(), 4, "Blast recipe did not lock four parallels");
        int duration = noviceDuration(recipe);
        eq(h, machine.getBatchDuration(), duration,
                "Blast recipe did not apply its novice proficiency duration");
        eq(h, machine.getBatchSteamPerTick(), 800, "Blast recipe locked the wrong steam demand");
        h.assertTrue(input.getInventory().getStackInSlot(0).isEmpty()
                        && input.getInventory().getStackInSlot(1).isEmpty(),
                "Blast recipe did not consume both inputs atomically at batch start");

        for (int tick = 1; tick <= duration; tick++) {
            call(machine, "runBatchTick");
            if (tick < duration) {
                eq(h, machine.getBatchProgress(), tick,
                        "Blast recipe did not advance exactly once on supplied tick " + tick);
                h.assertTrue(!machine.getBatchRecipeId().isEmpty(),
                        "Blast recipe completed before its locked duration at tick " + tick);
            }
        }
        h.assertTrue(machine.getBatchRecipeId().isEmpty(),
                "Blast recipe did not complete after its locked duration");
        eq(h, outputCount(machine, expectedOutput), 4,
                "Blast recipe did not deliver its guaranteed parallel output");
        eq(h, steamBefore - steam(machine), 800L * duration,
                "Blast recipe consumed the wrong total steam");
        eq(h, airBefore - air(machine), 16L * duration,
                "Blast recipe consumed the wrong total blast air");
    }

    private static int noviceDuration(GTRecipe recipe) {
        return durationAt(recipe, 0);
    }

    private static int durationAt(GTRecipe recipe, int level) {
        int percent = GSEDifficultyState.blastFurnaceDurationPercent(false, level);
        return Math.max(1, (recipe.duration * percent + 99) / 100);
    }

    private static long air(Object m) {
        return GSESteamEngineTestSupport.<SteamAirIntakeHatchPartMachine>list(m, "airIntakeHatches").stream()
                .mapToLong(hatch -> hatch.tank.getFluidInTank(0).getAmount()).sum();
    }
}
