package ac.grim.grimac.platform.bukkit.command;

import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.platform.bukkit.player.MLMenuGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

/**
 * Команда /tushpAcList для открытия меню "Курятник"
 * BUKKIT MODULE - использует Bukkit API
 */
public class MLListCommand implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {

        // ========== /tushpAcList - Открыть меню ==========
        commandManager.command(
                commandManager.commandBuilder("tushpAcList")
                        .permission("grim.ml.list")
                        .handler(this::handleListMenu)
        );
    }

    private void handleListMenu(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();

        if (!sender.isPlayer()) {
            sender.sendMessage("§c[GrimAC ML] Только игроки могут использовать эту команду!");
            return;
        }

        PlatformPlayer platformPlayer = sender.getPlatformPlayer();
        if (platformPlayer == null) {
            sender.sendMessage("§c[GrimAC ML] Ошибка получения данных игрока!");
            return;
        }

        // Получаем Bukkit Player
        Player bukkitPlayer = Bukkit.getPlayer(platformPlayer.getUniqueId());
        if (bukkitPlayer == null) {
            sender.sendMessage("§c[GrimAC ML] Игрок не найден на сервере!");
            return;
        }

        // Открываем меню
        MLMenuGUI.openMenu(bukkitPlayer, 0);
    }
}
