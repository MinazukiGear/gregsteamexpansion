package com.hoshino.gregsteamexpansion.cokeoven;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * `gtceu:coke_oven` 配方类型的轻量索引 (coke-ovens.md 普通焦炉的外部配方兼容):
 * 焦炉仓物品输入模式与控制器输入槽根据服务端当前已经加载的合法焦炉配方动态判断
 * 可插入物品, 不硬编码原木/煤炭/煤炭块。配方列表在每次数据包重载后刷新。
 */
public final class CokeOvenRecipeIndex {

    private record Cache(RecipeManager manager, List<GTRecipe> recipes, List<Ingredient> inputIngredients,
                         List<GTRecipe> recipesSortedById) {}

    private static volatile @Nullable Cache cache;

    private CokeOvenRecipeIndex() {}

    /** 数据包重载后调用 (服务端 AddReloadListenerEvent / 客户端 RecipesUpdatedEvent)。 */
    public static void invalidate() {
        cache = null;
    }

    private static Cache get(RecipeManager manager) {
        Cache current = cache;
        if (current == null || current.manager() != manager) {
            List<GTRecipe> recipes = new ArrayList<>();
            List<Ingredient> inputs = new ArrayList<>();
            for (GTRecipe gtRecipe : manager.getAllRecipesFor(GTRecipeTypes.COKE_OVEN_RECIPES)) {
                recipes.add(gtRecipe);
                var contents = gtRecipe.getInputContents(ItemRecipeCapability.CAP);
                if (contents.isEmpty()) continue; // 没有物品输入的配方不可能被焦炉匹配
                var ingredient = ItemRecipeCapability.CAP.of(contents.get(0).getContent());
                inputs.add(ingredient);
            }
            List<GTRecipe> sorted = new ArrayList<>(recipes);
            sorted.sort(java.util.Comparator.comparing(GTRecipe::getId));
            current = new Cache(manager, List.copyOf(recipes), List.copyOf(inputs), List.copyOf(sorted));
            cache = current;
        }
        return current;
    }

    public static List<GTRecipe> recipes(RecipeManager manager) {
        return get(manager).recipes();
    }

    /** 按资源 ID 字典序排列的全部配方 (配方选择的确定性顺序)。 */
    public static List<GTRecipe> recipesSortedById(RecipeManager manager) {
        return get(manager).recipesSortedById();
    }

    /** 物品是否至少能参与一条当前已加载的合法焦炉配方 (按 Ingredient 语义匹配, 支持标签)。 */
    public static boolean isValidInput(RecipeManager manager, ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (Ingredient ingredient : get(manager).inputIngredients()) {
            if (ingredient.test(stack)) return true;
        }
        return false;
    }

    /**
     * 输入槽物品合法但所有匹配配方的输出都放不下时, 用于区分"输入无效"与
     * "输出堵塞"的候选配方数量查询。
     */
    public static boolean hasRecipeFor(RecipeManager manager, ItemStack stack) {
        return isValidInput(manager, stack);
    }
}
