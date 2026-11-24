package org.pots.potsmod.managers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.pots.potsmod.PotsmodClient;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class BackendManager {
    public static final String CURRENT_VERSION = "0.20";
    public static final String BACKEND_BASE = "http://136.243.54.107:28958";
    public static final String BACKEND_CHALLENGE_URL = BACKEND_BASE + "/api/mod/challenge";
    public static final String BACKEND_BOOT_URL = BACKEND_BASE + "/api/mod/boot";
    public static final String BACKEND_HEARTBEAT_URL = BACKEND_BASE + "/api/mod/heartbeat";
    public static final String BACKEND_CHECK_URL = BACKEND_BASE + "/api/mod/check-ban";
    public static final String BACKEND_CHAT_URL = BACKEND_BASE + "/api/mod/chat/messages";

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Potsmod-Backend-Thread");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean booted = false;
    private static volatile boolean isBanned = false;
    private static volatile String activeBanReason = null;
    private static volatile String activeBanTitle = "§c§lDisconnected by r [Banned]";
    private static volatile long lastChatId = 0;
    private static final java.util.Set<Long> PROCESSED_CHAT_IDS = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private static volatile String sessionToken = null;
    private static volatile boolean isGuildMember = false;
    private static volatile String guildName = null;
    private static volatile String latestVersion = null;
    private static volatile String downloadUrl = null;

    private BackendManager() {}

    public static void init() {
        sendBootInfo();
        startHeartbeat();
        startChatPoll();

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            checkAndShowUpdateNotice(client);
        });
    }

    public static String getSessionToken() {
        return sessionToken;
    }

    public static boolean isGuildMember() {
        return isGuildMember;
    }

    public static String getGuildName() {
        return guildName;
    }

    public static void checkAndShowUpdateNotice(Minecraft mc) {
        if (mc == null) return;
        String lastNotified = org.pots.potsmod.config.PotsmodConfig.getData().getLastNotifiedVersion();
        if (!CURRENT_VERSION.equalsIgnoreCase(lastNotified)) {
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("§6[r] §aThe mod R has been automatically updated to the new version " + CURRENT_VERSION),
                            false
                    );
                    org.pots.potsmod.config.PotsmodConfig.getData().setLastNotifiedVersion(CURRENT_VERSION);
                    org.pots.potsmod.config.PotsmodConfig.save();
                }
            });
        }
    }

    public static boolean isBanned() {
        return isBanned;
    }

    public static String getActiveBanReason() {
        return activeBanReason;
    }

    public static Component formatReason(String raw, String defaultMessage) {
        if (raw == null || raw.isBlank()) {
            raw = defaultMessage;
        }
        // Replace & color codes with §
        String translated = raw.replaceAll("(?i)&([0-9a-fk-or])", "§$1");
        // Convert literal "\n" strings to actual newlines
        translated = translated.replace("\\n", "\n");
        return Component.literal(translated);
    }

    public static boolean checkIfBannedAndBlock(Minecraft mc, Screen parentScreen) {
        // Fast sync check if ban status is not currently known
        if (!isBanned) {
            queryBanStatusSync();
        }

        if (isBanned) {
            if (mc != null) {
                mc.execute(() -> {
                    Component title = formatReason(activeBanTitle, "§c§lDisconnected by r [Banned]");
                    Component desc = formatReason(activeBanReason, "§cYou are banned from using r.\n\n§eReason: §fAccess Denied");
                    Screen parent = parentScreen != null ? parentScreen : new TitleScreen();
                    mc.setScreen(new DisconnectedScreen(parent, title, desc));
                });
            }
            return true;
        }
        return false;
    }

    public static void queryBanStatusSync() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            User user = mc.getUser();
            if (user == null) return;

            String name = user.getName();
            String uuid = user.getProfileId() != null ? user.getProfileId().toString() : "";
            if (uuid.isEmpty()) return;

            String queryUrl = String.format("%s?uuid=%s&name=%s", BACKEND_CHECK_URL, uuid, name);
            URL url = URI.create(queryUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(800);
            conn.setReadTimeout(800);
            conn.setRequestProperty("User-Agent", "Potsmod-Client");

            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream()) {
                    String respStr = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject json = JsonParser.parseString(respStr).getAsJsonObject();
                    if (json.has("banned") && json.get("banned").getAsBoolean()) {
                        isBanned = true;
                        activeBanReason = json.has("reason") && !json.get("reason").isJsonNull()
                                ? json.get("reason").getAsString()
                                : "Banned by an administrator";
                        if (json.has("banTitle") && !json.get("banTitle").isJsonNull()) {
                            activeBanTitle = json.get("banTitle").getAsString();
                        } else if (json.has("title") && !json.get("title").isJsonNull()) {
                            activeBanTitle = json.get("title").getAsString();
                        }
                    } else {
                        isBanned = false;
                        activeBanReason = null;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void startHeartbeat() {
        // Run heartbeat every 3 seconds for near real-time presence, bans, and kicks
        SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                if (sessionToken == null || sessionToken.isEmpty()) return;

                Minecraft mc = Minecraft.getInstance();
                if (mc == null) return;
                User user = mc.getUser();
                if (user == null) return;

                String jsonPayload = String.format("{\"sessionToken\":\"%s\",\"lastChatId\":%d}", sessionToken, lastChatId);

                URL url = URI.create(BACKEND_HEARTBEAT_URL).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(1500);
                conn.setReadTimeout(1500);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("x-session-token", sessionToken);
                conn.setRequestProperty("User-Agent", "Potsmod-Client");
                conn.setDoOutput(true);

                byte[] bytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    try (InputStream is = conn.getInputStream()) {
                        String respStr = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        JsonObject json = JsonParser.parseString(respStr).getAsJsonObject();

                        // Ban check
                        if (json.has("banned") && json.get("banned").getAsBoolean()) {
                            String reason = json.has("reason") && !json.get("reason").isJsonNull()
                                    ? json.get("reason").getAsString()
                                    : "Banned by an administrator";
                            String banTitle = json.has("banTitle") && !json.get("banTitle").isJsonNull()
                                    ? json.get("banTitle").getAsString()
                                    : (json.has("title") && !json.get("title").isJsonNull() ? json.get("title").getAsString() : null);
                            handleBan(reason, banTitle);
                        } else {
                            // If unbanned on dashboard, clear ban immediately
                            if (isBanned) {
                                isBanned = false;
                                activeBanReason = null;
                            }
                        }

                        // Kick check (one-time)
                        if (json.has("kicked") && json.get("kicked").getAsBoolean()) {
                            String kickReason = json.has("kickReason") && !json.get("kickReason").isJsonNull()
                                    ? json.get("kickReason").getAsString()
                                    : "Kicked by an administrator";
                            String kickTitle = json.has("kickTitle") && !json.get("kickTitle").isJsonNull()
                                    ? json.get("kickTitle").getAsString()
                                    : (json.has("title") && !json.get("title").isJsonNull() ? json.get("title").getAsString() : null);
                            handleKick(kickReason, kickTitle);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }, 2, 3, TimeUnit.SECONDS);
    }

    private static void startChatPoll() {
        // Fast 1-second poll for real-time Discord chat message delivery
        SCHEDULER.scheduleWithFixedDelay(() -> {
            try {
                if (sessionToken == null || sessionToken.isEmpty()) return;

                Minecraft mc = Minecraft.getInstance();
                if (mc == null || mc.player == null) return;

                String queryUrl = BACKEND_CHAT_URL + "?since=" + lastChatId + "&sessionToken=" + sessionToken;
                URL url = URI.create(queryUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                conn.setRequestProperty("x-session-token", sessionToken);
                conn.setRequestProperty("User-Agent", "Potsmod-Client");

                if (conn.getResponseCode() == 200) {
                    try (InputStream is = conn.getInputStream()) {
                        String respStr = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        JsonObject json = JsonParser.parseString(respStr).getAsJsonObject();
                        if (json.has("messages") && json.get("messages").isJsonArray()) {
                            for (com.google.gson.JsonElement el : json.getAsJsonArray("messages")) {
                                if (el.isJsonObject()) {
                                    JsonObject m = el.getAsJsonObject();
                                    long id = m.has("id") ? m.get("id").getAsLong() : 0;
                                    if (id > 0 && !PROCESSED_CHAT_IDS.add(id)) {
                                        continue; // Prevent any duplicate display
                                    }
                                    if (id > lastChatId) {
                                        lastChatId = id;
                                    }
                                    String author = m.has("author") ? m.get("author").getAsString() : "Discord User";
                                    String content = m.has("message") ? m.get("message").getAsString() : "";
                                    String formatted = m.has("formatted") ? m.get("formatted").getAsString() : null;

                                    Component displayComp = buildEdnDiscordMessage(author, content, formatted);
                                    mc.execute(() -> {
                                        if (mc.player != null) {
                                            mc.player.displayClientMessage(displayComp, false);
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }, 1, 1, TimeUnit.SECONDS);
    }

    private static final Identifier PILL_FONT_ID = Identifier.fromNamespaceAndPath("minecraft", "banner/pill");
    private static final Identifier PREFIX_FONT_ID = Identifier.fromNamespaceAndPath("minecraft", "chat/prefix");
    private static final FontDescription PILL_FONT = new FontDescription.Resource(PILL_FONT_ID);
    private static final FontDescription PREFIX_FONT = new FontDescription.Resource(PREFIX_FONT_ID);
    private static final Style PREFIX_STYLE = Style.EMPTY.withFont(PREFIX_FONT).withColor(ChatFormatting.AQUA);
    private static final int[] SHIELD = new int[]{851964, 57350, 851967, 57346, 851966};
    private static final String LINE_MARKER = "\u200c";

    private static String pillLabel(String label) {
        StringBuilder out = new StringBuilder();
        out.appendCodePoint(57440).appendCodePoint(851967);
        for (int i = 0; i < label.length(); ++i) {
            int letter = Character.toLowerCase(label.charAt(i)) - 97;
            if (letter < 0 || letter >= 26) continue;
            out.appendCodePoint(57392 + letter).appendCodePoint(851967);
        }
        out.appendCodePoint(57442);
        return out.toString();
    }

    private static Component prefix(int[] glyphs, String marker) {
        StringBuilder out = new StringBuilder();
        out.append(marker);
        for (int glyph : glyphs) {
            out.appendCodePoint(glyph);
        }
        return Component.literal(out.toString()).append(" ").setStyle(PREFIX_STYLE);
    }

    private static Component buildEdnDiscordMessage(String author, String message, String fallbackFormatted) {
        try {
            Style pillStyle = Style.EMPTY.withFont(PILL_FONT).withColor(ChatFormatting.AQUA);
            MutableComponent body = Component.empty()
                    .append(Component.literal(pillLabel("discord")).setStyle(pillStyle))
                    .append(Component.literal(" " + author + ": ").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(message).withStyle(ChatFormatting.AQUA));

            MutableComponent out = Component.empty();
            out.append(prefix(SHIELD, LINE_MARKER));
            out.append(body);
            return out;
        } catch (Throwable t) {
            MutableComponent prefix = Component.literal("[Discord] ")
                    .withStyle(ChatFormatting.BLUE);
            MutableComponent authorComp = Component.literal(author + ": ")
                    .withStyle(ChatFormatting.AQUA);
            MutableComponent msgComp = Component.literal(message)
                    .withStyle(ChatFormatting.AQUA);
            return prefix.append(authorComp).append(msgComp);
        }
    }

    public static void handleBan(String reason) {
        handleBan(reason, null);
    }

    public static void handleBan(String reason, String title) {
        isBanned = true;
        activeBanReason = reason;
        if (title != null && !title.isBlank()) {
            activeBanTitle = title;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        mc.execute(() -> {
            Component titleComp = formatReason(activeBanTitle, "§c§lDisconnected by r [Banned]");
            Component desc = formatReason(reason, "§cYou have been banned from using r by an administrator.");
            if (mc.level != null || mc.getConnection() != null) {
                mc.disconnect(new DisconnectedScreen(new TitleScreen(), titleComp, desc), false);
            } else if (mc.screen != null && !(mc.screen instanceof DisconnectedScreen)) {
                mc.setScreen(new DisconnectedScreen(new TitleScreen(), titleComp, desc));
            }
        });
    }

    public static void handleKick(String reason) {
        handleKick(reason, null);
    }

    public static void handleKick(String reason, String title) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        String kickTitle = (title != null && !title.isBlank()) ? title : "§e§lDisconnected by r [Kicked]";
        mc.execute(() -> {
            Component titleComp = formatReason(kickTitle, "§e§lDisconnected by r [Kicked]");
            Component desc = formatReason(reason, "§eYou have been kicked by an administrator.");
            if (mc.level != null || mc.getConnection() != null) {
                mc.disconnect(new DisconnectedScreen(new TitleScreen(), titleComp, desc), false);
            }
        });
    }

    public static void sendBootInfo() {
        if (booted) return;
        booted = true;

        SCHEDULER.submit(() -> {
            for (int attempt = 0; attempt < 5; attempt++) {
                try {
                    Thread.sleep(1500);

                    Minecraft mc = Minecraft.getInstance();
                    if (mc == null) continue;
                    User user = mc.getUser();
                    if (user == null) continue;

                    String name = user.getName();
                    String uuid = user.getProfileId() != null ? user.getProfileId().toString() : "";
                    if (uuid.isEmpty()) continue;

                    // 1. Fetch challenge nonce from backend
                    URL chalUrl = URI.create(BACKEND_CHALLENGE_URL).toURL();
                    HttpURLConnection chalConn = (HttpURLConnection) chalUrl.openConnection();
                    chalConn.setRequestMethod("GET");
                    chalConn.setConnectTimeout(2500);
                    chalConn.setReadTimeout(2500);
                    chalConn.setRequestProperty("User-Agent", "Potsmod-Client");

                    String nonce = null;
                    if (chalConn.getResponseCode() == 200) {
                        try (InputStream is = chalConn.getInputStream()) {
                            String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                            JsonObject j = JsonParser.parseString(resp).getAsJsonObject();
                            if (j.has("nonce") && !j.get("nonce").isJsonNull()) {
                                nonce = j.get("nonce").getAsString();
                            }
                        }
                    }
                    if (nonce == null || nonce.isEmpty()) continue;

                    // 2. Call Mojang session server joinServer with accessToken and nonce as serverId
                    String accessToken = user.getAccessToken();
                    String cleanUuid = uuid.replace("-", "");
                    JsonObject joinObj = new JsonObject();
                    joinObj.addProperty("accessToken", accessToken);
                    joinObj.addProperty("selectedProfile", cleanUuid);
                    joinObj.addProperty("serverId", nonce);

                    URL mojangUrl = URI.create("https://sessionserver.mojang.com/session/minecraft/join").toURL();
                    HttpURLConnection mojangConn = (HttpURLConnection) mojangUrl.openConnection();
                    mojangConn.setRequestMethod("POST");
                    mojangConn.setConnectTimeout(3500);
                    mojangConn.setReadTimeout(3500);
                    mojangConn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    mojangConn.setDoOutput(true);

                    byte[] joinBytes = joinObj.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream os = mojangConn.getOutputStream()) {
                        os.write(joinBytes);
                    }
                    int joinStatus = mojangConn.getResponseCode();
                    if (joinStatus != 204 && joinStatus != 200) {
                        PotsmodClient.LOGGER.warn("Mojang session join returned code: " + joinStatus);
                    }

                    // 3. Post to backend boot endpoint with the verified nonce and username
                    String skinUrl = "https://crafatar.com/renders/head/" + uuid + "?overlay";
                    JsonObject bootObj = new JsonObject();
                    bootObj.addProperty("nonce", nonce);
                    bootObj.addProperty("username", name);
                    bootObj.addProperty("skinUrl", skinUrl);
                    bootObj.addProperty("version", CURRENT_VERSION);

                    URL url = URI.create(BACKEND_BOOT_URL).toURL();
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(2500);
                    conn.setReadTimeout(2500);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setRequestProperty("User-Agent", "Potsmod-Client");
                    conn.setDoOutput(true);

                    byte[] bytes = bootObj.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(bytes);
                    }

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        try (InputStream is = conn.getInputStream()) {
                            String respStr = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                            JsonObject json = JsonParser.parseString(respStr).getAsJsonObject();
                            if (json.has("banned") && json.get("banned").getAsBoolean()) {
                                String reason = json.has("reason") && !json.get("reason").isJsonNull()
                                        ? json.get("reason").getAsString()
                                        : "Banned by an administrator";
                                String banTitle = json.has("banTitle") && !json.get("banTitle").isJsonNull()
                                        ? json.get("banTitle").getAsString()
                                        : (json.has("title") && !json.get("title").isJsonNull() ? json.get("title").getAsString() : null);
                                handleBan(reason, banTitle);
                            }

                            // Capture backend session token & guild verification
                            if (json.has("sessionToken") && !json.get("sessionToken").isJsonNull()) {
                                sessionToken = json.get("sessionToken").getAsString();
                            }
                            if (json.has("isGuildMember") && !json.get("isGuildMember").isJsonNull()) {
                                isGuildMember = json.get("isGuildMember").getAsBoolean();
                            }
                            if (json.has("guildName") && !json.get("guildName").isJsonNull()) {
                                guildName = json.get("guildName").getAsString();
                            }
                            if (json.has("latestVersion") && !json.get("latestVersion").isJsonNull()) {
                                latestVersion = json.get("latestVersion").getAsString();
                            }
                            if (json.has("downloadUrl") && !json.get("downloadUrl").isJsonNull()) {
                                downloadUrl = json.get("downloadUrl").getAsString();
                            }

                            // Auto-updater enabled for future releases
                            if (org.pots.potsmod.config.PotsmodConfig.getData().isAutoUpdate() && latestVersion != null && downloadUrl != null) {
                                if (isNewerVersion(latestVersion, CURRENT_VERSION)) {
                                    triggerAutoUpdate(downloadUrl, latestVersion);
                                }
                            }
                        }
                        PotsmodClient.LOGGER.info("Potsmod Backend boot reported successfully: " + name + " (" + uuid + ") [guildMember=" + isGuildMember + "]");
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static void triggerAutoUpdate(String dlUrl, String newVer) {
        SCHEDULER.submit(() -> {
            try {
                Path modsDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("mods");
                if (!Files.exists(modsDir)) {
                    Files.createDirectories(modsDir);
                }

                String fullUrl = dlUrl.startsWith("http") ? dlUrl : (BACKEND_BASE + dlUrl);
                URL url = URI.create(fullUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("User-Agent", "Potsmod-Client");

                if (conn.getResponseCode() == 200) {
                    Path tempFile = modsDir.resolve("Potsmod-" + newVer + ".jar.tmp");
                    Path targetFile = modsDir.resolve("Potsmod-" + newVer + ".jar");

                    try (InputStream is = conn.getInputStream();
                         OutputStream os = Files.newOutputStream(tempFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            os.write(buffer, 0, read);
                        }
                    }

                    // Register shutdown hook to delete old Potsmod jars and activate the new jar
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try {
                            // Find and delete previous Potsmod / RMod jars in mods folder
                            try (var stream = Files.list(modsDir)) {
                                stream.filter(p -> {
                                    String fn = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                                    return (fn.startsWith("potsmod-") || fn.startsWith("rmod-")) && fn.endsWith(".jar") && !p.equals(targetFile);
                                }).forEach(oldJar -> {
                                    try { Files.deleteIfExists(oldJar); } catch (Exception ignored) {}
                                });
                            }
                            if (Files.exists(tempFile)) {
                                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (Exception ignored) {}
                    }, "Potsmod-AutoUpdater"));

                    PotsmodClient.LOGGER.info("Potsmod: Staged update to version {} (will apply on restart)", newVer);
                }
            } catch (Exception e) {
                PotsmodClient.LOGGER.warn("Potsmod auto-update failed: {}", e.getMessage());
            }
        });
    }

    private static boolean isNewerVersion(String remote, String current) {
        if (remote == null || current == null || remote.equalsIgnoreCase(current)) return false;
        try {
            String cleanRemote = remote.replaceAll("[^0-9.]", "");
            String cleanCurrent = current.replaceAll("[^0-9.]", "");
            String[] rParts = cleanRemote.split("\\.");
            String[] cParts = cleanCurrent.split("\\.");
            int max = Math.max(rParts.length, cParts.length);
            for (int i = 0; i < max; i++) {
                int r = i < rParts.length && !rParts[i].isEmpty() ? Integer.parseInt(rParts[i]) : 0;
                int c = i < cParts.length && !cParts[i].isEmpty() ? Integer.parseInt(cParts[i]) : 0;
                if (r > c) return true;
                if (r < c) return false;
            }
        } catch (Exception ignored) {}
        return !remote.equalsIgnoreCase(current);
    }
}
