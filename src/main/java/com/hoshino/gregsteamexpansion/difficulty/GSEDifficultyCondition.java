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
 * (difficulty.md 配方加载机制). The startup config has already fixed the tier
 * before the first datapack load, so no world-specific reload is required.
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
