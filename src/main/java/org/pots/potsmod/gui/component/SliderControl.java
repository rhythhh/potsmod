package org.pots.potsmod.gui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.pots.potsmod.config.setting.ConfigSetting;
import org.pots.potsmod.config.setting.FloatSetting;
import org.pots.potsmod.config.setting.IntSetting;
import org.pots.potsmod.gui.GuiStyle;
import org.pots.potsmod.gui.theme.ThemeTokens;
import org.pots.potsmod.render.Render2D;

public class SliderControl {
    public static final int TRACK_HEIGHT = 2;
    public static final int KNOB_SIZE = 8;
    private final ConfigSetting<?> setting;
    private final boolean isInt;
    private final float min;
    private final float max;

    private int x, y, width;
    private boolean dragging;

    public SliderControl(ConfigSetting<?> setting) {
        this.setting = setting;
        if (setting instanceof IntSetting intSetting) {
            this.isInt = true;
            this.min = intSetting.getMin();
            this.max = intSetting.getMax();
        } else if (setting instanceof FloatSetting floatSetting) {
            this.isInt = false;
            this.min = floatSetting.getMin();
            this.max = floatSetting.getMax();
        } else {
            throw new IllegalArgumentException("SliderControl requires IntSetting or FloatSetting");
        }
    }

    public void setPosition(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public float getProgress() {
        if (isInt) {
            int val = ((ConfigSetting<Integer>) setting).get();
            return (val - min) / (max - min);
        } else {
            float val = ((ConfigSetting<Float>) setting).get();
            return (val - min) / (max - min);
        }
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        String valStr = isInt ? String.valueOf(setting.get()) : String.format(java.util.Locale.ROOT, "%.2f", (Float) setting.get());
        int valW = font.width(GuiStyle.styled(valStr));
        int trackW = width - valW - 10;
        int trackY = y + 7;

        // Unfilled track
        Render2D.shaderRoundedRect(g, x, trackY, trackW, TRACK_HEIGHT, 1, ThemeTokens.SLIDER_TRACK_UNFILLED);

        // Filled track
        float progress = Math.clamp(getProgress(), 0.0f, 1.0f);
        int filledW = Math.round(progress * trackW);
        if (filledW > 0) {
            Render2D.shaderRoundedRect(g, x, trackY, Math.min(trackW, filledW), TRACK_HEIGHT, 1, ThemeTokens.SLIDER_TRACK_FILLED);
        }

        // Circular knob
        int knobX = x + Math.round(progress * trackW) - KNOB_SIZE / 2;
        int knobY = trackY + TRACK_HEIGHT / 2 - KNOB_SIZE / 2;
        Render2D.shaderRoundedRect(g, knobX, knobY, KNOB_SIZE, KNOB_SIZE, KNOB_SIZE / 2, ThemeTokens.SLIDER_KNOB);

        // Value text at right
        g.drawString(font, GuiStyle.styled(valStr), x + trackW + 8, y + 4, ThemeTokens.TEXT_TITLE, false);
    }

    @SuppressWarnings("unchecked")
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;

        String valStr = isInt ? String.valueOf(setting.get()) : String.format(java.util.Locale.ROOT, "%.2f", (Float) setting.get());
        int valW = 20; // approximate
        int trackW = width - valW - 10;

        if (mx >= x - 2 && mx <= x + trackW + 2 && my >= y && my <= y + 16) {
            dragging = true;
            updateValueFromMouse(mx, trackW);
            return true;
        }

        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    public void mouseDragged(double mx, double my, int button) {
        if (dragging) {
            String valStr = isInt ? String.valueOf(setting.get()) : String.format(java.util.Locale.ROOT, "%.2f", (Float) setting.get());
            int valW = 20;
            int trackW = width - valW - 10;
            updateValueFromMouse(mx, trackW);
        }
    }

    @SuppressWarnings("unchecked")
    private void updateValueFromMouse(double mx, int trackW) {
        float pct = (float) ((mx - x) / (double) trackW);
        pct = Math.clamp(pct, 0.0f, 1.0f);

        if (isInt) {
            int val = Math.round(min + pct * (max - min));
            ((ConfigSetting<Integer>) setting).set(val);
        } else {
            float val = min + pct * (max - min);
            ((ConfigSetting<Float>) setting).set(val);
        }
    }
}
