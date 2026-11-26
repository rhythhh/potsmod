package org.pots.potsmod.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@UtilityClass
public class FeatureHelpManager {
    private static final Map<String, String> EXPLANATIONS = new HashMap<>();
    private static boolean loaded = false;

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Identifier id = Identifier.fromNamespaceAndPath("potsmod", "feature_explanations.json");
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    for (String key : obj.keySet()) {
                        EXPLANATIONS.put(key, obj.get(key).getAsString());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static String getExplanation(String featureId, String title, String fallbackDesc) {
        ensureLoaded();
        if (featureId != null && EXPLANATIONS.containsKey(featureId)) {
            return EXPLANATIONS.get(featureId);
        }
        if (fallbackDesc != null && !fallbackDesc.isBlank()) {
            return fallbackDesc;
        }
        return title != null ? "Configuration settings for " + title + "." : "Feature explanation.";
    }
}
