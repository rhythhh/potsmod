package org.pots.potsmod.gui.component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.pots.potsmod.config.setting.ButtonSetting;
import org.pots.potsmod.gui.GuiStyle;
import org.pots.potsmod.gui.theme.GuiTheme;
import org.pots.potsmod.render.Render2D;

import java.util.List;

@RequiredArgsConstructor
public class ButtonComponent implements ConfigComponent {
    public static final int HEIGHT = 18;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 14;
    private static final int BUTTON_RIGHT_PADDING = 4;
    private static final int LABEL_CONTROL_GAP = 8;

    private final ButtonSetting setting;
    private int x, y, width;
    @Getter
    private int height = HEIGHT;

    public void setPosition(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public void updateLabelLayout(Font font, int width) {
        this.width = width;
        List<FormattedCharSequence> lines = WrappedText.lines(font, setting.getTitle(), labelMaxWidth(width));
        height = WrappedText.rowHeight(font, lines, HEIGHT);
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, GuiTheme theme, int opacity) {
        updateLabelLayout(font, width);
        int textColor = 0xFFFFFFFF;
        int centerY = y + height / 2;

        List<FormattedCharSequence> lines = WrappedText.lines(font, setting.getTitle(), labelMaxWidth(width));
        WrappedText.draw(g, font, lines, x, WrappedText.centeredY(y, height, WrappedText.height(font, lines)), textColor);

        int buttonX = x + width - BUTTON_WIDTH - BUTTON_RIGHT_PADDING;
        int buttonY = centerY - BUTTON_HEIGHT / 2;
        boolean hovered = mouseX >= buttonX && mouseX <= buttonX + BUTTON_WIDTH && mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT;

        int btnBg = hovered ? Render2D.withAlpha(theme.accentColor(), 35) : 0xFF14151E;
        int btnBorder = hovered ? theme.accentColor() : 0xFF282B3C;

        Render2D.shaderRoundedSurface(
                g,
                buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                4, btnBg, btnBorder
        );

        String btnText = setting.getButtonText();
        if (btnText != null) {
            int tw = font.width(GuiStyle.styled(btnText));
            g.drawString(font, GuiStyle.styled(btnText), buttonX + (BUTTON_WIDTH - tw) / 2, buttonY + (BUTTON_HEIGHT - font.lineHeight) / 2 + 1, textColor, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int centerY = y + height / 2;
            int buttonX = x + width - BUTTON_WIDTH - BUTTON_RIGHT_PADDING;
            int buttonY = centerY - BUTTON_HEIGHT / 2;
            if (mx >= buttonX && mx <= buttonX + BUTTON_WIDTH && my >= buttonY && my <= buttonY + BUTTON_HEIGHT) {
                Runnable action = setting.get();
                if (action != null) {
                    action.run();
                }
                return true;
            }
        }
        return false;
    }

    private int labelMaxWidth(int width) {
        return Math.max(1, width - BUTTON_WIDTH - BUTTON_RIGHT_PADDING - LABEL_CONTROL_GAP);
    }
}
