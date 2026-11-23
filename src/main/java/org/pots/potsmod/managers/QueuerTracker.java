package org.pots.potsmod.managers;

import com.wynntils.core.components.Models;
import com.wynntils.models.territories.TerritoryAttackTimer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import org.pots.potsmod.eventbus.REventBus;
import org.pots.potsmod.eventbus.Subscribe;
import org.pots.potsmod.models.events.ChatMessageReceivedEvent;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class QueuerTracker {
    private static final Pattern DEFENSE_MSG_PATTERN =
            Pattern.compile("^\\s*([A-Za-z0-9_]{3,16})(?:/\\S+)?:\\s*(.+?) defense is (.+)$");

    private static final Map<String, QueuerEntry> queuers = new ConcurrentHashMap<>();

    private record QueuerEntry(String sender, long addedAt) {
    }

    public static void init() {
        REventBus.subscribe(new Object() {
            @Subscribe
            public void onChat(ChatMessageReceivedEvent event) {
                if (event.message() != null) {
                    processMessage(event.message().getString());
                }
            }
        });

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (message != null) {
                processMessage(message.getString());
            }
            return true;
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (message != null) {
                processMessage(message.getString());
            }
        });

        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (message != null) {
                processMessage(message.getString());
            }
            return true;
        });

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (message != null) {
                processMessage(message.getString());
            }
        });

        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> clear());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static void processMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String clean = ChatFormatting.stripFormatting(raw);
        clean = clean.replaceAll("[\\uE000-\\uF8FF\\r\\n\\p{Cntrl}]", "");
        clean = clean.replaceAll("\\s+", " ").trim();
        Matcher m = DEFENSE_MSG_PATTERN.matcher(clean);
        if (m.matches()) {
            String ign = m.group(1);
            String territory = m.group(2).trim();
            queuers.put(territory, new QueuerEntry(ign, System.currentTimeMillis()));
        }
    }

    public static String getQueuer(String territory) {
        if (territory == null || queuers.isEmpty()) {
            return null;
        }
        cleanup();
        QueuerEntry entry = queuers.get(territory.trim());
        return entry != null ? entry.sender() : null;
    }

    public static void cleanup() {
        if (queuers.isEmpty()) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            Set<String> activeTerritories = Models.GuildAttackTimer.getAttackTimers().stream()
                    .filter(t -> t.timerEnd() > now)
                    .map(TerritoryAttackTimer::territoryName)
                    .collect(Collectors.toSet());

            queuers.entrySet().removeIf(entry -> {
                String territory = entry.getKey();
                Optional<TerritoryAttackTimer> timerOpt = Models.GuildAttackTimer.getAttackTimerForTerritory(territory);
                if (timerOpt.isPresent()) {
                    // If timer has ended, remove
                    if (timerOpt.get().timerEnd() <= now) {
                        return true;
                    }
                    return false;
                }
                // If not in active timers and past initial 15s grace period, remove
                return !activeTerritories.contains(territory) && (now - entry.getValue().addedAt) > 15_000L;
            });
        } catch (Exception ignored) {
        }
    }

    public static void clear() {
        queuers.clear();
    }
}
