package org.pots.potsmod.gui.component;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.config.setting.*;
import org.pots.potsmod.gui.GuiStyle;
import org.pots.potsmod.gui.animation.Animation;
import org.pots.potsmod.gui.animation.Easing;
import org.pots.potsmod.gui.theme.ThemeTokens;
import org.pots.potsmod.render.Render2D;

import java.util.ArrayList;
import java.util.List;

public class SettingCard {
    @Getter
    private final String id;
    @Getter
    private final String title;
    @Getter
    private final String description;
    @Getter
    private final String sectionHeader;

    // Optional section master toggle
    private final SettingSection section;
    private final ConfigSetting<?> setting;

    // Sub-settings if this card is a module section
    @Getter
    private final List<SettingCard> subCards = new ArrayList<>();
    @Getter
    private boolean subExpanded;

    // Control instances
    private final Animation toggleAnim = new Animation(Easing.EASE_OUT_EXPO, 120);
    private DropdownControl dropdown;
    private KeybindChip keybind;
    private NumberStepper stepper;
    private SliderControl slider;
    private EditBox editBox;

    private int x, y, width;
    @Getter
    private int height = 34;
    private int headerHeight = 34;

    // Factory for section module card
    public static SettingCard ofSection(SettingSection sec, String sectionLabel) {
        return new SettingCard(sec, sectionLabel);
    }

    // Factory for standalone setting card
    public static SettingCard ofSetting(ConfigSetting<?> set, String sectionLabel) {
        return new SettingCard(set, sectionLabel);
    }

    private SettingCard(SettingSection sec, String sectionLabel) {
        this.section = sec;
        this.setting = null;
        this.id = sec.id();
        this.title = sec.title();
        this.description = sec.description();
        this.sectionHeader = sectionLabel;
        this.toggleAnim.setValue(sec.hasToggle() && sec.isEnabled() ? 1.0f : 0.0f);

        for (ConfigSetting<?> sub : sec.settings()) {
            subCards.add(SettingCard.ofSetting(sub, null));
        }
    }

    private SettingCard(ConfigSetting<?> set, String sectionLabel) {
        this.section = null;
        this.setting = set;
        this.id = set.getId();
        this.title = set.getTitle();
        this.description = set.getDescription();
        this.sectionHeader = sectionLabel;

        if (set instanceof BooleanSetting bool) {
            this.toggleAnim.setValue(Boolean.TRUE.equals(bool.get()) ? 1.0f : 0.0f);
        } else if (set instanceof ChoiceSetting choice) {
            this.dropdown = new DropdownControl(choice);
        } else if (set instanceof KeybindSetting kb) {
            this.keybind = new KeybindChip(kb);
        } else if (set instanceof IntSetting || set instanceof FloatSetting) {
            if (set instanceof IntSetting is && is.getMax() - is.getMin() <= 10) {
                this.stepper = new NumberStepper(set);
            } else {
                this.slider = new SliderControl(set);
            }
        }
    }

    public void initEditBoxes(Font font, List<EditBox> container) {
        if (setting instanceof StringSetting str) {
            int h = Math.max(14, font.lineHeight + 3);
            editBox = new EditBox(font, 0, 0, 70, h, GuiStyle.styled(title));
            editBox.setBordered(false);
            editBox.setHeight(h);
            editBox.setTextColor(0xFFFFFFFF);
            GuiStyle.applyFont(editBox, title);
            editBox.setValue(str.format());
            editBox.setResponder(str::tryParseAndSet);
            container.add(editBox);
        }
        for (SettingCard sub : subCards) {
            sub.initEditBoxes(font, container);
        }
    }

    public int calculateHeight(Font font, int cardWidth) {
        this.width = cardWidth;
        int controlW = computeControlWidth(font);
        int maxTextW = Math.max(50, cardWidth - controlW - ThemeTokens.CARD_PADDING_X * 2 - 10);

        List<FormattedCharSequence> titleLines = title != null && !title.isBlank()
                ? WrappedText.lines(font, title, maxTextW)
                : List.of();
        int titleH = titleLines.isEmpty() ? font.lineHeight : titleLines.size() * font.lineHeight;

        List<FormattedCharSequence> descLines = description != null && !description.isBlank()
                ? WrappedText.lines(font, description, maxTextW)
                : List.of();

        int textH = titleH;
        if (!descLines.isEmpty()) {
            textH += 2 + descLines.size() * (font.lineHeight - 1);
        }

        int baseH = Math.max(34, textH + ThemeTokens.CARD_PADDING_Y * 2);
        this.headerHeight = baseH;
        if (subExpanded && !subCards.isEmpty()) {
            int subTotal = 6;
            for (SettingCard sub : subCards) {
                subTotal += sub.calculateHeight(font, cardWidth - 12) + ThemeTokens.CARD_GAP;
            }
            baseH += subTotal;
        }

        this.height = baseH;
        return baseH;
    }

    private int computeControlWidth(Font font) {
        int w = 24; // info icon space + gap
        if (section != null) {
            if (section.hasToggle()) w += ToggleSwitch.WIDTH + 8;
            return Math.max(46, w);
        }
        if (setting instanceof BooleanSetting) return ToggleSwitch.WIDTH + 24;
        if (dropdown != null) return 80 + 24;
        if (keybind != null) return keybind.getWidth(font) + 24;
        if (stepper != null) return 60 + 24;
        if (slider != null) return 80 + 24;
        if (editBox != null) return 100 + 24;
        if (setting instanceof ButtonSetting btn) {
            String txt = btn.getButtonText() != null ? btn.getButtonText() : "Action";
            return Math.max(50, font.width(GuiStyle.styled(txt)) + 12) + 24;
        }
        if (setting instanceof ColorSetting) return 30 + 24;
        return 40 + 24;
    }

    public void setPosition(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        int bg = hovered ? ThemeTokens.CARD_BG_HOVER : ThemeTokens.CARD_BG;
        int border = hovered ? ThemeTokens.CARD_BORDER_HOVER : ThemeTokens.CARD_BORDER;

        Render2D.shaderRoundedSurface(g, x, y, width, height, ThemeTokens.RADIUS_CARD, bg, border);

        int controlW = computeControlWidth(font);
        int maxTextW = Math.max(50, width - controlW - ThemeTokens.CARD_PADDING_X * 2 - 10);

        int textX = x + ThemeTokens.CARD_PADDING_X;
        int textY = y + ThemeTokens.CARD_PADDING_Y;

        // Title (wrapped to maxTextW so it never overlaps controls)
        List<FormattedCharSequence> titleLines = WrappedText.lines(font, title, maxTextW);
        for (FormattedCharSequence line : titleLines) {
            g.drawString(font, line, textX, textY, ThemeTokens.TEXT_TITLE, false);
            textY += font.lineHeight;
        }
        textY += 2;

        // Description
        if (description != null && !description.isBlank()) {
            List<FormattedCharSequence> descLines = WrappedText.lines(font, description, maxTextW);
            for (FormattedCharSequence line : descLines) {
                g.drawString(font, line, textX, textY, ThemeTokens.TEXT_DESCRIPTION, false);
                textY += font.lineHeight - 1;
            }
        }

        // Render control on right
        int controlRight = x + width - ThemeTokens.CARD_PADDING_X;
        int centerY = y + headerHeight / 2;

        renderControl(g, font, controlRight, centerY, mouseX, mouseY);

        // Sub-settings drawer if expanded
        if (subExpanded && !subCards.isEmpty()) {
            int subY = y + headerHeight + 2;
            Render2D.horizontalLine(g, x + 8, subY - 4, width - 16, ThemeTokens.DIVIDER_LINE);
            for (SettingCard sub : subCards) {
                sub.setPosition(x + 6, subY, width - 12);
                sub.render(g, font, mouseX, mouseY);
                subY += sub.getHeight() + ThemeTokens.CARD_GAP;
            }
        }
    }

    public String getHoveredExplanation(int mx, int my) {
        if (subExpanded) {
            for (SettingCard sub : subCards) {
                String subExp = sub.getHoveredExplanation(mx, my);
                if (subExp != null) return subExp;
            }
        }
        int activeH = subExpanded ? headerHeight : height;
        if (mx < x || mx > x + width || my < y || my > y + activeH) {
            return null;
        }

        int infoX = x + width - ThemeTokens.CARD_PADDING_X - 10;
        int infoY = y + headerHeight / 2;
        int distSq = (mx - infoX) * (mx - infoX) + (my - infoY) * (my - infoY);
        if (distSq <= 64) { // 8px radius
            return org.pots.potsmod.util.FeatureHelpManager.getExplanation(id, title, description);
        }
        return null;
    }

    private void renderControl(GuiGraphics g, Font font, int rightX, int centerY, int mx, int my) {
        // Render ⓘ Info Icon at the end of every feature card
        int infoX = rightX - 6;
        int infoY = centerY;
        boolean infoHovered = (mx - infoX) * (mx - infoX) + (my - infoY) * (my - infoY) <= 64;
        int circleBg = infoHovered ? 0xFF352418 : 0xFF1C140E;
        int circleBorder = infoHovered ? 0xFFF5B545 : 0x55E5A84B;
        Render2D.shaderRoundedSurface(g, infoX - 6, infoY - 6, 12, 12, 6, circleBg, circleBorder);
        String infoChar = "i";
        int iw = font.width(infoChar);
        int infoColor = infoHovered ? 0xFFFFF5E4 : ThemeTokens.TEXT_DESCRIPTION;
        g.drawString(font, infoChar, infoX - iw / 2, infoY - font.lineHeight / 2 + 1, infoColor, false);
        rightX -= 16;

        if (section != null) {
            if (section.hasToggle()) {
                toggleAnim.run(section.isEnabled() ? 1.0f : 0.0f);
                ToggleSwitch.render(g, rightX - ToggleSwitch.WIDTH, centerY - ToggleSwitch.HEIGHT / 2, (float) toggleAnim.getValue());
            }
            return;
        }

        if (setting instanceof BooleanSetting bool) {
            toggleAnim.run(Boolean.TRUE.equals(bool.get()) ? 1.0f : 0.0f);
            ToggleSwitch.render(g, rightX - ToggleSwitch.WIDTH, centerY - ToggleSwitch.HEIGHT / 2, (float) toggleAnim.getValue());
            return;
        }

        if (dropdown != null) {
            int dw = 80;
            dropdown.setPosition(rightX - dw, centerY - DropdownControl.HEIGHT / 2, dw);
            dropdown.renderButton(g, font, mx, my);
            return;
        }

        if (keybind != null) {
            int kw = keybind.getWidth(font);
            keybind.setPosition(rightX - kw, centerY - KeybindChip.HEIGHT / 2, kw);
            keybind.render(g, font, mx, my);
            return;
        }

        if (stepper != null) {
            int sw = 60;
            stepper.setPosition(rightX - sw, centerY - NumberStepper.BTN_SIZE / 2, sw);
            stepper.render(g, font, mx, my);
            return;
        }

        if (slider != null) {
            int slw = 80;
            slider.setPosition(rightX - slw, centerY - 8, slw);
            slider.render(g, font, mx, my);
            return;
        }

        if (editBox != null) {
            int ew = Math.min(130, Math.max(70, width - ThemeTokens.CARD_PADDING_X * 2 - 90));
            int eh = editBox.getHeight();
            int ey = centerY - eh / 2;
            int ex = rightX - ew;
            Render2D.shaderRoundedSurface(g, ex, ey, ew, eh, ThemeTokens.RADIUS_INPUT, ThemeTokens.INPUT_BG,
                    editBox.isFocused() ? ThemeTokens.INPUT_BORDER_FOCUSED : ThemeTokens.INPUT_BORDER);
            GuiStyle.layoutBorderlessEditBox(editBox, font, ex + 4, ey, ew - 8, eh);
            editBox.setVisible(true);
            editBox.render(g, mx, my, 0);
            return;
        }

        if (setting instanceof ButtonSetting btn) {
            String txt = btn.getButtonText() != null ? btn.getButtonText() : "Action";
            int tw = Minecraft.getInstance().font.width(GuiStyle.styled(txt));
            int bw = Math.max(50, tw + 12);
            int bh = 16;
            int bx = rightX - bw;
            int by = centerY - bh / 2;
            boolean bhov = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
            Render2D.shaderRoundedSurface(g, bx, by, bw, bh, ThemeTokens.RADIUS_INPUT,
                    bhov ? ThemeTokens.INPUT_BG_HOVER : ThemeTokens.INPUT_BG, bhov ? ThemeTokens.CARD_BORDER_HOVER : ThemeTokens.INPUT_BORDER);
            g.drawString(font, GuiStyle.styled(txt), bx + (bw - tw) / 2, by + (bh - font.lineHeight) / 2 + 1, ThemeTokens.TEXT_TITLE, false);
            return;
        }

        if (setting instanceof ColorSetting col) {
            int cw = 28;
            int ch = 14;
            int cx = rightX - cw;
            int cy = centerY - ch / 2;
            Render2D.shaderRoundedSurface(g, cx, cy, cw, ch, ThemeTokens.RADIUS_INPUT, col.get() | 0xFF000000, ThemeTokens.INPUT_BORDER);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (subExpanded) {
            for (SettingCard sub : subCards) {
                if (sub.mouseClicked(mx, my, button)) return true;
            }
        }

        int activeH = subExpanded ? headerHeight : height;
        if (mx < x || mx > x + width || my < y || my > y + activeH) {
            return false;
        }

        if (dropdown != null && dropdown.mouseClicked(mx, my, button)) {
            return true;
        }

        if (keybind != null && keybind.mouseClicked(mx, my, button)) {
            return true;
        }

        if (stepper != null && stepper.mouseClicked(mx, my, button)) {
            return true;
        }

        if (slider != null && slider.mouseClicked(mx, my, button)) {
            return true;
        }

        int controlRight = x + width - ThemeTokens.CARD_PADDING_X;
        int centerY = y + headerHeight / 2;

        // Check info icon click (consume click so card doesn't toggle)
        int infoX = controlRight - 6;
        int infoY = centerY;
        if ((mx - infoX) * (mx - infoX) + (my - infoY) * (my - infoY) <= 64) {
            org.pots.potsmod.util.ModSounds.playClick();
            return true;
        }
        controlRight -= 16;

        if (section != null) {
            if (section.hasToggle()) {
                int swX = controlRight - ToggleSwitch.WIDTH;
                int swY = centerY - ToggleSwitch.HEIGHT / 2;
                if (mx >= swX - 4 && mx <= swX + ToggleSwitch.WIDTH + 4 && my >= swY - 4 && my <= swY + ToggleSwitch.HEIGHT + 4) {
                    section.setEnabled(!section.isEnabled());
                    PotsmodConfig.save();
                    PotsmodConfig.applyFeatureStates();
                    return true;
                }
            }

            if (!subCards.isEmpty()) {
                subExpanded = !subExpanded;
                return true;
            } else if (section.hasToggle()) {
                section.setEnabled(!section.isEnabled());
                PotsmodConfig.save();
                PotsmodConfig.applyFeatureStates();
                return true;
            }
            return false;
        }

        if (setting instanceof BooleanSetting bool) {
            bool.set(!Boolean.TRUE.equals(bool.get()));
            return true;
        }

        if (setting instanceof ButtonSetting btn) {
            String txt = btn.getButtonText() != null ? btn.getButtonText() : "Action";
            int tw = Minecraft.getInstance().font.width(GuiStyle.styled(txt));
            int bw = Math.max(50, tw + 12);
            int bh = 16;
            int bx = controlRight - bw;
            int by = centerY - bh / 2;
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + bh) {
                if (btn.getAction() != null) {
                    btn.getAction().run();
                }
                return true;
            }
        }

        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (slider != null && slider.mouseReleased(mx, my, button)) {
            return true;
        }
        if (subExpanded) {
            for (SettingCard sub : subCards) {
                if (sub.mouseReleased(mx, my, button)) return true;
            }
        }
        return false;
    }

    public void mouseDragged(double mx, double my, int button) {
        if (slider != null) {
            slider.mouseDragged(mx, my, button);
        }
        if (subExpanded) {
            for (SettingCard sub : subCards) {
                sub.mouseDragged(mx, my, button);
            }
        }
    }

    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (keybind != null && keybind.keyPressed(event)) {
            return true;
        }
        if (subExpanded) {
            for (SettingCard sub : subCards) {
                if (sub.keyPressed(event)) return true;
            }
        }
        return false;
    }

    public DropdownControl getOpenDropdown() {
        if (dropdown != null && dropdown.isOpen()) return dropdown;
        if (subExpanded) {
            for (SettingCard sub : subCards) {
                DropdownControl dd = sub.getOpenDropdown();
                if (dd != null) return dd;
            }
        }
        return null;
    }
}
