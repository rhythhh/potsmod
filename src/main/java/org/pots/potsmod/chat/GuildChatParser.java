package org.pots.potsmod.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GuildChatParser {
    public static final int GUILD_CHAT_COLOR = 0x55FFFF;
    private static final Pattern CHAT_PATTERN = Pattern.compile(
            "^(?:<\\d+>\\s*)?([a-zA-Z0-9_][a-zA-Z0-9_ ]*[a-zA-Z0-9_]|[a-zA-Z0-9_]{1,16})\\s*:\\s*(.*)$",
            Pattern.DOTALL
    );
    private static final Pattern HOVER_REAL_NAME_PATTERN = Pattern.compile(
            "(?:'(?:s)? real (?:user)?name is\\s+|Real Username:\\s*)([a-zA-Z0-9_]{3,16})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern IGN = Pattern.compile("[a-zA-Z0-9_]{3,16}");

    private GuildChatParser() {
    }

    public static boolean looksLikeGuildChat(Component message) {
        return hasLeadingGuildChatColor(message);
    }

    public static Optional<CapturedMessage> parse(Component message) {
        Optional<CapturedMessage> captured = parseSender(message);
        return captured.filter(line -> !line.message().isEmpty());
    }

    public static Optional<CapturedMessage> parseSender(Component message) {
        if (!hasLeadingGuildChatColor(message)) {
            return Optional.empty();
        }
        List<Segment> segments = collect(message);
        String cleaned = ChatText.normalizeWhitespace(concat(segments));
        Matcher matcher = CHAT_PATTERN.matcher(cleaned);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String displayed = matcher.group(1).trim();
        String content = matcher.group(2).trim();

        // Handle slash formatted sender (e.g. RealName/Nickname)
        if (displayed.contains("/")) {
            String[] parts = displayed.split("/", 2);
            if (parts.length == 2 && IGN.matcher(parts[0].trim()).matches()) {
                String realIgn = parts[0].trim();
                String nick = parts[1].trim();
                org.pots.potsmod.utils.PlayerResolver.learnFromComponent(message);
                return Optional.of(new CapturedMessage(realIgn, nick, content));
            }
        }

        String real = findRealUsername(segments, displayed);
        if (real == null) {
            real = org.pots.potsmod.utils.PlayerResolver.resolveRealName(message, displayed);
        }
        String username = resolveAvatarUsername(displayed, real);
        if (username == null) {
            return Optional.empty();
        }
        String nickname = username.equalsIgnoreCase(displayed) ? username : displayed;
        return Optional.of(new CapturedMessage(username, nickname, content));
    }

    private static String findRealUsername(List<Segment> segments, String displayed) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (Segment seg : segments) {
            String fromHover = hoverUsername(seg.hover());
            if (fromHover != null) {
                candidates.add(fromHover);
            }
        }
        for (Segment seg : segments) {
            String insertion = seg.style().getInsertion();
            if (insertion != null && IGN.matcher(insertion).matches()) {
                candidates.add(insertion);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (displayed == null || displayed.isBlank()) {
            return candidates.iterator().next();
        }
        String displayedLower = displayed.trim().toLowerCase(Locale.ROOT);
        String bestPrefix = null;
        for (String candidate : candidates) {
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.equals(displayedLower) || !lower.startsWith(displayedLower) || candidate.length() <= displayed.trim().length() || (bestPrefix != null && candidate.length() <= bestPrefix.length())) {
                continue;
            }
            bestPrefix = candidate;
        }
        if (bestPrefix != null) {
            return bestPrefix;
        }
        for (String candidate : candidates) {
            if (!candidate.equalsIgnoreCase(displayed.trim())) {
                return candidate;
            }
        }
        return candidates.iterator().next();
    }

    private static String hoverUsername(String hover) {
        if (hover == null || hover.isBlank()) {
            return null;
        }
        String text = hover.replace('\u2019', '\'').replace('\u2018', '\'');
        Matcher matcher = HOVER_REAL_NAME_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String resolveAvatarUsername(String displayed, String real) {
        if (real != null && IGN.matcher(real).matches()) {
            return real;
        }
        if (displayed != null && IGN.matcher(displayed).matches()) {
            return displayed;
        }
        String normalized = displayed == null ? "" : displayed.replaceAll("[^a-zA-Z0-9_]", "");
        return IGN.matcher(normalized).matches() ? normalized : null;
    }

    private static List<Segment> collect(Component message) {
        ArrayList<Segment> segments = new ArrayList<>();
        message.visit((style, text) -> {
            if (!text.isEmpty()) {
                segments.add(new Segment(text, style, hoverText(style)));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return segments;
    }

    private static String hoverText(Style style) {
        if (style == null) return null;
        HoverEvent hover = style.getHoverEvent();
        if (hover instanceof HoverEvent.ShowText showText) {
            return showText.value().getString();
        }
        return null;
    }

    public static boolean hasLeadingGuildChatColor(Component message) {
        if (message == null) {
            return false;
        }
        TextColor rootColor = message.getStyle().getColor();
        if (rootColor != null) {
            return rootColor.getValue() == GUILD_CHAT_COLOR;
        }
        Optional<Boolean> leading = message.visit((style, text) -> {
            if (text == null || text.isBlank()) {
                return Optional.empty();
            }
            TextColor color = style.getColor();
            return Optional.of(color != null && color.getValue() == GUILD_CHAT_COLOR);
        }, Style.EMPTY);
        return leading.orElse(false);
    }

    private static String concat(List<Segment> segments) {
        StringBuilder builder = new StringBuilder();
        for (Segment segment : segments) {
            builder.append(segment.text());
        }
        return ChatText.unwrapWynncraftSoftWraps(builder.toString());
    }

    private record Segment(String text, Style style, String hover) {
    }
}
