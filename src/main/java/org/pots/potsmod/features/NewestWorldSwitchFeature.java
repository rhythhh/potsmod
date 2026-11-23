package org.pots.potsmod.features;

import com.wynntils.core.components.Models;
import com.wynntils.models.worlds.profile.ServerProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.config.choices.ServerRegionChoice;
import org.pots.potsmod.util.KeyUtil;

import java.util.List;

public class NewestWorldSwitchFeature extends Feature {

    private boolean wasKeyDown = false;

    @Override
    public void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                wasKeyDown = false;
                return;
            }

            if (!PotsmodConfig.getData().isNewestWorldSwitchEnabled()) {
                wasKeyDown = false;
                return;
            }

            String boundKey = PotsmodConfig.getData().getNewestWorldSwitchKey();
            if (boundKey == null || boundKey.isEmpty()) {
                wasKeyDown = false;
                return;
            }

            boolean isDown = KeyUtil.isDown(boundKey);
            if (isDown && !wasKeyDown) {
                wasKeyDown = true;
                if (client.screen == null) {
                    switchToNewestWorld(client);
                }
            } else if (!isDown) {
                wasKeyDown = false;
            }
        });
    }

    public static void switchToNewestWorld(Minecraft client) {
        if (client.player == null || client.getConnection() == null) return;

        ServerRegionChoice region = PotsmodConfig.getData().getNewestWorldRegion();
        if (region == null) region = ServerRegionChoice.ALL;

        String newestServer = findNewestServer(region);

        if (newestServer == null || newestServer.isEmpty()) {
            client.player.displayClientMessage(
                    Component.literal("[RMod] No online servers found for region: " + region.name())
                            .withStyle(ChatFormatting.RED),
                    false
            );
            return;
        }

        client.player.displayClientMessage(
                Component.literal("[RMod] Switching to newest world: " + newestServer + " (" + region.name() + ")...")
                        .withStyle(ChatFormatting.GREEN),
                false
        );

        client.getConnection().sendCommand("switch " + newestServer);
    }

    public static String findNewestServer(ServerRegionChoice region) {
        try {
            List<String> sortedServers = Models.ServerList.getServersSortedOnUptime();
            if (sortedServers == null || sortedServers.isEmpty()) {
                return null;
            }

            if (region == ServerRegionChoice.ALL) {
                return sortedServers.get(0);
            }

            String prefix = region.name();
            for (String serverName : sortedServers) {
                if (serverName != null && serverName.startsWith(prefix)) {
                    return serverName;
                }
            }
        } catch (Exception ignored) {}

        return null;
    }
}
