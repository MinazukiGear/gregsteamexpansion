package com.hoshino.gregsteamexpansion.integration.emi;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.integration.emi.multipage.MultiblockInfoEmiRecipe;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 从 EMI 的普通物品索引中隐藏遗留的 {@code gtceu:steam_input_hatch}
 * (machines-and-hatches.md 禁用范围第 3 条). EMI stays an optional client-side
 * recipe viewer: this class is only instantiated by EMI itself through its
 * {@code @EmiEntrypoint} scan, so the mod never requires EMI at runtime.
 * GSE multiblock previews use synthetic IDs so they cannot collide with a
 * controller's data-driven crafting recipe (notably the large coke oven).
 */
@EmiEntrypoint
public class GSEEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.removeEmiStacks(stack -> {
            Item item = stack.getKeyOfType(Item.class);
            if (item == null) {
                return false;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            return GTCEu.id("steam_input_hatch").equals(id);
        });
        // EMI applies this predicate to both current and future registrations,
        // so plugin ordering cannot reintroduce the upstream preview IDs.
        registry.removeRecipes(recipe -> recipe.getClass() == MultiblockInfoEmiRecipe.class
                && recipe.getId() != null
                && GregSteamExpansion.MOD_ID.equals(recipe.getId().getNamespace()));
        GTRegistries.MACHINES.values().stream()
                .filter(MultiblockMachineDefinition.class::isInstance)
                .map(MultiblockMachineDefinition.class::cast)
                .filter(definition -> GregSteamExpansion.MOD_ID.equals(definition.getId().getNamespace()))
                .filter(MultiblockMachineDefinition::isRenderXEIPreview)
                .forEach(definition -> registry.addRecipe(new MultiblockInfoEmiRecipe(definition) {
                    @Override
                    public ResourceLocation getId() {
                        return GregSteamExpansion.id("/multiblock_info/" + definition.getId().getPath());
                    }
                }));
    }
}
