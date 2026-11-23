package org.pots.potsmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.pots.potsmod.eventbus.REventBus;
import org.pots.potsmod.features.GuildWebhookFeature;
import org.pots.potsmod.models.events.ChatMessageReceivedEvent;
import org.pots.potsmod.models.events.ChatModifyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void edenBridge$captureRawSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        if (packet.overlay()) return;
        GuildWebhookFeature.onIncomingSystemChat(packet.content(), false);
    }

    @WrapOperation(method = "handleSystemChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/ChatListener;handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    public void wrap(ChatListener instance, Component component, boolean bl, Operation<Void> original) {
        if (bl) {
            original.call(instance, component, true);
            return;
        }
        ChatModifyEvent event = new ChatModifyEvent(component);
        REventBus.dispatch(event);
        Component modified = event.getMessage();
        if (modified == null) return;
        REventBus.dispatch(new ChatMessageReceivedEvent(modified));
        original.call(instance, modified, false);
    }
}
