package org.pots.potsmod.util;

import com.mojang.blaze3d.platform.InputConstants;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.pots.potsmod.config.setting.KeybindSetting;

import java.lang.reflect.Field;
import java.util.Locale;

@UtilityClass
public class KeyUtil {

    public static final String UNBOUND = "";

    /**
     * Parse any key string representation (Minecraft key name, GLFW name, single char, numeric code)
     * into an InputConstants.Key. Returns InputConstants.UNKNOWN if unparsable or unbound.
     */
    public static InputConstants.Key parse(String raw) {
        if (raw == null) {
            return InputConstants.UNKNOWN;
        }
        String s = raw.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("none") || s.equalsIgnoreCase("unbound")) {
            return InputConstants.UNKNOWN;
        }

        // 1. Try directly with InputConstants.getKey (e.g. "key.keyboard.semicolon", "key.mouse.3")
        try {
            InputConstants.Key direct = InputConstants.getKey(s);
            if (direct != null && direct != InputConstants.UNKNOWN) {
                return direct;
            }
        } catch (Exception ignored) {
        }

        String lower = s.toLowerCase(Locale.ROOT);

        // 2. Try prefixing "key.keyboard."
        try {
            InputConstants.Key prefixed = InputConstants.getKey("key.keyboard." + lower);
            if (prefixed != null && prefixed != InputConstants.UNKNOWN) {
                return prefixed;
            }
        } catch (Exception ignored) {
        }

        // 3. Try prefixing "key.mouse."
        try {
            InputConstants.Key mouse = InputConstants.getKey("key.mouse." + lower);
            if (mouse != null && mouse != InputConstants.UNKNOWN) {
                return mouse;
            }
        } catch (Exception ignored) {
        }

        // 4. Try as numeric integer (GLFW key code or mouse button)
        try {
            int code = Integer.parseInt(s);
            if (code >= 0) {
                return InputConstants.Type.KEYSYM.getOrCreate(code);
            }
        } catch (NumberFormatException ignored) {
        }

        // 5. Try single character lookup
        if (s.length() == 1) {
            char c = s.charAt(0);
            return switch (c) {
                case ';' -> InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_SEMICOLON);
                case '=' -> InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_EQUAL);
                case '-' -> InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_MINUS);
                case '`' -> InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_GRAVE_ACCENT);
                case ',' -> InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_COMMA);
                case '.' -> InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_PERIOD);
                case '/' -> InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_SLASH);
                case '\\' -> InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_BACKSLASH);
                case '[' -> InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_BRACKET);
                case ']' -> InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_RIGHT_BRACKET);
                case '\'' -> InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_APOSTROPHE);
                default -> {
                    if (c >= 'a' && c <= 'z') {
                        yield InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_A + (c - 'a'));
                    }
                    if (c >= 'A' && c <= 'Z') {
                        yield InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_A + (c - 'A'));
                    }
                    if (c >= '0' && c <= '9') {
                        yield InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_0 + (c - '0'));
                    }
                    yield InputConstants.UNKNOWN;
                }
            };
        }

        // 6. Try GLFW reflection (e.g. "TAB", "SEMICOLON", "GLFW_KEY_TAB")
        String upper = s.toUpperCase(Locale.ROOT);
        String glfwFieldName = upper.startsWith("GLFW_KEY_") ? upper : "GLFW_KEY_" + upper;
        try {
            Field field = GLFW.class.getField(glfwFieldName);
            int code = field.getInt(null);
            return InputConstants.Type.KEYSYM.getOrCreate(code);
        } catch (Exception ignored) {
        }

        return InputConstants.UNKNOWN;
    }

    /**
     * Normalizes a raw string into standard InputConstants key name (e.g. "key.keyboard.semicolon")
     * or empty string for unbound. Falls back to defaultKeyName if unparsable.
     */
    public static String normalizeKey(String raw, String defaultKeyName) {
        if (raw == null || raw.trim().isEmpty() || raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("unbound")) {
            return UNBOUND;
        }
        InputConstants.Key key = parse(raw);
        if (key != null && key != InputConstants.UNKNOWN) {
            return key.getName();
        }
        // Unparsable: fallback to default
        if (defaultKeyName != null && !defaultKeyName.isEmpty()) {
            InputConstants.Key def = parse(defaultKeyName);
            if (def != null && def != InputConstants.UNKNOWN) {
                return def.getName();
            }
        }
        return UNBOUND;
    }

    public static String getDisplayName(String keyName) {
        InputConstants.Key key = parse(keyName);
        if (key == null || key == InputConstants.UNKNOWN) {
            return "Unbound";
        }
        try {
            return key.getDisplayName().getString();
        } catch (Exception e) {
            return key.getName();
        }
    }

    public static boolean matches(KeybindSetting setting, KeyEvent event) {
        if (setting == null) {
            return false;
        }
        return matches(setting.get(), event);
    }

    public static boolean matches(String keyName, KeyEvent event) {
        if (event == null) {
            return false;
        }
        InputConstants.Key bound = parse(keyName);
        if (bound == null || bound == InputConstants.UNKNOWN) {
            return false;
        }
        InputConstants.Key eventKey = InputConstants.getKey(event);
        return bound.equals(eventKey) || (bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() == event.key());
    }

    public static boolean matchesMouse(String keyName, int button) {
        InputConstants.Key bound = parse(keyName);
        if (bound == null || bound == InputConstants.UNKNOWN) {
            return false;
        }
        return bound.getType() == InputConstants.Type.MOUSE && bound.getValue() == button;
    }

    public static boolean isDown(KeybindSetting setting) {
        if (setting == null) {
            return false;
        }
        return isDown(setting.get());
    }

    public static boolean isDown(String rawKey) {
        InputConstants.Key key = parse(rawKey);
        if (key == null || key == InputConstants.UNKNOWN) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        long window = mc.getWindow().handle();
        if (window == 0) {
            return false;
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            int keyCode = key.getValue();
            if (keyCode >= 0) {
                return GLFW.glfwGetKey(window, keyCode) == GLFW.GLFW_PRESS;
            }
        }
        return false;
    }
}
