package org.pots.potsmod.utils;

import net.minecraft.network.chat.Component;
import org.pots.potsmod.chat.PlayerNameResolver;

public final class PlayerResolver {
    private PlayerResolver() {}

    public static void learnFromComponent(Component component) {
        PlayerNameResolver.observeMessage(component);
    }

    public static String resolveRealName(Component component, String rawName) {
        return PlayerNameResolver.resolve(component, rawName);
    }
}
