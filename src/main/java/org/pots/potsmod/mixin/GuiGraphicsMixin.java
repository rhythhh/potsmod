package org.pots.potsmod.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.world.item.ItemStack;
import org.pots.potsmod.eventbus.REventBus;
import org.pots.potsmod.gui.render.GuiGraphicsScissorState;
import org.pots.potsmod.models.events.SlotRenderEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin implements GuiGraphicsScissorState {
    @Unique
    private final Deque<ScreenRectangle> rmod$scissorAreas = new ArrayDeque<>();

    @Inject(method = "enableScissor", at = @At("TAIL"))
    private void rmod$trackEnableScissor(int x1, int y1, int x2, int y2, CallbackInfo ci) {
        ScreenRectangle requested = new ScreenRectangle(
                x1,
                y1,
                Math.max(0, x2 - x1),
                Math.max(0, y2 - y1)
        );
        ScreenRectangle current = rmod$scissorAreas.peekLast();
        ScreenRectangle next = current == null ? requested : current.intersection(requested);
        rmod$scissorAreas.addLast(next == null ? ScreenRectangle.empty() : next);
    }

    @Inject(method = "disableScissor", at = @At("TAIL"))
    private void rmod$trackDisableScissor(CallbackInfo ci) {
        if (!rmod$scissorAreas.isEmpty()) {
            rmod$scissorAreas.removeLast();
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"))
    private void rmod$refreshConsumableModel(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        REventBus.dispatch(new SlotRenderEvent((GuiGraphics) (Object) this, stack, x, y));
    }

    @Override
    public ScreenRectangle rmod$getCurrentScissorArea() {
        return rmod$scissorAreas.peekLast();
    }
}
