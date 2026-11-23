package org.pots.potsmod.config;

import lombok.Getter;
import lombok.Setter;
import org.pots.potsmod.config.choices.GuiAnimationMode;
import org.pots.potsmod.config.choices.RadianceOverlayMode;
import org.pots.potsmod.config.choices.ShoutReplacement;
import org.pots.potsmod.features.ConsuTextFeature;
import org.pots.potsmod.gui.theme.FontOption;
import org.pots.potsmod.gui.theme.ThemeOption;
import org.pots.potsmod.util.KeyUtil;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class PotsmodConfigData {
    private static final String DEFAULT_RADIANCE_SYNC_WORKER_URL = "https://radiancesync.wavelink.workers.dev";
    private String apiBase = "https://api.wynncraft.com/v3/";

    private boolean autoUpdate = true;
    private String lastNotifiedVersion = "";

    private String clickGuiTheme = ThemeOption.DEFAULT.getKey();
    private String clickGuiFont = FontOption.MINECRAFT_DEFAULT.getKey();
    private GuiAnimationMode clickGuiAnimation = GuiAnimationMode.NONE;
    private int animationTime = 1000;
    private float guiOpacity = 0.9f;
    private int customGuiBackground = 0x171A21;
    private int customGuiSecondary = 0x11141B;
    private int customGuiAccent = 0x4794FD;
    private int guiWidth = 500;
    private int guiHeight = 350;

    private boolean resourceTickOverlayEnabled = false;
    private boolean chatEncryptionFeatureEnabled = true;
    private boolean warTimersFeatureEnabled = true;
    private boolean warTowerEhpFeatureEnabled = false;
    private boolean consuTextFeatureEnabled = true;
    private boolean shoutFilterFeatureEnabled = false;
    private boolean viewModelFeatureEnabled = false;
    private boolean radianceSyncEnabled = false;
    private boolean territoryRouteFlowFeatureEnabled = true;
    private boolean defenseColorOverlayFeatureEnabled = true;
    private boolean customTerritoryMapEnabled = false;
    private boolean customEconomyMenuEnabled = false;
    private int territoryColorMode = 0;
    private boolean worldMapHudFeatureEnabled = true;
    private boolean inventoryCalculatorEnabled = true;

    private boolean guildWebhooksFeatureEnabled = true;
    private boolean guildBankWebhookEnabled = true;
    private String guildBankWebhookUrl = "";
    private boolean economyWebhookEnabled = true;
    private String economyWebhookUrl = "";

    private String quickAttackKey = KeyUtil.UNBOUND;
    private String economySandboxKey = KeyUtil.UNBOUND;
    private boolean newestWorldSwitchEnabled = false;
    private org.pots.potsmod.config.choices.ServerRegionChoice newestWorldRegion = org.pots.potsmod.config.choices.ServerRegionChoice.ALL;
    private String newestWorldSwitchKey = KeyUtil.UNBOUND;

    private String worldMapHudToggleKey = "key.keyboard.semicolon";
    private String worldMapHudZoomInKey = "key.keyboard.equal";
    private String worldMapHudZoomOutKey = "key.keyboard.minus";
    private int worldMapHudHoldThresholdMs = 250;
    private float pointerOpacity = 1.0f;
    private float defenseOverlayOpacity = 0.40f;
    private int defenseOverlayBorderThickness = 2;
    private int defenseColorVeryLow = 0x00AA00;
    private int defenseColorLow = 0x55FF55;
    private int defenseColorMedium = 0xFFFF55;
    private int defenseColorHigh = 0xFF5555;
    private int defenseColorVeryHigh = 0xAA0000;
    private int highlightColorConns = 0x00FFFF;
    private int highlightColorExternals = 0xFF00FF;

    private float terrainOpacity = 0.25f;
    private float territoryOpacity = 0.60f;
    private float borderOpacity = 0.60f;
    private boolean showBorder = true;
    private float defenseTintAlpha = 0.35f;
    private Set<String> defenseOverlayEnabledGuilds = new HashSet<>();

    private boolean hqToTerritoryRouteEnabled = true;
    private int hqToTerritoryLineColor = 0x1976FF;
    private boolean territoryToHqRouteEnabled = true;
    private int territoryToHqLineColor = 0xFF3B3B;
    private float territoryRouteLineWidth = 1.5f;
    private float territoryRouteGlowStrength = 0.46f;
    private float territoryRouteLightLength = 10.0f;
    private float territoryRouteLightSpacing = 24.0f;
    private float territoryRouteLightSpeed = 28.0f;

    private ShoutReplacement shoutFilterMode = ShoutReplacement.COLLAPSE;

    private String encryptionPrefix = "@";
    private String encryptionKey = "six seven";

    private int color = 0xFF0000;
    private int colorInside = 0xFFFFFF;
    private int notQColor = 0xFFFFFF;
    private int notQInsideColor = 0xFFFFFF;
    private int maximumDistance = 1000;
    private int maximumTerritories = 10;
    private int territoryWarningCount = 0;
    private int maxTimeTerr = 10;
    private int warnTime = 30;

    private float idScale = 0.7f;
    private int idXOffset = 1;
    private int idYOffset = 1;
    private String consumableTextureCategory = ConsuTextFeature.NO_TEXTURE_CATEGORY;

    private int streamCooldown = 5000;

    private int xOffset = 0;
    private int yOffset = 0;
    private int zOffset = 0;
    private int xRotation = 0;
    private int yRotation = 0;
    private int zRotation = 0;
    private float itemScale = 1.0f;
    private boolean disableHeldBobbing = true;

    private List<String> favouritePlayers = new ArrayList<>();
    private List<String> avoidedPlayers = new ArrayList<>();
    private List<String> ignoredPlayers = new ArrayList<>();

    private boolean radianceSyncRequireWar = true;
    private int radianceSyncSelfTier = 0;
    private float radianceSyncCastPromptSeconds = 2.0f;
    private RadianceOverlayMode radianceSyncOverlayMode = RadianceOverlayMode.CAST;
    private String radianceSyncWorkerUrl = DEFAULT_RADIANCE_SYNC_WORKER_URL;
    private boolean radianceSyncPingEnabled = false;
    private float radianceSyncPingVolume = 1.0f;
    private float radianceSyncPingPitch = 1.0f;
    private float radianceSyncOverlayScale = 1.0f;
    private int radianceSyncOverlayOffsetX = 0;
    private int radianceSyncOverlayOffsetY = -10;
    private String radianceSyncGroupKey = "";

    private int resTickOverlayOffsetX = 0;
    private int resTickOverlayOffsetY = 0;
    private float resTickOverlayScale = 1.0f;

    public void setEncryptionPrefix(String encryptionPrefix) {
        this.encryptionPrefix = encryptionPrefix == null || encryptionPrefix.isBlank() ? "@" : encryptionPrefix;
    }

    public void normalise() {
        if (apiBase == null || apiBase.isBlank()) {
            apiBase = "https://api.wynncraft.com/v3/";
        }

        if (favouritePlayers == null) {
            favouritePlayers = new ArrayList<>();
        }
        if (avoidedPlayers == null) {
            avoidedPlayers = new ArrayList<>();
        }
        if (ignoredPlayers == null) {
            ignoredPlayers = new ArrayList<>();
        }

        clickGuiTheme = ThemeOption.resolve(clickGuiTheme).getKey();
        clickGuiFont = FontOption.resolve(clickGuiFont).getKey();

        if (clickGuiAnimation == null) {
            clickGuiAnimation = GuiAnimationMode.NONE;
        }
        if (shoutFilterMode == null) {
            shoutFilterMode = ShoutReplacement.COLLAPSE;
        }
        if (defenseOverlayOpacity <= 0.0f || defenseOverlayOpacity > 1.0f) {
            defenseOverlayOpacity = 0.40f;
        }
        if (defenseOverlayBorderThickness < 1 || defenseOverlayBorderThickness > 10) {
            defenseOverlayBorderThickness = 2;
        }
        if (defenseColorVeryLow == 0 || defenseColorVeryLow == 0x32CD32) {
            defenseColorVeryLow = 0x00AA00;
        }
        if (defenseColorLow == 0 || defenseColorLow == 0x9ACD32) {
            defenseColorLow = 0x55FF55;
        }
        if (defenseColorMedium == 0 || defenseColorMedium == 0xFFD700) {
            defenseColorMedium = 0xFFFF55;
        }
        if (defenseColorHigh == 0 || defenseColorHigh == 0xFF0000) {
            defenseColorHigh = 0xFF5555;
        }
        if (defenseColorVeryHigh == 0 || defenseColorVeryHigh == 0xDC143C) {
            defenseColorVeryHigh = 0xAA0000;
        }
        if (encryptionPrefix == null || encryptionPrefix.isBlank()) {
            encryptionPrefix = "@";
        }
        if (encryptionKey == null) {
            encryptionKey = "";
        }
        if (guildBankWebhookUrl == null) {
            guildBankWebhookUrl = "";
        }
        if (economyWebhookUrl == null) {
            economyWebhookUrl = "";
        }

        animationTime = clamp(animationTime, 100, 2000);
        guiOpacity = clamp(guiOpacity, 0.1f, 1.0f);
        customGuiBackground &= 0xFFFFFF;
        customGuiSecondary &= 0xFFFFFF;
        customGuiAccent &= 0xFFFFFF;
        guiWidth = clamp(guiWidth, 320, 1000);
        guiHeight = clamp(guiHeight, 260, 800);

        color &= 0xFFFFFF;
        colorInside &= 0xFFFFFF;
        notQColor &= 0xFFFFFF;
        notQInsideColor &= 0xFFFFFF;
        maximumDistance = clamp(maximumDistance, 50, 5000);
        maximumTerritories = clamp(maximumTerritories, 1, 30);
        territoryWarningCount = clamp(territoryWarningCount, 0, 10);
        maxTimeTerr = clamp(maxTimeTerr, 1, 10);
        warnTime = clamp(warnTime, 10, 120);

        hqToTerritoryLineColor &= 0xFFFFFF;
        territoryToHqLineColor &= 0xFFFFFF;
        territoryRouteLineWidth = clamp(territoryRouteLineWidth, 0.5f, 4.0f);
        territoryRouteGlowStrength = clamp(territoryRouteGlowStrength, 0.0f, 1.0f);
        territoryRouteLightLength = clamp(territoryRouteLightLength, 1.0f, 32.0f);
        territoryRouteLightSpacing = clamp(territoryRouteLightSpacing, 8.0f, 80.0f);
        territoryRouteLightSpeed = clamp(territoryRouteLightSpeed, 0.0f, 100.0f);

        idScale = clamp(idScale, 0.25f, 2.5f);
        idXOffset = clamp(idXOffset, -16, 16);
        idYOffset = clamp(idYOffset, -16, 16);
        consumableTextureCategory = ConsuTextFeature.resolveTextureCategory(consumableTextureCategory);

        streamCooldown = clamp(streamCooldown, 100, 10000);

        xOffset = clamp(xOffset, -150, 150);
        yOffset = clamp(yOffset, -150, 150);
        zOffset = clamp(zOffset, -150, 50);
        xRotation = clamp(xRotation, -180, 180);
        yRotation = clamp(yRotation, -180, 180);
        zRotation = clamp(zRotation, -180, 180);
        itemScale = clamp(itemScale, 0.1f, 3.0f);

        if (radianceSyncOverlayMode == null) {
            radianceSyncOverlayMode = RadianceOverlayMode.CAST;
        }
        radianceSyncSelfTier = clamp(radianceSyncSelfTier, 0, 3);
        radianceSyncCastPromptSeconds = clamp(radianceSyncCastPromptSeconds, 0.5f, 10.0f);
        radianceSyncPingVolume = clamp(radianceSyncPingVolume, 0.1f, 2.0f);
        radianceSyncPingPitch = clamp(radianceSyncPingPitch, 0.5f, 2.0f);
        radianceSyncOverlayScale = clamp(radianceSyncOverlayScale, 0.5f, 3.0f);
        radianceSyncOverlayOffsetX = clamp(radianceSyncOverlayOffsetX, -2000, 2000);
        radianceSyncOverlayOffsetY = clamp(radianceSyncOverlayOffsetY, -2000, 2000);
        if (radianceSyncGroupKey == null) {
            radianceSyncGroupKey = "";
        }
        radianceSyncWorkerUrl = normaliseRadianceWorkerUrl(radianceSyncWorkerUrl);
        resTickOverlayScale = clamp(resTickOverlayScale, 0.5f, 3.0f);
        resTickOverlayOffsetX = clamp(resTickOverlayOffsetX, -2000, 2000);
        resTickOverlayOffsetY = clamp(resTickOverlayOffsetY, -2000, 2000);

        quickAttackKey = KeyUtil.normalizeKey(quickAttackKey, KeyUtil.UNBOUND);
        newestWorldSwitchKey = KeyUtil.normalizeKey(newestWorldSwitchKey, KeyUtil.UNBOUND);
        if (newestWorldRegion == null) {
            newestWorldRegion = org.pots.potsmod.config.choices.ServerRegionChoice.ALL;
        }

        worldMapHudToggleKey = KeyUtil.normalizeKey(worldMapHudToggleKey, "key.keyboard.semicolon");
        worldMapHudZoomInKey = KeyUtil.normalizeKey(worldMapHudZoomInKey, "key.keyboard.equal");
        worldMapHudZoomOutKey = KeyUtil.normalizeKey(worldMapHudZoomOutKey, "key.keyboard.minus");
        worldMapHudHoldThresholdMs = clamp(worldMapHudHoldThresholdMs, 50, 2000);
        pointerOpacity = clamp(pointerOpacity, 0.05f, 1.0f);
        terrainOpacity = clamp(terrainOpacity, 0.05f, 1.0f);
        territoryOpacity = clamp(territoryOpacity, 0.05f, 1.0f);
        borderOpacity = clamp(borderOpacity, 0.05f, 1.0f);
        defenseTintAlpha = clamp(defenseTintAlpha, 0.05f, 1.0f);
        if (defenseOverlayEnabledGuilds == null) {
            defenseOverlayEnabledGuilds = new HashSet<>();
        }
    }

    public Set<String> getDefenseOverlayEnabledGuilds() {
        if (defenseOverlayEnabledGuilds == null) {
            defenseOverlayEnabledGuilds = new HashSet<>();
        }
        return defenseOverlayEnabledGuilds;
    }

    private int clamp(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    private float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) {
            return min;
        }
        return Math.clamp(value, min, max);
    }

    private String normaliseRadianceWorkerUrl(String workerUrl) {
        if (workerUrl == null || workerUrl.isBlank()) {
            return DEFAULT_RADIANCE_SYNC_WORKER_URL;
        }
        try {
            String trimmed = workerUrl.trim();
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host != null && ("https".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme))) {
                return trimmed;
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_RADIANCE_SYNC_WORKER_URL;
    }
}
