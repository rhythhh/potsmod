package org.pots.potsmod.features;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.models.bind.CustomBindEntry;
import org.pots.potsmod.util.KeyUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomBindsFeature extends Feature {
    private final Map<String, Boolean> keyPressStates = new HashMap<>();

    @Override
    public void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getConnection() == null) {
                keyPressStates.clear();
                return;
            }

            // Only trigger in-game when no GUI is open
            if (client.screen != null) {
                keyPressStates.clear();
                return;
            }

            List<CustomBindEntry> binds = PotsmodConfig.getData().getCustomBinds();
            if (binds == null || binds.isEmpty()) {
                keyPressStates.clear();
                return;
            }

            for (CustomBindEntry bind : binds) {
                String key = bind.getKey();
                if (key == null || key.isBlank() || key.equalsIgnoreCase(KeyUtil.UNBOUND)) {
                    continue;
                }

                String bindId = bind.getId();
                boolean isDown = KeyUtil.isDown(key);
                boolean wasDown = keyPressStates.getOrDefault(bindId, false);

                if (isDown && !wasDown) {
                    keyPressStates.put(bindId, true);
                    executeBind(client, bind);
                } else if (!isDown) {
                    keyPressStates.put(bindId, false);
                }
            }
        });
    }

    private void executeBind(Minecraft client, CustomBindEntry bind) {
        String cmd = bind.getCommand();
        if (cmd == null || cmd.isBlank()) {
            return;
        }

        // Clean command string: remove leading slashes and whitespace
        String trimmed = cmd.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1).trim();
        }

        if (trimmed.isEmpty()) {
            return;
        }

        if (client.getConnection() != null) {
            client.getConnection().sendCommand(trimmed);
        }
    }
}
