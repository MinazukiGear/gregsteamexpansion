package com.hoshino.gregsteamexpansion.machine.multiblock.voidproducer;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 大型蒸汽采矿厂 / Large Steam Ore Plant controller
 * (large-steam-ore-plant.md, F1): void ore producer — 4 production stations
 * share a 200-tick cycle at 3,000 mB/t each (12,000 mB/t full speed, 10
 * supply hatches); at every cycle end each station independently draws from
 * the weighted pool and yields its material's RAW ORE ×8, feeding the
 * washer → macerator → centrifuge chain unchanged. Output is items only via
 * item output buses; the pool is config-overridable
 * ({@code machines.large_steam_ore_plant.*}) with the built-in default table
 * as fallback, and the config toggle can disable running entirely.
 */
@ParametersAreNonnullByDefault
public class LargeSteamOrePlantMachine extends AbstractSteamVoidMachine {

    /** 每抽粗矿数量 (定案: 全部条目 ×8). */
    public static final int RAW_ORE_PER_DRAW = 8;

    /**
     * 内置默认概率表 (权重和 114): 金属与非金属统一产出粗矿; 无粗矿形态的材料
     * (个别宝石) 运行时回退为 gem / dust 形态, 数量不变.
     */
    private static final List<PoolMaterial> DEFAULT_POOL = List.of(
            new PoolMaterial(GTMaterials.Coal, 20),
            new PoolMaterial(GTMaterials.Iron, 15),
            new PoolMaterial(GTMaterials.Copper, 15),
            new PoolMaterial(GTMaterials.Tin, 12),
            new PoolMaterial(GTMaterials.Lead, 10),
            new PoolMaterial(GTMaterials.Zinc, 10),
            new PoolMaterial(GTMaterials.Nickel, 8),
            new PoolMaterial(GTMaterials.Gold, 6),
            new PoolMaterial(GTMaterials.Redstone, 6),
            new PoolMaterial(GTMaterials.Bauxite, 4),
            new PoolMaterial(GTMaterials.Lapis, 4),
            new PoolMaterial(GTMaterials.Diamond, 2),
            new PoolMaterial(GTMaterials.Emerald, 2));

    private record PoolMaterial(Material material, int weight) {}

    /** Lazily resolved pool; rebuilt when the configured entry list changes. */
    @Nullable
    private static volatile List<WeightedEntry> cachedPool;
    @Nullable
    private static volatile String cachedPoolSource = "";

    public LargeSteamOrePlantMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    //////////////////////////////////////
    // ***** Family hooks ******//
    //////////////////////////////////////

    @Override
    public int stationCount() {
        return 4;
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
        return GSEDifficultyConfig.orePlantEnabled();
    }

    /** 议题 6: Easy 4× / Normal 2× / Expert 1× (只放大产出数量). */
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
    protected boolean requiresItemOutput() {
        return true;
    }

    @Override
    protected SoundEntry workingSoundEntry() {
        // 议题 9: 研磨机声 (破碎选矿语义).
        return GTSoundEntries.MACERATOR;
    }

    @Override
    public BlockPattern getPattern() {
        return GSEVoidPatterns.createOrePlant(getDefinition());
    }

    //////////////////////////////////////
    // ***** Pool ******//
    //////////////////////////////////////

    /**
     * 当前生效池: 配置权重表非空时按条目解析 (物品注册名), 空表或全部非法时回退
     * 内置默认表 (材料 → rawOre → gem → dust 回退链). 解析结果按配置内容指纹缓存.
     */
    private List<WeightedEntry> pool() {
        List<? extends String> configured = GSEDifficultyConfig.orePlantWeightEntries();
        String source = String.join(";", configured);
        List<WeightedEntry> pool = cachedPool;
        if (pool != null && source.equals(cachedPoolSource)) {
            return pool;
        }
        List<WeightedEntry> resolved = new ArrayList<>();
        if (!configured.isEmpty()) {
            for (String entry : configured) {
                WeightedItem parsed = parseWeightedItem(entry);
                if (parsed != null) {
                    resolved.add(new WeightedEntry(parsed.stack(), null, parsed.weight()));
                }
            }
            if (resolved.isEmpty()) {
                GregSteamExpansion.LOGGER.warn(
                        "[Ore Plant] Configured weight table has no valid entries; falling back to the built-in table.");
            }
        }
        if (resolved.isEmpty()) {
            for (PoolMaterial poolMaterial : DEFAULT_POOL) {
                ItemStack stack = resolveOreForm(poolMaterial.material());
                if (!stack.isEmpty()) {
                    resolved.add(new WeightedEntry(stack.copyWithCount(RAW_ORE_PER_DRAW), null, poolMaterial.weight()));
                } else {
                    GregSteamExpansion.LOGGER.warn(
                            "[Ore Plant] Material {} has no raw/gem/dust item form; skipped in the default pool.",
                            poolMaterial.material().getName());
                }
            }
        }
        cachedPool = List.copyOf(resolved);
        cachedPoolSource = source;
        return cachedPool;
    }

    private record WeightedItem(ItemStack stack, int weight) {}

    /** 粗矿形态回退链: rawOre → gem → dust (非金属宝石材料的兜底, 议题 3). */
    private static ItemStack resolveOreForm(Material material) {
        ItemStack raw = ChemicalHelper.get(TagPrefix.rawOre, material);
        if (!raw.isEmpty()) {
            return raw;
        }
        ItemStack gem = ChemicalHelper.get(TagPrefix.gem, material);
        if (!gem.isEmpty()) {
            return gem;
        }
        return ChemicalHelper.get(TagPrefix.dust, material);
    }

    /** `<物品注册名>|<权重>|[数量]`; 非法条目记录警告并跳过. */
    @Nullable
    private static WeightedItem parseWeightedItem(String entry) {
        String[] parts = entry.split("\\|");
        if (parts.length < 2 || parts.length > 3) {
            warnInvalid(entry);
            return null;
        }
        var item = ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(parts[0].trim()));
        if (item == null || item.getDefaultInstance().isEmpty()) {
            warnInvalid(entry);
            return null;
        }
        int weight;
        try {
            weight = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException exception) {
            warnInvalid(entry);
            return null;
        }
        if (weight <= 0) {
            warnInvalid(entry);
            return null;
        }
        int count = 1;
        if (parts.length == 3) {
            try {
                count = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException exception) {
                warnInvalid(entry);
                return null;
            }
            if (count <= 0) {
                warnInvalid(entry);
                return null;
            }
        }
        return new WeightedItem(new ItemStack(item, count), weight);
    }

    private static void warnInvalid(String entry) {
        GregSteamExpansion.LOGGER.warn("[Ore Plant] Invalid configured pool entry '{}'; entry skipped.", entry);
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
        for (int station = 0; station < stationCount(); station++) {
            WeightedEntry entry = weightedPick(pool);
            if (entry == null) {
                return;
            }
            int count = entry.stack().getCount() * multiplier;
            ItemStack source = entry.stack().copyWithCount(1);
            // 数量超过堆叠上限时拆分为多组 (家族口径).
            while (count > 0) {
                int chunk = Math.min(source.getMaxStackSize(), count);
                addPendingItem(source.copyWithCount(chunk));
                count -= chunk;
            }
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

    /** `4 工位 × 200t · 产出倍率 ×4`. */
    private String stationSummary() {
        return stationCount() + " × " + cycleTicks() + "t · ×" + outputMultiplier();
    }

    /** Hover: 当前生效权重表逐条 (物品 ×数量 · 百分比). */
    private List<Component> probabilityTooltips() {
        List<Component> tooltips = new ArrayList<>();
        tooltips.add(Component.translatable(UI_PREFIX + "pool_detail").withStyle(ChatFormatting.GRAY));
        List<WeightedEntry> pool = pool();
        int totalWeight = 0;
        for (WeightedEntry entry : pool) {
            totalWeight += entry.weight();
        }
        for (WeightedEntry entry : pool) {
            ItemStack stack = entry.stack();
            double percent = totalWeight > 0 ? entry.weight() * 100.0 / totalWeight : 0;
            tooltips.add(Component.literal(
                    String.format(java.util.Locale.ROOT, "- %s ×%d · %.1f%% (%d/%d)",
                            stack.getHoverName().getString(), stack.getCount(),
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
        // 由基类 animateTick 在客户端按 tick 调用; 破碎烟尘从控制器前方升起.
        if (getLevel() == null || getFrontFacing() == null) {
            return;
        }
        RandomSource random = getLevel().random;
        var front = getFrontFacing();
        double x = getPos().getX() + 0.5 + front.getStepX() * 0.6;
        double y = getPos().getY() + 0.5;
        double z = getPos().getZ() + 0.5 + front.getStepZ() * 0.6;
        getLevel().addParticle(ParticleTypes.SMOKE,
                x + (random.nextDouble() - 0.5) * 0.4,
                y + random.nextDouble() * 0.4,
                z + (random.nextDouble() - 0.5) * 0.4,
                0.0, 0.02, 0.0);
    }
}
