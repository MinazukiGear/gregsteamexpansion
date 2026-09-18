package com.hoshino.gregsteamexpansion.client;

import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderManager;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.gregtechceu.gtceu.client.util.RenderBufferHelper;
import com.gregtechceu.gtceu.client.util.RenderUtil;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.LargeSteamTankMachine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.util.EnumSet;

/** Renders one translucent steam cuboid inside the formed variable-size tank. */
public final class LargeSteamTankRenderer
        extends DynamicRender<LargeSteamTankMachine, LargeSteamTankRenderer> {

    public static final Codec<LargeSteamTankRenderer> CODEC = Codec.unit(new LargeSteamTankRenderer());
    public static final DynamicRenderType<LargeSteamTankMachine, LargeSteamTankRenderer> TYPE =
            new DynamicRenderType<>(CODEC);

    static {
        DynamicRenderManager.register(GregSteamExpansion.id("large_steam_tank"), TYPE);
    }

    public static void bootstrap() {}

    @Override
    public DynamicRenderType<LargeSteamTankMachine, LargeSteamTankRenderer> getType() {
        return TYPE;
    }

    @Override
    public AABB getRenderBoundingBox(LargeSteamTankMachine machine) {
        BlockPos pos = machine.getPos();
        int width = Math.max(1, machine.getFormedWidth());
        int height = Math.max(1, machine.getFormedHeight());
        return new AABB(pos.offset(-width, 0, -width), pos.offset(width + 1, height + 1, width + 1));
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void render(LargeSteamTankMachine machine, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!machine.isFormed()) return;
        int width = machine.getFormedWidth();
        int height = machine.getFormedHeight();
        int amount = machine.getRenderedAmount();
        int capacity = machine.getFormedCapacity();
        if (width < 3 || height < 4 || amount <= 0 || capacity <= 0) return;

        FluidStack steam = GTMaterials.Steam.getFluid(Math.max(1, amount));
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(steam.getFluid());
        var sprite = RenderUtil.FluidTextureType.STILL.map(extensions, steam);
        VertexConsumer consumer = bufferSource.getBuffer(Sheets.translucentCullBlockSheet());

        float fill = Math.min(1.0F, (float) amount / capacity);
        float halfInner = (width - 2) / 2.0F;
        float top = height - 1.5F;
        float bottom = top - (height - 2) * fill;
        float front = 0.5F;
        float back = width - 1.5F;
        float inset = 0.01F;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(machine.getFrontFacing().getRotation());
        RenderBufferHelper.renderCube(
                consumer,
                poseStack.last(),
                EnumSet.allOf(Direction.class),
                extensions.getTintColor(steam) | 0xFF000000,
                LightTexture.FULL_BRIGHT,
                sprite,
                -halfInner + inset,
                bottom + inset,
                front + inset,
                halfInner - inset,
                top - inset,
                back - inset);
        poseStack.popPose();
    }
}
