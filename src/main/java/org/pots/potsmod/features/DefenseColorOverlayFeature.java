package org.pots.potsmod.features;

import com.wynntils.core.components.Models;
import com.wynntils.models.territories.TerritoryInfo;
import com.wynntils.models.territories.profile.TerritoryProfile;
import com.wynntils.models.territories.type.GuildResourceValues;
import com.wynntils.screens.maps.GuildMapScreen;
import com.wynntils.services.map.pois.TerritoryPoi;
import com.wynntils.utils.render.MapRenderer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.pots.potsmod.PotsmodClient;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.config.PotsmodConfigData;
import org.pots.potsmod.mixin.wynntils.AbstractMapScreenAccessor;
import org.pots.potsmod.util.TerritoryUtils;

import java.util.*;

/**
 * Defense color overlay & connection highlight feature for Wynntils' guild territory map.
 * Uses Fabric API screen events and AbstractMapScreenAccessor.
 */
public class DefenseColorOverlayFeature extends Feature {

    public enum ConnectionMode {
        OFF,
        CONNS,
        EXTERNALS;

        public ConnectionMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final long DOUBLE_CLICK_WINDOW_MS = 500L;

    // Small on-screen mode button dimensions
    private static final int BTN_X = 10;
    private static final int BTN_Y = 30;
    private static final int BTN_W = 96;
    private static final int BTN_H = 18;

    /** Set of guilds whose territories have defense overlay enabled (persistent across map opens). */
    private final Set<String> selectedGuilds = new HashSet<>();

    /** Name of the currently selected territory for connection highlights. */
    private String selectedTerritory = null;

    /** Current connection highlight mode (OFF -> CONNS -> EXTERNALS). */
    private ConnectionMode connectionMode = ConnectionMode.OFF;

    // Click tracking for double-click detection (same button + same guild within 250ms)
    private long lastClickTime;
    private int lastClickButton = -1;
    private String lastClickGuild;

    // Rate-limiting for render log line
    private long lastLogTime;

    @Override
    public void init() {
        selectedGuilds.clear();
        selectedGuilds.addAll(PotsmodConfig.getData().getDefenseOverlayEnabledGuilds());

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof GuildMapScreen guildMapScreen)) {
                return;
            }

            // Reset selection and click tracking on screen init (do NOT clear selectedGuilds!)
            selectedTerritory = null;
            connectionMode = ConnectionMode.OFF;
            resetClickTracking();

            // Register afterRender for drawing on top of the guild map
            ScreenEvents.afterRender(guildMapScreen).register((scr, guiGraphics, mouseX, mouseY, tickDelta) -> {
                if (isDisabled()) {
                    return;
                }
                renderOverlay(scr, guiGraphics, mouseX, mouseY);
            });

            // Register allowMouseClick to hit-test and consume button clicks
            ScreenMouseEvents.allowMouseClick(guildMapScreen).register((scr, event) -> {
                if (isDisabled()) {
                    return true;
                }
                if (event.button() == 0 && isOverButton(event.x(), event.y())) {
                    connectionMode = connectionMode.next();
                    logHighlightChange();
                    return false; // Consume click!
                }
                return true; // Allow normal click
            });

            // Register beforeMouseClick for territory selection and double-click handling
            ScreenMouseEvents.beforeMouseClick(guildMapScreen).register((scr, event) -> {
                if (isDisabled()) {
                    return;
                }
                if (isOverButton(event.x(), event.y())) {
                    return;
                }
                onMouseClick(scr, event);
            });
        });
    }

    private boolean isOverButton(double mouseX, double mouseY) {
        return mouseX >= BTN_X && mouseX <= BTN_X + BTN_W && mouseY >= BTN_Y && mouseY <= BTN_Y + BTN_H;
    }

    private void resetClickTracking() {
        lastClickTime = 0L;
        lastClickButton = -1;
        lastClickGuild = null;
    }

    private void onMouseClick(Screen screen, MouseButtonEvent event) {
        if (!((Object) screen instanceof AbstractMapScreenAccessor accessor)) {
            return;
        }

        int button = event.button();
        if (button != 0 && button != 1) {
            return;
        }

        if (!(accessor.getHovered() instanceof TerritoryPoi poi)) {
            return;
        }

        TerritoryInfo info = poi.getTerritoryInfo();
        String guildName = info != null ? info.getGuildName() : null;

        // Single left-click selection for connection highlights
        if (button == 0) {
            String name = poi.getName();
            if (name != null) {
                selectedTerritory = name;
                logHighlightChange();
            }
        }

        // Double click detection for enabling/disabling guild defense overlay
        if (guildName == null || guildName.equalsIgnoreCase("None")) {
            return; // Ignore unowned territories
        }

        long now = System.currentTimeMillis();

        if (now - lastClickTime <= DOUBLE_CLICK_WINDOW_MS
                && button == lastClickButton
                && Objects.equals(guildName, lastClickGuild)) {
            // Double click detected
            if (button == 0) {
                if (selectedGuilds.contains(guildName)) {
                    selectedGuilds.remove(guildName);
                } else {
                    selectedGuilds.add(guildName);
                }
            } else if (button == 1) {
                selectedGuilds.remove(guildName);
            }
            PotsmodConfig.getData().setDefenseOverlayEnabledGuilds(new HashSet<>(selectedGuilds));
            PotsmodConfig.save();
            resetClickTracking();
        } else {
            lastClickTime = now;
            lastClickButton = button;
            lastClickGuild = guildName;
        }
    }

    private void logHighlightChange() {
        PotsmodClient.LOGGER.info("Highlight change: sel='{}' mode={}", selectedTerritory, connectionMode);
    }

    /**
     * Refactored defense tint drawing used by both the real map screen and the HUD overlay.
     */
    public void renderDefenseTints(
            GuiGraphics guiGraphics,
            float mapCenterX,
            float mapCenterZ,
            float centerX,
            float centerZ,
            float zoomRenderScale,
            float opacity) {
        if (isDisabled()) {
            return;
        }

        boolean allTerritoriesMode = org.pots.potsmod.features.territorymap.WynntilsMapState.getColorMode() == 1;
        if (!allTerritoriesMode && selectedGuilds.isEmpty()) {
            return;
        }

        List<TerritoryPoi> pois = Models.Territory.getTerritoryPoisFromAdvancement();
        if (pois == null || pois.isEmpty()) {
            return;
        }

        PotsmodConfigData config = PotsmodConfig.getData();
        int alpha = Math.clamp(Math.round(opacity * 255.0f), 0, 255);
        int borderThickness = config.getDefenseOverlayBorderThickness();

        for (TerritoryPoi poi : pois) {
            TerritoryProfile profile = poi.getTerritoryProfile();
            if (profile == null) {
                continue;
            }

            TerritoryInfo info = poi.getTerritoryInfo();
            String guildName = info != null ? info.getGuildName() : null;
            if (!allTerritoriesMode) {
                if (guildName == null || !selectedGuilds.contains(guildName)) {
                    continue;
                }
            }

            GuildResourceValues defences = info != null ? info.getDefences() : null;
            if (defences == null || defences == GuildResourceValues.NONE) {
                defences = GuildResourceValues.VERY_LOW;
            }

            float renderX = MapRenderer.getRenderX(poi, mapCenterX, centerX, zoomRenderScale);
            float renderZ = MapRenderer.getRenderZ(poi, mapCenterZ, centerZ, zoomRenderScale);

            int startX = profile.getStartX();
            int startZ = profile.getStartZ();
            int endX   = profile.getEndX();
            int endZ   = profile.getEndZ();

            float halfW = Math.abs(endX - startX) * zoomRenderScale / 2.0f;
            float halfH = Math.abs(endZ - startZ) * zoomRenderScale / 2.0f;

            int x1 = Math.round(renderX - halfW);
            int z1 = Math.round(renderZ - halfH);
            int x2 = Math.round(renderX + halfW);
            int z2 = Math.round(renderZ + halfH);

            if (x2 < 0 || z2 < 0 || x1 > guiGraphics.guiWidth() || z1 > guiGraphics.guiHeight()) {
                continue;
            }

            int rgb = defenseRgb(config, defences);
            int fillColor = (alpha << 24) | (rgb & 0xFFFFFF);
            int borderColor = (0xFF << 24) | (rgb & 0xFFFFFF);
            drawBox(guiGraphics, x1, z1, x2, z2, fillColor, borderColor, borderThickness);
        }
    }

    private void renderOverlay(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!((Object) screen instanceof AbstractMapScreenAccessor accessor)) {
            return;
        }

        List<TerritoryPoi> pois = Models.Territory.getTerritoryPoisFromAdvancement();
        if (pois == null || pois.isEmpty()) {
            return;
        }

        // Required diagnostic log line
        long now = System.currentTimeMillis();
        if (now - lastLogTime >= 1000L) {
            lastLogTime = now;
            long withInfoCount = pois.stream().filter(p -> p.getTerritoryInfo() != null).count();
            PotsmodClient.LOGGER.info("overlay render: selected={} withInfo={}/{}", selectedGuilds, withInfoCount, pois.size());
        }

        PotsmodConfigData config = PotsmodConfig.getData();
        float mapCenterX = accessor.getMapCenterX();
        float mapCenterZ = accessor.getMapCenterZ();
        float centerX = accessor.getCenterX();
        float centerZ = accessor.getCenterZ();
        float zoomRenderScale = accessor.getZoomRenderScale();

        // 1. Draw Defense Tints for selected guilds
        renderDefenseTints(guiGraphics, mapCenterX, mapCenterZ, centerX, centerZ, zoomRenderScale, config.getDefenseOverlayOpacity());

        // 2. Draw Connection Highlights & Selection Outline (for real map screen)
        renderConnectionHighlights(guiGraphics, pois, mapCenterX, mapCenterZ, centerX, centerZ, zoomRenderScale, config);

        // 3. Draw mode toggle button on top
        renderButton(guiGraphics, mouseX, mouseY);
    }

    private void renderConnectionHighlights(
            GuiGraphics guiGraphics,
            List<TerritoryPoi> pois,
            float mapCenterX,
            float mapCenterZ,
            float centerX,
            float centerZ,
            float zoomRenderScale,
            PotsmodConfigData config) {
        if (selectedTerritory == null) {
            return;
        }

        Set<String> connectedTerritories = Collections.emptySet();
        if (connectionMode == ConnectionMode.CONNS) {
            connectedTerritories = TerritoryUtils.reach(selectedTerritory, 1);
        } else if (connectionMode == ConnectionMode.EXTERNALS) {
            connectedTerritories = TerritoryUtils.reach(selectedTerritory, 3);
        }

        int activeColor = connectionMode == ConnectionMode.CONNS ? config.getHighlightColorConns() : config.getHighlightColorExternals();
        int connFillColor = (0x80 << 24) | (activeColor & 0xFFFFFF);
        int connBorderColor = (0xFF << 24) | (activeColor & 0xFFFFFF);
        int borderThickness = config.getDefenseOverlayBorderThickness();

        for (TerritoryPoi poi : pois) {
            TerritoryProfile profile = poi.getTerritoryProfile();
            if (profile == null) {
                continue;
            }

            String territoryName = poi.getName();
            boolean isConnected = connectedTerritories.contains(territoryName);
            boolean isSelected = Objects.equals(territoryName, selectedTerritory);

            if (!isConnected && !isSelected) {
                continue;
            }

            float renderX = MapRenderer.getRenderX(poi, mapCenterX, centerX, zoomRenderScale);
            float renderZ = MapRenderer.getRenderZ(poi, mapCenterZ, centerZ, zoomRenderScale);

            int startX = profile.getStartX();
            int startZ = profile.getStartZ();
            int endX   = profile.getEndX();
            int endZ   = profile.getEndZ();

            float halfW = Math.abs(endX - startX) * zoomRenderScale / 2.0f;
            float halfH = Math.abs(endZ - startZ) * zoomRenderScale / 2.0f;

            int x1 = Math.round(renderX - halfW);
            int z1 = Math.round(renderZ - halfH);
            int x2 = Math.round(renderX + halfW);
            int z2 = Math.round(renderZ + halfH);

            if (x2 < 0 || z2 < 0 || x1 > guiGraphics.guiWidth() || z1 > guiGraphics.guiHeight()) {
                continue;
            }

            if (isConnected) {
                drawBox(guiGraphics, x1, z1, x2, z2, connFillColor, connBorderColor, Math.max(1, borderThickness));
            }
            if (isSelected) {
                drawOutline(guiGraphics, x1, z1, x2, z2, 0xFFFFFFFF, Math.max(2, borderThickness));
            }
        }
    }

    private void renderButton(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean hovered = isOverButton(mouseX, mouseY);
        int bgColor = hovered ? 0xDD222222 : 0xAA000000;
        int borderColor = hovered ? 0xFFFFFFFF : 0xFF777777;

        guiGraphics.fill(BTN_X, BTN_Y, BTN_X + BTN_W, BTN_Y + BTN_H, bgColor);
        // Border
        guiGraphics.fill(BTN_X, BTN_Y, BTN_X + BTN_W, BTN_Y + 1, borderColor);
        guiGraphics.fill(BTN_X, BTN_Y + BTN_H - 1, BTN_X + BTN_W, BTN_Y + BTN_H, borderColor);
        guiGraphics.fill(BTN_X, BTN_Y, BTN_X + 1, BTN_Y + BTN_H, borderColor);
        guiGraphics.fill(BTN_X + BTN_W - 1, BTN_Y, BTN_X + BTN_W, BTN_Y + BTN_H, borderColor);

        Font font = Minecraft.getInstance().font;
        String text = "Highlight: " + connectionMode.name();
        PotsmodConfigData config = PotsmodConfig.getData();
        int activeColor = connectionMode == ConnectionMode.CONNS ? config.getHighlightColorConns() : config.getHighlightColorExternals();
        int textColor = connectionMode == ConnectionMode.OFF ? 0xFFAAAAAA
                : (0xFF << 24) | (activeColor & 0xFFFFFF);
        int textX = BTN_X + (BTN_W - font.width(text)) / 2;
        int textY = BTN_Y + (BTN_H - 8) / 2;
        guiGraphics.drawString(font, text, textX, textY, textColor, false);
    }

    private void drawBox(GuiGraphics g, int x1, int z1, int x2, int z2, int fillColor, int borderColor, int borderWidth) {
        g.fill(x1, z1, x2, z2, fillColor);
        if (borderWidth > 0) {
            drawOutline(g, x1, z1, x2, z2, borderColor, borderWidth);
        }
    }

    private void drawOutline(GuiGraphics g, int x1, int z1, int x2, int z2, int color, int thickness) {
        int t = Math.min(thickness, Math.min((x2 - x1) / 2, (z2 - z1) / 2));
        if (t <= 0) {
            return;
        }
        // Top
        g.fill(x1, z1, x2, z1 + t, color);
        // Bottom
        g.fill(x1, z2 - t, x2, z2, color);
        // Left
        g.fill(x1, z1 + t, x1 + t, z2 - t, color);
        // Right
        g.fill(x2 - t, z1 + t, x2, z2 - t, color);
    }

    private static int defenseRgb(PotsmodConfigData config, GuildResourceValues defences) {
        if (defences == null) {
            return 0;
        }
        if (defences == GuildResourceValues.VERY_LOW) {
            return config.getDefenseColorVeryLow();
        }
        if (defences == GuildResourceValues.LOW) {
            return config.getDefenseColorLow();
        }
        if (defences == GuildResourceValues.MEDIUM) {
            return config.getDefenseColorMedium();
        }
        if (defences == GuildResourceValues.HIGH) {
            return config.getDefenseColorHigh();
        }
        if (defences == GuildResourceValues.VERY_HIGH) {
            return config.getDefenseColorVeryHigh();
        }
        return 0;
    }
}
