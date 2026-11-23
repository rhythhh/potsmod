package org.pots.potsmod.features;

import com.wynntils.models.territories.TerritoryInfo;
import com.wynntils.services.map.pois.TerritoryPoi;
import org.pots.potsmod.PotsmodClient;
import org.pots.potsmod.eventbus.REventBus;
import org.pots.potsmod.eventbus.Subscribe;
import org.pots.potsmod.models.defense.DefenseEstimate;
import org.pots.potsmod.models.events.GuildMapResourcesUpdateEvent;
import org.pots.potsmod.models.events.TerritoryTooltipHeightEvent;
import org.pots.potsmod.models.events.TerritoryTooltipRenderEvent;
import org.pots.potsmod.render.Render2D;
import org.pots.potsmod.util.DefenseEstimateUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefenseEstimatesFeature extends Feature {
    private final Map<String, DefenseEstimate> estimateCache = new HashMap<>(16);

    private DefenseEstimate estimate(String territoryName, TerritoryInfo territoryInfo) {
        if (territoryInfo == null) {
            return DefenseEstimate.EMPTY;
        }
        DefenseEstimate cached = estimateCache.get(territoryName);
        if (cached != null) {
            return cached;
        }

        DefenseEstimate estimate = DefenseEstimateUtils.estimate(territoryName, territoryInfo);
        estimateCache.put(territoryName, estimate);
        return estimate;
    }

    @Subscribe
    public void renderTooltip(TerritoryTooltipRenderEvent event) {
        for (String line : tooltipLines(event.territoryPoi())) {
            Render2D.tooltipLine(event.guiGraphics(), event.xOffset(), event.renderYOffset(), line);
        }
    }

    @Subscribe
    public void increaseTooltipHeight(TerritoryTooltipHeightEvent event) {
        try {
            event.addHeight(tooltipLineCount(event.getTerritoryPoi()) * 10.0F);
        } catch (RuntimeException exception) {
            PotsmodClient.LOGGER.warn("Failed to calculate defense estimate tooltip height", exception);
        }
    }

    private List<String> tooltipLines(TerritoryPoi territoryPoi) {
        return DefenseEstimateUtils.tooltipLines(
                territoryPoi.getName(),
                estimate(territoryPoi.getName(), territoryPoi.getTerritoryInfo()));
    }

    private int tooltipLineCount(TerritoryPoi territoryPoi) {
        return tooltipLines(territoryPoi).size();
    }

    public void init() {
        estimateCache.clear();
        REventBus.subscribe(this);
    }

    public void clearCache() {
        estimateCache.clear();
    }

    @Subscribe
    public void onGuildMapResourcesUpdate(GuildMapResourcesUpdateEvent event) {
        this.clearCache();
    }
}
