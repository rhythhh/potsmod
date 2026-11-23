package org.pots.potsmod.gui;

import lombok.experimental.UtilityClass;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.gui.theme.FontOption;
import org.pots.potsmod.gui.theme.GuiTheme;
import org.pots.potsmod.gui.theme.ThemeOption;
import org.pots.potsmod.mixin.EditBoxAccessor;

@UtilityClass
public class GuiStyle {
    public static Component styled(String text) {
        return Component.literal(text).withStyle(currentFontStyle());
    }

    public static int styledWidth(Font font, String text) {
        return font.width(styled(text));
    }

    public static void applyFont(EditBox editBox, String hintText) {
        editBox.addFormatter((text, cursor) -> styled(text).getVisualOrderText());
        if (hintText != null) {
            editBox.setHint(styled(hintText));
        }
    }

    public static void layoutBorderlessEditBox(EditBox editBox, Font font, int x, int y, int width, int height) {
        editBox.setX(x);
        editBox.setY(y);
        editBox.setWidth(width);
        editBox.setHeight(height);
        if (editBox instanceof EditBoxAccessor accessor) {
            accessor.rmod$setTextY(y + Math.max(0, (height - font.lineHeight) / 2));
        }
    }

    public static GuiTheme configuredTheme() {
        return configuredTheme(1.0);
    }

    public static GuiTheme configuredTheme(double opacityMultiplier) {
        GuiTheme base = baseTheme();
        if (opacityMultiplier >= 1.0) return base;
        int bg = base.background();
        int alpha = (int) (((bg >> 24) & 0xFF) * opacityMultiplier);
        return GuiTheme.builder()
                .background((alpha << 24) | (bg & 0xFFFFFF))
                .secondary(base.secondary())
                .textColor(base.textColor())
                .secondaryText(base.secondaryText())
                .trinaryText(base.trinaryText())
                .overlay(base.overlay())
                .accentColor(base.accentColor())
                .shadowColor(base.shadowColor())
                .sliderTrack(base.sliderTrack())
                .scrollbarColor(base.scrollbarColor())
                .build();
    }

    private static GuiTheme baseTheme() {
        try {
            ThemeOption opt = PotsmodConfig.getClickGuiThemeOption();
            if (opt == ThemeOption.CUSTOM) {
                var data = PotsmodConfig.getData();
                int bg = 0xFF000000 | (data.getCustomGuiBackground() & 0xFFFFFF);
                int sec = 0xFF000000 | (data.getCustomGuiSecondary() & 0xFFFFFF);
                int acc = 0xFF000000 | (data.getCustomGuiAccent() & 0xFFFFFF);
                return GuiTheme.builder()
                        .background(bg)
                        .secondary(sec)
                        .textColor(0xFFFFFFFF)
                        .secondaryText(0xFF94A3B8)
                        .trinaryText(0xFF64748B)
                        .overlay(0x90000000)
                        .accentColor(acc)
                        .shadowColor(0x70000000)
                        .sliderTrack(0xFF232634)
                        .scrollbarColor(acc)
                        .build();
            }
            if (opt != null && opt.getTheme() != null) {
                return opt.getTheme();
            }
        } catch (Exception ignored) {
        }
        return GuiTheme.defaultTheme();
    }

    private static Style currentFontStyle() {
        try {
            FontOption option = PotsmodConfig.getClickGuiFontOption();
            return Style.EMPTY.withFont(new FontDescription.Resource(option.fontDescriptionId()));
        } catch (Exception e) {
            return Style.EMPTY;
        }
    }
}
