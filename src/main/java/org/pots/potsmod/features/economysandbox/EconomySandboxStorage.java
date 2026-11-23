/*
 * Copyright © RMod 2026.
 * This file is released under LGPLv3. See LICENSE for full license details.
 */
package org.pots.potsmod.features.economysandbox;

import com.google.gson.*;
import com.wynntils.models.territories.type.GuildResource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.pots.potsmod.PotsmodClient;
import org.pots.potsmod.models.events.ExecuteRunnableClickEvent;
import org.pots.potsmod.features.economysandbox.EconomySandboxData.SandboxUpgrade;
import org.pots.potsmod.features.economysandbox.EconomySandboxData.TreasuryTier;
import org.pots.potsmod.managers.TerritoryBaseManager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class EconomySandboxStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_VERSION = 1;

    private EconomySandboxStorage() {}

    public static Path getConfigDir() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath().resolve("config");
    }

    public static Path getSandboxFile() {
        return getConfigDir().resolve("economy_sandbox.json");
    }

    public static Path getLoadoutsDir() {
        return getConfigDir().resolve("loadouts");
    }

    // --- Autosave / Load Current Sandbox ---
    public static void saveCurrentState(EconomySandboxState state) {
        try {
            Path file = getSandboxFile();
            Files.createDirectories(file.getParent());
            JsonObject json = serializeState(state, "autosave");
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            PotsmodClient.LOGGER.error("Failed to save economy sandbox state", e);
        }
    }

    public static EconomySandboxState loadCurrentState() {
        Path file = getSandboxFile();
        if (!Files.exists(file)) {
            // Completely empty by default
            return new EconomySandboxState();
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return deserializeState(json);
        } catch (Exception e) {
            PotsmodClient.LOGGER.error("Failed to load economy sandbox state, starting empty", e);
            return new EconomySandboxState();
        }
    }

    // --- Named Loadouts Management ---
    public static List<String> listLoadouts() {
        Path dir = getLoadoutsDir();
        if (!Files.exists(dir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        String filename = p.getFileName().toString();
                        return filename.substring(0, filename.length() - 5);
                    })
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            PotsmodClient.LOGGER.error("Failed to list economy loadouts", e);
            return Collections.emptyList();
        }
    }

    public static void saveLoadout(String name, EconomySandboxState state) throws IOException {
        String sanitized = sanitizeFileName(name);
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Invalid loadout name");
        }
        Path dir = getLoadoutsDir();
        Files.createDirectories(dir);
        Path file = dir.resolve(sanitized + ".json");
        JsonObject json = serializeState(state, name);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        }
    }

    public static EconomySandboxState loadLoadout(String name) throws IOException {
        String sanitized = sanitizeFileName(name);
        Path file = getLoadoutsDir().resolve(sanitized + ".json");
        if (!Files.exists(file)) {
            throw new IOException("Loadout not found: " + name);
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return deserializeState(json);
        }
    }

    public static boolean deleteLoadout(String name) {
        try {
            String sanitized = sanitizeFileName(name);
            Path file = getLoadoutsDir().resolve(sanitized + ".json");
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            PotsmodClient.LOGGER.error("Failed to delete loadout: " + name, e);
            return false;
        }
    }

    public static void renameLoadout(String oldName, String newName) throws IOException {
        String oldSanitized = sanitizeFileName(oldName);
        String newSanitized = sanitizeFileName(newName);
        if (newSanitized.isEmpty()) {
            throw new IllegalArgumentException("Invalid new loadout name");
        }
        Path oldFile = getLoadoutsDir().resolve(oldSanitized + ".json");
        Path newFile = getLoadoutsDir().resolve(newSanitized + ".json");
        if (!Files.exists(oldFile)) {
            throw new IOException("Original loadout does not exist: " + oldName);
        }
        if (Files.exists(newFile) && !oldFile.equals(newFile)) {
            throw new IOException("A loadout with that name already exists: " + newName);
        }
        Files.move(oldFile, newFile);
    }

    // --- Import / Export ---
    public static void exportToFile(Path targetFile, EconomySandboxState state) throws IOException {
        Files.createDirectories(targetFile.getParent());
        JsonObject json = serializeState(state, "Export");
        try (Writer writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        }
    }

    public static EconomySandboxState importFromFile(Path sourceFile) throws IOException {
        if (!Files.exists(sourceFile)) {
            throw new IOException("File does not exist: " + sourceFile);
        }
        try (Reader reader = Files.newBufferedReader(sourceFile, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return deserializeState(json);
        }
    }

    private static final byte MAGIC_BINARY = (byte) 0xEC;
    private static final byte BINARY_VERSION = 2;
    private static final java.util.regex.Pattern SHARE_PATTERN = java.util.regex.Pattern.compile("rmod:[A-Za-z0-9_-]+");

    public static Component makeShareCodeClickable(Component original) {
        if (original == null) return null;
        String raw = original.getString();
        if (!raw.contains("rmod:")) return original;

        MutableComponent root = Component.empty();
        original.visit((style, text) -> {
            var matcher = SHARE_PATTERN.matcher(text);
            int lastEnd = 0;
            while (matcher.find()) {
                if (matcher.start() > lastEnd) {
                    root.append(Component.literal(text.substring(lastEnd, matcher.start())).withStyle(style));
                }
                String code = matcher.group();
                MutableComponent tag = Component.literal("[Eco Loadout: Click to Import]")
                        .withStyle(Style.EMPTY
                                .withColor(TextColor.fromRgb(0x00FF88))
                                .withBold(true)
                                .withUnderlined(true)
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal("§eClick to instantly import this Eco Loadout!\n§7Adds directly to your Eco Loadouts menu.")))
                                .withClickEvent(ExecuteRunnableClickEvent.of(() -> {
                                    Minecraft client = Minecraft.getInstance();
                                    try {
                                        EconomySandboxState state = EconomySandboxStorage.importFromShareString(code);
                                        int emptySlot = -1;
                                        for (int i = 0; i < 27; i++) {
                                            var s = EcoSaveManager.getSlot(i);
                                            if (s != null && !s.isConfigured()) {
                                                emptySlot = i;
                                                break;
                                            }
                                        }
                                        int slotIdx = emptySlot != -1 ? emptySlot : 0;
                                        String loadoutName = "Imported " + (slotIdx + 1);
                                        EcoSaveManager.saveSlotFromState(slotIdx, loadoutName, state);
                                        EconomySandboxStorage.saveCurrentState(state);
                                        client.submit(() -> client.setScreen(new EconomySandboxScreen(slotIdx)));
                                        if (client.player != null) {
                                            client.player.displayClientMessage(
                                                    Component.literal("§a[RMod] Successfully imported Eco Loadout into Slot " + (slotIdx + 1) + "!"), false);
                                        }
                                    } catch (Exception e) {
                                        if (client.player != null) {
                                            client.player.displayClientMessage(
                                                    Component.literal("§c[RMod] Failed to import Eco Loadout: " + e.getMessage()), false);
                                        }
                                    }
                                })));
                root.append(tag);
                lastEnd = matcher.end();
            }
            if (lastEnd < text.length()) {
                root.append(Component.literal(text.substring(lastEnd)).withStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);

        return root;
    }

    public static String exportToShareString(EconomySandboxState state) {
        JsonObject json = serializeState(state, "Share");
        String jsonStr = GSON.toJson(json);

        // 1. Try uploading to cloud for ultra-short 10-character key (chat safe!)
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(3))
                    .build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.pastes.dev/post"))
                    .timeout(java.time.Duration.ofSeconds(4))
                    .header("Content-Type", "text/json")
                    .header("User-Agent", "RMod-Minecraft")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonStr))
                    .build();
            java.net.http.HttpResponse<String> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 201 || res.statusCode() == 200) {
                JsonObject respObj = JsonParser.parseString(res.body()).getAsJsonObject();
                if (respObj.has("key")) {
                    return "rmod:" + respObj.get("key").getAsString();
                }
            }
        } catch (Exception e) {
            PotsmodClient.LOGGER.warn("Cloud share upload failed, falling back to local string", e);
        }

        // 2. Binary fallback
        try {
            java.util.List<String> sortedTerrs = new java.util.ArrayList<>(TerritoryBaseManager.getTerritories().keySet());
            java.util.Collections.sort(sortedTerrs);
            java.util.Map<String, Integer> nameToIdx = new java.util.HashMap<>();
            for (int i = 0; i < sortedTerrs.size(); i++) {
                nameToIdx.put(sortedTerrs.get(i), i);
            }

            java.io.ByteArrayOutputStream rawBaos = new java.io.ByteArrayOutputStream();
            rawBaos.write(MAGIC_BINARY);
            rawBaos.write(BINARY_VERSION);
            rawBaos.write(state.getDefaultTreasury().ordinal());

            // Tributes: Ore, Wood, Fish, Crops
            for (GuildResource res : GuildResource.values()) {
                int trib = state.getTributes().getOrDefault(res, 0);
                rawBaos.write((trib >> 8) & 0xFF);
                rawBaos.write(trib & 0xFF);
            }

            var terrs = state.getAddedTerritories().values();
            rawBaos.write((terrs.size() >> 8) & 0xFF);
            rawBaos.write(terrs.size() & 0xFF);

            for (EconomyTerritoryState terr : terrs) {
                int idx = nameToIdx.getOrDefault(terr.getName(), -1);
                if (idx < 0) {
                    rawBaos.write(0xFF);
                    rawBaos.write(0xFF);
                    byte[] nameBytes = terr.getName().getBytes(StandardCharsets.UTF_8);
                    rawBaos.write(nameBytes.length);
                    rawBaos.write(nameBytes);
                } else {
                    rawBaos.write((idx >> 8) & 0xFF);
                    rawBaos.write(idx & 0xFF);
                }

                int treas = terr.getTreasury() != null ? terr.getTreasury().ordinal() : 0;
                rawBaos.write(((treas & 0x07) << 1) | (terr.isHq() ? 1 : 0));

                java.util.List<int[]> upgList = new java.util.ArrayList<>();
                for (SandboxUpgrade u : SandboxUpgrade.values()) {
                    int lvl = terr.getUpgradeLevel(u);
                    if (lvl > 0) {
                        upgList.add(new int[]{u.ordinal(), lvl});
                    }
                }
                rawBaos.write(upgList.size());
                for (int[] u : upgList) {
                    rawBaos.write((u[0] * 12 + u[1]) & 0xFF);
                }
            }

            byte[] uncompressed = rawBaos.toByteArray();
            java.io.ByteArrayOutputStream compBaos = new java.io.ByteArrayOutputStream();
            try (java.util.zip.DeflaterOutputStream dos = new java.util.zip.DeflaterOutputStream(
                    compBaos, new java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION))) {
                dos.write(uncompressed);
            }
            return "rmod:" + Base64.getUrlEncoder().withoutPadding().encodeToString(compBaos.toByteArray());
        } catch (Exception e) {
            return "rmod:" + Base64.getUrlEncoder().withoutPadding().encodeToString(jsonStr.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static EconomySandboxState importFromShareString(String shareString) {
        if (shareString == null || shareString.trim().isEmpty()) {
            throw new IllegalArgumentException("Share string cannot be empty.");
        }
        String cleaned = shareString.trim();
        if (cleaned.startsWith("rmod:")) {
            cleaned = cleaned.substring(5);
        } else if (cleaned.startsWith("eco:")) {
            cleaned = cleaned.substring(4);
        }

        // 1. Cloud key fallback – unchanged
        if (cleaned.length() <= 16 && cleaned.matches("^[A-Za-z0-9_-]+$")) {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(3))
                        .build();
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("https://api.pastes.dev/" + cleaned))
                        .timeout(java.time.Duration.ofSeconds(4))
                        .header("User-Agent", "RMod-Minecraft")
                        .GET()
                        .build();
                java.net.http.HttpResponse<String> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    JsonElement elem = JsonParser.parseString(res.body());
                    if (elem.isJsonObject()) {
                        return deserializeState(elem.getAsJsonObject());
                    }
                }
            } catch (Exception e) {
                PotsmodClient.LOGGER.warn("Failed to fetch cloud share code: " + cleaned, e);
            }
        }

        // 2. Decode Base64 (binary or JSON fallback)
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(cleaned);
        } catch (Exception e) {
            try {
                bytes = Base64.getDecoder().decode(cleaned);
            } catch (Exception ex) {
                // treat as raw JSON string
                bytes = cleaned.getBytes(StandardCharsets.UTF_8);
            }
        }

        // Decompress if needed
        byte[] data;
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes);
             java.util.zip.InflaterInputStream iis = new java.util.zip.InflaterInputStream(bais)) {
            data = iis.readAllBytes();
        } catch (Exception e) {
            data = bytes; // not compressed
        }

        // If it looks like the old JSON format, just deserialize
        if (data.length >= 2 && data[0] == MAGIC_BINARY && data[1] == BINARY_VERSION) {
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data);
            buf.get(); // magic
            buf.get(); // version

            // Default treasury tier
            int treasuryOrdinal = buf.get() & 0xFF;
            TreasuryTier defaultTreasury = (treasuryOrdinal >= 0 && treasuryOrdinal < TreasuryTier.values().length)
                    ? TreasuryTier.values()[treasuryOrdinal]
                    : TreasuryTier.VERY_LOW;

            // Tributes (4 resources)
            Map<GuildResource, Integer> tributes = new EnumMap<>(GuildResource.class);
            for (GuildResource res : GuildResource.values()) {
                int hi = buf.get() & 0xFF;
                int lo = buf.get() & 0xFF;
                tributes.put(res, (hi << 8) | lo);
            }

            // Territory count
            int terrCount = ((buf.get() & 0xFF) << 8) | (buf.get() & 0xFF);

            EconomySandboxState state = new EconomySandboxState();
            state.setDefaultTreasury(defaultTreasury);
            state.getTributes().clear();
            state.getTributes().putAll(tributes);

            // Build name->index map (same as export)
            List<String> sortedTerrs = new ArrayList<>(TerritoryBaseManager.getTerritories().keySet());
            Collections.sort(sortedTerrs);
            Map<Integer, String> idxToName = new HashMap<>();
            for (int i = 0; i < sortedTerrs.size(); i++) {
                idxToName.put(i, sortedTerrs.get(i));
            }

            for (int i = 0; i < terrCount; i++) {
                // Index or fallback to name string
                int hi = buf.get() & 0xFF;
                int lo = buf.get() & 0xFF;
                int idx = (hi << 8) | lo;
                String terrName;
                if (idx == 0xFFFF) { // sentinel for unknown name
                    int nameLen = buf.get() & 0xFF;
                    byte[] nameBytes = new byte[nameLen];
                    buf.get(nameBytes);
                    terrName = new String(nameBytes, StandardCharsets.UTF_8);
                } else {
                    terrName = idxToName.getOrDefault(idx, "UNKNOWN_TERRITORY_");
                }

                EconomyTerritoryState terrState = state.addTerritory(terrName);

                // Treasury flag + HQ flag packed into one byte
                int flagByte = buf.get() & 0xFF;
                boolean hqFlag = (flagByte & 0x01) != 0;
                int treasIdx = (flagByte >> 1) & 0x07; // we stored tier ordinal (0‑7) in bits 1‑3
                if (treasIdx >= 0 && treasIdx < TreasuryTier.values().length) {
                    terrState.setTreasury(TreasuryTier.values()[treasIdx]);
                }
                if (hqFlag) {
                    state.setHqTerritory(terrName);
                }

                // Upgrades list
                int upgCount = buf.get() & 0xFF;
                for (int u = 0; u < upgCount; u++) {
                    int packed = buf.get() & 0xFF;
                    int upgradeIdx = packed / 12;
                    int level = packed % 12;
                    if (upgradeIdx >= 0 && upgradeIdx < SandboxUpgrade.values().length) {
                        terrState.setUpgradeLevel(SandboxUpgrade.values()[upgradeIdx], level);
                    }
                }
            }
            return state;
        } else {
            // Fallback – treat as plain JSON (legacy format)
            String jsonStr = new String(data, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            return deserializeState(obj);
        }
    }

    // --- JSON Serialization & Strict Validation ---
    private static JsonObject serializeState(EconomySandboxState state, String name) {
        JsonObject root = new JsonObject();
        root.addProperty("version", CURRENT_VERSION);
        if (name != null) {
            root.addProperty("name", name);
        }
        if (state.getHqTerritory() != null) {
            root.addProperty("hq", state.getHqTerritory());
        }
        root.addProperty("defaultTreasury", state.getDefaultTreasury().name());

        JsonObject tributesObj = new JsonObject();
        for (Map.Entry<GuildResource, Integer> entry : state.getTributes().entrySet()) {
            tributesObj.addProperty(entry.getKey().name(), entry.getValue());
        }
        root.add("tributes", tributesObj);

        JsonObject terrsObj = new JsonObject();
        for (EconomyTerritoryState terr : state.getAddedTerritories().values()) {
            JsonObject terrObj = new JsonObject();
            if (terr.getTreasury() != null) {
                terrObj.addProperty("treasury", terr.getTreasury().name());
            }
            if (terr.isHq()) {
                terrObj.addProperty("isHq", true);
            }
            JsonObject upgObj = new JsonObject();
            for (SandboxUpgrade upgrade : SandboxUpgrade.values()) {
                int lvl = terr.getUpgradeLevel(upgrade);
                if (lvl > 0) {
                    upgObj.addProperty(upgrade.name(), lvl);
                }
            }
            terrObj.add("upgrades", upgObj);
            terrsObj.add(terr.getName(), terrObj);
        }
        root.add("territories", terrsObj);

        return root;
    }

    private static EconomySandboxState deserializeState(JsonObject root) {
        if (!root.has("territories") || !root.get("territories").isJsonObject()) {
            throw new IllegalArgumentException("Missing or invalid 'territories' object in sandbox data.");
        }

        EconomySandboxState state = new EconomySandboxState();

        if (root.has("defaultTreasury") && root.get("defaultTreasury").isJsonPrimitive()) {
            try {
                state.setDefaultTreasury(TreasuryTier.valueOf(root.get("defaultTreasury").getAsString()));
            } catch (Exception ignored) {
                state.setDefaultTreasury(TreasuryTier.VERY_LOW);
            }
        }

        if (root.has("tributes") && root.get("tributes").isJsonObject()) {
            JsonObject tributesObj = root.getAsJsonObject("tributes");
            for (Map.Entry<String, JsonElement> entry : tributesObj.entrySet()) {
                try {
                    GuildResource res = GuildResource.valueOf(entry.getKey());
                    state.getTributes().put(res, entry.getValue().getAsInt());
                } catch (Exception ignored) {}
            }
        }

        JsonObject terrsObj = root.getAsJsonObject("territories");
        Map<String, ?> catalog = TerritoryBaseManager.getTerritories();

        for (Map.Entry<String, JsonElement> entry : terrsObj.entrySet()) {
            String terrName = entry.getKey();
            // Validate territory exists in catalogue (or allow if catalog not loaded yet)
            if (catalog != null && !catalog.isEmpty() && !catalog.containsKey(terrName)) {
                PotsmodClient.LOGGER.warn("Skipping unknown territory from sandbox data: {}", terrName);
                continue;
            }

            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject terrObj = entry.getValue().getAsJsonObject();
            EconomyTerritoryState terrState = state.addTerritory(terrName);

            if (terrObj.has("treasury") && terrObj.get("treasury").isJsonPrimitive()) {
                try {
                    terrState.setTreasury(TreasuryTier.valueOf(terrObj.get("treasury").getAsString()));
                } catch (Exception ignored) {}
            }

            if (terrObj.has("isHq") && terrObj.get("isHq").getAsBoolean()) {
                terrState.setHq(true);
                state.setHqTerritory(terrName);
            }

            if (terrObj.has("upgrades") && terrObj.get("upgrades").isJsonObject()) {
                JsonObject upgObj = terrObj.getAsJsonObject("upgrades");
                for (Map.Entry<String, JsonElement> upgEntry : upgObj.entrySet()) {
                    try {
                        SandboxUpgrade upgrade = SandboxUpgrade.valueOf(upgEntry.getKey());
                        int lvl = upgEntry.getValue().getAsInt();
                        terrState.setUpgradeLevel(upgrade, lvl);
                    } catch (Exception ignored) {}
                }
            }
        }

        if (root.has("hq") && root.get("hq").isJsonPrimitive()) {
            String hqName = root.get("hq").getAsString();
            if (state.getAddedTerritories().containsKey(hqName)) {
                state.setHqTerritory(hqName);
            }
        }

        state.invalidateDistanceCache();
        return state;
    }

    private static String sanitizeFileName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[^a-zA-Z0-9._ -]", "").trim();
    }
}
