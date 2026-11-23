package org.pots.potsmod.config.setting;

import lombok.Getter;
import org.pots.potsmod.util.KeyUtil;

import java.util.function.Consumer;
import java.util.function.Supplier;

@Getter
public class KeybindSetting extends ConfigSetting<String> {

    public KeybindSetting(String id, String title, String description, Supplier<String> getter, Consumer<String> setter) {
        super(id, title, description, SettingKind.KEYBIND, getter, setter);
    }

    @Override
    public String format() {
        return KeyUtil.getDisplayName(get());
    }

    @Override
    public boolean tryParseAndSet(String rawValue) {
        set(KeyUtil.normalizeKey(rawValue, ""));
        return true;
    }
}
