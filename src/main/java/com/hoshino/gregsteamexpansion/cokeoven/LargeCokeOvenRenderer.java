package com.hoshino.gregsteamexpansion.cokeoven;

import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderManager;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.largecokeoven.LargeCokeOvenMachine;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 大型焦炉三炉门同步渲染 (coke-ovens.md 已确认纹理、模型、粒子与声音):
 * 三个炉门由控制器根据同一份权威状态快照统一渲染 — 只有主状态为"正在工作"
 * 时同步点亮自发光火焰, 其余状态全部熄灭; 正面小型状态符号以不同轮廓区分
 * 工作 (火焰形) / 堵塞或等待输出 (带感叹号箱形) / 结构无效 (断裂轮廓)。
 * 不给通用的 gtceu:coke_oven_bricks 增加方块实体、持久化状态或独立 tick。
 *
 * <p>实际并行 1–6 不映射到特定炉室或点亮数量; 三个炉室始终同步开始与熄灭。</p>
 */
public class LargeCokeOvenRenderer extends DynamicRender<LargeCokeOvenMachine, LargeCokeOvenRenderer> {

    public static final Codec<LargeCokeOvenRenderer> CODEC = Codec.unit(new LargeCokeOvenRenderer());
    public static final DynamicRenderType<LargeCokeOvenMachine, LargeCokeOvenRenderer> TYPE =
            new DynamicRenderType<>(CODEC);

    static {
        DynamicRenderManager.register(GregSteamExpansion.id("large_coke_oven"), TYPE);
    }

    private static final Material DOOR_OFF = new Material(TextureAtlas.LOCATION_BLOCKS,
            GregSteamExpansion.id("block/large_coke_oven/furnace_door"));
    private static final Material DOOR_LIT = new Material(TextureAtlas.LOCATION_BLOCKS,
            GregSteamExpansion.id("block/large_coke_oven/furnace_door_lit"));
    private static final Material SYMBOL_FLAME = new Material(TextureAtlas.LOCATION_BLOCKS,
            GregSteamExpansion.id("block/large_coke_oven/status_flame"));
    private static final Material SYMBOL_BLOCKED = new Material(TextureAtlas.LOCATION_BLOCKS,
            GregSteamExpansion.id("block/large_coke_oven/status_blocked"));
    private static final Material SYMBOL_INVALID = new Material(TextureAtlas.LOCATION_BLOCKS,
            GregSteamExpansion.id("block/large_coke_oven/status_invalid"));

    /** 炉室列的宽度偏移 (0 为控制器列): 左炉室 -2、中炉室 0、右炉室 +2。 */
    private static final int[] CHAMBER_OFFSETS = {-2, 0, 2};

    @Override
    public DynamicRenderType<LargeCokeOvenMachine, LargeCokeOvenRenderer> getType() {
        return TYPE;
    }

    @Override
    public AABB getRenderBoundingBox(LargeCokeOvenMachine machine) {
        BlockPos pos = machine.self().getPos();
        return new AABB(pos.offset(-3, 0, -3), pos.offset(4, 6, 4));
    }

    @Override
    public boolean shouldRenderOffScreen(LargeCokeOvenMachine machine) {
        return false;
    }

    @Override
    public boolean shouldRender(LargeCokeOvenMachine machine, Vec3 cameraPos) {
        return super.shouldRender(machine, cameraPos);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void render(LargeCokeOvenMachine machine, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        boolean working = machine.getOvenStatus() == LargeCokeOvenMachine.OvenStatus.WORKING;
        Direction front = machine.getFrontFacing();
        Quaternionf rotation = front.getRotation();

        var consumer = bufferSource.getBuffer(RenderType.cutout());
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        // 三炉门: 每炉室 1 宽 × 3 高, 同步点亮/熄灭 (9 个 1×1 quad)。
        var doorSprite = (working ? DOOR_LIT : DOOR_OFF).sprite();
        int doorLight = working ? LightTexture.FULL_BRIGHT : packedLight;
        for (int chamber : CHAMBER_OFFSETS) {
            for (int row = 1; row <= 3; row++) {
                drawTemplateQuad(pose, normal, consumer, doorSprite, rotation,
                        chamber, row, -0.5f, 0.5f, doorLight);
            }
        }

        // 正面状态符号 (右上角小块); 其余状态不点亮特殊符号。
        Material symbol = switch (machine.getOvenStatus()) {
            case WORKING -> SYMBOL_FLAME;
            case WAITING_OUTPUT, STARTUP_OUTPUT_BLOCKED -> SYMBOL_BLOCKED;
            case STRUCTURE_INVALID -> SYMBOL_INVALID;
            default -> null;
        };
        if (symbol != null) {
            drawTemplateQuad(pose, normal, consumer, symbol.sprite(), rotation,
                    0.7f, 0.7f, -0.505f, 0.125f, LightTexture.FULL_BRIGHT);
        }
    }

    /**
     * 在模板坐标系 (F=NORTH, 控制器格中心为原点) 绘制一个面向 -Z 的正方形
     * quad (UV 覆盖整张贴图), 再经 {@link Direction#getRotation()} 旋转到
     * 控制器的实际朝向。
     *
     * @param cx     模板 X 中心 (炉室列偏移或符号位置)
     * @param cy     模板 Y 中心 (行 1..3 或符号位置)
     * @param depth  模板 Z 平面 (正面墙 -0.5)
     * @param half   quad 半宽
     */
    @OnlyIn(Dist.CLIENT)
    private static void drawTemplateQuad(Matrix4f pose, Matrix3f normal, VertexConsumer consumer,
                                         net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
                                         Quaternionf rotation, float cx, float cy, float depth, float half,
                                         int light) {
        // 模板四角 (F=NORTH, 逆时针从左下), UV 顺 (0,1)→(1,1)→(1,0)→(0,0)。
        float[][] corners = {
                {cx - half, cy - half, depth},
                {cx + half, cy - half, depth},
                {cx + half, cy + half, depth},
                {cx - half, cy + half, depth},
        };
        float u0 = sprite.getU(0), u1 = sprite.getU(16);
        float v0 = sprite.getV(0), v1 = sprite.getV(16);
        float[] us = {u0, u1, u1, u0};
        float[] vs = {v1, v1, v0, v0};
        // 法线: 模板 -Z 经旋转。
        Vector3f normalVec = new Vector3f(0, 0, -1);
        rotation.transform(normalVec);
        for (int i = 0; i < 4; i++) {
            Vector3f v = new Vector3f(corners[i]);
            rotation.transform(v);
            // 模板原点 = 控制器格中心 → 平移到 blockentity corner 系。
            float wx = v.x() + 0.5f;
            float wy = v.y() + 0.5f;
            float wz = v.z() + 0.5f;
            consumer.vertex(pose, wx, wy, wz)
                    .color(1.0f, 1.0f, 1.0f, 1.0f)
                    .uv(us[i], vs[i])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(normal, normalVec.x(), normalVec.y(), normalVec.z())
                    .endVertex();
        }
    }
}
