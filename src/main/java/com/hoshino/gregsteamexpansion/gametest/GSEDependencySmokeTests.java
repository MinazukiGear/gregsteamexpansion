package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.terminal.TerminalCompatibility;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalWorldData;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Real-server dependency gate, conditional recipe and core-machine smoke coverage. */
@GameTestHolder("gregsteamexpansion_smoke")
@PrefixGameTestTemplate(false)
public final class GSEDependencySmokeTests {
    private GSEDependencySmokeTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 200)
    public static void optionalDependenciesGateTerminalWithoutBreakingCore(GameTestHelper helper) {
        String configuredProfile = System.getProperty("gse.dependencySmokeProfile");
        String profile = configuredProfile == null ? "full" : configuredProfile;
        ModList mods = ModList.get();
        for (String required : List.of("minecraft", "forge", "gtceu", GregSteamExpansion.MOD_ID)) {
            helper.assertTrue(mods.isLoaded(required), "Missing required runtime mod: " + required);
        }
        boolean ae2 = !profile.equals("core");
        boolean gtmthings = profile.equals("full");
        helper.assertTrue(mods.isLoaded("ae2") == ae2, "AE2 presence differs from profile " + profile);
        helper.assertTrue(mods.isLoaded("guideme") == ae2, "GuideME presence differs from profile " + profile);
        helper.assertTrue(mods.isLoaded("gtmthings") == gtmthings,
                "GTM Things presence differs from profile " + profile);
        if (configuredProfile != null) {
            for (String absent : List.of("jade", "emi", "ftbteams", "sophisticatedcore",
                    "sophisticatedbackpacks", "sophisticatedstorage", "extendedae", "expatternprovider")) {
                helper.assertTrue(!mods.isLoaded(absent), "Smoke runtime unexpectedly loaded " + absent);
            }
        }

        var recipes = helper.getLevel().getRecipeManager();
        helper.assertTrue(recipes.byKey(GregSteamExpansion.id("shaped/steam_compressor")).isPresent(),
                "Core steam-compressor acquisition recipe did not load");
        helper.assertTrue(TerminalCompatibility.isAvailable() == gtmthings,
                "Terminal availability does not require both optional dependencies");
        helper.assertTrue(recipes.byKey(GregSteamExpansion.id("shaped/ultimate_terminal")).isPresent() == gtmthings,
                "Conditional terminal recipe differs from dependency availability");
        var terminal = GSEBlocks.ULTIMATE_TERMINAL.get().getDefaultInstance();
        helper.assertTrue(!terminal.isEmpty(), "Stable terminal item registration disappeared");
        if (!gtmthings) {
            List<Component> tooltip = new ArrayList<>();
            terminal.getItem().appendHoverText(terminal, helper.getLevel(), tooltip, TooltipFlag.Default.NORMAL);
            helper.assertTrue(tooltip.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents text
                            && text.getKey().equals("gregsteamexpansion.ultimate_terminal.dependencies_missing")),
                    "Unavailable terminal did not explain its missing dependencies");
            var player = FakePlayerFactory.getMinecraft(helper.getLevel());
            player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
            helper.assertTrue(terminal.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND)
                            .getResult() == InteractionResult.FAIL,
                    "Unavailable terminal opened instead of rejecting use");
            // Exercise the material ledger as well as the item gate: no AE classes may load here.
            GSEStructureTestUtils.placeMachine(helper, GSEMachines.STEAM_CRUSHER,
                    new net.minecraft.core.BlockPos(16, 16, 16));
            player.getAbilities().instabuild = false;
            player.getInventory().clearContent();
            var ledger = UltimateTerminalWorldData.load(new net.minecraft.nbt.CompoundTag());
            helper.assertTrue(ledger.addTarget(player, helper.getLevel().dimension(),
                            helper.absolutePos(new net.minecraft.core.BlockPos(16, 16, 16)))
                            && ledger.preview(player).batchMaterials().stream().allMatch(material -> material.network() == 0)
                            && !ledger.start(player),
                    "Unavailable optional backend linked AE classes or fabricated materials");
        }

        var definition = GSEMachines.STEAM_COMPRESSOR;
        var controller = GSEStructureTestUtils.placeShape(helper, definition, definition.getMatchingShapes().get(0));
        helper.assertTrue(controller != null, "Core steam-compressor controller did not instantiate");
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(controller.isFormed(),
                "Core steam compressor failed to form in profile " + profile)).thenSucceed();
    }
}
