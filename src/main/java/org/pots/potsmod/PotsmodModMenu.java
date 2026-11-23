package org.pots.potsmod;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.pots.potsmod.config.PotsmodConfig;

public class PotsmodModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return PotsmodConfig::getConfigScreen;
    }
}
