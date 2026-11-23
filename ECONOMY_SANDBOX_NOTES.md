# Economy Sandbox Reference Notes & Source Documentation

Documented from:
1. `https://github.com/fa-rog/economy` (`main.js`, `territory.js`, `map.js`, `upgrades.js`, `tooltips.js`)
2. `https://github.com/Wynntils/Wynntils` (`TerritoryManagementScreen.java`, `TerritoryManagementHolder.java`, `TerritoryItem.java`, `TerritoryUpgrade.java`, `GuildResourceValues.java`, `ManageTerritoryPoi.java`, `GuildOverallProductionWidget.java`, `TerritoryAnnotator.java`, `Texture.java`)

---

## Part A: fa-rog Economy Reference

### 1. Upgrade Tables, Costs, and Effects
All 21 upgrades with levels, upkeep cost per hour, and numerical effects:
- **damage** (Resource: Ore, Levels 0-11):
  - Costs: `[0, 100, 300, 600, 1200, 2400, 4800, 8400, 12000, 15600, 19200, 22800]`
  - Multipliers: `[1, 1.4, 1.8, 2.2, 2.6, 3, 3.4, 3.8, 4.2, 4.6, 5, 5.4]` (+40% per level up to +440%)
- **attack** (Resource: Crops, Levels 0-11):
  - Costs: `[0, 100, 300, 600, 1200, 2400, 4800, 8400, 12000, 15600, 19200, 22800]`
  - Multipliers: `[1, 1.5, 2, 2.5, 3.2, 4, 5, 6, 7.2, 7.6, 8.4, 9.4]` (+50% to +840%)
- **health** (Resource: Wood, Levels 0-11):
  - Costs: `[0, 100, 300, 600, 1200, 2400, 4800, 8400, 12000, 15600, 19200, 22800]`
  - Multipliers: `[1, 1.5, 2, 2.5, 3.2, 4, 5, 6.2, 7.4, 8.6, 9.8, 11]` (+50% to +1000%)
- **defence** (Resource: Fish, Levels 0-11):
  - Costs: `[0, 100, 300, 600, 1200, 2400, 4800, 8400, 12000, 15600, 19200, 22800]`
  - Multipliers: `[1, 4, 5.5, 6.25, 7, 7.5, 7.9, 8.2, 8.4, 8.6, 8.8, 9]` (+300% to +800%)
- **minions** ("Stronger Minions", Resource: Wood, Levels 0-4):
  - Costs: `[0, 200, 400, 800, 1600]`
  - Multipliers: `[1, 2.5, 3, 3.5, 4]` (+150% to +300%)
- **multi** ("Tower Multi-Attacks", Resource: Fish, Levels 0-1):
  - Costs: `[0, 4800]`
  - Targets: `[1, 2]`
- **aura** ("Tower Aura", Resource: Crops, Levels 0-3):
  - Costs: `[0, 800, 1600, 3200]`
  - Interval seconds: `[0, 24, 18, 12]`
- **volley** ("Tower Volley", Resource: Ore, Levels 0-3):
  - Costs: `[0, 200, 400, 800]`
  - Interval seconds: `[0, 20, 15, 10]`
- **gatheringXp** ("Gathering Experience", Resource: Wood, Levels 0-8):
  - Costs: `[0, 600, 1300, 2000, 2700, 3400, 5500, 10000, 20000]`
  - Multipliers: `[1, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.8, 2.0]` (+10% to +100%)
- **mobXp** ("Mob Experience", Resource: Fish, Levels 0-8):
  - Costs: `[0, 600, 1200, 1800, 2400, 3000, 5000, 10000, 20000]`
  - Multipliers: `[1, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.8, 2.0]` (+10% to +100%)
- **mobDamage** ("Mob Damage", Resource: Crops, Levels 0-8):
  - Costs: `[0, 600, 1200, 1800, 2400, 3000, 5000, 10000, 20000]`
  - Multipliers: `[1, 1.1, 1.2, 1.4, 1.6, 1.8, 2.2, 2.6, 3.0]` (+10% to +200%)
- **pvpDamage** ("PvP Damage", Resource: Ore, Levels 0-8):
  - Costs: `[0, 600, 1200, 1800, 2400, 3000, 5000, 10000, 20000]`
  - Multipliers: `[1, 1.05, 1.1, 1.15, 1.2, 1.25, 1.4, 1.65, 1.8]` (+5% to +80%)
- **xpSeeking** ("XP Seeking", Resource: Emeralds, Levels 0-9):
  - Costs: `[0, 100, 200, 400, 800, 1600, 3200, 6400, 9600, 12800]`
  - Guild XP/h: `[0, 36000, 66000, 120000, 228000, 456000, 900000, 1740000, 2580000, 3360000]`
- **tomeSeeking** ("Tome Seeking", Resource: Fish, Levels 0-3):
  - Costs: `[0, 400, 3200, 6400]`
  - Drop Chance %/h: `[0, 0.15, 1.2, 2.4]`
- **emeraldSeeking** ("Emerald Seeking", Resource: Wood, Levels 0-5):
  - Costs: `[0, 200, 800, 1600, 3200, 6400]`
  - Drop Chance %/h: `[0, 0.3, 3.0, 6.0, 12.0, 24.0]`
- **resourceStorage** ("Larger Resource Storage", Resource: Emeralds, Levels 0-6):
  - Costs: `[0, 400, 800, 2000, 5000, 16000, 48000]`
  - Storage multipliers: `[1, 2, 4, 8, 15, 34, 80]`
  - Non-HQ Cap: `[300, 600, 1200, 2400, 4500, 10200, 24000]`
  - HQ Cap: `[1500, 3000, 6000, 12000, 22500, 51000, 120000]`
- **emeraldStorage** ("Larger Emerald Storage", Resource: Wood, Levels 0-6):
  - Costs: `[0, 200, 400, 1000, 2500, 8000, 24000]`
  - Storage multipliers: `[1, 2, 4, 8, 15, 34, 80]`
  - Non-HQ Cap: `[3000, 6000, 12000, 24000, 45000, 102000, 240000]`
  - HQ Cap: `[5000, 10000, 20000, 40000, 75000, 170000, 400000]`
- **efficientResources** ("Efficient Resources", Resource: Emeralds, Levels 0-6):
  - Costs: `[0, 6000, 12000, 24000, 48000, 96000, 192000]`
  - Multipliers: `[1, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0]`
- **efficientEmeralds** ("Efficient Emeralds", Resource: Ore, Levels 0-3):
  - Costs: `[0, 2000, 8000, 32000]`
  - Multipliers: `[1, 1.35, 2.0, 4.0]`
- **resourceRate** ("Resource Rate", Resource: Emeralds, Levels 0-3):
  - Costs: `[0, 6000, 18000, 32000]`
  - Seconds interval: `[4, 3, 2, 1]` (Production multiplier: `4 / interval` = `[1.0, 4/3, 2.0, 4.0]`)
- **emeraldRate** ("Emerald Rate", Resource: Crops, Levels 0-3):
  - Costs: `[0, 2000, 8000, 32000]`
  - Seconds interval: `[4, 3, 2, 1]` (Production multiplier: `4 / interval` = `[1.0, 4/3, 2.0, 4.0]`)

### 2. Formulas in fa-rog
- **Treasury Bonus**:
  `treasuryBonus = 1 + baseTreasury * Math.min(1.0, Math.max(0.4, 1.3 - 0.15 * distanceToHq))`
  - Distance 0..2: factor = 1.00
  - Distance 3: factor = 0.85
  - Distance 4: factor = 0.70
  - Distance 5: factor = 0.55
  - Distance 6+: factor = 0.40
- **Production Calculation**:
  - `emeraldFactor = efficientEmeralds.effects[lvl] * 4 / emeraldRate.effects[lvl]`
  - `production.emeralds = baseEmeralds * emeraldFactor * treasuryBonus`
  - `resourceFactor = efficientResources.effects[lvl] * 4 / resourceRate.effects[lvl]`
  - `production[res] = baseResource * resourceFactor * treasuryBonus`
- **Best Storages Calculation**:
  `minEms = Math.round((costs.emeralds + production.emeralds) / 60)`
  `minRes = Math.max(...['ore', 'wood', 'fish', 'crops'].map(res => Math.round((costs[res] + production[res]) / 60)))`
  - While `(isHq ? 5000 : 3000) * emeraldStorage.effects[lvl] < minEms`, increment `emeraldStorage`
  - While `(isHq ? 1500 : 300) * resourceStorage.effects[lvl] < minRes`, increment `resourceStorage`
- **Upgrade Editor Step**:
  - Normal click: ±1
  - Shift + click: ±3 (or larger step, e.g. 5)
  - Left click = +step, Right click = -step

---

## Part B: Wynntils Reference

### 1. Defence Level & Difficulty Calculation
In `TerritoryItem.java`:
```java
int total = damage + attack + health + defence + aura + volley;
if (aura == 0) total -= 5;
if (volley == 0) total -= 3;

if (total < -2) return GuildResourceValues.VERY_LOW;
else if (total < 11) return GuildResourceValues.LOW;
else if (total < 23) return GuildResourceValues.MEDIUM;
else if (total < 41) return GuildResourceValues.HIGH;
else return GuildResourceValues.VERY_HIGH;
```
If HQ is present, Wynntils flags `isHeadquarters = true`.

### 2. Colours in Wynntils
In `GuildResourceValues.java` / `ManageTerritoryPoi.java`:
- **VERY_LOW**: `ChatFormatting.DARK_GREEN` -> `0xFF00AA00`
- **LOW**: `ChatFormatting.GREEN` -> `0xFF55FF55`
- **MEDIUM**: `ChatFormatting.YELLOW` -> `0xFFFFFF55`
- **HIGH**: `ChatFormatting.RED` -> `0xFFFF5555`
- **VERY_HIGH**: `ChatFormatting.DARK_RED` -> `0xFFAA0000`

Box Rendering in `ManageTerritoryPoi.java`:
- **Fill**: `colors.stream().map(x -> x.withAlpha(80)).toList()` (80 / 255 alpha, or dimmer ~25-45 for unadded)
- **Border**:
  - Normal connected: Defence color (or resource multicolor border)
  - Unconnected: `CommonColors.RED` (`0xFFFF5555`) with 1.5f width
  - Selection: Bright Highlight / Checkmark texture
  - Headquarters: `Texture.GUILD_HEADQUARTERS` or Gold border (`0xFFFFD700`)

### 3. Textures
From `com.wynntils.utils.render.Texture`:
- `Texture.TERRITORY_MANAGEMENT_BACKGROUND` (`territory_management/territory_management_background.png`, 256x136)
- `Texture.TERRITORY_SIDEBAR` (`territory_management/territory_sidebar.png`, 20x136)
- `Texture.CHECKMARK_GREEN` (`icons/generic/check_green.png`, 16x16)
- `Texture.CHECKMARK_GRAY` (`icons/generic/check_gray.png`, 16x16)
- `Texture.CHECKMARK_YELLOW` (`icons/generic/check_yellow.png`, 16x16)
- `Texture.GUILD_HEADQUARTERS` (`icons/map/guild_headquarters.png`, 16x13)
- `Texture.ARROW_LEFT_ICON` (`icons/generic/arrow_left_icon.png`, 16x16)
- `Texture.DEFENSE_FILTER_ICON` (`icons/generic/defense_filter_icon.png`, 16x16)
- `Texture.HELP_ICON` (`icons/generic/help_icon.png`, 10x16)
- `Texture.TERRITORY_LOADOUT` (`territory_management/territory_loadout.png`, 16x16)
- `Texture.SCROLLBAR_BUTTON` (`ui_components/scroll_button_container.png`, 12x15)

### 4. Guild Output Widget
Sums across all territories:
- `production = sum(territory.getProduction(res)) + receivedTributes`
- `storage = sum(territory.getStorage(res))`
- `usage = sum(territory.getUsage(res)) + sentTributes`
- Delta = `production - usage` (blue if positive, red if negative)

---

## Part C: Visible Missing Data / Notes
None! All 21 upgrade costs, effects, defense thresholds, textures, and formulas match between fa-rog and Wynntils.
