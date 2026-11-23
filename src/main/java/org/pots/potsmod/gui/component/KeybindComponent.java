package org.pots.potsmod.gui.component;

import com.mojang.blaze3d.platform.InputConstants;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import org.pots.potsmod.config.setting.KeybindSetting;
import org.pots.potsmod.gui.GuiStyle;
import org.pots.potsmod.gui.theme.GuiTheme;
import org.pots.potsmod.render.Render2D;
import org.pots.potsmod.util.KeyUtil;

import java.util.List;

@RequiredArgsConstructor
public class KeybindComponent implements ConfigComponent {
    public static final int HEIGHT = 18;
    private static final int MIN_BUTTON_WIDTH = 64;
    private static final int BUTTON_HEIGHT = 14;
    private static final int BUTTON_RIGHT_PADDING = 4;
    private static final int LABEL_CONTROL_GAP = 8;

    @Getter
    private static KeybindComponent activeCapturing = null;

    private final KeybindSetting setting;
    private int x, y, width;
    @Getter
    private int height = HEIGHT;
    @Getter
    private boolean capturing = false;

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

    @Override
    public void setPosition(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    @Override
    public void updateLabelLayout(Font font, int width) {
        this.width = width;
        int btnW = computeButtonWidth(font);
        int maxLabelW = Math.max(1, width - btnW - BUTTON_RIGHT_PADDING - LABEL_CONTROL_GAP);
        List<FormattedCharSequence> lines = WrappedText.lines(font, setting.getTitle(), maxLabelW);
        height = WrappedText.rowHeight(font, lines, HEIGHT);
    }

    private int computeButtonWidth(Font font) {
        String text = capturing ? "Press a key..." : KeyUtil.getDisplayName(setting.get());
        int tw = font.width(GuiStyle.styled(text));
        return Math.max(MIN_BUTTON_WIDTH, tw + 14);
    }

    @Override
    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, GuiTheme theme, int opacity) {
        updateLabelLayout(font, width);
        int textColor = 0xFFFFFFFF;
        int centerY = y + height / 2;

        int btnW = computeButtonWidth(font);
        int maxLabelW = Math.max(1, width - btnW - BUTTON_RIGHT_PADDING - LABEL_CONTROL_GAP);
        List<FormattedCharSequence> lines = WrappedText.lines(font, setting.getTitle(), maxLabelW);
        WrappedText.draw(g, font, lines, x, WrappedText.centeredY(y, height, WrappedText.height(font, lines)), textColor);

        int buttonX = x + width - btnW - BUTTON_RIGHT_PADDING;
        int buttonY = centerY - BUTTON_HEIGHT / 2;
        boolean hovered = mouseX >= buttonX && mouseX <= buttonX + btnW && mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT;

        int btnBg = capturing ? Render2D.withAlpha(theme.accentColor(), 45) : 0xFF14151E;
        int btnBorder = capturing ? theme.accentColor() : (hovered ? 0xFF3E435B : 0xFF282B3C);

        Render2D.shaderRoundedSurface(
                g,
                buttonX, buttonY, btnW, BUTTON_HEIGHT,
                4, btnBg, btnBorder
        );

        String display = capturing ? "> Press key <" : KeyUtil.getDisplayName(setting.get());
        int btnTextColor = capturing ? theme.accentColor() : 0xFFE2E8F0;

        int tw = font.width(GuiStyle.styled(display));
        g.drawString(font, GuiStyle.styled(display), buttonX + (btnW - tw) / 2, buttonY + (BUTTON_HEIGHT - font.lineHeight) / 2 + 1, btnTextColor, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Font font = Minecraft.getInstance().font;
        int btnW = computeButtonWidth(font);
        int centerY = y + height / 2;
        int buttonX = x + width - btnW - BUTTON_RIGHT_PADDING;
        int buttonY = centerY - BUTTON_HEIGHT / 2;
        boolean overButton = mx >= buttonX && mx <= buttonX + btnW && my >= buttonY && my <= buttonY + BUTTON_HEIGHT;

        if (capturing) {
            // Right click clears and exits capture
            if (button == 1) {
                setting.set(KeyUtil.UNBOUND);
                cancelCapture();
                return true;
            }
            // Mouse button 2+ (middle click or button 3, 4, etc.) captures mouse key
            if (button >= 2) {
                InputConstants.Key mouseKey = InputConstants.Type.MOUSE.getOrCreate(button);
                setting.set(mouseKey.getName());
                cancelCapture();
                return true;
            }
            // Left click on button cancels capture
            if (button == 0 && overButton) {
                cancelCapture();
                return true;
            }
            // Left click outside button also cancels capture
            if (button == 0) {
                cancelCapture();
                return false;
            }
            return true;
        }

        if (overButton) {
            if (button == 0) {
                // Enter capture mode
                if (activeCapturing != null && activeCapturing != this) {
                    activeCapturing.cancelCapture();
                }
                activeCapturing = this;
                capturing = true;
                return true;
            } else if (button == 1) {
                // Right click clears it immediately
                setting.set(KeyUtil.UNBOUND);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!capturing) {
            return false;
        }

        int key = event.key();

        // Escape cancels and keeps old value
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            cancelCapture();
            return true;
        }

        // Backspace or Delete clears keybind
        if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            setting.set(KeyUtil.UNBOUND);
            cancelCapture();
            return true;
        }

        // Any other key: capture it
        InputConstants.Key inputKey = InputConstants.getKey(event);
        if (inputKey != null && inputKey != InputConstants.UNKNOWN) {
            setting.set(inputKey.getName());
        }
        cancelCapture();
        return true;
    }
}
