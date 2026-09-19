package com.hoshino.gregsteamexpansion.terminal;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;

import java.util.List;

/** Optional controller contract exposing geometric pattern variants to the Ultimate Terminal. */
public interface UltimateTerminalStructureVariants {
    String CHANNEL_ID = "structure_size";

    List<Variant> terminalStructureVariants();

    record Variant(String label, BlockPattern pattern) {}
}
