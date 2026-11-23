package org.pots.potsmod.config;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.pots.potsmod.PotsmodClient;
import org.pots.potsmod.config.choices.SettingCategory;
import org.pots.potsmod.config.setting.SettingSection;
import org.pots.potsmod.features.Feature;
import org.pots.potsmod.gui.screen.ConfigScreen;
import org.pots.potsmod.gui.theme.FontOption;
import org.pots.potsmod.gui.theme.ThemeOption;
import org.pots.potsmod.managers.FeatureManager;
import org.pots.potsmod.managers.KeybindManager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@UtilityClass
public class PotsmodConfig {
    private static final Gson GSON = new Gson();
    private static final List<SettingSection> SECTIONS = List.copyOf(PotsmodConfigSections.createSections());

    @Getter
    private static PotsmodConfigData data = new PotsmodConfigData();

    public static void init() {
        loadConfig();
        KeybindManager.registerKeybinding(
                "Open Potsmod Click GUI",
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                () -> minecraft().setScreen(getConfigScreen())
        );
    }

    public static void onFeaturesInitialized() {
        applyFeatureStates();
    }

    public static void reset() {
        data = new PotsmodConfigData();
        data.normalise();
        save();
        applyFeatureStates();
    }

    public static Screen getConfigScreen() {
        return getConfigScreen(minecraft().screen);
    }

    public static Screen getConfigScreen(Screen currentScreen) {
        return new ConfigScreen(currentScreen);
    }

    public static boolean isCustomTerritoryMapEnabled() {
        return data.isCustomTerritoryMapEnabled();
    }

    public static boolean isCustomEconomyMenuEnabled() {
        return data.isCustomEconomyMenuEnabled();
    }

    public static int getTerritoryColorMode() {
        return data.getTerritoryColorMode();
    }

    public static void setTerritoryColorMode(int mode) {
        data.setTerritoryColorMode(mode);
        save();
    }

    public static List<SettingSection> getSections(SettingCategory category) {
        return SECTIONS.stream()
                .filter(section -> section.category() == category)
                .toList();
    }

    public static ThemeOption getClickGuiThemeOption() {
        return ThemeOption.resolve(getData().getClickGuiTheme());
    }

    public static FontOption getClickGuiFontOption() {
        return FontOption.resolve(getData().getClickGuiFont());
    }

    public static void save() {
        try {
            Files.createDirectories(configDir());
            try (Writer writer = Files.newBufferedWriter(configFile())) {
                GSON.toJson(data, writer);
            }
        } catch (IOException exception) {
            PotsmodClient.LOGGER.error("Failed to save config", exception);
        }
    }

    public static void applyFeatureStates() {
        PotsmodConfigData config = getData();
        applyEnabledState(FeatureManager.getChatEncryptionFeature(), config.isChatEncryptionFeatureEnabled());
        applyEnabledState(FeatureManager.getWarTimersFeature(), config.isWarTimersFeatureEnabled());
        applyEnabledState(FeatureManager.getWarTowerEHPFeature(), config.isWarTowerEhpFeatureEnabled());
        applyEnabledState(FeatureManager.getConsuTextFeature(), config.isConsuTextFeatureEnabled());
        applyEnabledState(FeatureManager.getShoutFilterFeature(), config.isShoutFilterFeatureEnabled());
        applyEnabledState(FeatureManager.getViewModelTransformationFeature(), config.isViewModelFeatureEnabled());
        applyEnabledState(FeatureManager.getRadianceSyncFeature(), config.isRadianceSyncEnabled());
        applyEnabledState(FeatureManager.getTerritoryRouteFlowFeature(), config.isTerritoryRouteFlowFeatureEnabled());
        applyEnabledState(FeatureManager.getDefenseColorOverlayFeature(), config.isDefenseColorOverlayFeatureEnabled());
        applyEnabledState(FeatureManager.getWorldMapHudFeature(), config.isWorldMapHudFeatureEnabled());
        applyEnabledState(FeatureManager.getInventoryCalculatorFeature(), config.isInventoryCalculatorEnabled());
        applyEnabledState(FeatureManager.getGuildWebhookFeature(), config.isGuildWebhooksFeatureEnabled());
    }

    private static void applyEnabledState(Feature feature, boolean enabled) {
        if (feature != null) {
            feature.setEnabled(enabled);
        }
    }

    private static void loadConfig() {
        if (!Files.exists(configFile())) {
            Path legacyRmod = configDir().resolve("rmod.json");
            Path legacyNyah = configDir().resolve("nyah-mod.json");
            Path source = Files.exists(legacyRmod) ? legacyRmod : (Files.exists(legacyNyah) ? legacyNyah : null);

            if (source != null) {
                try (Reader reader = Files.newBufferedReader(source)) {
                    data = GSON.fromJson(reader, PotsmodConfigData.class);
                } catch (Exception ignored) {
                }
            }
            if (data == null) {
                data = new PotsmodConfigData();
            }
            data.normalise();
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(configFile())) {
            data = GSON.fromJson(reader, PotsmodConfigData.class);
        } catch (IOException exception) {
            PotsmodClient.LOGGER.error("Error loading config file!", exception);
            data = new PotsmodConfigData();
        }

        if (data == null) {
            data = new PotsmodConfigData();
        }
        data.normalise();
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    private static Path configDir() {
        return minecraft().gameDirectory.toPath().resolve("config");
    }

    private static Path configFile() {
        return configDir().resolve("potsmod.json");
    }
}
