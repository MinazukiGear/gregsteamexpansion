package com.hoshino.gregsteamexpansion.terminal;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.hoshino.gregsteamexpansion.menu.UltimateTerminalMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Player-facing entry point for Ultimate Terminal projects. */
public final class UltimateTerminalItem extends Item {
    public UltimateTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !TerminalCompatibility.isAvailable()) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        if (!(MetaMachine.getMachine(context.getLevel(), pos) instanceof IMultiController)) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide && player instanceof ServerPlayer serverPlayer) {
            UltimateTerminalWorldData data = UltimateTerminalWorldData.get(serverPlayer.server);
            boolean changed = player.isShiftKeyDown()
                    ? data.removeTarget(serverPlayer, context.getLevel().dimension(), pos)
                    : data.addTarget(serverPlayer, context.getLevel().dimension(), pos);
            Component message = Component.translatable(changed
                    ? (player.isShiftKeyDown()
                            ? "gregsteamexpansion.ultimate_terminal.target_removed"
                            : "gregsteamexpansion.ultimate_terminal.target_added")
                    : "gregsteamexpansion.ultimate_terminal.target_unchanged");
            player.displayClientMessage(message, true);
            if (changed) UltimateTerminalMessages.forceFull(serverPlayer);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!TerminalCompatibility.isAvailable()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        "gregsteamexpansion.ultimate_terminal.dependencies_missing"), true);
            }
            return InteractionResultHolder.fail(held);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                    (id, inventory, ignored) -> new UltimateTerminalMenu(id, inventory),
                    Component.translatable("item.gregsteamexpansion.ultimate_terminal")));
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, net.minecraft.world.level.LevelReader level,
                                      BlockPos pos, Player player) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (TerminalCompatibility.isAvailable()) {
            tooltip.add(Component.translatable("gregsteamexpansion.ultimate_terminal.tooltip.1")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("gregsteamexpansion.ultimate_terminal.tooltip.2")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("gregsteamexpansion.ultimate_terminal.dependencies_missing")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
