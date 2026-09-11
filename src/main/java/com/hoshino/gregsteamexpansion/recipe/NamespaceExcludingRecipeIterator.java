package com.hoshino.gregsteamexpansion.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import java.util.Iterator;

/**
 * Lazily filters recipes owned by one namespace.
 *
 * <p>The implementation is a top-level runtime class because anonymous or
 * nested classes declared in a Mixin compile into the protected Mixin package
 * and cannot be loaded after the injection method is merged into its target.</p>
 */
public final class NamespaceExcludingRecipeIterator implements Iterator<GTRecipe> {
    private final Iterator<GTRecipe> source;
    private final String excludedNamespace;
    private GTRecipe next;

    public NamespaceExcludingRecipeIterator(Iterator<GTRecipe> source, String excludedNamespace) {
        this.source = source;
        this.excludedNamespace = excludedNamespace;
        this.next = advance();
    }

    private GTRecipe advance() {
        while (source.hasNext()) {
            GTRecipe candidate = source.next();
            if (!excludedNamespace.equals(candidate.getId().getNamespace())) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public GTRecipe next() {
        GTRecipe current = next;
        next = advance();
        return current;
    }
}
