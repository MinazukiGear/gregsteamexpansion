package com.hoshino.gregsteamexpansion.machine.multiblock.processor;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.registry.GSEProcessorPatterns;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;
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
 * recipe type at up to 96 parallel and 0.4× duration, 240× the primitive
 * blast furnace's throughput.
 *
 * <p>The recipe type carries no EU/t, so the family's 2 mB/EU economics are
 * replaced by a flat draw of 200 mB/t per parallel (19,200 mB/t at full load,
 * exactly 16 supply hatches at their 1,200 mB/t caps). Every consuming tick
 * also draws blast air at 4 mB/t per parallel (384 mB/t at full load) from up
 * to 8 Steam Air Intake Hatch tuyeres — sustained full load needs all 8
 * (50 mB/t passive collection each, 400 mB/t); an air shortfall freezes the
 * tick with the steam-shortage rollback and reports "鼓风不足".</p>
 *
 * <p>Family rules via {@link AbstractSteamProcessorMachine}: per-tick atomic
 * steam withdrawal across supply hatches (1,200 mB/t per hatch), last-
 * successful-recipe preference, worst-case output precheck, atomic inputs,
 * persisted pending outputs, exactly-one Steam Exhaust Hatch with obstruction
 * freeze and hazard cycles, three difficulty tiers identical.</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class LargeSteamBlastFurnaceMachine extends AbstractSteamProcessorMachine {

    /** Fixed duration multiplier (2026-09-09 用户裁定耗时减免: 0.4×). */
    public static final double DURATION_MULTIPLIER = 0.4;
    /** Fixed per-parallel steam draw (粉碎机家族口径 200 mB/t). */
    public static final long STEAM_PER_TICK_PER_PARALLEL_MB = 200;
    /** Blast air per consuming tick per parallel (鼓风随并行缩放, 满载 256 mB/t). */
    public static final long BLAST_AIR_PER_PARALLEL_MB = 4;

    public LargeSteamBlastFurnaceMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public GTRecipeType recipeType() {
        return GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES;
    }

    @Override
    public int maximumParallel() {
        // 议题 6 (2026-09-09 二次裁定): 极大结构换极高效率 — 96 并行 + 0.4×
        // 耗时 = 240× 原始高炉吞吐 (全模组最高并行, 超过 S1/A4/C0 的 64).
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
        return Math.max(1, (long) Math.ceil(recipe.duration * DURATION_MULTIPLIER));
    }

    @Override
    protected long batchSteamPerTickMb(GTRecipe recipe, long eu, int parallel) {
        // 议题 5: 配方无 EU, 蒸汽按固定马力费计收 (与 EU 脱钩);
        // 满载 96 并行 = 19,200 mB/t, 恰好用满 16 个供给仓 (1,200 mB/t/仓).
        return STEAM_PER_TICK_PER_PARALLEL_MB * parallel;
    }

    @Override
    protected boolean drawAuxiliaryInputs(int parallel, boolean simulate) {
        // 鼓风随并行线性缩放, 跨全部鼓风口聚合抽取 (SIMULATE 先行, 缺风不取汽;
        // EXECUTE 仅在蒸汽成功扣取后调用).
        List<SteamAirIntakeHatchPartMachine> intakes = airIntakes();
        if (intakes.isEmpty()) {
            return false;
        }
        int demand = (int) (BLAST_AIR_PER_PARALLEL_MB * parallel);
        if (demand <= 0) {
            return true;
        }
        int remaining = demand;
        for (var intake : intakes) {
            FluidStack drained = intake.tank.drainInternal(GTMaterials.Air.getFluid(remaining),
                    simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            remaining -= drained.getAmount();
            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
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
