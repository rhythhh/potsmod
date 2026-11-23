package org.pots.potsmod.models.events;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.pots.potsmod.eventbus.EventInfo;
import org.pots.potsmod.eventbus.Preference;

@EventInfo(preference = Preference.CALLER)
public record SlotRenderEvent(GuiGraphics context, ItemStack stack, int slotX, int slotY) {
}
