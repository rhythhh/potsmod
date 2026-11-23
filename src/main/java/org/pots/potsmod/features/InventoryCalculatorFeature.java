package org.pots.potsmod.features;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.util.CalcParser;

public class InventoryCalculatorFeature extends Feature {

    private static final int BOX_WIDTH  = 220;
    private static final int BOX_HEIGHT = 20;
    private static final int BOX_ABOVE  = 40; // px from screen bottom

    @Override
    public void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!PotsmodConfig.getData().isInventoryCalculatorEnabled()) return;
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;

            int boxX = scaledWidth  / 2 - BOX_WIDTH  / 2;
            int boxY = scaledHeight     - BOX_ABOVE;

            EditBox calcBox = new EditBox(
                    client.font,
                    boxX, boxY,
                    BOX_WIDTH, BOX_HEIGHT,
                    Component.literal("Calculator")
            );
            calcBox.setHint(Component.literal("Calculator: 2*3+1"));
            calcBox.setMaxLength(256);
            calcBox.setFocused(false);

            // Set screen focus to calcBox when clicked
            calcBox.setResponder(val -> {});

            // Intercept keyboard presses
            ScreenKeyboardEvents.allowKeyPress(containerScreen).register((scr, event) -> {
                if (!calcBox.isFocused()) return true;

                int key = event.key();
                // Esc -> unfocus only (don't close inventory)
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    calcBox.setFocused(false);
                    containerScreen.setFocused(null);
                    return false;
                }
                // Enter -> copy raw result to clipboard
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                    Double result = CalcParser.parse(calcBox.getValue());
                    if (result != null) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(CalcParser.formatRaw(result));
                    }
                    return false;
                }

                // Forward key press directly to the EditBox
                calcBox.keyPressed(event);
                // Return false to consume event so inventory hotkeys (E, 1-9, Q, etc.) never trigger
                return false;
            });

            // Intercept mouse clicks
            ScreenMouseEvents.allowMouseClick(containerScreen).register((scr, event) -> {
                double mx = event.x();
                double my = event.y();
                if (mx >= boxX && mx <= boxX + BOX_WIDTH && my >= boxY && my <= boxY + BOX_HEIGHT) {
                    calcBox.setFocused(true);
                    containerScreen.setFocused(calcBox);
                    calcBox.mouseClicked(event, false);
                    return false; // Consume click so slot behind isn't triggered
                }

                if (calcBox.isFocused()) {
                    calcBox.setFocused(false);
                    if (containerScreen.getFocused() == calcBox) {
                        containerScreen.setFocused(null);
                    }
                }
                return true;
            });

            // Render EditBox and live result
            ScreenEvents.afterRender(containerScreen).register((scr, guiGraphics, mouseX, mouseY, tickDelta) -> {
                if (!PotsmodConfig.getData().isInventoryCalculatorEnabled()) return;
                calcBox.render(guiGraphics, mouseX, mouseY, tickDelta);
                drawResult(guiGraphics, calcBox, boxX, boxY, scaledWidth, scaledHeight);
            });
        });
    }

    private void drawResult(GuiGraphics guiGraphics, EditBox calcBox, int boxX, int boxY, int screenW, int screenH) {
        String expr = calcBox.getValue();
        Minecraft mc = Minecraft.getInstance();

        String resultText;
        int color;
        if (expr == null || expr.isBlank()) {
            resultText = "= ?";
            color = 0xFFFF4444; // red
        } else {
            try {
                Double result = CalcParser.parse(expr);
                if (result == null) {
                    resultText = "= ?";
                    color = 0xFFFF4444;
                } else {
                    resultText = "= " + CalcParser.format(result);
                    color = 0xFFFFFFFF; // white
                }
            } catch (Exception e) {
                resultText = "= ?";
                color = 0xFFFF4444;
            }
        }

        int textWidth = mc.font.width(resultText);
        int textY = boxY + (BOX_HEIGHT - mc.font.lineHeight) / 2;

        int outsideX = boxX + BOX_WIDTH + 4;
        if (outsideX + textWidth <= screenW - 4) {
            guiGraphics.drawString(mc.font, resultText, outsideX, textY, color, true);
        } else {
            int textX = boxX + BOX_WIDTH - textWidth - 4;
            guiGraphics.drawString(mc.font, resultText, textX, textY, color, true);
        }
    }
}
