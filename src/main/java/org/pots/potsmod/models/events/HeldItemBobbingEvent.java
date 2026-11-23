package org.pots.potsmod.models.events;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.pots.potsmod.eventbus.Cancelable;
import org.pots.potsmod.eventbus.EventInfo;
import org.pots.potsmod.eventbus.Preference;

@EventInfo(preference = Preference.CALLER)
public record HeldItemBobbingEvent(Minecraft minecraft, ItemInHandRenderer itemInHandRenderer, float tickProgress,
                                   boolean sleeping, PoseStack matrixStack) implements Cancelable {
}
