package org.pots.potsmod.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lombok.experimental.UtilityClass;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.features.IgnoreFeature;
import org.pots.potsmod.features.economysandbox.EcoSaveManager;
import org.pots.potsmod.features.economysandbox.EconomySandboxScreen;
import org.pots.potsmod.features.economysandbox.EconomySandboxState;
import org.pots.potsmod.features.economysandbox.EconomySandboxStorage;
import org.pots.potsmod.managers.FeatureManager;
import org.pots.potsmod.managers.OverlayManager;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

@UtilityClass
public class PotsmodCommand {
    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return literal("potsmod")
                .then(literal("config").executes(ctx -> openConfig()))
                .then(literal("ignoremanager").executes(ctx -> openIgnore(ctx.getSource())))
                .then(literal("ignore").executes(ctx -> openIgnore(ctx.getSource())))
                .then(literal("overlays").executes(ctx -> openOverlays()))
                .then(literal("overlaymanager").executes(ctx -> openOverlays()))
                .then(literal("as").executes(ctx -> autoStream()))
                .then(literal("autostream").executes(ctx -> autoStream()))
                .then(literal("sandbox").executes(ctx -> openSandbox()))
                .then(literal("ecosandbox").executes(ctx -> openSandbox()))
                .then(literal("import")
                        .executes(ctx -> importEco(ctx.getSource(), null))
                        .then(argument("code", StringArgumentType.greedyString())
                                .executes(ctx -> importEco(ctx.getSource(), StringArgumentType.getString(ctx, "code")))));
    }

    private static int importEco(FabricClientCommandSource source, String inputCode) {
        String code = inputCode;
        if (code == null || code.trim().isEmpty()) {
            code = Minecraft.getInstance().keyboardHandler.getClipboard();
        }
        if (code == null || code.trim().isEmpty()) {
            source.sendError(Component.literal("§c[r] No share code provided or found in clipboard."));
            return 0;
        }

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

            String loadoutName = "Imported " + (emptySlot != -1 ? (emptySlot + 1) : "Eco");
            int slotIdx = emptySlot != -1 ? emptySlot : 0;
            EcoSaveManager.saveSlotFromState(slotIdx, loadoutName, state);
            EconomySandboxStorage.saveCurrentState(state);

            Minecraft client = Minecraft.getInstance();
            client.submit(() -> client.setScreen(new EconomySandboxScreen(slotIdx)));

            source.sendFeedback(Component.literal("§a[r] Successfully imported Eco Loadout into Slot " + (slotIdx + 1) + " ('" + loadoutName + "')!"));
            return 1;
        } catch (Exception e) {
            source.sendError(Component.literal("§c[r] Failed to import Eco Loadout: " + e.getMessage()));
            return 0;
        }
    }

    private static int openSandbox() {
        Minecraft client = Minecraft.getInstance();
        client.submit(() -> client.setScreen(new org.pots.potsmod.features.economysandbox.EconomySandboxScreen()));
        return 1;
    }

    private static int openConfig() {
        Minecraft client = Minecraft.getInstance();
        client.submit(() -> client.setScreen(PotsmodConfig.getConfigScreen(client.screen)));
        return 1;
    }

    private static int openIgnore(FabricClientCommandSource source) {
        IgnoreFeature ignoreFeature = FeatureManager.getIgnoreFeature();
        if (ignoreFeature == null) {
            source.sendError(Component.literal("Ignore manager is not initialized."));
            return 0;
        }
        Minecraft.getInstance().submit(ignoreFeature::openScreen);
        return 1;
    }

    private static int openOverlays() {
        Minecraft.getInstance().submit(() -> OverlayManager.openConfig());
        return 1;
    }

    private static int autoStream() {
        Minecraft.getInstance().submit(() -> FeatureManager.getAutoStreamFeature().toggle());
        return 1;
    }
}
