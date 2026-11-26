package org.pots.potsmod.util;

import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

@UtilityClass
public class ModSounds {
    public static final Identifier MEOW_ID = Identifier.fromNamespaceAndPath("potsmod", "meow");
    public static final SoundEvent MEOW = SoundEvent.createVariableRangeEvent(MEOW_ID);

    public static void playClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    public static void playMeow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(new SimpleSoundInstance(
                    MEOW_ID,
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f,
                    RandomSource.create(),
                    false,
                    0,
                    net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE,
                    0.0,
                    0.0,
                    0.0,
                    true
            ));
        }
    }
}
