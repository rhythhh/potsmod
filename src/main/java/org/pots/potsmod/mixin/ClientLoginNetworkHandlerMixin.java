package org.pots.potsmod.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import org.pots.potsmod.eventbus.REventBus;
import org.pots.potsmod.models.events.ServerJoinEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientHandshakePacketListenerImpl.class)
public class ClientLoginNetworkHandlerMixin {

    @Inject(method = "handleHello", at = @At("RETURN"))
    public void onHello(ClientboundHelloPacket clientboundHelloPacket, CallbackInfo ci, @Local String string) {
        REventBus.dispatch(new ServerJoinEvent(string));
    }

}