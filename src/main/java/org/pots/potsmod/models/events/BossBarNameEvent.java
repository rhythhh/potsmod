package org.pots.potsmod.models.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.chat.Component;
import org.pots.potsmod.eventbus.EventInfo;
import org.pots.potsmod.eventbus.Preference;

@Getter
@Setter
@AllArgsConstructor
@EventInfo(preference = Preference.CALLER)
public class BossBarNameEvent {
    private Component title;
}
