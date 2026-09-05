package com.hoshino.gregsteamexpansion.client.cokeoven;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 焦炉仓正面模式方框 (coke-ovens.md 运行表现): 三个形状、尺寸完全相同的方框,
 * 仅以颜色区分 — 绿色物品输入、橙色物品输出、蓝色流体输出。方框叠加在上游
 * 基础模型的正面, 随仓自身持久化模式实时刷新; 结构成型后仍保持可见。
 */
@Mod.EventBusSubscriber(modid = GregSteamExpansion.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CokeOvenHatchModeModel {

    public static final ModelProperty<CokeOvenMode> MODE_PROPERTY =
            new ModelProperty<>(java.util.Objects::nonNull);

    private static final Map<CokeOvenMode, ResourceLocation> SPRITES = new EnumMap<>(CokeOvenMode.class);

    static {
        for (CokeOvenMode mode : CokeOvenMode.values()) {
            SPRITES.put(mode, GregSteamExpansion.id("block/coke_oven_hatch/mode_" + mode.getSerializedName()));
        }
    }

    private CokeOvenHatchModeModel() {}

    /** 用模式感知的包装模型替换焦炉仓方块的全部烘焙结果。 */
    @SubscribeEvent
    public static void onModelBake(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        for (var entry : new ArrayList<>(models.entrySet())) {
            var key = entry.getKey();
            if (key.getNamespace().equals("gtceu") && key.getPath().equals("coke_oven_hatch")) {
                BakedModel base = entry.getValue();
                if (!(base instanceof Wrapped)) {
                    models.put(key, new Wrapped(base));
                }
            }
        }
    }

    /** 在烘焙完成的方块模型上叠加正面模式方框。 */
    public static final class Wrapped implements BakedModel {

        // 方框略小于整格面, 与上游 overlay hatch 贴图的边界视觉一致。
        private static final AABB OVERLAY_BOX = new AABB(0.001, 0.001, 0.001, 0.999, 0.999, 0.999);

        private final BakedModel base;

        private Wrapped(BakedModel base) {
            this.base = base;
        }

        @NotNull
        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                        RandomSource rand, ModelData data, @Nullable RenderType renderType) {
            List<BakedQuad> quads = base.getQuads(state, side, rand, data, renderType);
            CokeOvenMode mode = data.get(MODE_PROPERTY);
            if (mode == null || state == null || side == null) return quads;
            // 方框只画在仓的当前正面 (方块的 facing 状态, 与上游基础模型正面对应)。
            Direction front;
            try {
                front = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
            } catch (IllegalArgumentException e) {
                return quads;
            }
            if (side != front) return quads;
            var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                    .apply(SPRITES.get(mode));
            BakedQuad overlay = com.gregtechceu.gtceu.client.util.StaticFaceBakery.bakeFace(OVERLAY_BOX, side, sprite);
            List<BakedQuad> result = new ArrayList<>(quads.size() + 1);
            result.addAll(quads);
            result.add(overlay);
            return result;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            // 无 model data 的路径 (物品栏、掉落物): 不叠加方框。
            return base.getQuads(state, side, rand);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return base.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return base.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return base.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return base.isCustomRenderer();
        }

        @Override
        public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon() {
            return base.getParticleIcon();
        }

        @Override
        public ItemTransforms getTransforms() {
            return base.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return base.getOverrides();
        }

        @NotNull
        @Override
        public ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
                                      @NotNull BlockState state, @NotNull ModelData modelData) {
            return base.getModelData(level, pos, state, modelData);
        }
    }
}
