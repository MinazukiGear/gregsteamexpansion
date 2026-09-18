package com.hoshino.gregsteamexpansion.client;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the first server-reported multiblock structure error as a red block
 * outline. The marker remains after the player looks away and is removed only
 * after the associated controller is confirmed formed (or the client session
 * ends).
 */
public final class StructureErrorHighlight {

    private static final double BOX_INFLATION = 0.004D;

    private static @Nullable BlockPos target;
    private static @Nullable BlockPos controller;
    private static @Nullable ResourceKey<Level> dimension;

    private StructureErrorHighlight() {}

    public static void show(BlockPos controllerPos, BlockPos targetPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        controller = controllerPos.immutable();
        target = targetPos.immutable();
        dimension = minecraft.level.dimension();
    }

    /** Clears a marker only when it belongs to the controller Jade confirmed formed. */
    public static void clearForController(BlockPos controllerPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && dimension != null &&
                dimension.equals(minecraft.level.dimension()) && controllerPos.equals(controller)) {
            clear();
        }
    }

    public static void clear() {
        target = null;
        controller = null;
        dimension = null;
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos pos = target;
        BlockPos controllerPos = controller;
        if (minecraft.level == null || pos == null || controllerPos == null || dimension == null ||
                !dimension.equals(minecraft.level.dimension())) {
            return;
        }

        // GTCEu marks MultiblockControllerMachine#isFormed as @DescSynced, so
        // this becomes true client-side without requiring another Jade lookup.
        if (minecraft.level.hasChunkAt(controllerPos) &&
                minecraft.level.getBlockEntity(controllerPos) instanceof MetaMachineBlockEntity blockEntity &&
                blockEntity.getMetaMachine() instanceof IMultiController multiblock && multiblock.isFormed()) {
            clear();
            return;
        }
        if (!minecraft.level.hasChunkAt(pos)) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        AABB box = new AABB(pos).inflate(BOX_INFLATION)
                .move(-camera.x, -camera.y, -camera.z);
        drawVisibleOutline(event.getPoseStack(), box);
    }

    private static void drawVisibleOutline(PoseStack poseStack, AABB box) {
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.lineWidth(3.0F);
        try {
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
            LevelRenderer.renderLineBox(poseStack, buffer, box,
                    1.0F, 0.08F, 0.08F, 1.0F);
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            RenderSystem.lineWidth(1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }
}
