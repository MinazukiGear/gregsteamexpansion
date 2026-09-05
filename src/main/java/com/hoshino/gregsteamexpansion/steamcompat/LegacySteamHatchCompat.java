package com.hoshino.gregsteamexpansion.steamcompat;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.data.GTRecipes;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamHatchIOTransfer;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamSupplyHatchPartMachine;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 禁用 GTCEu 7.5.3 蒸汽输入仓并执行 1:1 迁移
 * (machines-and-hatches.md "GTCEu 蒸汽输入仓的禁用范围" 与 "旧存档迁移").
 *
 * <p>The disable has five coordinated pieces, each matching exactly one
 * registration object:</p>
 * <ol>
 * <li>{@code gtceu:steam_hatch} recipe — filtered at every resource reload via
 * {@code GregSteamExpansionAddon#removeRecipes()} (GTCEu resource-pack filter).</li>
 * <li>{@code gtceu:steam_input_hatch} ability membership — skipped by
 * {@link com.hoshino.gregsteamexpansion.mixins.PartAbilityMixin}.</li>
 * <li>Creative tab / EMI visibility — hidden by
 * {@link com.hoshino.gregsteamexpansion.mixins.RegistrateDisplayItemsGeneratorMixin}
 * and {@link com.hoshino.gregsteamexpansion.integration.emi.GSEEmiPlugin}.</li>
 * <li>Placement of a legacy item — converted 1:1 in place right after the block
 * event, so no legacy block can ever exist again.</li>
 * <li>Legacy blocks from old saves — migrated in place with full machine state
 * (facing, paint, covers, steam content incl. overflow) when their chunk loads;
 * item forms convert on entity load, pickup, login and container access.</li>
 * </ol>
 *
 * <p>{@link #onServerAboutToStart} verifies the three design-mandated
 * conditions (旧仓未登记、新仓已登记、旧配方不存在) and aborts world load with a
 * clear error when the disable is only half-effective, so a machine never
 * accepts different hatches across restarts. The disable matches only the
 * precise resource IDs — never a name substring, the machine class or the whole
 * {@code PartAbility.STEAM} ability.</p>
 */
@Mod.EventBusSubscriber(modid = GregSteamExpansion.MOD_ID)
public final class LegacySteamHatchCompat {

    private static final ResourceLocation LEGACY_HATCH_ID = GTCEu.id("steam_input_hatch");
    private static final ResourceLocation LEGACY_RECIPE_ID = GTCEu.id("steam_hatch");

    private LegacySteamHatchCompat() {}

    /** Null when the installed GTCEu no longer registers the legacy block. */
    @Nullable
    private static Block legacyBlock() {
        return ForgeRegistries.BLOCKS.getValue(LEGACY_HATCH_ID);
    }

    private static boolean isLegacyItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return LEGACY_HATCH_ID.equals(id);
    }

    private static ItemStack supplyHatchStack(int count) {
        return GSEMachines.STEAM_SUPPLY_HATCH.asStack(count);
    }

    //////////////////////////////////////
    // ****** Verification ******//
    //////////////////////////////////////

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        List<String> violations = verifyDisableState(event.getServer());
        if (!violations.isEmpty()) {
            GregSteamExpansion.LOGGER.error(
                    "Legacy steam input hatch disable is half-effective; refusing to load the world: {}",
                    String.join("; ", violations));
            throw new IllegalStateException(
                    "Greg Steam Expansion: legacy gtceu:steam_input_hatch disable verification failed: "
                            + String.join("; ", violations));
        }
    }

    /**
     * 三个启动核验条件 (machines-and-hatches.md 启动核验): the legacy block is no
     * longer registered in any part ability, the new supply hatch is registered
     * in {@code PartAbility.STEAM}, and the legacy recipe no longer exists. The
     * ability map is read reflectively so a stale memoized view cannot mask a
     * half-applied disable.
     */
    static List<String> verifyDisableState(MinecraftServer server) {
        List<String> violations = new ArrayList<>();
        Block legacy = legacyBlock();
        Block supply = GSEMachines.STEAM_SUPPLY_HATCH.getBlock();

        if (abilityRegistryContains(PartAbility.STEAM, legacy)) {
            violations.add("gtceu:steam_input_hatch is still registered in PartAbility.STEAM");
        }
        if (legacy != null && PartAbility.STEAM.isApplicable(legacy)) {
            violations.add("gtceu:steam_input_hatch is still applicable to PartAbility.STEAM");
        }
        if (!abilityRegistryContains(PartAbility.STEAM, supply) || !PartAbility.STEAM.isApplicable(supply)) {
            violations.add("gregsteamexpansion:steam_supply_hatch is not registered in PartAbility.STEAM");
        }
        if (!GTRecipes.RECIPE_FILTERS.contains(LEGACY_RECIPE_ID)) {
            violations.add("the gtceu:steam_hatch recipe filter was never registered");
        }
        if (server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)
                .stream().anyMatch(recipe -> recipe.getId().equals(LEGACY_RECIPE_ID))) {
            violations.add("the gtceu:steam_hatch recipe still exists");
        }
        return violations;
    }

    @SuppressWarnings("unchecked")
    private static boolean abilityRegistryContains(PartAbility ability, @Nullable Block block) {
        if (block == null) {
            return false;
        }
        try {
            // registry is a per-ability instance map (tier -> blocks).
            Field field = PartAbility.class.getDeclaredField("registry");
            field.setAccessible(true);
            Int2ObjectMap<Set<Block>> registry = (Int2ObjectMap<Set<Block>>) field.get(ability);
            for (Set<Block> blocks : registry.values()) {
                if (blocks.contains(block)) {
                    return true;
                }
            }
            return false;
        } catch (ReflectiveOperationException | ClassCastException e) {
            // Cannot verify -> treat as half-effective (verified only against
            // GTCEu 7.5.3; re-verify every supported GTCEu version).
            throw new IllegalStateException(
                    "Unable to inspect PartAbility registrations for the legacy steam hatch check", e);
        }
    }

    //////////////////////////////////////
    // ****** Placement conversion ******//
    //////////////////////////////////////

    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide() || event.isCanceled()) {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        Block legacy = legacyBlock();
        if (legacy == null || !placed.is(legacy) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // 1:1 conversion in place: the legacy item was already consumed by the
        // placement, so replacing the fresh block keeps counts exact and never
        // produces a legacy block that could persist.
        BlockState newState = GSEMachines.STEAM_SUPPLY_HATCH.getBlock().defaultBlockState();
        for (var property : newState.getBlock().getStateDefinition().getProperties()) {
            if (property instanceof DirectionProperty direction && oldStateHasValue(placed, direction)
                    && direction.getPossibleValues().contains(placed.getValue(direction))) {
                newState = newState.trySetValue(direction, placed.getValue(direction));
            }
        }
        level.setBlock(event.getPos(), newState, Block.UPDATE_ALL);
        GregSteamExpansion.LOGGER.debug("Converted legacy steam input hatch placement at {} to a steam supply hatch",
                event.getPos());
    }

    private static boolean oldStateHasValue(BlockState state, DirectionProperty direction) {
        return state.hasProperty(direction);
    }

    //////////////////////////////////////
    // ****** World migration ******//
    //////////////////////////////////////

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        Block legacy = legacyBlock();
        if (legacy == null) {
            return;
        }
        List<BlockPos> found = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            if (entry.getValue().getBlockState().is(legacy)) {
                found.add(entry.getKey().immutable());
            }
        }
        if (found.isEmpty()) {
            return;
        }
        // Defer to the next tick: the chunk is still finishing its load pass.
        level.getServer().tell(new TickTask(level.getServer().getTickCount(), () -> {
            for (BlockPos pos : found) {
                migrateLegacyHatch(level, pos);
            }
        }));
    }

    /**
     * 旧存档迁移 (machines-and-hatches.md 旧存档迁移): fully read the legacy block
     * entity first, then create and fill the replacement hatch, then let the
     * block swap discard the legacy entity. Any failure restores the original
     * block and its saved data untouched and logs the coordinates — the world
     * never ends up with air or a half-written hatch.
     */
    static void migrateLegacyHatch(ServerLevel level, BlockPos pos) {
        Block legacy = legacyBlock();
        BlockState oldState = level.getBlockState(pos);
        if (legacy == null || !oldState.is(legacy)) {
            return;
        }
        BlockEntity oldEntity = level.getBlockEntity(pos);
        if (!(oldEntity instanceof IMachineBlockEntity machineHolder)) {
            GregSteamExpansion.LOGGER.warn("Legacy steam hatch at {} has no machine block entity; left untouched",
                    pos);
            return;
        }
        MetaMachine machine = machineHolder.getMetaMachine();
        if (!(machine instanceof FluidHatchPartMachine oldHatch)) {
            GregSteamExpansion.LOGGER.warn("Legacy steam hatch at {} is not a fluid hatch machine; left untouched",
                    pos);
            return;
        }

        // Full read before any mutation.
        CompoundTag savedOld = oldEntity.saveWithoutMetadata();
        Direction frontFacing = oldHatch.getFrontFacing();
        Direction upwardsFacing = oldHatch.getUpwardsFacing();
        int paintingColor = oldHatch.getPaintingColor();
        boolean workingEnabled = oldHatch.isWorkingEnabled();
        var steam = oldHatch.tank.getFluidInTank(0).copy();
        List<SteamHatchIOTransfer.CoverData> covers = SteamHatchIOTransfer.detachCoversSilently(oldHatch);

        BlockState newState = GSEMachines.STEAM_SUPPLY_HATCH.getBlock().defaultBlockState();
        if (frontFacing != null) {
            for (var property : newState.getBlock().getStateDefinition().getProperties()) {
                if (property instanceof DirectionProperty direction && newState.hasProperty(direction)
                        && direction.getPossibleValues().contains(frontFacing)) {
                    newState = newState.trySetValue(direction, frontFacing);
                }
            }
        }

        level.setBlock(pos, newState, Block.UPDATE_ALL);
        BlockEntity newEntity = level.getBlockEntity(pos);
        if (newEntity instanceof IMachineBlockEntity newHolder &&
                newHolder.getMetaMachine() instanceof SteamSupplyHatchPartMachine newHatch) {
            newHatch.setFrontFacing(frontFacing);
            if (upwardsFacing != null) {
                newHatch.setUpwardsFacing(upwardsFacing);
            }
            newHatch.setPaintingColor(paintingColor);
            newHatch.setWorkingEnabled(workingEnabled);
            // Over-limit legacy steam is preserved verbatim: the tank rejects
            // further input while above capacity but keeps draining normally
            // (machines-and-hatches.md 超额存量处理).
            newHatch.tank.setFluidInTank(0, steam);
            SteamHatchIOTransfer.restoreCovers(newHatch, covers, pos);
            newHatch.markDirty();
            newEntity.setChanged();
            GregSteamExpansion.LOGGER.info("Migrated a legacy steam input hatch at {} ({} mB steam, {} covers)",
                    pos, steam.getAmount(), covers.size());
            return;
        }

        // Failure: put the legacy block back exactly as it was.
        GregSteamExpansion.LOGGER.error("Failed to create a steam supply hatch at {}; legacy hatch restored", pos);
        level.setBlock(pos, oldState, Block.UPDATE_ALL);
        BlockEntity restored = level.getBlockEntity(pos);
        if (restored != null) {
            restored.load(savedOld);
            restored.setChanged();
        }
        SteamHatchIOTransfer.dropCapturedCovers(level, pos, covers);
    }

    //////////////////////////////////////
    // ****** Item conversion ******//
    //////////////////////////////////////

    /** 掉落物加载/生成时按 1:1 转换 (machines-and-hatches.md 旧存档迁移). */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (isLegacyItem(stack)) {
            itemEntity.setItem(supplyHatchStack(stack.getCount()));
        }
    }

    /** 玩家拾取后转换背包中的旧仓物品 (machines-and-hatches.md 旧存档迁移). */
    @SubscribeEvent
    public static void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) {
            convertContainer(player.getInventory());
        }
    }

    /** 登录时转换随身物品，覆盖旧存档玩家背包中的旧仓. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) {
            convertContainer(player.getInventory());
        }
    }

    /** 容器被打开时转换其中与玩家物品栏中的旧仓物品. */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        AbstractContainerMenu menu = event.getContainer();
        for (Slot slot : menu.slots) {
            convertContainer(slot.container);
        }
    }

    private static void convertContainer(Container container) {
        boolean changed = false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (isLegacyItem(stack)) {
                // 1:1 by count, no steam carried, no recipe statistics
                // (machines-and-hatches.md 物品转换).
                container.setItem(i, supplyHatchStack(stack.getCount()));
                changed = true;
            }
        }
        if (changed) {
            container.setChanged();
        }
    }
}
