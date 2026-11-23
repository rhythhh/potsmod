package org.pots.potsmod.functions;

import com.wynntils.core.components.Models;
import com.wynntils.core.consumers.functions.arguments.FunctionArguments;
import org.pots.potsmod.managers.FeatureManager;
import org.pots.potsmod.util.TerritoryUtils;

public final class WarFunctions {

    public static class ResTickFunction extends RFunction<Integer> {
        @Override
        public Integer getValue(FunctionArguments arguments) {
            return FeatureManager.getResTickFeature().getSecondsUntilResTick();
        }
    }

    public static class HqTimerFunction extends RFunction<String> {
        @Override
        public String getValue(FunctionArguments arguments) {
            var hq = TerritoryUtils.getHQ(Models.Guild.getGuildName());
            if (hq.isEmpty())
                return "";
            var poi = Models.Territory.getTerritoryPoiFromAdvancement(hq.get());
            return poi.getTerritoryProfile().getReadableRelativeTimeAcquired();
        }
    }
}
