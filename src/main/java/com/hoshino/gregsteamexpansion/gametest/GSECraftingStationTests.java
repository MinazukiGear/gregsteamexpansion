package com.hoshino.gregsteamexpansion.gametest;

import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.blockentity.CraftingStationBlockEntity;
import com.hoshino.gregsteamexpansion.menu.CraftingStationMenu;
import com.hoshino.gregsteamexpansion.registry.GSEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Transaction-level coverage for crafting station matching, commit and routing. */
@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSECraftingStationTests {
    private GSECraftingStationTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void transactionToolRefillRemainderAndRollback(GameTestHelper helper) {
        BlockPos stationPos = new BlockPos(1, 1, 1);
        helper.setBlock(stationPos, GSEBlocks.CRAFTING_STATION.get().defaultBlockState());
        helper.setBlock(stationPos.east(), Blocks.CHEST.defaultBlockState());
        var stationEntity = helper.getLevel().getBlockEntity(helper.absolutePos(stationPos));
        var chestEntity = helper.getLevel().getBlockEntity(helper.absolutePos(stationPos.east()));
        helper.assertTrue(stationEntity instanceof CraftingStationBlockEntity,
                "Crafting station block entity is missing");
        helper.assertTrue(chestEntity instanceof ChestBlockEntity, "Source chest block entity is missing");
        CraftingStationBlockEntity station = (CraftingStationBlockEntity) stationEntity;
        ChestBlockEntity chest = (ChestBlockEntity) chestEntity;

        Item saw = ForgeRegistries.ITEMS.tags().getTag(CustomTags.CRAFTING_SAWS).stream()
                .findFirst().orElseThrow(() -> new IllegalStateException("No registered crafting saw"));
        Player player = helper.makeMockSurvivalPlayer();
        BlockPos absolutePos = helper.absolutePos(stationPos);
        player.setPos(absolutePos.getX() + 0.5, absolutePos.getY() + 0.5, absolutePos.getZ() + 0.5);
        station.tryStartViewing(player);
        CraftingStationMenu menu = new CraftingStationMenu(2, player.getInventory(), station);

        ItemStack sawStack = new ItemStack(saw);
        station.getTools().setStackInSlot(0, sawStack);
        station.getGrid().setStackInSlot(3, new ItemStack(GSEBlocks.CRAFTING_STATION_ITEM.get()));
        menu.broadcastChanges();
        ItemStack slabResult = menu.getSlot(CraftingStationMenu.RESULT_SLOT).getItem().copy();
        helper.assertTrue(slabResult.is(GSEBlocks.CRAFTING_STATION_SLAB_ITEM.get()),
                "Virtual saw did not produce the slab preview");
        int initialSawDamage = station.getTools().getStackInSlot(0).getDamageValue();
        menu.getSlot(CraftingStationMenu.RESULT_SLOT).onTake(player, slabResult.copy());
        helper.assertTrue(station.getGrid().getStackInSlot(3).isEmpty(),
                "Single craft did not consume its grid input");
        helper.assertTrue(station.getTools().getStackInSlot(0).getDamageValue() > initialSawDamage,
                "Borrowed tool was not damaged by the committed craft");

        chest.setItem(0, new ItemStack(GSEBlocks.CRAFTING_STATION_ITEM.get(), 3));
        station.getGrid().setStackInSlot(3, new ItemStack(GSEBlocks.CRAFTING_STATION_ITEM.get()));
        menu.broadcastChanges();
        int slabsBefore = count(player, GSEBlocks.CRAFTING_STATION_SLAB_ITEM.get());
        menu.quickMoveStack(player, CraftingStationMenu.RESULT_SLOT);
        int expectedSlabs = slabResult.getCount() * 4;
        helper.assertTrue(count(player, GSEBlocks.CRAFTING_STATION_SLAB_ITEM.get()) - slabsBefore == expectedSlabs,
                "Shift craft did not lock the recipe and consume the grid plus three refills");
        helper.assertTrue(chest.getItem(0).isEmpty() && station.getGrid().getStackInSlot(3).isEmpty(),
                "Shift craft left a refill input behind");

        clearGrid(station);
        station.getTools().setStackInSlot(0, ItemStack.EMPTY);
        ItemStack gridSaw = new ItemStack(saw);
        int gridSawDamage = gridSaw.getDamageValue();
        station.getGrid().setStackInSlot(0, gridSaw);
        station.getGrid().setStackInSlot(3, new ItemStack(GSEBlocks.CRAFTING_STATION_ITEM.get()));
        menu.broadcastChanges();
        ItemStack directResult = menu.getSlot(CraftingStationMenu.RESULT_SLOT).getItem().copy();
        helper.assertTrue(directResult.is(GSEBlocks.CRAFTING_STATION_SLAB_ITEM.get()),
                "Direct-grid saw recipe did not match");
        menu.getSlot(CraftingStationMenu.RESULT_SLOT).onTake(player, directResult);
        helper.assertTrue(station.getGrid().getStackInSlot(0).is(saw)
                        && station.getGrid().getStackInSlot(0).getDamageValue() > gridSawDamage,
                "Damaged tool remainder was not retained in its pattern cell");

        clearGrid(station);
        station.getGrid().setStackInSlot(0, new ItemStack(Items.WHEAT));
        ItemStack before = station.getGrid().getStackInSlot(0).copy();
        menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(CraftingStationMenu.RESULT_SLOT).getItem().isEmpty(),
                "Incomplete recipe exposed a stale result");
        helper.assertTrue(menu.quickMoveStack(player, CraftingStationMenu.RESULT_SLOT).isEmpty()
                        && ItemStack.matches(before, station.getGrid().getStackInSlot(0)),
                "Rejected craft mutated its input");

        menu.removed(player);
        helper.succeed();
    }

    private static void clearGrid(CraftingStationBlockEntity station) {
        for (int slot = 0; slot < station.getGrid().getSlots(); slot++) {
            station.getGrid().setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    private static int count(Player player, Item item) {
        return player.getInventory().items.stream()
                .filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
