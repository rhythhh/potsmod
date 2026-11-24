/*
 * Copyright © RMod 2026.
 * This file is released under LGPLv3. See LICENSE for full license details.
 */
package org.pots.potsmod.features.territorymap;

import org.pots.potsmod.PotsmodClient;
import com.wynntils.core.components.Models;
import org.pots.potsmod.models.defense.DefenseEstimate;
import com.wynntils.models.territories.TerritoryInfo;
import com.wynntils.models.territories.profile.TerritoryProfile;
import com.wynntils.models.territories.type.GuildResource;
import com.wynntils.models.territories.type.GuildResourceValues;
import com.wynntils.models.territories.type.TerritoryUpgrade;
import com.wynntils.screens.maps.AbstractMapScreen;
import com.wynntils.screens.maps.widgets.MapButton;
import com.wynntils.services.map.pois.TerritoryPoi;
import com.wynntils.utils.colors.CommonColors;
import com.wynntils.utils.colors.CustomColor;
import com.wynntils.utils.render.MapRenderer;
import com.wynntils.utils.render.RenderUtils;
import com.wynntils.utils.render.Texture;
import com.wynntils.utils.render.type.PointerType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.pots.potsmod.managers.TerritoryBaseManager;
import org.pots.potsmod.models.records.TerritoryBase;
import org.pots.potsmod.models.records.TerritoryCombatStats;
import org.pots.potsmod.util.DefenseEstimateUtils;
import org.pots.potsmod.util.TerritoryUtils;

import java.util.*;
import java.util.Locale;

public class RevampedTerritoryMapScreen extends AbstractMapScreen {
    private static final Identifier RES_EMERALDS = Identifier.fromNamespaceAndPath("potsmod", "textures/resources/emeralds.png");
    private static final Identifier RES_ORE = Identifier.fromNamespaceAndPath("potsmod", "textures/resources/ore.png");
    private static final Identifier RES_WOOD = Identifier.fromNamespaceAndPath("potsmod", "textures/resources/wood.png");
    private static final Identifier RES_FISH = Identifier.fromNamespaceAndPath("potsmod", "textures/resources/fish.png");
    private static final Identifier RES_CROPS = Identifier.fromNamespaceAndPath("potsmod", "textures/resources/crops.png");

    private boolean showResourceColors = false;
    private boolean showTerritoryAbbreviation = false;

    // 0 = Guild Color, 1 = Defense Tier, 2 = Production Upgrades
    private int getColorMode() {
        return WynntilsMapState.getColorMode();
    }

    private GuildResourceValues defenseFilter = null;
    private GuildResourceValues treasuryFilter = null;

    private MapButton resourceColorButton;
    private MapButton territoryNamesButton;
    private MapButton defenseColorModeButton;
    private MapButton defenseFilterButton;
    private MapButton treasuryFilterButton;

    // Persistent view state across map closes
    private static float lastMapCenterX = Float.NaN;
    private static float lastMapCenterZ = Float.NaN;
    private static float lastZoomLevel = Float.NaN;

    // Smooth cursor-anchored zooming
    private float targetZoomLevel = -1.0f;
    private float targetMapCenterX = Float.NaN;
    private float targetMapCenterZ = Float.NaN;

    public RevampedTerritoryMapScreen() {
        super();
        this.firstInit = true;
        this.shouldCenterMap = Float.isNaN(lastMapCenterX);
    }

    @Override
    protected void doInit() {
        super.doInit();
        if (isPlayerInsideMainArea()) {
            centerMapAroundPlayer();
            if (!Float.isNaN(lastZoomLevel)) {
                setZoomLevel(lastZoomLevel);
            }
        } else if (!Float.isNaN(lastMapCenterX) && !Float.isNaN(lastMapCenterZ) && !Float.isNaN(lastZoomLevel)) {
            updateMapCenter(lastMapCenterX, lastMapCenterZ);
            setZoomLevel(lastZoomLevel);
        } else {
            centerMapOnWorld();
        }

        // Bottom-middle action buttons
        resourceColorButton = new MapButton(
                Texture.ADD_ICON,
                button -> {
                    showResourceColors = !showResourceColors;
                },
                List.of(
                        Component.literal("Toggle Resource Colors").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        Component.literal("Switch between guild owner colors and resource production colors").withStyle(ChatFormatting.GRAY)
                )
        );
        addMapButton(resourceColorButton);

        territoryNamesButton = new MapButton(
                Texture.SIGN_ICON,
                button -> {
                    showTerritoryAbbreviation = !showTerritoryAbbreviation;
                },
                List.of(
                        Component.literal("Toggle Territory Names").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        Component.literal("Switch between owning guild tag and territory name abbreviation").withStyle(ChatFormatting.GRAY)
                )
        );
        addMapButton(territoryNamesButton);

        defenseColorModeButton = new MapButton(
                Texture.DEFENSE_FILTER_ICON,
                button -> {
                    WynntilsMapState.setColorMode((getColorMode() + 1) % 2);
                    updateButtonTooltips();
                },
                getDefenseColorModeTooltip()
        );
        addMapButton(defenseColorModeButton);

        defenseFilterButton = new MapButton(
                Texture.DEFENSE_FILTER_ICON,
                button -> {
                    cycleDefenseFilter();
                    updateButtonTooltips();
                },
                getDefenseFilterTooltip()
        );
        addMapButton(defenseFilterButton);

        treasuryFilterButton = new MapButton(
                Texture.TREASURY,
                button -> {
                    cycleTreasuryFilter();
                    updateButtonTooltips();
                },
                getTreasuryFilterTooltip()
        );
        addMapButton(treasuryFilterButton);

        addMapButton(new MapButton(
                Texture.ARROW_LEFT_ICON,
                button -> {
                    onClose();
                },
                List.of(
                        Component.literal("Close Territory Map").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        Component.literal("Return to game / main map").withStyle(ChatFormatting.GRAY)
                )
        ));
    }

    private void cycleDefenseFilter() {
        if (defenseFilter == null) defenseFilter = GuildResourceValues.VERY_LOW;
        else if (defenseFilter == GuildResourceValues.VERY_LOW) defenseFilter = GuildResourceValues.LOW;
        else if (defenseFilter == GuildResourceValues.LOW) defenseFilter = GuildResourceValues.MEDIUM;
        else if (defenseFilter == GuildResourceValues.MEDIUM) defenseFilter = GuildResourceValues.HIGH;
        else if (defenseFilter == GuildResourceValues.HIGH) defenseFilter = GuildResourceValues.VERY_HIGH;
        else defenseFilter = null;
    }

    private void cycleTreasuryFilter() {
        if (treasuryFilter == null) treasuryFilter = GuildResourceValues.VERY_LOW;
        else if (treasuryFilter == GuildResourceValues.VERY_LOW) treasuryFilter = GuildResourceValues.LOW;
        else if (treasuryFilter == GuildResourceValues.LOW) treasuryFilter = GuildResourceValues.MEDIUM;
        else if (treasuryFilter == GuildResourceValues.MEDIUM) treasuryFilter = GuildResourceValues.HIGH;
        else if (treasuryFilter == GuildResourceValues.HIGH) treasuryFilter = GuildResourceValues.VERY_HIGH;
        else treasuryFilter = null;
    }

    private List<Component> getDefenseColorModeTooltip() {
        int cm = getColorMode();
        return List.of(
                Component.literal("Territory Color Mode: " + (cm == 1 ? "Defense Tier" : "Guild Color"))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal(cm == 1
                        ? "Territory boxes colored by Defense tier. Click for Guild Color mode."
                        : "Territory boxes colored by Owning Guild. Click for Defense Tier mode.")
                        .withStyle(ChatFormatting.GRAY)
        );
    }

    private List<Component> getDefenseFilterTooltip() {
        String filterName = defenseFilter != null ? defenseFilter.getAsString() : "All";
        return List.of(
                Component.literal("Defense Filter: " + filterName).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal("Click to cycle filter (All > Very Low > Low > Medium > High > Very High)").withStyle(ChatFormatting.GRAY)
        );
    }

    private List<Component> getTreasuryFilterTooltip() {
        String filterName = treasuryFilter != null ? treasuryFilter.getAsString() : "All";
        return List.of(
                Component.literal("Treasury Filter: " + filterName).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal("Click to cycle filter (All > Very Low > Low > Medium > High > Very High)").withStyle(ChatFormatting.GRAY)
        );
    }

    private void updateButtonTooltips() {
        if (defenseColorModeButton != null) defenseColorModeButton.setTooltip(getDefenseColorModeTooltip());
        if (defenseFilterButton != null) defenseFilterButton.setTooltip(getDefenseFilterTooltip());
        if (treasuryFilterButton != null) treasuryFilterButton.setTooltip(getTreasuryFilterTooltip());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) return false;

        if (targetZoomLevel < 0) {
            targetZoomLevel = zoomLevel;
            targetMapCenterX = mapCenterX;
            targetMapCenterZ = mapCenterZ;
        }

        // Current world coords under pointer
        float curScale = MapRenderer.getZoomRenderScaleFromLevel(targetZoomLevel);
        float worldX = targetMapCenterX + (float) (mouseX - centerX) / curScale;
        float worldZ = targetMapCenterZ + (float) (mouseY - centerZ) / curScale;

        // Apply scroll delta
        float delta = (float) verticalAmount * 2.5f;
        targetZoomLevel = Math.max(1.0f, Math.min(100.0f, targetZoomLevel + delta));
        float futureScale = MapRenderer.getZoomRenderScaleFromLevel(targetZoomLevel);

        // Keep world point anchored under cursor
        targetMapCenterX = worldX - (float) (mouseX - centerX) / futureScale;
        targetMapCenterZ = worldZ - (float) (mouseY - centerZ) / futureScale;

        return true;
    }

    @Override
    public void doRender(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Smooth zoom & smooth pan to cursor
        if (targetZoomLevel >= 0) {
            if (isPanning) {
                targetZoomLevel = -1.0f;
            } else {
                float zoomDiff = targetZoomLevel - zoomLevel;
                float cxDiff = targetMapCenterX - mapCenterX;
                float czDiff = targetMapCenterZ - mapCenterZ;

                if (Math.abs(zoomDiff) > 0.05f || Math.abs(cxDiff) > 0.5f || Math.abs(czDiff) > 0.5f) {
                    float factor = 0.35f;
                    setZoomLevel(zoomLevel + zoomDiff * factor);
                    mapCenterX += cxDiff * factor;
                    mapCenterZ += czDiff * factor;
                } else {
                    setZoomLevel(targetZoomLevel);
                    mapCenterX = targetMapCenterX;
                    mapCenterZ = targetMapCenterZ;
                    targetZoomLevel = -1.0f;
                }
            }
        }

        // 1. World Map Terrain
        renderMap(g);

        // 2. Scissored territory area
        RenderUtils.enableScissor(
                g,
                (int) (renderX + renderedBorderXOffset),
                (int) (renderY + renderedBorderYOffset),
                (int) mapWidth,
                (int) mapHeight);

        renderConnections(g);
        renderHqOutboundTriangles(g);
        String hoveredTerr = renderTerritoryBoxes(g, mouseX, mouseY);

        // Player pointer (same as GuildMapScreen)
        renderCursor(g, 1.5f, CommonColors.WHITE, com.wynntils.utils.render.type.PointerType.ARROW);

        RenderUtils.disableScissor(g);

        // 3. Map border, coordinates, zoom widgets, and bottom buttons
        renderMapBorder(g);
        renderCoordinates(g, mouseX, mouseY);
        renderMapButtons(g, mouseX, mouseY, partialTick);
        renderZoomWidgets(g, mouseX, mouseY, partialTick);

        // 4. Hover tooltips (buttons have precedence over territory card)
        boolean hoveredButton = false;
        if (mapButtons != null) {
            for (MapButton btn : mapButtons) {
                if (btn.isMouseOver(mouseX, mouseY)) {
                    hoveredButton = true;
                    break;
                }
            }
        }

        if (hoveredButton) {
            renderTooltip(g, mouseX, mouseY);
        } else if (hoveredTerr != null) {
            try {
                renderTerritoryTooltip(g, hoveredTerr, mouseX, mouseY);
            } catch (Throwable t) {
                PotsmodClient.LOGGER.error("Error rendering territory tooltip for {}", hoveredTerr, t);
            }
        }
    }

    // --- Outbound HQ Directional Triangles ---
    private void renderHqOutboundTriangles(GuiGraphics g) {
        if (zoomRenderScale < 0.25f) return; // Cull when zoomed out to prevent lag

        Map<String, TerritoryBase> catalog = TerritoryBaseManager.getTerritories();
        if (catalog == null) return;

        float alphaFactor = Math.min(1.0f, (zoomRenderScale - 0.25f) / 0.25f);
        int alpha = (int) (0xFF * alphaFactor);
        int triColor = (alpha << 24) | 0x00FFD700;

        for (Map.Entry<String, TerritoryBase> entry : catalog.entrySet()) {
            String terrName = entry.getKey();
            TerritoryBase base = entry.getValue();
            if (base == null || base.connections() == null) continue;

            TerritoryInfo info = getTerritoryInfo(terrName);
            if (info == null || !info.isHeadquarters()) continue;

            TerritoryProfile hqProf = Models.Territory.getTerritoryProfile(terrName);
            if (hqProf == null) continue;

            float hqMinX = MapRenderer.getRenderX(Math.min(hqProf.getStartX(), hqProf.getEndX()), mapCenterX, centerX, zoomRenderScale);
            float hqMaxX = MapRenderer.getRenderX(Math.max(hqProf.getStartX(), hqProf.getEndX()), mapCenterX, centerX, zoomRenderScale);
            float hqMinZ = MapRenderer.getRenderZ(Math.min(hqProf.getStartZ(), hqProf.getEndZ()), mapCenterZ, centerZ, zoomRenderScale);
            float hqMaxZ = MapRenderer.getRenderZ(Math.max(hqProf.getStartZ(), hqProf.getEndZ()), mapCenterZ, centerZ, zoomRenderScale);

            float cx1 = (hqMinX + hqMaxX) / 2.0f;
            float cz1 = (hqMinZ + hqMaxZ) / 2.0f;

            for (String connName : base.connections()) {
                TerritoryProfile p2 = Models.Territory.getTerritoryProfile(connName);
                if (p2 == null) continue;

                float cx2 = MapRenderer.getRenderX((p2.getStartX() + p2.getEndX()) / 2, mapCenterX, centerX, zoomRenderScale);
                float cz2 = MapRenderer.getRenderZ((p2.getStartZ() + p2.getEndZ()) / 2, mapCenterZ, centerZ, zoomRenderScale);

                float dx = cx2 - cx1;
                float dz = cz2 - cz1;
                float len = (float) Math.hypot(dx, dz);
                if (len < 1.0f) continue;
                float ndx = dx / len;
                float ndz = dz / len;

                float hw = (hqMaxX - hqMinX) / 2.0f;
                float hh = (hqMaxZ - hqMinZ) / 2.0f;
                if (hw <= 0 || hh <= 0) continue;

                float scaleX = Math.abs(ndx) > 1e-5f ? hw / Math.abs(ndx) : Float.MAX_VALUE;
                float scaleZ = Math.abs(ndz) > 1e-5f ? hh / Math.abs(ndz) : Float.MAX_VALUE;
                float t = Math.min(scaleX, scaleZ);

                float borderX = cx1 + ndx * t;
                float borderZ = cz1 + ndz * t;

                drawOutboundTriangle(g, borderX, borderZ, ndx, ndz, 5.0f, triColor);
            }
        }
    }

    private void drawOutboundTriangle(GuiGraphics g, float tipX, float tipZ, float dirX, float dirZ, float size, int argb) {
        float perpX = -dirZ;
        float perpZ = dirX;

        float baseCenterX = tipX - dirX * size;
        float baseCenterZ = tipZ - dirZ * size;
        float halfBase = size * 0.7f;

        float leftX = baseCenterX + perpX * halfBase;
        float leftZ = baseCenterZ + perpZ * halfBase;
        float rightX = baseCenterX - perpX * halfBase;
        float rightZ = baseCenterZ - perpZ * halfBase;

        CustomColor col = CustomColor.fromInt(argb);
        RenderUtils.drawLine(g, col, tipX, tipZ, leftX, leftZ, 1.5f);
        RenderUtils.drawLine(g, col, tipX, tipZ, rightX, rightZ, 1.5f);
        RenderUtils.drawLine(g, col, leftX, leftZ, rightX, rightZ, 1.5f);
        RenderUtils.drawLine(g, col, tipX, tipZ, (leftX + rightX) / 2.0f, (leftZ + rightZ) / 2.0f, 1.5f);
    }

    // --- Route Lines (Optimized: skips neutral lines when zoomed out to eliminate lag) ---
    private void renderConnections(GuiGraphics g) {
        if (zoomRenderScale < 0.08f) return;

        Map<String, TerritoryBase> catalog = TerritoryBaseManager.getTerritories();
        if (catalog == null) return;

        boolean isZoomedOut = zoomRenderScale < 0.25f;
        float fade = Math.min(1.0f, (zoomRenderScale - 0.08f) / 0.12f);
        float lineWidth = Math.max(1.1f, Math.min(2.2f, 1.5f * zoomRenderScale * 2.2f));

        Set<String> drawn = new HashSet<>();

        for (Map.Entry<String, TerritoryBase> entry : catalog.entrySet()) {
            String terrName = entry.getKey();
            TerritoryBase base = entry.getValue();
            if (base == null || base.connections() == null) continue;

            TerritoryProfile p1 = Models.Territory.getTerritoryProfile(terrName);
            if (p1 == null) continue;

            float x1 = MapRenderer.getRenderX((p1.getStartX() + p1.getEndX()) / 2, mapCenterX, centerX, zoomRenderScale);
            float z1 = MapRenderer.getRenderZ((p1.getStartZ() + p1.getEndZ()) / 2, mapCenterZ, centerZ, zoomRenderScale);

            String g1 = getGuildName(terrName);
            TerritoryInfo info1 = getTerritoryInfo(terrName);
            boolean isHq1 = info1 != null && info1.isHeadquarters();

            for (String connName : base.connections()) {
                String pairKey = terrName.compareTo(connName) < 0 ? terrName + "<->" + connName : connName + "<->" + terrName;
                if (drawn.add(pairKey)) {
                    String g2 = getGuildName(connName);
                    boolean sameGuild = g1 != null && g1.equals(g2);
                    TerritoryInfo info2 = getTerritoryInfo(connName);
                    boolean isHq2 = info2 != null && info2.isHeadquarters();
                    boolean connectedToHq = sameGuild && (isHq1 || isHq2);

                    // When heavily zoomed out, only draw active guild links to avoid dropping frames
                    if (isZoomedOut && !sameGuild) {
                        continue;
                    }

                    TerritoryProfile p2 = Models.Territory.getTerritoryProfile(connName);
                    if (p2 == null) continue;

                    float x2 = MapRenderer.getRenderX((p2.getStartX() + p2.getEndX()) / 2, mapCenterX, centerX, zoomRenderScale);
                    float z2 = MapRenderer.getRenderZ((p2.getStartZ() + p2.getEndZ()) / 2, mapCenterZ, centerZ, zoomRenderScale);

                    int baseColor;
                    if (connectedToHq) {
                        baseColor = 0x001E90FF; // HQ active link: bright blue
                    } else if (sameGuild) {
                        baseColor = 0x00FFFACD; // Same guild connection: warm light cream
                    } else {
                        baseColor = 0x00111111; // Base territory route: crisp dark line
                    }

                    int lineAlpha = (int) (0xDD * fade);
                    int lineColor = (lineAlpha << 24) | baseColor;

                    RenderUtils.drawLine(g, CustomColor.fromInt(lineColor), x1, z1, x2, z2, lineWidth);
                }
            }
        }
    }

    // --- Territory Boxes Rendering (Zero GPU flushes: uses batched GUI outlines) ---
    private String renderTerritoryBoxes(GuiGraphics g, int mouseX, int mouseY) {
        String hoveredTerritory = null;

        List<TerritoryPoi> pois = Models.Territory.getTerritoryPoisFromAdvancement();
        if (pois == null) return null;

        boolean isZoomedOut75 = zoomRenderScale < 0.28f; // 75-100% zoomed out

        for (TerritoryPoi poi : pois) {
            String name = poi.getName();
            TerritoryProfile profile = poi.getTerritoryProfile();
            if (profile == null) continue;
            TerritoryInfo info = poi.getTerritoryInfo();

            // Filtering
            if (defenseFilter != null) {
                GuildResourceValues def = info != null ? info.getDefences() : null;
                if (def != defenseFilter) continue;
            }
            if (treasuryFilter != null) {
                GuildResourceValues treas = info != null ? info.getTreasury() : null;
                if (treas != treasuryFilter) continue;
            }

            int minX = Math.min(profile.getStartX(), profile.getEndX());
            int maxX = Math.max(profile.getStartX(), profile.getEndX());
            int minZ = Math.min(profile.getStartZ(), profile.getEndZ());
            int maxZ = Math.max(profile.getStartZ(), profile.getEndZ());

            float rx = MapRenderer.getRenderX(minX, mapCenterX, centerX, zoomRenderScale);
            float rz = MapRenderer.getRenderZ(minZ, mapCenterZ, centerZ, zoomRenderScale);
            float rx2 = MapRenderer.getRenderX(maxX, mapCenterX, centerX, zoomRenderScale);
            float rz2 = MapRenderer.getRenderZ(maxZ, mapCenterZ, centerZ, zoomRenderScale);

            float rw = Math.abs(rx2 - rx);
            float rh = Math.abs(rz2 - rz);

            // Frustum Culling
            if (rx + rw < renderX + renderedBorderXOffset || rx > renderX + renderedBorderXOffset + mapWidth ||
                rz + rh < renderY + renderedBorderYOffset || rz > renderY + renderedBorderYOffset + mapHeight) {
                continue;
            }

            boolean isHovered = mouseX >= rx && mouseX <= rx + rw && mouseY >= rz && mouseY <= rz + rh;
            if (isHovered && hoveredTerritory == null) {
                hoveredTerritory = name;
            }

            boolean isHq = info != null && info.isHeadquarters();

            String guildName = (info != null && info.getGuildName() != null && !info.getGuildName().isEmpty())
                    ? info.getGuildName()
                    : profile.getGuild();
            CustomColor guildColor = guildName != null ? Models.Guild.getColor(guildName) : CommonColors.WHITE;
            if (guildColor == null) guildColor = CommonColors.WHITE;
            int guildRgb = guildColor.asInt() & 0x00FFFFFF;

            // Box Fill & Border Color: Guild Color or Defense Tier
            int baseRgb;
            if (getColorMode() == 1) {
                GuildResourceValues def = info != null ? info.getDefences() : GuildResourceValues.VERY_LOW;
                baseRgb = getDefenseTierColorHex(def) & 0x00FFFFFF;
            } else if (showResourceColors && info != null && !info.getResourceColors().isEmpty()) {
                baseRgb = info.getResourceColors().get(0).asInt() & 0x00FFFFFF;
            } else {
                baseRgb = guildRgb;
            }

            // Restrained opacity for clear terrain readability (batched fill)
            int fillAlpha = isHovered ? 0x58 : 0x36;
            int fillColor = (fillAlpha << 24) | baseRgb;
            g.fill((int) rx, (int) rz, (int) (rx + rw), (int) (rz + rh), fillColor);

            // Fast batched outline (avoids 1,600 GPU flushes per frame)
            int borderCol = isHq ? 0xFFFFD700 : ((isHovered ? 0xFF : 0xC0) << 24) | baseRgb;
            g.renderOutline((int) rx, (int) rz, (int) rw, (int) rh, borderCol);
            if (isHq && rw >= 4 && rh >= 4) {
                g.renderOutline((int) rx + 1, (int) rz + 1, (int) rw - 2, (int) rh - 2, 0xFFFFD700);
            }

            // Check if this territory has an active queued attack timer
            String friendlyName = profile != null ? profile.getFriendlyName() : name;
            java.util.Optional<com.wynntils.models.territories.TerritoryAttackTimer> attackTimerCheck =
                    com.wynntils.core.components.Models.GuildAttackTimer.getAttackTimerForTerritory(friendlyName);
            if (!attackTimerCheck.isPresent() && !friendlyName.equals(name)) {
                attackTimerCheck = com.wynntils.core.components.Models.GuildAttackTimer.getAttackTimerForTerritory(name);
            }

            // Glowing pulsing red border around the whole territory box if queued!
            if (attackTimerCheck.isPresent()) {
                long nowPulse = System.currentTimeMillis();
                float boxPulse = (float) Math.sin(nowPulse / 200.0) * 0.5f + 0.5f; // 0.0 to 1.0
                int boxPulseAlpha = (int) (160 + boxPulse * 95); // 160 to 255
                int pulseRed = (boxPulseAlpha << 24) | 0xFF2222;

                g.renderOutline((int) rx, (int) rz, (int) rw, (int) rh, pulseRed);
                if (rw >= 4 && rh >= 4) {
                    g.renderOutline((int) rx + 1, (int) rz + 1, (int) rw - 2, (int) rh - 2, pulseRed);
                }
                if (rw >= 6 && rh >= 6) {
                    g.renderOutline((int) rx - 1, (int) rz - 1, (int) rw + 2, (int) rh + 2, (boxPulseAlpha / 2 << 24) | 0xFF0000);
                }
            }

            // HQ Crown Badge
            if (isHq && rw >= 14 && rh >= 14) {
                RenderUtils.drawTexturedRect(g, Texture.GUILD_HEADQUARTERS, (int) rx + 2, (int) rz + 2);
            }

            // Territory Label: Owning Guild Tag OR Territory Name Abbreviation (Toggle Mode)
            String tag = showTerritoryAbbreviation
                    ? getTerritoryAbbreviation(name)
                    : ((info != null && info.getGuildPrefix() != null && !info.getGuildPrefix().isEmpty())
                            ? info.getGuildPrefix()
                            : profile.getGuildPrefix());

            if (tag == null || tag.isEmpty()) {
                tag = getTerritoryAbbreviation(name);
            }

            int tagColor = 0xFF000000 | guildRgb; // Always owning guild color

            if (rw >= 10 && rh >= 8) {
                // Guild tag / name: placed slightly toward top
                int tw = font.width(tag);
                float tagScale = tw > rw - 2 ? Math.max(0.55f, (rw - 2) / (float) tw) : 1.0f;

                g.pose().pushMatrix();
                float tx = rx + rw / 2.0f;
                float ty = rz + Math.max(3.0f, rh * 0.22f);
                g.pose().translate(tx, ty);
                if (tagScale < 1.0f) {
                    g.pose().scale(tagScale, tagScale);
                }
                g.drawString(font, tag, -tw / 2, -4, tagColor, true);
                g.pose().popMatrix();

                // Check if this territory has an active war/attack timer queued (friendly name or raw name)
                String friendly = profile != null ? profile.getFriendlyName() : name;
                java.util.Optional<com.wynntils.models.territories.TerritoryAttackTimer> attackTimerOpt =
                        com.wynntils.core.components.Models.GuildAttackTimer.getAttackTimerForTerritory(friendly);
                if (!attackTimerOpt.isPresent() && !friendly.equals(name)) {
                    attackTimerOpt = com.wynntils.core.components.Models.GuildAttackTimer.getAttackTimerForTerritory(name);
                }

                if (attackTimerOpt.isPresent()) {
                    // Queued territory timer: ALWAYS visible regardless of zoom level!
                    com.wynntils.models.territories.TerritoryAttackTimer attackTimer = attackTimerOpt.get();
                    String warStr = "⚔ " + attackTimer.timerString();
                    int warW = font.width(warStr);

                    float centerX = rx + rw / 2.0f;
                    float centerY = rz + rh / 2.0f;

                    // If box is small (e.g. zoomed out), keep text at readable size (minimum scale 0.7f)
                    float warScale = rw < warW + 4 ? Math.max(0.70f, (rw - 2) / (float) warW) : 1.0f;

                    // Pulsing dark red background badge + glowing border
                    long timeMs = System.currentTimeMillis();
                    float pulse = (float) Math.sin(timeMs / 250.0) * 0.5f + 0.5f; // 0.0 to 1.0
                    int bgAlpha = 0xD0;
                    int badgeBg = (bgAlpha << 24) | 0x220000;
                    int borderAlpha = (int) (180 + pulse * 75);
                    int badgeBorder = (borderAlpha << 24) | 0xFF2222;

                    int padX = 3;
                    int padY = 2;
                    int bw = (int) ((warW + padX * 2) * warScale);
                    int bh = (int) ((font.lineHeight + padY * 2) * warScale);

                    int bx1 = (int) (centerX - bw / 2.0f);
                    int by1 = (int) (centerY - bh / 2.0f);
                    int bx2 = bx1 + bw;
                    int by2 = by1 + bh;

                    // Draw dark badge with pulsing glowing border
                    g.fill(bx1, by1, bx2, by2, badgeBg);
                    g.renderOutline(bx1, by1, bw, bh, badgeBorder);

                    // Draw bold high-contrast attack timer text
                    g.pose().pushMatrix();
                    g.pose().translate(centerX, centerY);
                    if (warScale < 1.0f) g.pose().scale(warScale, warScale);
                    g.drawString(font, warStr, -warW / 2, -font.lineHeight / 2, 0xFFFF4444, true);
                    g.pose().popMatrix();
                } else if (!isZoomedOut75 && rw >= 14 && rh >= 12) {
                    // Regular territory ownership age timer (culled when zoomed out to keep map clean)
                    String timerStr = formatHeldTimer(profile);
                    if (timerStr != null && !timerStr.isEmpty()) {
                        GuildResourceValues treas = info != null ? info.getTreasury() : GuildResourceValues.VERY_LOW;
                        int timerColor = getTreasuryColorHex(treas);

                        int timW = font.width(timerStr);
                        float timScale = timW > rw - 2 ? Math.max(0.50f, (rw - 2) / (float) timW) : 1.0f;

                        g.pose().pushMatrix();
                        float timCenterX = rx + rw / 2.0f;
                        float timCenterY = rz + rh / 2.0f;
                        g.pose().translate(timCenterX, timCenterY);
                        if (timScale < 1.0f) {
                            g.pose().scale(timScale, timScale);
                        }
                        g.drawString(font, timerStr, -timW / 2, -4, timerColor, true);
                        g.pose().popMatrix();
                    }
                }
            }

            // Resource Icons on territories (including Green Emeralds for cities!)
            // Placed at bottom of the territory box, larger and more visible
            if (!isZoomedOut75 && rw >= 12 && rh >= 14) {
                TerritoryBase base = TerritoryBaseManager.getTerritory(name);
                List<Identifier> prodIcons = new ArrayList<>();
                collectResourceIcons(prodIcons, base, info);

                if (!prodIcons.isEmpty()) {
                    int iconCount = prodIcons.size();
                    // Larger icon size: up to 12-14px when zoomed in, scaled cleanly
                    int iconSize = Math.max(8, Math.min(13, (int) Math.min((rw - 2) / iconCount, rh * 0.35f)));
                    int totalIconsW = iconCount * iconSize + (iconCount - 1) * 2;
                    float iconsX = rx + (rw - totalIconsW) / 2.0f;
                    float iconsY = rz + rh - iconSize - 2;
                    for (int i = 0; i < iconCount; i++) {
                        RenderUtils.drawScalingTexturedRect(g, prodIcons.get(i), iconsX + i * (iconSize + 2), iconsY, iconSize, iconSize, 16, 16);
                    }
                }
            }
        }
        return hoveredTerritory;
    }

    private static void collectResourceIcons(List<Identifier> list, TerritoryBase base, TerritoryInfo info) {
        if (base != null) {
            // 1. Emerald icon ONLY for cities (cities in Wynncraft have base emerald production >= 18000)
            if (base.getProduction(GuildResource.EMERALDS) >= 18000) {
                list.add(RES_EMERALDS);
            }

            // 2. Material icons: exactly 1 icon for single production (3600), exactly 2 icons for double production (>= 7200)
            addMaterialIcon(list, RES_ORE, base.getProduction(GuildResource.ORE));
            addMaterialIcon(list, RES_WOOD, base.getProduction(GuildResource.WOOD));
            addMaterialIcon(list, RES_FISH, base.getProduction(GuildResource.FISH));
            addMaterialIcon(list, RES_CROPS, base.getProduction(GuildResource.CROPS));
        } else if (info != null && info.getGenerators() != null) {
            int em = info.getGenerators().getOrDefault(GuildResource.EMERALDS, 0);
            if (em >= 18000) {
                list.add(RES_EMERALDS);
            }
            addMaterialIcon(list, RES_ORE, info.getGenerators().getOrDefault(GuildResource.ORE, 0));
            addMaterialIcon(list, RES_WOOD, info.getGenerators().getOrDefault(GuildResource.WOOD, 0));
            addMaterialIcon(list, RES_FISH, info.getGenerators().getOrDefault(GuildResource.FISH, 0));
            addMaterialIcon(list, RES_CROPS, info.getGenerators().getOrDefault(GuildResource.CROPS, 0));
        }
    }

    private static void addMaterialIcon(List<Identifier> list, Identifier icon, int amount) {
        if (icon == null || amount <= 0) return;
        if (amount >= 7200) {
            list.add(icon);
            list.add(icon); // Exactly 2 icons for double production
        } else {
            list.add(icon); // Exactly 1 icon for normal single production
        }
    }

    private static Identifier getResourceIcon(GuildResource res) {
        if (res == null) return null;
        return switch (res) {
            case EMERALDS -> RES_EMERALDS;
            case ORE -> RES_ORE;
            case WOOD -> RES_WOOD;
            case FISH -> RES_FISH;
            case CROPS -> RES_CROPS;
        };
    }

    private static int getTreasuryColorHex(GuildResourceValues treas) {
        if (treas == null) return 0xFF00AA00;
        return switch (treas) {
            case VERY_HIGH -> 0xFFAA0000; // Dark Red
            case HIGH -> 0xFFFF5555;      // Red
            case MEDIUM -> 0xFFFFFF55;    // Yellow
            case LOW -> 0xFF55FF55;       // Green
            case VERY_LOW, NONE -> 0xFF00AA00; // Dark Green
        };
    }

    private static int getDefenseTierColorHex(GuildResourceValues def) {
        if (def == null) return 0xFF00AA00;
        return switch (def) {
            case VERY_HIGH -> 0xFFAA0000; // Dark Red
            case HIGH -> 0xFFFF5555;      // Red
            case MEDIUM -> 0xFFFFFF55;    // Yellow
            case LOW -> 0xFF55FF55;       // Green
            case VERY_LOW, NONE -> 0xFF00AA00; // Dark Green
        };
    }

    /**
     * Production Upgrades color logic (matches Wynntils TerritoryItem / TerritoryBonusEffectHighlighter):
     * - Green  = 3/3 emerald production (EMERALD_RATE + EFFICIENT_EMERALDS both maxed >= 6)
     * - Yellow = 3/3 resource production (RESOURCE_RATE + EFFICIENT_RESOURCES both maxed >= 6)
     * - Both maxed = List of [Green, Yellow] (drawn as mixed / multicolored stripes)
     * - Otherwise = Dim gray
     */
    public static List<CustomColor> getProductionColors(TerritoryInfo info) {
        if (info == null) return List.of(CustomColor.fromHSV(0.0f, 0.0f, 0.6f, 1.0f));
        Map<TerritoryUpgrade, Integer> defs = null;
        try {
            var estimate = DefenseEstimateUtils.estimate("", info);
            if (estimate != null && estimate != org.pots.potsmod.models.defense.DefenseEstimate.EMPTY) {
                defs = estimate.defenses();
            }
        } catch (Throwable ignored) {}
        if (defs == null) return List.of(CustomColor.fromHSV(0.0f, 0.0f, 0.6f, 1.0f));

        int emeraldLevels = defs.getOrDefault(TerritoryUpgrade.EMERALD_RATE, 0) + defs.getOrDefault(TerritoryUpgrade.EFFICIENT_EMERALDS, 0);
        int resourceLevels = defs.getOrDefault(TerritoryUpgrade.RESOURCE_RATE, 0) + defs.getOrDefault(TerritoryUpgrade.EFFICIENT_RESOURCES, 0);

        List<CustomColor> colors = new ArrayList<>();
        if (emeraldLevels >= 6) {
            colors.add(CustomColor.fromHSV(0.33333334f, 0.8f, 0.9f, 1.0f)); // Wynntils Green
        }
        if (resourceLevels >= 6) {
            colors.add(CustomColor.fromHSV(0.16666667f, 0.8f, 0.9f, 1.0f)); // Wynntils Yellow
        } else if (resourceLevels > 0 && colors.isEmpty()) {
            colors.add(CustomColor.fromHSV(0.16666667f, 0.5f, 0.8f, 1.0f));
        }

        if (colors.isEmpty()) {
            colors.add(CustomColor.fromHSV(0.0f, 0.0f, 0.6f, 1.0f));
        }
        return colors;
    }

    // --- Territory Hover Tooltip ---
    private void renderTerritoryTooltip(GuiGraphics g, String terrName, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        TerritoryProfile profile = Models.Territory.getTerritoryProfile(terrName);
        TerritoryInfo info = getTerritoryInfo(terrName);

        boolean isHq = info != null && info.isHeadquarters();

        // 1. Territory Name (Bold White; Bold Gold if HQ)
        ChatFormatting nameStyle = isHq ? ChatFormatting.GOLD : ChatFormatting.WHITE;
        lines.add(Component.literal(terrName).withStyle(nameStyle, ChatFormatting.BOLD));

        // 2. Guild Name & Tag (Aligned with territory name: Golden Guild Name, Gray Tag)
        String guildName = (info != null && info.getGuildName() != null && !info.getGuildName().isEmpty())
                ? info.getGuildName()
                : (profile != null ? profile.getGuild() : null);
        String guildPrefix = (info != null && info.getGuildPrefix() != null && !info.getGuildPrefix().isEmpty())
                ? info.getGuildPrefix()
                : (profile != null ? profile.getGuildPrefix() : null);

        if (guildName != null && !guildName.isEmpty()) {
            var guildComp = Component.literal(guildName).withStyle(ChatFormatting.GOLD);
            if (guildPrefix != null && !guildPrefix.isEmpty()) {
                guildComp.append(Component.literal(" [" + guildPrefix + "]").withStyle(ChatFormatting.GRAY));
            }
            lines.add(guildComp);
        }

        // Spacing
        lines.add(Component.literal(""));

        // 3. Held for (Text "Held for: " in gray, total time held color-coded following treasury color system)
        String heldTimeStr = formatHeldTimer(profile);
        if (heldTimeStr != null && !heldTimeStr.isEmpty()) {
            GuildResourceValues treasVal = info != null ? info.getTreasury() : GuildResourceValues.VERY_LOW;
            ChatFormatting heldColor = switch (treasVal != null ? treasVal : GuildResourceValues.VERY_LOW) {
                case VERY_HIGH -> ChatFormatting.DARK_RED;
                case HIGH -> ChatFormatting.RED;
                case MEDIUM -> ChatFormatting.YELLOW;
                case LOW -> ChatFormatting.GREEN;
                case VERY_LOW, NONE -> ChatFormatting.DARK_GREEN;
            };
            lines.add(Component.literal("Held for: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(heldTimeStr).withStyle(heldColor)));
        }

        // 4. Treasury (Yellow, non-bolded)
        GuildResourceValues treasury = info != null ? info.getTreasury() : GuildResourceValues.VERY_LOW;
        double bonus = TerritoryUtils.getTreasuryBonus(terrName);
        String treasStr = treasury != null ? treasury.getAsString() : "Very Low";
        lines.add(Component.literal("Treasury: %s (+%.1f%%)".formatted(treasStr, bonus * 100.0)).withStyle(ChatFormatting.YELLOW));

        // 5. Defense (Text "Defense:" in gray, tier color coded: Very Low dark green, low lime, medium yellow, high red, very high dark red)
        GuildResourceValues defense = info != null ? info.getDefences() : GuildResourceValues.VERY_LOW;
        String defStr = defense != null ? defense.getAsString() : "Very Low";
        ChatFormatting defColor = switch (defense != null ? defense : GuildResourceValues.VERY_LOW) {
            case VERY_HIGH -> ChatFormatting.DARK_RED;
            case HIGH -> ChatFormatting.RED;
            case MEDIUM -> ChatFormatting.YELLOW;
            case LOW -> ChatFormatting.GREEN;
            case VERY_LOW, NONE -> ChatFormatting.DARK_GREEN;
        };
        lines.add(Component.literal("Defense: ").withStyle(ChatFormatting.GRAY).append(Component.literal(defStr).withStyle(defColor)));

        // 5. Production
        lines.add(Component.literal(""));
        lines.add(Component.literal("Production:").withStyle(ChatFormatting.GRAY));
        TerritoryBase base = TerritoryBaseManager.getTerritory(terrName);
        for (GuildResource res : GuildResource.values()) {
            int prod = info != null ? info.getGeneration(res) : (base != null ? base.getProduction(res) : 0);
            int held = 0;
            int cap = 0;
            if (info != null) {
                var st = info.getStorage(res);
                if (st != null) {
                    held = st.current();
                    cap = st.max();
                }
            }
            if (prod > 0 || cap > 0) {
                lines.add(Component.literal("%s %s: +%d/h (%s/%s)".formatted(
                        res.getPrettySymbol().trim(),
                        res.getName(),
                        prod,
                        formatStatNum(held),
                        formatStatNum(cap))).withStyle(ChatFormatting.WHITE));
            }
        }

        // 6. Total Stats & Combat Stats
        DefenseEstimate estimate = DefenseEstimate.EMPTY;
        try {
            if (info != null) {
                estimate = DefenseEstimateUtils.estimate(terrName, info);
            }
        } catch (Throwable t) {
            estimate = DefenseEstimate.EMPTY;
        }
        if (estimate != DefenseEstimate.EMPTY) {
            Map<TerritoryUpgrade, Integer> defs = estimate.defenses();

            lines.add(Component.literal(""));
            lines.add(Component.literal("Total Stats:").withStyle(ChatFormatting.GOLD));

            int dmgLvl = defs.getOrDefault(TerritoryUpgrade.DAMAGE, 0);
            int atkLvl = defs.getOrDefault(TerritoryUpgrade.ATTACK, 0);
            int hpLvl = defs.getOrDefault(TerritoryUpgrade.HEALTH, 0);
            int defLvl = defs.getOrDefault(TerritoryUpgrade.DEFENCE, 0);

            try {
                TerritoryCombatStats stats = TerritoryUtils.estimateStats(defs, info);
                if (stats != null) {
                    String dmgLine = "Damage: %s - %s (%.1fx)".formatted(
                            formatStatNum(stats.minDamage()),
                            formatStatNum(stats.maxDamage()),
                            stats.attackSpeed());
                    lines.add(Component.literal("Ⓑ (%d) ".formatted(dmgLvl)).withStyle(ChatFormatting.WHITE)
                            .append(Component.literal(dmgLine).withStyle(ChatFormatting.WHITE)));

                    lines.add(Component.literal("Ⓑ (%d) ".formatted(atkLvl)).withStyle(ChatFormatting.WHITE)
                            .append(Component.literal("Damage per Second: %s".formatted(formatStatNum(stats.averageDps()))).withStyle(ChatFormatting.WHITE)));

                    String healthLine = "Health: %s (%.1f%%)".formatted(
                            formatStatNum(stats.health()),
                            stats.defence() * 100.0);
                    lines.add(Component.literal("Ⓒ (%d) ".formatted(hpLvl)).withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(healthLine).withStyle(ChatFormatting.GOLD)));

                    String ehpStr = Double.isInfinite(stats.ehp()) ? "∞" : formatStatNum(stats.ehp());
                    lines.add(Component.literal("Ⓚ (%d) ".formatted(defLvl)).withStyle(ChatFormatting.AQUA)
                            .append(Component.literal("Effective Health: %s".formatted(ehpStr)).withStyle(ChatFormatting.AQUA)));
                }
            } catch (Exception ignored) {}

            List<String> bonusList = new ArrayList<>();
            int aura = defs.getOrDefault(TerritoryUpgrade.TOWER_AURA, 0);
            if (aura > 0) bonusList.add("Aura " + aura);
            int volley = defs.getOrDefault(TerritoryUpgrade.TOWER_VOLLEY, 0);
            if (volley > 0) bonusList.add("Volley " + volley);
            int multi = defs.getOrDefault(TerritoryUpgrade.TOWER_MULTI_ATTACKS, 0);
            if (multi > 0) bonusList.add("Multi " + multi);
            int minions = defs.getOrDefault(TerritoryUpgrade.STRONGER_MINIONS, 0);
            if (minions > 0) bonusList.add("Minions " + minions);

            if (!bonusList.isEmpty()) {
                lines.add(Component.literal(String.join(", ", bonusList)).withStyle(ChatFormatting.WHITE));
            }
        }

        // 7. Fastest Queue (Golden)
        int queueTime = TerritoryUtils.getQueueTime(terrName);
        lines.add(Component.literal(""));
        String qStr = queueTime >= 0 ? "Fastest Queue: " + queueTime + "m" : "Fastest Queue: None";
        lines.add(Component.literal(qStr).withStyle(ChatFormatting.GOLD));

        int tipX = mouseX + 12;
        int tipY = mouseY;
        RenderUtils.renderTooltip(g, lines, tipX, tipY);
    }

    private static String formatStatNum(double val) {
        if (val >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", val / 1_000_000.0);
        } else if (val >= 1_000) {
            return String.format(Locale.ROOT, "%,.0f", val);
        }
        return String.format(Locale.ROOT, "%.0f", val);
    }

    private static TerritoryInfo getTerritoryInfo(String name) {
        try {
            TerritoryPoi poi = Models.Territory.getTerritoryPoiFromAdvancement(name);
            if (poi != null && poi.getTerritoryInfo() != null) return poi.getTerritoryInfo();
        } catch (Throwable ignored) {}
        try {
            return TerritoryUtils.territoryInfo(name);
        } catch (Throwable ignored) {}
        return null;
    }

    private static String getGuildName(String name) {
        try {
            TerritoryInfo info = getTerritoryInfo(name);
            if (info != null && info.getGuildName() != null && !info.getGuildName().isEmpty()) {
                return info.getGuildName();
            }
            TerritoryProfile profile = Models.Territory.getTerritoryProfile(name);
            if (profile != null) return profile.getGuild();
        } catch (Throwable ignored) {}
        return null;
    }

    private static String getTerritoryAbbreviation(String name) {
        if (name == null || name.isEmpty()) return "";
        String[] parts = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) sb.append(p.charAt(0));
        }
        return sb.toString();
    }

    private static void addResourceIcons(List<Identifier> list, Identifier icon, int amount) {
        if (icon == null || amount <= 0) return;
        int count = amount > 3600 ? 2 : 1;
        for (int i = 0; i < count; i++) {
            list.add(icon);
        }
    }

    public static String formatHeldTimer(TerritoryProfile profile) {
        if (profile == null) return null;
        java.time.Instant acquired = profile.getAcquired();
        if (acquired == null) {
            return profile.getReadableRelativeTimeAcquired();
        }
        long diffMs = Math.max(0, System.currentTimeMillis() - acquired.toEpochMilli());
        long totalSeconds = diffMs / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) {
            return days + "d " + hours + "h";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }

    @Override
    public void onClose() {
        lastMapCenterX = this.mapCenterX;
        lastMapCenterZ = this.mapCenterZ;
        lastZoomLevel = this.zoomLevel;
        super.onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
