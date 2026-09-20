package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyState;
import com.hoshino.gregsteamexpansion.machine.multiblock.SteamBudget;
import com.hoshino.gregsteamexpansion.machine.multiblock.SteamProcessorUI;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.registry.GSEProcessorPatterns;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 大型蒸汽高炉 / Large Steam Blast Furnace controller
 * (large-steam-blast-furnace.md, 2026-09-09 用户裁定「极高造价与极大的结构换取
 * 极高效率」+ 同日追加「结构不要四四方方, 占地更大, 效率更高, 耗时减免」):
 * a 13×13-footprint, 15-tall three-stage tapered megastructure — hearth with a
 * 121-block coke-brick bed, a 13×13 tuyere deck, nine 11×11 hollow shaft
 * layers walled in blast bricks (gtceu:firebricks), a 9×9 throat cap and a
 * 5×5×3 chimney crown — running the full {@code gtceu:primitive_blast_furnace}
 * recipe type at up to 96 parallel. Its duration is selected from a
 * difficulty-profiled four-step proficiency curve; only completed parallel
 * operations of the exact same recipe advance the curve.
 *
 * <p>The recipe type carries no EU/t, so the family's 2 mB/EU economics are
 * replaced by a flat draw of 200 mB/t per parallel (19,200 mB/t at full load,
 * 16 ordinary supply hatches at 1,200 mB/t or 4 large hatches at 4,800 mB/t). Every consuming tick
 * also draws blast air at 4 mB/t per parallel (384 mB/t at full load) from up
 * to 8 Steam Air Intake Hatch tuyeres — sustained full load needs all 8
 * (50 mB/t passive collection each, 400 mB/t); an air shortfall freezes the
 * tick with the steam-shortage rollback and reports "鼓风不足".</p>
 *
 * <p>Family rules via {@link AbstractSteamProcessorMachine}: per-tick atomic
 * steam withdrawal across supply hatches (1,200 mB/t ordinary, 4,800 mB/t large), last-
 * successful-recipe preference, worst-case output precheck, atomic inputs,
 * persisted pending outputs, exactly-one Steam Exhaust Hatch with obstruction
 * freeze and hazard cycles. Difficulty profiles only change the four
 * proficiency durations and their three operation thresholds.</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class LargeSteamBlastFurnaceMachine extends AbstractSteamProcessorMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            LargeSteamBlastFurnaceMachine.class, AbstractSteamProcessorMachine.MANAGED_FIELD_HOLDER);

    private static final String PROFICIENCY_UI_PREFIX =
            "gregsteamexpansion.machine.large_steam_blast_furnace.proficiency.";
    /** Fixed per-parallel steam draw (粉碎机家族口径 200 mB/t). */
    public static final long STEAM_PER_TICK_PER_PARALLEL_MB = 200;
    /** Blast air per consuming tick per parallel (鼓风随并行缩放, 满载 384 mB/t). */
    public static final long BLAST_AIR_PER_PARALLEL_MB = 4;

    /** Exact recipe id whose consecutive completed operations are tracked. */
    @Persisted
    @DescSynced
    private String proficiencyRecipeId = "";
    /** Completed parallel operations, capped at the selected profile's mastery threshold. */
    @Persisted
    @DescSynced
    private int proficiencyOperations;

    public LargeSteamBlastFurnaceMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public GTRecipeType recipeType() {
        return GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES;
    }

    @Override
    public int maximumParallel() {
        // 96 remains the fixed cap; proficiency now controls duration and keeps
        // even mastered default throughput below the former 0.4x/240x values.
        return 96;
    }

    @Override
    protected boolean requiresExhaustHatch() {
        // 大型机口径: 必须且只能 1 个排气仓.
        return true;
    }

    @Override
    protected boolean hasExhaustHazard() {
        return true;
    }

    @Override
    protected boolean allowsAirIntake() {
        // 议题 4: 鼓风口 — 家族首个必需进气室机型.
        return true;
    }

    @Override
    protected int maximumAirIntakes() {
        // 最多 8 个鼓风口; 满载鼓风 384 mB/t 恰好需要全部 8 个
        // (8 × 50 mB/t = 400 mB/t 采集上限) 才能长期自持.
        return 8;
    }

    @Override
    protected boolean requiresAirIntake() {
        return true;
    }

    // 不覆写 acceptsRecipe: 本机执行 primitive_blast_furnace 类型的**全部**配方
    // (本模组 3 条铁粉 → 锻铁 + 上游 18 条炼钢配方, 含 steel_from_*_wrought
    // 锻铁 → 钢), 即上游 PBF 同语义的 96 并行规模化上位。共用类型的切开只做在
    // 上游那一侧 (RecipeLogicMixin: 原版 PBF 不执行本模组注入的锻铁配方)。

    @Override
    protected boolean passesVoltageGate(GTRecipe recipe) {
        // 议题 3: PBF 配方无 EU/t (能量即配方内固体燃料), 直接放行;
        // 外部模组若给该类型加入带 EU 配方, 仍按家族 ≤ LV 门处理.
        var eut = recipe.getInputEUt();
        if (eut.isEmpty()) {
            return true;
        }
        return super.passesVoltageGate(recipe);
    }

    @Override
    protected long batchDurationTicks(GTRecipe recipe, int parallel) {
        int percent = durationPercentFor(recipe.getId().toString());
        return Math.max(1, ((long) recipe.duration * percent + 99L) / 100L);
    }

    @Override
    protected void onBatchStarted(GTRecipe recipe, int parallel) {
        String recipeId = recipe.getId().toString();
        if (!recipeId.equals(proficiencyRecipeId)) {
            proficiencyRecipeId = recipeId;
            proficiencyOperations = 0;
            markDirty();
        }
    }

    @Override
    protected void onBatchCompleted(GTRecipe recipe, int parallel) {
        String recipeId = recipe.getId().toString();
        if (!recipeId.equals(proficiencyRecipeId)) {
            // Legacy in-flight batches have no proficiency fields. Finishing one
            // seeds the new streak without altering its already locked economics.
            proficiencyRecipeId = recipeId;
            proficiencyOperations = 0;
        }
        int cap = GSEDifficultyState.blastFurnaceRequiredOperations(isRemote(), 3);
        proficiencyOperations = (int) Math.min(cap, (long) boundedProficiencyOperations() + parallel);
        markDirty();
    }

    private int durationPercentFor(String recipeId) {
        int level = recipeId.equals(proficiencyRecipeId) ? getProficiencyLevel() : 0;
        return GSEDifficultyState.blastFurnaceDurationPercent(isRemote(), level);
    }

    public int getProficiencyLevel() {
        int operations = boundedProficiencyOperations();
        if (operations >= GSEDifficultyState.blastFurnaceRequiredOperations(isRemote(), 3)) return 3;
        if (operations >= GSEDifficultyState.blastFurnaceRequiredOperations(isRemote(), 2)) return 2;
        if (operations >= GSEDifficultyState.blastFurnaceRequiredOperations(isRemote(), 1)) return 1;
        return 0;
    }

    public String getProficiencyRecipeId() {
        return proficiencyRecipeId;
    }

    public int getProficiencyOperations() {
        return boundedProficiencyOperations();
    }

    private int boundedProficiencyOperations() {
        int cap = GSEDifficultyState.blastFurnaceRequiredOperations(isRemote(), 3);
        return Math.max(0, Math.min(cap, proficiencyOperations));
    }

    public int getProficiencyDurationPercent() {
        return GSEDifficultyState.blastFurnaceDurationPercent(isRemote(), getProficiencyLevel());
    }

    @Override
    protected int appendAdditionalInfoRows(DraggableScrollableWidgetGroup scroll, int y) {
        return SteamProcessorUI.tooltipRow(scroll, y, PROFICIENCY_UI_PREFIX + "label",
                this::proficiencyText, this::proficiencyTooltips);
    }

    private String proficiencyText() {
        int level = getProficiencyLevel();
        String name = Component.translatable(PROFICIENCY_UI_PREFIX + "level." + level).getString();
        int target = level >= 3
                ? GSEDifficultyState.blastFurnaceRequiredOperations(isRemote(), 3)
                : GSEDifficultyState.blastFurnaceRequiredOperations(isRemote(), level + 1);
        return name + " (" + boundedProficiencyOperations() + " / " + target + ")";
    }

    private List<Component> proficiencyTooltips() {
        Component recipe = proficiencyRecipeId.isEmpty()
                ? Component.translatable(PROFICIENCY_UI_PREFIX + "none")
                : Component.literal(proficiencyRecipeId);
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable(PROFICIENCY_UI_PREFIX + "recipe", recipe));
        lines.add(Component.translatable(PROFICIENCY_UI_PREFIX + "duration",
                getProficiencyDurationPercent()));
        if (getProficiencyLevel() < 3) {
            lines.add(Component.translatable(PROFICIENCY_UI_PREFIX + "next",
                    GSEDifficultyState.blastFurnaceRequiredOperations(isRemote(), getProficiencyLevel() + 1)));
        } else {
            lines.add(Component.translatable(PROFICIENCY_UI_PREFIX + "mastered"));
        }
        return List.copyOf(lines);
    }

    @Override
    public void onMachineRemoved() {
        // Production experience belongs to this placed controller. It is not
        // copied into the dropped item or carried to a replacement block.
        proficiencyRecipeId = "";
        proficiencyOperations = 0;
        super.onMachineRemoved();
    }

    @Override
    protected long batchSteamPerTickMb(GTRecipe recipe, long eu, int parallel) {
        // 议题 5: 配方无 EU, 蒸汽按固定马力费计收 (与 EU 脱钩);
        // 满载 96 并行 = 19,200 mB/t: 16 个普通仓或 4 个大型仓.
        return STEAM_PER_TICK_PER_PARALLEL_MB * parallel;
    }

    @Override
    protected long batchAuxiliaryPerTickMb(int parallel) {
        return BLAST_AIR_PER_PARALLEL_MB * parallel;
    }

    @Override
    protected boolean drawAuxiliaryInputs(long demand, boolean simulate) {
        // 鼓风随并行线性缩放, 跨全部鼓风口聚合抽取 (SIMULATE 先行, 缺风不取汽;
        // EXECUTE 仅在蒸汽成功扣取后调用).
        List<SteamAirIntakeHatchPartMachine> intakes = airIntakes();
        return SteamBudget.drawBlastAir(intakes, demand,
                simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    protected Component auxiliaryShortfallText() {
        return Component.translatable("gregsteamexpansion.machine.large_steam_blast_furnace.low_blast");
    }

    @Override
    public BlockPattern getPattern() {
        return GSEProcessorPatterns.createBlastFurnace(getDefinition());
    }
}
