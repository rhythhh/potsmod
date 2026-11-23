package org.pots.potsmod.gui.component;

import net.minecraft.client.gui.GuiGraphics;
import org.pots.potsmod.render.Render2D;
import org.pots.potsmod.gui.theme.ThemeTokens;

public class ToggleSwitch {
    public static final int WIDTH = 24;
    public static final int HEIGHT = 12;

    public static void render(GuiGraphics g, int x, int y, float animProgress) {
        int trackColor = Render2D.lerpColor(ThemeTokens.TOGGLE_OFF_TRACK, ThemeTokens.TOGGLE_ON_TRACK, animProgress);
        int borderColor = Render2D.lerpColor(ThemeTokens.TOGGLE_OFF_BORDER, ThemeTokens.TOGGLE_ON_BORDER, animProgress);

        Render2D.shaderRoundedSurface(
                g,
                x,
                y,
                WIDTH,
                HEIGHT,
                HEIGHT / 2,
                trackColor,
                borderColor
        );

        int knobSize = HEIGHT - 4; // 8px
        int knobY = y + 2;
        int startX = x + 2;
        int endX = x + WIDTH - 2 - knobSize;
        int knobX = Math.round(startX + animProgress * (endX - startX));

        int knobColor = Render2D.lerpColor(ThemeTokens.TOGGLE_OFF_KNOB, ThemeTokens.TOGGLE_ON_KNOB, animProgress);
        Render2D.shaderRoundedRect(g, knobX, knobY, knobSize, knobSize, knobSize / 2, knobColor);
    }
}
