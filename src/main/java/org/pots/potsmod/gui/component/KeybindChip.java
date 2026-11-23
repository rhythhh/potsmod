package org.pots.potsmod.gui.component;

import com.mojang.blaze3d.platform.InputConstants;
import lombok.Getter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.pots.potsmod.config.setting.KeybindSetting;
import org.pots.potsmod.gui.GuiStyle;
import org.pots.potsmod.gui.theme.ThemeTokens;
import org.pots.potsmod.render.Render2D;
import org.pots.potsmod.util.KeyUtil;

public class KeybindChip {
    public static final int HEIGHT = 16;
    public static final int MIN_WIDTH = 48;

    @Getter
    private static KeybindChip activeCapturing = null;

    private final KeybindSetting setting;
    @Getter
    private boolean capturing;
    private int x, y, width;

    public KeybindChip(KeybindSetting setting) {
        this.setting = setting;
    }

    public static void clearActiveCapturing() {
        if (activeCapturing != null) {
            activeCapturing.capturing = false;
            activeCapturing = null;
        }
    }

    public void cancelCapture() {
        this.capturing = false;
        if (activeCapturing == this) {
            activeCapturing = null;
        }
    }

    public void setPosition(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = Math.max(MIN_WIDTH, width);
    }

    public int getWidth(Font font) {
        String text = capturing ? "Press key" : KeyUtil.getDisplayName(setting.get());
        return Math.max(MIN_WIDTH, font.width(GuiStyle.styled(text)) + 12);
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEIGHT;
        int bg = capturing ? 0xFF2A2A2A : (hovered ? ThemeTokens.INPUT_BG_HOVER : ThemeTokens.INPUT_BG);
        int border = capturing ? 0xFFFFFFFF : (hovered ? ThemeTokens.CARD_BORDER_HOVER : ThemeTokens.INPUT_BORDER);

        Render2D.shaderRoundedSurface(g, x, y, width, HEIGHT, ThemeTokens.RADIUS_INPUT, bg, border);

        String text = capturing ? "..." : KeyUtil.getDisplayName(setting.get());
        int textColor = capturing ? ThemeTokens.TEXT_ACTIVE : (hovered ? 0xFFE0E0E0 : ThemeTokens.TEXT_INACTIVE);

        int tw = font.width(GuiStyle.styled(text));
        g.drawString(font, GuiStyle.styled(text), x + (width - tw) / 2, y + (HEIGHT - font.lineHeight) / 2 + 1, textColor, false);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        boolean over = mx >= x && mx <= x + width && my >= y && my <= y + HEIGHT;

        if (capturing) {
            if (button == 1) { // Right click clears
                setting.set(KeyUtil.UNBOUND);
                cancelCapture();
                return true;
            }
            if (button >= 2) { // Middle click or extra buttons
                InputConstants.Key mouseKey = InputConstants.Type.MOUSE.getOrCreate(button);
                setting.set(mouseKey.getName());
                cancelCapture();
                return true;
            }
            return true;
        }

        if (over && button == 0) {
            if (activeCapturing != null && activeCapturing != this) {
                activeCapturing.cancelCapture();
            }
            capturing = true;
            activeCapturing = this;
            return true;
        }

        return false;
    }

    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (!capturing) return false;

        int key = event.key();
        if (key == 256) { // GLFW_KEY_ESCAPE
            cancelCapture();
            return true;
        }

        if (key == 261 || key == 259) { // GLFW_KEY_DELETE or GLFW_KEY_BACKSPACE
            setting.set(KeyUtil.UNBOUND);
            cancelCapture();
            return true;
        }

        InputConstants.Key inputKey = InputConstants.getKey(event);
        if (inputKey != null && inputKey != InputConstants.UNKNOWN) {
            setting.set(inputKey.getName());
        }
        cancelCapture();
        return true;
    }
}
