package com.hoshino.gregsteamexpansion.machine.multiblock.voidproducer;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.sound.SoundEntry;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.difficulty.Difficulty;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyConfig;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import com.hoshino.gregsteamexpansion.registry.GSEVoidPatterns;

import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 大型蒸汽流体钻井 / Large Steam Fluid Drill controller
 * (large-steam-fluid-drill.md, F2): void fluid producer — 2 pump stations
 * share a 200-tick cycle at 3,000 mB/t each (6,000 mB/t full speed, 5
 * supply hatches); at every cycle end each pump independently draws from
 * the weighted pool and yields 2,000 mB of the drawn overworld fluid
 * (light oil / oil / heavy oil / natural gas by default, weights mirroring
 * the upstream bedrock deposits). Output is fluids only via fluid output
 * hatches (GTCEu standard or the mod's steam fluid output hatch, freely
 * mixed). Pool is config-overridable ({@code machines.large_steam_fluid_drill.*})
 * and the config toggle can disable running entirely.
 */
@ParametersAreNonnullByDefault
public class LargeSteamFluidDrillMachine extends AbstractSteamVoidMachine {

    /** 每抽流体量 mB (定案 2,000 mB; 难度倍率放大). */
    public static final int FLUID_PER_DRAW_MB = 2000;

    /**
     * 内置默认概率表 (权重和 75, 沿用上游主世界沉积层权重; 天然气保留为定案).
     */
    private static final List<PoolFluid> DEFAULT_POOL = List.of(
            new PoolFluid(GTMaterials.OilLight, 25),
            new PoolFluid(GTMaterials.Oil, 20),
            new PoolFluid(GTMaterials.OilHeavy, 15),
            new PoolFluid(GTMaterials.NaturalGas, 15));

    private record PoolFluid(Material material, int weight) {}

    /** Lazily resolved pool; rebuilt when the configured entry list changes. */
    @Nullable
    private static volatile List<WeightedEntry> cachedPool;
    @Nullable
    private static volatile String cachedPoolSource = "";

    public LargeSteamFluidDrillMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    //////////////////////////////////////
    // ***** Family hooks ******//
    //////////////////////////////////////

    @Override
    public int stationCount() {
        return 2;
    }

    @Override
    public int cycleTicks() {
        return 200;
    }

    @Override
    public long steamPerStationTick() {
        return 3000;
    }

    @Override
    protected boolean configEnabled() {
        return GSEDifficultyConfig.fluidDrillEnabled();
    }

    /** 议题 6: Easy 4× / Normal 2× / Expert 1× (只放大产出量). */
    @Override
    public int outputMultiplier() {
        Difficulty difficulty = GSEDifficultyState.current(isRemote());
        return switch (difficulty) {
            case EASY -> 4;
            case NORMAL -> 2;
            case EXPERT -> 1;
        };
    }

    @Override
    protected int maximumInterfaces() {
        // 议题 4: 仓室合计 ≤ 16 (5 供给仓 + 1 排气仓 + 流体输出仓).
        return 16;
    }

    @Override
    protected boolean requiresFluidOutput() {
        return true;
    }

    @Override
    protected SoundEntry workingSoundEntry() {
        // 议题 9: 上游矿机声 (钻井语义).
        return GTSoundEntries.MINER;
    }

    @Override
    public BlockPattern getPattern() {
        return GSEVoidPatterns.createFluidDrill(getDefinition());
    }

    //////////////////////////////////////
    // ***** Pool ******//
    //////////////////////////////////////

    /**
     * 当前生效池: 配置权重表非空时按条目解析 (流体注册名), 空表或全部非法时回退
     * 内置默认表. 解析结果按配置内容指纹缓存.
     */
    private List<WeightedEntry> pool() {
        List<? extends String> configured = GSEDifficultyConfig.fluidDrillWeightEntries();
        String source = String.join(";", configured);
        List<WeightedEntry> pool = cachedPool;
        if (pool != null && source.equals(cachedPoolSource)) {
            return pool;
        }
        List<WeightedEntry> resolved = new ArrayList<>();
        if (!configured.isEmpty()) {
            for (String entry : configured) {
                WeightedFluid parsed = parseWeightedFluid(entry);
                if (parsed != null) {
                    resolved.add(new WeightedEntry(null,
                            new FluidStack(parsed.fluid(), FLUID_PER_DRAW_MB), parsed.weight()));
                }
            }
            if (resolved.isEmpty()) {
                GregSteamExpansion.LOGGER.warn(
                        "[Fluid Drill] Configured weight table has no valid entries; falling back to the built-in table.");
            }
        }
        if (resolved.isEmpty()) {
            for (PoolFluid poolFluid : DEFAULT_POOL) {
                Fluid fluid = poolFluid.material().getFluid();
                if (fluid != null) {
                    resolved.add(new WeightedEntry(null,
                            new FluidStack(fluid, FLUID_PER_DRAW_MB), poolFluid.weight()));
                } else {
                    GregSteamExpansion.LOGGER.warn(
                            "[Fluid Drill] Material {} has no fluid form; skipped in the default pool.",
                            poolFluid.material().getName());
                }
            }
        }
        cachedPool = List.copyOf(resolved);
        cachedPoolSource = source;
        return cachedPool;
    }

    private record WeightedFluid(Fluid fluid, int weight) {}

    /** `<流体注册名>|<权重>`; 非法条目记录警告并跳过. */
    @Nullable
    private static WeightedFluid parseWeightedFluid(String entry) {
        String[] parts = entry.split("\\|");
        if (parts.length != 2) {
            GregSteamExpansion.LOGGER.warn("[Fluid Drill] Invalid configured pool entry '{}'; entry skipped.", entry);
            return null;
        }
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(parts[0].trim()));
        if (fluid == null || fluid.defaultFluidState().isEmpty()) {
            GregSteamExpansion.LOGGER.warn("[Fluid Drill] Invalid configured pool entry '{}'; entry skipped.", entry);
            return null;
        }
        int weight;
        try {
            weight = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException exception) {
            GregSteamExpansion.LOGGER.warn("[Fluid Drill] Invalid configured pool entry '{}'; entry skipped.", entry);
            return null;
        }
        if (weight <= 0) {
            GregSteamExpansion.LOGGER.warn("[Fluid Drill] Invalid configured pool entry '{}'; entry skipped.", entry);
            return null;
        }
        return new WeightedFluid(fluid, weight);
    }

    //////////////////////////////////////
    // ***** Production ******//
    //////////////////////////////////////

    @Override
    protected void produceOutputs() {
        List<WeightedEntry> pool = pool();
        if (pool.isEmpty()) {
            return;
        }
        int multiplier = outputMultiplier();
        for (int pump = 0; pump < stationCount(); pump++) {
            WeightedEntry entry = weightedPick(pool);
            if (entry == null) {
                return;
            }
            FluidStack stack = entry.fluid().copy();
            stack.setAmount(FLUID_PER_DRAW_MB * multiplier);
            addPendingFluid(stack);
        }
    }

    //////////////////////////////////////
    // ***** UI ******//
    //////////////////////////////////////

    private static final String UI_PREFIX = "gregsteamexpansion.machine.void_producer.ui.";

    @Override
    protected int addMachineInfoRows(DraggableScrollableWidgetGroup scroll, int y) {
        y = infoRow(scroll, y, UI_PREFIX + "stations", this::stationSummary, ChatFormatting.WHITE);
        int totalWeight = 0;
        for (WeightedEntry entry : pool()) {
            totalWeight += entry.weight();
        }
        String summary = Component.translatable(UI_PREFIX + "pool_summary",
                pool().size(), totalWeight).getString();
        return probabilityRow(scroll, y, () -> summary, this::probabilityTooltips);
    }

    /** `2 泵位 × 200t · 产出倍率 ×4`. */
    private String stationSummary() {
        return stationCount() + " × " + cycleTicks() + "t · ×" + outputMultiplier();
    }

    /** Hover: 当前生效权重表逐条 (流体 ×mB · 百分比). */
    private List<Component> probabilityTooltips() {
        List<Component> tooltips = new ArrayList<>();
        tooltips.add(Component.translatable(UI_PREFIX + "pool_detail").withStyle(ChatFormatting.GRAY));
        List<WeightedEntry> pool = pool();
        int totalWeight = 0;
        for (WeightedEntry entry : pool) {
            totalWeight += entry.weight();
        }
        for (WeightedEntry entry : pool) {
            FluidStack fluid = entry.fluid();
            if (fluid == null) {
                continue;
            }
            double percent = totalWeight > 0 ? entry.weight() * 100.0 / totalWeight : 0;
            tooltips.add(Component.literal(
                    String.format(java.util.Locale.ROOT, "- %s ×%d mB · %.1f%% (%d/%d)",
                            fluid.getDisplayName().getString(), fluid.getAmount(),
                            percent, entry.weight(), totalWeight))
                    .withStyle(ChatFormatting.WHITE));
        }
        return tooltips;
    }

    //////////////////////////////////////
    // ***** Particles ******//
    //////////////////////////////////////

    @Override
    public void spawnWorkingParticles() {
        if (getLevel() == null || getFrontFacing() == null) {
            return;
        }
        RandomSource random = getLevel().random;
        var front = getFrontFacing();
        double x = getPos().getX() + 0.5 + front.getStepX() * 0.6;
        double y = getPos().getY() + 0.5;
        double z = getPos().getZ() + 0.5 + front.getStepZ() * 0.6;
        getLevel().addParticle(ParticleTypes.DRIPPING_WATER,
                x + (random.nextDouble() - 0.5) * 0.4,
                y + random.nextDouble() * 0.4,
                z + (random.nextDouble() - 0.5) * 0.4,
                0.0, 0.0, 0.0);
    }
}
