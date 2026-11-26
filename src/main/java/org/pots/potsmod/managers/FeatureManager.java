package org.pots.potsmod.managers;

import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.features.*;
import org.pots.potsmod.features.economysandbox.EconomySandboxFeature;

import java.util.List;

@SuppressWarnings("unused")
@UtilityClass
public class FeatureManager {
    @Getter
    private static ResourceTickFeature resTickFeature;
    @Getter
    private static ChatEncryptionFeature chatEncryptionFeature;
    @Getter
    private static WarTimersFeature warTimersFeature;
    @Getter
    private static WarTowerEHPFeature warTowerEHPFeature;
    @Getter
    private static ConsuTextFeature consuTextFeature;
    @Getter
    private static ShoutFilterFeature shoutFilterFeature;
    @Getter
    private static ViewModelTransformationFeature viewModelTransformationFeature;
    @Getter
    private static RadianceSyncFeature radianceSyncFeature;
    @Getter
    private static AutoStreamFeature autoStreamFeature;
    @Getter
    private static IgnoreFeature ignoreFeature;
    @Getter
    private static DefenseEstimatesFeature defenseEstimatesFeature;
    @Getter
    private static TerritoryRouteFlowFeature territoryRouteFlowFeature;
    @Getter
    private static TerritoryApiFeature territoryApiFeature;
    @Getter
    private static DefenseColorOverlayFeature defenseColorOverlayFeature;
    @Getter
    private static WorldMapHudFeature worldMapHudFeature;
    @Getter
    private static InventoryCalculatorFeature inventoryCalculatorFeature;
    @Getter
    private static EconomySandboxFeature economySandboxFeature;
    @Getter
    private static GuildWebhookFeature guildWebhookFeature;
    @Getter
    private static NewestWorldSwitchFeature newestWorldSwitchFeature;

    public static void init() {
        resTickFeature = new ResourceTickFeature();
        chatEncryptionFeature = new ChatEncryptionFeature();
        warTimersFeature = new WarTimersFeature();
        warTowerEHPFeature = new WarTowerEHPFeature();
        consuTextFeature = new ConsuTextFeature();
        shoutFilterFeature = new ShoutFilterFeature();
        viewModelTransformationFeature = new ViewModelTransformationFeature();
        radianceSyncFeature = new RadianceSyncFeature();
        autoStreamFeature = new AutoStreamFeature();
        ignoreFeature = new IgnoreFeature();
        defenseEstimatesFeature = new DefenseEstimatesFeature();
        territoryRouteFlowFeature = new TerritoryRouteFlowFeature();
        territoryApiFeature = new TerritoryApiFeature();
        defenseColorOverlayFeature = new DefenseColorOverlayFeature();
        worldMapHudFeature = new WorldMapHudFeature();
        inventoryCalculatorFeature = new InventoryCalculatorFeature();
        economySandboxFeature = new EconomySandboxFeature();
        guildWebhookFeature = new GuildWebhookFeature();
        newestWorldSwitchFeature = new NewestWorldSwitchFeature();

        for (Feature feature : List.of(
                resTickFeature,
                chatEncryptionFeature,
                warTimersFeature,
                warTowerEHPFeature,
                consuTextFeature,
                shoutFilterFeature,
                viewModelTransformationFeature,
                radianceSyncFeature,
                autoStreamFeature,
                ignoreFeature,
                defenseEstimatesFeature,
                territoryRouteFlowFeature,
                territoryApiFeature,
                defenseColorOverlayFeature,
                worldMapHudFeature,
                inventoryCalculatorFeature,
                economySandboxFeature,
                guildWebhookFeature,
                newestWorldSwitchFeature
        )) {
            feature.runSafe("init", feature::init);
        }

        PotsmodConfig.onFeaturesInitialized();
    }
}
