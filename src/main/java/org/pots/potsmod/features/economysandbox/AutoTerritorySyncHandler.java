/*
 * Copyright © RMod 2026.
 * This file is released under LGPLv3. See LICENSE for full license details.
 */
package org.pots.potsmod.features.economysandbox;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class AutoTerritorySyncHandler {
    private static boolean active = false;
    private static boolean clickedTerritories = false;
    private static EconomySandboxState pendingState = null;
    private static Runnable onComplete = null;
    private static int ticksSinceOpen = 0;

    public static void init() {
        // Use a tick-based approach so the menu has time to receive inventory data from the server
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!active || clickedTerritories) return;
            if (!(client.screen instanceof AbstractContainerScreen<?> containerScreen)) return;

            String title = ChatFormatting.stripFormatting(containerScreen.getTitle().getString());
            if (title == null || !title.contains("Guild Management")) return;

            ticksSinceOpen++;
            // Wait at least 10 ticks (~0.5s) for the server to send slot data before clicking
            if (ticksSinceOpen < 10) return;

            AbstractContainerMenu menu = containerScreen.getMenu();
            if (menu == null || client.gameMode == null || client.player == null) return;

            int targetSlot = 14;
            if (targetSlot >= menu.slots.size()) return;

            Slot slot = menu.slots.get(targetSlot);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) return; // Not populated yet, keep waiting

            String name = ChatFormatting.stripFormatting(stack.getHoverName().getString());
            if (name != null && name.toLowerCase().contains("territor")) {
                clickedTerritories = true;
                ticksSinceOpen = 0;
                client.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.PICKUP, client.player);
            }
        });
    }

    public static void start(EconomySandboxState state, Runnable callback) {
        active = true;
        clickedTerritories = false;
        pendingState = state;
        onComplete = callback;
        ticksSinceOpen = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            mc.gui.getChat().addMessage(Component.literal("§6[potsmod] §7Syncing live guild territories from Wynncraft..."));
            mc.getConnection().sendCommand("gu manage");
        } else {
            stop();
        }
    }

    public static void onTerritoriesCaptured(int count) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (pendingState != null) {
                LiveTerritoryDataManager.loadIntoSandbox(pendingState);
            }
            if (onComplete != null) {
                onComplete.run();
            }
            // Close whatever screen is currently open (the territory management screen)
            if (mc.screen != null) {
                mc.setScreen(null);
            }
            mc.gui.getChat().addMessage(
                    Component.literal("§6[potsmod] §aSuccessfully loaded §e" + count + " §aguild territories with 100% accurate defenses, HQ & treasuries!")
            );
            stop();
            // Reopen the Economy Sandbox screen
            mc.execute(() -> mc.setScreen(new EconomySandboxScreen()));
        });
    }

    public static void stop() {
        active = false;
        clickedTerritories = false;
        pendingState = null;
        onComplete = null;
        ticksSinceOpen = 0;
    }

    public static boolean isActive() {
        return active;
    }
}
