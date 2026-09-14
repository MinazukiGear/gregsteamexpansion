package com.hoshino.gregsteamexpansion.gametest;

import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * 结构成型测试工具。
 *
 * <p>每台多方块机器注册时都带了 {@link MultiblockShapeInfo}（没写的也会由
 * {@code MultiblockMachineDefinition#getMatchingShapes()} 从 pattern preview 生成），
 * 里面是成型所需的全部方块的真实 BlockState（外壳 + 控制器 + 各仓室）。
 * 把它按坐标铺进 gametest 区域，就得到了一台"照着答案摆好的机器"——
 * 成型失败一定是图案/ShapeInfo 本身的 bug，不是摆放手误。
 *
 * <p>配合足尺寸的空气模板使用（3x1x1 的 empty 放不下多方块）。
 */
public final class GSEStructureTestUtils {

    private GSEStructureTestUtils() {}

    /**
     * 铺出该机器第一个 shape 并按 EMI 的方式断言其 pattern 可以匹配。
     *
     * <p>这里必须使用注册到机器定义上的 {@link MultiblockShapeInfo}：EMI 的
     * {@code PatternPreviewWidget} 消费的正是这组 shape。若改从 pattern 重新生成，
     * 测试只会证明 pattern 自身正确，无法发现预览 shape 的转置或朝向错误。
     */
    public static void assertFirstShapeForms(GameTestHelper helper, MultiblockMachineDefinition definition) {
        java.util.List<MultiblockShapeInfo> shapes = definition.getMatchingShapes();
        if (shapes.isEmpty()) {
            helper.fail("No preview shape registered for " + definition.getId());
            return;
        }
        MultiblockShapeInfo shape = shapes.get(0);
        MultiblockControllerMachine controller = placeShape(helper, definition, shape);
        if (controller == null) {
            helper.fail("Shape for " + definition.getId() + " contains no controller block of that definition");
            return;
        }
        var pattern = controller.getPattern();
        if (pattern == null) {
            helper.fail("No pattern registered for " + definition.getId());
            return;
        }
        if (!pattern.checkPatternAt(controller.getMultiblockState(), true)) {
            helper.fail("Preview shape for " + definition.getId() + " does not match its pattern: "
                    + describeError(helper, controller));
            return;
        }
        helper.succeed();
    }

    /**
     * Verifies a normal preview shape, then swaps its first target block and
     * checks whether the replacement is admitted by the same structure.
     */
    public static void assertFirstShapeReplacementMatches(GameTestHelper helper,
                                                          MultiblockMachineDefinition definition,
                                                          Block target,
                                                          MachineDefinition replacement,
                                                          boolean expectedMatch) {
        java.util.List<MultiblockShapeInfo> shapes = definition.getMatchingShapes();
        if (shapes.isEmpty()) {
            helper.fail("No preview shape registered for " + definition.getId());
            return;
        }
        MultiblockShapeInfo shape = shapes.get(0);
        MultiblockControllerMachine controller = placeShape(helper, definition, shape);
        if (controller == null || controller.getPattern() == null) {
            helper.fail("No usable pattern registered for " + definition.getId());
            return;
        }
        if (!controller.getPattern().checkPatternAt(controller.getMultiblockState(), true)) {
            helper.fail("Baseline preview shape for " + definition.getId() + " does not match: "
                    + describeError(helper, controller));
            return;
        }

        BlockInfo[][][] blocks = shape.getBlocks();
        int controllerX = blocks.length / 2;
        int controllerY = findControllerY(blocks, definition);
        if (controllerY < 0) {
            helper.fail("Preview shape for " + definition.getId() + " contains no controller block");
            return;
        }
        BlockPos anchor = new BlockPos(16, 16, 16);
        BlockPos replacementPos = null;
        for (int x = 0; x < blocks.length && replacementPos == null; x++) {
            for (int y = 0; y < blocks[x].length && replacementPos == null; y++) {
                for (int z = 0; z < blocks[x][y].length; z++) {
                    BlockInfo info = blocks[x][y][z];
                    if (info != null && info != BlockInfo.EMPTY && info.getBlockState().is(target)) {
                        replacementPos = anchor.offset(x - controllerX, y - controllerY, z);
                        break;
                    }
                }
            }
        }
        if (replacementPos == null) {
            helper.fail("Preview shape for " + definition.getId() + " contains no target block " + target);
            return;
        }

        BlockState replacementState = replacement.getBlock().defaultBlockState();
        RotationState replacementRotation = replacement.getRotationState();
        if (replacementRotation != RotationState.NONE && replacementState.hasProperty(replacementRotation.property)) {
            replacementState = replacementState.setValue(replacementRotation.property, Direction.NORTH);
        }
        helper.setBlock(replacementPos, replacementState);
        boolean matched = controller.getPattern().checkPatternAt(controller.getMultiblockState(), true);
        if (matched != expectedMatch) {
            helper.fail("Replacement " + replacement.getId() + (expectedMatch ? " was rejected by " : " was accepted by ")
                    + definition.getId() + ": " + describeError(helper, controller));
            return;
        }
        helper.succeed();
    }

    private static int findControllerY(BlockInfo[][][] blocks, MultiblockMachineDefinition definition) {
        for (int x = 0; x < blocks.length; x++) {
            for (int y = 0; y < blocks[x].length; y++) {
                for (int z = 0; z < blocks[x][y].length; z++) {
                    BlockInfo info = blocks[x][y][z];
                    if (info != null && info != BlockInfo.EMPTY
                            && info.getBlockState().getBlock() == definition.getBlock()) {
                        return y;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * 按 shape 铺方块，返回找到的控制器。失败返回 null。
     *
     * <p>{@code PatternPreviewWidget} 固定把 shape 数组的三个维度当作世界
     * X/Y/Z 放进 dummy world；本测试必须采用相同约定。builder 的字符串布局
     * 只是构造 shape 的手段，pattern 的 {@code start(...)} 不能在这里再次应用，
     * 否则会把已转换好的预览坐标二次转置。
     */
    @Nullable
    public static MultiblockControllerMachine placeShape(GameTestHelper helper,
                                                         MultiblockMachineDefinition definition,
                                                         MultiblockShapeInfo shape) {
        return placeShape(helper, definition, shape, new BlockPos(16, 16, 16), Direction.NORTH);
    }

    /**
     * 按指定控制器位置和水平朝向铺出 shape。shape 以北向预览为基准，
     * 所有坐标及带方向的方块状态围绕控制器同步旋转。
     */
    @Nullable
    public static MultiblockControllerMachine placeShape(GameTestHelper helper,
                                                         MultiblockMachineDefinition definition,
                                                         MultiblockShapeInfo shape,
                                                         BlockPos anchor,
                                                         Direction facing) {
        if (!facing.getAxis().isHorizontal()) {
            helper.fail("Shape facing must be horizontal for " + definition.getId() + ": " + facing);
            return null;
        }
        BlockInfo[][][] blocks = shape.getBlocks();
        int sizeChar = blocks.length;
        int sizeRow = sizeChar > 0 ? blocks[0].length : 0;
        int sizeAisle = sizeRow > 0 ? blocks[0][0].length : 0;
        if (sizeChar == 0 || sizeRow == 0 || sizeAisle == 0) {
            helper.fail("Shape for " + definition.getId() + " is empty");
            return null;
        }

        // 先定位控制器在数组里的下标（shapeInfo 与 pattern 共用同一套字符串布局，
        // 故这也等价于 pattern 的 centerOffset）。
        int cc = -1, cr = -1, ca = -1;
        for (int x = 0; x < sizeChar; x++) {
            for (int y = 0; y < sizeRow; y++) {
                for (int z = 0; z < sizeAisle; z++) {
                    BlockInfo info = blocks[x][y][z];
                    if (info == null || info == BlockInfo.EMPTY) continue;
                    BlockState st = info.getBlockState();
                    if (st == null || st.isAir()) continue;
                    if (st.getBlock() == definition.getBlock()) {
                        cc = x; cr = y; ca = z;
                    }
                }
            }
        }
        if (cc < 0) {
            helper.fail("Shape for " + definition.getId() + " contains no controller block");
            return null;
        }
        // Pattern/shape agreement cannot catch both definitions drifting to
        // the same wrong column. All GSE multiblocks put the controller at the
        // horizontal centre of the front face, so assert that design rule too.
        int expectedControllerX = sizeChar / 2;
        if (cc != expectedControllerX || ca != 0) {
            helper.fail("Controller for " + definition.getId()
                    + " must be at front-face horizontal centre [x=" + expectedControllerX + ", z=0]"
                    + ", but preview shape uses [x=" + cc + ", z=" + ca + "]");
            return null;
        }

        var level = helper.getLevel();
        Rotation rotation = rotationFromNorth(facing);

        // 先单独放下控制器，并把北向预览旋转到目标朝向。
        BlockState controllerState = blocks[cc][cr][ca].getBlockState().rotate(rotation);
        RotationState rs = definition.getRotationState();
        if (rs != RotationState.NONE && controllerState.hasProperty(rs.property)) {
            controllerState = controllerState.setValue(rs.property, facing);
        }
        helper.setBlock(anchor, controllerState);
        MetaMachine ctrlMachine = MetaMachine.getMachine(level, helper.absolutePos(anchor));
        if (!(ctrlMachine instanceof MultiblockControllerMachine controller)) {
            helper.fail("Controller for " + definition.getId() + " did not instantiate after placement");
            return null;
        }
        // Shape 数组已经是世界 X/Y/Z；相对控制器原样铺放。
        for (int x = 0; x < sizeChar; x++) {
            for (int y = 0; y < sizeRow; y++) {
                for (int z = 0; z < sizeAisle; z++) {
                    BlockInfo info = blocks[x][y][z];
                    if (info == null || info == BlockInfo.EMPTY) continue;
                    BlockState state = info.getBlockState();
                    if (state == null || state.isAir()) continue;
                    state = state.rotate(rotation);
                    if (state.getBlock() == definition.getBlock()) {
                        RotationState rs2 = definition.getRotationState();
                        if (rs2 != RotationState.NONE && state.hasProperty(rs2.property)) {
                            state = state.setValue(rs2.property, facing);
                        }
                    }
                    int dx = x - cc, dy = y - cr, dz = z - ca;
                    BlockPos rotated = new BlockPos(dx, dy, dz).rotate(rotation);
                    BlockPos pos = anchor.offset(rotated);
                    helper.setBlock(pos, state);
                }
            }
        }
        return controller;
    }

    private static Rotation rotationFromNorth(Direction facing) {
        return switch (facing) {
            case NORTH -> Rotation.NONE;
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new IllegalArgumentException("Facing must be horizontal: " + facing);
        };
    }

    /**
     * 失败信息尽量给足可定位的细节：控制器相对坐标 + 出错格的相对坐标 + 该格实际方块。
     * 期望方块列表（PatternError#getCandidates）太长太吵，改成输出错误类型名。
     */
    private static String describeError(GameTestHelper helper, MultiblockControllerMachine controller) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        StringBuilder text = new StringBuilder()
                .append("controller@").append(controller.getPos().subtract(origin));
        var state = controller.getMultiblockState();
        if (state == null || state.error == null) {
            return text.append(" | no pattern error recorded (controller never ran a check?)").toString();
        }
        BlockPos errorPos = state.error.getPos();
        var actual = helper.getLevel().getBlockState(errorPos);
        return text.append(" | ").append(state.error.getClass().getSimpleName())
                .append(" at ").append(errorPos.subtract(origin))
                .append(" (world ").append(errorPos).append(")")
                .append(" | actual=").append(actual)
                .toString();
    }
}
