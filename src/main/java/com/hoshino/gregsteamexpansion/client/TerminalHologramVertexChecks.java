package com.hoshino.gregsteamexpansion.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

/** Opt-in check against the old forwarding path, comparing actual GPU vertex-buffer bytes. */
final class TerminalHologramVertexChecks {
    private TerminalHologramVertexChecks() {}

    static int verify(List<com.hoshino.gregsteamexpansion.terminal.UltimateTerminalSnapshot> snapshots) {
        List<BlockState> states = new ArrayList<>(List.of(Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.GLASS.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState()));
        for (var snapshot : snapshots) for (var cell : snapshot.cells()) {
            if (cell.expected().getItem() instanceof BlockItem item && !states.contains(item.getBlock().defaultBlockState())) {
                states.add(item.getBlock().defaultBlockState());
            }
        }
        int checks = 0;
        for (var state : states) for (float alpha : new float[]{0.18F, 0.35F}) {
            BufferBuilder oldBuffer = new BufferBuilder(4096), newBuffer = new BufferBuilder(4096);
            oldBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            newBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            VertexConsumer old = new LegacyAlpha(oldBuffer, alpha);
            var optimized = new UltimateTerminalHologramRenderer.AlphaVertexConsumer(newBuffer);
            optimized.setAlpha(alpha);
            PoseStack poses = new PoseStack();
            poses.translate(2.75, -1.25, 12.5);
            poses.mulPose(new org.joml.Quaternionf().rotateXYZ(.13F, .67F, -.31F));
            poses.scale(.98F, .98F, .98F);
            var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            for (int side = 0; side <= 6; side++) {
                Direction direction = side == 6 ? null : Direction.values()[side];
                for (var quad : model.getQuads(state, direction, RandomSource.create(42), ModelData.EMPTY, null)) {
                    float red = quad.isTinted() ? .33F : 1F;
                    float green = quad.isTinted() ? .85F : 1F;
                    float blue = quad.isTinted() ? .2F : 1F;
                    old.putBulkData(poses.last(), quad, red, green, blue, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    optimized.putBulkData(poses.last(), quad, red, green, blue, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    checks++;
                }
            }
            var oldRendered = oldBuffer.end();
            var newRendered = newBuffer.end();
            try {
                var oldBytes = oldRendered.vertexBuffer();
                var newBytes = newRendered.vertexBuffer();
                // BLOCK's final byte is uninitialized alignment padding, unused by the GPU.
                for (int offset = 31; offset < oldBytes.remaining(); offset += 32) oldBytes.put(offset, (byte) 0);
                for (int offset = 31; offset < newBytes.remaining(); offset += 32) newBytes.put(offset, (byte) 0);
                if (oldBytes.mismatch(newBytes) != -1) {
                    throw new IllegalStateException("Hologram vertex bytes differ: " + state + " alpha=" + alpha);
                }
            } finally {
                oldRendered.release(); newRendered.release();
            }
        }
        if (checks == 0) throw new IllegalStateException("No vertex parity checks ran");
        return checks;
    }

    private record LegacyAlpha(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override public VertexConsumer vertex(double x, double y, double z) { delegate.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { delegate.color(r, g, b, Math.round(a * alpha)); return this; }
        @Override public VertexConsumer uv(float u, float v) { delegate.uv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { delegate.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { delegate.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
        @Override public void endVertex() { delegate.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { delegate.defaultColor(r, g, b, Math.round(a * alpha)); }
        @Override public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
    }
}
