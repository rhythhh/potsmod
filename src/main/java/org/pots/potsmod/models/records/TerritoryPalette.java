package org.pots.potsmod.models.records;

public record TerritoryPalette(int defaultColor, int insideColor) {
    public int colorFor(boolean inside) {
        return inside ? insideColor : defaultColor;
    }
}
