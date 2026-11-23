package org.pots.potsmod.gui.component;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.pots.potsmod.config.setting.ChoiceSetting;
import org.pots.potsmod.gui.GuiStyle;
import org.pots.potsmod.gui.theme.ThemeTokens;
import org.pots.potsmod.render.Render2D;

import java.util.List;

public class DropdownControl {
    public static final int HEIGHT = 18;
    private static final int CHEVRON_WIDTH = 12;

    private final ChoiceSetting setting;
    @Getter
    @Setter
    private boolean open;
    private int x;
    private int y;
    private int width;

    public DropdownControl(ChoiceSetting setting) {
        this.setting = setting;
    }

    public void setPosition(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public void renderButton(GuiGraphics g, Font font, int mouseX, int mouseY) {
        boolean hovered = isOverButton(mouseX, mouseY);
        int bg = hovered ? ThemeTokens.INPUT_BG_HOVER : ThemeTokens.INPUT_BG;
        int border = open ? ThemeTokens.INPUT_BORDER_FOCUSED : (hovered ? ThemeTokens.CARD_BORDER_HOVER : ThemeTokens.INPUT_BORDER);

        Render2D.shaderRoundedSurface(g, x, y, width, HEIGHT, ThemeTokens.RADIUS_INPUT, bg, border);

        String display = setting.displayValue(setting.get());
        int maxTextW = width - CHEVRON_WIDTH - 8;
        if (font.width(GuiStyle.styled(display)) > maxTextW) {
            while (!display.isEmpty() && font.width(GuiStyle.styled(display + "...")) > maxTextW) {
                display = display.substring(0, display.length() - 1);
            }
            display += "...";
        }

        int textY = y + (HEIGHT - font.lineHeight) / 2 + 1;
        g.drawString(font, GuiStyle.styled(display), x + 6, textY, ThemeTokens.TEXT_TITLE, false);

        int cx = x + width - 9;
        int cy = y + HEIGHT / 2;
        if (open) {
            g.fill(cx - 3, cy + 1, cx + 4, cy + 2, ThemeTokens.TEXT_INACTIVE);
            g.fill(cx - 2, cy, cx + 3, cy + 1, ThemeTokens.TEXT_INACTIVE);
            g.fill(cx - 1, cy - 1, cx + 2, cy, ThemeTokens.TEXT_INACTIVE);
        } else {
            g.fill(cx - 3, cy - 1, cx + 4, cy, ThemeTokens.TEXT_INACTIVE);
            g.fill(cx - 2, cy, cx + 3, cy + 1, ThemeTokens.TEXT_INACTIVE);
            g.fill(cx - 1, cy + 1, cx + 2, cy + 2, ThemeTokens.TEXT_INACTIVE);
        }
    }

    public void renderPopup(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!open) return;

        List<String> options = setting.getOptions();
        int itemH = 16;
        int totalH = options.size() * itemH + 4;
        int popupY = y + HEIGHT + 2;

        Render2D.shaderRoundedSurface(g, x, popupY, width, totalH, ThemeTokens.RADIUS_INPUT, 0xFF141414, 0x30FFFFFF);

        for (int i = 0; i < options.size(); i++) {
            String opt = options.get(i);
            int iy = popupY + 2 + i * itemH;
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= iy && mouseY < iy + itemH;
            boolean selected = opt.equals(setting.get());

            if (hovered) {
                Render2D.shaderRoundedRect(g, x + 2, iy, width - 4, itemH, 3, 0xFF262626);
            }

            int textColor = selected ? ThemeTokens.TEXT_ACTIVE : (hovered ? 0xFFCCCCCC : ThemeTokens.TEXT_INACTIVE);
            String label = setting.displayValue(opt);
            int maxLabelW = Math.max(10, width - 12);
            if (font.width(GuiStyle.styled(label)) > maxLabelW) {
                while (!label.isEmpty() && font.width(GuiStyle.styled(label + "...")) > maxLabelW) {
                    label = label.substring(0, label.length() - 1);
                }
                label += "...";
            }
            g.drawString(font, GuiStyle.styled(label), x + 6, iy + (itemH - font.lineHeight) / 2 + 1, textColor, false);
        }
    }

    public boolean isOverButton(double mx, double my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + HEIGHT;
    }

    public boolean isOverPopup(double mx, double my) {
        if (!open) return false;
        int itemH = 16;
        int totalH = setting.getOptions().size() * itemH + 4;
        int popupY = y + HEIGHT + 2;
        return mx >= x && mx <= x + width && my >= popupY && my <= popupY + totalH;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;

        if (isOverButton(mx, my)) {
            open = !open;
            return true;
        }

        if (open && isOverPopup(mx, my)) {
            int itemH = 16;
            int popupY = y + HEIGHT + 2;
            int index = (int) ((my - popupY - 2) / itemH);
            List<String> options = setting.getOptions();
            if (index >= 0 && index < options.size()) {
                setting.set(options.get(index));
                open = false;
                return true;
            }
        }

        if (open) {
            open = false;
            return true;
        }

        return false;
    }
}
