package org.pots.potsmod.features;

import com.mojang.blaze3d.platform.InputConstants;
import com.wynntils.core.components.Managers;
import com.wynntils.core.components.Models;
import com.wynntils.core.components.Services;
import com.wynntils.features.map.GuildMapFeature;
import com.wynntils.screens.maps.GuildMapScreen;
import com.wynntils.services.map.MapTexture;
import com.wynntils.services.map.pois.TerritoryPoi;
import com.wynntils.utils.colors.CommonColors;
import com.wynntils.utils.colors.CustomColor;
import com.wynntils.utils.render.MapRenderer;
import com.wynntils.utils.render.RenderUtils;
import com.wynntils.utils.render.Texture;
import com.wynntils.utils.render.type.PointerType;
import com.wynntils.utils.type.BoundingBox;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.pots.potsmod.PotsmodClient;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.config.PotsmodConfigData;
import org.pots.potsmod.managers.FeatureManager;
import org.pots.potsmod.mixin.wynntils.AbstractMapScreenAccessor;
import org.pots.potsmod.util.KeyUtil;

import java.util.List;

/**
 * Keybind-toggled HUD overlay drawing the real Wynntils map
 * (terrain tiles + territory boxes/tags + defense tints + player pointer) translucently on top of the game.
 * Tap = toggle, Hold = peek.
 * No Screen is opened; movement and keybinds keep working.
 */
public class WorldMapHudFeature extends Feature {

    private static final float SCREEN_SIDE_OFFSET = 10.0f;
    private static final float BORDER_OFFSET = 6.0f;
    private static final float ZOOM_STEP = 0.5f;

    // Stored zoomLevel shared with GuildMapScreen; default matches AbstractMapScreen
    private float zoomLevel = MapRenderer.DEFAULT_ZOOM_LEVEL;

    private static final Identifier HUD_ID  = Identifier.fromNamespaceAndPath("potsmod", "world_map_hud");
    private static final Identifier KEY_CAT = Identifier.fromNamespaceAndPath("potsmod", "hud");

    private boolean overlayEnabled = false;
    private boolean pendingToggleLog = false;

    // Tap / hold edge-detection state
    private boolean wasKeyDown = false;
    private long pressStartMs = 0L;
    private boolean peek = false;
    private boolean ignoreRelease = false;

    // Zoom key edge-detection state
    private boolean wasZoomInDown = false;
    private boolean wasZoomOutDown = false;

    private KeyMapping toggleKey;
    private KeyMapping zoomInKey;
    private KeyMapping zoomOutKey;

    @Override
    public void init() {
        KeyMapping.Category category = KeyMapping.Category.register(KEY_CAT);

        PotsmodConfigData config = PotsmodConfig.getData();
        InputConstants.Key initialToggle = KeyUtil.parse(config.getWorldMapHudToggleKey());
        InputConstants.Key initialZoomIn = KeyUtil.parse(config.getWorldMapHudZoomInKey());
        InputConstants.Key initialZoomOut = KeyUtil.parse(config.getWorldMapHudZoomOutKey());

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.rmod.world_map_hud",
                initialToggle.getType(),
                initialToggle.getValue(),
                category
        ));
        zoomInKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.rmod.world_map_hud_zoom_in",
                initialZoomIn.getType(),
                initialZoomIn.getValue(),
                category
        ));
        zoomOutKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.rmod.world_map_hud_zoom_out",
                initialZoomOut.getType(),
                initialZoomOut.getValue(),
                category
        ));

        // Hook GuildMapScreen keyboard events: read zoom, close screen, enable overlay in sticky mode
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof GuildMapScreen guildMapScreen)) {
                return;
            }

            // Do NOT override GuildMapScreen zoom here - GuildMapScreenMixin already
            // restores the last zoom/center from WynntilsMapState. If we apply our HUD
            // zoomLevel here it tramples Wynntils' own zoom logic.

            ScreenKeyboardEvents.allowKeyPress(guildMapScreen).register((scr, event) -> {
                PotsmodConfigData cfg = PotsmodConfig.getData();
                if (KeyUtil.matches(cfg.getWorldMapHudToggleKey(), event) || (toggleKey != null && toggleKey.matches(event))) {
                    if ((Object) guildMapScreen instanceof AbstractMapScreenAccessor accessor) {
                        zoomLevel = accessor.getZoomLevel();
                    }
                    overlayEnabled = true;
                    pendingToggleLog = true;
                    // Sticky mode: ignore the physical release of this key press
                    peek = false;
                    ignoreRelease = true;
                    wasKeyDown = true;
                    client.setScreen(null);
                    return false; // Consume key press
                }
                return true;
            });
        });

        // Tick loop: poll key states and handle edge detection
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                resetTrackingState();
                overlayEnabled = false;
                return;
            }

            // Reset tracking state while any screen is open
            if (client.screen != null) {
                resetTrackingState();
                return;
            }

            pollKeys();
        });

        HudElementRegistry.addLast(HUD_ID, this::renderHud);
    }

    private void pollKeys() {
        PotsmodConfigData config = PotsmodConfig.getData();

        // 1. Overlay toggle / peek key
        boolean isDown = KeyUtil.isDown(config.getWorldMapHudToggleKey());

        if (isDown && !wasKeyDown) {
            // Key went DOWN
            if (!overlayEnabled) {
                overlayEnabled = true;
                pressStartMs = System.currentTimeMillis();
                peek = true;
                ignoreRelease = false;
                pendingToggleLog = true;
            } else {
                overlayEnabled = false;
                peek = false;
                ignoreRelease = true;
                PotsmodClient.LOGGER.info("[WorldMapHud] overlay toggle OFF");
            }
        } else if (!isDown && wasKeyDown) {
            // Key went UP
            if (!ignoreRelease && peek) {
                long heldMs = System.currentTimeMillis() - pressStartMs;
                int threshold = config.getWorldMapHudHoldThresholdMs();
                if (heldMs >= threshold) {
                    // Peek mode: held >= threshold -> hide on release
                    overlayEnabled = false;
                    PotsmodClient.LOGGER.info("[WorldMapHud] overlay peek released after {}ms (hidden)", heldMs);
                } else {
                    // Short tap: leave it ON!
                    PotsmodClient.LOGGER.info("[WorldMapHud] overlay tap detected ({}ms < {}ms), left ON", heldMs, threshold);
                }
            }
            peek = false;
            ignoreRelease = false;
        }
        wasKeyDown = isDown;

        // 2. Zoom in key (edge detection)
        boolean isZoomIn = KeyUtil.isDown(config.getWorldMapHudZoomInKey());
        if (isZoomIn && !wasZoomInDown) {
            setZoomLevel(zoomLevel + ZOOM_STEP);
        }
        wasZoomInDown = isZoomIn;

        // 3. Zoom out key (edge detection)
        boolean isZoomOut = KeyUtil.isDown(config.getWorldMapHudZoomOutKey());
        if (isZoomOut && !wasZoomOutDown) {
            setZoomLevel(zoomLevel - ZOOM_STEP);
        }
        wasZoomOutDown = isZoomOut;
    }

    private void resetTrackingState() {
        wasKeyDown = false;
        pressStartMs = 0L;
        peek = false;
        ignoreRelease = false;
        wasZoomInDown = false;
        wasZoomOutDown = false;
    }

    public void updateKeys() {
        PotsmodConfigData config = PotsmodConfig.getData();
        if (toggleKey != null) {
            InputConstants.Key key = KeyUtil.parse(config.getWorldMapHudToggleKey());
            toggleKey.setKey(key);
        }
        if (zoomInKey != null) {
            InputConstants.Key key = KeyUtil.parse(config.getWorldMapHudZoomInKey());
            zoomInKey.setKey(key);
        }
        if (zoomOutKey != null) {
            InputConstants.Key key = KeyUtil.parse(config.getWorldMapHudZoomOutKey());
            zoomOutKey.setKey(key);
        }
        KeyMapping.resetMapping();
    }

    private void setZoomLevel(float newLevel) {
        zoomLevel = Math.max(1.0f, Math.min((float) MapRenderer.ZOOM_LEVELS, newLevel));
    }

    private void renderHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!overlayEnabled || isDisabled()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }
        if (client.screen != null) {
            return; // Never draw while any screen is open
        }

        PotsmodConfigData config = PotsmodConfig.getData();

        float screenW = guiGraphics.guiWidth();
        float screenH = guiGraphics.guiHeight();

        // Exact layout logic matching AbstractMapScreen.doInit
        float renderX = SCREEN_SIDE_OFFSET;
        float renderY = SCREEN_SIDE_OFFSET;
        float renderWidth = screenW - 2.0f * SCREEN_SIDE_OFFSET;
        float renderHeight = screenH - 2.0f * SCREEN_SIDE_OFFSET;

        float borderScaleX = screenW / (float) Texture.FULLSCREEN_MAP_BORDER.width();
        float borderScaleY = screenH / (float) Texture.FULLSCREEN_MAP_BORDER.height();

        float renderedBorderXOffset = BORDER_OFFSET * borderScaleX;
        float renderedBorderYOffset = BORDER_OFFSET * borderScaleY;

        float innerX = renderX + renderedBorderXOffset;
        float innerY = renderY + renderedBorderYOffset;
        float innerWidth = renderWidth - 2.0f * renderedBorderXOffset;
        float innerHeight = renderHeight - 2.0f * renderedBorderYOffset;

        float centerX = innerX + innerWidth / 2.0f;
        float centerZ = innerY + innerHeight / 2.0f;

        float playerX = (float) client.player.getX();
        float playerZ = (float) client.player.getZ();

        float zoomRenderScale = MapRenderer.getZoomRenderScaleFromLevel(zoomLevel);

        BoundingBox viewBox = BoundingBox.centered(
                playerX, playerZ,
                innerWidth / zoomRenderScale,
                innerHeight / zoomRenderScale
        );

        float terrainOpacity = config.getTerrainOpacity();
        int bgAlpha = Math.clamp(Math.round(terrainOpacity * 255.0f), 0, 255);

        int drawn = 0;

        // Scissor to inner map rect
        RenderUtils.enableScissor(guiGraphics, (int) innerX, (int) innerY, (int) innerWidth, (int) innerHeight);
        try {
            // 1. Black bg with same alpha as terrain
            guiGraphics.fill((int) innerX, (int) innerY, (int) (innerX + innerWidth), (int) (innerY + innerHeight), (bgAlpha << 24));

            // 2. Terrain tiles with configured opacity
            List<MapTexture> tiles = Services.Map.getMapsForBoundingBox(viewBox);
            if (tiles != null) {
                for (MapTexture tile : tiles) {
                    renderMapTileAlpha(
                            guiGraphics, tile,
                            playerX, playerZ,
                            centerX, centerZ,
                            zoomRenderScale, viewBox,
                            terrainOpacity
                    );
                }
            }

            // 3. Territories
            List<TerritoryPoi> pois = Models.Territory.getTerritoryPoisFromAdvancement();
            if (pois != null) {
                for (TerritoryPoi poi : pois) {
                    if (poi.getLocation() == null) continue;
                    if (!poi.isVisible(zoomRenderScale, zoomLevel)) continue;

                    float renderX_poi = MapRenderer.getRenderX(poi, playerX, centerX, zoomRenderScale);
                    float renderZ_poi = MapRenderer.getRenderZ(poi, playerZ, centerZ, zoomRenderScale);

                    if (renderX_poi < innerX - 200 || renderX_poi > innerX + innerWidth + 200) continue;
                    if (renderZ_poi < innerY - 200 || renderZ_poi > innerY + innerHeight + 200) continue;

                    poi.renderAt(guiGraphics, renderX_poi, renderZ_poi, false, 1.0f, zoomRenderScale, zoomLevel, true);
                    drawn++;
                }
            }

            // 4. Defense tint
            DefenseColorOverlayFeature defenseFeature = FeatureManager.getDefenseColorOverlayFeature();
            if (defenseFeature != null && !defenseFeature.isDisabled()) {
                defenseFeature.renderDefenseTints(guiGraphics, playerX, playerZ, centerX, centerZ, zoomRenderScale, config.getDefenseTintAlpha());
            }

            // 5. Player pointer (cursor)
            PointerType pointerType = PointerType.ARROW;
            CustomColor pointerColor = CommonColors.WHITE;
            try {
                GuildMapFeature guildMapFeature = Managers.Feature.getFeatureInstance(GuildMapFeature.class);
                if (guildMapFeature != null) {
                    pointerType = guildMapFeature.pointerType.get();
                    pointerColor = guildMapFeature.pointerColor.get();
                }
            } catch (Throwable ignored) {
            }
            MapRenderer.renderCursor(
                    guiGraphics,
                    centerX, centerZ,
                    1.5f,
                    pointerColor.withAlpha(config.getPointerOpacity()),
                    pointerType,
                    false
            );
        } finally {
            RenderUtils.disableScissor(guiGraphics);
        }

        // 6. Border (after disabling scissor)
        if (config.isShowBorder()) {
            RenderUtils.drawTexturedRect(
                    guiGraphics,
                    Texture.FULLSCREEN_MAP_BORDER.identifier(),
                    CommonColors.WHITE.withAlpha(config.getBorderOpacity()),
                    renderX, renderY,
                    renderWidth, renderHeight,
                    Texture.FULLSCREEN_MAP_BORDER.width(),
                    Texture.FULLSCREEN_MAP_BORDER.height()
            );
        }

        // Log toggle line once on toggle
        if (pendingToggleLog) {
            pendingToggleLog = false;
            PotsmodClient.LOGGER.info("[WorldMapHud] overlay toggle ON: zoomLevel={} rect={}x{} territoriesDrawn={}",
                    zoomLevel, (int) innerWidth, (int) innerHeight, drawn);
        }
    }

    private void renderMapTileAlpha(
            GuiGraphics guiGraphics,
            MapTexture tile,
            float mapCenterX,
            float mapCenterZ,
            float centerX,
            float centerZ,
            float zoomRenderScale,
            BoundingBox viewBox,
            float alpha) {
        float tileX1 = tile.getX1();
        float tileZ1 = tile.getZ1();
        float tileX2 = tile.getX2() + 1.0f;
        float tileZ2 = tile.getZ2() + 1.0f;

        float clampX1 = Math.max(viewBox.x1(), tileX1);
        float clampZ1 = Math.max(viewBox.z1(), tileZ1);
        float clampX2 = Math.min(viewBox.x2(), tileX2);
        float clampZ2 = Math.min(viewBox.z2(), tileZ2);

        if (clampX1 >= clampX2 || clampZ1 >= clampZ2) {
            return;
        }

        float renderX1 = centerX + (clampX1 - mapCenterX) * zoomRenderScale;
        float renderZ1 = centerZ + (clampZ1 - mapCenterZ) * zoomRenderScale;
        float renderX2 = centerX + (clampX2 - mapCenterX) * zoomRenderScale;
        float renderZ2 = centerZ + (clampZ2 - mapCenterZ) * zoomRenderScale;

        float u1 = clampX1 - tileX1;
        float v1 = clampZ1 - tileZ1;
        float u2 = clampX2 - tileX1;
        float v2 = clampZ2 - tileZ1;

        RenderUtils.drawTexturedRect(
                guiGraphics,
                tile.identifier(),
                CommonColors.WHITE.withAlpha(alpha),
                renderX1,
                renderZ1,
                renderX2 - renderX1,
                renderZ2 - renderZ1,
                u1,
                v1,
                u2 - u1,
                v2 - v1,
                tile.getTextureWidth(),
                tile.getTextureHeight()
        );
    }
}
