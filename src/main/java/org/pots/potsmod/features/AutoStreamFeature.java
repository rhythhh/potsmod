package org.pots.potsmod.features;

import com.wynntils.core.components.Models;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.pots.potsmod.config.PotsmodConfig;
import org.pots.potsmod.eventbus.REventBus;
import org.pots.potsmod.eventbus.Subscribe;
import org.pots.potsmod.models.events.BossBarNameEvent;
import org.pots.potsmod.models.events.CommandSentEvent;
import org.pots.potsmod.util.McUtils;

public class AutoStreamFeature extends Feature {

    private long lastSeen;
    private long lastStreamed;
    private boolean streamEnabled = false;

    @Override
    public void init() {
        REventBus.subscribe(this);
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                runSafe("onTick", () -> onTick(client)));
        this.setEnabled(false);
    }

    public void enable() {
        var mc = Minecraft.getInstance();
        if (!streamEnabled && mc.getConnection() != null) {
            mc.getConnection().sendCommand("stream");
            streamEnabled = true;
        }
        this.setEnabled(true);
        McUtils.logMessageToClient("Auto stream enabled!");
    }

    public void disable() {
        var connection = Minecraft.getInstance().getConnection();
        if (streamEnabled && connection != null) {
            connection.sendCommand("stream");
            streamEnabled = false;
        }
        this.setEnabled(false);
        McUtils.logMessageToClient("Auto stream disabled!");
    }

    public void toggle() {
        if (isEnabled()) {
            disable();
        } else {
            enable();
        }
    }

    private void onTick(Minecraft mc) {
        if (!Models.WorldState.onWorld()) {
            lastStreamed = System.currentTimeMillis();
            streamEnabled = false;
            return;
        }
        if (isDisabled() || mc.getConnection() == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        boolean isStreamCooldownOver = (currentTime - lastSeen) >= PotsmodConfig.getData().getStreamCooldown();
        boolean isCommandCooldownOver = (currentTime - lastStreamed) >= 1000;

        if (isStreamCooldownOver && isCommandCooldownOver) {
            mc.getConnection().sendCommand("stream");
            lastStreamed = currentTime;
            streamEnabled = true;
        }
    }

    @Subscribe
    private void onBossBar(BossBarNameEvent event) {
        if (event.getTitle().getString().contains("Streamer mode enabled")) {
            lastSeen = System.currentTimeMillis();
        }
    }

    @Subscribe
    private void onCommand(CommandSentEvent event) {
        if (event.command().startsWith("stream")) {
            streamEnabled = !streamEnabled;
            if (isEnabled()) {
                this.disable();
            }
        }
    }
}
