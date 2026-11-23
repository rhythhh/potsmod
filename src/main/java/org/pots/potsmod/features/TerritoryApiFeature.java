package org.pots.potsmod.features;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wynntils.core.components.Models;
import com.wynntils.models.territories.TerritoryInfo;
import com.wynntils.models.territories.profile.TerritoryProfile;
import com.wynntils.services.map.pois.TerritoryPoi;
import lombok.Getter;
import org.pots.potsmod.PotsmodClient;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.eventbus.REventBus;
import org.pots.potsmod.eventbus.Subscribe;
import org.pots.potsmod.mixin.wynntils.TerritoryModelAccessor;
import org.pots.potsmod.models.events.GuildMapResourcesUpdateEvent;
import org.pots.potsmod.models.events.GuildMapUpdateEvent;
import org.pots.potsmod.models.events.WynntilsTerritoryApiUpdateEvent;
import org.pots.potsmod.util.TerritoryUtils;
import org.pots.potsmod.util.WebUtils;

import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

public class TerritoryApiFeature extends Feature {
    private int errors = 0;
    @Getter
    private long territoryLastTick = 0;

    @Subscribe
    public void UpdateTerritories(WynntilsTerritoryApiUpdateEvent event) {
        if (this.enabled) {
            WebUtils.queryAPIAsyncH(PotsmodConfig.getData().getApiBase() + "guild/list/territory")
                    .thenApply(this::parseTerritoryApi)
                    .exceptionally(ex -> {
                        PotsmodClient.LOGGER.error("Failed to fetch/parse territory API response", ex);
                        errors++;
                        if (errors >= 5) {
                            this.enabled = false;
                            PotsmodClient.LOGGER.error("Too many exceptions in Territory Api Feature, disabling.");
                        }
                        return null;
                    });
        }
    }

    private HttpResponse<String> parseTerritoryApi(HttpResponse<String> data) {
        JsonObject json = JsonParser.parseString(data.body()).getAsJsonObject();
        TerritoryModelAccessor territoryModelAccessor = (TerritoryModelAccessor) (Object) (Models.Territory);

        Map<String, TerritoryInfo> tempInfoMap = new HashMap<>();
        Map<String, TerritoryProfile> tempProfileMap = new HashMap<>();

        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
            JsonObject territoryObject = entry.getValue().getAsJsonObject();
            territoryObject.addProperty("name", entry.getKey());
            Gson territoryProfileGson = territoryModelAccessor.getTerritoryProfileGson();
            TerritoryProfile territoryProfile = territoryProfileGson.fromJson(territoryObject, TerritoryProfile.class);
            tempProfileMap.put(entry.getKey(), territoryProfile);

            TerritoryInfo territoryInfo = TerritoryUtils.territoryInfoFromJson(entry.getValue().getAsJsonObject());
            tempInfoMap.put(entry.getKey(), territoryInfo);
        }

        territoryModelAccessor.setTerritoryProfileMap(tempProfileMap);

        var territoryPoiMap = territoryModelAccessor.getTerritoryPoiMap();
        for (Map.Entry<String, TerritoryInfo> entry : tempInfoMap.entrySet()) {
            TerritoryProfile territoryProfile = Models.Territory.getTerritoryProfile(entry.getKey());
            if (territoryProfile != null) {
                territoryPoiMap.put(entry.getKey(), new TerritoryPoi(() -> Models.Territory.getTerritoryProfile(entry.getKey()), entry.getValue()));
            }
        }

        long newTerritoryLastTick = data.headers()
                .firstValue("territorylasttick")
                .map(value -> OffsetDateTime.parse(value.replace(' ', 'T')).toInstant().toEpochMilli())
                .orElseThrow(() -> new RuntimeException("Missing territorylasttick header"));
        if (newTerritoryLastTick != this.territoryLastTick) {
            this.territoryLastTick = newTerritoryLastTick;
            REventBus.dispatch(new GuildMapResourcesUpdateEvent());
        }

        REventBus.dispatch(new GuildMapUpdateEvent());

        errors = 0;
        return data;
    }

    @Override
    public void init() {
        REventBus.subscribe(this);
    }
}
