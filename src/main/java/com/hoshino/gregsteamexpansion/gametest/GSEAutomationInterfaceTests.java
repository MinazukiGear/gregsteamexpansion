package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven.LargeCokeOvenMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.cokeoven.GSECokeOvenMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.LargeCokeOvenHatchPartMachine;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenMode;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

import static com.hoshino.gregsteamexpansion.gametest.GSESteamEngineTestSupport.*;

/** Compatibility contracts for item automation interfaces on supported multiblocks. */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSEAutomationInterfaceTests {
    private GSEAutomationInterfaceTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void smallCrusherAcceptsAutomationItemInterfaces(GameTestHelper h) {
        assertAutomationItemInterfaces(h, GSEMachines.STEAM_CRUSHER);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void compressorAcceptsAutomationItemInterfaces(GameTestHelper h) {
        assertAutomationItemInterfaces(h, GSEMachines.STEAM_COMPRESSOR);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void chemicalBathAcceptsAutomationItemInterfaces(GameTestHelper h) {
        assertAutomationItemInterfaces(h, GSEMachines.STEAM_CHEMICAL_BATH);
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void smallCentrifugeAcceptsAutomationItemInterfaces(GameTestHelper h) {
        assertAutomationItemInterfaces(h, GSEMachines.STEAM_CENTRIFUGE);
    }

    private static void assertAutomationItemInterfaces(GameTestHelper h,
                                                       MultiblockMachineDefinition definition) {
        var m = GSEStructureTestUtils.placeShape(h, definition, definition.getMatchingShapes().get(0));
        h.assertTrue(m != null, "Missing fixture controller for " + definition.getId());
        BlockPos inputPos = findBlock(h, m, GTMachines.STEAM_IMPORT_BUS.getBlock());
        BlockPos outputPos = findBlock(h, m, GTMachines.STEAM_EXPORT_BUS.getBlock());
        h.assertTrue(inputPos != null, "Fixture has no steam item input bus: " + definition.getId());
        h.assertTrue(outputPos != null, "Fixture has no steam item output bus: " + definition.getId());

        var inputs = List.of(
                requiredBlock(h, "gtmthings:creative_item_input_bus"),
                requiredBlock(h, "gtceu:me_input_bus"),
                requiredBlock(h, "gtceu:me_pattern_buffer"));
        var outputs = List.of(
                requiredBlock(h, "gtceu:me_output_bus"),
                requiredBlock(h, "gtmthings:me_export_buffer"));
        for (var output : outputs) {
            h.getLevel().setBlockAndUpdate(outputPos, output.defaultBlockState());
            for (var input : inputs) {
                if (m.isFormed()) m.onStructureInvalid();
                h.getLevel().setBlockAndUpdate(inputPos, input.defaultBlockState());
                h.assertTrue(m.checkPattern(), definition.getId() + " rejected input "
                        + ForgeRegistries.BLOCKS.getKey(input) + " with output "
                        + ForgeRegistries.BLOCKS.getKey(output));
                m.onStructureFormed();
                h.assertTrue(m.isFormed(), definition.getId() + " did not form after compatibility match");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void largeCokeOvenAcceptsAutomationItemInterfaces(GameTestHelper h) {
        var definition = GSEMachines.LARGE_COKE_OVEN;
        var m = (LargeCokeOvenMachine) GSEStructureTestUtils.placeShape(
                h, definition, definition.getMatchingShapes().get(1));
        h.assertTrue(m != null, "Missing large coke-oven fixture controller");
        List<BlockPos> hatches = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                m.getPos().offset(-8, 0, -8), m.getPos().offset(8, 8, 8))) {
            if (h.getLevel().getBlockState(pos).is(GSEMachines.LARGE_COKE_OVEN_HATCH.getBlock())) {
                hatches.add(pos.immutable());
            }
        }
        h.assertTrue(hatches.size() == 3, "Automated coke-oven fixture must contain three bespoke hatches");
        if (m.isFormed()) m.onStructureInvalid();
        var fluidMachine = MetaMachine.getMachine(h.getLevel(), hatches.get(2));
        h.assertTrue(fluidMachine instanceof LargeCokeOvenHatchPartMachine,
                "Remaining coke-oven fluid hatch machine missing");
        set(fluidMachine, "mode", CokeOvenMode.FLUID_OUTPUT);
        h.getLevel().setBlockAndUpdate(hatches.get(0),
                requiredBlock(h, "gtceu:me_pattern_buffer").defaultBlockState());
        h.getLevel().setBlockAndUpdate(hatches.get(1),
                requiredBlock(h, "gtmthings:me_export_buffer").defaultBlockState());
        h.assertTrue(m.checkPattern(), "Large coke oven rejected ME pattern input and ME output");
        m.onStructureFormed();
        h.assertTrue(m.isFormed(), "Large coke oven did not form with automation item interfaces");
        h.assertTrue(m.getStandardOutputBuses().size() == 1,
                "Large coke oven did not collect its ME output bus");
        h.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 300)
    public static void regularCokeOvenAcceptsAutomationItemInterfaces(GameTestHelper h) {
        var definition = GTMultiMachines.COKE_OVEN;
        var m = (GSECokeOvenMachine) GSEStructureTestUtils.placeShape(
                h, definition, definition.getMatchingShapes().get(1));
        h.assertTrue(m != null, "Missing regular coke-oven fixture controller");
        List<BlockPos> hatches = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                m.getPos().offset(-4, -4, -4), m.getPos().offset(4, 4, 4))) {
            if (h.getLevel().getBlockState(pos).is(GTMachines.COKE_OVEN_HATCH.getBlock())) {
                hatches.add(pos.immutable());
            }
        }
        h.assertTrue(hatches.size() == 3, "Automated regular coke-oven fixture must contain three hatches");
        var inputs = List.of(
                requiredBlock(h, "gtmthings:creative_item_input_bus"),
                requiredBlock(h, "gtceu:me_input_bus"),
                requiredBlock(h, "gtceu:me_pattern_buffer"));
        var outputs = List.of(
                requiredBlock(h, "gtceu:me_output_bus"),
                requiredBlock(h, "gtmthings:me_export_buffer"));
        for (var output : outputs) {
            h.getLevel().setBlockAndUpdate(hatches.get(1), output.defaultBlockState());
            for (var input : inputs) {
                if (m.isFormed()) m.onStructureInvalid();
                h.getLevel().setBlockAndUpdate(hatches.get(0), input.defaultBlockState());
                h.assertTrue(m.checkPattern(), "Regular coke oven rejected input "
                        + ForgeRegistries.BLOCKS.getKey(input) + " with output "
                        + ForgeRegistries.BLOCKS.getKey(output));
                m.onStructureFormed();
                h.assertTrue(m.isFormed(), "Regular coke oven did not form after compatibility match");
                h.assertTrue(m.getStandardOutputBuses().size() == 1,
                        "Regular coke oven did not collect its automation output bus");
            }
        }
        h.succeed();
    }

    private static net.minecraft.world.level.block.Block requiredBlock(GameTestHelper h, String id) {
        var block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(id));
        h.assertTrue(block != null && block != Blocks.AIR, "Required compatibility block missing: " + id);
        return block;
    }
}
