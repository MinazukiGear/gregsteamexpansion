package com.hoshino.gregsteamexpansion.gametest;

import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeSteamTankMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamTankValvePartMachine;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.registry.GSEPartAbilities;
import com.hoshino.gregsteamexpansion.registry.GSESteamTankPatterns;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSESteamTankTests {

    private GSESteamTankTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 600)
    public static void allSteamTankSizesFormAndUseOuterVolume(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_TANK;
        for (int width : GSESteamTankPatterns.WIDTHS) {
            for (int height = GSESteamTankPatterns.MIN_HEIGHT;
                 height <= GSESteamTankPatterns.MAX_HEIGHT; height++) {
                int expected = width * width * height * LargeSteamTankMachine.CAPACITY_PER_OUTER_BLOCK_MB;
                helper.assertTrue(LargeSteamTankMachine.capacityFor(width, height) == expected,
                        "Wrong capacity for " + width + "x" + width + "x" + height);

                clearTankArea(helper);
                LargeSteamTankMachine tank = (LargeSteamTankMachine) GSEStructureTestUtils.placeShape(
                        helper, definition, GSESteamTankPatterns.createShapeInfo(definition, width, height));
                helper.assertTrue(tank != null, "No controller for " + width + "x" + width + "x" + height);
                if (tank == null) return;
                helper.assertTrue(tank.checkPattern(),
                        "Steam tank did not match at " + width + "x" + width + "x" + height);
                tank.onStructureFormed();
                helper.assertTrue(tank.isFormed(),
                        "Steam tank did not form at " + width + "x" + width + "x" + height);
                helper.assertTrue(tank.getFormedWidth() == width && tank.getFormedHeight() == height,
                        "Steam tank reported wrong dimensions at " + width + "x" + width + "x" + height);
                helper.assertTrue(tank.getFormedCapacity() == expected,
                        "Steam tank reported wrong capacity at " + width + "x" + width + "x" + height);
                tank.onStructureInvalid();
            }
        }
        helper.assertTrue(LargeSteamTankMachine.capacityFor(3, 4) == 2_304_000,
                "Minimum steam tank capacity changed");
        helper.assertTrue(LargeSteamTankMachine.capacityFor(9, 8) == 41_472_000,
                "Maximum steam tank capacity changed");
        helper.assertTrue(GSEPartAbilities.STEAM_TANK_VALVE.isApplicable(GSEMachines.STEAM_TANK_VALVE.getBlock()),
                "Steam tank valve is missing its dedicated ability");
        helper.succeed();
    }

    private static void clearTankArea(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(16, 16, 16);
        for (int x = -5; x <= 5; x++) {
            for (int y = 0; y <= 8; y++) {
                for (int z = 0; z <= 10; z++) {
                    helper.setBlock(anchor.offset(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void canonicalSteamTankFormsAndSharesFilteredStorage(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_TANK;
        LargeSteamTankMachine tank = (LargeSteamTankMachine) GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(tank != null, "Steam tank preview has no controller");
        if (tank == null) return;

        helper.assertTrue(tank.checkPattern(), "Canonical 9x9x4 steam tank did not match");
        tank.onStructureFormed();
        helper.assertTrue(tank.isFormed(), "Canonical steam tank did not form");
        helper.assertTrue(tank.getFormedWidth() == 9 && tank.getFormedHeight() == 4,
                "Canonical steam tank reported wrong dimensions");
        helper.assertTrue(tank.getFormedCapacity() == 20_736_000,
                "Canonical steam tank reported wrong outer-volume capacity");
        helper.assertTrue(tank.getValveCount() == 1,
                "Canonical steam tank preview must contain exactly one representative valve");
        helper.assertTrue(tank.getFluidHandlerCap(null, false) == null,
                "Steam tank controller exposed a fluid capability");

        SteamTankValvePartMachine valve = tank.getParts().stream()
                .filter(SteamTankValvePartMachine.class::isInstance)
                .map(SteamTankValvePartMachine.class::cast)
                .findFirst()
                .orElse(null);
        helper.assertTrue(valve != null, "Formed steam tank did not collect its valve");
        if (valve == null) return;

        var input = valve.getFluidHandlerCap(valve.getFrontFacing(), false);
        helper.assertTrue(input != null, "Input-mode valve exposed no fluid capability");
        helper.assertTrue(input.fill(GTMaterials.Water.getFluid(1_000), FluidAction.SIMULATE) == 0,
                "Steam tank accepted water");
        helper.assertTrue(input.fill(GTMaterials.Steam.getFluid(12_345), FluidAction.EXECUTE) == 12_345,
                "Steam tank input valve rejected standard steam");
        helper.assertTrue(input.drain(1_000, FluidAction.SIMULATE).isEmpty(),
                "Input-mode valve allowed extraction");

        valve.setOutputMode(true);
        var output = valve.getFluidHandlerCap(valve.getFrontFacing(), false);
        helper.assertTrue(output.fill(GTMaterials.Steam.getFluid(1), FluidAction.SIMULATE) == 0,
                "Output-mode valve allowed insertion");
        helper.assertTrue(output.drain(2_345, FluidAction.EXECUTE).getAmount() == 2_345,
                "Output-mode valve failed to drain shared steam");
        helper.assertTrue(tank.getStoredAmount() == 10_000,
                "Valve operations did not conserve the controller's shared steam");

        CompoundTag dropData = new CompoundTag();
        tank.saveToItem(dropData);
        LargeSteamTankMachine restored = GSEStructureTestUtils.placeMachine(
                helper, GSEMachines.LARGE_STEAM_TANK, new BlockPos(3, 3, 3));
        restored.loadFromItem(dropData);
        helper.assertTrue(restored.getStoredAmount() == 10_000,
                "Controller item round-trip lost stored steam");
        helper.assertTrue(restored.getFormedCapacity() == 20_736_000,
                "Controller item round-trip lost the last formed capacity");
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void valveActivelyTransfersSteamInBothModes(GameTestHelper helper) {
        var definition = GSEMachines.LARGE_STEAM_TANK;
        LargeSteamTankMachine tank = (LargeSteamTankMachine) GSEStructureTestUtils.placeShape(
                helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(tank != null && tank.checkPattern(), "Steam tank fixture did not match");
        if (tank == null) return;
        tank.onStructureFormed();

        SteamTankValvePartMachine valve = tank.getParts().stream()
                .filter(SteamTankValvePartMachine.class::isInstance)
                .map(SteamTankValvePartMachine.class::cast)
                .findFirst()
                .orElse(null);
        helper.assertTrue(valve != null, "Steam tank fixture has no valve");
        if (valve == null) return;

        BlockPos templateOrigin = helper.absolutePos(BlockPos.ZERO);
        BlockPos sourcePos = valve.getPos().relative(valve.getFrontFacing()).subtract(templateOrigin);
        FluidHatchPartMachine source = GSEStructureTestUtils.placeMachine(
                helper, GTMachines.FLUID_EXPORT_HATCH[1], sourcePos);
        source.setWorkingEnabled(false);
        source.tank.setFluidInTank(0, GTMaterials.Steam.getFluid(4_000));
        helper.getLevel().updateNeighborsAt(source.getPos(), source.getBlockState().getBlock());

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(tank.getStoredAmount() == 4_000,
                    "Input-mode valve did not pull steam from the adjacent fluid handler");
            helper.assertTrue(source.tank.getFluidInTank(0).isEmpty(),
                    "Adjacent fluid handler retained steam after valve import");

            source.tank.setFluidInTank(0, GTMaterials.Water.getFluid(1_000));
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(tank.getStoredAmount() == 4_000,
                        "Active valve import bypassed the standard-steam filter");
                helper.assertTrue(source.tank.getFluidInTank(0).getAmount() == 1_000,
                        "Active valve import removed a rejected fluid from its source");

                FluidHatchPartMachine sink = GSEStructureTestUtils.placeMachine(
                        helper, GTMachines.FLUID_IMPORT_HATCH[1], sourcePos);
                sink.setWorkingEnabled(false);
                helper.getLevel().updateNeighborsAt(sink.getPos(), sink.getBlockState().getBlock());
                valve.setOutputMode(true);
                helper.runAfterDelay(10, () -> {
                    helper.assertTrue(tank.getStoredAmount() == 0,
                            "Output-mode valve did not push steam into the adjacent fluid handler");
                    helper.assertTrue(sink.tank.getFluidInTank(0).getAmount() == 4_000,
                            "Adjacent fluid handler did not receive the valve's active steam output");
                    helper.succeed();
                });
            });
        });
    }
}
