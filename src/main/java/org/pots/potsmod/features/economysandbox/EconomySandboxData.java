/*
 * Copyright © Wynntils 2024-2026.
 * Copyright © RMod 2026.
 * This file is released under LGPLv3. See LICENSE for full license details.
 *
 * Territory upgrade costs, rates, levels and caps referenced from Wynntils
 * (common/src/main/java/com/wynntils/models/territories/type/TerritoryUpgrade.java and TerritoryItem.java).
 */
package org.pots.potsmod.features.economysandbox;

import com.wynntils.models.territories.type.GuildResource;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Single centralized repository of all economy numbers, costs, multipliers, rates, and storage caps.
 * No numbers are invented; all values originate from Wynntils.
 * Anything not found in Wynntils is explicitly marked with a visible TODO comment.
 */
public final class EconomySandboxData {

    private EconomySandboxData() {}

    // =========================================================================
    // VISIBLE TODO / MISSING DATA CONSTANTS & DOCUMENTATION
    // =========================================================================
    // TODO [MISSING DATA]: When no HQ is designated or a territory is cut off, Wynntils does not
    // explicitly define if treasury rate defaults to 0.0 or uses the disconnected distance multiplier (0.40).
    // In the sandbox, when no HQ is set or a territory is unreachable from HQ, treasury bonus defaults to 0.0.
    public static final double MISSING_DATA_DISCONNECTED_TREASURY_BONUS = 0.0;

    // TODO [MISSING DATA]: Inter-territory resource routing transfer capacity and route losses are not
    // modeled in Wynntils (TerritoryManagementHolder directly sums generation without simulated bottlenecks).
    // The sandbox mirrors Wynntils by summing production directly across all added territories.
    public static final boolean ROUTE_TRANSFER_BOTTLENECK_SIMULATED = false;

    // TODO [MISSING DATA]: Real-time storage fill accumulation over time. The sandbox serves as a
    // layout & economy calculator; current storage amount displays 0 / max.
    public static final int CURRENT_STORAGE_DEFAULT = 0;

    // =========================================================================
    // TREASURY MULTIPLIERS (from Wynntils / RMod TerritoryUtils)
    // =========================================================================
    @Getter
    @RequiredArgsConstructor
    public enum TreasuryTier {
        VERY_LOW("Very Low", 0.00),
        LOW("Low", 0.10),
        MEDIUM("Medium", 0.20),
        HIGH("High", 0.25),
        VERY_HIGH("Very High", 0.30);

        private final String displayName;
        private final double rate;

        public TreasuryTier next() {
            TreasuryTier[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }

        public TreasuryTier prev() {
            TreasuryTier[] vals = values();
            return vals[(ordinal() + vals.length - 1) % vals.length];
        }
    }

    /**
     * Treasury distance multipliers based on hops from HQ (from TerritoryUtils.getTreasuryBonus).
     * 0, 1, 2 hops: 1.00
     * 3 hops: 0.85
     * 4 hops: 0.70
     * 5 hops: 0.55
     * 6+ hops: 0.40
     */
    public static double getTreasuryDistanceModifier(int hopsFromHq) {
        if (hopsFromHq < 0) {
            return 1.0;
        }
        if (hopsFromHq == Integer.MAX_VALUE) {
            // When no HQ is designated in the sandbox, apply full 1.0 modifier
            return 1.0;
        }
        return Math.min(1.0, Math.max(0.4, 1.3 - 0.15 * hopsFromHq));
    }

    // =========================================================================
    // PRODUCTION MODIFIERS (from Wynntils TerritoryItem.java)
    // =========================================================================
    public static final List<Double> RATE_MODIFIERS = List.of(1.0, 4.0 / 3.0, 2.0, 4.0);
    public static final List<Double> RESOURCE_MODIFIERS = List.of(1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0);
    public static final List<Double> EMERALD_MODIFIERS = List.of(1.0, 1.35, 2.0, 4.0);

    // =========================================================================
    // STORAGE CAPS (from Wynntils/RMod TerritoryUtils.java)
    // =========================================================================
    // Base storage: Material = 300 (HQ = 1500), Emerald = 3000 (HQ = 5000)
    public static final int[] RESOURCE_STORAGE_CAPS = { 300, 600, 1200, 2400, 4500, 10200, 24000 };
    public static final int[] HQ_RESOURCE_STORAGE_CAPS = { 1500, 3000, 6000, 12000, 22500, 51000, 120000 };

    public static final int[] EMERALD_STORAGE_CAPS = { 3000, 6000, 12000, 24000, 45000, 102000, 240000 };
    public static final int[] HQ_EMERALD_STORAGE_CAPS = { 5000, 10000, 20000, 40000, 75000, 170000, 400000 };

    public static int getResourceStorageCap(int level, boolean isHq) {
        int clamped = Math.clamp(level, 0, RESOURCE_STORAGE_CAPS.length - 1);
        return isHq ? HQ_RESOURCE_STORAGE_CAPS[clamped] : RESOURCE_STORAGE_CAPS[clamped];
    }

    public static int getEmeraldStorageCap(int level, boolean isHq) {
        int clamped = Math.clamp(level, 0, EMERALD_STORAGE_CAPS.length - 1);
        return isHq ? HQ_EMERALD_STORAGE_CAPS[clamped] : EMERALD_STORAGE_CAPS[clamped];
    }

    // =========================================================================
    // UPGRADE DEFINITIONS (All values from Wynntils TerritoryUpgrade.java)
    // =========================================================================
    public record UpgradeLevel(long cost, double bonus) {}

    public enum UpgradeCategory {
        TOWER,
        BONUS,
        GUILD,
        SEEKING,
        STORAGE,
        PRODUCTION
    }

    @Getter
    @RequiredArgsConstructor
    public enum SandboxUpgrade {
        // --- Tower ---
        DAMAGE(
                "Damage",
                UpgradeCategory.TOWER,
                GuildResource.ORE,
                Items.IRON_SWORD,
                "Increases the damage the tower does",
                "Damage: +%s%%",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(100L, 40d),
                        new UpgradeLevel(300L, 80d),
                        new UpgradeLevel(600L, 120d),
                        new UpgradeLevel(1200L, 160d),
                        new UpgradeLevel(2400L, 200d),
                        new UpgradeLevel(4800L, 240d),
                        new UpgradeLevel(8400L, 280d),
                        new UpgradeLevel(12000L, 320d),
                        new UpgradeLevel(15600L, 360d),
                        new UpgradeLevel(19200L, 400d),
                        new UpgradeLevel(22800L, 440d)
                }
        ),
        ATTACK(
                "Attack",
                UpgradeCategory.TOWER,
                GuildResource.CROPS,
                Items.RABBIT_HIDE,
                "Increases the rate the tower does an attack",
                "Attacks/s: +%s%%",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(100L, 50d),
                        new UpgradeLevel(300L, 100d),
                        new UpgradeLevel(600L, 150d),
                        new UpgradeLevel(1200L, 220d),
                        new UpgradeLevel(2400L, 300d),
                        new UpgradeLevel(4800L, 400d),
                        new UpgradeLevel(8400L, 500d),
                        new UpgradeLevel(12000L, 620d),
                        new UpgradeLevel(15600L, 660d),
                        new UpgradeLevel(19200L, 740d),
                        new UpgradeLevel(22800L, 840d)
                }
        ),
        HEALTH(
                "Health",
                UpgradeCategory.TOWER,
                GuildResource.WOOD,
                Items.FERMENTED_SPIDER_EYE,
                "Increases the health the tower has",
                "Health: +%s%%",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(100L, 50d),
                        new UpgradeLevel(300L, 100d),
                        new UpgradeLevel(600L, 150d),
                        new UpgradeLevel(1200L, 220d),
                        new UpgradeLevel(2400L, 300d),
                        new UpgradeLevel(4800L, 400d),
                        new UpgradeLevel(8400L, 520d),
                        new UpgradeLevel(12000L, 640d),
                        new UpgradeLevel(15600L, 760d),
                        new UpgradeLevel(19200L, 880d),
                        new UpgradeLevel(22800L, 1000d)
                }
        ),
        DEFENCE(
                "Defence",
                UpgradeCategory.TOWER,
                GuildResource.FISH,
                Items.SHIELD,
                "Increases the defense the tower has",
                "Defence: +%s%%",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(100L, 300d),
                        new UpgradeLevel(300L, 450d),
                        new UpgradeLevel(600L, 525d),
                        new UpgradeLevel(1200L, 600d),
                        new UpgradeLevel(2400L, 650d),
                        new UpgradeLevel(4800L, 690d),
                        new UpgradeLevel(8400L, 720d),
                        new UpgradeLevel(12000L, 740d),
                        new UpgradeLevel(15600L, 760d),
                        new UpgradeLevel(19200L, 780d),
                        new UpgradeLevel(22800L, 800d)
                }
        ),

        // --- Tower Bonuses ---
        STRONGER_MINIONS(
                "Stronger Minions",
                UpgradeCategory.BONUS,
                GuildResource.WOOD,
                Items.SKELETON_SKULL,
                "Buffs the minions that spawn when your territory is attacked",
                "Minion Dmg: +%s%%",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(200L, 150d),
                        new UpgradeLevel(400L, 200d),
                        new UpgradeLevel(800L, 250d),
                        new UpgradeLevel(1600L, 300d)
                }
        ),
        TOWER_MULTI_ATTACKS(
                "Tower Multi-Attacks",
                UpgradeCategory.BONUS,
                GuildResource.FISH,
                Items.ARROW,
                "Increases the number of players your Guild Tower can attack at once",
                "Targets: %s",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 1d),
                        new UpgradeLevel(4800L, 2d)
                }
        ),
        TOWER_AURA(
                "Aura",
                UpgradeCategory.BONUS,
                GuildResource.CROPS,
                Items.ENDER_PEARL,
                "Cast an outward-moving Aura from the Tower damaging players",
                "Cooldown: %ss",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(800L, 24d),
                        new UpgradeLevel(1600L, 18d),
                        new UpgradeLevel(3200L, 12d)
                }
        ),
        TOWER_VOLLEY(
                "Volley",
                UpgradeCategory.BONUS,
                GuildResource.ORE,
                Items.FIRE_CHARGE,
                "Cast a volley of fireballs from the Tower damaging players",
                "Cooldown: %ss",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(200L, 20d),
                        new UpgradeLevel(400L, 15d),
                        new UpgradeLevel(800L, 10d)
                }
        ),

        // --- Guild XP & Combat Buffs ---
        GATHERING_EXPERIENCE(
                "Gathering XP",
                UpgradeCategory.GUILD,
                GuildResource.WOOD,
                Items.WOODEN_PICKAXE,
                "Your guild will gain more gathering xp in this territory",
                "Gather XP: +%s%%",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(600L, 10d),
                        new UpgradeLevel(1300L, 20d),
                        new UpgradeLevel(2000L, 30d),
                        new UpgradeLevel(2700L, 40d),
                        new UpgradeLevel(3400L, 50d),
                        new UpgradeLevel(5500L, 60d),
                        new UpgradeLevel(10000L, 80d),
                        new UpgradeLevel(20000L, 100d)
                }
        ),
        MOB_EXPERIENCE(
                "Mob XP",
                UpgradeCategory.GUILD,
                GuildResource.FISH,
                Items.ROTTEN_FLESH,
                "Your guild will gain more combat xp in this territory",
                "Mob XP: +%s%%",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(600L, 10d),
                        new UpgradeLevel(1200L, 20d),
                        new UpgradeLevel(1800L, 30d),
                        new UpgradeLevel(2400L, 40d),
                        new UpgradeLevel(3000L, 50d),
                        new UpgradeLevel(5000L, 60d),
                        new UpgradeLevel(10000L, 80d),
                        new UpgradeLevel(20000L, 100d)
                }
        ),
        MOB_DAMAGE(
                "Mob Damage",
                UpgradeCategory.GUILD,
                GuildResource.CROPS,
                Items.BLAZE_POWDER,
                "Your guild will deal more damage to mobs in this territory",
                "Mob Dmg: +%s%%",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(600L, 10d),
                        new UpgradeLevel(1200L, 20d),
                        new UpgradeLevel(1800L, 40d),
                        new UpgradeLevel(2400L, 60d),
                        new UpgradeLevel(3000L, 80d),
                        new UpgradeLevel(5000L, 120d),
                        new UpgradeLevel(10000L, 160d),
                        new UpgradeLevel(20000L, 200d)
                }
        ),
        PVP_DAMAGE(
                "PvP Damage",
                UpgradeCategory.GUILD,
                GuildResource.ORE,
                Items.BOW,
                "Your guild will deal more damage to players in this territory",
                "PvP Dmg: +%s%%",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(600L, 5d),
                        new UpgradeLevel(1200L, 10d),
                        new UpgradeLevel(1800L, 15d),
                        new UpgradeLevel(2400L, 20d),
                        new UpgradeLevel(3000L, 25d),
                        new UpgradeLevel(5000L, 40d),
                        new UpgradeLevel(10000L, 65d),
                        new UpgradeLevel(20000L, 80d)
                }
        ),

        // --- Seeking ---
        XP_SEEKING(
                "XP Seeking",
                UpgradeCategory.SEEKING,
                GuildResource.EMERALDS,
                Items.BOOK,
                "Your guild will have a chance to find XP while holding this territory",
                "Guild XP: +%s/h",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(100L, 36000d),
                        new UpgradeLevel(200L, 66000d),
                        new UpgradeLevel(400L, 120000d),
                        new UpgradeLevel(800L, 228000d),
                        new UpgradeLevel(1600L, 456000d),
                        new UpgradeLevel(3200L, 900000d),
                        new UpgradeLevel(6400L, 1740000d),
                        new UpgradeLevel(9600L, 2580000d),
                        new UpgradeLevel(12800L, 3360000d)
                }
        ),
        TOME_SEEKING(
                "Tome Seeking",
                UpgradeCategory.SEEKING,
                GuildResource.FISH,
                Items.ENCHANTED_BOOK,
                "Your guild will have a chance to find tomes while holding this territory",
                "Chance: %s%%/h",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(400L, 0.15d),
                        new UpgradeLevel(3200L, 1.2d),
                        new UpgradeLevel(6400L, 2.4d)
                }
        ),
        EMERALD_SEEKING(
                "Emerald Seeking",
                UpgradeCategory.SEEKING,
                GuildResource.WOOD,
                Items.EMERALD_ORE,
                "Your guild will have a chance to find emeralds while holding this territory",
                "Chance: %s%%/h",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(200L, 0.3d),
                        new UpgradeLevel(800L, 3d),
                        new UpgradeLevel(1600L, 6d),
                        new UpgradeLevel(3200L, 12d),
                        new UpgradeLevel(6400L, 24d)
                }
        ),

        // --- Storage ---
        RESOURCE_STORAGE(
                "Larger Resource Storage",
                UpgradeCategory.STORAGE,
                GuildResource.EMERALDS,
                Items.BREAD,
                "Increases the storage limit for resources in this territory",
                "Cap: +%s%%",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(400L, 100d),
                        new UpgradeLevel(800L, 300d),
                        new UpgradeLevel(2000L, 700d),
                        new UpgradeLevel(5000L, 1400d),
                        new UpgradeLevel(16000L, 3300d),
                        new UpgradeLevel(48000L, 7900d)
                }
        ),
        EMERALD_STORAGE(
                "Larger Emerald Storage",
                UpgradeCategory.STORAGE,
                GuildResource.WOOD,
                Items.EMERALD_BLOCK,
                "Increases the storage limit for emeralds in this territory",
                "Cap: +%s%%",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(200L, 100d),
                        new UpgradeLevel(400L, 300d),
                        new UpgradeLevel(1000L, 700d),
                        new UpgradeLevel(2500L, 1400d),
                        new UpgradeLevel(8000L, 3300d),
                        new UpgradeLevel(24000L, 7900d)
                }
        ),

        // --- Production & Efficiency ---
        EFFICIENT_RESOURCES(
                "Efficient Resources",
                UpgradeCategory.PRODUCTION,
                GuildResource.EMERALDS,
                Items.GOLDEN_PICKAXE,
                "Increases the amount of resources this territory will produce",
                "Res Bonus: +%s%%",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(6000L, 50d),
                        new UpgradeLevel(12000L, 100d),
                        new UpgradeLevel(24000L, 150d),
                        new UpgradeLevel(48000L, 200d),
                        new UpgradeLevel(96000L, 250d),
                        new UpgradeLevel(192000L, 300d)
                }
        ),
        RESOURCE_RATE(
                "Resource Rate",
                UpgradeCategory.PRODUCTION,
                GuildResource.EMERALDS,
                Items.MUSHROOM_STEM,
                "Decreases the time needed to produce resources on this territory",
                "Interval: %ss",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 4d),
                        new UpgradeLevel(6000L, 3d),
                        new UpgradeLevel(18000L, 2d),
                        new UpgradeLevel(32000L, 1d)
                }
        ),
        EFFICIENT_EMERALDS(
                "Efficient Emeralds",
                UpgradeCategory.PRODUCTION,
                GuildResource.ORE,
                Items.EMERALD,
                "Increases the amount of emeralds this territory will produce",
                "Em Bonus: +%s%%",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 0d),
                        new UpgradeLevel(2000L, 35d),
                        new UpgradeLevel(8000L, 100d),
                        new UpgradeLevel(32000L, 300d)
                }
        ),
        EMERALD_RATE(
                "Emerald Rate",
                UpgradeCategory.PRODUCTION,
                GuildResource.CROPS,
                Items.EXPERIENCE_BOTTLE,
                "Decreases the time needed to produce emeralds on this territory",
                "Interval: %ss",
                new UpgradeLevel[] {
                        new UpgradeLevel(0L, 4d),
                        new UpgradeLevel(2000L, 3d),
                        new UpgradeLevel(8000L, 2d),
                        new UpgradeLevel(32000L, 1d)
                }
        );

        private final String displayName;
        private final UpgradeCategory category;
        private final GuildResource costResource;
        private final Item icon;
        private final String description;
        private final String bonusFormat;
        private final UpgradeLevel[] levels;

        public int getMaxLevel() {
            return levels.length - 1;
        }

        public long getCost(int level) {
            int clamped = Math.clamp(level, 0, getMaxLevel());
            return levels[clamped].cost();
        }

        public double getBonus(int level) {
            int clamped = Math.clamp(level, 0, getMaxLevel());
            return levels[clamped].bonus();
        }

        public String formatBonus(int level) {
            double bonus = getBonus(level);
            if (bonus == (long) bonus) {
                return bonusFormat.formatted(String.valueOf((long) bonus));
            }
            return bonusFormat.formatted(String.format(java.util.Locale.ROOT, "%.2f", bonus));
        }
    }
}
