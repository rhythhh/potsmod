package org.pots.potsmod.gui.theme;

import lombok.Builder;

@Builder
public record GuiTheme(int background, int secondary, int textColor, int secondaryText, int trinaryText,
                       int overlay, int accentColor, int shadowColor, int sliderTrack, int scrollbarColor) {
    public static GuiTheme defaultTheme() {
        return potatoTheme();
    }

    public static GuiTheme potatoTheme() {
        return GuiTheme.builder()
                .background(ThemeTokens.WINDOW_BG)
                .secondary(ThemeTokens.CARD_BG)
                .textColor(ThemeTokens.TEXT_TITLE)
                .secondaryText(ThemeTokens.TEXT_DESCRIPTION)
                .trinaryText(ThemeTokens.TEXT_INACTIVE)
                .overlay(0x70140E0A)
                .accentColor(0xFFE5A84B)
                .shadowColor(0x800A0604)
                .sliderTrack(ThemeTokens.SLIDER_TRACK_UNFILLED)
                .scrollbarColor(0x60E5A84B)
                .build();
    }

    public static GuiTheme darkMonochromeTheme() {
        return potatoTheme();
    }
}
