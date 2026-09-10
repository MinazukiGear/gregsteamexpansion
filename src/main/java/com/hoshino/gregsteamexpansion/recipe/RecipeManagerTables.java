package com.hoshino.gregsteamexpansion.recipe;

import com.hoshino.gregsteamexpansion.mixins.RecipeManagerAccessor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared access to vanilla {@link RecipeManager}'s recipe tables for the
 * datapack-load rewrite hooks ({@code OreCrushingMigration} /
 * {@code BoilerRoomFuelSync}).
 *
 * <p>Vanilla stores the tables as Guava {@code ImmutableMap}s after
 * {@code RecipeManager#apply}, so any hook that needs to add or remove entries
 * must copy them into mutable maps first. That copy previously happened in both
 * hooks independently and cloned <em>every</em> recipe type — on a large pack
 * that is tens of thousands of entries duplicated per load, on both sides
 * (server reload and client recipe sync), while each hook only ever touches one
 * or two types.</p>
 *
 * <p>{@link #mutableTablesOf} therefore copies the type index only and converts
 * a table to a mutable copy lazily, via {@link #mutableTable}. Callers must not
 * hand the source tables anywhere else: they alias the read-only vanilla maps
 * until the accessor writes the whole set back at the end of the transaction.</p>
 */
public final class RecipeManagerTables {

    private RecipeManagerTables() {}

    /**
     * Shallow copy of the recipe manager's type → name → recipe index, with the
     * per-type tables left as the original (immutable) instances. Returns
     * {@code null} when the manager does not expose its tables at all, which the
     * callers treat as a load-blocking failure.
     *
     * <p>The type index is always copied even if the caller ends up mutating
     * nothing: it is read by every recipe lookup, so it must never be handed out
     * for in-place writes.</p>
     */
    @Nullable
    public static Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> mutableTablesOf(RecipeManager manager) {
        if (manager instanceof RecipeManagerAccessor accessor) {
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> current = accessor.gse$getRecipes();
            if (current == null) {
                return null;
            }
            return new HashMap<>(current);
        }
        return null;
    }

    /**
     * Writable view of one recipe type's table: the existing mutable instance if
     * a previous call already converted it, otherwise a copy of the immutable
     * original, stored back into the index. Never returns {@code null} — a
     * missing type yields an empty table, which is what both hooks want when they
     * (re)build the target type from scratch.
     */
    public static Map<ResourceLocation, Recipe<?>> mutableTable(
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> tables, RecipeType<?> type) {
        Map<ResourceLocation, Recipe<?>> table = tables.get(type);
        if (table instanceof HashMap<ResourceLocation, Recipe<?>>) {
            return table;
        }
        Map<ResourceLocation, Recipe<?>> mutable = table == null ? new HashMap<>() : new HashMap<>(table);
        tables.put(type, mutable);
        return mutable;
    }

    /**
     * True when {@link #mutableTable} would have to copy — i.e. the caller only
     * wants to read the table and should keep aliasing the immutable original
     * instead of paying for a copy it will never mutate.
     */
    public static boolean isMutable(Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> tables,
                                    RecipeType<?> type) {
        return tables.get(type) instanceof HashMap<ResourceLocation, Recipe<?>>;
    }
}
