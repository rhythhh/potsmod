package org.pots.potsmod.features.territorymap;

import org.pots.potsmod.config.PotsmodConfig;

public class WynntilsMapState {
    /** 0 = Guild Color, 1 = Defense Tier */
    private static int colorMode = -1; // -1 = not yet loaded

    // Remembered territory map zoom and center
    public static float lastZoomLevel = Float.NaN;
    public static float lastMapCenterX = Float.NaN;
    public static float lastMapCenterZ = Float.NaN;

    public static int getColorMode() {
        if (colorMode == -1) {
            colorMode = PotsmodConfig.getTerritoryColorMode();
        }
        return colorMode;
    }

    public static void setColorMode(int mode) {
        colorMode = mode;
        PotsmodConfig.setTerritoryColorMode(mode);
    }
}
