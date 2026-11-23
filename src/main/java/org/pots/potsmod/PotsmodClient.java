package org.pots.potsmod;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.managers.*;
import org.slf4j.Logger;

public class PotsmodClient implements ClientModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitializeClient() {
        KeybindManager.init();
        PotsmodConfig.init();
        SchedulerManager.init();
        OverlayManager.init();
        TerritoryBaseManager.init();
        FeatureManager.init();
        CommandManager.init();
        QueuerTracker.init();
        BackendManager.init();
        org.pots.potsmod.features.economysandbox.AutoTerritorySyncHandler.init();
    }

    public static void beforeWynntilsInitFeatures() {
        FunctionManager.init();
    }
}
