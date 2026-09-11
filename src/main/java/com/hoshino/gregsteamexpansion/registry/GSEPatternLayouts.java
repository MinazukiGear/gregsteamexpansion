package com.hoshino.gregsteamexpansion.registry;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import java.util.Map;

/** Converts one representative layer layout into runtime patterns and preview shapes. */
final class GSEPatternLayouts {

    private GSEPatternLayouts() {}

    /**
     * Normalizes preview-only symbols to runtime predicate symbols. Representative
     * hatch positions therefore retain the original candidate predicate.
     */
    static FactoryBlockPattern pattern(String[][] layers, Map<Character, Character> aliases) {
        var builder = FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP);
        for (String[] layer : layers) {
            String[] rows = new String[layer.length];
            for (int row = 0; row < layer.length; row++) {
                StringBuilder normalized = new StringBuilder(layer[row].length());
                for (int column = 0; column < layer[row].length(); column++) {
                    char symbol = layer[row].charAt(column);
                    normalized.append(aliases.getOrDefault(symbol, symbol));
                }
                rows[row] = normalized.toString();
            }
            builder.aisle(rows);
        }
        return builder;
    }

    /** Converts LEFT/FRONT/UP layers into preview-positive X/Y/Z coordinates. */
    static MultiblockShapeInfo.ShapeInfoBuilder shape(String[][] layers) {
        int height = layers.length;
        int width = layers[0][0].length();
        int depth = layers[0].length;
        var builder = MultiblockShapeInfo.builder();
        for (int row = depth - 1; row >= 0; row--) {
            String[] previewRows = new String[height];
            for (int layer = 0; layer < height; layer++) {
                StringBuilder value = new StringBuilder(width);
                for (int column = 0; column < width; column++) {
                    value.append(layers[layer][row].charAt(column));
                }
                previewRows[layer] = value.toString();
            }
            builder.aisle(previewRows);
        }
        return builder;
    }
}
