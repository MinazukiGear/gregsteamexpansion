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
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.item.BlockItem;
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
        if (snapshot == null || snapshot.cells().isEmpty() || minecraft.level == null
                || !snapshot.dimension().equals(minecraft.level.dimension().location())) return;
        if (TerminalClientMetrics.ENABLED) TerminalClientMetrics.begin();

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer translucent = new AlphaVertexConsumer(buffers.getBuffer(RenderType.translucent()));
        boolean drewGhost = false;
        int ghosts = 0;
        for (var cell : snapshot.cells()) {
            if (!visible(cell) || cell.pos().distToCenterSqr(camera.x, camera.y, camera.z) > MAX_DISTANCE_SQUARED) continue;
            if (!(cell.expected().getItem() instanceof BlockItem blockItem)) continue;
            if (cell.status() == UltimateStructurePlanner.CellStatus.REMOVE
                    || cell.status() == UltimateStructurePlanner.CellStatus.UNLOADED) continue;
            Tint color = color(cell.status());
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
                    poses.last(), translucent, state, model, color.red, color.green, color.blue,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            poses.popPose();
            drewGhost = true;
            ghosts++;
        }
        if (drewGhost) buffers.endBatch(RenderType.translucent());
        if (TerminalClientMetrics.ENABLED) TerminalClientMetrics.ghostEnd(ghosts);
        int outlines = drawOutlines(poses, camera, snapshot);
        if (TerminalClientMetrics.ENABLED) TerminalClientMetrics.end(outlines);
    }

    private static boolean visible(com.hoshino.gregsteamexpansion.terminal.UltimateTerminalSnapshot.CellInfo cell) {
        var mode = UltimateTerminalClientState.displayMode();
        if (mode == UltimateTerminalClientState.DisplayMode.LAYER
                && cell.pos().getY() != UltimateTerminalClientState.layer()) return false;
        return mode != UltimateTerminalClientState.DisplayMode.DIFFERENCES
                || cell.status() != UltimateStructurePlanner.CellStatus.SATISFIED;
    }

    private static int drawOutlines(PoseStack poses, Vec3 camera,
                                     com.hoshino.gregsteamexpansion.terminal.UltimateTerminalSnapshot snapshot) {
        int count = 0;
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
                Tint color = color(cell.status());
                double x = cell.pos().getX() - camera.x;
                double y = cell.pos().getY() - camera.y;
                double z = cell.pos().getZ() - camera.z;
                LevelRenderer.renderLineBox(poses, buffer, x - 0.003D, y - 0.003D, z - 0.003D,
                        x + 1.003D, y + 1.003D, z + 1.003D, color.red, color.green, color.blue, 0.95F);
                count++;
            }
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            RenderSystem.lineWidth(1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
        return count;
    }

    private static Tint color(UltimateStructurePlanner.CellStatus status) {
        return switch (status) {
            case SATISFIED -> Tint.GREEN;
            case MISSING -> Tint.WHITE;
            case CONFLICT -> Tint.RED;
            case UPGRADE -> Tint.YELLOW;
            case REMOVE -> Tint.BLUE;
            case UNLOADED -> Tint.GRAY;
        };
    }

    private record Tint(float red, float green, float blue) {
        private static final Tint GREEN = new Tint(0.33F, 1.0F, 0.33F);
        private static final Tint WHITE = new Tint(1.0F, 1.0F, 1.0F);
        private static final Tint RED = new Tint(1.0F, 0.33F, 0.33F);
        private static final Tint YELLOW = new Tint(1.0F, 0.85F, 0.2F);
        private static final Tint BLUE = new Tint(0.25F, 0.55F, 1.0F);
        private static final Tint GRAY = new Tint(0.67F, 0.67F, 0.67F);
    }

    static final class AlphaVertexConsumer implements VertexConsumer {
        private static final float[] BRIGHTNESS = {1, 1, 1, 1};
        private static final int[] FULL_LIGHT = {LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT,
                LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT};
        private final VertexConsumer delegate;
        private float alpha = 0.35F;

        AlphaVertexConsumer(VertexConsumer delegate) { this.delegate = delegate; }
        void setAlpha(float alpha) { this.alpha = alpha; }

        @Override public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float red, float green, float blue,
                                          int light, int overlay) {
            int[] lights = light == LightTexture.FULL_BRIGHT ? FULL_LIGHT : new int[]{light, light, light, light};
            // Match the old wrapper's round(255 * alpha), including 0.18 -> 46 rather than 45.
            // nextUp prevents a float division/multiplication round trip from truncating one alpha byte.
            float encodedAlpha = Math.nextUp(Math.round(255F * alpha) / 255F);
            // Let the underlying consumer use its bulk/complete-vertex path. Forge still applies
            // baked light and normals; models and quads are queried normally on every draw.
            delegate.putBulkData(pose, quad, BRIGHTNESS, red, green, blue, encodedAlpha, lights, overlay, false);
        }

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
