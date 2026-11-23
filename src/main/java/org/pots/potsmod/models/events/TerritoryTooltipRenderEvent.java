package org.pots.potsmod.models.events;

import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.wynntils.services.map.pois.TerritoryPoi;
import net.minecraft.client.gui.GuiGraphics;
import org.pots.potsmod.eventbus.EventInfo;
import org.pots.potsmod.eventbus.Preference;

@EventInfo(preference = Preference.CALLER)
public record TerritoryTooltipRenderEvent(
        GuiGraphics guiGraphics,
        int xOffset,
        int yOffset,
        TerritoryPoi territoryPoi,
        LocalFloatRef renderYOffset
) {
}
