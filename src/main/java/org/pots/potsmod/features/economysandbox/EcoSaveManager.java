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

public final class EcoSaveManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int SLOT_COUNT = 27;

    public static class EcoSaveSlot {
        public String name = "Empty";
        public boolean configured = false;
        public String defaultTreasury = null;
        public Map<String, Integer> tributes = new HashMap<>();
        // territory name -> {upgrades map + treasury}
        public List<SavedTerritory> territories = new ArrayList<>();

        public boolean isConfigured() { return configured; }

        public void clear() {
            name = "Empty";
            configured = false;
            defaultTreasury = null;
            tributes.clear();
            territories.clear();
        }

        public static class SavedTerritory {
            public String name;
            public String treasury;
            public boolean isHq;
            public Map<String, Integer> upgrades = new HashMap<>();
        }
    }

    private static final List<EcoSaveSlot> SLOTS = new ArrayList<>(SLOT_COUNT);

    static {
        for (int i = 0; i < SLOT_COUNT; i++) SLOTS.add(new EcoSaveSlot());
        load();
    }

    public static List<EcoSaveSlot> getSlots() { return SLOTS; }

    public static EcoSaveSlot getSlot(int index) {
        if (index >= 0 && index < SLOTS.size()) return SLOTS.get(index);
        return null;
    }

    public static EcoSaveSlot createSlotFromState(String slotName, EconomySandboxState state) {
        if (state == null) return null;
        EcoSaveSlot slot = new EcoSaveSlot();
        slot.name = slotName != null && !slotName.isBlank() ? slotName : "Eco Setup";
        slot.configured = true;
        if (state.getDefaultTreasury() != null) slot.defaultTreasury = state.getDefaultTreasury().name();
        state.getTributes().forEach((res, val) -> slot.tributes.put(res.name(), val));
        for (EconomyTerritoryState terr : state.getAddedTerritories().values()) {
            EcoSaveSlot.SavedTerritory st = new EcoSaveSlot.SavedTerritory();
            st.name = terr.getName();
            if (terr.getTreasury() != null) st.treasury = terr.getTreasury().name();
            st.isHq = terr.isHq();
            for (SandboxUpgrade u : SandboxUpgrade.values()) {
                int lvl = terr.getUpgradeLevel(u);
                if (lvl != 0) st.upgrades.put(u.name(), lvl);
            }
            slot.territories.add(st);
        }
        return slot;
    }

    public static EconomySandboxState slotToState(EcoSaveSlot slot) {
        if (slot == null || !slot.isConfigured()) return new EconomySandboxState();
        EconomySandboxState state = new EconomySandboxState();
        if (slot.defaultTreasury != null) {
            try { state.setDefaultTreasury(TreasuryTier.valueOf(slot.defaultTreasury)); } catch (Exception ignored) {}
        }
        slot.tributes.forEach((resName, val) -> {
            try {
                var res = com.wynntils.models.territories.type.GuildResource.valueOf(resName);
                state.getTributes().put(res, val);
            } catch (Exception ignored) {}
        });
        for (EcoSaveSlot.SavedTerritory st : slot.territories) {
            EconomyTerritoryState terr = state.addTerritory(st.name);
            if (st.treasury != null) try { terr.setTreasury(TreasuryTier.valueOf(st.treasury)); } catch (Exception ignored) {}
            terr.setHq(st.isHq);
            st.upgrades.forEach((uName, lvl) -> {
                try { terr.setUpgradeLevel(SandboxUpgrade.valueOf(uName), lvl); } catch (Exception ignored) {}
            });
        }
        state.invalidateDistanceCache();
        return state;
    }

    public static void saveSlotFromState(int index, String slotName, EconomySandboxState state) {
        if (index < 0 || index >= SLOTS.size() || state == null) return;
        EcoSaveSlot slot = new EcoSaveSlot();
        slot.name = slotName != null && !slotName.isBlank() ? slotName : "Eco Setup";
        slot.configured = true;
        if (state.getDefaultTreasury() != null) slot.defaultTreasury = state.getDefaultTreasury().name();
        state.getTributes().forEach((res, val) -> slot.tributes.put(res.name(), val));
        for (EconomyTerritoryState terr : state.getAddedTerritories().values()) {
            EcoSaveSlot.SavedTerritory st = new EcoSaveSlot.SavedTerritory();
            st.name = terr.getName();
            if (terr.getTreasury() != null) st.treasury = terr.getTreasury().name();
            st.isHq = terr.isHq();
            for (SandboxUpgrade u : SandboxUpgrade.values()) {
                int lvl = terr.getUpgradeLevel(u);
                if (lvl != 0) st.upgrades.put(u.name(), lvl);
            }
            slot.territories.add(st);
        }
        SLOTS.set(index, slot);
        save();
    }

    public static void applySlotToState(int index, EconomySandboxState state) {
        if (index < 0 || index >= SLOTS.size() || state == null) return;
        EcoSaveSlot slot = SLOTS.get(index);
        if (slot == null || !slot.isConfigured()) return;
        state.clearAll();
        if (slot.defaultTreasury != null) {
            try { state.setDefaultTreasury(TreasuryTier.valueOf(slot.defaultTreasury)); } catch (Exception ignored) {}
        }
        slot.tributes.forEach((resName, val) -> {
            try {
                var res = com.wynntils.models.territories.type.GuildResource.valueOf(resName);
                state.getTributes().put(res, val);
            } catch (Exception ignored) {}
        });
        for (EcoSaveSlot.SavedTerritory st : slot.territories) {
            EconomyTerritoryState terr = state.addTerritory(st.name);
            if (st.treasury != null) try { terr.setTreasury(TreasuryTier.valueOf(st.treasury)); } catch (Exception ignored) {}
            terr.setHq(st.isHq);
            st.upgrades.forEach((uName, lvl) -> {
                try { terr.setUpgradeLevel(SandboxUpgrade.valueOf(uName), lvl); } catch (Exception ignored) {}
            });
        }
        state.invalidateDistanceCache();
    }

    public static void exportSlotToFile(int index, Path file) throws Exception {
        EcoSaveSlot slot = getSlot(index);
        if (slot == null || !slot.isConfigured()) throw new Exception("Slot empty");
        Files.createDirectories(file.getParent());
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(slot, w);
        }
    }

    public static EcoSaveSlot importSlotFromFile(Path file) throws Exception {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, EcoSaveSlot.class);
        }
    }

    public static Path getFilePath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("eco_saves.json");
    }

    public static void save() {
        try {
            Path file = getFilePath();
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(SLOTS, w);
            }
        } catch (Exception e) {
            PotsmodClient.LOGGER.error("Failed to save eco saves", e);
        }
    }

    public static void load() {
        try {
            Path file = getFilePath();
            if (!Files.exists(file)) return;
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<List<EcoSaveSlot>>() {}.getType();
                List<EcoSaveSlot> loaded = GSON.fromJson(r, type);
                if (loaded != null) {
                    for (int i = 0; i < SLOT_COUNT && i < loaded.size(); i++) SLOTS.set(i, loaded.get(i));
                }
            }
        } catch (Exception e) {
            PotsmodClient.LOGGER.error("Failed to load eco saves", e);
        }
    }
}
