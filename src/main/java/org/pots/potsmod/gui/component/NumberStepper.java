package org.pots.potsmod.gui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.pots.potsmod.config.setting.ConfigSetting;
import org.pots.potsmod.config.setting.FloatSetting;
import org.pots.potsmod.config.setting.IntSetting;
import org.pots.potsmod.gui.GuiStyle;
import org.pots.potsmod.gui.theme.ThemeTokens;
import org.pots.potsmod.render.Render2D;

public class NumberStepper {
    public static final int BTN_SIZE = 14;
    private final ConfigSetting<?> setting;
    private final boolean isInt;
    private final float min;
    private final float max;
    private final float step;

    private int x, y, width;
    private long lastStepTime = 0;

    public NumberStepper(ConfigSetting<?> setting) {
        this.setting = setting;
        if (setting instanceof IntSetting intSetting) {
            this.isInt = true;
            this.min = intSetting.getMin();
            this.max = intSetting.getMax();
            this.step = 1.0f;
        } else if (setting instanceof FloatSetting floatSetting) {
            this.isInt = false;
            this.min = floatSetting.getMin();
            this.max = floatSetting.getMax();
            this.step = 0.1f;
        } else {
            throw new IllegalArgumentException("NumberStepper requires IntSetting or FloatSetting");
        }
    }

    public void setPosition(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int minusX = x;
        int plusX = x + width - BTN_SIZE;
        int btnY = y + 2;

        boolean hoverMinus = mouseX >= minusX && mouseX <= minusX + BTN_SIZE && mouseY >= btnY && mouseY <= btnY + BTN_SIZE;
        boolean hoverPlus = mouseX >= plusX && mouseX <= plusX + BTN_SIZE && mouseY >= btnY && mouseY <= btnY + BTN_SIZE;

        // Minus button (circular)
        Render2D.shaderRoundedSurface(g, minusX, btnY, BTN_SIZE, BTN_SIZE, BTN_SIZE / 2,
                hoverMinus ? 0xFF2A2A2A : ThemeTokens.INPUT_BG, hoverMinus ? ThemeTokens.CARD_BORDER_HOVER : ThemeTokens.INPUT_BORDER);
        int minusW = font.width("-");
        g.drawString(font, GuiStyle.styled("-"), minusX + (BTN_SIZE - minusW) / 2, btnY + (BTN_SIZE - font.lineHeight) / 2,
                hoverMinus ? ThemeTokens.TEXT_ACTIVE : ThemeTokens.TEXT_INACTIVE, false);

        // Value text
        String valStr = isInt ? String.valueOf(setting.get()) : String.format(java.util.Locale.ROOT, "%.1f", (Float) setting.get());
        int valW = font.width(GuiStyle.styled(valStr));
        int valX = x + (width - valW) / 2;
        g.drawString(font, GuiStyle.styled(valStr), valX, y + 4, ThemeTokens.TEXT_TITLE, false);

        // Plus button (circular)
        Render2D.shaderRoundedSurface(g, plusX, btnY, BTN_SIZE, BTN_SIZE, BTN_SIZE / 2,
                hoverPlus ? 0xFF2A2A2A : ThemeTokens.INPUT_BG, hoverPlus ? ThemeTokens.CARD_BORDER_HOVER : ThemeTokens.INPUT_BORDER);
        int plusW = font.width("+");
        g.drawString(font, GuiStyle.styled("+"), plusX + (BTN_SIZE - plusW) / 2, btnY + (BTN_SIZE - font.lineHeight) / 2,
                hoverPlus ? ThemeTokens.TEXT_ACTIVE : ThemeTokens.TEXT_INACTIVE, false);
    }

    @SuppressWarnings("unchecked")
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;

        int minusX = x;
        int plusX = x + width - BTN_SIZE;
        int btnY = y + 2;

        if (mx >= minusX && mx <= minusX + BTN_SIZE && my >= btnY && my <= btnY + BTN_SIZE) {
            stepValue(-1);
            return true;
        }

        if (mx >= plusX && mx <= plusX + BTN_SIZE && my >= btnY && my <= btnY + BTN_SIZE) {
            stepValue(1);
            return true;
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private void stepValue(int dir) {
        if (isInt) {
            int cur = ((ConfigSetting<Integer>) setting).get();
            int next = Math.clamp(cur + dir, (int) min, (int) max);
            ((ConfigSetting<Integer>) setting).set(next);
        } else {
            float cur = ((ConfigSetting<Float>) setting).get();
            float next = Math.clamp(cur + dir * step, min, max);
            ((ConfigSetting<Float>) setting).set(next);
        }
    }
}
