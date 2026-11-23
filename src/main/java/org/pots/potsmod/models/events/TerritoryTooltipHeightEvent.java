package org.pots.potsmod.models.events;

import com.wynntils.services.map.pois.TerritoryPoi;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.pots.potsmod.eventbus.EventInfo;
import org.pots.potsmod.eventbus.Preference;

@Getter
@RequiredArgsConstructor
@EventInfo(preference = Preference.CALLER)
public class TerritoryTooltipHeightEvent {
    private final TerritoryPoi territoryPoi;
    private float additionalHeight;

    public void addHeight(float height) {
        additionalHeight += height;
    }
}
