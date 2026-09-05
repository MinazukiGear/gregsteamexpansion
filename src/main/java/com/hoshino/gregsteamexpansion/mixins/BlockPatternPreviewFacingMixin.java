package com.hoshino.gregsteamexpansion.mixins;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * EMI/JEI 多方块预览朝向 (steam-crushers.md 结构预览): the preview's auto
 * rotation turns every non-controller machine toward the FIRST adjacent air
 * side in a fixed order, so adjacent interfaces (a bus row, the large
 * crusher's I/K/O wall) end up facing inconsistent sides — only the row end
 * keeps the outward front. After the vanilla rotation, restore the facing the
 * shape info explicitly wrote whenever that side is still air: shape authors
 * place interfaces with their front pointing out of the structure, and the
 * restore makes the whole row face outward again. World auto-build
 * ({@code resetAllFacing}) keeps the vanilla behaviour — this only touches
 * the preview path.
 */
@Mixin(value = BlockPattern.class, remap = false)
public abstract class BlockPatternPreviewFacingMixin {

    // The resetFacing call sits inside getPreview's forEach LAMBDA — the
    // synthetic method lambda$getPreview$6, not getPreview itself (that
    // mismatch failed the injection check with 0/1 succeeded).
    @WrapOperation(method = "lambda$getPreview$6", at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/api/pattern/BlockPattern;resetFacing(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Ljava/util/function/BiPredicate;Ljava/util/function/Consumer;)V"))
    // instance method target: the wrapper's first parameter is the target
    // class instance (this)
    private void gse$preferShapeFacing(BlockPattern instance, BlockPos pos, BlockState state, Direction facing,
                                       BiPredicate<BlockPos, Direction> checker,
                                       Consumer<BlockState> consumer, Operation<Void> original) {
        original.call(instance, pos, state, facing, checker, consumer);

        Direction shapeFacing = null;
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            shapeFacing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        // Only restore horizontal fronts the vanilla pass may have moved away,
        // and only when the shape-written front still looks at air (the same
        // validity rule the vanilla pass just used).
        if (shapeFacing != null && shapeFacing != state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                && checker.test(pos, shapeFacing)) {
            consumer.accept(state.setValue(BlockStateProperties.HORIZONTAL_FACING, shapeFacing));
        }
    }
}
