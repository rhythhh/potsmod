/*
 * Copyright © RMod 2026.
 * This file is released under LGPLv3. See LICENSE for full license details.
 */
package org.pots.potsmod.features.economysandbox;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.eventbus.REventBus;
import org.pots.potsmod.eventbus.Subscribe;
import org.pots.potsmod.features.Feature;
import org.pots.potsmod.models.events.ChatModifyEvent;
import org.pots.potsmod.util.KeyUtil;

public class EconomySandboxFeature extends Feature {
    private boolean wasKeyDown = false;

    @Override
    public void init() {
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (message != null && !overlay) {
                return EconomySandboxStorage.makeShareCodeClickable(message);
            }
            return message;
        });

        REventBus.subscribe(this);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                wasKeyDown = false;
                return;
            }

            String boundKey = PotsmodConfig.getData().getEconomySandboxKey();
            if (boundKey == null || boundKey.isEmpty()) {
                wasKeyDown = false;
                return;
            }

            boolean isDown = KeyUtil.isDown(boundKey);
            if (isDown && !wasKeyDown) {
                wasKeyDown = true;
                if (client.screen == null) {
                    client.setScreen(new EconomySandboxScreen());
                } else if (client.screen instanceof EconomySandboxScreen) {
                    client.setScreen(null);
                }
            } else if (!isDown) {
                wasKeyDown = false;
            }
        });
    }

    @Subscribe
    public void onChatModify(ChatModifyEvent event) {
        if (event.getMessage() != null) {
            event.setMessage(EconomySandboxStorage.makeShareCodeClickable(event.getMessage()));
        }
    }

    public void openScreen() {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new EconomySandboxScreen());
    }
}
