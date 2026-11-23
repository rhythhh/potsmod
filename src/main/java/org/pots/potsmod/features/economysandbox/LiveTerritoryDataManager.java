/*
 * Copyright © RMod 2026.
 * This file is released under LGPLv3. See LICENSE for full license details.
 */
package org.pots.potsmod.features.economysandbox;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.wynntils.core.components.Models;
import com.wynntils.models.items.items.gui.TerritoryItem;
import com.wynntils.models.territories.type.GuildResourceValues;
import com.wynntils.models.territories.type.TerritoryUpgrade;
import com.wynntils.services.map.pois.TerritoryPoi;
import net.minecraft.client.Minecraft;
import org.pots.potsmod.PotsmodClient;
import org.pots.potsmod.features.economysandbox.EconomySandboxData.SandboxUpgrade;
import org.pots.potsmod.features.economysandbox.EconomySandboxData.TreasuryTier;
import org.pots.potsmod.models.api.TerritoryResponse;
import org.pots.potsmod.util.TerritoryUtils;
import org.pots.potsmod.util.WynncraftAPI;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class LiveTerritoryDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class CachedTerritory {
        public String name;
        public boolean isHq;
        public float treasuryBonus;
        public String treasury;
        public Map<String, Integer> upgrades = new HashMap<>();

        public CachedTerritory() {}

        public CachedTerritory(String name, boolean isHq, float treasuryBonus, String treasury, Map<String, Integer> upgrades) {
            this.name = name;
            this.isHq = isHq;
            this.treasuryBonus = treasuryBonus;
            this.treasury = treasury;
            if (upgrades != null) {
                this.upgrades.putAll(upgrades);
            }
        }
    }

    private static final Map<String, CachedTerritory> CACHE = new LinkedHashMap<>();
    private static final Map<String, TerritoryResponse> API_CACHE = new HashMap<>();

    static {
        loadFromDisk();
        syncWithWynncraftAPI();
    }

    public static void syncWithWynncraftAPI() {
        try {
            WynncraftAPI.territoryResponseAsync().thenAccept(map -> {
                if (map == null || map.isEmpty()) return;
                synchronized (LiveTerritoryDataManager.class) {
                    API_CACHE.putAll(map);
                    boolean changed = false;
                    for (Map.Entry<String, TerritoryResponse> entry : map.entrySet()) {
                        String name = entry.getKey();
                        TerritoryResponse resp = entry.getValue();
                        if (resp != null && resp.treasury() != null) {
                            CachedTerritory cached = CACHE.get(name);
                            if (cached != null) {
                                cached.treasury = resp.treasury();
                                changed = true;
                            }
                        }
                    }
                    if (changed) {
                        saveToDisk();
                    }
                }
            }).exceptionally(e -> null);
        } catch (Throwable ignored) {}
    }

    public static synchronized void updateFromWynntils(Collection<TerritoryItem> items) {
        if (items == null || items.isEmpty()) return;

        String knownHq = null;
        try {
            String guildName = Models.Guild.getGuildName();
            if (guildName != null && !guildName.isEmpty()) {
                Optional<String> hqOpt = TerritoryUtils.getHQ(guildName);
                if (hqOpt.isPresent()) {
                    knownHq = hqOpt.get();
                }
            }
        } catch (Throwable ignored) {}

        for (TerritoryItem item : items) {
            if (item == null || item.getName() == null) continue;
            String name = item.getName();
            boolean isHq = item.isHeadquarters();

            if (!isHq) {
                if (knownHq != null && knownHq.equalsIgnoreCase(name)) {
                    isHq = true;
                } else {
                    try {
                        TerritoryPoi poi = Models.Territory.getTerritoryPoiFromAdvancement(name);
                        if (poi != null && poi.getTerritoryInfo() != null && poi.getTerritoryInfo().isHeadquarters()) {
                            isHq = true;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            // 1. Direct from Wynncraft API cache (100% accurate, no distance decay)
            TreasuryTier tier = null;
            TerritoryResponse apiResp = API_CACHE.get(name);
            if (apiResp != null && apiResp.treasury() != null) {
                try {
                    tier = TreasuryTier.valueOf(apiResp.treasury());
                } catch (Exception ignored) {}
            }

            // 2. Exact Treasury Tier from Wynncraft advancement data if not in API cache
            if (tier == null) {
                try {
                    TerritoryPoi poi = Models.Territory.getTerritoryPoiFromAdvancement(name);
                    if (poi != null && poi.getTerritoryInfo() != null && poi.getTerritoryInfo().getTreasury() != null) {
                        GuildResourceValues tr = poi.getTerritoryInfo().getTreasury();
                        tier = switch (tr) {
                            case VERY_HIGH -> TreasuryTier.VERY_HIGH;
                            case HIGH -> TreasuryTier.HIGH;
                            case MEDIUM -> TreasuryTier.MEDIUM;
                            case LOW -> TreasuryTier.LOW;
                            case VERY_LOW, NONE -> TreasuryTier.VERY_LOW;
                        };
                    }
                } catch (Throwable ignored) {}
            }

            // 3. Fallback only if both API and Poi had no treasury
            if (tier == null) {
                float b = item.getTreasuryBonus();
                if (b >= 0) {
                    int hops = -1;
                    try {
                        hops = TerritoryUtils.getHQDistance(name);
                    } catch (Throwable ignored) {}
                    double distMod = 1.0;
                    if (hops >= 0) {
                        distMod = switch (hops) {
                            case 0, 1, 2 -> 1.0;
                            case 3 -> 0.85;
                            case 4 -> 0.70;
                            case 5 -> 0.55;
                            default -> 0.40;
                        };
                    }
                    float pct = (b > 1.0f ? b : b * 100.0f) / (float) distMod;
                    if (pct >= 27.5f) {
                        tier = TreasuryTier.VERY_HIGH;
                    } else if (pct >= 22.5f) {
                        tier = TreasuryTier.HIGH;
                    } else if (pct >= 15.0f) {
                        tier = TreasuryTier.MEDIUM;
                    } else if (pct >= 5.0f) {
                        tier = TreasuryTier.LOW;
                    } else {
                        tier = TreasuryTier.VERY_LOW;
                    }
                }
            }

            Map<String, Integer> upgMap = new HashMap<>();
            if (item.getUpgrades() != null) {
                for (Map.Entry<TerritoryUpgrade, Integer> e : item.getUpgrades().entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        upgMap.put(e.getKey().name(), e.getValue());
                    }
                }
            }
            CACHE.put(name, new CachedTerritory(
                    name,
                    isHq,
                    item.getTreasuryBonus(),
                    tier != null ? tier.name() : null,
                    upgMap
            ));
        }
        saveToDisk();
    }

    public static synchronized int loadIntoSandbox(EconomySandboxState state) {
        // Clear all current territories in the sandbox first so lost territories are removed!
        for (String terrName : new ArrayList<>(state.getTerritories().keySet())) {
            state.removeTerritory(terrName);
        }

        // Fetch user's guild name
        String myGuild = null;
        try {
            myGuild = Models.Guild.getGuildName();
        } catch (Throwable ignored) {}

        // Fallback to checking cached territories if guild name not set in client model
        if ((myGuild == null || myGuild.isEmpty()) && !CACHE.isEmpty()) {
            String firstCached = CACHE.keySet().iterator().next();
            TerritoryResponse tr = API_CACHE.get(firstCached);
            if (tr != null && tr.guild() != null && tr.guild().name() != null) {
                myGuild = tr.guild().name();
            }
        }

        // Find all territories currently owned by this guild from Wynncraft's API
        Set<String> apiOwned = new HashSet<>();
        String apiHq = null;
        if (myGuild != null && !myGuild.isEmpty()) {
            for (Map.Entry<String, TerritoryResponse> entry : API_CACHE.entrySet()) {
                TerritoryResponse tr = entry.getValue();
                if (tr != null && tr.guild() != null) {
                    boolean matches = myGuild.equalsIgnoreCase(tr.guild().name())
                            || myGuild.equalsIgnoreCase(tr.guild().prefix());
                    if (matches) {
                        apiOwned.add(entry.getKey());
                        if (tr.hq()) {
                            apiHq = entry.getKey();
                        } else if (apiHq == null && tr.guild().hq() != null && tr.guild().hq().equalsIgnoreCase(entry.getKey())) {
                            apiHq = entry.getKey();
                        }
                    }
                }
            }
        }

        // If API had owned territories, use that as the exact set; otherwise fallback to CACHE.keySet()
        Set<String> targetNames = !apiOwned.isEmpty() ? apiOwned : new HashSet<>(CACHE.keySet());
        if (targetNames.isEmpty()) {
            loadFromDisk();
            targetNames = new HashSet<>(CACHE.keySet());
        }
        if (targetNames.isEmpty()) {
            return 0;
        }

        int count = 0;
        String foundHq = apiHq;
        Map<TreasuryTier, Integer> treasuryCount = new EnumMap<>(TreasuryTier.class);

        for (String name : targetNames) {
            EconomyTerritoryState terr = state.addTerritory(name);
            CachedTerritory cached = CACHE.get(name);

            boolean isHq = (foundHq != null && foundHq.equalsIgnoreCase(name))
                    || (cached != null && cached.isHq);

            if (!isHq) {
                TerritoryResponse tr = API_CACHE.get(name);
                if (tr != null && tr.hq()) {
                    isHq = true;
                }
            }

            terr.setHq(isHq);
            if (isHq && foundHq == null) {
                foundHq = name;
            }

            // Treasury directly from Wynncraft API
            TreasuryTier tier = null;
            TerritoryResponse tr = API_CACHE.get(name);
            if (tr != null && tr.treasury() != null) {
                try {
                    tier = TreasuryTier.valueOf(tr.treasury());
                } catch (Exception ignored) {}
            }

            if (tier == null && cached != null && cached.treasury != null) {
                try {
                    tier = TreasuryTier.valueOf(cached.treasury);
                } catch (Exception ignored) {}
            }

            if (tier == null) {
                try {
                    TerritoryPoi poi = Models.Territory.getTerritoryPoiFromAdvancement(name);
                    if (poi != null && poi.getTerritoryInfo() != null && poi.getTerritoryInfo().getTreasury() != null) {
                        GuildResourceValues gv = poi.getTerritoryInfo().getTreasury();
                        tier = switch (gv) {
                            case VERY_HIGH -> TreasuryTier.VERY_HIGH;
                            case HIGH -> TreasuryTier.HIGH;
                            case MEDIUM -> TreasuryTier.MEDIUM;
                            case LOW -> TreasuryTier.LOW;
                            case VERY_LOW, NONE -> TreasuryTier.VERY_LOW;
                        };
                    }
                } catch (Throwable ignored) {}
            }

            if (tier == null) {
                tier = TreasuryTier.MEDIUM; // Safe default
            }

            terr.setTreasury(tier);
            treasuryCount.put(tier, treasuryCount.getOrDefault(tier, 0) + 1);

            // Upgrades from cache if available
            if (cached != null && cached.upgrades != null) {
                for (Map.Entry<String, Integer> upg : cached.upgrades.entrySet()) {
                    SandboxUpgrade sbUpg = mapToSandboxUpgrade(upg.getKey());
                    if (sbUpg != null) {
                        terr.setUpgradeLevel(sbUpg, upg.getValue());
                    }
                }
            }
            count++;
        }

        if (foundHq != null) {
            state.setHqTerritory(foundHq);
        }

        TreasuryTier mostCommon = null;
        int maxCount = -1;
        for (Map.Entry<TreasuryTier, Integer> e : treasuryCount.entrySet()) {
            if (e.getValue() > maxCount) {
                maxCount = e.getValue();
                mostCommon = e.getKey();
            }
        }
        if (mostCommon != null) {
            state.setDefaultTreasury(mostCommon);
        }

        state.invalidateDistanceCache();
        return count;
    }

    public static SandboxUpgrade mapToSandboxUpgrade(String upgradeName) {
        if (upgradeName == null) return null;
        return switch (upgradeName) {
            case "DAMAGE" -> SandboxUpgrade.DAMAGE;
            case "ATTACK" -> SandboxUpgrade.ATTACK;
            case "HEALTH" -> SandboxUpgrade.HEALTH;
            case "DEFENCE" -> SandboxUpgrade.DEFENCE;
            case "EMERALD_STORAGE" -> SandboxUpgrade.EMERALD_STORAGE;
            case "EMERALD_RATE" -> SandboxUpgrade.EMERALD_RATE;
            case "RESOURCE_STORAGE" -> SandboxUpgrade.RESOURCE_STORAGE;
            case "RESOURCE_RATE" -> SandboxUpgrade.RESOURCE_RATE;
            case "TOWER_AURA" -> SandboxUpgrade.TOWER_AURA;
            case "TOWER_VOLLEY" -> SandboxUpgrade.TOWER_VOLLEY;
            case "STRONGER_MINIONS" -> SandboxUpgrade.STRONGER_MINIONS;
            case "EFFICIENT_EMERALDS" -> SandboxUpgrade.EFFICIENT_EMERALDS;
            case "EFFICIENT_RESOURCES" -> SandboxUpgrade.EFFICIENT_RESOURCES;
            case "TOWER_MULTI_ATTACKS" -> SandboxUpgrade.TOWER_MULTI_ATTACKS;
            default -> null;
        };
    }

    public static synchronized CachedTerritory getCached(String name) {
        return CACHE.get(name);
    }

    public static synchronized void saveToDisk() {
        try {
            Path path = getSavePath();
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(CACHE, writer);
            }
        } catch (Exception e) {
            PotsmodClient.LOGGER.error("Failed to save live territories cache", e);
        }
    }

    public static synchronized void loadFromDisk() {
        try {
            Path path = getSavePath();
            if (!Files.exists(path)) return;
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, CachedTerritory>>() {}.getType();
                Map<String, CachedTerritory> map = GSON.fromJson(reader, type);
                if (map != null) {
                    CACHE.clear();
                    CACHE.putAll(map);
                }
            }
        } catch (Exception e) {
            PotsmodClient.LOGGER.error("Failed to load live territories cache", e);
        }
    }

    private static Path getSavePath() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath().resolve("config").resolve("rmod_live_territories.json");
    }
}
