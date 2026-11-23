package org.pots.potsmod.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlayerResolver {
    private static final Pattern IGN_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
    private static final Pattern HOVER_REAL_NAME = Pattern.compile(
            "(?:'(?:s)? real (?:user)?name is\\s+|Real Username:\\s*)([a-zA-Z0-9_]{3,16})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NICK_HOVER = Pattern.compile(
            "([a-zA-Z0-9_]{3,16})'s nickname is",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PAREN_NICK = Pattern.compile(
            "(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{3,16})\\s*\\(([a-zA-Z0-9_]{3,16})\\)"
    );
    private static final Pattern SLASH_NICK = Pattern.compile(
            "([a-zA-Z0-9_]{3,16})/([a-zA-Z0-9_]{3,16})"
    );

    // Nickname -> Real IGN cache
    private static final Map<String, String> NICK_TO_REAL = new ConcurrentHashMap<>();

    private PlayerResolver() {}

    public static void learnFromComponent(Component component) {
        if (component == null) return;
        component.visit((style, text) -> {
            String trimmedText = text != null ? text.trim() : "";
            if (trimmedText.isEmpty()) return Optional.empty();

            String insertion = style.getInsertion();
            if (insertion != null) {
                insertion = insertion.trim();
                if (IGN_PATTERN.matcher(insertion).matches()) {
                    if (!trimmedText.equalsIgnoreCase(insertion)) {
                        NICK_TO_REAL.put(cleanKey(trimmedText), insertion);
                    }
                    NICK_TO_REAL.put(cleanKey(insertion), insertion);
                }
            }

            HoverEvent hover = style.getHoverEvent();
            if (hover instanceof HoverEvent.ShowText showText) {
                String hoverContent = showText.value().getString().replace('\u2019', '\'').replace('\u2018', '\'');
                Matcher m = HOVER_REAL_NAME.matcher(hoverContent);
                if (m.find()) {
                    String real = m.group(1).trim();
                    if (IGN_PATTERN.matcher(real).matches()) {
                        NICK_TO_REAL.put(cleanKey(trimmedText), real);
                        NICK_TO_REAL.put(cleanKey(real), real);
                    }
                }
                Matcher m2 = NICK_HOVER.matcher(hoverContent);
                if (m2.find()) {
                    String real = m2.group(1).trim();
                    if (IGN_PATTERN.matcher(real).matches()) {
                        NICK_TO_REAL.put(cleanKey(trimmedText), real);
                        NICK_TO_REAL.put(cleanKey(real), real);
                    }
                }
            }
            return Optional.empty();
        }, Style.EMPTY);

        String fullStr = component.getString();
        Matcher pm = PAREN_NICK.matcher(fullStr);
        while (pm.find()) {
            String part1 = pm.group(1);
            String part2 = pm.group(2);
            // In Wynncraft, paren typically shows: Nickname (RealName) or RealName (Nickname)
            NICK_TO_REAL.put(cleanKey(part1), part2);
            NICK_TO_REAL.put(cleanKey(part2), part2);
        }

        Matcher sm = SLASH_NICK.matcher(fullStr);
        while (sm.find()) {
            String realIgn = sm.group(1);
            String nick = sm.group(2);
            NICK_TO_REAL.put(cleanKey(nick), realIgn);
            NICK_TO_REAL.put(cleanKey(realIgn), realIgn);
        }
    }

    public static String resolveRealName(Component component, String rawName) {
        if (rawName == null || rawName.isBlank()) return "Unknown";

        // Try direct ChatText resolution from component hover/insertion (EdenMod approach)
        if (component != null) {
            String resolved = org.pots.potsmod.chat.ChatText.resolveRealName(component, rawName);
            if (resolved != null && !resolved.isBlank() && IGN_PATTERN.matcher(resolved).matches()) {
                NICK_TO_REAL.put(cleanKey(rawName), resolved);
                NICK_TO_REAL.put(cleanKey(resolved), resolved);
                return resolved;
            }
        }

        String clean = cleanName(rawName);

        // Learn first from the specific event component
        if (component != null) {
            learnFromComponent(component);
        }

        // 1. Direct cache lookup
        String cached = NICK_TO_REAL.get(cleanKey(clean));
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        // 2. Check tablist / connection player info
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getConnection() != null) {
                for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                    if (info.getProfile() != null && info.getProfile().name() != null) {
                        String realIgn = info.getProfile().name();
                        if (realIgn.equalsIgnoreCase(clean)) {
                            NICK_TO_REAL.put(cleanKey(clean), realIgn);
                            return realIgn;
                        }
                        if (info.getTabListDisplayName() != null) {
                            String tabDisplay = info.getTabListDisplayName().getString();
                            if (tabDisplay.contains(clean)) {
                                NICK_TO_REAL.put(cleanKey(clean), realIgn);
                                return realIgn;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Fallback: clean IGN characters
        return clean;
    }

    private static String cleanKey(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    }

    private static String cleanName(String s) {
        String stripped = s.replaceAll("(?i)[§&][0-9a-fk-orx]", "").trim();
        Matcher m = Pattern.compile("([a-zA-Z0-9_]{3,16})$").matcher(stripped);
        if (m.find()) {
            return m.group(1);
        }
        return stripped.replaceAll("[^a-zA-Z0-9_]", "");
    }
}
