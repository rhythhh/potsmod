package org.pots.potsmod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.pots.potsmod.eventbus.REventBus;
import org.pots.potsmod.features.territorymap.RevampedTerritoryMapScreen;
import org.pots.potsmod.models.events.PostInitEvent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

    @Inject(at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;gameThread:Ljava/lang/Thread;", shift = At.Shift.AFTER, ordinal = 0, opcode = Opcodes.PUTFIELD), method = "run")
    private void onStart(CallbackInfo ci) {
        REventBus.dispatch(new PostInitEvent());
    }

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen onSetScreen(Screen screen) {
        if (screen != null && screen.getClass().getName().equals("com.wynntils.screens.maps.GuildMapScreen")) {
            if (org.pots.potsmod.config.PotsmodConfig.isCustomTerritoryMapEnabled()) {
                return new RevampedTerritoryMapScreen();
            }
        }
        return screen;
    }
}
