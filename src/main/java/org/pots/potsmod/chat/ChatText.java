package org.pots.potsmod.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatText {
    public static final Pattern IGN = Pattern.compile("[a-zA-Z0-9_]{3,16}");
    private static final String WYNN_SOFT_WRAP = "\udaff\udffc\ue001\udb00\udc06";
    private static final Pattern URL_PREFIX = Pattern.compile("https?://[^\\s]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOVER_REAL_NAME = Pattern.compile("(?:'(?:s)? real (?:user)?name is\\s+|Real Username:\\s*)([a-zA-Z0-9_]{3,16})", Pattern.CASE_INSENSITIVE);
    private static final Pattern NICK_HOVER = Pattern.compile("([a-zA-Z0-9_]{3,16})'s nickname is", Pattern.CASE_INSENSITIVE);
    private static final Pattern NICK_PAREN = Pattern.compile("^([a-zA-Z0-9_]{3,16})\\s*\\(.+\\)$");

    private ChatText() {
    }

    public static String resolveRealName(Component message, String displayed) {
        if (displayed == null) {
            return null;
        }
        String stripped = stripNickDecoration(displayed);
        if (message == null) {
            return IGN.matcher(stripped).matches() ? stripped : null;
        }
        String resolved = resolveRealNameAnywhere(message, stripped);
        return resolved != null ? resolved : stripped;
    }

    public static String resolveRealNameAnywhere(Component message, String displayed) {
        if (message == null) {
            return null;
        }
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        message.visit((style, fragment) -> {
            String hover = hoverRealName(style);
            if (hover != null) {
                candidates.add(hover);
            }
            String insertion = style.getInsertion();
            if (insertion != null && IGN.matcher(insertion).matches()) {
                candidates.add(insertion);
            }
            return Optional.empty();
        }, Style.EMPTY);
        if (candidates.isEmpty()) {
            return null;
        }
        String nick = displayed == null ? "" : displayed.trim();
        if (nick.isEmpty()) {
            return candidates.iterator().next();
        }
        String nickLower = nick.toLowerCase(Locale.ROOT);
        String bestPrefix = null;
        for (String candidate : candidates) {
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.equals(nickLower) || !lower.startsWith(nickLower) || (bestPrefix != null && candidate.length() <= bestPrefix.length())) {
                continue;
            }
            bestPrefix = candidate;
        }
        if (bestPrefix != null) {
            return bestPrefix;
        }
        for (String candidate : candidates) {
            if (!candidate.equalsIgnoreCase(nick)) {
                return candidate;
            }
        }
        return candidates.iterator().next();
    }

    private static List<MetaChar> flatten(Component message) {
        if (message == null) {
            return List.of();
        }
        ArrayList<MetaChar> out = new ArrayList<>();
        message.visit((style, fragment) -> {
            String hover = hoverRealName(style);
            String insertion = style.getInsertion();
            boolean previousWasSpace = !out.isEmpty() && out.get(out.size() - 1).value() == ' ';
            int index = 0;
            while (index < fragment.length()) {
                int codePoint = fragment.codePointAt(index);
                index += Character.charCount(codePoint);
                if (Character.isWhitespace(codePoint) || isIgnorable(codePoint)) {
                    if (!previousWasSpace) {
                        out.add(new MetaChar(' ', hover, insertion));
                        previousWasSpace = true;
                    }
                    continue;
                }
                for (char ch : Character.toChars(codePoint)) {
                    out.add(new MetaChar(ch, hover, insertion));
                }
                previousWasSpace = false;
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static String text(List<MetaChar> chars) {
        StringBuilder builder = new StringBuilder(chars.size());
        for (MetaChar mc : chars) {
            builder.append(mc.value());
        }
        return builder.toString();
    }

    public static String hoverRealName(Style style) {
        if (style == null) return null;
        HoverEvent hover = style.getHoverEvent();
        if (hover instanceof HoverEvent.ShowText showText) {
            String text = showText.value().getString().replace('\u2019', '\'').replace('\u2018', '\'');
            Matcher matcher = HOVER_REAL_NAME.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
            Matcher nick = NICK_HOVER.matcher(text);
            if (nick.find()) {
                return nick.group(1);
            }
        }
        return null;
    }

    public static String resolveClickTargetName(Component message, String displayed) {
        String base = stripNickDecoration(displayed);
        String resolved = resolveRealNameAnywhere(message, base);
        return resolved != null ? resolved : base;
    }

    private static String stripNickDecoration(String displayed) {
        String name = displayed == null ? "" : displayed.trim();
        int slash = name.indexOf('/');
        if (slash > 0) {
            return name.substring(0, slash).trim();
        }
        Matcher paren = NICK_PAREN.matcher(name);
        if (paren.matches()) {
            return paren.group(1).trim();
        }
        return name;
    }

    public static String normalize(String raw) {
        return normalizeWhitespace(raw).replaceAll("\\s+([,.:;!?])", "$1");
    }

    public static String unwrapWynncraftSoftWraps(String raw) {
        if (raw == null || raw.indexOf(WYNN_SOFT_WRAP) < 0) {
            return raw;
        }
        StringBuilder out = new StringBuilder(raw.length());
        int cursor = 0;
        while (true) {
            int marker = raw.indexOf(WYNN_SOFT_WRAP, cursor);
            if (marker < 0) {
                out.append(raw, cursor, raw.length());
                return out.toString();
            }
            int before = marker;
            if (before > cursor && raw.charAt(before - 1) == '\n') {
                --before;
            }
            out.append(raw, cursor, before);
            int continuation = marker + WYNN_SOFT_WRAP.length();
            if (continuation < raw.length() && raw.charAt(continuation) == ' ') {
                ++continuation;
            }
            if (!continuesUrl(out, raw, continuation)) {
                out.append(' ');
            }
            cursor = continuation;
        }
    }

    public static String normalizeWhitespace(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        boolean previousWasSpace = false;
        int index = 0;
        while (index < raw.length()) {
            int codePoint = raw.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || isIgnorable(codePoint)) {
                if (!previousWasSpace) {
                    out.append(' ');
                    previousWasSpace = true;
                }
                continue;
            }
            out.appendCodePoint(codePoint);
            previousWasSpace = false;
        }
        return out.toString().trim();
    }

    private static boolean continuesUrl(StringBuilder prefix, String raw, int continuation) {
        int tokenStart;
        for (tokenStart = prefix.length(); tokenStart > 0 && !Character.isWhitespace(prefix.charAt(tokenStart - 1)); --tokenStart) {
        }
        if (!URL_PREFIX.matcher(prefix.substring(tokenStart)).matches() || continuation >= raw.length()) {
            return false;
        }
        char first = raw.charAt(continuation);
        return !Character.isWhitespace(first) && first != '<' && first != '>';
    }

    public static boolean isIgnorable(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.UNASSIGNED, Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.SURROGATE -> true;
            default -> false;
        };
    }

    private record MetaChar(char value, String hover, String insertion) {
    }
}
