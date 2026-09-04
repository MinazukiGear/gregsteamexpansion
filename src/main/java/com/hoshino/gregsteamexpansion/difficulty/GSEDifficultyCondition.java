package com.hoshino.gregsteamexpansion.difficulty;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

/**
 * Datapack condition selecting the recipe variant for one difficulty tier
 * (difficulty.md 配方与数据重载机制). Recipes are authored against the Normal
 * baseline; while a server has not resolved the save tier — the process's
 * very first datapack load — every condition falls back to Normal. The
 * startup reload re-evaluates them once the save tier is known, and the
 * client never evaluates the condition itself because its recipe set comes
 * from the server sync.
 */
public record GSEDifficultyCondition(Difficulty difficulty) implements ICondition {
    public static final ResourceLocation ID = GregSteamExpansion.id("difficulty");

    public GSEDifficultyCondition {
        java.util.Objects.requireNonNull(difficulty, "difficulty");
    }

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test(ICondition.IContext context) {
        if (!GSEDifficultyState.isResolved()) {
            return difficulty == Difficulty.NORMAL;
        }
        return difficulty == GSEDifficultyState.resolved();
    }

    public static final class Serializer implements IConditionSerializer<GSEDifficultyCondition> {
        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {}

        @Override
        public ResourceLocation getID() {
            return ID;
        }

        @Override
        public void write(JsonObject json, GSEDifficultyCondition condition) {
            json.addProperty("difficulty", condition.difficulty().getSerializedName());
        }

        @Override
        public GSEDifficultyCondition read(JsonObject json) {
            String name = GsonHelper.getAsString(json, "difficulty");
            Difficulty difficulty = Difficulty.byName(name);
            if (difficulty == null) {
                throw new JsonSyntaxException(
                        "Invalid difficulty '" + name + "' in condition " + ID);
            }
            return new GSEDifficultyCondition(difficulty);
        }
    }
}
