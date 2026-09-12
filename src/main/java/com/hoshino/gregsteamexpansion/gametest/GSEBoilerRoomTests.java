package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.BoilerRoomMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MufflerPartMachine;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;

import io.netty.buffer.Unpooled;

import java.util.ArrayList;

/** Specification regressions independent of pattern-generated preview expectations. */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSEBoilerRoomTests {
    private GSEBoilerRoomTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void bronzeIntakePlacement(GameTestHelper helper) {
        intakePlacement(helper, GSEMachines.BOILER_ROOM_BRONZE);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void steelIntakePlacement(GameTestHelper helper) {
        intakePlacement(helper, GSEMachines.BOILER_ROOM_STEEL);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void titaniumIntakePlacement(GameTestHelper helper) {
        intakePlacement(helper, GSEMachines.BOILER_ROOM_TITANIUM);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void tungstensteelIntakePlacement(GameTestHelper helper) {
        intakePlacement(helper, GSEMachines.BOILER_ROOM_TUNGSTENSTEEL);
    }

    private static void intakePlacement(GameTestHelper h, MultiblockMachineDefinition definition) {
        var shape = definition.getMatchingShapes().get(0);
        var blocks = shape.getBlocks();
        int intakes = 0;
        for (var plane : blocks) {
            for (var row : plane) {
                for (var info : row) {
                    if (info != null && info.getBlockState().is(GSEMachines.STEAM_AIR_INTAKE_HATCH.getBlock())) {
                        intakes++;
                    }
                }
            }
        }
        h.assertTrue(intakes == 1, "Preview must contain exactly one intake, found " + intakes);
        var m = GSEStructureTestUtils.placeShape(h, definition, shape);
        h.assertTrue(m != null, "Preview controller missing");
        var roof = m.getPos().offset(0, 3, 5);
        var interior = m.getPos().offset(-2, -2, 1);
        var shell = m.getPos().offset(-3, -2, 0);
        var level = h.getLevel();
        var intakeState = level.getBlockState(roof);
        var shellState = level.getBlockState(shell);
        h.assertTrue(MetaMachine.getMachine(level, roof) instanceof SteamAirIntakeHatchPartMachine,
                "Intake must be at the roof centre");
        h.assertTrue(MetaMachine.getMachine(level, roof).getFrontFacing() == Direction.UP,
                "Preview intake must face outside, upwards");
        h.assertTrue(level.isEmptyBlock(roof.above()), "Preview intake front must be unobstructed");
        for (int dx : new int[] {-2, 2}) {
            for (int dz = 1; dz <= 9; dz++) {
                h.assertTrue(level.isEmptyBlock(m.getPos().offset(dx, -2, dz)),
                        "Second-layer firebox sides must remain air");
            }
        }
        matches(h, m, true, "Registered preview");
        level.setBlockAndUpdate(roof, shellState);
        matches(h, m, false, "Roof intake replaced with casing");
        // Every strip position, including both ends, may host the sole intake.
        for (int dz = 0; dz < 11; dz++) {
            var pos = m.getPos().offset(0, 3, dz);
            level.setBlockAndUpdate(pos, intakeState);
            matches(h, m, true, "Single intake at strip position " + dz);
            level.setBlockAndUpdate(pos, shellState);
        }
        for (int dz = 0; dz < 11; dz++) {
            level.setBlockAndUpdate(m.getPos().offset(0, 3, dz), intakeState);
        }
        matches(h, m, true, "All eleven intake slots occupied");
        // Other standard interfaces stay legal on general casing, never on the strip.
        var forbidden = new com.gregtechceu.gtceu.api.machine.MachineDefinition[] {
                GTMachines.FLUID_IMPORT_HATCH[1], GTMachines.FLUID_EXPORT_HATCH[1], GTMachines.ITEM_IMPORT_BUS[1]
        };
        for (int dz = 0; dz < 11; dz++) {
            var pos = m.getPos().offset(0, 3, dz);
            for (var hatch : forbidden) {
                level.setBlockAndUpdate(pos, hatch.getBlock().defaultBlockState());
                matches(h, m, false, "Other hatch on strip position " + dz);
            }
            level.setBlockAndUpdate(pos, intakeState);
        }
        for (int dz = 0; dz < 11; dz++) {
            level.setBlockAndUpdate(m.getPos().offset(0, 3, dz), shellState);
        }
        level.setBlockAndUpdate(roof, Blocks.AIR.defaultBlockState());
        matches(h, m, false, "Missing roof intake");
        level.setBlockAndUpdate(interior, intakeState);
        matches(h, m, false, "Intake relocated inside");
        level.setBlockAndUpdate(roof, intakeState);
        matches(h, m, false, "Extra intake inside");
        level.setBlockAndUpdate(interior, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(shell, intakeState);
        matches(h, m, false, "Extra intake on casing shell");
        level.setBlockAndUpdate(shell, shellState);
        matches(h, m, true, "Restored preview");
        h.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void bronzeCombinedAirSupply(GameTestHelper h) {
        combinedAirSupply(h, GSEMachines.BOILER_ROOM_BRONZE);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void steelCombinedAirSupply(GameTestHelper h) {
        combinedAirSupply(h, GSEMachines.BOILER_ROOM_STEEL);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void titaniumCombinedAirSupply(GameTestHelper h) {
        combinedAirSupply(h, GSEMachines.BOILER_ROOM_TITANIUM);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void tungstensteelCombinedAirSupply(GameTestHelper h) {
        combinedAirSupply(h, GSEMachines.BOILER_ROOM_TUNGSTENSTEEL);
    }

    private static void combinedAirSupply(GameTestHelper h, MultiblockMachineDefinition definition) {
        var m = (BoilerRoomMachine) GSEStructureTestUtils.placeShape(h, definition,
                definition.getMatchingShapes().get(0));
        h.assertTrue(m != null, "Preview controller missing");
        var intakeState = h.getLevel().getBlockState(m.getPos().offset(0, 3, 5));
        for (int dz = 0; dz < 11; dz++) {
            h.getLevel().setBlockAndUpdate(m.getPos().offset(0, 3, dz), intakeState);
        }
        h.startSequence().thenWaitUntil(() -> h.assertTrue(m.isFormed(), "Multi-intake boiler has not formed"))
                .thenExecute(() -> {
                    var intakes = m.getParts().stream().filter(SteamAirIntakeHatchPartMachine.class::isInstance)
                            .map(SteamAirIntakeHatchPartMachine.class::cast).toList();
                    h.assertTrue(intakes.size() == 11 && m.hasAirIntake(), "Controller did not accept all 11 intakes");
                    intakes.forEach(intake -> intake.tank.drainInternal(Integer.MAX_VALUE, FluidAction.EXECUTE));
                    int demand = m.getAirConsumptionPerTick();
                    // Split across the last two tanks: the first tank is empty,
                    // and neither contributor alone can satisfy this tier.
                    var first = intakes.get(9);
                    var second = intakes.get(10);
                    first.tank.fillInternal(GTMaterials.Air.getFluid(demand / 2), FluidAction.EXECUTE);
                    second.tank.fillInternal(GTMaterials.Air.getFluid(demand / 2 - 1), FluidAction.EXECUTE);
                    h.assertTrue(m.isAirStarved(), "One mB short was accepted");
                    h.assertTrue(!consumeAir(m), "Short supply was consumed");
                    h.assertTrue(first.tank.getFluidInTank(0).getAmount() == demand / 2
                                    && second.tank.getFluidInTank(0).getAmount() == demand / 2 - 1,
                            "Shortage partially drained contributing intakes");
                    second.tank.fillInternal(GTMaterials.Air.getFluid(1), FluidAction.EXECUTE);
                    h.assertTrue(!m.isAirStarved(), "Combined exact demand was rejected");
                    h.assertTrue(consumeAir(m), "Combined exact demand could not be consumed");
                    h.assertTrue(first.tank.getFluidInTank(0).isEmpty() && second.tank.getFluidInTank(0).isEmpty(),
                            "Combined draw did not charge exactly one tick");
                    second.tank.fillInternal(GTMaterials.Air.getFluid(demand + 17), FluidAction.EXECUTE);
                    h.assertTrue(consumeAir(m) && second.tank.getFluidInTank(0).getAmount() == 17,
                            "Draw from a later intake did not preserve excess air");
                }).thenSucceed();
    }

    private static boolean consumeAir(BoilerRoomMachine m) {
        try {
            var method = BoilerRoomMachine.class.getDeclaredMethod("consumeAir");
            method.setAccessible(true);
            return (boolean) method.invoke(m);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot execute boiler air draw", e);
        }
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void completeFuelRecipeStarts(GameTestHelper h) {
        var definition = GSEMachines.BOILER_ROOM_BRONZE;
        var m = (BoilerRoomMachine) GSEStructureTestUtils.placeShape(h, definition,
                definition.getMatchingShapes().get(0));
        h.assertTrue(m != null, "Preview controller missing");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(m.isFormed(), "Boiler room has not formed"))
                .thenExecute(() -> {
                    var muffler = m.getParts().stream().filter(MufflerPartMachine.class::isInstance)
                            .map(MufflerPartMachine.class::cast).findFirst().orElseThrow();
                    h.assertTrue(muffler.getFrontFacing() == m.getFrontFacing().getOpposite(),
                            "Preview muffler must face outward through the back wall");
                    h.assertTrue(muffler.isFrontFaceFree(), "Preview muffler front must be unobstructed");
                    var fluidInputs = m.getParts().stream().filter(FluidHatchPartMachine.class::isInstance)
                            .map(FluidHatchPartMachine.class::cast)
                            .filter(part -> part.tank.getHandlerIO() == com.gregtechceu.gtceu.api.capability.recipe.IO.IN)
                            .toList();
                    var itemInput = m.getParts().stream().filter(ItemBusPartMachine.class::isInstance)
                            .map(ItemBusPartMachine.class::cast)
                            .filter(part -> part.getInventory().getHandlerIO() !=
                                    com.gregtechceu.gtceu.api.capability.recipe.IO.OUT)
                            .findFirst().orElseThrow();
                    var intake = m.getParts().stream().filter(SteamAirIntakeHatchPartMachine.class::isInstance)
                            .map(SteamAirIntakeHatchPartMachine.class::cast).findFirst().orElseThrow();
                    h.assertTrue(fluidInputs.size() >= 2,
                            "Boiler preview needs separate water and liquid-fuel input hatches");
                    fluidInputs.get(0).tank.fillInternal(GTMaterials.Water.getFluid(8_000), FluidAction.EXECUTE);
                    fluidInputs.get(1).tank.fillInternal(GTMaterials.Creosote.getFluid(8_000), FluidAction.EXECUTE);
                    var powder = ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coal);
                    h.assertTrue(itemInput.getInventory().insertItemInternal(0, powder, false).isEmpty(),
                            "Powder input bus rejected coal dust");
                    intake.tank.fillInternal(GTMaterials.Air.getFluid(4_000), FluidAction.EXECUTE);
                    h.assertTrue(m.getRecipeType().searchRecipe(m, recipe -> true).hasNext(),
                            "Boiler recipe lookup found no liquid fuel with populated hatches");
                    m.getRecipeLogic().updateTickSubscription();
                })
                .thenWaitUntil(() -> h.assertTrue(m.getRecipeLogic().isWorking(),
                        "Boiler found inputs but did not start; status=" + m.getRecipeLogic().getStatus()
                                + ", failures=" + m.getRecipeLogic().getFailureReasons()
                                + ", waiting=" + m.getRecipeLogic().getWaitingReason()))
                .thenWaitUntil(() -> h.assertTrue(m.getRecipeLogic().getProgress() > 0 && m.getRoomTemperature() > 0,
                        "Boiler recipe started but did not advance or begin heating"))
                .thenSucceed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void addonCreativeInputBusSuppliesPowder(GameTestHelper h) {
        var definition = GSEMachines.BOILER_ROOM_BRONZE;
        var m = (BoilerRoomMachine) GSEStructureTestUtils.placeShape(h, definition,
                definition.getMatchingShapes().get(0));
        h.assertTrue(m != null, "Preview controller missing");

        // The preview puts its item import bus two blocks below the controller.
        // Replace it with the runtime-only GTM Things implementation that
        // advertises IMPORT_ITEMS but does not extend ItemBusPartMachine.
        var creativeBus = ForgeRegistries.BLOCKS.getValue(
                ResourceLocation.tryParse("gtmthings:creative_item_input_bus"));
        h.assertTrue(creativeBus != null && creativeBus != Blocks.AIR,
                "GTM Things creative item input bus is unavailable in the test runtime");
        var busPos = m.getPos().below(2);
        h.getLevel().setBlockAndUpdate(busPos, creativeBus.defaultBlockState());

        h.startSequence().thenWaitUntil(() -> h.assertTrue(m.isFormed(),
                "Boiler room did not accept the addon item input bus"))
                .thenExecute(() -> {
                    var creativeMachine = MetaMachine.getMachine(h.getLevel(), busPos);
                    h.assertTrue(creativeMachine != null, "Addon input bus machine missing");
                    try {
                        Object inventoryObject = creativeMachine.getClass().getMethod("getInventory")
                                .invoke(creativeMachine);
                        h.assertTrue(inventoryObject instanceof IItemHandlerModifiable,
                                "Addon input bus did not expose an item handler");
                        var inventory = (IItemHandlerModifiable) inventoryObject;
                        inventory.setStackInSlot(0, ChemicalHelper.get(TagPrefix.dust, GTMaterials.Coal));
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError("Cannot configure addon creative input bus", e);
                    }

                    var fluidInputs = m.getParts().stream().filter(FluidHatchPartMachine.class::isInstance)
                            .map(FluidHatchPartMachine.class::cast)
                            .filter(part -> part.tank.getHandlerIO() ==
                                    com.gregtechceu.gtceu.api.capability.recipe.IO.IN)
                            .toList();
                    var intake = m.getParts().stream().filter(SteamAirIntakeHatchPartMachine.class::isInstance)
                            .map(SteamAirIntakeHatchPartMachine.class::cast).findFirst().orElseThrow();
                    fluidInputs.get(0).tank.fillInternal(GTMaterials.Water.getFluid(8_000), FluidAction.EXECUTE);
                    fluidInputs.get(1).tank.fillInternal(GTMaterials.Creosote.getFluid(8_000), FluidAction.EXECUTE);
                    intake.tank.fillInternal(GTMaterials.Air.getFluid(4_000), FluidAction.EXECUTE);
                    h.assertTrue(!m.isMissingPowder(),
                            "Controller did not discover coal dust in the addon input capability");
                    m.getRecipeLogic().updateTickSubscription();
                })
                .thenWaitUntil(() -> h.assertTrue(m.getRecipeLogic().isWorking(),
                        "Addon powder input was visible but did not start the boiler recipe; waiting="
                                + m.getRecipeLogic().getWaitingReason()))
                .thenSucceed();
    }

    private static void matches(GameTestHelper h, MultiblockControllerMachine m, boolean expected, String name) {
        h.assertTrue(m.getPattern().checkPatternAt(m.getMultiblockState(), true) == expected,
                name + (expected ? " did not form" : " incorrectly formed"));
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void powderGaugeAndStatus(GameTestHelper h) {
        var definition = GSEMachines.BOILER_ROOM_BRONZE;
        var m = (BoilerRoomMachine) GSEStructureTestUtils.placeShape(h, definition,
                definition.getMatchingShapes().get(0));
        h.assertTrue(m != null, "Preview controller missing");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(m.isFormed(), "Boiler room has not formed"))
                .thenExecute(() -> {
                    var ui = m.createUI(FakePlayerFactory.getMinecraft(h.getLevel()));
                    var gauges = ui.getFlatWidgetCollection().stream().filter(ProgressWidget.class::isInstance)
                            .map(ProgressWidget.class::cast).toList();
                    h.assertTrue(gauges.size() == 2, "UI must expose temperature and powder gauges");
                    h.assertTrue(ui.getFlatWidgetCollection().stream().anyMatch(ComponentPanelWidget.class::isInstance),
                            "UI lost its status panel and throttle controls");
                    set(m, "roomTemperature", 400);
                    set(m, "powderBurnTotal", 1600);
                    set(m, "powderBurnRemaining", 400);
                    h.assertTrue(gauges.get(0).progressSupplier.getAsDouble() == 0.5,
                            "Thermometer does not use room temperature");
                    h.assertTrue(gauges.get(1).progressSupplier.getAsDouble() == 0.25,
                            "Powder gauge does not use remaining buffer");
                    assertSynced(h, gauges.get(0), 0.5);
                    assertSynced(h, gauges.get(1), 0.25);
                    var intake = m.getParts().stream().filter(SteamAirIntakeHatchPartMachine.class::isInstance)
                            .map(SteamAirIntakeHatchPartMachine.class::cast).findFirst().orElseThrow();
                    intake.tank.drainInternal(Integer.MAX_VALUE, FluidAction.EXECUTE);
                    status(h, m, "air_starved");
                    intake.tank.fillInternal(GTMaterials.Air.getFluid(1000), FluidAction.EXECUTE);
                    status(h, m, "co_firing");
                    set(m, "powderBurnRemaining", 0);
                    set(m, "powderBurnTotal", 0);
                    assertSynced(h, gauges.get(1), 0);
                    status(h, m, "missing_powder");
                    // Leave no hot, dry fixture subscribed after the assertions.
                    set(m, "roomTemperature", 0);
                    m.onStructureInvalid();
                    status(h, m, "no_air_intake");
                })
                .thenSucceed();
    }

    private static void assertSynced(GameTestHelper h, ProgressWidget serverGauge, double expected) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            // LDLib serializes its last sampled value, not the supplier directly.
            // Sample through the same server update method used by an open UI.
            serverGauge.detectAndSendChanges();
            serverGauge.writeInitialData(buffer);
            var clientGauge = new ProgressWidget(() -> -1, 0, 0, 18, 18);
            clientGauge.readInitialData(buffer);
            h.assertTrue(clientGauge.getLastProgressValue() == expected,
                    "Client gauge did not receive server progress " + expected);
        } finally {
            buffer.release();
        }
    }

    private static void status(GameTestHelper h, BoilerRoomMachine m, String suffix) {
        var lines = new ArrayList<Component>();
        m.addDisplayText(lines);
        h.assertTrue(lines.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals("gregsteamexpansion.machine.boiler_room.status." + suffix)),
                "Missing boiler status: " + suffix);
    }

    private static void set(BoilerRoomMachine m, String name, int value) {
        try {
            var field = BoilerRoomMachine.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(m, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot seed boiler fixture field " + name, e);
        }
    }
}
