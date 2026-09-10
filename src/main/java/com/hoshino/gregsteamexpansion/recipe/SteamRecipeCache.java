package com.hoshino.gregsteamexpansion.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Process-wide cache of a GT recipe type's recipe lists and indexes, keyed by
 * {@link RecipeCacheLifecycle#revision()} + type.
 *
 * <p>Every steam processor used to snapshot the whole type into an
 * instance field on each revision change: {@code List.copyOf(recipes)} plus a
 * fresh {@code id → recipe} map, per machine. A difficulty switch triggers a
 * full datapack reload, so the cost scaled with (machine count × recipe count)
 * exactly when the server was already busy. The tables are immutable and
 * identical for every machine of a type, so they are shared here instead.</p>
 *
 * <p>A stale entry is unreachable rather than removed: the key carries the
 * revision, so the previous revision's tables simply stop being looked up and
 * become garbage once the last machine on that revision retargets. No
 * invalidation pass, no cross-world interference — the same property
 * {@code BoilerFuelCache} relies on.</p>
 */
public final class SteamRecipeCache {

    /**
     * One revision's view of a recipe type. All fields are immutable and safe to
     * share across machines, threads and saves.
     *
     * @param recipes   registration-ordered recipes of the machine's category
     * @param byId      {@code recipes} indexed by recipe id
     * @param byItem    recipes bucketed by the items they accept as input, for
     *                  candidate lookup from bus contents
     * @param byFluid   recipes bucketed by the fluids they accept as input
     */
    public record Entry(List<GTRecipe> recipes,
                        Map<ResourceLocation, GTRecipe> byId,
                        Map<RecipeCapability<?>, Map<Object, List<GTRecipe>>> byContent) {

        @Nullable
        public GTRecipe byId(ResourceLocation id) {
            return byId.get(id);
        }
    }

    private static final Entry EMPTY = new Entry(List.of(), Map.of(), Map.of());

    private static long revision = Long.MIN_VALUE;
    @Nullable
    private static GTRecipeType type;
    private static Entry entry = EMPTY;

    private SteamRecipeCache() {}

    /**
     * Shared entry for {@code type} at the current revision, built on first use
     * after a reload. Never {@code null}; a null type yields the empty entry.
     *
     * <p>Not synchronized: entries are immutable and building twice is
     * harmless, so the worst case under a race is one duplicated build.</p>
     */
    public static Entry get(@Nullable GTRecipeType type) {
        if (type == null) {
            return EMPTY;
        }
        long currentRevision = RecipeCacheLifecycle.revision();
        if (entry != EMPTY && revision == currentRevision && SteamRecipeCache.type == type) {
            return entry;
        }
        Entry built = SteamRecipeIndex.build(type);
        revision = currentRevision;
        SteamRecipeCache.type = type;
        entry = built;
        return built;
    }
}
