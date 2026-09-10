package com.hoshino.gregsteamexpansion.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.IdentityHashMap;
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
 * <p>All types share a revision-scoped identity map. On revision changes the
 * map is cleared; machines may retain their old immutable entries until they
 * refresh, but future lookups only return entries from the current revision.</p>
 */
public final class SteamRecipeCache {

    /**
     * One revision's view of a recipe type. All fields are immutable and safe to
     * share across machines, threads and saves.
     *
     * @param recipes   registration-ordered recipes of the machine's category
     * @param byId      {@code recipes} indexed by recipe id
     * @param byContent recipes bucketed by capability and accepted input content
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
    private static final Map<GTRecipeType, Entry> entries = new IdentityHashMap<>();

    private SteamRecipeCache() {}

    /**
     * Shared entry for {@code type} at the current revision, built on first use
     * after a reload. Never {@code null}; a null type yields the empty entry.
     *
     * <p>Synchronizing the lookup keeps the revision and all type entries
     * consistent and prevents duplicate builds for concurrent callers.</p>
     */
    public static synchronized Entry get(@Nullable GTRecipeType type) {
        if (type == null) {
            return EMPTY;
        }
        long currentRevision = RecipeCacheLifecycle.revision();
        if (revision != currentRevision) {
            entries.clear();
            revision = currentRevision;
        }
        return entries.computeIfAbsent(type, SteamRecipeIndex::build);
    }
}
