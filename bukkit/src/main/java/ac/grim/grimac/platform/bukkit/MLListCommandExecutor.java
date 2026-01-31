package ac.grim.grimac.platform.bukkit;

import ac.grim.grimac.platform.bukkit.player.MLMenuGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Простой executor для команды /tushpAcList
 */
public class MLListCommandExecutor implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("§c[GrimAC ML] Только игроки могут использовать эту команду!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("grim.ml.list")) {
            player.sendMessage("§cУ вас нет прав!");
            return true;
        }

        // Открываем GUI
        MLMenuGUI.openMenu(player, 0);
        return true;
    }
}
