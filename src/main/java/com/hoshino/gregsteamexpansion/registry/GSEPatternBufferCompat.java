package com.hoshino.gregsteamexpansion.registry;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

/** Pattern buffers occupy item input interfaces only, without loading optional AE2 classes. */
public final class GSEPatternBufferCompat {
    private GSEPatternBufferCompat() {}

    public static boolean isPatternBuffer(ResourceLocation id) {
        return id != null && id.getNamespace().equals("gtceu") &&
                (id.getPath().equals("me_pattern_buffer") || id.getPath().equals("me_pattern_buffer_proxy"));
    }

    public static boolean isPatternBuffer(IMultiPart part) {
        return isPatternBuffer(part.self().getDefinition().getId());
    }

    public static TraceabilityPredicate abilities(PartAbility ability) {
        return Predicates.blocks(ability.getAllBlocks().stream()
                .filter(block -> ability == PartAbility.IMPORT_ITEMS ||
                        !isPatternBuffer(ForgeRegistries.BLOCKS.getKey(block)))
                .toArray(Block[]::new));
    }
}
