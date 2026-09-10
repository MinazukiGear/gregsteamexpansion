package com.hoshino.gregsteamexpansion.command;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.hoshino.gregsteamexpansion.structure.StructureDiagnostics;
import com.hoshino.gregsteamexpansion.structure.StructureProblem;
import com.hoshino.gregsteamexpansion.structure.StructureText;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * 结构诊断调试指令 (structure-diagnostics.md 通道 T6 / 决策 P4)。
 *
 * <p>{@code /gse structure} 打印玩家准星所指控制器的**完整**诊断 (候选不截断),
 * 供调试与人工排查使用。玩家侧的正常入口是 Jade (通道 T1); 这条指令的价值在于
 * 给出被截断掉的候选全貌和引擎原始错误键。
 */
public final class GSECommands {

    /** 准星搜索距离, 与提示文案里的数值保持一致。 */
    private static final int REACH = 12;
    /** 指令里一次最多列出的候选数 (全量候选可能有上百项)。 */
    private static final int MAX_LISTED = 16;

    private GSECommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gse")
                .then(Commands.literal("structure")
                        .requires(source -> source.hasPermission(2))
                        .executes(GSECommands::reportStructure)));
    }

    private static int reportStructure(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayerOrException();
        HitResult hit = player.pick(REACH, 0.0F, false);
        MetaMachine machine = hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                ? MetaMachine.getMachine(player.level(), blockHit.getBlockPos())
                : null;
        if (!(machine instanceof IMultiController controller)) {
            source.sendFailure(Component.translatable("gregsteamexpansion.command.structure.no_controller",
                    REACH));
            return 0;
        }
        if (controller.isFormed()) {
            source.sendSuccess(() -> Component
                    .translatable("gregsteamexpansion.command.structure.valid")
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        // 全量候选 (Integer.MAX_VALUE = 不截断), 列表长度在输出时再限。
        StructureProblem problem = StructureDiagnostics.describe(controller, Integer.MAX_VALUE)
                .orElseGet(() -> new StructureProblem(StructureProblem.Kind.UNINITIALIZED, null,
                        List.of(), 0, StructureProblem.NO_LIMIT, 0, null));

        source.sendSuccess(() -> Component
                .translatable("gregsteamexpansion.command.structure.reason", StructureText.reason(problem))
                .withStyle(ChatFormatting.YELLOW), false);
        if (problem.hasPosition()) {
            BlockPos pos = problem.pos();
            source.sendSuccess(() -> Component
                    .translatable("gregsteamexpansion.command.structure.position", pos.toShortString())
                    .withStyle(ChatFormatting.GRAY), false);
        }
        if (!problem.expected().isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "gregsteamexpansion.command.structure.expected", expectedList(problem)), false);
        }
        if (problem.rawKey() != null) {
            source.sendSuccess(() -> Component
                    .translatable("gregsteamexpansion.command.structure.raw_key", problem.rawKey())
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return 1;
    }

    /**
     * 候选列表。逐段 append 惰性 {@code Component} 而不是拼接 {@code String}:
     * 服务端上 {@code getString()} 会固化成服务端语言, 发到客户端就换不回来了
     * (设计文档 R6)。超出上限时补一句"(共 N 种, 显示前 M 种)"。
     */
    private static Component expectedList(StructureProblem problem) {
        List<ItemStack> all = problem.expected();
        int shown = Math.min(all.size(), MAX_LISTED);
        MutableComponent line = Component.empty();
        Component separator = Component.translatable("gregsteamexpansion.structure.list_separator");
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                line.append(separator);
            }
            line.append(all.get(i).getHoverName());
        }
        if (all.size() > shown) {
            line.append(" ");
            line.append(Component.translatable("gregsteamexpansion.command.structure.truncated",
                    all.size(), shown));
        }
        return line;
    }
}
