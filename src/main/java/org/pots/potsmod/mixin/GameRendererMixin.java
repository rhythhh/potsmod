package org.pots.potsmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix4f;
import org.pots.potsmod.eventbus.REventBus;
import org.pots.potsmod.gui.screen.ConfigScreen;
import org.pots.potsmod.models.events.HeldItemBobbingEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Final
    @Shadow
    public ItemInHandRenderer itemInHandRenderer;

    @Final
    @Shadow
    private Minecraft minecraft;

    @Unique
    private boolean rmod$skipVanillaHandRender = false;

    @Inject(
            method = "renderItemInHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void rmod$onBeforeBobView(
            float f,
            boolean bl,
            Matrix4f matrix4f,
            CallbackInfo ci,
            @Local PoseStack poseStack
    ) {
        rmod$skipVanillaHandRender = false;

        HeldItemBobbingEvent event = new HeldItemBobbingEvent(
                this.minecraft,
                this.itemInHandRenderer,
                f,
                bl,
                poseStack
        );

        REventBus.dispatch(event, canceled -> rmod$skipVanillaHandRender = true);
    }

    @WrapOperation(
            method = "renderItemInHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V"
            )
    )
    private void rmod$skipRender(
            ItemInHandRenderer instance,
            float f,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            LocalPlayer localPlayer,
            int i,
            Operation<Void> original
    ) {
        if (rmod$skipVanillaHandRender) {
            rmod$skipVanillaHandRender = false;
            return;
        }
        original.call(instance, f, poseStack, submitNodeCollector, localPlayer, i);
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void rmod$prepareClickGuiAnimation(
            DeltaTracker deltaTracker,
            boolean bl,
            CallbackInfo ci,
            @Local GuiGraphics guiGraphics
    ) {
        if (this.minecraft.screen instanceof ConfigScreen configScreen && configScreen.shouldPreparePortalSnapshot()) {
            configScreen.preparePortalSnapshotOffscreen();
            configScreen.renderPortalTransition(guiGraphics);
        }
    }
}
