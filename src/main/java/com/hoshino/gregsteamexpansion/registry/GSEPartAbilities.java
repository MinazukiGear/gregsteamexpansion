package com.hoshino.gregsteamexpansion.registry;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;

/**
 * 自定义多方块机器部件能力 (machines-and-hatches.md 注册身份)。
 *
 * <p>These abilities only ever register the matching hatch registered by this
 * mod. They deliberately do not extend the generic GTCEu fluid-hatch or steam
 * abilities: electric-era machines must not accept the cheap steam-era
 * hatches, and the fluid hatches must never satisfy {@code PartAbility.STEAM}
 * energy requirements.</p>
 */
public final class GSEPartAbilities {
    /**
     * 蒸汽流体输入仓 / Steam Fluid Input Hatch: recipe-fluid intake for steam
     * multiblocks that explicitly accept it in their structure predicates.
     */
    public static final PartAbility STEAM_IMPORT_FLUIDS = new PartAbility("steam_import_fluids");
    /**
     * 蒸汽流体输出仓 / Steam Fluid Output Hatch: recipe-fluid output for steam
     * multiblocks that explicitly accept it in their structure predicates.
     */
    public static final PartAbility STEAM_EXPORT_FLUIDS = new PartAbility("steam_export_fluids");
    /**
     * 蒸汽进气室 / Steam Air Intake Hatch: dedicated air-collecting part for
     * steam multiblocks that explicitly accept it in their structure
     * predicates; never a generic fluid source.
     */
    public static final PartAbility STEAM_AIR_INTAKE = new PartAbility("steam_air_intake");

    private GSEPartAbilities() {}
}
