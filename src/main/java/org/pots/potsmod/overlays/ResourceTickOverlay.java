package org.pots.potsmod.overlays;

import lombok.RequiredArgsConstructor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.models.render.TextOverlay;

import java.util.function.IntSupplier;

@RequiredArgsConstructor
public class ResourceTickOverlay implements TextOverlay {
    private final IntSupplier timeUntilResourceTick;

    @Override
    public String defaultValue() {
        return "-1";
    }

    @Override
    public void onHudRender(GuiGraphics drawContext, DeltaTracker tickCounter) {
        drawCenteredText(drawContext, Minecraft.getInstance(), timeUntilResourceTick.getAsInt() + "s", 0, 0, 0xFFFFFFFF);
    }

    @Override
    public int getXOffset() {
        return PotsmodConfig.getData().getResTickOverlayOffsetX();
    }

    @Override
    public void setXOffset(int xOffset) {
        PotsmodConfig.getData().setResTickOverlayOffsetX(xOffset);
    }

    @Override
    public int getYOffset() {
        return PotsmodConfig.getData().getResTickOverlayOffsetY();
    }

    @Override
    public void setYOffset(int yOffset) {
        PotsmodConfig.getData().setResTickOverlayOffsetY(yOffset);
    }

    @Override
    public float getScale() {
        return PotsmodConfig.getData().getResTickOverlayScale();
    }

    @Override
    public void setScale(float scale) {
        PotsmodConfig.getData().setResTickOverlayScale(scale);
    }

    @Override
    public boolean isEnabled() {
        return PotsmodConfig.getData().isResourceTickOverlayEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        PotsmodConfig.getData().setResourceTickOverlayEnabled(enabled);
    }
}
