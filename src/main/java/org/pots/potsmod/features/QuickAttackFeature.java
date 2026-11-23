package org.pots.potsmod.features;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.util.KeyUtil;

public class QuickAttackFeature extends Feature {

    @Override
    public void init() {
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                ScreenKeyboardEvents.allowKeyPress(containerScreen).register((s, event) -> {
                    boolean handled = handleKeyPress(containerScreen, event);
                    return !handled;
                });
                ScreenMouseEvents.allowMouseClick(containerScreen).register((s, event) -> {
                    boolean handled = handleMouseClick(containerScreen, event);
                    return !handled;
                });
            }
        });
    }

    private boolean handleKeyPress(AbstractContainerScreen<?> screen, KeyEvent event) {
        String boundKey = PotsmodConfig.getData().getQuickAttackKey();
        if (boundKey == null || boundKey.isEmpty() || !KeyUtil.matches(boundKey, event)) {
            return false;
        }

        return triggerAttackClick(screen);
    }

    private boolean handleMouseClick(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
        String boundKey = PotsmodConfig.getData().getQuickAttackKey();
        if (boundKey == null || boundKey.isEmpty() || !KeyUtil.matchesMouse(boundKey, event.button())) {
            return false;
        }

        return triggerAttackClick(screen);
    }

    private boolean triggerAttackClick(AbstractContainerScreen<?> screen) {
        String title = ChatFormatting.stripFormatting(screen.getTitle().getString());
        if (title == null || !title.contains("Attacking:")) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        MultiPlayerGameMode gameMode = mc.gameMode;
        Player player = mc.player;
        AbstractContainerMenu menu = screen.getMenu();

        if (gameMode != null && player != null && menu != null) {
            for (Slot slot : menu.slots) {
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) {
                    continue;
                }
                String hoverName = ChatFormatting.stripFormatting(stack.getHoverName().getString());
                if (hoverName != null && hoverName.contains("Attack with your Guild's emeralds")) {
                    gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.PICKUP, player);
                    break;
                }
            }
        }

        return true;
    }
}
