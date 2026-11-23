package org.pots.potsmod.features;

import com.wynntils.utils.type.Pair;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.config.PotsmodConfigData;
import org.pots.potsmod.eventbus.REventBus;
import org.pots.potsmod.eventbus.Subscribe;
import org.pots.potsmod.models.events.GuildMapUpdateEvent;
import org.pots.potsmod.models.events.HoveredTerritoryInfoRenderEvent;
import org.pots.potsmod.render.TerritoryRouteRenderer;
import org.pots.potsmod.render.TerritoryRouteStyle;
import org.pots.potsmod.util.TerritoryUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TerritoryRouteFlowFeature extends Feature {
    private final Map<String, Pair<List<String>, List<String>>> routeCache = new HashMap<>();
    private final TerritoryRouteRenderer renderer = new TerritoryRouteRenderer();

    @Override
    public void init() {
        routeCache.clear();
        REventBus.subscribe(this);
    }

    @Subscribe
    public void renderRoutes(HoveredTerritoryInfoRenderEvent event) {
        PotsmodConfigData config = PotsmodConfig.getData();
        boolean headquartersToTerritoryEnabled = config.isHqToTerritoryRouteEnabled();
        boolean territoryToHeadquartersEnabled = config.isTerritoryToHqRouteEnabled();
        if (!headquartersToTerritoryEnabled && !territoryToHeadquartersEnabled) {
            return;
        }

        String territoryName = event.territoryPoi().getName();
        Pair<List<String>, List<String>> routes = routeCache.computeIfAbsent(
                territoryName,
                TerritoryUtils::getResPaths
        );
        TerritoryRouteStyle headquartersToTerritory = routeStyle(config, headquartersToTerritoryEnabled, config.getHqToTerritoryLineColor());
        TerritoryRouteStyle territoryToHeadquarters = routeStyle(config, territoryToHeadquartersEnabled, config.getTerritoryToHqLineColor());
        renderer.render(
                event,
                territoryName,
                routes.a(),
                headquartersToTerritory,
                routes.b(),
                territoryToHeadquarters
        );
    }

    @Subscribe
    public void onGuildMapUpdate(GuildMapUpdateEvent event) {
        routeCache.clear();
    }

    private TerritoryRouteStyle routeStyle(PotsmodConfigData config, boolean enabled, int color) {
        return new TerritoryRouteStyle(
                enabled,
                color,
                config.getTerritoryRouteLineWidth(),
                config.getTerritoryRouteGlowStrength(),
                config.getTerritoryRouteLightLength(),
                config.getTerritoryRouteLightSpacing(),
                config.getTerritoryRouteLightSpeed()
        );
    }
}
