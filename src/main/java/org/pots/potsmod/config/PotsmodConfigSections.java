package org.pots.potsmod.config;

import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import org.pots.potsmod.config.choices.GuiAnimationMode;
import org.pots.potsmod.config.choices.RadianceOverlayMode;
import org.pots.potsmod.config.choices.SettingCategory;
import org.pots.potsmod.config.choices.ShoutReplacement;
import org.pots.potsmod.config.setting.*;
import org.pots.potsmod.features.ConsuTextFeature;
import org.pots.potsmod.gui.theme.FontOption;
import org.pots.potsmod.gui.theme.GuiTheme;
import org.pots.potsmod.gui.theme.ThemeOption;
import org.pots.potsmod.managers.FeatureManager;
import org.pots.potsmod.managers.OverlayManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

@UtilityClass
final class PotsmodConfigSections {
    static List<SettingSection> createSections() {
        List<SettingSection> sections = new ArrayList<>();
        sections.addAll(generalSections());
        sections.addAll(warSections());
        sections.addAll(socialSections());
        return sections;
    }

    private static List<SettingSection> generalSections() {
        return categorySections(
                SettingCategory.GENERAL,
                PotsmodConfigSections::clientSection,
                PotsmodConfigSections::guiSection,
                PotsmodConfigSections::viewModelSection,
                PotsmodConfigSections::inventoryCalculatorSection
        );
    }

    private static List<SettingSection> warSections() {
        return categorySections(
                SettingCategory.WAR,
                PotsmodConfigSections::customTerritoryMapSection,
                PotsmodConfigSections::defenseColorOverlaySection,
                PotsmodConfigSections::economySandboxSection,
                PotsmodConfigSections::newestWorldSwitchSection,
                PotsmodConfigSections::quickAttackSection,
                PotsmodConfigSections::worldMapHudSection,
                PotsmodConfigSections::resourceTickSection,
                PotsmodConfigSections::territoryRouteFlowSection,
                PotsmodConfigSections::territoryBoxesSection,
                PotsmodConfigSections::consumableLabelsSection,
                PotsmodConfigSections::towerEhpSection,
                PotsmodConfigSections::radianceSyncSection
        );
    }

    private static List<SettingSection> socialSections() {
        return categorySections(
                SettingCategory.SOCIAL,
                PotsmodConfigSections::chatEncryptionSection,
                PotsmodConfigSections::autoStreamSection,
                PotsmodConfigSections::shoutFilterSection
        );
    }

    private static List<SettingSection> categorySections(SettingCategory category, SectionFactory... factories) {
        return Arrays.stream(factories)
                .map(factory -> factory.create(category))
                .toList();
    }

    private static SettingSection clientSection(SettingCategory category) {
        return SettingSection.standard(
                "client",
                "Client",
                "Core Wynncraft data and shared client preferences.",
                category,
                null,
                null,
                List.of(
                        bool("auto_update", "Auto Update", "Automatically download and update the mod to the latest version on restart.", PotsmodConfigData::isAutoUpdate, PotsmodConfigData::setAutoUpdate),
                        string("api_base", "API URL", "Base Wynncraft API URL.", PotsmodConfigData::getApiBase, PotsmodConfigData::setApiBase)
                )
        );
    }

    private static SettingSection customTerritoryMapSection(SettingCategory category) {
        return SettingSection.standard(
                "custom_territory_map",
                "Custom Territory Map",
                "Visual revamped territory map screen replacing Wynntils default map.",
                category,
                null,
                null,
                List.of(
                        bool("enabled", "Enable Custom Territory Map", "Use custom revamped territory map instead of Wynntils default map.", PotsmodConfigData::isCustomTerritoryMapEnabled, PotsmodConfigData::setCustomTerritoryMapEnabled),
                        bool("custom_economy_menu", "Custom Economy Menu", "Adds custom animations and visual tweaks to the economy menu.", PotsmodConfigData::isCustomEconomyMenuEnabled, PotsmodConfigData::setCustomEconomyMenuEnabled)
                )
        );
    }

    private static SettingSection economySandboxSection(SettingCategory category) {
        return SettingSection.standard(
                "economy_sandbox",
                "Practice Eco",
                "Somewhere you can practice eco...",
                category,
                null,
                null,
                List.of(
                        keybind("economy_sandbox", "Set keybind", "Key to open the Economy Sandbox.", PotsmodConfigData::getEconomySandboxKey, PotsmodConfigData::setEconomySandboxKey),
                        button("open_economy_sandbox", "Open", "Open the Economy Sandbox screen directly.", () -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.setScreen(new org.pots.potsmod.features.economysandbox.EconomySandboxScreen());
                        }, "Open")
                )
        );
    }

    private static SettingSection newestWorldSwitchSection(SettingCategory category) {
        return SettingSection.standard(
                "newest_world_switch",
                "Newest World Switch",
                "Instantly switch to the newest active world (/switch <world>). HERO+ rank is required for /switch.",
                category,
                null,
                null,
                List.of(
                        bool("enabled", "Enable Newest World Switch", "Allow pressing keybind to switch to newest world. HERO+ rank is required.", PotsmodConfigData::isNewestWorldSwitchEnabled, PotsmodConfigData::setNewestWorldSwitchEnabled),
                        choice("region", "Region", "Select server region (NA, EU, AS, or ALL).", PotsmodConfigData::getNewestWorldRegion, PotsmodConfigData::setNewestWorldRegion, org.pots.potsmod.config.choices.ServerRegionChoice.class),
                        keybind("keybind", "Switch Keybind", "Key to switch to newest world.", PotsmodConfigData::getNewestWorldSwitchKey, PotsmodConfigData::setNewestWorldSwitchKey)
                )
        );
    }

    private static SettingSection quickAttackSection(SettingCategory category) {
        return SettingSection.standard(
                "quick_attack",
                "Attack bind",
                "Set a keybind to instantly press the minecraft thingy on the /gu attack menu...",
                category,
                null,
                null,
                List.of(
                        keybind("quick_attack", "Key bind", "Key to click attack in the guild attack menu.", PotsmodConfigData::getQuickAttackKey, PotsmodConfigData::setQuickAttackKey)
                )
        );
    }

    private static SettingSection guiSection(SettingCategory category) {
        return SettingSection.standard(
                "gui",
                "GUI Settings",
                "Visual customizations for the Click GUI.",
                category,
                null,
                null,
                List.of(
                        choice("click_gui_font", "GUI Font", "Font used in the Click GUI.", PotsmodConfigData::getClickGuiFont, PotsmodConfigData::setClickGuiFont, FontOption.keys(), FontOption::labelFor),
                        choice("click_gui_animation", "GUI Animation", "Opening and closing effect for the Click GUI.", PotsmodConfigData::getClickGuiAnimation, PotsmodConfigData::setClickGuiAnimation, GuiAnimationMode.class),
                        integer("animation_time", "Animation Time", "Time for config animation.", 100, 2000, PotsmodConfigData::getAnimationTime, PotsmodConfigData::setAnimationTime),
                        floating("gui_opacity", "GUI Opacity", "Overall background transparency.", 0.1f, 1.0f, PotsmodConfigData::getGuiOpacity, PotsmodConfigData::setGuiOpacity)
                )
        );
    }


    private static SettingSection chatEncryptionSection(SettingCategory category) {
        return featureSection(
                "chat_encryption",
                "Chat Encryption",
                "Encrypt and decode guild messages with a shared key.",
                category,
                PotsmodConfigData::isChatEncryptionFeatureEnabled,
                PotsmodConfigData::setChatEncryptionFeatureEnabled,
                List.of(
                        string("encryption_prefix", "Encryption Prefix", "Messages starting with this prefix are encrypted.", PotsmodConfigData::getEncryptionPrefix, PotsmodConfigData::setEncryptionPrefix),
                        string("encryption_key", "Encryption Key", "Shared AES key material.", PotsmodConfigData::getEncryptionKey, PotsmodConfigData::setEncryptionKey)
                )
        );
    }

    private static SettingSection autoStreamSection(SettingCategory category) {
        return SettingSection.standard(
                "auto_stream",
                "Auto Stream",
                "Automatically stream when you change worlds",
                category,
                null,
                null,
                List.of(
                        integer("timeout", "Timeout", "Time to wait between stream boss bars before attempting to re-stream", 100, 10000, PotsmodConfigData::getStreamCooldown, PotsmodConfigData::setStreamCooldown)
                )
        );
    }

    private static SettingSection viewModelSection(SettingCategory category) {
        return featureSection(
                "view_model",
                "View Model",
                "Hand offsets, rotations",
                category,
                PotsmodConfigData::isViewModelFeatureEnabled,
                PotsmodConfigData::setViewModelFeatureEnabled,
                List.of(
                        integer("x_offset", "X Offset", "Horizontal offset in hundredths.", -150, 150, PotsmodConfigData::getXOffset, PotsmodConfigData::setXOffset),
                        integer("y_offset", "Y Offset", "Vertical offset in hundredths.", -150, 150, PotsmodConfigData::getYOffset, PotsmodConfigData::setYOffset),
                        integer("z_offset", "Z Offset", "Depth offset in hundredths.", -150, 50, PotsmodConfigData::getZOffset, PotsmodConfigData::setZOffset),
                        integer("x_rotation", "X Rotation", "Pitch rotation.", -180, 180, PotsmodConfigData::getXRotation, PotsmodConfigData::setXRotation),
                        integer("y_rotation", "Y Rotation", "Yaw rotation.", -180, 180, PotsmodConfigData::getYRotation, PotsmodConfigData::setYRotation),
                        integer("z_rotation", "Z Rotation", "Roll rotation.", -180, 180, PotsmodConfigData::getZRotation, PotsmodConfigData::setZRotation),
                        floating("item_scale", "Item Scale", "Held item scale multiplier.", 0.1f, 3.0f, PotsmodConfigData::getItemScale, PotsmodConfigData::setItemScale),
                        bool("disable_held_bobbing", "Disable Bobbing", "Renders hands without vanilla bobbing.", PotsmodConfigData::isDisableHeldBobbing, PotsmodConfigData::setDisableHeldBobbing)
                )
        );
    }


    private static SettingSection inventoryCalculatorSection(SettingCategory category) {
        return featureSection(
                "inventory_calculator",
                "Inventory Calculator",
                "Math expression calculator shown in the inventory screen.",
                category,
                PotsmodConfigData::isInventoryCalculatorEnabled,
                PotsmodConfigData::setInventoryCalculatorEnabled,
                List.of()
        );
    }

    private static SettingSection resourceTickSection(SettingCategory category) {
        return SettingSection.standard(
                "resource_tick",
                "Resource Tick",
                "Tracks the current guild resource tick from territory data.",
                category,
                null,
                null,
                List.of(
                        bool("enable_overlay", "Enable Overlay", "Enable the resource tick overlay.", PotsmodConfigData::isResourceTickOverlayEnabled, PotsmodConfigData::setResourceTickOverlayEnabled),
                        button("res_overlay", "Move Overlay", "Move the resource tick overlay", () -> OverlayManager.openConfig(List.of(FeatureManager.getResTickFeature().getResTickOverlay())), "Open Editor")
                )
        );
    }

    private static SettingSection territoryBoxesSection(SettingCategory category) {
        return featureSection(
                "territory_boxes",
                "Territory Boxes",
                "Draws upcoming attack territories directly in-world.",
                category,
                PotsmodConfigData::isWarTimersFeatureEnabled,
                PotsmodConfigData::setWarTimersFeatureEnabled,
                List.of(
                        color("territory_color", "Box Color", "Color used for queued territory outlines.", PotsmodConfigData::getColor, PotsmodConfigData::setColor),
                        color("territory_color_inside", "Inside Box Color", "Color used for queued territory outlines that you are inside.", PotsmodConfigData::getColorInside, PotsmodConfigData::setColorInside),
                        color("unqueued_territory_color", "Unqueued Box Color", "Color used for unqueued territory outlines.", PotsmodConfigData::getNotQColor, PotsmodConfigData::setNotQColor),
                        color("unqueued_territory_inside_color", "Inside Unqueued Box Color", "Color used for unqueued territory outlines that you are inside.", PotsmodConfigData::getNotQInsideColor, PotsmodConfigData::setNotQInsideColor),
                        integer("maximum_territories", "Max Territories", "Maximum queued territories to render.", 1, 30, PotsmodConfigData::getMaximumTerritories, PotsmodConfigData::setMaximumTerritories),
                        integer("maximum_distance", "Max Distance", "Max squared-distance filter input.", 50, 5000, PotsmodConfigData::getMaximumDistance, PotsmodConfigData::setMaximumDistance),
                        integer("maximum_terr_warn", "Max Territory Warning", "How many upcoming attack timers to warn you about (set 0 to disable)", 0, 10, PotsmodConfigData::getTerritoryWarningCount, PotsmodConfigData::setTerritoryWarningCount),
                        integer("max_time_warn", "Max Queue Time", "Maximum time of an attack timer to be included (min)", 1, 10, PotsmodConfigData::getMaxTimeTerr, PotsmodConfigData::setMaxTimeTerr),
                        integer("often_warn", "Warn Speed", "How many seconds between warns", 10, 120, PotsmodConfigData::getWarnTime, PotsmodConfigData::setWarnTime)
                )
        );
    }

    private static SettingSection defenseColorOverlaySection(SettingCategory category) {
        return featureSection(
                "defense_color_overlay",
                "Defense Color Overlay",
                "Territory defense tier overlays and connection highlights on the guild territory map.",
                category,
                PotsmodConfigData::isDefenseColorOverlayFeatureEnabled,
                PotsmodConfigData::setDefenseColorOverlayFeatureEnabled,
                List.of(
                        floating("overlay_opacity", "Overlay Opacity", "Transparency of the territory defense boxes.", 0.10f, 1.00f, PotsmodConfigData::getDefenseOverlayOpacity, PotsmodConfigData::setDefenseOverlayOpacity),
                        integer("border_thickness", "Border Thickness", "Thickness of territory box borders.", 1, 8, PotsmodConfigData::getDefenseOverlayBorderThickness, PotsmodConfigData::setDefenseOverlayBorderThickness),
                        color("color_very_low", "Very Low Defense Color", "Color for Very Low defense territories.", PotsmodConfigData::getDefenseColorVeryLow, PotsmodConfigData::setDefenseColorVeryLow),
                        color("color_low", "Low Defense Color", "Color for Low defense territories.", PotsmodConfigData::getDefenseColorLow, PotsmodConfigData::setDefenseColorLow),
                        color("color_medium", "Medium Defense Color", "Color for Medium defense territories.", PotsmodConfigData::getDefenseColorMedium, PotsmodConfigData::setDefenseColorMedium),
                        color("color_high", "High Defense Color", "Color for High defense territories.", PotsmodConfigData::getDefenseColorHigh, PotsmodConfigData::setDefenseColorHigh),
                        color("color_very_high", "Very High Defense Color", "Color for Very High defense territories.", PotsmodConfigData::getDefenseColorVeryHigh, PotsmodConfigData::setDefenseColorVeryHigh),
                        color("color_conns", "Connections Color", "Color for CONNS highlights.", PotsmodConfigData::getHighlightColorConns, PotsmodConfigData::setHighlightColorConns),
                        color("color_externals", "Externals Color", "Color for EXTERNALS highlights.", PotsmodConfigData::getHighlightColorExternals, PotsmodConfigData::setHighlightColorExternals)
                )
        );
    }

    private static SettingSection worldMapHudSection(SettingCategory category) {
        return featureSection(
                "world_map_hud",
                "World Map HUD",
                "Keybind-toggled real-time Wynntils map overlay on your HUD.",
                category,
                PotsmodConfigData::isWorldMapHudFeatureEnabled,
                PotsmodConfigData::setWorldMapHudFeatureEnabled,
                List.of(
                        keybind("toggle_key", "Toggle Key", "Key to toggle or peek the map overlay.", PotsmodConfigData::getWorldMapHudToggleKey, PotsmodConfigSections::setWorldMapHudToggleKey),
                        keybind("zoom_in_key", "Zoom In Key", "Key to zoom in the map overlay.", PotsmodConfigData::getWorldMapHudZoomInKey, PotsmodConfigSections::setWorldMapHudZoomInKey),
                        keybind("zoom_out_key", "Zoom Out Key", "Key to zoom out the map overlay.", PotsmodConfigData::getWorldMapHudZoomOutKey, PotsmodConfigSections::setWorldMapHudZoomOutKey),
                        integer("hold_threshold", "Hold Threshold", "Milliseconds to hold toggle key before peek mode activates.", 50, 1000, PotsmodConfigData::getWorldMapHudHoldThresholdMs, PotsmodConfigData::setWorldMapHudHoldThresholdMs),
                        floating("pointer_opacity", "Pointer Opacity", "Transparency of the player pointer on the map overlay.", 0.05f, 1.0f, PotsmodConfigData::getPointerOpacity, PotsmodConfigData::setPointerOpacity),
                        floating("terrain_opacity", "Terrain Opacity", "Transparency of terrain map tiles.", 0.05f, 1.0f, PotsmodConfigData::getTerrainOpacity, PotsmodConfigData::setTerrainOpacity),
                        floating("territory_opacity", "Territory Opacity", "Transparency of territory boxes.", 0.05f, 1.0f, PotsmodConfigData::getTerritoryOpacity, PotsmodConfigData::setTerritoryOpacity),
                        floating("border_opacity", "Border Opacity", "Transparency of the map border frame.", 0.05f, 1.0f, PotsmodConfigData::getBorderOpacity, PotsmodConfigData::setBorderOpacity),
                        bool("show_border", "Show Border", "Whether to render the Wynntils map border frame.", PotsmodConfigData::isShowBorder, PotsmodConfigData::setShowBorder),
                        floating("defense_tint_alpha", "Defense Tint Alpha", "Transparency of the defense color overlay on the HUD map.", 0.05f, 1.0f, PotsmodConfigData::getDefenseTintAlpha, PotsmodConfigData::setDefenseTintAlpha)
                )
        );
    }

    private static void setWorldMapHudToggleKey(PotsmodConfigData data, String keyStr) {
        data.setWorldMapHudToggleKey(keyStr);
        if (FeatureManager.getWorldMapHudFeature() != null) {
            FeatureManager.getWorldMapHudFeature().updateKeys();
        }
    }

    private static void setWorldMapHudZoomInKey(PotsmodConfigData data, String keyStr) {
        data.setWorldMapHudZoomInKey(keyStr);
        if (FeatureManager.getWorldMapHudFeature() != null) {
            FeatureManager.getWorldMapHudFeature().updateKeys();
        }
    }

    private static void setWorldMapHudZoomOutKey(PotsmodConfigData data, String keyStr) {
        data.setWorldMapHudZoomOutKey(keyStr);
        if (FeatureManager.getWorldMapHudFeature() != null) {
            FeatureManager.getWorldMapHudFeature().updateKeys();
        }
    }

    private static SettingSection territoryRouteFlowSection(SettingCategory category) {
        return featureSection(
                "territory_route_flow",
                "Territory Route Flow",
                "Shows animated resource flow on the guild map.",
                category,
                PotsmodConfigData::isTerritoryRouteFlowFeatureEnabled,
                PotsmodConfigData::setTerritoryRouteFlowFeatureEnabled,
                List.of(
                        bool("hq_to_territory_enabled", "HQ to Territory", "Render the route flowing from the headquarters to the hovered territory.", PotsmodConfigData::isHqToTerritoryRouteEnabled, PotsmodConfigData::setHqToTerritoryRouteEnabled),
                        bool("territory_to_hq_enabled", "Territory to HQ", "Render the route flowing from the hovered territory back to the headquarters.", PotsmodConfigData::isTerritoryToHqRouteEnabled, PotsmodConfigData::setTerritoryToHqRouteEnabled),
                        color("hq_to_territory_color", "HQ to Territory Color", "Color of the HQ-to-territory route.", PotsmodConfigData::getHqToTerritoryLineColor, PotsmodConfigData::setHqToTerritoryLineColor),
                        color("territory_to_hq_color", "Territory to HQ Color", "Color of the territory-to-HQ route.", PotsmodConfigData::getTerritoryToHqLineColor, PotsmodConfigData::setTerritoryToHqLineColor),
                        floating("route_width", "Line Width", "Thickness shared by both routes.", 0.5f, 4.0f, PotsmodConfigData::getTerritoryRouteLineWidth, PotsmodConfigData::setTerritoryRouteLineWidth),
                        floating("route_glow", "Glow", "Bloom strength shared by both routes.", 0.0f, 1.0f, PotsmodConfigData::getTerritoryRouteGlowStrength, PotsmodConfigData::setTerritoryRouteGlowStrength),
                        floating("light_length", "Light Length", "Length of the travelling light streaks.", 1.0f, 32.0f, PotsmodConfigData::getTerritoryRouteLightLength, PotsmodConfigData::setTerritoryRouteLightLength),
                        floating("light_spacing", "Light Spacing", "Distance between travelling light streaks.", 8.0f, 80.0f, PotsmodConfigData::getTerritoryRouteLightSpacing, PotsmodConfigData::setTerritoryRouteLightSpacing),
                        floating("light_speed", "Light Speed", "Travel speed of the resource-flow lights.", 0.0f, 100.0f, PotsmodConfigData::getTerritoryRouteLightSpeed, PotsmodConfigData::setTerritoryRouteLightSpeed)
                )
        );
    }

    private static SettingSection consumableLabelsSection(SettingCategory category) {
        return featureSection(
                "consumable_labels",
                "Consumable Labels",
                "Compact crafted-consumable identifiers in inventories.",
                category,
                PotsmodConfigData::isConsuTextFeatureEnabled,
                PotsmodConfigSections::setConsuTextFeatureEnabled,
                List.of(
                        choice("texture_category", "Texture Category", "Model texture pack for consumable item icons.", PotsmodConfigData::getConsumableTextureCategory, PotsmodConfigSections::setConsumableTextureCategory, ConsuTextFeature::textureCategoryOptions, ConsuTextFeature::textureCategoryLabel),
                        floating("label_scale", "Label Scale", "Overlay text scale.", 0.25f, 2.5f, PotsmodConfigData::getIdScale, PotsmodConfigData::setIdScale),
                        integer("label_x_offset", "Label X Offset", "Horizontal label offset.", -16, 16, PotsmodConfigData::getIdXOffset, PotsmodConfigData::setIdXOffset),
                        integer("label_y_offset", "Label Y Offset", "Vertical label offset.", -16, 16, PotsmodConfigData::getIdYOffset, PotsmodConfigData::setIdYOffset)
                )
        );
    }

    private static SettingSection towerEhpSection(SettingCategory category) {
        return featureSection(
                "tower_ehp",
                "Tower EHP",
                "Replaces tower boss-bar HP with effective HP.",
                category,
                PotsmodConfigData::isWarTowerEhpFeatureEnabled,
                PotsmodConfigData::setWarTowerEhpFeatureEnabled,
                List.of()
        );
    }

    private static SettingSection shoutFilterSection(SettingCategory category) {
        return featureSection(
                "shout_filter",
                "Shout Filter",
                "Modify shouts to be less obstructive.",
                category,
                PotsmodConfigData::isShoutFilterFeatureEnabled,
                PotsmodConfigData::setShoutFilterFeatureEnabled,
                List.of(
                        choice("filter_mode", "Shout Filter Mode", "What to replace shouts with", PotsmodConfigData::getShoutFilterMode, PotsmodConfigData::setShoutFilterMode, ShoutReplacement.class)
                )
        );
    }

    private static SettingSection radianceSyncSection(SettingCategory category) {
        return featureSection(
                "radiance_sync",
                "Radiance Sync",
                "Tracks Radiance with Guard Partners in War",
                category,
                PotsmodConfigData::isRadianceSyncEnabled,
                PotsmodConfigData::setRadianceSyncEnabled,
                List.of(
                        string("group_key", "Group Key", "Shared sync group key (shared with party members).", PotsmodConfigData::getRadianceSyncGroupKey, PotsmodConfigData::setRadianceSyncGroupKey),
                        integer("self_aspect_tier", "Self Aspect Tier", "Your Radiance aspect tier (0 = none, 1-3).", 0, 3, PotsmodConfigData::getRadianceSyncSelfTier, PotsmodConfigData::setRadianceSyncSelfTier),
                        bool("require_war", "Require War", "Only show overlay while in war.", PotsmodConfigData::isRadianceSyncRequireWar, PotsmodConfigData::setRadianceSyncRequireWar),
                        choice("overlay_mode", "Overlay Mode", "cast = cast prompt mode; status = radiance remaining.", PotsmodConfigData::getRadianceSyncOverlayMode, PotsmodConfigData::setRadianceSyncOverlayMode, RadianceOverlayMode.class),
                        string("worker_url", "Worker URL", "WebSocket worker URL for Radiance sync.", PotsmodConfigData::getRadianceSyncWorkerUrl, PotsmodConfigData::setRadianceSyncWorkerUrl),
                        floating("cast_prompt_seconds", "Cast Prompt Seconds", "How long the cast prompt stays visible.", 0.5f, 10.0f, PotsmodConfigData::getRadianceSyncCastPromptSeconds, PotsmodConfigData::setRadianceSyncCastPromptSeconds),
                        bool("ping_sound", "Ping Sound", "Play a sound when Radiance is ready.", PotsmodConfigData::isRadianceSyncPingEnabled, PotsmodConfigData::setRadianceSyncPingEnabled),
                        floating("ping_volume", "Ping Volume", "Volume of the ping sound.", 0.1f, 2.0f, PotsmodConfigData::getRadianceSyncPingVolume, PotsmodConfigData::setRadianceSyncPingVolume),
                        floating("ping_pitch", "Ping Pitch", "Pitch of the ping sound.", 0.5f, 2.0f, PotsmodConfigData::getRadianceSyncPingPitch, PotsmodConfigData::setRadianceSyncPingPitch),
                        button("move_overlay", "Move Overlay", "Move the sync overlay", () -> OverlayManager.openConfig(List.of(FeatureManager.getRadianceSyncFeature().getOverlay())), "Open Editor")
                )
        );
    }

    private static SettingSection featureSection(
            String id,
            String title,
            String description,
            SettingCategory category,
            Function<PotsmodConfigData, Boolean> enabledGetter,
            BiConsumer<PotsmodConfigData, Boolean> enabledSetter,
            List<ConfigSetting<?>> settings
    ) {
        return SettingSection.standard(
                id,
                title,
                description,
                category,
                enabled(enabledGetter),
                sectionToggle(enabledSetter),
                settings
        );
    }

    private static PotsmodConfigData config() {
        return PotsmodConfig.getData();
    }

    private static java.util.function.Supplier<Boolean> enabled(Function<PotsmodConfigData, Boolean> getter) {
        return () -> getter.apply(config());
    }

    private static java.util.function.Consumer<Boolean> sectionToggle(BiConsumer<PotsmodConfigData, Boolean> setter) {
        return value -> {
            setter.accept(config(), value);
            PotsmodConfig.applyFeatureStates();
        };
    }

    private static StringSetting string(String id, String title, String description, Function<PotsmodConfigData, String> getter, BiConsumer<PotsmodConfigData, String> setter) {
        return new StringSetting(id, title, description, () -> getter.apply(config()), persist(setter));
    }

    private static KeybindSetting keybind(String id, String title, String description, Function<PotsmodConfigData, String> getter, BiConsumer<PotsmodConfigData, String> setter) {
        return new KeybindSetting(id, title, description, () -> getter.apply(config()), persist(setter));
    }

    private static IntSetting integer(
            String id,
            String title,
            String description,
            int min,
            int max,
            Function<PotsmodConfigData, Integer> getter,
            BiConsumer<PotsmodConfigData, Integer> setter
    ) {
        return new IntSetting(id, title, description, min, max, () -> getter.apply(config()), persist(setter));
    }

    private static FloatSetting floating(
            String id,
            String title,
            String description,
            float min,
            float max,
            Function<PotsmodConfigData, Float> getter,
            BiConsumer<PotsmodConfigData, Float> setter
    ) {
        return new FloatSetting(id, title, description, min, max, () -> getter.apply(config()), persist(setter));
    }

    private static BooleanSetting bool(
            String id,
            String title,
            String description,
            Function<PotsmodConfigData, Boolean> getter,
            BiConsumer<PotsmodConfigData, Boolean> setter
    ) {
        return new BooleanSetting(id, title, description, () -> getter.apply(config()), persist(setter));
    }

    private static ColorSetting color(
            String id,
            String title,
            String description,
            Function<PotsmodConfigData, Integer> getter,
            BiConsumer<PotsmodConfigData, Integer> setter
    ) {
        return new ColorSetting(id, title, description, () -> getter.apply(config()), persist(setter));
    }

    private static ButtonSetting button(String id, String title, String description, Runnable action, String buttonText) {
        return new ButtonSetting(id, title, description, action, buttonText);
    }

    private static <T extends Enum<T>> ChoiceSetting choice(
            String id,
            String title,
            String description,
            Function<PotsmodConfigData, T> getter,
            BiConsumer<PotsmodConfigData, T> setter,
            Class<T> type
    ) {
        Function<String, String> labelResolver = raw -> {
            String lower = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        };

        return new ChoiceSetting(
                id,
                title,
                description,
                () -> {
                    T value = getter.apply(config());
                    if (value == null) {
                        return type.getEnumConstants()[0].name();
                    }
                    return value.name();
                },
                value -> setter.accept(config(), T.valueOf(type, value)),
                Arrays.stream(type.getEnumConstants()).map(Enum::name).toList(),
                labelResolver
        );
    }

    private static ChoiceSetting choice(
            String id,
            String title,
            String description,
            Function<PotsmodConfigData, String> getter,
            BiConsumer<PotsmodConfigData, String> setter,
            List<String> options,
            Function<String, String> labelResolver
    ) {
        return new ChoiceSetting(
                id,
                title,
                description,
                () -> getter.apply(config()),
                value -> setter.accept(config(), value),
                options,
                labelResolver
        );
    }

    private static ChoiceSetting choice(
            String id,
            String title,
            String description,
            Function<PotsmodConfigData, String> getter,
            BiConsumer<PotsmodConfigData, String> setter,
            Supplier<List<String>> options,
            Function<String, String> labelResolver
    ) {
        return new ChoiceSetting(
                id,
                title,
                description,
                () -> getter.apply(config()),
                value -> setter.accept(config(), value),
                options,
                labelResolver
        );
    }

    private static <T> java.util.function.Consumer<T> persist(BiConsumer<PotsmodConfigData, T> setter) {
        return value -> {
            setter.accept(config(), value);
            PotsmodConfig.save();
        };
    }

    private static void setClickGuiTheme(PotsmodConfigData data, String value) {
        ThemeOption nextTheme = ThemeOption.resolve(value);
        keepCustomTheme(data);
        data.setClickGuiTheme(nextTheme.getKey());
    }

    private static void setConsumableTextureCategory(PotsmodConfigData data, String value) {
        data.setConsumableTextureCategory(ConsuTextFeature.resolveTextureCategory(value));
        ConsuTextFeature.refreshLoadedItemModels();
    }

    private static void setConsuTextFeatureEnabled(PotsmodConfigData data, Boolean value) {
        data.setConsuTextFeatureEnabled(value);
        ConsuTextFeature.refreshLoadedItemModels();
    }

    private static void setCustomGuiBackground(PotsmodConfigData data, Integer value) {
        updateCustomThemeColor(data, PotsmodConfigSections::getVisibleCustomGuiBackground, PotsmodConfigData::setCustomGuiBackground, value);
    }

    private static void setCustomGuiSecondary(PotsmodConfigData data, Integer value) {
        updateCustomThemeColor(data, PotsmodConfigSections::getVisibleCustomGuiSecondary, PotsmodConfigData::setCustomGuiSecondary, value);
    }

    private static void setCustomGuiAccent(PotsmodConfigData data, Integer value) {
        updateCustomThemeColor(data, PotsmodConfigSections::getVisibleCustomGuiAccent, PotsmodConfigData::setCustomGuiAccent, value);
    }

    private static void updateCustomThemeColor(
            PotsmodConfigData data,
            Function<PotsmodConfigData, Integer> visibleGetter,
            BiConsumer<PotsmodConfigData, Integer> setter,
            int value
    ) {
        int normalisedValue = value & 0xFFFFFF;
        int previousVisibleValue = visibleGetter.apply(data) & 0xFFFFFF;
        if (PotsmodConfig.getClickGuiThemeOption() != ThemeOption.CUSTOM && normalisedValue != previousVisibleValue) {
            copyThemeToCustom(data, PotsmodConfig.getClickGuiThemeOption().getTheme());
            data.setClickGuiTheme(ThemeOption.CUSTOM.getKey());
        }
        setter.accept(data, normalisedValue);
    }

    private static int getVisibleCustomGuiBackground(PotsmodConfigData data) {
        if (PotsmodConfig.getClickGuiThemeOption() == ThemeOption.CUSTOM) {
            return data.getCustomGuiBackground() & 0xFFFFFF;
        }
        return PotsmodConfig.getClickGuiThemeOption().getTheme().background() & 0xFFFFFF;
    }

    private static int getVisibleCustomGuiSecondary(PotsmodConfigData data) {
        if (PotsmodConfig.getClickGuiThemeOption() == ThemeOption.CUSTOM) {
            return data.getCustomGuiSecondary() & 0xFFFFFF;
        }
        return PotsmodConfig.getClickGuiThemeOption().getTheme().secondary() & 0xFFFFFF;
    }

    private static int getVisibleCustomGuiAccent(PotsmodConfigData data) {
        if (PotsmodConfig.getClickGuiThemeOption() == ThemeOption.CUSTOM) {
            return data.getCustomGuiAccent() & 0xFFFFFF;
        }
        return PotsmodConfig.getClickGuiThemeOption().getTheme().accentColor() & 0xFFFFFF;
    }

    private static void keepCustomTheme(PotsmodConfigData data) {
        data.setCustomGuiBackground(data.getCustomGuiBackground() & 0xFFFFFF);
        data.setCustomGuiSecondary(data.getCustomGuiSecondary() & 0xFFFFFF);
        data.setCustomGuiAccent(data.getCustomGuiAccent() & 0xFFFFFF);
    }

    private static void copyThemeToCustom(PotsmodConfigData data, GuiTheme theme) {
        data.setCustomGuiBackground(theme.background() & 0xFFFFFF);
        data.setCustomGuiSecondary(theme.secondary() & 0xFFFFFF);
        data.setCustomGuiAccent(theme.accentColor() & 0xFFFFFF);
    }

    @FunctionalInterface
    private interface SectionFactory {
        SettingSection create(SettingCategory category);
    }
}
