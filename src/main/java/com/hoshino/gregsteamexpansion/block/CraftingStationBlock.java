package com.hoshino.gregsteamexpansion.block;

import com.hoshino.gregsteamexpansion.blockentity.CraftingStationBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

public class CraftingStationBlock extends Block implements EntityBlock {
    public CraftingStationBlock(Properties properties) {
        super(properties);
    }

    /**
     * Shared right-click behaviour for both forms: a single player at a time
     * may have the interface open (crafting-station.md 1.7).
     */
    protected static InteractionResult openStation(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof CraftingStationBlockEntity station) {
            if (!level.isClientSide) {
                if (!station.tryStartViewing(player)) {
                    player.displayClientMessage(CraftingStationBlockEntity.IN_USE_MESSAGE, true);
                    return InteractionResult.CONSUME;
                }
                if (player instanceof ServerPlayer serverPlayer) {
                    NetworkHooks.openScreen(serverPlayer, station, pos);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        return openStation(level, pos, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CraftingStationBlockEntity station) {
            station.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CraftingStationBlockEntity(pos, state);
    }
}
