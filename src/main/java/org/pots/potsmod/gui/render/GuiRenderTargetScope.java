package org.pots.potsmod.gui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;

public record GuiRenderTargetScope(RenderTarget previous) implements AutoCloseable {
    @Override
    public void close() {
        GuiRenderTargetOverride.restore(previous);
    }
}
