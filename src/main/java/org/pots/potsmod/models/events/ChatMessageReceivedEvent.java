package org.pots.potsmod.models.events;

import net.minecraft.network.chat.Component;
import org.pots.potsmod.eventbus.EventInfo;
import org.pots.potsmod.eventbus.Preference;

@EventInfo(preference = Preference.CALLER)
public record ChatMessageReceivedEvent(Component message) {
}
