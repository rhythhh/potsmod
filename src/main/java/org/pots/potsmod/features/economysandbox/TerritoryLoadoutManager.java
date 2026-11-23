/*
 * Copyright © RMod 2026.
 * This file is released under LGPLv3. See LICENSE for full license details.
 */
package org.pots.potsmod.features.economysandbox;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import org.pots.potsmod.PotsmodClient;
import org.pots.potsmod.features.economysandbox.EconomySandboxData.SandboxUpgrade;
import org.pots.potsmod.features.economysandbox.EconomySandboxData.TreasuryTier;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class TerritoryLoadoutManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SLOT_COUNT = 27; // 3 rows of 9 slots like a single chest

    public static class TerritoryLoadoutSlot {
        public String name = "Empty";
        public String lore = "";
        public TreasuryTier treasury = null;
        public Map<SandboxUpgrade, Integer> upgrades = new EnumMap<>(SandboxUpgrade.class);
        public boolean configured = false;

        public TerritoryLoadoutSlot() {}

        public TerritoryLoadoutSlot(String name, String lore, TreasuryTier treasury, Map<SandboxUpgrade, Integer> upgrades) {
            this.name = name;
            this.lore = lore != null ? lore : "";
            this.treasury = treasury;
            if (upgrades != null) {
                this.upgrades.putAll(upgrades);
            }
            this.configured = true;
        }

        public boolean isConfigured() {
            return configured;
        }

        public void clear() {
            this.name = "Empty";
            this.lore = "";
            this.treasury = null;
            this.upgrades.clear();
            this.configured = false;
        }
    }

    private static final List<TerritoryLoadoutSlot> SLOTS = new ArrayList<>(SLOT_COUNT);

    static {
        for (int i = 0; i < SLOT_COUNT; i++) {
            SLOTS.add(new TerritoryLoadoutSlot());
        }
        load();
    }

    public static List<TerritoryLoadoutSlot> getSlots() {
        return SLOTS;
    }

    public static TerritoryLoadoutSlot getSlot(int index) {
        if (index >= 0 && index < SLOTS.size()) {
            return SLOTS.get(index);
        }
        return null;
    }

    public static void saveSlotFromTerritory(int index, String loadoutName, EconomyTerritoryState territory) {
        if (index < 0 || index >= SLOTS.size() || territory == null) return;
        TerritoryLoadoutSlot slot = new TerritoryLoadoutSlot(
                loadoutName != null && !loadoutName.isBlank() ? loadoutName : territory.getName(),
                "",
                territory.getTreasury(),
                territory.getUpgrades()
        );
        SLOTS.set(index, slot);
        save();
    }

    public static void applySlotToTerritory(int index, EconomyTerritoryState territory) {
        if (index < 0 || index >= SLOTS.size() || territory == null) return;
        TerritoryLoadoutSlot slot = SLOTS.get(index);
        if (slot == null || !slot.isConfigured()) return;
        if (slot.treasury != null) {
            territory.setTreasury(slot.treasury);
        }
        for (SandboxUpgrade u : SandboxUpgrade.values()) {
            territory.setUpgradeLevel(u, slot.upgrades.getOrDefault(u, 0));
        }
    }

    public static Path getFilePath() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath().resolve("config").resolve("territory_loadouts.json");
    }

    public static void save() {
        try {
            Path file = getFilePath();
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(SLOTS, writer);
            }
        } catch (Exception e) {
            PotsmodClient.LOGGER.error("Failed to save territory loadouts", e);
        }
    }

    public static void load() {
        try {
            Path file = getFilePath();
            if (!Files.exists(file)) return;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<List<TerritoryLoadoutSlot>>() {}.getType();
                List<TerritoryLoadoutSlot> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    for (int i = 0; i < SLOT_COUNT && i < loaded.size(); i++) {
                        SLOTS.set(i, loaded.get(i));
                    }
                }
            }
        } catch (Exception e) {
            PotsmodClient.LOGGER.error("Failed to load territory loadouts", e);
        }
    }
}
