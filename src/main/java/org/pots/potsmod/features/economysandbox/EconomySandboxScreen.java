/*
 * Copyright © Wynntils 2024-2026.
 * Copyright © RMod 2026.
 * This file is released under LGPLv3. See LICENSE for full license details.
 */
package org.pots.potsmod.features.economysandbox;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.wynntils.models.territories.TerritoryInfo;
import com.wynntils.models.territories.profile.TerritoryProfile;
import com.wynntils.models.territories.type.GuildResource;
import com.wynntils.models.territories.type.TerritoryUpgrade;
import com.wynntils.screens.maps.AbstractMapScreen;
import com.wynntils.screens.maps.widgets.MapButton;
import com.wynntils.utils.colors.CommonColors;
import com.wynntils.utils.colors.CustomColor;
import com.wynntils.utils.render.MapRenderer;
import com.wynntils.utils.render.RenderUtils;
import com.wynntils.utils.render.Texture;
import org.pots.potsmod.models.records.TerritoryCombatStats;
import org.pots.potsmod.util.TerritoryUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.pots.potsmod.features.economysandbox.EconomySandboxData.SandboxUpgrade;
import org.pots.potsmod.features.economysandbox.EconomySandboxData.TreasuryTier;
import org.pots.potsmod.gui.GuiStyle;
import org.pots.potsmod.gui.render.UiRect;
import org.pots.potsmod.managers.TerritoryBaseManager;
import org.pots.potsmod.models.records.TerritoryBase;
import org.pots.potsmod.render.Render2D;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class EconomySandboxScreen extends AbstractMapScreen {
    private static final int BG_COLOR = 0xFF000000; // Opaque black background
    private static final int BORDER_COLOR = 0xFF2B2B2F; // lines and UI borders
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFAAAAAA;

    private static final Identifier RES_ORE = Identifier.fromNamespaceAndPath("potsmod", "textures/resources/ore.png");
    private static final Identifier RES_WOOD = Identifier.fromNamespaceAndPath("potsmod", "textures/resources/wood.png");
    private static final Identifier RES_FISH = Identifier.fromNamespaceAndPath("potsmod", "textures/resources/fish.png");
    private static final Identifier RES_CROPS = Identifier.fromNamespaceAndPath("potsmod", "textures/resources/crops.png");

    private final EconomySandboxState sandboxState;

    // Top search & autocomplete
    private EditBox searchBox;
    private String searchQuery = "";
    private final List<String> autocompleteMatches = new ArrayList<>();

    // fa-rog Upgrades and Bonuses Overlay State
    private boolean upgradeOverlayOpen = false;
    private List<Component> hoveredSlotTooltip = null;

    // Bottom Map Buttons
    private MapButton treasuryButton;

    // Map drag tracking
    private double mouseDownX;
    private double mouseDownY;
    private boolean isMapDragging = false;

    // Smooth cursor-anchored zooming
    private float targetZoomLevel = -1.0f;
    private float targetMapCenterX = Float.NaN;
    private float targetMapCenterZ = Float.NaN;

    // Modals
    private enum ModalMode {
        NONE,
        TRIBUTES,
        LOADOUTS,
        SAVES,
        CLEAR_CONFIRM,
        SHARE_STRING,
        PROMPT_INPUT
    }
    private ModalMode modal = ModalMode.NONE;

    // Loadout Chest GUI state
    private int selectedLoadoutSlotIndex = -1;
    private int activeBulkLoadoutIndex = -1;
    private int hoveredLoadoutSlotIndex = -1;
    private boolean isSelectingTerritoryForSlot = false;

    // Eco Saves Chest GUI state
    private int selectedSaveSlotIndex = -1;

    // Tribute inputs
    private final Map<GuildResource, EditBox> tributeInputBoxes = new EnumMap<>(GuildResource.class);

    // Loadout modal state
    private List<String> loadoutNames = new ArrayList<>();
    private String selectedLoadoutName = null;
    private EditBox newLoadoutNameBox;
    private double loadoutScroll = 0;

    // Share String modal state
    private EditBox shareStringBox;
    private String shareStringFeedback = "";
    private boolean shareStringSuccess = false;

    // Prompt modal state (rename loadout, import file path, export file path)
    private String promptTitle = "";
    private String promptMessage = "";
    private EditBox promptInputBox;
    private java.util.function.Consumer<String> promptAction = null;

    // Persistent view state across sandbox closes
    private static float lastMapCenterX = Float.NaN;
    private static float lastMapCenterZ = Float.NaN;
    private static float lastZoomLevel = Float.NaN;

    // 0 = Default, 1 = Defense Tier, 2 = Production Upgrades
    private int colorMode = 1;
    private MapButton colorModeButton;

    public EconomySandboxScreen() {
        super();
        this.sandboxState = EconomySandboxStorage.loadCurrentState();
        this.shouldCenterMap = Float.isNaN(lastMapCenterX);
    }

    public EconomySandboxScreen(int initialSaveSlot) {
        super();
        this.sandboxState = EconomySandboxStorage.loadCurrentState();
        this.modal = ModalMode.SAVES;
        this.selectedSaveSlotIndex = initialSaveSlot;
        this.shouldCenterMap = Float.isNaN(lastMapCenterX);
    }

    @Override
    protected void doInit() {
        super.doInit();

        // Restore exact view position and zoom if previously viewed, else default to HQ or center
        if (!Float.isNaN(lastMapCenterX) && !Float.isNaN(lastMapCenterZ) && !Float.isNaN(lastZoomLevel)) {
            updateMapCenter(lastMapCenterX, lastMapCenterZ);
            setZoomLevel(lastZoomLevel);
        } else if (sandboxState.getHqTerritory() != null) {
            TerritoryProfile hqProfile = com.wynntils.core.components.Models.Territory.getTerritoryProfile(sandboxState.getHqTerritory());
            if (hqProfile != null) {
                updateMapCenter((hqProfile.getStartX() + hqProfile.getEndX()) / 2f, (hqProfile.getStartZ() + hqProfile.getEndZ()) / 2f);
            } else {
                updateMapCenter(MAP_CENTER_X, MAP_CENTER_Z);
            }
        } else {
            updateMapCenter(MAP_CENTER_X, MAP_CENTER_Z);
        }

        // Search EditBox in top bar
        searchBox = new EditBox(font, 130, 14, 110, 16, GuiStyle.styled("Add territory"));
        searchBox.setBordered(false);
        searchBox.setTextColor(0xFFFFFFFF);
        searchBox.setCanLoseFocus(true);
        GuiStyle.applyFont(searchBox, "Add territory...");
        searchBox.setResponder(val -> {
            searchQuery = val;
            updateAutocomplete();
        });
        searchBox.setValue(searchQuery);

        initTributeInputs();
        // initLoadoutInputs(); // Loadout inputs disabled per user request
        initShareStringInputs();
        initPromptInputs();

        initMapButtons();
    }

    private void initMapButtons() {
        mapButtons.clear();

        // 1. Green arrow: closes the sandbox
        addMapButton(new MapButton(
                Texture.ARROW_LEFT_ICON,
                (button) -> this.onClose(),
                List.of(Component.literal("[>] ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.translatable("gui.back")))));

        // 2. Treasury: cycles the global treasury Very Low > Low > Medium > High > Very High
        treasuryButton = new MapButton(
                Texture.TREASURY,
                (button) -> {
                    TreasuryTier next = button == 1
                            ? sandboxState.getDefaultTreasury().prev()
                            : sandboxState.getDefaultTreasury().next();
                    sandboxState.setDefaultTreasury(next);
                    if (treasuryButton != null) {
                        treasuryButton.setTooltip(getTreasuryButtonTooltip());
                    }
                    persist();
                },
                getTreasuryButtonTooltip());
        addMapButton(treasuryButton);

        // Loadouts button removed as per user request
// addMapButton(new MapButton(
//        Texture.TERRITORY_LOADOUT,
//        (button) -> {
//            selectedLoadoutSlotIndex = -1;
//            modal = ModalMode.LOADOUTS;
//        },
//        List.of(
//                Component.literal("Territory Loadouts")
//                        .withStyle(ChatFormatting.GOLD),
//                Component.literal("Configure & bulk-apply upgrade presets")
//                        .withStyle(ChatFormatting.GRAY))));

        // 4. Eco Loadouts: Full Sandbox Save / Load / Share
        addMapButton(new MapButton(
                Texture.TERRITORY_LOADOUT,
                (button) -> {
                    modal = ModalMode.SAVES;
                },
                List.of(
                        Component.literal("Eco Loadouts")
                                .withStyle(ChatFormatting.GOLD),
                        Component.literal("Manage, share & bulk-load eco setups across 27 slots")
                                .withStyle(ChatFormatting.GRAY))));

        // 5. Load Owned Territories & Real Defenses (Auto-syncs live via /gu manage)
        addMapButton(new MapButton(
                Texture.GUILD_HEADQUARTERS,
                (button) -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.setScreen(null); // Close economy sandbox immediately
                    AutoTerritorySyncHandler.start(sandboxState, () -> {
                        persist();
                    });
                },
                List.of(
                        Component.literal("Load Current Territories")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        Component.literal("Instantly syncs live territories, defenses, HQ & treasuries")
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("Automatically opens /gu manage and captures exact live data")
                                .withStyle(ChatFormatting.DARK_GRAY))));

        // 6. Territory Color Mode (Defense Tier -> Production Upgrades -> Default)
        colorModeButton = new MapButton(
                Texture.DEFENSE_FILTER_ICON,
                (button) -> {
                    colorMode = (colorMode + 1) % 3;
                    updateColorModeButtonTooltip();
                },
                getColorModeButtonTooltip()
        );
        addMapButton(colorModeButton);
    }

    private List<Component> getColorModeButtonTooltip() {
        String modeName = switch (colorMode) {
            case 2 -> "Production Upgrades";
            case 0 -> "Default";
            default -> "Defense Tier";
        };
        String desc = switch (colorMode) {
            case 2 -> "Boxes colored by Production Upgrades (yellow=resources, green=emeralds). Click for Default.";
            case 0 -> "Boxes colored standard. Click for Defense Tier.";
            default -> "Boxes colored by Defense Tier. Click for Production Upgrades.";
        };
        return List.of(
                Component.literal("Territory Color Mode: " + modeName)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal(desc).withStyle(ChatFormatting.GRAY)
        );
    }

    private void updateColorModeButtonTooltip() {
        if (colorModeButton != null) {
            colorModeButton.setTooltip(getColorModeButtonTooltip());
        }
    }

    public List<CustomColor> getProductionColorsForTerritory(String name) {
        int emRate = 0;
        int effEm = 0;
        int resRate = 0;
        int effRes = 0;

        EconomyTerritoryState terrState = sandboxState.getTerritory(name);
        if (terrState != null) {
            emRate = terrState.getUpgradeLevel(EconomySandboxData.SandboxUpgrade.EMERALD_RATE);
            effEm = terrState.getUpgradeLevel(EconomySandboxData.SandboxUpgrade.EFFICIENT_EMERALDS);
            resRate = terrState.getUpgradeLevel(EconomySandboxData.SandboxUpgrade.RESOURCE_RATE);
            effRes = terrState.getUpgradeLevel(EconomySandboxData.SandboxUpgrade.EFFICIENT_RESOURCES);
        }

        // Fallback to LiveTerritoryDataManager cache if not modified in sandbox
        if (emRate == 0 && effEm == 0 && resRate == 0 && effRes == 0) {
            LiveTerritoryDataManager.CachedTerritory cached = LiveTerritoryDataManager.getCached(name);
            if (cached != null && cached.upgrades != null) {
                emRate = cached.upgrades.getOrDefault("EMERALD_RATE", 0);
                effEm = cached.upgrades.getOrDefault("EFFICIENT_EMERALDS", 0);
                resRate = cached.upgrades.getOrDefault("RESOURCE_RATE", 0);
                effRes = cached.upgrades.getOrDefault("EFFICIENT_RESOURCES", 0);
            }
        }

        int emeraldLevels = emRate + effEm;
        int resourceLevels = resRate + effRes;

        List<CustomColor> colors = new ArrayList<>();
        if (emeraldLevels > 0) {
            colors.add(CustomColor.fromHSV(0.33333334f, 0.8f, 0.9f, 1.0f)); // Wynntils Green
        }
        if (resourceLevels > 6) {
            colors.add(CustomColor.fromHSV(0.1f, 0.9f, 0.9f, 1.0f)); // Wynntils Orange
        } else if (resourceLevels == 6) {
            colors.add(CustomColor.fromHSV(0.16666667f, 0.8f, 0.9f, 1.0f)); // Wynntils Yellow
        } else if (resourceLevels > 0) {
            colors.add(CustomColor.fromHSV(0.16666667f, 0.5f, 0.8f, 1.0f)); // Wynntils Light Yellow
        }

        if (colors.isEmpty()) {
            colors.add(CustomColor.fromHSV(0.0f, 0.0f, 0.6f, 1.0f)); // Wynntils Gray
        }
        return colors;
    }

    private List<Component> getTreasuryButtonTooltip() {
        TreasuryTier tier = sandboxState.getDefaultTreasury();
        int pct = (int) Math.round(tier.getRate() * 100);
        return List.of(
                Component.literal("Global Treasury: " + tier.getDisplayName())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal("Production Bonus: +" + pct + "%")
                        .withStyle(ChatFormatting.GREEN),
                Component.empty(),
                Component.literal("Left-click: Next tier (up to Very High)")
                        .withStyle(ChatFormatting.GRAY),
                Component.literal("Right-click: Previous tier (down to Very Low)")
                        .withStyle(ChatFormatting.GRAY));
    }

    private void initTributeInputs() {
        tributeInputBoxes.clear();
        for (GuildResource res : GuildResource.values()) {
            EditBox box = new EditBox(font, 0, 0, 70, 14, GuiStyle.styled(res.getName()));
            box.setBordered(false);
            box.setTextColor(0xFFFFFFFF);
            box.setValue(String.valueOf(sandboxState.getTributes().getOrDefault(res, 0)));
            tributeInputBoxes.put(res, box);
        }
    }

    private void initLoadoutInputs() {
        newLoadoutNameBox = new EditBox(font, 0, 0, 130, 14, GuiStyle.styled("Loadout Name"));
        newLoadoutNameBox.setBordered(false);
        newLoadoutNameBox.setTextColor(0xFFFFFFFF);
        GuiStyle.applyFont(newLoadoutNameBox, "New loadout name...");
        refreshLoadoutsList();
    }

    private void initShareStringInputs() {
        shareStringBox = new EditBox(font, 0, 0, 260, 16, GuiStyle.styled("Share String"));
        shareStringBox.setMaxLength(32767);
        shareStringBox.setBordered(false);
        shareStringBox.setTextColor(0xFFFFFFFF);
        GuiStyle.applyFont(shareStringBox, "Paste share string here...");
    }

    private void initPromptInputs() {
        promptInputBox = new EditBox(font, 0, 0, 240, 16, GuiStyle.styled("Input"));
        promptInputBox.setBordered(false);
        promptInputBox.setTextColor(0xFFFFFFFF);
    }

    private void refreshLoadoutsList() {
        loadoutNames = new ArrayList<>(EconomySandboxStorage.listLoadouts());
        if (selectedLoadoutName != null && !loadoutNames.contains(selectedLoadoutName)) {
            selectedLoadoutName = loadoutNames.isEmpty() ? null : loadoutNames.getFirst();
        } else if (selectedLoadoutName == null && !loadoutNames.isEmpty()) {
            selectedLoadoutName = loadoutNames.getFirst();
        }
    }

    private void updateAutocomplete() {
        autocompleteMatches.clear();
        if (searchQuery.trim().isEmpty()) {
            return;
        }
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        Map<String, TerritoryBase> catalog = TerritoryBaseManager.getTerritories();
        if (catalog == null) {
            return;
        }
        for (String name : catalog.keySet()) {
            if (name.toLowerCase(Locale.ROOT).contains(q)) {
                autocompleteMatches.add(name);
                if (autocompleteMatches.size() >= 8) {
                    break;
                }
            }
        }
    }

    private void persist() {
        EconomySandboxStorage.saveCurrentState(sandboxState);
    }

    // =========================================================================
    // RENDERING
    // =========================================================================
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

        // 2. Map scissor & territories
        RenderUtils.enableScissor(
                g,
                (int) (renderX + renderedBorderXOffset),
                (int) (renderY + renderedBorderYOffset),
                (int) mapWidth,
                (int) mapHeight);

        renderConnections(g);
        String hoveredTerr = renderTerritoryBoxes(g, mouseX, mouseY);

        RenderUtils.disableScissor(g);

        // 3. Map border & widgets
        renderMapBorder(g);
        renderCoordinates(g, mouseX, mouseY);
        renderMapButtons(g, mouseX, mouseY, partialTick);
        renderZoomWidgets(g, mouseX, mouseY, partialTick);

        // 4. Left Output Widget (Wynntils Guild Output style)
        renderGuildOutputWidget(g);

        // 5. fa-rog 21-Upgrade Overlay
        if (upgradeOverlayOpen && !sandboxState.getSelectedTerritories().isEmpty()) {
            renderUpgradeOverlay(g, mouseX, mouseY);
        }

        // 6. Map Hover Tooltip (if not hovering UI and no modal)
        if (modal == ModalMode.NONE && hoveredTerr != null && !isMouseOverUI(mouseX, mouseY)) {
            renderTerritoryTooltip(g, hoveredTerr, mouseX, mouseY);
        }

        // 7. Bulk Loadout Banner
        if (activeBulkLoadoutIndex != -1 && modal == ModalMode.NONE) {
            renderBulkApplyBanner(g, mouseX, mouseY);
        }

        // 8. Modals
        if (modal != ModalMode.NONE) {
            renderModal(g, mouseX, mouseY, partialTick);
        }

        // Hovered Slot Tooltip from Chest GUI
        if (hoveredSlotTooltip != null) {
            RenderUtils.renderTooltip(g, hoveredSlotTooltip, mouseX + 12, mouseY);
        }

        // 9. Bottom Map Buttons Tooltips (Treasury, Loadouts, Saves, Back)
        if (modal == ModalMode.NONE && hoveredSlotTooltip == null) {
            super.renderTooltip(g, mouseX, mouseY);
        }
    }

    // --- Territory Route Lines (Connecting all territories) ---
    private void renderConnections(GuiGraphics g) {
        Map<String, TerritoryBase> catalog = TerritoryBaseManager.getTerritories();
        if (catalog == null) return;

        Set<String> drawn = new HashSet<>();

        for (Map.Entry<String, TerritoryBase> entry : catalog.entrySet()) {
            String terrName = entry.getKey();
            TerritoryBase base = entry.getValue();
            if (base == null || base.connections() == null) continue;

            TerritoryProfile p1 = com.wynntils.core.components.Models.Territory.getTerritoryProfile(terrName);
            if (p1 == null) continue;

            float x1 = MapRenderer.getRenderX((p1.getStartX() + p1.getEndX()) / 2, mapCenterX, centerX, zoomRenderScale);
            float z1 = MapRenderer.getRenderZ((p1.getStartZ() + p1.getEndZ()) / 2, mapCenterZ, centerZ, zoomRenderScale);

            for (String connName : base.connections()) {
                String pairKey = terrName.compareTo(connName) < 0 ? terrName + "<->" + connName : connName + "<->" + terrName;
                if (drawn.add(pairKey)) {
                    TerritoryProfile p2 = com.wynntils.core.components.Models.Territory.getTerritoryProfile(connName);
                    if (p2 == null) continue;

                    float x2 = MapRenderer.getRenderX((p2.getStartX() + p2.getEndX()) / 2, mapCenterX, centerX, zoomRenderScale);
                    float z2 = MapRenderer.getRenderZ((p2.getStartZ() + p2.getEndZ()) / 2, mapCenterZ, centerZ, zoomRenderScale);

                    boolean bothAdded = sandboxState.isAdded(terrName) && sandboxState.isAdded(connName);
                    boolean connectedToHq = bothAdded && sandboxState.getHqDistance(terrName) >= 0 && sandboxState.getHqDistance(connName) >= 0;

                    int lineColor;
                    float lineWidth;
                    if (connectedToHq) {
                        lineColor = 0xFF1E90FF; // HQ active trade route: Bright blue
                        lineWidth = 2.0f;
                    } else if (bothAdded) {
                        lineColor = 0xFFFFFACD; // Added connection: warm light cream
                        lineWidth = 1.8f;
                    } else {
                        lineColor = 0xDD0A0A0A; // Base territory route: crisp visible dark line
                        lineWidth = 1.5f;
                    }
                    RenderUtils.drawLine(g, CustomColor.fromARGBInt(lineColor), x1, z1, x2, z2, lineWidth);
                }
            }
        }
    }

    // --- Territory Boxes on Map (Step 1: All Territories, Step 2: Wynntils Colors) ---
    private String renderTerritoryBoxes(GuiGraphics g, int mouseX, int mouseY) {
        Map<String, TerritoryBase> catalog = TerritoryBaseManager.getTerritories();
        if (catalog == null) return null;

        String hoveredTerritory = null;

        for (String name : catalog.keySet()) {
            TerritoryProfile profile = com.wynntils.core.components.Models.Territory.getTerritoryProfile(name);
            if (profile == null) continue;

            float x1 = MapRenderer.getRenderX(profile.getStartX(), mapCenterX, centerX, zoomRenderScale);
            float x2 = MapRenderer.getRenderX(profile.getEndX(), mapCenterX, centerX, zoomRenderScale);
            float z1 = MapRenderer.getRenderZ(profile.getStartZ(), mapCenterZ, centerZ, zoomRenderScale);
            float z2 = MapRenderer.getRenderZ(profile.getEndZ(), mapCenterZ, centerZ, zoomRenderScale);

            float rx = Math.min(x1, x2);
            float rz = Math.min(z1, z2);
            float rw = Math.abs(x2 - x1);
            float rh = Math.abs(z2 - z1);

            // Cull if completely off screen
            if (rx + rw < renderX || rx > renderX + mapWidth || rz + rh < renderY || rz > renderY + mapHeight) {
                continue;
            }

            boolean isHovered = mouseX >= rx && mouseX <= rx + rw && mouseY >= rz && mouseY <= rz + rh;
            if (isHovered && hoveredTerritory == null) {
                hoveredTerritory = name;
            }

            boolean isAdded = sandboxState.isAdded(name);
            boolean isSelected = sandboxState.getSelectedTerritories().contains(name);
            boolean isHq = name.equals(sandboxState.getHqTerritory());

            // Step 2: Defense difficulty color
            // Very Low: 0xFF00AA00, Low: 0xFF55FF55, Medium: 0xFFFFFF55, High: 0xFFFF5555, Very High: 0xFFAA0000
            int defColor;
            if (isAdded) {
                EconomyTerritoryState state = sandboxState.getTerritory(name);
                defColor = state != null ? state.getDefenseDifficultyColor() : 0xFF00AA00;
            } else {
                defColor = 0xFF00AA00; // Very Low
            }

            // Material resources produced by this territory (shows 2 icons if double production)
            TerritoryBase base = catalog.get(name);
            List<Identifier> prodIcons = new ArrayList<>();
            if (base != null) {
                addResourceIcons(prodIcons, RES_ORE, base.getProduction(GuildResource.ORE));
                addResourceIcons(prodIcons, RES_WOOD, base.getProduction(GuildResource.WOOD));
                addResourceIcons(prodIcons, RES_FISH, base.getProduction(GuildResource.FISH));
                addResourceIcons(prodIcons, RES_CROPS, base.getProduction(GuildResource.CROPS));
            }

            // Check if territory is cut off (added but disconnected from HQ)
            boolean isCutOff = isAdded && sandboxState.getHqTerritory() != null
                    && !name.equals(sandboxState.getHqTerritory())
                    && sandboxState.getHqDistance(name) < 0;

            // Check if territory has multihit (TOWER_MULTI_ATTACKS level > 0)
            boolean hasMultihit = false;
            if (isAdded) {
                EconomyTerritoryState terrState = sandboxState.getTerritory(name);
                hasMultihit = terrState != null && terrState.getUpgradeLevel(EconomySandboxData.SandboxUpgrade.TOWER_MULTI_ATTACKS) > 0;
            }

            if (colorMode == 2) {
                // Production Upgrades Mode: Yellow = resources, Green = emeralds, Mixed = both
                List<CustomColor> pColors = getProductionColorsForTerritory(name);
                int fillAlpha = isHovered ? 125 : (isSelected ? 110 : 80);

                if (pColors.size() > 1) {
                    List<CustomColor> alphaColors = pColors.stream().map(c -> c.withAlpha(fillAlpha)).toList();
                    RenderUtils.drawMulticoloredRect(g, alphaColors, rx, rz, rw, rh);
                    int borderColor = isCutOff ? 0xFFFF3333 : (isSelected ? 0xFF00FF88 : (isHq ? 0xFFFFD700 : 0xFF88FF55));
                    RenderUtils.drawMulticoloredRectBorders(g, pColors, rx, rz, rw, rh, 1.5f, 0.5f);
                } else {
                    int pCol = pColors.get(0).asInt() & 0x00FFFFFF;
                    int fillColor = (fillAlpha << 24) | pCol;
                    g.fill((int) rx, (int) rz, (int) (rx + rw), (int) (rz + rh), fillColor);
                    int borderColor = isCutOff ? 0xFFFF3333 : (isSelected ? 0xFF00FF88 : (isHq ? 0xFFFFD700 : (0xFF000000 | pCol)));
                    RenderUtils.drawRectBorders(g, CustomColor.fromARGBInt(borderColor), rx, rz, rx + rw, rz + rh, 1.5f);
                }
            } else if (isAdded) {
                int fillAlpha = isHovered ? 125 : (isSelected ? 110 : 80);

                if (colorMode == 0) {
                    // Default Mode
                    int fillColor = (fillAlpha << 24) | 0x00AAAAAA;
                    g.fill((int) rx, (int) rz, (int) (rx + rw), (int) (rz + rh), fillColor);
                } else if (hasMultihit) {
                    // Diagonal split: top-left triangle = Aqua, bottom-right = defColor
                    int aquaFill = (fillAlpha << 24) | 0x0055FFFF;
                    int defFill  = (fillAlpha << 24) | (defColor & 0x00FFFFFF);
                    // Draw the full rect with defColor first
                    g.fill((int) rx, (int) rz, (int) (rx + rw), (int) (rz + rh), defFill);
                    // Overlay a diagonal aqua triangle using horizontal scanlines
                    int iRx = (int) rx, iRz = (int) rz, iRw = (int) rw, iRh = (int) rh;
                    for (int row = 0; row < iRh; row++) {
                        int aquaEndX = iRx + iRw - (int) Math.round(((double) row / iRh) * iRw);
                        if (aquaEndX > iRx) {
                            g.fill(iRx, iRz + row, Math.min(aquaEndX, iRx + iRw), iRz + row + 1, aquaFill);
                        }
                    }
                } else {
                    int fillColor = (fillAlpha << 24) | (defColor & 0x00FFFFFF);
                    g.fill((int) rx, (int) rz, (int) (rx + rw), (int) (rz + rh), fillColor);
                }

                int borderColor;
                if (isCutOff) {
                    borderColor = 0xFFFF3333; // Cut-off: bright red
                } else if (isSelected) {
                    borderColor = 0xFF00FF88; // Bright Emerald Selection
                } else if (isHq) {
                    borderColor = 0xFFFFD700; // HQ Gold
                } else if (colorMode == 0) {
                    borderColor = 0xFFAAAAAA;
                } else {
                    borderColor = 0xFF000000 | (defColor & 0x00FFFFFF); // Wynntils Defense Border
                }
                RenderUtils.drawRectBorders(g, CustomColor.fromARGBInt(borderColor), rx, rz, rx + rw, rz + rh, 1.5f);
            } else {
                // Unadded territory: 10% white fill (NOT defColor)
                int fillAlpha = 0x1A; // ~10% opacity white
                int fillColor = (fillAlpha << 24) | 0x00FFFFFF;
                g.fill((int) rx, (int) rz, (int) (rx + rw), (int) (rz + rh), fillColor);

                // Border: use the defense color of the territory (very low = dark green) at low opacity
                int borderAlpha = isHovered ? 180 : 70;
                // For unadded territories, always show "Very Low" color (0x00AA00) since no upgrades
                int borderColor = (borderAlpha << 24) | 0x0000AA00;
                RenderUtils.drawRectBorders(g, CustomColor.fromARGBInt(borderColor), rx, rz, rx + rw, rz + rh, 1.2f);
            }

            // Draw resource icons & prefix initials
            String initials = getInitials(name);
            int tw = font.width(initials);
            int labelCol = isAdded ? (isSelected ? 0xFF00FF88 : (isHq ? 0xFFFFD700 : (0xFF000000 | (defColor & 0x00FFFFFF))))
                                   : (isHovered ? 0xFF55FF55 : 0xFF00AA00);

            if (!prodIcons.isEmpty() && rw >= 14 && rh >= 16) {
                int iconCount = prodIcons.size();
                int maxIconH = Math.min(12, Math.max(8, (int) (rh - font.lineHeight - 2)));
                int iconSize = Math.min(maxIconH, Math.max(8, (int) (rw / iconCount - 1)));
                if (iconSize >= 8 && rh >= iconSize + font.lineHeight) {
                    int totalIconsW = iconCount * iconSize + (iconCount - 1) * 2;
                    float iconsX = rx + (rw - totalIconsW) / 2.0f;
                    float iconsY = rz + Math.max(2, (rh - (iconSize + font.lineHeight)) / 2.0f);
                    for (int i = 0; i < iconCount; i++) {
                        RenderUtils.drawScalingTexturedRect(g, prodIcons.get(i), iconsX + i * (iconSize + 2), iconsY, iconSize, iconSize, 16, 16);
                    }
                    int tx = (int) (rx + (rw - tw) / 2.0f);
                    int ty = (int) (iconsY + iconSize + 1);
                    g.drawString(font, initials, tx, ty, labelCol, true);
                } else {
                    int tx = (int) (rx + (rw - tw) / 2.0f);
                    int ty = (int) (rz + (rh - font.lineHeight) / 2.0f);
                    g.drawString(font, initials, tx, ty, labelCol, true);
                }
            } else {
                int tx = (int) (rx + (rw - tw) / 2.0f);
                int ty = (int) (rz + (rh - font.lineHeight) / 2.0f);
                g.drawString(font, initials, tx, ty, labelCol, true);
            }

            // Status markers (HQ crown / Checkmark)
            if (isAdded) {
                if (isHq && rw >= 20 && rh >= 16) {
                    RenderUtils.drawTexturedRect(g, Texture.GUILD_HEADQUARTERS, (int) rx + 2, (int) rz + 2);
                } else if (isHq && rw >= 12) {
                    g.drawString(font, "♛", (int) rx + 2, (int) rz + 2, 0xFFFFD700, true);
                }
                if (isSelected && rw >= 20 && rh >= 16) {
                    RenderUtils.drawTexturedRect(g, Texture.CHECKMARK_GREEN, (int) (rx + rw - 18), (int) rz + 2);
                } else if (isSelected && rw >= 12) {
                    g.drawString(font, "✓", (int) (rx + rw - 8), (int) rz + 2, 0xFF00FF88, true);
                }
            }
        }

        return hoveredTerritory;
    }

    private String getInitials(String name) {
        String[] parts = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(p.charAt(0));
            }
        }
        return sb.toString();
    }

    // --- Left Output Widget (Wynntils Guild Output style - Step 3) ---
    private void renderGuildOutputWidget(GuiGraphics g) {
        int x = 10;
        int y = 42;

        int emeraldProduction = sandboxState.getOverallProduction(GuildResource.EMERALDS);
        int oreProduction = sandboxState.getOverallProduction(GuildResource.ORE);
        int woodProduction = sandboxState.getOverallProduction(GuildResource.WOOD);
        int fishProduction = sandboxState.getOverallProduction(GuildResource.FISH);
        int cropsProduction = sandboxState.getOverallProduction(GuildResource.CROPS);

        int emeraldStorage = sandboxState.getOverallStorageMax(GuildResource.EMERALDS);
        int oreStorage = sandboxState.getOverallStorageMax(GuildResource.ORE);
        int woodStorage = sandboxState.getOverallStorageMax(GuildResource.WOOD);
        int fishStorage = sandboxState.getOverallStorageMax(GuildResource.FISH);
        int cropsStorage = sandboxState.getOverallStorageMax(GuildResource.CROPS);

        long emeraldUsage = sandboxState.getOverallUsage(GuildResource.EMERALDS);
        long oreUsage = sandboxState.getOverallUsage(GuildResource.ORE);
        long woodUsage = sandboxState.getOverallUsage(GuildResource.WOOD);
        long fishUsage = sandboxState.getOverallUsage(GuildResource.FISH);
        long cropsUsage = sandboxState.getOverallUsage(GuildResource.CROPS);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Guild Output").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        lines.add(Component.literal(""));
        lines.add(Component.literal("Total resource output").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal(""));

        lines.add(Component.literal("+%d Emeralds per Hour".formatted(emeraldProduction))
                .withStyle(ChatFormatting.GREEN));
        lines.add(Component.literal("0/%d in storage".formatted(emeraldStorage)).withStyle(ChatFormatting.GREEN));
        lines.add(Component.literal("Ⓑ +%d Ore per Hour".formatted(oreProduction)).withStyle(ChatFormatting.WHITE));
        lines.add(Component.literal("Ⓑ 0/%d in storage".formatted(oreStorage)).withStyle(ChatFormatting.WHITE));
        lines.add(Component.literal("Ⓒ +%d Wood per Hour".formatted(woodProduction)).withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("Ⓒ 0/%d in storage".formatted(woodStorage)).withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("Ⓚ +%d Fish per Hour".formatted(fishProduction)).withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("Ⓚ 0/%d in storage".formatted(fishStorage)).withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("Ⓙ +%d Crops per Hour".formatted(cropsProduction)).withStyle(ChatFormatting.YELLOW));
        lines.add(Component.literal("Ⓙ 0/%d in storage".formatted(cropsStorage)).withStyle(ChatFormatting.YELLOW));

        lines.add(Component.literal(""));

        long emeraldDelta = emeraldProduction - emeraldUsage;
        long oreDelta = oreProduction - oreUsage;
        long woodDelta = woodProduction - woodUsage;
        long fishDelta = fishProduction - fishUsage;
        long cropDelta = cropsProduction - cropsUsage;

        lines.add(Component.literal("Overall Cost (per hour):").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("%.1fk Emeralds (%.1f%%)"
                        .formatted(emeraldUsage / 1000d, emeraldProduction > 0 ? (double) emeraldUsage / emeraldProduction * 100d : 0d))
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(" [%s%.1fk]".formatted(emeraldDelta >= 0 ? "+" : "", emeraldDelta / 1000d))
                        .withStyle(emeraldDelta >= 0 ? ChatFormatting.BLUE : ChatFormatting.RED)));
        lines.add(Component.literal("Ⓑ %.1fk Ore (%.1f%%)"
                        .formatted(oreUsage / 1000d, oreProduction > 0 ? (double) oreUsage / oreProduction * 100d : 0d))
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" [%s%.1fk]".formatted(oreDelta >= 0 ? "+" : "", oreDelta / 1000d))
                        .withStyle(oreDelta >= 0 ? ChatFormatting.BLUE : ChatFormatting.RED)));
        lines.add(Component.literal("Ⓒ %.1fk Wood (%.1f%%)"
                        .formatted(woodUsage / 1000d, woodProduction > 0 ? (double) woodUsage / woodProduction * 100d : 0d))
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" [%s%.1fk]".formatted(woodDelta >= 0 ? "+" : "", woodDelta / 1000d))
                        .withStyle(woodDelta >= 0 ? ChatFormatting.BLUE : ChatFormatting.RED)));
        lines.add(Component.literal("Ⓚ %.1fk Fish (%.1f%%)"
                        .formatted(fishUsage / 1000d, fishProduction > 0 ? (double) fishUsage / fishProduction * 100d : 0d))
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" [%s%.1fk]".formatted(fishDelta >= 0 ? "+" : "", fishDelta / 1000d))
                        .withStyle(fishDelta >= 0 ? ChatFormatting.BLUE : ChatFormatting.RED)));
        lines.add(Component.literal("Ⓙ %.1fk Crops (%.1f%%)"
                        .formatted(cropsUsage / 1000d, cropsProduction > 0 ? (double) cropsUsage / cropsProduction * 100d : 0d))
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" [%s%.1fk]".formatted(cropDelta >= 0 ? "+" : "", cropDelta / 1000d))
                        .withStyle(cropDelta >= 0 ? ChatFormatting.BLUE : ChatFormatting.RED)));

        RenderUtils.renderTooltip(g, lines, x, y);
    }

    private String formatShort(long num) {
        if (Math.abs(num) >= 1000) {
            return String.format(Locale.ROOT, "%.1fk", num / 1000.0);
        }
        return String.valueOf(num);
    }

    private static void addResourceIcons(List<Identifier> list, Identifier icon, int amount) {
        if (amount <= 0) return;
        int count = amount > 3600 ? 2 : 1;
        for (int i = 0; i < count; i++) {
            list.add(icon);
        }
    }

    // --- fa-rog 21-Upgrade Overlay Replica ---
    private static class UpgradeSlotDef {
        final int col;
        final int row;
        final SandboxUpgrade upgrade;
        final Identifier iconTexture;
        final int maxLevel;

        UpgradeSlotDef(int col, int row, SandboxUpgrade upgrade, String textureFile, int maxLevel) {
            this.col = col;
            this.row = row;
            this.upgrade = upgrade;
            this.iconTexture = Identifier.fromNamespaceAndPath("potsmod", "textures/upgrades/" + textureFile);
            this.maxLevel = maxLevel;
        }
    }

    private static final List<UpgradeSlotDef> UPGRADE_SLOTS = List.of(
            // Row 0 (4 items): Tower stats
            new UpgradeSlotDef(0, 0, SandboxUpgrade.DAMAGE, "damage.png", 11),
            new UpgradeSlotDef(1, 0, SandboxUpgrade.ATTACK, "attack.png", 11),
            new UpgradeSlotDef(2, 0, SandboxUpgrade.HEALTH, "health.png", 11),
            new UpgradeSlotDef(3, 0, SandboxUpgrade.DEFENCE, "defence.png", 11),

            // Row 1 (4 items): Tower abilities
            new UpgradeSlotDef(0, 1, SandboxUpgrade.STRONGER_MINIONS, "minions.png", 4),
            new UpgradeSlotDef(1, 1, SandboxUpgrade.TOWER_MULTI_ATTACKS, "multi.png", 1),
            new UpgradeSlotDef(2, 1, SandboxUpgrade.TOWER_AURA, "aura.png", 3),
            new UpgradeSlotDef(3, 1, SandboxUpgrade.TOWER_VOLLEY, "volley.png", 3),

            // Row 2 (7 items): Bonuses & Seekers
            new UpgradeSlotDef(0, 2, SandboxUpgrade.GATHERING_EXPERIENCE, "gathering_xp.png", 8),
            new UpgradeSlotDef(1, 2, SandboxUpgrade.MOB_EXPERIENCE, "mob_xp.png", 8),
            new UpgradeSlotDef(2, 2, SandboxUpgrade.MOB_DAMAGE, "mob_damage.png", 8),
            new UpgradeSlotDef(3, 2, SandboxUpgrade.PVP_DAMAGE, "pvp_damage.png", 8),
            new UpgradeSlotDef(4, 2, SandboxUpgrade.XP_SEEKING, "xp_seeking.png", 9),
            new UpgradeSlotDef(5, 2, SandboxUpgrade.TOME_SEEKING, "tome_seeking.png", 3),
            new UpgradeSlotDef(6, 2, SandboxUpgrade.EMERALD_SEEKING, "emerald_seeking.png", 5),

            // Row 3 (6 items): Storages & Rates
            new UpgradeSlotDef(0, 3, SandboxUpgrade.RESOURCE_STORAGE, "resource_storage.png", 6),
            new UpgradeSlotDef(1, 3, SandboxUpgrade.EMERALD_STORAGE, "emerald_storage.png", 6),
            new UpgradeSlotDef(2, 3, SandboxUpgrade.EFFICIENT_RESOURCES, "efficient_resources.png", 6),
            new UpgradeSlotDef(3, 3, SandboxUpgrade.EFFICIENT_EMERALDS, "efficient_emeralds.png", 3),
            new UpgradeSlotDef(4, 3, SandboxUpgrade.RESOURCE_RATE, "resource_rate.png", 3),
            new UpgradeSlotDef(5, 3, SandboxUpgrade.EMERALD_RATE, "emerald_rate.png", 3)
    );

    private int getEffectiveUpgradeLevel(SandboxUpgrade upgrade) {
        int minLevel = Integer.MAX_VALUE;
        for (String tName : sandboxState.getSelectedTerritories()) {
            EconomyTerritoryState s = sandboxState.getTerritory(tName);
            if (s != null) {
                minLevel = Math.min(minLevel, s.getUpgradeLevel(upgrade));
            }
        }
        return minLevel == Integer.MAX_VALUE ? 0 : minLevel;
    }

    private void renderUpgradeOverlay(GuiGraphics g, int mouseX, int mouseY) {
        hoveredSlotTooltip = null;
        if (!upgradeOverlayOpen || sandboxState.getSelectedTerritories().isEmpty()) {
            return;
        }

        int slotSize = 24;
        int slotSpacing = 6;
        int rowHeight = slotSize + 6;
        int padX = 14;
        int padTop = 30;
        // Extra space for: Set HQ row (22px), separator (8px), action buttons row (20px), treasury row (20px), hint (22px)
        int padBottom = 110;

        int maxCols = 7;
        int panelW = padX * 2 + maxCols * slotSize + (maxCols - 1) * slotSpacing;
        int panelH = padTop + 4 * rowHeight + padBottom;

        int px = this.width - panelW - 12;
        int py = (int) (renderY + renderedBorderYOffset) + 4;

        // Container panel background (normal box #000000 with #2B2B2F border)
        Render2D.surface(g, new UiRect(px, py, panelW, panelH), 0xFF000000, 0xFF2B2B2F);

        // Header bar
        Render2D.surface(g, new UiRect(px, py, panelW, 22), 0xFF101013, 0xFF2B2B2F);
        String primaryName = sandboxState.getSelectedTerritories().iterator().next();
        String title = sandboxState.getSelectedTerritories().size() == 1 ? primaryName : "Selected (" + sandboxState.getSelectedTerritories().size() + ")";
        String fullTitle = title + " - Upgrades";
        int maxTitleW = panelW - 32;
        if (font.width(fullTitle) > maxTitleW) {
            fullTitle = font.plainSubstrByWidth(fullTitle, maxTitleW - 10) + "...";
        }
        g.drawString(font, fullTitle, px + 8, py + 7, 0xFFFFFFFF, false);

        // Close 'X' button
        int closeX = px + panelW - 16;
        int closeY = py + 5;
        boolean hoverClose = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        g.drawString(font, "✕", closeX, closeY + 1, hoverClose ? 0xFFFF5555 : 0xFFAAAAAA, false);

        // Render upgrade slots (rows 0-3)
        for (UpgradeSlotDef slot : UPGRADE_SLOTS) {
            int sx = px + padX + slot.col * (slotSize + slotSpacing);
            int sy = py + padTop + slot.row * rowHeight;

            boolean hovered = mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize;
            int slotBg = hovered ? 0xFF2B2B2F : 0xFF101013;
            int slotBorder = hovered ? 0xFF3A3A3E : 0xFF2B2B2F;
            Render2D.surface(g, new UiRect(sx, sy, slotSize, slotSize), slotBg, slotBorder);

            // Render upgrade icon
            RenderUtils.drawScalingTexturedRect(g, slot.iconTexture, sx + 2, sy + 2, slotSize - 4, slotSize - 4, 16, 16);

            // Render level in bottom-right corner
            int curLevel = getEffectiveUpgradeLevel(slot.upgrade);
            String lvlText = String.valueOf(curLevel);
            int tw = font.width(lvlText);
            g.drawString(font, lvlText, sx + slotSize - tw - 1, sy + slotSize - font.lineHeight, curLevel > 0 ? 0xFFFFFF55 : 0xFFAAAAAA, true);

            if (hovered) {
                hoveredSlotTooltip = buildUpgradeTooltip(slot, curLevel);
            }
        }

        // --- Bottom action section ---
        int bottomSectionY = py + padTop + 4 * rowHeight + 6;

        // Separator line
        g.fill(px + 8, bottomSectionY, px + panelW - 8, bottomSectionY + 1, 0xFF2B2B2F);
        bottomSectionY += 7;

        // Row 1: Set HQ | Per-territory Treasury
        int btnH = 16;
        int bx = px + padX;

        // Set HQ button
        boolean isSingleSelected = sandboxState.getSelectedTerritories().size() == 1;
        boolean selectedIsHq = isSingleSelected && sandboxState.getSelectedTerritories().iterator().next().equals(sandboxState.getHqTerritory());
        String hqBtnLabel = selectedIsHq ? "Unset HQ" : "Set HQ";
        int hqBtnW = font.width(hqBtnLabel) + 14;
        boolean hoverHQ = mouseX >= bx && mouseX <= bx + hqBtnW && mouseY >= bottomSectionY && mouseY <= bottomSectionY + btnH;
        Render2D.surface(g, new UiRect(bx, bottomSectionY, hqBtnW, btnH), hoverHQ ? 0xFF2B2B2F : 0xFF101013, hoverHQ ? 0xFFFFD700 : 0xFF2B2B2F);
        g.drawString(font, hqBtnLabel, bx + (hqBtnW - font.width(hqBtnLabel)) / 2, bottomSectionY + (btnH - font.lineHeight) / 2 + 1, hoverHQ ? 0xFFFFD700 : 0xFFCCCCCC, false);
        bx += hqBtnW + 8;

        // Per-territory Treasury button (cycles tier for selected territories only)
        String selTreasStr = getSelectedTreasuryLabel();
        String selTreasLabel = "Treasury: " + selTreasStr;
        int selTreasW = font.width(selTreasLabel) + 12;
        boolean hoverSelTreas = mouseX >= bx && mouseX <= bx + selTreasW && mouseY >= bottomSectionY && mouseY <= bottomSectionY + btnH;
        Render2D.surface(g, new UiRect(bx, bottomSectionY, selTreasW, btnH), hoverSelTreas ? 0xFF2B2B2F : 0xFF101013, hoverSelTreas ? 0xFF3A3A3E : 0xFF2B2B2F);
        g.drawString(font, selTreasLabel, bx + (selTreasW - font.width(selTreasLabel)) / 2, bottomSectionY + (btnH - font.lineHeight) / 2 + 1, 0xFFAAFFAA, false);

        bottomSectionY += btnH + 6;

        // Row 2: action buttons
        bx = px + padX;
        int[] actionWidths = {28, 34, 46, 38, 40};
        String[] actionLabels = {"All", "None", "Remove", "Reset", "Clear"};
        for (int i = 0; i < actionLabels.length; i++) {
            String lbl = actionLabels[i];
            int bw = actionWidths[i];
            boolean hov = mouseX >= bx && mouseX <= bx + bw && mouseY >= bottomSectionY && mouseY <= bottomSectionY + btnH;
            int fill = hov ? 0xFF2B2B2F : 0xFF101013;
            int border = hov ? 0xFF3A3A3E : 0xFF2B2B2F;
            Render2D.surface(g, new UiRect(bx, bottomSectionY, bw, btnH), fill, border);
            g.drawString(font, lbl, bx + (bw - font.width(lbl)) / 2, bottomSectionY + (btnH - font.lineHeight) / 2 + 1, hov ? 0xFFFFFFFF : 0xFFCCCCCC, false);
            bx += bw + 5;
        }

        bottomSectionY += btnH + 8;

        // Row 3: hints
        g.drawString(font, "L-Click: +1 | R-Click: -1 | Shift: ±5 (upgrades)", px + 10, bottomSectionY, 0xFFAAAAAA, false);
        g.drawString(font, "ESC or '✕' to close", px + 10, bottomSectionY + 10, 0xFF666666, false);
    }

    private String getSelectedTreasuryLabel() {
        if (sandboxState.getSelectedTerritories().isEmpty()) return "—";
        // Show the per-territory treasury of the first selected, or "Mixed" if multiple differ
        TreasuryTier first = null;
        boolean mixed = false;
        for (String tName : sandboxState.getSelectedTerritories()) {
            EconomyTerritoryState s = sandboxState.getTerritory(tName);
            TreasuryTier t = s != null ? s.getTreasury() : null;
            if (t == null) t = sandboxState.getDefaultTreasury(); // inherit
            if (first == null) {
                first = t;
            } else if (first != t) {
                mixed = true;
                break;
            }
        }
        if (mixed) return "Mixed";
        return first != null ? first.getDisplayName() : "—";
    }

    private List<Component> buildUpgradeTooltip(UpgradeSlotDef slot, int curLevel) {
        SandboxUpgrade upgrade = slot.upgrade;
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(upgrade.getDisplayName() + " [Lv. %d/%d]".formatted(curLevel, slot.maxLevel)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        tip.add(Component.literal(upgrade.getDescription()).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal(""));
        tip.add(Component.literal("Current: " + upgrade.formatBonus(curLevel)).withStyle(ChatFormatting.GREEN));
        if (curLevel < slot.maxLevel) {
            long nextCost = upgrade.getCost(curLevel + 1);
            tip.add(Component.literal("Next Lv: " + upgrade.formatBonus(curLevel + 1) + " (Cost: %s %s/h)".formatted(formatShort(nextCost), upgrade.getCostResource().getName())).withStyle(ChatFormatting.AQUA));
        } else {
            tip.add(Component.literal("★ Maximum Level Reached").withStyle(ChatFormatting.YELLOW));
        }
        long curCost = upgrade.getCost(curLevel);
        if (curCost > 0) {
            tip.add(Component.literal("Upkeep: %s %s/h".formatted(formatShort(curCost), upgrade.getCostResource().getName())).withStyle(ChatFormatting.RED));
        }
        tip.add(Component.literal(""));
        tip.add(Component.literal("Left-Click: +1 | Right-Click: -1").withStyle(ChatFormatting.YELLOW));
        tip.add(Component.literal("Shift + Click: ±5").withStyle(ChatFormatting.YELLOW));
        if (sandboxState.getSelectedTerritories().size() > 1) {
            tip.add(Component.literal("(Applies to all %d selected territories)".formatted(sandboxState.getSelectedTerritories().size())).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return tip;
    }

    private boolean handleUpgradeOverlayClick(int mx, int my, int button) {
        if (!upgradeOverlayOpen || sandboxState.getSelectedTerritories().isEmpty()) {
            return false;
        }

        int slotSize = 24;
        int slotSpacing = 6;
        int rowHeight = slotSize + 6;
        int padX = 14;
        int padTop = 30;
        int padBottom = 110;

        int maxCols = 7;
        int panelW = padX * 2 + maxCols * slotSize + (maxCols - 1) * slotSpacing;
        int panelH = padTop + 4 * rowHeight + padBottom;

        int px = this.width - panelW - 12;
        int py = (int) (renderY + renderedBorderYOffset) + 4;

        if (mx < px || mx > px + panelW || my < py || my > py + panelH) {
            return false;
        }

        // Close button clicked
        int closeX = px + panelW - 16;
        int closeY = py + 5;
        if (mx >= closeX && mx <= closeX + 12 && my >= closeY && my <= closeY + 12) {
            upgradeOverlayOpen = false;
            return true;
        }

        boolean shift = (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS)
                || (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);

        // Check upgrade slots
        for (UpgradeSlotDef slot : UPGRADE_SLOTS) {
            int sx = px + padX + slot.col * (slotSize + slotSpacing);
            int sy = py + padTop + slot.row * rowHeight;

            if (mx >= sx && mx < sx + slotSize && my >= sy && my < sy + slotSize) {
                int curLvl = getEffectiveUpgradeLevel(slot.upgrade);
                int step = shift ? 5 : 1;
                int newLvl = curLvl;

                if (button == 0) { // Left-click +1
                    newLvl = Math.min(slot.maxLevel, curLvl + step);
                } else if (button == 1) { // Right-click -1
                    newLvl = Math.max(0, curLvl - step);
                }

                if (newLvl != curLvl) {
                    for (String tName : sandboxState.getSelectedTerritories()) {
                        EconomyTerritoryState s = sandboxState.getTerritory(tName);
                        if (s != null) {
                            s.setUpgradeLevel(slot.upgrade, newLvl);
                        }
                    }
                    sandboxState.invalidateDistanceCache();
                    persist();
                }
                return true;
            }
        }

        // --- Bottom action section ---
        int bottomSectionY = py + padTop + 4 * rowHeight + 6 + 7; // after separator
        int btnH = 16;

        // Row 1: Set HQ | Per-territory Treasury
        int bx = px + padX;
        boolean isSingleSelected = sandboxState.getSelectedTerritories().size() == 1;

        boolean selectedIsHq = isSingleSelected && sandboxState.getSelectedTerritories().iterator().next().equals(sandboxState.getHqTerritory());
        String hqBtnLabel = selectedIsHq ? "Unset HQ" : "Set HQ";
        int hqBtnW = font.width(hqBtnLabel) + 14;
        if (mx >= bx && mx <= bx + hqBtnW && my >= bottomSectionY && my <= bottomSectionY + btnH) {
            if (isSingleSelected) {
                String sel = sandboxState.getSelectedTerritories().iterator().next();
                sandboxState.toggleHq(sel);
                sandboxState.invalidateDistanceCache();
                persist();
            }
            return true;
        }
        bx += hqBtnW + 8;

        // Per-territory Treasury (left-click = next, right-click = prev, for selected territories)
        String selTreasStr = getSelectedTreasuryLabel();
        String selTreasLabel = "Treasury: " + selTreasStr;
        int selTreasW = font.width(selTreasLabel) + 12;
        if (mx >= bx && mx <= bx + selTreasW && my >= bottomSectionY && my <= bottomSectionY + btnH) {
            for (String tName : sandboxState.getSelectedTerritories()) {
                EconomyTerritoryState s = sandboxState.getTerritory(tName);
                if (s != null) {
                    TreasuryTier cur = s.getTreasury() != null ? s.getTreasury() : sandboxState.getDefaultTreasury();
                    TreasuryTier nextT = button == 1 ? cur.prev() : cur.next();
                    s.setTreasury(nextT);
                }
            }
            persist();
            return true;
        }

        bottomSectionY += btnH + 6;

        // Row 2: action buttons
        bx = px + padX;
        int[] actionWidths = {28, 34, 46, 38, 40};
        String[] actionLabels = {"All", "None", "Remove", "Reset", "Clear"};
        for (int i = 0; i < actionLabels.length; i++) {
            int bw = actionWidths[i];
            if (mx >= bx && mx <= bx + bw && my >= bottomSectionY && my <= bottomSectionY + btnH) {
                switch (actionLabels[i]) {
                    case "All" -> { sandboxState.selectAll(); persist(); }
                    case "None" -> { sandboxState.selectNone(); persist(); }
                    case "Remove" -> { sandboxState.removeSelected(); upgradeOverlayOpen = false; persist(); }
                    case "Reset" -> { sandboxState.resetSelected(); persist(); }
                    case "Clear" -> modal = ModalMode.CLEAR_CONFIRM;
                }
                return true;
            }
            bx += bw + 5;
        }

        return true; // Consume panel click
    }
    // --- Top Bar (Wynntils Styled - Step 3) ---
    private void renderTopBar(GuiGraphics g, int mouseX, int mouseY) {
    }

    private int drawTopButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, String text) {
        return drawTopButton(g, mouseX, mouseY, x, y, w, h, text, null);
    }

    private int drawTopButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, String text, Texture icon) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int fill = hovered ? 0xFF2B2B2F : 0xFF101013;
        int border = hovered ? 0xFF3A3A3E : 0xFF2B2B2F;
        int textCol = hovered ? 0xFFFFFFFF : 0xFFCCCCCC;
        Render2D.surface(g, new UiRect(x, y, w, h), fill, border);

        int textX = x;
        int availableW = w;
        if (icon != null) {
            RenderUtils.drawTexturedRect(g, icon, x + 3, y + (h - icon.height()) / 2);
            textX += icon.width() + 4;
            availableW -= icon.width() + 4;
        }
        int tw = font.width(text);
        g.drawString(font, text, textX + (availableW - tw) / 2, y + (h - font.lineHeight) / 2 + 1, textCol, false);
        return x + w + 5;
    }

    private void renderAutocompleteDropdown(GuiGraphics g, int mouseX, int mouseY) {
        int x = 125;
        int y = 37;
        int w = 180;
        int itemH = 16;
        int h = autocompleteMatches.size() * itemH + 4;

        Render2D.surface(g, new UiRect(x, y, w, h), 0xF014161F, 0xFF353A4C);

        for (int i = 0; i < autocompleteMatches.size(); i++) {
            String name = autocompleteMatches.get(i);
            int iy = y + 2 + i * itemH;
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= iy && mouseY <= iy + itemH;
            if (hovered) {
                g.fill(x + 2, iy, x + w - 2, iy + itemH, 0x44FFFFFF);
            }
            boolean added = sandboxState.isAdded(name);
            String label = (added ? "✓ " : "+ ") + name;
            g.drawString(font, label, x + 6, iy + 4, added ? 0xFF00FF88 : 0xFFFFFFFF, false);
        }
    }

    // --- Territory Hover Tooltip ---
    private void renderTerritoryTooltip(GuiGraphics g, String terrName, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        boolean isAdded = sandboxState.isAdded(terrName);
        boolean isHq = terrName.equals(sandboxState.getHqTerritory());

        lines.add(Component.literal(terrName).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));

        if (isAdded) {
            if (isHq) {
                lines.add(Component.literal("★ Guild Headquarters").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
            int hops = sandboxState.getHqDistance(terrName);
            if (sandboxState.getHqTerritory() != null) {
                if (hops >= 0) {
                    lines.add(Component.literal("HQ Distance: %d hops".formatted(hops)).withStyle(ChatFormatting.GRAY));
                } else {
                    lines.add(Component.literal("⚠ Cut off / Disconnected from HQ").withStyle(ChatFormatting.RED));
                }
            }

            EconomyTerritoryState terrState = sandboxState.getTerritory(terrName);
            double bonus = sandboxState.getTreasuryBonus(terrName);
            TreasuryTier treasury = terrState != null && terrState.getTreasury() != null
                    ? terrState.getTreasury() : sandboxState.getDefaultTreasury();
            lines.add(Component.literal("Treasury: %s (+%.1f%%)".formatted(treasury.getDisplayName(), bonus * 100.0)).withStyle(ChatFormatting.YELLOW));

            lines.add(Component.literal(""));
            lines.add(Component.literal("Production:").withStyle(ChatFormatting.GRAY));
            for (GuildResource res : GuildResource.values()) {
                int prod = sandboxState.getTerritoryProduction(terrName, res);
                int cap = sandboxState.getTerritoryStorageCap(terrName, res);
                if (prod > 0 || cap > 0) {
                    lines.add(Component.literal("%s %s: +%d/h (0/%d)".formatted(
                            res.getPrettySymbol().trim(), res.getName(), prod, cap)).withStyle(ChatFormatting.WHITE));
                }
            }

            lines.add(Component.literal(""));
            lines.add(Component.literal("Total Upkeep:").withStyle(ChatFormatting.GRAY));
            for (GuildResource res : GuildResource.values()) {
                long usage = sandboxState.getTerritoryUsage(terrName, res);
                if (usage > 0) {
                    lines.add(Component.literal("%s %s: %s/h".formatted(
                            res.getPrettySymbol().trim(), res.getName(), formatShort(usage))).withStyle(ChatFormatting.RED));
                }
            }

            // --- Combat Stats (using RMod's TerritoryUtils.estimateStats logic) ---
            if (terrState != null) {
                try {
                    // Build a TerritoryUpgrade -> Integer map from EconomyTerritoryState upgrades
                    Map<TerritoryUpgrade, Integer> upgradeMap = new java.util.EnumMap<>(TerritoryUpgrade.class);
                    // Map SandboxUpgrade -> TerritoryUpgrade for the 4 tower stat upgrades
                    upgradeMap.put(TerritoryUpgrade.DAMAGE,  terrState.getUpgradeLevel(EconomySandboxData.SandboxUpgrade.DAMAGE));
                    upgradeMap.put(TerritoryUpgrade.ATTACK,  terrState.getUpgradeLevel(EconomySandboxData.SandboxUpgrade.ATTACK));
                    upgradeMap.put(TerritoryUpgrade.HEALTH,  terrState.getUpgradeLevel(EconomySandboxData.SandboxUpgrade.HEALTH));
                    upgradeMap.put(TerritoryUpgrade.DEFENCE, terrState.getUpgradeLevel(EconomySandboxData.SandboxUpgrade.DEFENCE));

                    // Try to get territory info for multiplier (connection count etc.)
                    TerritoryInfo info = TerritoryUtils.territoryInfo(terrName);
                    if (info != null) {
                        TerritoryCombatStats stats = TerritoryUtils.estimateStats(upgradeMap, info);

                        String diffName = terrState.getDefenseDifficultyName();
                        lines.add(Component.literal(""));
                        lines.add(Component.literal("Total Stats (%s):".formatted(diffName)).withStyle(ChatFormatting.GOLD));

                        // Damage line with attack speed (ore icon prefix, matching white/ore color)
                        String dmgLine = "Damage: %s - %s (%.1f)".formatted(
                                formatStatNum(stats.minDamage()),
                                formatStatNum(stats.maxDamage()),
                                stats.attackSpeed());
                        lines.add(Component.literal("Ⓑ ").withStyle(ChatFormatting.WHITE)
                                .append(Component.literal(dmgLine).withStyle(ChatFormatting.WHITE)));

                        // DPS line (ore icon prefix, matching white/ore color)
                        lines.add(Component.literal("Ⓑ ").withStyle(ChatFormatting.WHITE)
                                .append(Component.literal("Damage per Second: %s".formatted(formatStatNum(stats.averageDps()))).withStyle(ChatFormatting.WHITE)));

                        // Health line with defence % (wood icon prefix, matching gold/wood color)
                        String healthLine = "Health: %s (%.1f%%)".formatted(
                                formatStatNum(stats.health()),
                                stats.defence() * 100.0);
                        lines.add(Component.literal("Ⓒ ").withStyle(ChatFormatting.GOLD)
                                .append(Component.literal(healthLine).withStyle(ChatFormatting.GOLD)));

                        // EHP (fish icon prefix, matching aqua/fish color)
                        String ehpStr = Double.isInfinite(stats.ehp()) ? "∞" : formatStatNum(stats.ehp());
                        lines.add(Component.literal("Ⓚ ").withStyle(ChatFormatting.AQUA)
                                .append(Component.literal("Effective Health: %s".formatted(ehpStr)).withStyle(ChatFormatting.AQUA)));
                    }
                } catch (Exception ignored) {
                    // If stat calculation fails (no live territory data), skip stats section
                }
            }

            lines.add(Component.literal(""));
            lines.add(Component.literal("R-Click to open upgrade menu").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.literal("Not added to sandbox").withStyle(ChatFormatting.DARK_GRAY));
            TerritoryBase base = TerritoryBaseManager.getTerritory(terrName);
            if (base != null) {
                for (GuildResource res : GuildResource.values()) {
                    int p = base.getProduction(res);
                    if (p > 0) {
                        lines.add(Component.literal("Base %s: %d/h".formatted(res.getName(), p)).withStyle(ChatFormatting.GRAY));
                    }
                }
            }
            lines.add(Component.literal(""));
            lines.add(Component.literal("[+] Click on map to add to Sandbox").withStyle(ChatFormatting.GREEN));
        }

        RenderUtils.renderTooltip(g, lines, mouseX + 12, mouseY);
    }

    /** Format a large number with commas, e.g. 390000 -> "390,000" */
    private static String formatStatNum(double val) {
        if (val >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", val / 1_000_000.0);
        } else if (val >= 1_000) {
            // Format with comma grouping
            return String.format(Locale.ROOT, "%,.0f", val);
        }
        return String.format(Locale.ROOT, "%.0f", val);
    }

    // =========================================================================
    // MODAL DIALOGS
    // =========================================================================
    private void renderModal(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dim background
        g.fill(0, 0, this.width, this.height, 0x88000000);

        switch (modal) {
            case TRIBUTES -> renderTributesModal(g, mouseX, mouseY);
            // case LOADOUTS -> renderChestLoadoutsModal(g, mouseX, mouseY); // Loadouts disabled
            case SAVES -> renderSavesModal(g, mouseX, mouseY);
            case CLEAR_CONFIRM -> renderClearConfirmModal(g, mouseX, mouseY);
            case SHARE_STRING -> renderShareStringModal(g, mouseX, mouseY);
            case PROMPT_INPUT -> renderPromptModal(g, mouseX, mouseY);
            default -> {}
        }
    }

    private void renderTributesModal(GuiGraphics g, int mouseX, int mouseY) {
        int w = 260;
        int h = 180;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        Render2D.surface(g, new UiRect(x, y, w, h), BG_COLOR, BORDER_COLOR);

        g.drawString(font, "Guild Tributes", x + 12, y + 10, TEXT_COLOR, false);
        g.drawString(font, "Positive = received (+), Negative = sent (-)", x + 12, y + 22, TEXT_MUTED, false);

        int curY = y + 36;
        for (GuildResource res : GuildResource.values()) {
            g.drawString(font, res.getPrettySymbol() + " " + res.getName() + ":", x + 16, curY + 3, TEXT_COLOR, false);
            EditBox box = tributeInputBoxes.get(res);
            if (box != null) {
                int boxX = x + 160;
                Render2D.surface(g, new UiRect(boxX, curY, 80, 14), 0xFF111111, box.isFocused() ? 0xFFFFFFFF : 0x66FFFFFF);
                GuiStyle.layoutBorderlessEditBox(box, font, boxX + 4, curY + 1, 72, 14);
                box.render(g, mouseX, mouseY, 0);
            }
            curY += 18;
        }

        int btnY = y + h - 24;
        drawTopButton(g, mouseX, mouseY, x + 12, btnY, 65, 16, "Apply");
        drawTopButton(g, mouseX, mouseY, x + 85, btnY, 80, 16, "Reset to 0");
        drawTopButton(g, mouseX, mouseY, x + 175, btnY, 65, 16, "Cancel");
    }

    private void renderChestLoadoutsModal(GuiGraphics g, int mouseX, int mouseY) {
        int slotSize = 20;
        int slotSpacing = 2;
        int cols = 9;
        int rows = 3;
        int gridW = cols * slotSize + (cols - 1) * slotSpacing;
        int gridH = rows * slotSize + (rows - 1) * slotSpacing;
        int w = 240;
        int h = 145;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        Render2D.surface(g, new UiRect(x, y, w, h), BG_COLOR, BORDER_COLOR);

        g.drawString(font, "Territory Defenses Loadouts", x + 12, y + 10, TEXT_COLOR, false);
        g.drawString(font, "Chest slots: Click to save or bulk-apply", x + 12, y + 22, TEXT_MUTED, false);

        int startX = x + (w - gridW) / 2;
        int startY = y + 36;

        for (int i = 0; i < 27; i++) {
            int r = i / cols;
            int c = i % cols;
            int sx = startX + c * (slotSize + slotSpacing);
            int sy = startY + r * (slotSize + slotSpacing);

            boolean isHovered = mouseX >= sx && mouseX <= sx + slotSize && mouseY >= sy && mouseY <= sy + slotSize;
            boolean isSel = (selectedLoadoutSlotIndex == i);

            int bg = isSel ? 0xFF2B4C2B : (isHovered ? 0xFF333333 : 0xFF141414);
            int border = isSel ? 0xFF00FF88 : (isHovered ? 0xFFFFFFFF : 0xFF444444);
            Render2D.surface(g, new UiRect(sx, sy, slotSize, slotSize), bg, border);

            TerritoryLoadoutManager.TerritoryLoadoutSlot slot = TerritoryLoadoutManager.getSlot(i);
            if (slot != null && slot.isConfigured()) {
                g.renderItem(new ItemStack(Items.IRON_CHESTPLATE), sx + 2, sy + 2);
            }

            if (isHovered) {
                if (slot == null || !slot.isConfigured()) {
                    hoveredSlotTooltip = List.of(
                            Component.literal("Empty loadout").withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE),
                            Component.literal("(left click)").withStyle(ChatFormatting.GRAY),
                            Component.empty(),
                            Component.literal("click to sellect a pre-configured").withStyle(ChatFormatting.GRAY),
                            Component.literal("territory and save it's current").withStyle(ChatFormatting.GRAY),
                            Component.literal("defenses").withStyle(ChatFormatting.GRAY)
                    );
                } else {
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.literal(slot.name).withStyle(ChatFormatting.BOLD, ChatFormatting.GREEN));
                    lines.add(Component.literal("(left click to select territories & apply with [ENTER])").withStyle(ChatFormatting.YELLOW));
                    lines.add(Component.literal("(right click to rename, shift+right click to clear)").withStyle(ChatFormatting.AQUA));
                    if (slot.treasury != null) {
                        lines.add(Component.literal("Treasury: " + slot.treasury.getDisplayName()).withStyle(ChatFormatting.GOLD));
                    }
                    lines.add(Component.empty());
                    lines.add(Component.literal("Defenses:").withStyle(ChatFormatting.GRAY));
                    boolean any = false;
                    for (SandboxUpgrade u : SandboxUpgrade.values()) {
                        int lvl = slot.upgrades.getOrDefault(u, 0);
                        if (lvl > 0) {
                            any = true;
                            lines.add(Component.literal(" • " + u.getDisplayName() + ": " + lvl).withStyle(ChatFormatting.WHITE));
                        }
                    }
                    if (!any) {
                        lines.add(Component.literal(" • (None)").withStyle(ChatFormatting.DARK_GRAY));
                    }
                    hoveredSlotTooltip = lines;
                }
            }
        }

        int btnY = startY + gridH + 10;
        boolean hasSelSlot = selectedLoadoutSlotIndex >= 0 && selectedLoadoutSlotIndex < 27
                && TerritoryLoadoutManager.getSlot(selectedLoadoutSlotIndex) != null
                && TerritoryLoadoutManager.getSlot(selectedLoadoutSlotIndex).isConfigured();

        if (hasSelSlot) {
            drawTopButton(g, mouseX, mouseY, x + 12, btnY, 95, 16, "Apply to Sel.");
            drawTopButton(g, mouseX, mouseY, x + 112, btnY, 55, 16, "Rename");
            drawTopButton(g, mouseX, mouseY, x + 172, btnY, 56, 16, "Close");
        } else {
            drawTopButton(g, mouseX, mouseY, x + w - 66, btnY, 54, 16, "Close");
        }
    }

    private void renderSavesModal(GuiGraphics g, int mouseX, int mouseY) {
        int slotSize = 20;
        int slotSpacing = 2;
        int cols = 9;
        int rows = 3;
        int gridW = cols * slotSize + (cols - 1) * slotSpacing;
        int gridH = rows * slotSize + (rows - 1) * slotSpacing;
        int w = 250;
        int h = 190;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        Render2D.surface(g, new UiRect(x, y, w, h), BG_COLOR, BORDER_COLOR);

        g.drawString(font, "Eco Loadouts", x + 12, y + 10, TEXT_COLOR, false);
        g.drawString(font, "L: Load | R: Delete | Shift+L: Share | Shift+R: Rename", x + 12, y + 22, TEXT_MUTED, false);

        int startX = x + (w - gridW) / 2;
        int startY = y + 36;

        for (int i = 0; i < 27; i++) {
            int r = i / cols;
            int c = i % cols;
            int sx = startX + c * (slotSize + slotSpacing);
            int sy = startY + r * (slotSize + slotSpacing);

            boolean isHovered = mouseX >= sx && mouseX <= sx + slotSize && mouseY >= sy && mouseY <= sy + slotSize;
            boolean isSel = (selectedSaveSlotIndex == i);

            int bg = isSel ? 0xFF1C2B4C : (isHovered ? 0xFF2B2B2F : 0xFF101013);
            int border = isSel ? 0xFF00FF88 : (isHovered ? 0xFFFFFFFF : 0xFF2B2B2F);
            Render2D.surface(g, new UiRect(sx, sy, slotSize, slotSize), bg, border);

            EcoSaveManager.EcoSaveSlot slot = EcoSaveManager.getSlot(i);
            if (slot != null && slot.isConfigured()) {
                g.renderItem(new ItemStack(Items.WRITABLE_BOOK), sx + 2, sy + 2);
            }

            if (isHovered) {
                if (slot == null || !slot.isConfigured()) {
                    hoveredSlotTooltip = List.of(
                            Component.literal("Empty Slot " + (i + 1)).withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE),
                            Component.literal("Left Click: Save current setup here").withStyle(ChatFormatting.GREEN)
                    );
                } else {
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.literal(slot.name).withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA));
                    lines.add(Component.literal("Left Click: Load").withStyle(ChatFormatting.GREEN));
                    lines.add(Component.literal("Right Click: Delete").withStyle(ChatFormatting.RED));
                    lines.add(Component.literal("Shift + Left Click: Copy Share Code").withStyle(ChatFormatting.GOLD));
                    lines.add(Component.literal("Shift + Right Click: Rename").withStyle(ChatFormatting.YELLOW));
                    lines.add(Component.empty());
                    lines.add(Component.literal("Territories: " + slot.territories.size()).withStyle(ChatFormatting.WHITE));
                    if (slot.defaultTreasury != null) {
                        lines.add(Component.literal("Treasury: " + slot.defaultTreasury).withStyle(ChatFormatting.GRAY));
                    }
                    hoveredSlotTooltip = lines;
                }
            }
        }

        // Bottom buttons row 1: slot actions
        int btnY1 = startY + gridH + 8;
        boolean hasSelSlot = selectedSaveSlotIndex >= 0 && selectedSaveSlotIndex < 27
                && EcoSaveManager.getSlot(selectedSaveSlotIndex) != null
                && EcoSaveManager.getSlot(selectedSaveSlotIndex).isConfigured();
        if (hasSelSlot) {
            drawTopButton(g, mouseX, mouseY, x + 12, btnY1, 52, 15, "Load");
            drawTopButton(g, mouseX, mouseY, x + 68, btnY1, 52, 15, "Share");
            drawTopButton(g, mouseX, mouseY, x + 124, btnY1, 54, 15, "Rename");
            drawTopButton(g, mouseX, mouseY, x + 182, btnY1, 56, 15, "Delete");
        }

        // Bottom buttons row 2: import / export / close
        int btnY2 = btnY1 + 20;
        drawTopButton(g, mouseX, mouseY, x + 12, btnY2, 68, 15, "Import Code");
        drawTopButton(g, mouseX, mouseY, x + 84, btnY2, 66, 15, "Share Code");
        drawTopButton(g, mouseX, mouseY, x + 154, btnY2, 44, 15, "JSON");
        drawTopButton(g, mouseX, mouseY, x + w - 46, btnY2, 34, 15, "Close");
    }

    private void renderClearConfirmModal(GuiGraphics g, int mouseX, int mouseY) {
        int w = 250;
        int h = 95;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        Render2D.surface(g, new UiRect(x, y, w, h), BG_COLOR, 0xFFFF5555);

        g.drawString(font, "Clear Sandbox Confirmation", x + 12, y + 12, 0xFFFF5555, false);
        g.drawString(font, "Are you sure you want to reset?", x + 12, y + 26, TEXT_COLOR, false);
        g.drawString(font, "All territories & edits will be cleared.", x + 12, y + 38, TEXT_MUTED, false);

        int btnY = y + 60;
        drawTopButton(g, mouseX, mouseY, x + 16, btnY, 100, 18, "Yes, Clear");
        drawTopButton(g, mouseX, mouseY, x + 134, btnY, 100, 18, "Cancel");
    }

    private void renderShareStringModal(GuiGraphics g, int mouseX, int mouseY) {
        int w = 300;
        int h = 130;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        Render2D.surface(g, new UiRect(x, y, w, h), BG_COLOR, BORDER_COLOR);

        g.drawString(font, "Share String Import / Export", x + 12, y + 10, TEXT_COLOR, false);
        g.drawString(font, "Base64 economy sandbox string", x + 12, y + 22, TEXT_MUTED, false);

        int inputX = x + 12;
        int inputY = y + 38;
        int inputW = w - 24;
        Render2D.surface(g, new UiRect(inputX, inputY, inputW, 18), 0xFF111111, shareStringBox.isFocused() ? 0xFFFFFFFF : 0x66FFFFFF);
        GuiStyle.layoutBorderlessEditBox(shareStringBox, font, inputX + 4, inputY + 1, inputW - 8, 18);
        shareStringBox.render(g, mouseX, mouseY, 0);

        if (!shareStringFeedback.isEmpty()) {
            int col = shareStringSuccess ? 0xFF00FF88 : 0xFFFF5555;
            g.drawString(font, shareStringFeedback, x + 12, y + 62, col, false);
        }

        int btnY = y + h - 28;
        drawTopButton(g, mouseX, mouseY, x + 12, btnY, 100, 18, "Copy to Clipboard");
        drawTopButton(g, mouseX, mouseY, x + 120, btnY, 95, 18, "Import / Apply");
        drawTopButton(g, mouseX, mouseY, x + 225, btnY, 63, 18, "Close");
    }

    private void renderPromptModal(GuiGraphics g, int mouseX, int mouseY) {
        int w = 280;
        int h = 110;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        Render2D.surface(g, new UiRect(x, y, w, h), BG_COLOR, BORDER_COLOR);

        g.drawString(font, promptTitle, x + 12, y + 10, TEXT_COLOR, false);
        g.drawString(font, promptMessage, x + 12, y + 22, TEXT_MUTED, false);

        int inputX = x + 12;
        int inputY = y + 42;
        int inputW = w - 24;
        Render2D.surface(g, new UiRect(inputX, inputY, inputW, 18), 0xFF111111, promptInputBox.isFocused() ? 0xFFFFFFFF : 0x66FFFFFF);
        GuiStyle.layoutBorderlessEditBox(promptInputBox, font, inputX + 4, inputY + 1, inputW - 8, 18);
        promptInputBox.render(g, mouseX, mouseY, 0);

        int btnY = y + h - 28;
        drawTopButton(g, mouseX, mouseY, x + 12, btnY, 110, 18, "Confirm");
        drawTopButton(g, mouseX, mouseY, x + 158, btnY, 110, 18, "Cancel");
    }

    private boolean isMouseOverUI(int mouseX, int mouseY) {
        // Bulk Loadout Banner
        if (activeBulkLoadoutIndex != -1 && modal == ModalMode.NONE) {
            int bw = 460;
            int bh = 34;
            int bx = (this.width - bw) / 2;
            int by = 12;
            if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) return true;
        }

        // Left output widget
        if (mouseX <= 195 && mouseY >= 40 && mouseY <= 330) return true;
        // Bottom map buttons bar (prevents clicking through to territories beneath)
        for (MapButton button : mapButtons) {
            if (button.isMouseOver(mouseX, mouseY)) return true;
        }
        int buttonBgY = (int) (this.renderHeight - this.renderedBorderYOffset - Texture.MAP_BUTTONS_BACKGROUND_MIDDLE.height());
        if (mouseY >= buttonBgY - 2 && mouseY <= buttonBgY + Texture.MAP_BUTTONS_BACKGROUND_MIDDLE.height() + 2) {
            int totalW = mapButtons.size() * Texture.MAP_BUTTONS_BACKGROUND_MIDDLE.width() + 10;
            if (mouseX >= centerX - totalW / 2f && mouseX <= centerX + totalW / 2f) return true;
        }
        // Upgrade Overlay — updated for new expanded panel height
        if (upgradeOverlayOpen && !sandboxState.getSelectedTerritories().isEmpty()) {
            int slotSize = 24;
            int slotSpacing = 6;
            int rowHeight = slotSize + 6;
            int padX = 14;
            int padTop = 30;
            int padBottom = 110;
            int maxCols = 7;
            int panelW = padX * 2 + maxCols * slotSize + (maxCols - 1) * slotSpacing;
            int panelH = padTop + 4 * rowHeight + padBottom;
            int px = this.width - panelW - 12;
            int py = (int) (renderY + renderedBorderYOffset) + 4;
            if (mouseX >= px && mouseX <= px + panelW && mouseY >= py && mouseY <= py + panelH) return true;
        }
        return false;
    }

    // =========================================================================
    // MOUSE & KEY EVENTS
    // =========================================================================
    @Override
    public boolean doMouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();
        int button = event.button();

        // 1. Modals
        if (modal != ModalMode.NONE) {
            handleModalClick(mx, my, button);
            return true;
        }

        // Bulk Loadout Banner clicks
        if (activeBulkLoadoutIndex != -1 && modal == ModalMode.NONE) {
            int bw = 460;
            int bh = 34;
            int bx = (this.width - bw) / 2;
            int by = 12;
            int btnY = by + 6;
            if (isInside(mx, my, bx + bw - 150, btnY, 78, 20)) {
                applyActiveBulkLoadout();
                return true;
            }
            if (isInside(mx, my, bx + bw - 68, btnY, 58, 20)) {
                activeBulkLoadoutIndex = -1;
                return true;
            }
            if (isInside(mx, my, bx, by, bw, bh)) {
                return true;
            }
        }

        // 2. fa-rog Upgrade Overlay Click
        if (upgradeOverlayOpen && !sandboxState.getSelectedTerritories().isEmpty()) {
            if (handleUpgradeOverlayClick(mx, my, button)) {
                return true;
            }
        }

        // 5. Left Output Widget
        if (mx >= 10 && mx <= 195 && my >= 42 && my <= 330) {
            return true; // Consume click
        }

        // Check bottom map buttons first before passing to map click
        for (MapButton mapButton : mapButtons) {
            if (mapButton.isMouseOver(mx, my)) {
                return mapButton.mouseClicked(event, isDoubleClick);
            }
        }
        if (isMouseOverUI(mx, my)) {
            return true;
        }

        // 6. Map Clicks
        mouseDownX = mx;
        mouseDownY = my;
        isMapDragging = false;

        String clickedTerritory = findTerritoryAt(mx, my);
        if (clickedTerritory != null) {
            if (activeBulkLoadoutIndex != -1) {
                // In bulk loadout mode: ALWAYS toggle territory selection without opening upgrade overlay!
                if (!sandboxState.isAdded(clickedTerritory)) {
                    sandboxState.addTerritory(clickedTerritory);
                    sandboxState.getSelectedTerritories().add(clickedTerritory);
                } else {
                    if (sandboxState.getSelectedTerritories().contains(clickedTerritory)) {
                        sandboxState.getSelectedTerritories().remove(clickedTerritory);
                    } else {
                        sandboxState.getSelectedTerritories().add(clickedTerritory);
                    }
                }
                persist();
                return true;
            }

            if (button == 1) {
                // Right-click territory = open upgrade overlay
                if (!sandboxState.isAdded(clickedTerritory)) {
                    sandboxState.addTerritory(clickedTerritory);
                }
                if (!sandboxState.getSelectedTerritories().contains(clickedTerritory)) {
                    sandboxState.getSelectedTerritories().clear();
                    sandboxState.getSelectedTerritories().add(clickedTerritory);
                }
                upgradeOverlayOpen = true;
                persist();
                return true;
            } else if (button == 0) {
                // Left-click territory = add/remove from selection (or toggle into eco if not added)
                if (!sandboxState.isAdded(clickedTerritory)) {
                    sandboxState.addTerritory(clickedTerritory);
                    sandboxState.getSelectedTerritories().add(clickedTerritory);
                } else {
                    if (sandboxState.getSelectedTerritories().contains(clickedTerritory)) {
                        sandboxState.getSelectedTerritories().remove(clickedTerritory);
                        if (sandboxState.getSelectedTerritories().isEmpty()) {
                            upgradeOverlayOpen = false;
                        }
                    } else {
                        sandboxState.getSelectedTerritories().add(clickedTerritory);
                    }
                }
                persist();
                return true;
            }
        }

        // Empty map clicked -> pass to AbstractMapScreen for map panning
        return super.doMouseClicked(event, isDoubleClick);
    }

    private boolean handleTopBarClick(int mx, int my, int button) {
        return false;
    }

    private void handleModalClick(int mx, int my, int button) {
        switch (modal) {
            case TRIBUTES -> {
                int w = 260;
                int h = 180;
                int x = (this.width - w) / 2;
                int y = (this.height - h) / 2;
                int btnY = y + h - 24;

                // Input box focus
                for (EditBox b : tributeInputBoxes.values()) {
                    b.setFocused(false);
                }
                int curY = y + 36;
                for (GuildResource res : GuildResource.values()) {
                    EditBox box = tributeInputBoxes.get(res);
                    if (box != null && isInside(mx, my, x + 160, curY, 80, 14)) {
                        box.setFocused(true);
                    }
                    curY += 18;
                }

                // Apply
                if (isInside(mx, my, x + 12, btnY, 65, 16)) {
                    for (Map.Entry<GuildResource, EditBox> entry : tributeInputBoxes.entrySet()) {
                        try {
                            int val = Integer.parseInt(entry.getValue().getValue().trim());
                            sandboxState.getTributes().put(entry.getKey(), val);
                        } catch (NumberFormatException ignored) {}
                    }
                    persist();
                    modal = ModalMode.NONE;
                }
                // Reset to 0
                else if (isInside(mx, my, x + 85, btnY, 80, 16)) {
                    for (Map.Entry<GuildResource, EditBox> entry : tributeInputBoxes.entrySet()) {
                        entry.getValue().setValue("0");
                        sandboxState.getTributes().put(entry.getKey(), 0);
                    }
                    persist();
                }
                // Cancel
                else if (isInside(mx, my, x + 175, btnY, 65, 16)) {
                    modal = ModalMode.NONE;
                }
            }

            case LOADOUTS -> handleChestLoadoutClick(mx, my, button);

            case SAVES -> handleSavesModalClick(mx, my, button);

            case CLEAR_CONFIRM -> {
                int w = 250;
                int h = 95;
                int x = (this.width - w) / 2;
                int y = (this.height - h) / 2;
                int btnY = y + 60;

                if (isInside(mx, my, x + 16, btnY, 100, 18)) {
                    sandboxState.clearAll();
                    persist();
                    modal = ModalMode.NONE;
                } else if (isInside(mx, my, x + 134, btnY, 100, 18)) {
                    modal = ModalMode.NONE;
                }
            }

            case SHARE_STRING -> {
                int w = 300;
                int h = 130;
                int x = (this.width - w) / 2;
                int y = (this.height - h) / 2;

                int inputX = x + 12;
                int inputY = y + 38;
                int inputW = w - 24;
                if (isInside(mx, my, inputX, inputY, inputW, 18)) {
                    shareStringBox.setFocused(true);
                } else {
                    shareStringBox.setFocused(false);
                }

                int btnY = y + h - 28;
                // Copy
                if (isInside(mx, my, x + 12, btnY, 100, 18)) {
                    String str = shareStringBox.getValue();
                    if (!str.isEmpty()) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(str);
                        shareStringFeedback = "Copied to clipboard!";
                        shareStringSuccess = true;
                    }
                }
                // Import
                else if (isInside(mx, my, x + 120, btnY, 95, 18)) {
                    String str = shareStringBox.getValue().trim();
                    try {
                        EconomySandboxState imported = EconomySandboxStorage.importFromShareString(str);
                        copyState(imported, sandboxState);
                        persist();

                        int targetSlot = -1;
                        for (int i = 0; i < 27; i++) {
                            var s = EcoSaveManager.getSlot(i);
                            if (s != null && !s.isConfigured()) {
                                targetSlot = i;
                                break;
                            }
                        }
                        if (targetSlot != -1) {
                            EcoSaveManager.saveSlotFromState(targetSlot, "Imported " + (targetSlot + 1), imported);
                            selectedSaveSlotIndex = targetSlot;
                        }

                        shareStringFeedback = "Successfully imported!";
                        shareStringSuccess = true;
                        modal = ModalMode.SAVES;
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.displayClientMessage(
                                    Component.literal("§a[RMod] Successfully imported Eco Loadout!"), false);
                        }
                    } catch (Exception e) {
                        shareStringFeedback = "Error: " + e.getMessage();
                        shareStringSuccess = false;
                    }
                }
                // Close
                else if (isInside(mx, my, x + 225, btnY, 63, 18)) {
                    modal = ModalMode.SAVES;
                }
            }

            case PROMPT_INPUT -> {
                int w = 280;
                int h = 110;
                int x = (this.width - w) / 2;
                int y = (this.height - h) / 2;

                int inputX = x + 12;
                int inputY = y + 42;
                int inputW = w - 24;
                if (isInside(mx, my, inputX, inputY, inputW, 18)) {
                    promptInputBox.setFocused(true);
                } else {
                    promptInputBox.setFocused(false);
                }

                int btnY = y + h - 28;
                // Confirm
                if (isInside(mx, my, x + 12, btnY, 110, 18)) {
                    if (promptAction != null) {
                        promptAction.accept(promptInputBox.getValue().trim());
                    }
                    modal = ModalMode.NONE;
                }
                // Cancel
                else if (isInside(mx, my, x + 158, btnY, 110, 18)) {
                    modal = ModalMode.NONE;
                }
            }

            default -> {}
        }
    }

    private void handleChestLoadoutClick(int mx, int my, int button) {
        int slotSize = 20;
        int slotSpacing = 2;
        int cols = 9;
        int gridW = cols * slotSize + (cols - 1) * slotSpacing;
        int gridH = 3 * slotSize + 2 * slotSpacing;
        int w = 240;
        int h = 145;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        int startX = x + (w - gridW) / 2;
        int startY = y + 36;

        for (int i = 0; i < 27; i++) {
            int r = i / cols;
            int c = i % cols;
            int sx = startX + c * (slotSize + slotSpacing);
            int sy = startY + r * (slotSize + slotSpacing);

            if (mx >= sx && mx <= sx + slotSize && my >= sy && my <= sy + slotSize) {
                TerritoryLoadoutManager.TerritoryLoadoutSlot slot = TerritoryLoadoutManager.getSlot(i);
                if (slot == null) return;

                if (!slot.isConfigured()) {
                    if (!sandboxState.getSelectedTerritories().isEmpty()) {
                        String terrName = sandboxState.getSelectedTerritories().iterator().next();
                        EconomyTerritoryState terrState = sandboxState.getTerritory(terrName);
                        if (terrState != null) {
                            promptTitle = "Name Loadout";
                            promptMessage = "Enter name for saved defenses:";
                            promptInputBox.setValue(terrName);
                            int slotIdx = i;
                            promptAction = name -> {
                                TerritoryLoadoutManager.saveSlotFromTerritory(slotIdx, name, terrState);
                            };
                            modal = ModalMode.PROMPT_INPUT;
                            return;
                        }
                    } else {
                        promptTitle = "Save Loadout";
                        promptMessage = "Select a territory on map first!";
                        promptInputBox.setValue("");
                        promptAction = name -> {};
                        modal = ModalMode.PROMPT_INPUT;
                        return;
                    }
                } else {
                    selectedLoadoutSlotIndex = i;
                    boolean shift = (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS)
                            || (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
                    if (button == 1 && shift) {
                        // Shift+right click = clear slot
                        slot.clear();
                        TerritoryLoadoutManager.save();
                        return;
                    }
                    if (button == 1) {
                        promptTitle = "Rename Loadout";
                        promptMessage = "Enter new name:";
                        promptInputBox.setValue(slot.name);
                        promptAction = name -> {
                            slot.name = name;
                            TerritoryLoadoutManager.save();
                        };
                        modal = ModalMode.PROMPT_INPUT;
                        return;
                    }
                    // Left click -> activate bulk apply mode, close chest GUI
                    activeBulkLoadoutIndex = i;
                    modal = ModalMode.NONE;
                    upgradeOverlayOpen = false;
                    return;
                }
                return;
            }
        }

        int btnY = startY + gridH + 10;
        boolean hasSelSlot = selectedLoadoutSlotIndex >= 0 && selectedLoadoutSlotIndex < 27
                && TerritoryLoadoutManager.getSlot(selectedLoadoutSlotIndex) != null
                && TerritoryLoadoutManager.getSlot(selectedLoadoutSlotIndex).isConfigured();

        if (hasSelSlot) {
            if (isInside(mx, my, x + 12, btnY, 95, 16)) {
                activeBulkLoadoutIndex = selectedLoadoutSlotIndex;
                modal = ModalMode.NONE;
                upgradeOverlayOpen = false;
                return;
            } else if (isInside(mx, my, x + 112, btnY, 55, 16)) {
                TerritoryLoadoutManager.TerritoryLoadoutSlot slot = TerritoryLoadoutManager.getSlot(selectedLoadoutSlotIndex);
                if (slot != null && slot.isConfigured()) {
                    promptTitle = "Rename Loadout";
                    promptMessage = "Enter new name:";
                    promptInputBox.setValue(slot.name);
                    promptAction = name -> {
                        slot.name = name;
                        TerritoryLoadoutManager.save();
                    };
                    modal = ModalMode.PROMPT_INPUT;
                }
            } else if (isInside(mx, my, x + 172, btnY, 56, 16)) {
                modal = ModalMode.NONE;
            }
        } else {
            if (isInside(mx, my, x + w - 66, btnY, 54, 16)) {
                modal = ModalMode.NONE;
            }
        }
    }

    private void handleSavesModalClick(int mx, int my, int button) {
        int slotSize = 20;
        int slotSpacing = 2;
        int cols = 9;
        int gridW = cols * slotSize + (cols - 1) * slotSpacing;
        int gridH = 3 * slotSize + 2 * slotSpacing;
        int w = 250;
        int h = 190;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        int startX = x + (w - gridW) / 2;
        int startY = y + 36;

        for (int i = 0; i < 27; i++) {
            int r = i / cols;
            int c = i % cols;
            int sx = startX + c * (slotSize + slotSpacing);
            int sy = startY + r * (slotSize + slotSpacing);
            if (mx >= sx && mx <= sx + slotSize && my >= sy && my <= sy + slotSize) {
                EcoSaveManager.EcoSaveSlot slot = EcoSaveManager.getSlot(i);
                if (slot == null) return;
                boolean shift = (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS)
                        || (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);

                if (!slot.isConfigured()) {
                    if (button == 0) {
                        // Empty: save current eco here
                        int idx = i;
                        promptTitle = "Save Eco Loadout";
                        promptMessage = "Enter name for this eco loadout:";
                        promptInputBox.setValue("Loadout " + (i + 1));
                        promptAction = name -> EcoSaveManager.saveSlotFromState(idx, name, sandboxState);
                        modal = ModalMode.PROMPT_INPUT;
                    }
                    return;
                }

                selectedSaveSlotIndex = i;

                if (shift && button == 0) {
                    // Shift + Left Click: Copy share code
                    EconomySandboxState tempState = EcoSaveManager.slotToState(slot);
                    String shareCode = EconomySandboxStorage.exportToShareString(tempState);
                    Minecraft.getInstance().keyboardHandler.setClipboard(shareCode);
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§a[RMod] Copied share code for '" + slot.name + "' to clipboard!"), false);
                    }
                    return;
                }

                if (shift && button == 1) {
                    // Shift + Right Click: Rename
                    int idx = i;
                    promptTitle = "Rename Eco Loadout";
                    promptMessage = "Enter new name:";
                    promptInputBox.setValue(slot.name);
                    promptAction = name -> { slot.name = name; EcoSaveManager.save(); };
                    modal = ModalMode.PROMPT_INPUT;
                    return;
                }

                if (button == 1) {
                    // Right Click: Delete
                    slot.clear();
                    EcoSaveManager.save();
                    if (selectedSaveSlotIndex == i) {
                        selectedSaveSlotIndex = -1;
                    }
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§c[RMod] Deleted eco loadout slot " + (i + 1) + "!"), false);
                    }
                    return;
                }

                if (button == 0) {
                    // Left Click: Load
                    EcoSaveManager.applySlotToState(i, sandboxState);
                    persist();
                    modal = ModalMode.NONE;
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§a[RMod] Loaded eco loadout '" + slot.name + "'!"), false);
                    }
                    return;
                }
                return;
            }
        }

        int btnY1 = startY + gridH + 8;
        int btnY2 = btnY1 + 20;
        boolean hasSelSlot = selectedSaveSlotIndex >= 0 && selectedSaveSlotIndex < 27
                && EcoSaveManager.getSlot(selectedSaveSlotIndex) != null
                && EcoSaveManager.getSlot(selectedSaveSlotIndex).isConfigured();

        if (hasSelSlot) {
            // Load
            if (isInside(mx, my, x + 12, btnY1, 52, 15)) {
                EcoSaveManager.applySlotToState(selectedSaveSlotIndex, sandboxState);
                persist();
                modal = ModalMode.NONE;
                return;
            }
            // Share
            if (isInside(mx, my, x + 68, btnY1, 52, 15)) {
                EcoSaveManager.EcoSaveSlot slot = EcoSaveManager.getSlot(selectedSaveSlotIndex);
                EconomySandboxState tempState = EcoSaveManager.slotToState(slot);
                String shareCode = EconomySandboxStorage.exportToShareString(tempState);
                Minecraft.getInstance().keyboardHandler.setClipboard(shareCode);
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("§a[RMod] Copied share code for '" + slot.name + "' to clipboard!"), false);
                }
                return;
            }
            // Rename
            if (isInside(mx, my, x + 124, btnY1, 54, 15)) {
                EcoSaveManager.EcoSaveSlot slot = EcoSaveManager.getSlot(selectedSaveSlotIndex);
                promptTitle = "Rename Eco Loadout";
                promptMessage = "Enter new name:";
                promptInputBox.setValue(slot.name);
                promptAction = name -> { slot.name = name; EcoSaveManager.save(); };
                modal = ModalMode.PROMPT_INPUT;
                return;
            }
            // Delete
            if (isInside(mx, my, x + 182, btnY1, 56, 15)) {
                EcoSaveManager.EcoSaveSlot slot = EcoSaveManager.getSlot(selectedSaveSlotIndex);
                if (slot != null) {
                    slot.clear();
                    EcoSaveManager.save();
                    selectedSaveSlotIndex = -1;
                }
                return;
            }
        }

        // Import Code
        if (isInside(mx, my, x + 12, btnY2, 68, 15)) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            shareStringBox.setValue(clip != null ? clip.trim() : "");
            shareStringFeedback = (clip != null && clip.startsWith("rmod:")) ? "Clipboard code detected! Click Import." : "Paste share code & click Import";
            shareStringSuccess = true;
            modal = ModalMode.SHARE_STRING;
            return;
        }

        // Share Code
        if (isInside(mx, my, x + 84, btnY2, 66, 15)) {
            EconomySandboxState st = hasSelSlot ? EcoSaveManager.slotToState(EcoSaveManager.getSlot(selectedSaveSlotIndex)) : sandboxState;
            String code = EconomySandboxStorage.exportToShareString(st);
            shareStringBox.setValue(code);
            Minecraft.getInstance().keyboardHandler.setClipboard(code);
            shareStringFeedback = "Copied to clipboard!";
            shareStringSuccess = true;
            modal = ModalMode.SHARE_STRING;
            return;
        }

        // JSON (Export if slot selected, else Import)
        if (isInside(mx, my, x + 154, btnY2, 44, 15)) {
            if (hasSelSlot) {
                int idx = selectedSaveSlotIndex;
                promptTitle = "Export Eco Loadout to JSON";
                promptMessage = "Enter filename (saves to config/):";
                promptInputBox.setValue(EcoSaveManager.getSlot(idx).name.replaceAll("[^a-zA-Z0-9_]", "_") + ".json");
                promptAction = fname -> {
                    try {
                        Path p = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(fname.endsWith(".json") ? fname : fname + ".json");
                        EcoSaveManager.exportSlotToFile(idx, p);
                    } catch (Exception e) { shareStringFeedback = "Export error: " + e.getMessage(); shareStringSuccess = false; }
                };
                modal = ModalMode.PROMPT_INPUT;
            } else {
                promptTitle = "Import Eco Loadout from JSON";
                promptMessage = "Enter full path to JSON file:";
                promptInputBox.setValue("");
                promptAction = fname -> {
                    try {
                        EcoSaveManager.EcoSaveSlot imported = EcoSaveManager.importSlotFromFile(Path.of(fname));
                        for (int i = 0; i < 27; i++) {
                            EcoSaveManager.EcoSaveSlot s = EcoSaveManager.getSlot(i);
                            if (s != null && !s.isConfigured()) {
                                EcoSaveManager.getSlots().set(i, imported);
                                EcoSaveManager.save();
                                selectedSaveSlotIndex = i;
                                return;
                            }
                        }
                    } catch (Exception e) { shareStringFeedback = "Import error: " + e.getMessage(); shareStringSuccess = false; }
                };
                modal = ModalMode.PROMPT_INPUT;
            }
            return;
        }

        // Close
        if (isInside(mx, my, x + w - 46, btnY2, 34, 15)) {
            modal = ModalMode.NONE;
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (modal != ModalMode.NONE) return;
        super.renderTooltip(g, mouseX, mouseY);
    }

    private void renderBulkApplyBanner(GuiGraphics g, int mouseX, int mouseY) {
        TerritoryLoadoutManager.TerritoryLoadoutSlot slot = TerritoryLoadoutManager.getSlot(activeBulkLoadoutIndex);
        if (slot == null || !slot.isConfigured()) {
            activeBulkLoadoutIndex = -1;
            return;
        }

        int bw = 460;
        int bh = 34;
        int bx = (this.width - bw) / 2;
        int by = 12;

        Render2D.surface(g, new UiRect(bx, by, bw, bh), 0xEE0B1C0B, 0xFF00FF88);

        int count = sandboxState.getSelectedTerritories().size();
        g.drawString(font, "Loadout Mode: " + slot.name + " (" + count + " selected)", bx + 10, by + 6, 0xFF00FF88, false);
        g.drawString(font, "Click territories to select/deselect. Press [ENTER] to apply.", bx + 10, by + 18, 0xFFAAAAAA, false);

        int btnY = by + 7;
        drawTopButton(g, mouseX, mouseY, bx + bw - 150, btnY, 78, 19, "Apply [ENTER]");
        drawTopButton(g, mouseX, mouseY, bx + bw - 68, btnY, 58, 19, "Cancel [ESC]");
    }

    private void applyActiveBulkLoadout() {
        if (activeBulkLoadoutIndex != -1) {
            TerritoryLoadoutManager.TerritoryLoadoutSlot slot = TerritoryLoadoutManager.getSlot(activeBulkLoadoutIndex);
            if (slot != null && slot.isConfigured()) {
                for (String targetTerr : sandboxState.getSelectedTerritories()) {
                    EconomyTerritoryState targetState = sandboxState.getTerritory(targetTerr);
                    if (targetState != null) {
                        TerritoryLoadoutManager.applySlotToTerritory(activeBulkLoadoutIndex, targetState);
                    }
                }
                sandboxState.invalidateDistanceCache();
                persist();
            }
            activeBulkLoadoutIndex = -1;
        }
    }

    private void copyState(EconomySandboxState src, EconomySandboxState dest) {
        dest.clearAll();
        dest.setDefaultTreasury(src.getDefaultTreasury());
        dest.getTributes().putAll(src.getTributes());
        for (EconomyTerritoryState terr : src.getAddedTerritories().values()) {
            EconomyTerritoryState copy = dest.addTerritory(terr.getName());
            copy.setTreasury(terr.getTreasury());
            copy.setHq(terr.isHq());
            for (SandboxUpgrade u : SandboxUpgrade.values()) {
                copy.setUpgradeLevel(u, terr.getUpgradeLevel(u));
            }
        }
        dest.setHqTerritory(src.getHqTerritory());
        dest.invalidateDistanceCache();
    }

    private boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private String findTerritoryAt(int mouseX, int mouseY) {
        Map<String, TerritoryBase> catalog = TerritoryBaseManager.getTerritories();
        if (catalog == null) return null;

        for (String name : catalog.keySet()) {
            TerritoryProfile profile = com.wynntils.core.components.Models.Territory.getTerritoryProfile(name);
            if (profile == null) continue;

            float x1 = MapRenderer.getRenderX(profile.getStartX(), mapCenterX, centerX, zoomRenderScale);
            float x2 = MapRenderer.getRenderX(profile.getEndX(), mapCenterX, centerX, zoomRenderScale);
            float z1 = MapRenderer.getRenderZ(profile.getStartZ(), mapCenterZ, centerZ, zoomRenderScale);
            float z2 = MapRenderer.getRenderZ(profile.getEndZ(), mapCenterZ, centerZ, zoomRenderScale);

            float rx = Math.min(x1, x2);
            float rz = Math.min(z1, z2);
            float rw = Math.abs(x2 - x1);
            float rh = Math.abs(z2 - z1);

            if (mouseX >= rx && mouseX <= rx + rw && mouseY >= rz && mouseY <= rz + rh) {
                return name;
            }
        }
        return null;
    }

    private void centerOnTerritory(String name) {
        TerritoryProfile p = com.wynntils.core.components.Models.Territory.getTerritoryProfile(name);
        if (p != null) {
            updateMapCenter((p.getStartX() + p.getEndX()) / 2f, (p.getStartZ() + p.getEndZ()) / 2f);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!isMapDragging) {
            double dx = Math.abs(event.x() - mouseDownX);
            double dy = Math.abs(event.y() - mouseDownY);
            if (dx < 4 && dy < 4 && !isMouseOverUI((int) event.x(), (int) event.y())) {
                String clicked = findTerritoryAt((int) event.x(), (int) event.y());
                if (clicked == null) {
                    if (activeBulkLoadoutIndex == -1) {
                        sandboxState.selectNone();
                        persist();
                    }
                }
            }
        }
        isMapDragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (modal != ModalMode.NONE) {
            return false;
        }
        double dx = Math.abs(event.x() - mouseDownX);
        double dy = Math.abs(event.y() - mouseDownY);
        if (dx > 3 || dy > 3) {
            isMapDragging = true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (modal == ModalMode.LOADOUTS) {
            loadoutScroll = Math.clamp(loadoutScroll + scrollY * 16, -Math.max(0, loadoutNames.size() * 16 - 130), 0);
            return true;
        }

        // Scroll to add/remove upgrade when hovering over an upgrade slot in the overlay
        if (upgradeOverlayOpen && !sandboxState.getSelectedTerritories().isEmpty() && scrollY != 0) {
            int slotSize = 24;
            int slotSpacing = 6;
            int rowHeight = slotSize + 6;
            int padX = 14;
            int padTop = 30;
            int padBottom = 110;
            int maxCols = 7;
            int panelW = padX * 2 + maxCols * slotSize + (maxCols - 1) * slotSpacing;
            int px = this.width - panelW - 12;
            int py = (int) (renderY + renderedBorderYOffset) + 4;

            boolean shift = (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS)
                    || (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
            int step = shift ? 5 : 1;

            for (UpgradeSlotDef slot : UPGRADE_SLOTS) {
                int sx = px + padX + slot.col * (slotSize + slotSpacing);
                int sy = py + padTop + slot.row * rowHeight;

                if (mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize) {
                    int curLvl = getEffectiveUpgradeLevel(slot.upgrade);
                    int newLvl = scrollY > 0 ? Math.min(slot.maxLevel, curLvl + step) : Math.max(0, curLvl - step);
                    if (newLvl != curLvl) {
                        for (String tName : sandboxState.getSelectedTerritories()) {
                            EconomyTerritoryState s = sandboxState.getTerritory(tName);
                            if (s != null) {
                                s.setUpgradeLevel(slot.upgrade, newLvl);
                            }
                        }
                        sandboxState.invalidateDistanceCache();
                        persist();
                    }
                    return true;
                }
            }
        }

        // Map Zooming: Smooth cursor-anchored zoom (matching territory map)
        if (scrollY != 0) {
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
            float delta = (float) scrollY * 2.5f;
            targetZoomLevel = Math.max(1.0f, Math.min(100.0f, targetZoomLevel + delta));
            float futureScale = MapRenderer.getZoomRenderScaleFromLevel(targetZoomLevel);

            // Keep world point anchored under cursor
            targetMapCenterX = worldX - (float) (mouseX - centerX) / futureScale;
            targetMapCenterZ = worldZ - (float) (mouseY - centerZ) / futureScale;

            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (activeBulkLoadoutIndex != -1 && modal == ModalMode.NONE) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                applyActiveBulkLoadout();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                activeBulkLoadoutIndex = -1;
                return true;
            }
        }

        if (modal != ModalMode.NONE) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                modal = ModalMode.NONE;
                return true;
            }
            if (modal == ModalMode.TRIBUTES) {
                for (EditBox b : tributeInputBoxes.values()) {
                    if (b.isFocused()) return b.keyPressed(event);
                }
            } else if (modal == ModalMode.LOADOUTS) {
                if (newLoadoutNameBox.isFocused()) return newLoadoutNameBox.keyPressed(event);
            } else if (modal == ModalMode.SHARE_STRING) {
                if (shareStringBox.isFocused()) return shareStringBox.keyPressed(event);
            } else if (modal == ModalMode.PROMPT_INPUT) {
                if (promptInputBox.isFocused()) return promptInputBox.keyPressed(event);
            }
            return true;
        }

        if (upgradeOverlayOpen && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            upgradeOverlayOpen = false;
            return true;
        }

        // Focus camera on HQ when 'H' is pressed (if no modal open and search box not focused)
        if (event.key() == GLFW.GLFW_KEY_H && (searchBox == null || !searchBox.isFocused())) {
            focusCameraOnHq();
            return true;
        }

        // Ctrl + A: select all added
        if (event.key() == GLFW.GLFW_KEY_A && (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS)) {
            sandboxState.selectAll();
            persist();
            return true;
        }

        // Delete / Backspace: remove selected
        if ((event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_BACKSPACE) && !sandboxState.getSelectedTerritories().isEmpty()) {
            sandboxState.removeSelected();
            persist();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (modal != ModalMode.NONE) {
            if (modal == ModalMode.TRIBUTES) {
                for (EditBox b : tributeInputBoxes.values()) {
                    if (b.isFocused()) return b.charTyped(event);
                }
            } else if (modal == ModalMode.LOADOUTS) {
                if (newLoadoutNameBox.isFocused()) return newLoadoutNameBox.charTyped(event);
            } else if (modal == ModalMode.SHARE_STRING) {
                if (shareStringBox.isFocused()) return shareStringBox.charTyped(event);
            } else if (modal == ModalMode.PROMPT_INPUT) {
                if (promptInputBox.isFocused()) return promptInputBox.charTyped(event);
            }
            return false;
        }

        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(event);
        }

        return super.charTyped(event);
    }

    private void focusCameraOnHq() {
        String hqName = sandboxState.getHqTerritory();
        if (hqName != null) {
            TerritoryProfile hqProfile = com.wynntils.core.components.Models.Territory.getTerritoryProfile(hqName);
            if (hqProfile != null) {
                targetMapCenterX = (hqProfile.getStartX() + hqProfile.getEndX()) / 2f;
                targetMapCenterZ = (hqProfile.getStartZ() + hqProfile.getEndZ()) / 2f;
                if (targetZoomLevel < 0) {
                    targetZoomLevel = zoomLevel;
                }
                return;
            }
        }
        // Fallback: world center
        targetMapCenterX = MAP_CENTER_X;
        targetMapCenterZ = MAP_CENTER_Z;
        if (targetZoomLevel < 0) {
            targetZoomLevel = zoomLevel;
        }
    }

    @Override
    public void onClose() {
        lastMapCenterX = this.mapCenterX;
        lastMapCenterZ = this.mapCenterZ;
        lastZoomLevel = this.zoomLevel;
        persist();
        super.onClose();
    }
}
