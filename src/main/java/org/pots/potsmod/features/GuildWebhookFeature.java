package org.pots.potsmod.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.pots.potsmod.PotsmodClient;
import org.pots.potsmod.chat.CapturedMessage;
import org.pots.potsmod.chat.GuildChatParser;
import org.pots.potsmod.chat.ChatText;
import org.pots.potsmod.managers.BackendManager;
import org.pots.potsmod.managers.FeatureManager;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.config.PotsmodConfigData;
import org.pots.potsmod.eventbus.REventBus;
import org.pots.potsmod.eventbus.Subscribe;
import org.pots.potsmod.models.events.ChatMessageReceivedEvent;
import org.pots.potsmod.utils.PlayerResolver;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GuildWebhookFeature extends Feature {
    private static final String BACKEND_EVENT_URL = BackendManager.BACKEND_BASE + "/api/mod/webhook-event";

    private static final ExecutorService ASYNC_POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "RMod-GuildWebhook-Worker");
        t.setDaemon(true);
        return t;
    });

    private static final Pattern FORMAT_CODES = Pattern.compile("(?i)[§&][0-9a-fk-orx]|&\\{[^}]+\\}|&<[^>]+>|&\\[[^\\]]+\\]");
    private static final String WYNN_SOFT_WRAP = "\udaff\udffc\ue001\udb00\udc06";
    private static final int GUILD_CHAT_COLOR = 5636095; // 0x55FFFF Aqua

    // EDN Chat Pattern
    private static final Pattern CHAT_PATTERN = Pattern.compile(
            "^(?:<\\d+>\\s*)?([a-zA-Z0-9_][a-zA-Z0-9_ ]*[a-zA-Z0-9_]|[a-zA-Z0-9_]{1,16})\\s*:\\s*(.*)$",
            Pattern.CASE_INSENSITIVE
    );

    // Bank chat log pattern from EDN
    private static final Pattern BANK_PATTERN = Pattern.compile(
            "^(.+?)\\s+(deposited|withdrew)\\s+(.+?)\\s+(?:to|from)\\s+the Guild Bank\\s+\\((.+)\\)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("^(?:(\\d+)x\\s+)?(.+)$");
    private static final Pattern CHARGES_PATTERN = Pattern.compile("^(.*?)(?:\\s+\\[([^\\]]+)\\])?$");
    private static final Pattern REWARD_PATTERN = Pattern.compile(
            "^(.+?)\\s+rewarded\\s+(.+?)\\s+to\\s+(.+?)(?:\\s+from Guild Rewards)?$",
            Pattern.CASE_INSENSITIVE
    );

    // Economy modification pattern from EDN
    private static final Pattern ECONOMY_PATTERN = Pattern.compile(
            "^(?:[^:]{1,64} changed \\d+ (?:upgrades|bonuses) on .+|" +
                    "[^:]{1,64} changed the (?:global )?(?:ally )?tax(?: of .+)? to \\d+%|" +
                    "[^:]{1,64} changed (?:the global style|the style of .+) to (?:fastest|cheapest)|" +
                    "[^:]{1,64} changed (?:the global borders|the borders of .+) to (?:open|close)|" +
                    "[^:]{1,64} set the guild headquarters to .+|" +
                    "[^:]{1,64} set .+ (?:bonus|upgrade) to level \\d+ on .+|" +
                    "[^:]{1,64} removed .+ (?:bonus|upgrade) from .+|" +
                    "[^:]{1,64} applied the loadout(?:\\s+|:).+ on .+|" +
                    "Territory .+ (?:is using|is producing) more resources than it can store[!.]?|" +
                    "Territory .+ production has stabil(?:ised|ized)[.!]?)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ECO_UPGRADES_CHANGED = Pattern.compile(
            "^([^:]{1,64})\\s+changed\\s+(\\d+)\\s+(?:upgrades|bonuses)\\s+on\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ECO_TAX_CHANGED = Pattern.compile(
            "^([^:]{1,64})\\s+changed\\s+the\\s+(?:(global|ally)\\s+)?tax(?:\\s+of\\s+(.+?))?\\s+to\\s+(\\d+)%$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ECO_STYLE_CHANGED = Pattern.compile(
            "^([^:]{1,64})\\s+changed\\s+(?:the global style|the style of (.+?))\\s+to\\s+(fastest|cheapest)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ECO_BORDERS_CHANGED = Pattern.compile(
            "^([^:]{1,64})\\s+changed\\s+(?:the global borders|the borders of (.+?))\\s+to\\s+(open|close)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ECO_HQ_SET = Pattern.compile(
            "^([^:]{1,64})\\s+set\\s+the\\s+guild\\s+headquarters\\s+to\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ECO_UPGRADE_SET = Pattern.compile(
            "^([^:]{1,64})\\s+set\\s+(.+?)\\s+(?:bonus|upgrade)\\s+to\\s+level\\s+(\\d+)\\s+on\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ECO_UPGRADE_REMOVED = Pattern.compile(
            "^([^:]{1,64})\\s+removed\\s+(.+?)\\s+(?:bonus|upgrade)\\s+from\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ECO_LOADOUT_APPLIED = Pattern.compile(
            "^([^:]{1,64})\\s+applied\\s+the\\s+loadout(?:\\s+|:\\s*)(.+?)\\s+on\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ECO_CAPACITY_WARNING = Pattern.compile(
            "^Territory\\s+(.+?)\\s+(is using|is producing)\\s+more\\s+resources\\s+than\\s+it\\s+can\\s+store[!.]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ECO_STABILIZED = Pattern.compile(
            "^Territory\\s+(.+?)\\s+production\\s+has\\s+stabil(?:ised|ized)[.!]?$", Pattern.CASE_INSENSITIVE);

    private final Map<String, Long> recentDebounce = new ConcurrentHashMap<>();

    @Override
    public void init() {
        REventBus.subscribe(this);
    }

    public static void onIncomingSystemChat(Component comp, boolean overlay) {
        if (overlay || comp == null) return;
        GuildWebhookFeature feature = FeatureManager.getGuildWebhookFeature();
        if (feature != null && feature.isEnabled()) {
            feature.handleIncomingComponent(comp);
        }
    }

    public void handleIncomingComponent(Component comp) {
        if (comp == null) return;

        PlayerResolver.learnFromComponent(comp);

        if (GuildChatParser.looksLikeGuildChat(comp)) {
            Optional<CapturedMessage> parsed = GuildChatParser.parse(comp);
            if (parsed.isPresent()) {
                CapturedMessage msg = parsed.get();
                if (shouldSendRelay(msg)) {
                    dispatchGuildChat(msg);
                    return;
                }
            }
        }

        String raw = comp.getString();
        if (raw == null || raw.isBlank()) return;

        String normalized = normalize(raw);
        if (normalized.isEmpty()) return;

        if (checkGuildBankMessage(normalized, comp)) return;
        checkEconomyMessage(normalized, comp);
    }

    @Subscribe
    public void onChatMessage(ChatMessageReceivedEvent event) {
        if (event == null || event.message() == null) {
            return;
        }
        handleIncomingComponent(event.message());
    }

    private synchronized boolean shouldSendRelay(CapturedMessage msg) {
        long now = System.currentTimeMillis();
        recentDebounce.entrySet().removeIf(e -> now - e.getValue() > 4000L);
        String key = msg.username().toLowerCase(Locale.ROOT) + ":" + normalizeRelay(msg.message());
        Long prev = recentDebounce.put(key, now);
        return prev == null || (now - prev >= 1500L);
    }

    private static String normalizeRelay(String s) {
        if (s == null) return "";
        return s.strip().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static void populateSecurityMetadata(JsonObject backendEvent, String realPlayer, String rawSender) {
        if (BackendManager.getSessionToken() != null) {
            backendEvent.addProperty("sessionToken", BackendManager.getSessionToken());
        }
        Minecraft mc = Minecraft.getInstance();
        boolean isOwn = false;
        if (mc.player != null) {
            String myIgn = mc.player.getGameProfile().name();
            if (realPlayer != null && myIgn.equalsIgnoreCase(realPlayer)) {
                isOwn = true;
            } else if (rawSender != null && myIgn.equalsIgnoreCase(rawSender)) {
                isOwn = true;
            } else if (rawSender != null) {
                String resolvedReal = org.pots.potsmod.utils.PlayerResolver.resolveRealName(null, rawSender);
                if (myIgn.equalsIgnoreCase(resolvedReal)) {
                    isOwn = true;
                }
            }
            if (isOwn && mc.player.getGameProfile().id() != null) {
                backendEvent.addProperty("playerUuid", mc.player.getGameProfile().id().toString());
            }
        }
        if (isOwn) {
            backendEvent.addProperty("isOwn", true);
        }
    }

    private void dispatchGuildChat(CapturedMessage msg) {
        // Enforce guild exclusivity: only forward if verified in tracked guild by backend
        if (!BackendManager.isGuildMember()) {
            return;
        }

        String realPlayer = msg.username();
        String rawSender = msg.nickname();
        String message = msg.message();

        String avatarTarget = (realPlayer != null && realPlayer.matches("^[a-zA-Z0-9_]{3,16}$")) ? realPlayer : "Steve";
        JsonObject directPayload = new JsonObject();
        directPayload.addProperty("username", realPlayer);
        directPayload.addProperty("avatar_url", "https://visage.surgeplay.com/face/128/" + avatarTarget);
        directPayload.addProperty("content", message);

        JsonObject backendEvent = new JsonObject();
        backendEvent.addProperty("type", "guild_chat");
        populateSecurityMetadata(backendEvent, realPlayer, rawSender);
        backendEvent.addProperty("realPlayerName", realPlayer);
        backendEvent.addProperty("rawPlayerName", rawSender);
        backendEvent.addProperty("message", message);
        backendEvent.addProperty("text", realPlayer + ": " + message);

        dispatchWebhook(backendEvent, null, directPayload);
    }

    public static String normalize(String s) {
        if (s == null || s.isBlank()) return "";
        s = FORMAT_CODES.matcher(s).replaceAll("");
        s = s.replace("§", "").replace("\uFFFD", "");
        s = s.replace("\u200c", "").replace("\u200b", "");
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            int type = Character.getType(cp);
            if (type == Character.PRIVATE_USE || type == Character.SURROGATE || (cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0x0C0000) {
                continue;
            }
            sb.appendCodePoint(cp);
        }
        s = sb.toString().replaceAll("\\s+", " ");
        return s.trim();
    }

    private boolean isDebounced(String key) {
        long now = System.currentTimeMillis();
        recentDebounce.entrySet().removeIf(e -> now - e.getValue() > 3000L);
        Long prev = recentDebounce.put(key, now);
        // Only debounce exact identical events repeated within 400ms (preventing duplicate client relays)
        return prev != null && (now - prev < 400L);
    }

    private boolean checkGuildBankMessage(String text, Component comp) {
        PotsmodConfigData config = PotsmodConfig.getData();
        String webhookUrl = config.getGuildBankWebhookUrl();

        if (text.contains("Guild Bank") && (text.contains("deposited") || text.contains("withdrew"))) {
            Matcher matcher = BANK_PATTERN.matcher(text);
            if (matcher.matches()) {
                String rawPlayer = matcher.group(1).trim();
                String action = matcher.group(2).trim().toLowerCase(Locale.ROOT);
                String rawItemPart = matcher.group(3).trim();
                String accessTier = matcher.group(4).trim();

                int quantity = 1;
                String itemName = rawItemPart;
                Matcher qm = QUANTITY_PATTERN.matcher(rawItemPart);
                if (qm.matches()) {
                    if (qm.group(1) != null) {
                        try {
                            quantity = Integer.parseInt(qm.group(1));
                        } catch (NumberFormatException ignored) {}
                    }
                    itemName = qm.group(2).trim();
                }

                String charges = null;
                Matcher cm = CHARGES_PATTERN.matcher(itemName);
                if (cm.matches()) {
                    itemName = cm.group(1).trim();
                    charges = cm.group(2);
                }

                String debounceKey = "bank:" + action + ":" + rawPlayer + ":" + quantity + ":" + itemName + ":" + accessTier;
                if (isDebounced(debounceKey)) {
                    return true;
                }

                boolean isDeposit = "deposited".equalsIgnoreCase(action);
                String realPlayer = PlayerResolver.resolveRealName(comp, rawPlayer);
                String title = isDeposit ? "📥 Guild Bank Deposit" : "📤 Guild Bank Withdrawal";
                int color = isDeposit ? 0x2ECC71 : 0xE74C3C;

                JsonObject directPayload = new JsonObject();
                directPayload.addProperty("username", realPlayer);
                directPayload.addProperty("avatar_url", "https://visage.surgeplay.com/face/128/" + realPlayer);

                JsonArray embeds = new JsonArray();
                JsonObject embed = new JsonObject();
                embed.addProperty("title", title);
                embed.addProperty("description", "**" + realPlayer + "** " + action + " **" + quantity + "x " + itemName + "** "
                        + (charges != null ? "`[" + charges + "]` " : "")
                        + (isDeposit ? "to" : "from") + " the Guild Bank `(" + accessTier + ")`");
                embed.addProperty("color", color);
                embed.addProperty("timestamp", Instant.now().toString());

                JsonObject author = new JsonObject();
                author.addProperty("name", realPlayer);
                author.addProperty("icon_url", "https://visage.surgeplay.com/bust/128/" + realPlayer);
                embed.add("author", author);

                JsonArray fields = new JsonArray();
                fields.add(createField("Player", realPlayer, true));
                fields.add(createField("Action", isDeposit ? "Deposit" : "Withdrawal", true));
                fields.add(createField("Item", quantity + "x " + itemName + (charges != null ? " [" + charges + "]" : ""), true));
                fields.add(createField("Access Tier", accessTier, true));
                embed.add("fields", fields);

                JsonObject footer = new JsonObject();
                footer.addProperty("text", "Wynncraft Guild Logger");
                footer.addProperty("icon_url", "https://visage.surgeplay.com/face/64/Steve");
                embed.add("footer", footer);

                embeds.add(embed);
                directPayload.add("embeds", embeds);

                JsonObject backendEvent = new JsonObject();
                backendEvent.addProperty("type", isDeposit ? "bank_deposit" : "bank_withdraw");
                populateSecurityMetadata(backendEvent, realPlayer, rawPlayer);
                backendEvent.addProperty("realPlayerName", realPlayer);
                backendEvent.addProperty("rawPlayerName", rawPlayer);
                backendEvent.addProperty("action", action);
                backendEvent.addProperty("quantity", quantity);
                backendEvent.addProperty("item", itemName);
                if (charges != null) backendEvent.addProperty("charges", charges);
                backendEvent.addProperty("accessTier", accessTier);
                backendEvent.addProperty("text", text);
                backendEvent.add("fields", fields);

                dispatchWebhook(backendEvent, webhookUrl, directPayload);
                return true;
            }
        }

        Matcher rm = REWARD_PATTERN.matcher(text);
        if (rm.matches()) {
            String actor = rm.group(1).trim();
            String reward = rm.group(2).trim();
            String recipient = rm.group(3).trim();

            String debounceKey = "reward:" + actor + ":" + reward + ":" + recipient;
            if (isDebounced(debounceKey)) {
                return true;
            }

            String realPlayer = PlayerResolver.resolveRealName(comp, actor);
            JsonObject directPayload = new JsonObject();
            directPayload.addProperty("username", realPlayer);
            directPayload.addProperty("avatar_url", "https://visage.surgeplay.com/face/128/" + realPlayer);

            JsonArray embeds = new JsonArray();
            JsonObject embed = new JsonObject();
            embed.addProperty("title", "🎁 Guild Reward Claimed");
            embed.addProperty("description", "**" + realPlayer + "** rewarded **" + reward + "** to **" + recipient + "** from Guild Rewards");
            embed.addProperty("color", 0x9B59B6);
            embed.addProperty("timestamp", Instant.now().toString());

            JsonObject author = new JsonObject();
            author.addProperty("name", realPlayer);
            author.addProperty("icon_url", "https://visage.surgeplay.com/bust/128/" + realPlayer);
            embed.add("author", author);

            JsonArray fields = new JsonArray();
            fields.add(createField("Dispenser", realPlayer, true));
            fields.add(createField("Recipient", recipient, true));
            fields.add(createField("Reward", reward, true));
            embed.add("fields", fields);

            JsonObject footer = new JsonObject();
            footer.addProperty("text", "Wynncraft Guild Logger");
            footer.addProperty("icon_url", "https://visage.surgeplay.com/face/64/Steve");
            embed.add("footer", footer);

            embeds.add(embed);
            directPayload.add("embeds", embeds);

            JsonObject backendEvent = new JsonObject();
            backendEvent.addProperty("type", "bank_reward");
            populateSecurityMetadata(backendEvent, realPlayer, actor);
            backendEvent.addProperty("realPlayerName", realPlayer);
            backendEvent.addProperty("rawPlayerName", actor);
            backendEvent.addProperty("action", "rewarded");
            backendEvent.addProperty("reward", reward);
            backendEvent.addProperty("recipient", recipient);
            backendEvent.addProperty("text", text);
            backendEvent.add("fields", fields);

            dispatchWebhook(backendEvent, webhookUrl, directPayload);
            return true;
        }

        return false;
    }

    private boolean checkEconomyMessage(String text, Component comp) {
        PotsmodConfigData config = PotsmodConfig.getData();
        String webhookUrl = config.getEconomyWebhookUrl();

        if (!ECONOMY_PATTERN.matcher(text).matches()) {
            return false;
        }

        String debounceKey = "eco:" + text;
        if (isDebounced(debounceKey)) {
            return true;
        }

        String title = "⚙️ Territory Economy Modification";
        int color = 0x3498DB;
        String actor = "Guild Member";
        String territory = null;
        String subType = "eco_general";
        String extraItem = null;
        String extraVal = null;
        String extraScope = null;
        JsonArray fields = new JsonArray();

        Matcher m;
        if ((m = ECO_UPGRADES_CHANGED.matcher(text)).matches()) {
            actor = m.group(1).trim();
            String count = m.group(2).trim();
            territory = m.group(3).trim();
            title = "🛠️ Territory Upgrades Changed";
            color = 0x3498DB;
            subType = "eco_upgrades";
            extraVal = count;
            fields.add(createField("Actor", actor, true));
            fields.add(createField("Territory", territory, true));
            fields.add(createField("Changes", count + " upgrades/bonuses", true));
        } else if ((m = ECO_TAX_CHANGED.matcher(text)).matches()) {
            actor = m.group(1).trim();
            String scope = m.group(2) != null ? m.group(2).trim() : "Guild";
            String terr = m.group(3) != null ? m.group(3).trim() : "Global";
            String tax = m.group(4).trim();
            title = "💰 Territory Tax Adjusted";
            color = 0xF1C40F;
            subType = "eco_tax";
            extraScope = scope;
            extraVal = tax;
            territory = terr;
            fields.add(createField("Actor", actor, true));
            fields.add(createField("Scope / Territory", scope + " • " + terr, true));
            fields.add(createField("New Tax", tax + "%", true));
        } else if ((m = ECO_STYLE_CHANGED.matcher(text)).matches()) {
            actor = m.group(1).trim();
            String terr = m.group(2) != null ? m.group(2).trim() : "Global";
            String style = m.group(3).trim();
            title = "⚡ Production Style Updated";
            color = 0x9B59B6;
            subType = "eco_style";
            extraVal = style;
            territory = terr;
            fields.add(createField("Actor", actor, true));
            fields.add(createField("Territory", terr, true));
            fields.add(createField("Style", style.toUpperCase(Locale.ROOT), true));
        } else if ((m = ECO_BORDERS_CHANGED.matcher(text)).matches()) {
            actor = m.group(1).trim();
            String terr = m.group(2) != null ? m.group(2).trim() : "Global";
            String borderState = m.group(3).trim();
            title = "🛡️ Territory Borders Adjusted";
            color = 0x1ABC9C;
            subType = "eco_borders";
            extraVal = borderState;
            territory = terr;
            fields.add(createField("Actor", actor, true));
            fields.add(createField("Territory", terr, true));
            fields.add(createField("Borders", borderState.toUpperCase(Locale.ROOT), true));
        } else if ((m = ECO_HQ_SET.matcher(text)).matches()) {
            actor = m.group(1).trim();
            territory = m.group(2).trim();
            title = "👑 Guild Headquarters Relocated";
            color = 0xE67E22;
            subType = "eco_hq";
            fields.add(createField("Actor", actor, true));
            fields.add(createField("New Headquarters", territory, true));
        } else if ((m = ECO_UPGRADE_SET.matcher(text)).matches()) {
            actor = m.group(1).trim();
            String upgrade = m.group(2).trim();
            String level = m.group(3).trim();
            territory = m.group(4).trim();
            title = "🔧 Upgrade Level Changed";
            color = 0x2980B9;
            subType = "eco_upgrade_set";
            extraItem = upgrade;
            extraVal = level;
            fields.add(createField("Actor", actor, true));
            fields.add(createField("Territory", territory, true));
            fields.add(createField("Upgrade", upgrade + " (Level " + level + ")", true));
        } else if ((m = ECO_UPGRADE_REMOVED.matcher(text)).matches()) {
            actor = m.group(1).trim();
            String upgrade = m.group(2).trim();
            territory = m.group(3).trim();
            title = "🗑️ Upgrade Removed";
            color = 0xE74C3C;
            subType = "eco_upgrade_removed";
            extraItem = upgrade;
            fields.add(createField("Actor", actor, true));
            fields.add(createField("Territory", territory, true));
            fields.add(createField("Removed Upgrade", upgrade, true));
        } else if ((m = ECO_LOADOUT_APPLIED.matcher(text)).matches()) {
            actor = m.group(1).trim();
            String loadout = m.group(2).trim();
            territory = m.group(3).trim();
            title = "📋 Eco Loadout Applied";
            color = 0x8E44AD;
            subType = "eco_loadout";
            extraItem = loadout;
            fields.add(createField("Actor", actor, true));
            fields.add(createField("Territory", territory, true));
            fields.add(createField("Loadout", loadout, true));
        } else if ((m = ECO_CAPACITY_WARNING.matcher(text)).matches()) {
            territory = m.group(1).trim();
            String action = m.group(2).trim();
            title = "⚠️ Territory Storage Overflow Warning";
            color = 0xE67E22;
            subType = "eco_capacity";
            extraVal = action;
            fields.add(createField("Territory", territory, true));
            fields.add(createField("Condition", action + " more resources than storage capacity!", false));
        } else if ((m = ECO_STABILIZED.matcher(text)).matches()) {
            territory = m.group(1).trim();
            title = "✅ Territory Production Stabilized";
            color = 0x2ECC71;
            subType = "eco_stabilized";
            fields.add(createField("Territory", territory, true));
            fields.add(createField("Status", "Production has stabilized.", true));
        } else {
            fields.add(createField("Event", text, false));
        }

        String realPlayer = PlayerResolver.resolveRealName(comp, actor);

        JsonObject directPayload = new JsonObject();
        directPayload.addProperty("username", realPlayer);
        directPayload.addProperty("avatar_url", "https://visage.surgeplay.com/face/128/" + realPlayer);

        JsonArray embeds = new JsonArray();
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("description", text);
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());

        JsonObject authorObj = new JsonObject();
        authorObj.addProperty("name", realPlayer);
        authorObj.addProperty("icon_url", "https://visage.surgeplay.com/bust/128/" + realPlayer);
        embed.add("author", authorObj);

        embed.add("fields", fields);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Wynncraft Guild Logger");
        footer.addProperty("icon_url", "https://visage.surgeplay.com/face/64/Steve");
        embed.add("footer", footer);

        embeds.add(embed);
        directPayload.add("embeds", embeds);

        JsonObject backendEvent = new JsonObject();
        backendEvent.addProperty("type", "eco");
        backendEvent.addProperty("subType", subType);
        populateSecurityMetadata(backendEvent, realPlayer, actor);
        if (extraItem != null) backendEvent.addProperty("item", extraItem);
        if (extraVal != null) backendEvent.addProperty("quantity", extraVal);
        if (extraVal != null) backendEvent.addProperty("extraVal", extraVal);
        if (extraScope != null) backendEvent.addProperty("scope", extraScope);
        backendEvent.addProperty("realPlayerName", realPlayer);
        backendEvent.addProperty("rawPlayerName", actor);
        if (territory != null) backendEvent.addProperty("territory", territory);
        backendEvent.addProperty("text", text);
        backendEvent.addProperty("eventTitle", title);
        backendEvent.addProperty("eventColor", String.format("#%06X", (0xFFFFFF & color)));
        backendEvent.add("fields", fields);

        dispatchWebhook(backendEvent, webhookUrl, directPayload);
        return true;
    }

    private static JsonObject createField(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", inline);
        return f;
    }

    private void dispatchWebhook(JsonObject backendEvent, String fallbackWebhookUrl, JsonObject directPayload) {
        ASYNC_POOL.submit(() -> {
            boolean sentByBackend = false;
            try {
                sentByBackend = postToBackend(BACKEND_EVENT_URL, backendEvent.toString());
            } catch (Exception e) {
                PotsmodClient.LOGGER.debug("Backend webhook dispatch unavailable: {}", e.getMessage());
            }

            if (!sentByBackend && fallbackWebhookUrl != null && !fallbackWebhookUrl.isBlank()) {
                try {
                    postHttp(fallbackWebhookUrl, directPayload.toString());
                } catch (Exception e) {
                    PotsmodClient.LOGGER.error("Fallback webhook post failed", e);
                }
            }
        });
    }

    private static boolean postToBackend(String endpoint, String json) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "r-MinecraftClient");
            if (BackendManager.getSessionToken() != null) {
                conn.setRequestProperty("x-session-token", BackendManager.getSessionToken());
            }
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);
            conn.setDoOutput(true);

            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }
            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private static void postHttp(String urlStr, String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "r-MinecraftClient");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setDoOutput(true);

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int respCode = conn.getResponseCode();
        if (respCode < 200 || respCode >= 300) {
            PotsmodClient.LOGGER.warn("Discord webhook returned response code {}", respCode);
        }
    }
}
