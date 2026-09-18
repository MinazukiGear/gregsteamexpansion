package com.hoshino.gregsteamexpansion.client;

import com.hoshino.gregsteamexpansion.terminal.UltimateStructurePlanner;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** Renders the selected server-authored blueprint while the terminal is held. */
public final class UltimateTerminalHologramRenderer {
    private static final double MAX_DISTANCE_SQUARED = 128.0D * 128.0D;

    private UltimateTerminalHologramRenderer() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || !UltimateTerminalClientState.isHoldingTerminal()) return;
        Minecraft minecraft = Minecraft.getInstance();
        var snapshot = UltimateTerminalClientState.snapshot();
        if (snapshot == null || minecraft.level == null
                || !snapshot.dimension().equals(minecraft.level.dimension().location())) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer translucent = new AlphaVertexConsumer(buffers.getBuffer(RenderType.translucent()));
        boolean drewGhost = false;
        for (var cell : snapshot.cells()) {
            if (!visible(cell) || cell.pos().distToCenterSqr(camera.x, camera.y, camera.z) > MAX_DISTANCE_SQUARED) continue;
            if (!(cell.expected().getItem() instanceof BlockItem blockItem)) continue;
            if (cell.status() == UltimateStructurePlanner.CellStatus.REMOVE
                    || cell.status() == UltimateStructurePlanner.CellStatus.UNLOADED) continue;
            float[] color = color(cell.status());
            float alpha = cell.status() == UltimateStructurePlanner.CellStatus.SATISFIED ? 0.18F : 0.35F;
            poses.pushPose();
            poses.translate(cell.pos().getX() - camera.x + 0.01D,
                    cell.pos().getY() - camera.y + 0.01D,
                    cell.pos().getZ() - camera.z + 0.01D);
            poses.scale(0.98F, 0.98F, 0.98F);
            var state = blockItem.getBlock().defaultBlockState();
            var model = minecraft.getBlockRenderer().getBlockModel(state);
            ((AlphaVertexConsumer) translucent).setAlpha(alpha);
            minecraft.getBlockRenderer().getModelRenderer().renderModel(
                    poses.last(), translucent, state, model, color[0], color[1], color[2],
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            poses.popPose();
            drewGhost = true;
        }
        if (drewGhost) buffers.endBatch(RenderType.translucent());
        drawOutlines(poses, camera, snapshot);
    }

    private static boolean visible(com.hoshino.gregsteamexpansion.terminal.UltimateTerminalSnapshot.CellInfo cell) {
        var mode = UltimateTerminalClientState.displayMode();
        if (mode == UltimateTerminalClientState.DisplayMode.LAYER
                && cell.pos().getY() != UltimateTerminalClientState.layer()) return false;
        return mode != UltimateTerminalClientState.DisplayMode.DIFFERENCES
                || cell.status() != UltimateStructurePlanner.CellStatus.SATISFIED;
    }

    private static void drawOutlines(PoseStack poses, Vec3 camera,
                                     com.hoshino.gregsteamexpansion.terminal.UltimateTerminalSnapshot snapshot) {
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.lineWidth(2.5F);
        try {
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
            for (var cell : snapshot.cells()) {
                if (!visible(cell) || cell.pos().distToCenterSqr(camera.x, camera.y, camera.z) > MAX_DISTANCE_SQUARED) continue;
                if (cell.status() == UltimateStructurePlanner.CellStatus.SATISFIED) continue;
                float[] color = color(cell.status());
                AABB box = new AABB(cell.pos()).inflate(0.003D).move(-camera.x, -camera.y, -camera.z);
                LevelRenderer.renderLineBox(poses, buffer, box, color[0], color[1], color[2], 0.95F);
            }
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            RenderSystem.lineWidth(1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    private static float[] color(UltimateStructurePlanner.CellStatus status) {
        return switch (status) {
            case SATISFIED -> new float[]{0.33F, 1.0F, 0.33F};
            case MISSING -> new float[]{1.0F, 1.0F, 1.0F};
            case CONFLICT -> new float[]{1.0F, 0.33F, 0.33F};
            case UPGRADE -> new float[]{1.0F, 0.85F, 0.2F};
            case REMOVE -> new float[]{0.25F, 0.55F, 1.0F};
            case UNLOADED -> new float[]{0.67F, 0.67F, 0.67F};
        };
    }

    private static final class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private float alpha = 0.35F;

        AlphaVertexConsumer(VertexConsumer delegate) { this.delegate = delegate; }
        void setAlpha(float alpha) { this.alpha = alpha; }

        @Override public VertexConsumer vertex(double x, double y, double z) { delegate.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) {
            delegate.color(r, g, b, Math.round(a * alpha)); return this;
        }
        @Override public VertexConsumer uv(float u, float v) { delegate.uv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { delegate.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { delegate.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
        @Override public void endVertex() { delegate.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) {
            delegate.defaultColor(r, g, b, Math.round(a * alpha));
        }
        @Override public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
    }
}
