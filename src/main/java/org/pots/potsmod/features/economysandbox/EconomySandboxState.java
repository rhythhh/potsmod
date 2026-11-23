/*
 * Copyright © RMod 2026.
 * This file is released under LGPLv3. See LICENSE for full license details.
 */
package org.pots.potsmod.features.economysandbox;

import com.wynntils.models.territories.type.GuildResource;
import lombok.Getter;
import lombok.Setter;
import org.pots.potsmod.features.economysandbox.EconomySandboxData.SandboxUpgrade;
import org.pots.potsmod.features.economysandbox.EconomySandboxData.TreasuryTier;
import org.pots.potsmod.managers.TerritoryBaseManager;
import org.pots.potsmod.models.records.TerritoryBase;

import java.util.*;

@Getter
@Setter
public class EconomySandboxState {
    private final Map<String, EconomyTerritoryState> addedTerritories = new LinkedHashMap<>();
    private final Set<String> selectedTerritories = new LinkedHashSet<>();
    private final Map<GuildResource, Integer> tributes = new EnumMap<>(GuildResource.class);
    private TreasuryTier defaultTreasury = TreasuryTier.VERY_LOW;
    private String hqTerritory = null;

    // Cache of distance from HQ (in hops)
    private transient Map<String, Integer> hqDistanceCache = new HashMap<>();
    private transient boolean distanceCacheDirty = true;

    public EconomySandboxState() {
        for (GuildResource resource : GuildResource.values()) {
            tributes.put(resource, 0);
        }
    }

    public void clearAll() {
        addedTerritories.clear();
        selectedTerritories.clear();
        hqTerritory = null;
        defaultTreasury = TreasuryTier.VERY_LOW;
        for (GuildResource resource : GuildResource.values()) {
            tributes.put(resource, 0);
        }
        invalidateDistanceCache();
    }

    public boolean isAdded(String territoryName) {
        return addedTerritories.containsKey(territoryName);
    }

    public EconomyTerritoryState getTerritory(String territoryName) {
        return addedTerritories.get(territoryName);
    }

    public Map<String, EconomyTerritoryState> getTerritories() {
        return addedTerritories;
    }

    public EconomyTerritoryState addTerritory(String territoryName) {
        if (!addedTerritories.containsKey(territoryName)) {
            EconomyTerritoryState state = new EconomyTerritoryState(territoryName);
            addedTerritories.put(territoryName, state);
            invalidateDistanceCache();
            return state;
        }
        return addedTerritories.get(territoryName);
    }

    public void removeTerritory(String territoryName) {
        EconomyTerritoryState removed = addedTerritories.remove(territoryName);
        if (removed != null) {
            selectedTerritories.remove(territoryName);
            if (territoryName.equals(hqTerritory)) {
                hqTerritory = null;
            }
            invalidateDistanceCache();
        }
    }

    public void toggleHq(String territoryName) {
        if (territoryName == null || !addedTerritories.containsKey(territoryName)) {
            return;
        }
        if (territoryName.equals(hqTerritory)) {
            // Unset HQ
            EconomyTerritoryState current = addedTerritories.get(territoryName);
            if (current != null) {
                current.setHq(false);
            }
            hqTerritory = null;
        } else {
            // Unset old HQ
            if (hqTerritory != null && addedTerritories.containsKey(hqTerritory)) {
                addedTerritories.get(hqTerritory).setHq(false);
            }
            hqTerritory = territoryName;
            addedTerritories.get(territoryName).setHq(true);
        }
        invalidateDistanceCache();
    }

    public void setHqTerritory(String territoryName) {
        if (hqTerritory != null && addedTerritories.containsKey(hqTerritory)) {
            addedTerritories.get(hqTerritory).setHq(false);
        }
        this.hqTerritory = territoryName;
        if (territoryName != null && addedTerritories.containsKey(territoryName)) {
            addedTerritories.get(territoryName).setHq(true);
        }
        invalidateDistanceCache();
    }

    public void invalidateDistanceCache() {
        distanceCacheDirty = true;
    }

    public int getHqDistance(String territoryName) {
        if (distanceCacheDirty) {
            recalculateHqDistances();
        }
        return hqDistanceCache.getOrDefault(territoryName, -1);
    }

    private void recalculateHqDistances() {
        hqDistanceCache.clear();
        distanceCacheDirty = false;

        if (hqTerritory == null || !addedTerritories.containsKey(hqTerritory)) {
            return;
        }

        Queue<String> queue = new ArrayDeque<>();
        queue.add(hqTerritory);
        hqDistanceCache.put(hqTerritory, 0);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDist = hqDistanceCache.get(current);

            TerritoryBase base = TerritoryBaseManager.getTerritory(current);
            if (base == null || base.connections() == null) {
                continue;
            }

            for (String neighbor : base.connections()) {
                // BFS only through added territories in sandbox
                if (addedTerritories.containsKey(neighbor) && !hqDistanceCache.containsKey(neighbor)) {
                    hqDistanceCache.put(neighbor, currentDist + 1);
                    queue.add(neighbor);
                }
            }
        }
    }

    public double getTreasuryBonus(String territoryName) {
        EconomyTerritoryState terr = addedTerritories.get(territoryName);
        if (terr == null) {
            return 0.0;
        }
        TreasuryTier tier = terr.getTreasury() != null ? terr.getTreasury() : defaultTreasury;
        int hops = getHqDistance(territoryName);
        double distMod = EconomySandboxData.getTreasuryDistanceModifier(hops);
        return tier.getRate() * distMod;
    }

    // --- Production Calculation ---
    public int getTerritoryProduction(String territoryName, GuildResource resource) {
        EconomyTerritoryState terr = addedTerritories.get(territoryName);
        if (terr == null) {
            return 0;
        }
        TerritoryBase base = TerritoryBaseManager.getTerritory(territoryName);
        if (base == null) {
            return 0;
        }

        int baseProd = base.getProduction(resource);
        if (baseProd <= 0) {
            return 0;
        }

        double treasuryBonus = getTreasuryBonus(territoryName);

        if (resource == GuildResource.EMERALDS) {
            double effEm = EconomySandboxData.EMERALD_MODIFIERS.get(
                    terr.getUpgradeLevel(SandboxUpgrade.EFFICIENT_EMERALDS));
            double rateEm = EconomySandboxData.RATE_MODIFIERS.get(
                    terr.getUpgradeLevel(SandboxUpgrade.EMERALD_RATE));
            return (int) Math.round(baseProd * (1.0 + treasuryBonus) * effEm * rateEm);
        } else {
            double effRes = EconomySandboxData.RESOURCE_MODIFIERS.get(
                    terr.getUpgradeLevel(SandboxUpgrade.EFFICIENT_RESOURCES));
            double rateRes = EconomySandboxData.RATE_MODIFIERS.get(
                    terr.getUpgradeLevel(SandboxUpgrade.RESOURCE_RATE));
            return (int) Math.round(baseProd * (1.0 + treasuryBonus) * effRes * rateRes);
        }
    }

    // --- Storage Cap Calculation ---
    public int getTerritoryStorageCap(String territoryName, GuildResource resource) {
        EconomyTerritoryState terr = addedTerritories.get(territoryName);
        if (terr == null) {
            return 0;
        }
        boolean isHq = terr.isHq();
        if (resource == GuildResource.EMERALDS) {
            return EconomySandboxData.getEmeraldStorageCap(
                    terr.getUpgradeLevel(SandboxUpgrade.EMERALD_STORAGE), isHq);
        } else {
            return EconomySandboxData.getResourceStorageCap(
                    terr.getUpgradeLevel(SandboxUpgrade.RESOURCE_STORAGE), isHq);
        }
    }

    // --- Cost / Usage Calculation ---
    public long getTerritoryUsage(String territoryName, GuildResource resource) {
        EconomyTerritoryState terr = addedTerritories.get(territoryName);
        if (terr == null) {
            return 0;
        }

        long sum = 0;
        for (SandboxUpgrade upgrade : SandboxUpgrade.values()) {
            if (upgrade.getCostResource() == resource) {
                sum += upgrade.getCost(terr.getUpgradeLevel(upgrade));
            }
        }
        return sum;
    }

    // --- Overall Sums across Sandbox ---
    public int getOverallProduction(GuildResource resource) {
        int sum = 0;
        for (String terrName : addedTerritories.keySet()) {
            sum += getTerritoryProduction(terrName, resource);
        }
        int tribute = tributes.getOrDefault(resource, 0);
        if (tribute > 0) {
            sum += tribute;
        }
        return sum;
    }

    public int getOverallStorageMax(GuildResource resource) {
        int sum = 0;
        for (String terrName : addedTerritories.keySet()) {
            sum += getTerritoryStorageCap(terrName, resource);
        }
        return sum;
    }

    public long getOverallUsage(GuildResource resource) {
        long sum = 0;
        for (String terrName : addedTerritories.keySet()) {
            sum += getTerritoryUsage(terrName, resource);
        }
        int tribute = tributes.getOrDefault(resource, 0);
        if (tribute < 0) {
            sum += -tribute; // Tribute given out counts as usage / cost
        }
        return sum;
    }

    public long getOverallDelta(GuildResource resource) {
        return (long) getOverallProduction(resource) - getOverallUsage(resource);
    }

    // --- Batch Operations ---
    public void selectAll() {
        selectedTerritories.clear();
        selectedTerritories.addAll(addedTerritories.keySet());
    }

    public void selectNone() {
        selectedTerritories.clear();
    }

    public void removeSelected() {
        List<String> toRemove = new ArrayList<>(selectedTerritories);
        for (String name : toRemove) {
            removeTerritory(name);
        }
        selectedTerritories.clear();
    }

    public void resetSelected() {
        for (String name : selectedTerritories) {
            EconomyTerritoryState terr = addedTerritories.get(name);
            if (terr != null) {
                terr.resetUpgrades();
                terr.setTreasury(null);
                if (terr.isHq()) {
                    terr.setHq(false);
                    hqTerritory = null;
                }
            }
        }
        invalidateDistanceCache();
    }

    public void applyBestStoragesForSelected() {
        for (String name : selectedTerritories) {
            EconomyTerritoryState terr = addedTerritories.get(name);
            if (terr == null) {
                continue;
            }
            boolean isHq = terr.isHq();

            // Emerald Storage: exact fa-rog formula minEms = (costs.emeralds + production.emeralds) / 60
            long minEms = Math.round((getTerritoryUsage(name, GuildResource.EMERALDS) + getTerritoryProduction(name, GuildResource.EMERALDS)) / 60.0);
            int bestEmLevel = 0;
            for (int lvl = 0; lvl <= SandboxUpgrade.EMERALD_STORAGE.getMaxLevel(); lvl++) {
                if (EconomySandboxData.getEmeraldStorageCap(lvl, isHq) >= minEms) {
                    bestEmLevel = lvl;
                    break;
                }
                bestEmLevel = lvl;
            }
            terr.setUpgradeLevel(SandboxUpgrade.EMERALD_STORAGE, bestEmLevel);

            // Resource Storage: exact fa-rog formula minRes = max((costs[res] + production[res]) / 60)
            long minRes = 0;
            for (GuildResource res : GuildResource.values()) {
                if (res != GuildResource.EMERALDS) {
                    long needed = Math.round((getTerritoryUsage(name, res) + getTerritoryProduction(name, res)) / 60.0);
                    minRes = Math.max(minRes, needed);
                }
            }
            int bestResLevel = 0;
            for (int lvl = 0; lvl <= SandboxUpgrade.RESOURCE_STORAGE.getMaxLevel(); lvl++) {
                if (EconomySandboxData.getResourceStorageCap(lvl, isHq) >= minRes) {
                    bestResLevel = lvl;
                    break;
                }
                bestResLevel = lvl;
            }
            terr.setUpgradeLevel(SandboxUpgrade.RESOURCE_STORAGE, bestResLevel);
        }
    }

    public int getLowestUpgradeLevelForSelected(SandboxUpgrade upgrade) {
        if (selectedTerritories.isEmpty()) {
            return 0;
        }
        int min = Integer.MAX_VALUE;
        for (String name : selectedTerritories) {
            EconomyTerritoryState state = addedTerritories.get(name);
            if (state != null) {
                min = Math.min(min, state.getUpgradeLevel(upgrade));
            }
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    public void setUpgradeLevelForSelected(SandboxUpgrade upgrade, int level) {
        for (String name : selectedTerritories) {
            EconomyTerritoryState state = addedTerritories.get(name);
            if (state != null) {
                state.setUpgradeLevel(upgrade, level);
            }
        }
    }

    public void modifyUpgradeLevelForSelected(SandboxUpgrade upgrade, int delta) {
        int baseLevel = getLowestUpgradeLevelForSelected(upgrade);
        int newLevel = Math.clamp(baseLevel + delta, 0, upgrade.getMaxLevel());
        setUpgradeLevelForSelected(upgrade, newLevel);
    }
}
