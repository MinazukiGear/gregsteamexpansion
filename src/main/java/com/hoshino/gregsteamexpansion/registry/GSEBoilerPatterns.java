package com.hoshino.gregsteamexpansion.registry;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Supplier;

/**
 * Fixed 7×11×7 trimmed-box pattern shared by the four Boiler Room tiers
 * (boiler-room.md P2#9): the bottom and top faces shrink to 5×11 (one block
 * in from each side along the 7-wide axis) while the five middle layers keep
 * the full 7×11 perimeter ring. Fixed parts: controller at the front-face
 * centre (height 4), muffler at the back-face centre (height 4), the 9-block
 * pipe axis between them, the tier firebox slabs (3×9 on the bottom layer,
 * replacing casing, plus the mirrored 3×9 filling interior air on layer 2)
 * and 1–11 air intakes along the top-face centre strip. That strip admits
 * only casing or air intakes; standard hatches replace casing elsewhere.
 */
public final class GSEBoilerPatterns {

    private GSEBoilerPatterns() {}

    /** Tier shell data: casing / pipe / firebox blocks (P3#16). */
    public record TierBlocks(Supplier<? extends Block> casing,
                             Supplier<? extends Block> pipe,
                             Supplier<? extends Block> firebox) {}

    /**
     * The hatch rule for the 230 general casing positions: GTCEu standard fluid
     * input hatch(es) (water + liquid fuel; a single multi-tank hatch or
     * ≥2 single hatches), a standard item input bus for the co-firing
     * powder, and a standard fluid output hatch for the steam. 蒸汽流体仓、
     * 蒸汽物品总线与蒸汽排气仓 are never admissible (P2#9 hard constraint) —
     * the mod's hatches register under GSEPartAbilities, never under these
     * GTCEu abilities. Any of the 230 general shell positions may use one of these
     * admitted interfaces; there is no ordinary-casing minimum.
     */
    private static TraceabilityPredicate casingCandidates(TierBlocks tier) {
        return Predicates.blocks(tier.casing().get())
                .or(GSEPatternBufferCompat.abilities(PartAbility.IMPORT_FLUIDS))
                .or(GSEPatternBufferCompat.abilities(PartAbility.IMPORT_ITEMS))
                .or(GSEPatternBufferCompat.abilities(PartAbility.EXPORT_FLUIDS));
    }

    // Row sets (back -> front, 11 rows × 7 chars) per height layer.
    private static final String[] BOTTOM_LAYER = {
            " CCCCC ",
            " CXXXC ", " CXXXC ", " CXXXC ", " CXXXC ", " CXXXC ",
            " CXXXC ", " CXXXC ", " CXXXC ", " CXXXC ",
            " CCCCC ",
    };
    private static final String[] FIREBOX_RING_LAYER = {
            "CCCCCCC",
            "CIXXXIC", "CIXXXIC", "CIXXXIC", "CIXXXIC", "CIXXXIC",
            "CIXXXIC", "CIXXXIC", "CIXXXIC", "CIXXXIC",
            "CCCCCCC",
    };
    private static final String[] PLAIN_RING_LAYER = {
            "CCCCCCC",
            "C     C", "C     C", "C     C", "C     C", "C     C",
            "C     C", "C     C", "C     C", "C     C",
            "CCCCCCC",
    };
    private static final String[] AXIS_LAYER = {
            "CCCMCCC",
            "C  P  C", "C  P  C", "C  P  C", "C  P  C", "C  P  C",
            "C  P  C", "C  P  C", "C  P  C", "C  P  C",
            "CCCSCCC",
    };
    private static final String[] TOP_LAYER = {
            " CCACC ",
            " CCACC ", " CCACC ", " CCACC ", " CCACC ",
            " CCACC ", " CCACC ", " CCACC ", " CCACC ",
            " CCACC ",
            " CCACC ",
    };

    /**
     * 锅炉房 pattern (P2#9 部件位置): rows run back -> front (depth 11),
     * chars left -> right (width 7), aisles bottom -> top (height 7).
     * Symbols: {@code C} tier casing (hatch-replaceable), {@code X} tier
     * firebox, {@code P} tier pipe casing, {@code M} muffler (exact 1),
     * {@code A} roof centre-strip casing or air intake (1–11 intakes),
     * {@code I} air beside the second-layer fireboxes, {@code S} controller.
     */
    public static BlockPattern createPattern(MultiblockMachineDefinition definition, TierBlocks tier) {
        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle(BOTTOM_LAYER)       // height 1 (bottom face + firebox slab)
                .aisle(FIREBOX_RING_LAYER) // height 2 (ring, firebox fills interior air)
                .aisle(PLAIN_RING_LAYER)   // height 3
                .aisle(AXIS_LAYER)         // height 4 (pipe axis + controller + muffler)
                .aisle(PLAIN_RING_LAYER)   // height 5
                .aisle(PLAIN_RING_LAYER)   // height 6
                .aisle(TOP_LAYER)          // height 7 (top face + air intake)
                .where('C', casingCandidates(tier))
                .where('X', Predicates.blocks(tier.firebox().get()))
                .where('P', Predicates.blocks(tier.pipe().get()))
                .where('M', Predicates.abilities(PartAbility.MUFFLER).setExactLimit(1))
                .where('A', Predicates.blocks(tier.casing().get())
                        .or(Predicates.abilities(GSEPartAbilities.STEAM_AIR_INTAKE).setMinGlobalLimited(1)))
                .where('I', Predicates.air())
                .where('S', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }

    /**
     * Representative layout (boiler-room.md P2#9): the minimum fixed set —
     * controller front-centre, muffler back-centre, intake top-centre, two
     * standard fluid input hatches (water + liquid fuel), one item input bus
     * for the powder and one fluid output hatch on the height-2 front wall.
     * Axis convention as in {@code GSEProcessorPatterns} (layers bottom ->
     * top, rows back -> front, chars west -> east).
     */
    public static MultiblockShapeInfo shapeInfo(MultiblockMachineDefinition definition, TierBlocks tier) {
        String[] fireboxRingWithHatches = FIREBOX_RING_LAYER.clone();
        fireboxRingWithHatches[10] = "CFFJLCC";
        // Minimal representative: one intake in the centre, casing in the
        // other ten optional strip slots. Pattern candidates still expose both.
        String[] roofWithIntake = TOP_LAYER.clone();
        roofWithIntake[5] = " CCNCC ";
        String[][] layers = {
                BOTTOM_LAYER,
                fireboxRingWithHatches,
                PLAIN_RING_LAYER,
                AXIS_LAYER,
                PLAIN_RING_LAYER.clone(),
                PLAIN_RING_LAYER.clone(),
                roofWithIntake,
        };
        return GSEPatternLayouts.shape(layers)
                .where('C', tier.casing().get())
                .where('X', tier.firebox().get())
                .where('P', tier.pipe().get())
                .where('I', Blocks.AIR)
                // MUFFLER_HATCH 用 ELECTRIC_TIERS 注册, ULV 槽 (index 0) 为 null,
                // 取 LV (index 1) 才非空; 否则 .where 传入 null Supplier 会在
                // MultiblockShapeInfo#where 里 NPE, 连结构预览都会崩。
                // The muffler sits on the back wall. With the representative
                // controller facing north, south points out of that wall;
                // facing it up leaves its front blocked by the layer above and
                // makes GTCEu silently reject every fuel recipe.
                .where('M', GTMachines.MUFFLER_HATCH[GTValues.LV], Direction.SOUTH)
                .where('A', tier.casing().get())
                .where('N', GSEMachines.STEAM_AIR_INTAKE_HATCH, Direction.UP)
                .where('S', definition, Direction.NORTH)
                .where('F', GTMachines.FLUID_IMPORT_HATCH[1], Direction.NORTH)
                .where('J', GTMachines.ITEM_IMPORT_BUS[1], Direction.NORTH)
                .where('L', GTMachines.FLUID_EXPORT_HATCH[1], Direction.NORTH)
                .build();
    }
}
