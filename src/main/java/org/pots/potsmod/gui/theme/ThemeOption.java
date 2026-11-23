package org.pots.potsmod.gui.theme;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum ThemeOption {
    DEFAULT("default", "Potato", GuiTheme.potatoTheme()),
    POTATO("potato", "Potato", GuiTheme.potatoTheme()),
    DARK("dark", "Dark", GuiTheme.darkMonochromeTheme()),
    OCEAN("ocean", "Ocean", GuiTheme.builder()
            .background(0xFF0D1117)
            .secondary(0xFF161B22)
            .textColor(0xFFFFFFFF)
            .secondaryText(0xFF8B949E)
            .trinaryText(0xFF6E7681)
            .overlay(0x60000000)
            .accentColor(0xFFFFFFFF)
            .shadowColor(0x00000000)
            .sliderTrack(0xFF21262D)
            .scrollbarColor(0x50FFFFFF)
            .build()),
    CUSTOM("custom", "Custom", GuiTheme.darkMonochromeTheme());

    private final String key;
    private final String label;
    private final GuiTheme theme;

    public static ThemeOption resolve(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return DEFAULT;
        String normalised = rawValue.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return Arrays.stream(values()).filter(o -> o.key.equals(normalised)).findFirst().orElse(DEFAULT);
    }

    public static List<String> keys() {
        return Arrays.stream(values())
                .map(ThemeOption::getKey)
                .toList();
    }

    public static String labelFor(String key) {
        return resolve(key).getLabel();
    }
}
