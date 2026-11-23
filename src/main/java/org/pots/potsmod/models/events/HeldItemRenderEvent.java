package org.pots.potsmod.models.events;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;
import org.pots.potsmod.eventbus.EventInfo;
import org.pots.potsmod.eventbus.Preference;

@EventInfo(preference = Preference.CALLER)
public record HeldItemRenderEvent(InteractionHand hand, PoseStack matrix) {
}
