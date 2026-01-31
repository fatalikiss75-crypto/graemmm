package ac.grim.grimac.platform.bukkit;

import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.platform.bukkit.player.MLMenuGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.incendo.cloud.CommandManager;

public final class MLCommandRegistrar {

    public static void register(CommandManager<Sender> manager) {
        manager.command(
                manager.commandBuilder("tushpAcList")
                        .permission("grim.ml.list")
                        .handler(context -> {

                            Sender sender = context.sender();

                            if (!sender.isPlayer()) {
                                sender.sendMessage("§cТолько игроки!");
                                return;
                            }

                            Player player = Bukkit.getPlayer(
                                    sender.getPlatformPlayer().getUniqueId()
                            );

                            if (player == null) return;

                            MLMenuGUI.openMenu(player, 0);
                        })
        );
    }
}
