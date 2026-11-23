package org.pots.potsmod.managers;

import lombok.experimental.UtilityClass;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.pots.potsmod.commands.BombsCommand;
import org.pots.potsmod.commands.PotsmodCommand;
import org.pots.potsmod.commands.ProfessionBombShareCommand;
import org.pots.potsmod.commands.RadianceSyncCommand;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

@UtilityClass
public class CommandManager {
    private static boolean registered;

    public static void init() {
        if (registered) {
            return;
        }
        registered = true;

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(RadianceSyncCommand.command());
            dispatcher.register(ProfessionBombShareCommand.command());
            dispatcher.register(BombsCommand.command());
            dispatcher.register(PotsmodCommand.command());
            dispatcher.register(literal("pm").redirect(dispatcher.getRoot().getChild("potsmod")));
        });
    }
}
