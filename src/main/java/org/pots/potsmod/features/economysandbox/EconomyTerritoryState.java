/*
 * Copyright © RMod 2026.
 * This file is released under LGPLv3. See LICENSE for full license details.
 */
package org.pots.potsmod.features.economysandbox;

import lombok.Getter;
import lombok.Setter;
import org.pots.potsmod.features.economysandbox.EconomySandboxData.SandboxUpgrade;
import org.pots.potsmod.features.economysandbox.EconomySandboxData.TreasuryTier;

import java.util.EnumMap;
import java.util.Map;

@Getter
@Setter
public class EconomyTerritoryState {
    private final String name;
    private final Map<SandboxUpgrade, Integer> upgrades = new EnumMap<>(SandboxUpgrade.class);
    private TreasuryTier treasury; // null means inherit sandbox default
    private boolean hq;

    public EconomyTerritoryState(String name) {
        this.name = name;
        resetUpgrades();
    }

    public EconomyTerritoryState(EconomyTerritoryState other) {
        this.name = other.name;
        this.upgrades.putAll(other.upgrades);
        this.treasury = other.treasury;
        this.hq = other.hq;
    }

    public int getUpgradeLevel(SandboxUpgrade upgrade) {
        return upgrades.getOrDefault(upgrade, 0);
    }

    public void setUpgradeLevel(SandboxUpgrade upgrade, int level) {
        int clamped = Math.clamp(level, 0, upgrade.getMaxLevel());
        upgrades.put(upgrade, clamped);
    }

    public void modifyUpgradeLevel(SandboxUpgrade upgrade, int delta) {
        setUpgradeLevel(upgrade, getUpgradeLevel(upgrade) + delta);
    }

    public void resetUpgrades() {
        for (SandboxUpgrade upgrade : SandboxUpgrade.values()) {
            upgrades.put(upgrade, 0);
        }
    }

    public int getDefenseDifficultyTotal() {
        int total = getUpgradeLevel(SandboxUpgrade.DAMAGE)
                + getUpgradeLevel(SandboxUpgrade.ATTACK)
                + getUpgradeLevel(SandboxUpgrade.HEALTH)
                + getUpgradeLevel(SandboxUpgrade.DEFENCE)
                + getUpgradeLevel(SandboxUpgrade.TOWER_AURA)
                + getUpgradeLevel(SandboxUpgrade.TOWER_VOLLEY);
        if (getUpgradeLevel(SandboxUpgrade.TOWER_AURA) == 0) total -= 5;
        if (getUpgradeLevel(SandboxUpgrade.TOWER_VOLLEY) == 0) total -= 3;
        return total;
    }

    public int getDefenseDifficultyTier() {
        int total = getDefenseDifficultyTotal();
        if (total < -2) return 0;       // Very Low (0 upgrades = -8)
        else if (total < 11) return 1;  // Low
        else if (total < 23) return 2;  // Medium
        else if (total < 41) return 3;  // High
        else return 4;                 // Very High
    }

    public String getDefenseDifficultyName() {
        return switch (getDefenseDifficultyTier()) {
            case 0 -> "Very Low";
            case 1 -> "Low";
            case 2 -> "Medium";
            case 3 -> "High";
            default -> "Very High";
        };
    }

    public int getDefenseDifficultyColor() {
        return switch (getDefenseDifficultyTier()) {
            case 0 -> 0xFF00AA00; // VERY_LOW (Dark Green)
            case 1 -> 0xFF55FF55; // LOW (Green)
            case 2 -> 0xFFFFFF55; // MEDIUM (Yellow)
            case 3 -> 0xFFFF5555; // HIGH (Red)
            default -> 0xFFAA0000; // VERY_HIGH (Dark Red)
        };
    }
}
