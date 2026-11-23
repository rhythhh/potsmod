package org.pots.potsmod.models.events;

import org.pots.potsmod.eventbus.EventInfo;
import org.pots.potsmod.eventbus.Preference;

@EventInfo(preference = Preference.CALLER)
public record PostInitEvent() {
}
