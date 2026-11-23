package org.pots.potsmod.models.events;

import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ExecuteRunnableClickEvent {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("potsmod", "runnable");
    private static final Map<String, Runnable> ACTIONS = new ConcurrentHashMap<>();

    private ExecuteRunnableClickEvent() {}

    public static ClickEvent of(Runnable runnable) {
        String key = UUID.randomUUID().toString();
        ACTIONS.put(key, runnable);
        return new ClickEvent.Custom(ID, Optional.of(StringTag.valueOf(key)));
    }

    public static Runnable get(String key) {
        return ACTIONS.get(key);
    }
}
