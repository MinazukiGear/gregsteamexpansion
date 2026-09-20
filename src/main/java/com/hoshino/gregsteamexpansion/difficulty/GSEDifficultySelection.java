package com.hoshino.gregsteamexpansion.difficulty;

import java.util.Objects;

/**
 * Complete startup difficulty supplied by an external pack authority.
 * Both values are immutable and must describe the same selected tier.
 */
public record GSEDifficultySelection(Difficulty difficulty, GSEDifficultyProfile profile) {
    public GSEDifficultySelection {
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(profile, "profile");
    }
}
